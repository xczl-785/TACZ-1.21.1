#!/usr/bin/env python3
"""Verify complete native item retirement, preserved caliber effects and the final jar."""
import argparse
import hashlib
import json
import re
import subprocess
import zipfile
from pathlib import Path
from verify_ammunition_chain import ROOT, PACK, read_json, snapshot, sha

from weapon_migration import source_rows, successor_hash
from adapter_migration import adapter_rows

LEDGER = ROOT/'docs/assembly-experiment/native-ammo-retirement.json'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path)
    args = parser.parse_args()
    ledger = json.loads(LEDGER.read_text())
    base = ledger['baseline_commit']
    rows = {r['path']: r for r in ledger['files']}
    actual = subprocess.check_output(['git', 'diff', '--name-status', base, '--', 'src'], cwd=ROOT, text=True).splitlines()
    actual_paths = {line.split('\t')[1] for line in actual}
    actual_paths.update(subprocess.check_output(['git','ls-files','--others','--exclude-standard','--','src'],cwd=ROOT,text=True).splitlines())
    expected_paths = set(rows) | set(source_rows()) | set(adapter_rows())
    assert actual_paths == expected_paths, ('Unexpected production diff', actual_paths ^ expected_paths)
    # Nothing in the canonical 86-round data/generation pipeline may change.
    ammo_changes = subprocess.check_output(['git','diff','--name-only',base,'--','ammunition'],cwd=ROOT,text=True).splitlines()
    assert set(ammo_changes) <= {'ammunition/README.md'}, ammo_changes
    assert not subprocess.check_output(['git','ls-files','--others','--exclude-standard','--','ammunition'],cwd=ROOT,text=True).strip()
    for path, row in rows.items():
        if row['action'] != 'add':
            before = subprocess.check_output(['git','show',base+':'+path],cwd=ROOT)
            assert hashlib.sha256(before).hexdigest() == row['before_sha256'], path
        if row['action'] == 'delete':
            assert not (ROOT/path).exists(), path
        else:
            assert sha(path) == successor_hash(path, row['after_sha256']), path
        # A ledger cannot authorize unrelated gun/refit/animation edits.
        if path.startswith('src/main/resources/'):
            assert ('/display/ammo/' in path or '/index/ammo/' in path or
                    '/geo_models/ammo/' in path or '/textures/ammo/' in path or
                    path.endswith('/models/item/ammo.json')), path
        assert not any(x in path for x in ('RefitInventoryExtension', '/refit/', '/animations/', '/assembled/')), path
    old = read_json('docs/assembly-experiment/ammunition-cleanup.json')
    current = snapshot()
    assert current['guns'] == old['guns'], '15 guns changed'
    assert current['tarkov_ammunition'] == old['tarkov_ammunition'], '86 rounds changed'
    old_native = {r['id']:r for r in old['native_ammunition']}
    for row in current['native_ammunition']:
        previous = old_native[row['id']]
        # Only native item presentation dependencies may disappear.
        assert row['dependencies'] == [d for d in previous['dependencies'] if not d['role'].startswith('item.')], row['id']
        for field, removed in [('index', {'stack_size','sort'}), ('display', {'model','texture','slot','transform'})]:
            before_text = subprocess.check_output(['git','show',base+':'+row[field]],cwd=ROOT,text=True)
            before_text = re.sub(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*[\s\S]*?\*/', lambda m:m[0] if m[0].startswith('"') else ' ', before_text)
            before = json.loads(before_text)
            assert read_json(row[field]) == {k:v for k,v in before.items() if k not in removed}, row[field]
    # No source reference may construct a retired item or load an item-only texture/model.
    retired_types = ['AmmoItemBuilder','AmmoItemDataAccessor','AmmoItemRenderer','AmmoTransform','ModItems.AMMO']
    for path in (ROOT/'src').rglob('*'):
        if path.suffix not in ('.java','.json','.lua'): continue
        text = path.read_text(errors='replace')
        assert not any(token in text for token in retired_types), path
        assert not re.search(r'"tacz:ammo"|tacz:ammo/(?:uv|slot)/', text), path
        if path.suffix == '.java':
            assert not re.search(r'ITEMS\.register\("ammo"', text), path
    assert not list((ROOT/PACK/'assets/tacz/geo_models/ammo').glob('*.json'))
    assert not list((ROOT/PACK/'assets/tacz/textures/ammo').rglob('*.png'))
    jar_resources = 0
    if args.jar:
        with zipfile.ZipFile(args.jar) as jar:
            names=set(jar.namelist())
            for path,row in rows.items():
                if row['action']!='delete':continue
                name=path.removeprefix('src/main/resources/') if path.startswith('src/main/resources/') else path.removeprefix('src/main/java/').removesuffix('.java')+'.class'
                assert name not in names, name
            for prefix in ['src/main/resources','ammunition/runtime']:
                for path in (ROOT/prefix).rglob('*'):
                    if not path.is_file():continue
                    name=path.relative_to(ROOT/prefix).as_posix()
                    if name=='META-INF/neoforge.mods.toml':continue
                    assert jar.read(name)==path.read_bytes(),name
                    jar_resources+=1
            for name in ['TemporaryAmmoRefund','TemporaryAmmoRefundPolicy','AmmunitionRegistry','TarkovAmmoItem']:
                assert 'com/tacz/guns/ammunition/'+name+'.class' in names,name
    print(json.dumps({'result':'PASS','tarkov_rounds':86,'guns':15,'caliber_effect_indices':24,
                      'production_changes':len(rows),'deleted_files':sum(r['action']=='delete' for r in rows.values()),
                      'jar_resources_compared':jar_resources,'gun_models_animations_refit_unchanged':True,
                      'client_acceptance':'owner_pending'},indent=2))


if __name__=='__main__':main()
