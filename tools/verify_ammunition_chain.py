#!/usr/bin/env python3
"""Audit the experimental fork's ammo resources; never infer item usability from assets.

--write-plan freezes the pre-cleanup inventory. Default verifies the ledger and live
references. --jar also checks shipped resources. No client or world is launched.
"""
import argparse
import hashlib
import json
import re
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACK = Path('src/main/resources/assets/tacz/custom/tacz_default_gun')
LEDGER = Path('docs/assembly-experiment/ammunition-cleanup.json')
CALIBERS = {
    '12/70': '12g', '338lapua': '338', '357mag': '357mag', '45acp': '45acp',
    '50ae': '50ae', '50bmg': '50bmg', '556x45': '556x45', '57x28': '57x28',
    '58x42': '58x42', '762x39': '762x39', '762x51': '308', '9x19': '9mm',
}
# This map documents ContentAmmoMixin's existing mapping, not a runtime implementation.
DELETIONS = [str(PACK / 'assets/tacz/textures/ammo/slot' / name)
             for name in ('20x82.png', 'font.png')]


def read_json(path):
    """Gson accepts comments in the original gun-pack JSON; preserve quoted URLs."""
    text = (ROOT / path).read_text(encoding='utf-8')
    text = re.sub(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*[\s\S]*?\*/',
                  lambda m: m[0] if m[0].startswith('"') else ' ', text)
    return json.loads(text)


def sha(path):
    return hashlib.sha256((ROOT / path).read_bytes()).hexdigest()


def resolve(ref, kind, folder, extension):
    namespace, name = ref.split(':', 1)
    path = PACK / kind / namespace / folder / (name + extension)
    assert (ROOT / path).is_file(), f'Missing {ref}: {path}'
    return str(path)


def asset_dependencies(display):
    result = []
    for section, fields in [('item', display), ('shell', display.get('shell', {})),
                            ('entity', display.get('entity', {}))]:
        for key, folder, ext in [('model', 'geo_models', '.json'),
                                 ('texture', 'textures', '.png'), ('slot', 'textures', '.png')]:
            if key not in fields:
                continue
            path = resolve(fields[key], 'assets', folder, ext)
            result.append({'role': section + '.' + key, 'path': path, 'sha256': sha(path)})
            if key == 'texture':
                for suffix in ('_n', '_s'):
                    variant = str(Path(path).with_name(Path(path).stem + suffix + '.png'))
                    if (ROOT / variant).is_file():
                        result.append({'role': section + '.texture' + suffix,
                                       'path': variant, 'sha256': sha(variant)})
    return result


def snapshot():
    native = []
    for path in sorted((ROOT / PACK / 'data/tacz/index/ammo').glob('*.json')):
        index_path = str(path.relative_to(ROOT))
        index = read_json(index_path)
        display_path = resolve(index['display'], 'assets', 'display/ammo', '.json')
        display = read_json(display_path)
        native.append({'id': 'tacz:' + path.stem, 'index': index_path,
                       'index_sha256': sha(index_path), 'display': display_path,
                       'display_sha256': sha(display_path),
                       'dependencies': asset_dependencies(display)})
    by_id = {row['id']: row for row in native}
    guns = []
    for path in sorted((ROOT / PACK / 'data/tacz/index/guns').glob('*.json')):
        index = read_json(path)
        data_path = resolve(index['data'], 'data', 'data/guns', '.json')
        data = read_json(data_path)
        ammo = by_id[data['ammo']]
        assert any(d['role'] == 'shell.model' for d in ammo['dependencies']), path
        assert any(d['role'] == 'shell.texture' for d in ammo['dependencies']), path
        script = data.get('script')
        if script:
            resolve(script, 'data', 'scripts', '.lua')
        guns.append({'id': 'tacz:' + path.stem, 'data': data_path,
                     'data_sha256': sha(data_path), 'ammo': data['ammo'],
                     'script': script, 'reload_type': data['reload']['type'],
                     'infinite': data['reload'].get('infinite', False)})
    approved = read_json('ammunition/inputs/approved-ammunition.json')['items']
    catalog = read_json('ammunition/runtime/data/tarkov_content/catalog/ammunition.json')
    assert len(approved) == len(catalog) == 86
    assert {r['id'] for r in catalog} == {'tarkov_content:ammo_' + r['id'] for r in approved}
    assert len({r['id'] for r in catalog}) == 86
    rounds = []
    langs = {lang: read_json(f'ammunition/runtime/assets/tarkov_content/lang/{lang}.json')
             for lang in ('en_us', 'zh_cn')}
    for row in catalog:
        assert 'tacz:' + CALIBERS[row['caliber']] in by_id, row
        model = f"ammunition/runtime/assets/tarkov_content/models/item/ammo_{row['sourceId']}.json"
        model_data = read_json(model)
        namespace, texture = model_data['textures']['layer0'].split(':', 1)
        texture_path = f'ammunition/runtime/assets/{namespace}/textures/{texture}.png'
        assert (ROOT / texture_path).is_file(), texture_path
        key = 'item.' + row['id'].replace(':', '.')
        assert all(key in lang for lang in langs.values()), key
        rounds.append({'id': row['id'], 'caliber': row['caliber'],
                       'native_render_id': 'tacz:' + CALIBERS[row['caliber']],
                       'model': model, 'model_sha256': sha(model),
                       'texture': texture_path, 'texture_sha256': sha(texture_path)})
    assert len(native) == 24 and len(guns) == 15
    return {'native_ammunition': native, 'guns': guns, 'tarkov_ammunition': rounds}


def verify_no_inbound_deletions():
    # Check literal resource locations, file paths, and constructor paths. Runtime
    # loaders resolve display fields; implicit shader companions are retained above.
    needles = [value for path in DELETIONS for value in
               [str(Path(path).relative_to(PACK / 'assets/tacz/textures')).removesuffix('.png'),
                Path(path).name if Path(path).stem != 'font' else 'slot/font']]
    roots = [ROOT / 'src', ROOT / 'ammunition/runtime']
    for root in roots:
        for path in root.rglob('*'):
            if path.suffix not in ('.java', '.json', '.lua', '.properties', '.toml'):
                continue
            text = path.read_text(errors='replace')
            assert not any(n in text for n in needles), f'Deleted asset referenced: {path}'


def run():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write-plan', action='store_true')
    parser.add_argument('--jar', type=Path)
    args = parser.parse_args()
    verify_no_inbound_deletions()
    live = snapshot()
    if args.write_plan:
        assert not (ROOT / LEDGER).exists(), 'Inventory is frozen; do not overwrite it'
        baseline = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
        plan = {'date': '2026-09-15', 'baseline_commit': baseline,
                'scope': 'Bundled resources only; no runtime implementation or item ID change',
                'deleted': [{'path': path, 'before_sha256': sha(path), 'old_id': None, 'new_id': None,
                             'resource_id': 'tacz:' + str(Path(path).relative_to(PACK / 'assets/tacz/textures')).removesuffix('.png'),
                             'reason': 'Unreferenced legacy ammo slot image; no index/display/code/script consumer',
                             'source': baseline + ':' + path} for path in DELETIONS], **live}
        (ROOT / LEDGER).write_text(json.dumps(plan, ensure_ascii=False, indent=2) + '\n')
        print('Frozen pre-cleanup inventory:', LEDGER)
        return
    plan = read_json(LEDGER)
    for key in live:
        assert live[key] == plan[key], f'Changed protected ammunition chain: {key}'
    for row in plan['deleted']:
        assert not (ROOT / row['path']).exists(), row['path']
        before = subprocess.check_output(['git', 'show', plan['baseline_commit'] + ':' + row['path']], cwd=ROOT)
        assert hashlib.sha256(before).hexdigest() == row['before_sha256']
    # Preserve ALL production code, all gun/animation/refit resources, all 86-round
    # authoring inputs and generated payloads, not only the obvious shell files.
    changes = subprocess.check_output(['git', 'diff', '--name-status', plan['baseline_commit'], '--',
                                       'src', 'ammunition', 'build.gradle.kts', 'settings.gradle.kts',
                                       'gradle', 'gradle.properties'], cwd=ROOT, text=True).splitlines()
    allowed = {'D\t' + path for path in DELETIONS} | {'M\tammunition/README.md'}
    assert set(changes) <= allowed, f'Out-of-scope changes: {set(changes) - allowed}'
    untracked = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard', '--',
                                        'src', 'ammunition'], cwd=ROOT, text=True).splitlines()
    assert not untracked, f'Unexpected production files: {untracked}'
    shipped = 0
    if args.jar:
        with zipfile.ZipFile(args.jar) as jar:
            names = set(jar.namelist())
            assert not any(p.removeprefix('src/main/resources/') in names for p in DELETIONS)
            for prefix in ('src/main/resources', 'ammunition/runtime'):
                for path in (ROOT / prefix).rglob('*'):
                    if not path.is_file():
                        continue
                    rel = str(path.relative_to(ROOT / prefix))
                    if rel == 'META-INF/neoforge.mods.toml':  # Gradle expands version tokens.
                        continue
                    assert jar.read(rel) == path.read_bytes(), f'Jar resource mismatch: {rel}'
                    shipped += 1
            for cl in ('ammunition/TarkovAmmoItem', 'ammunition/AmmunitionRegistry',
                       'item/AmmoItem', 'api/item/builder/AmmoItemBuilder'):
                assert 'com/tacz/guns/' + cl + '.class' in names
    print(json.dumps({'result': 'PASS', 'tarkov_rounds': len(live['tarkov_ammunition']),
                      'tarkov_calibers': len(CALIBERS), 'guns': len(live['guns']),
                      'gun_calibers': len({r['ammo'] for r in live['guns']}),
                      'native_indices': len(live['native_ammunition']),
                      'deleted_orphan_images': len(DELETIONS), 'jar_resources_compared': shipped,
                      'runtime_code_unchanged': True, 'client_acceptance': 'owner_pending'}, indent=2))


if __name__ == '__main__':
    run()
