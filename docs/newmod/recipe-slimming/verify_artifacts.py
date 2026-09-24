"""Check runtime exclusion, retained gun payloads and retired recipe classes in both delivery Jars."""
from pathlib import Path
from zipfile import ZipFile
import hashlib, json, os, subprocess

ROOT = Path(__file__).resolve().parents[3]
os.chdir(ROOT)
OUT = ROOT / 'docs/newmod/recipe-slimming'
audit = json.loads((OUT / 'source-audit.json').read_text())
version = '1.1.8-hotfix-r6-slim2-newmod.963361c2'
previous = '1.1.8-hotfix-r6-compat-slimming-newmod.963361c2'
content_root = Path('modules/tacz_adapter/weapon-content/resources')
excluded = {row['jar_entry']: row for row in audit['evidence_exclusions']}
retired = [row['path'].removeprefix('src/main/java/').removesuffix('.java') for row in audit['retirements']]
content = subprocess.check_output(['git', 'ls-files', str(content_root)]).decode().splitlines()
required = ['com/tacz/guns/resource/index/CommonBlockIndex.class',
            'com/tacz/guns/resource/manager/RecipeFilterManager.class',
            'com/tacz/guns/resource/RetiredRecipePolicy.class',
            'com/tacz/guns/api/event/common/GunFireEvent.class',
            'com/tacz/guns/command/sub/ConfigCommand.class']
required += ['com/tacz/guns/config/' + n + '.class' for n in ['PreLoadConfig', 'CommonConfig', 'ServerConfig', 'ClientConfig']]
required += ['data/tacz/recipe/' + n + '.json' for n in ['target', 'target_minecart', 'statue', 'gunpowder']]
services = list(Path('modules/tacz_adapter/src/main/resources/META-INF/services').glob('*'))
artifacts = []
for suffix in ['.jar', '-development.jar']:
    path = Path('build/libs') / ('tacz-neoforge-1.21.1-' + version + suffix)
    old_path = Path('../source/vendor/tacz') / ('tacz-neoforge-1.21.1-' + previous + suffix)
    with ZipFile(path) as jar, ZipFile(old_path) as old:
        names = set(jar.namelist())
        assert all(name in names for name in required)
        assert all(name not in names for name in excluded)
        for name, row in excluded.items():
            assert hashlib.sha256(Path(row['source']).read_bytes()).hexdigest() == row['sha256']
            assert old.read(name) == Path(row['source']).read_bytes()
        removed_bytes = sum(old.getinfo(name).compress_size for name in excluded)
        assert not any(n == r + '.class' or n.startswith(r + '$') for n in names for r in retired)
        for name in names:
            if name.startswith('com/tacz/') and name.endswith('.class'):
                payload = jar.read(name)
                assert not any(r.encode() in payload for r in retired), name
        for source in content:
            p = Path(source)
            name = str(p.relative_to(content_root))
            if name not in excluded:
                assert jar.read(name) == p.read_bytes(), name
        for name in old.namelist():
            if name.endswith(('.png', '.ogg', '.lua')) or '/compat/' in name and name.endswith('.class'):
                assert jar.read(name) == old.read(name), name
        for p in services:
            assert jar.read('META-INF/services/' + p.name) == p.read_bytes()
        for name in required:
            if name.startswith('data/'):
                assert jar.read(name) == old.read(name)
        # Four remaining recipes all use vanilla types. RecipeFilter is a shared block schema, not a recipe serializer.
        recipes = [n for n in names if n.startswith('data/tacz/recipe/') and n.endswith('.json')]
        assert len(recipes) == 4
        assert all(json.loads(jar.read(n))['type'].startswith('minecraft:') for n in recipes)
        artifacts.append({'path': str(path), 'bytes': path.stat().st_size,
                          'sha512': hashlib.sha512(path.read_bytes()).hexdigest(),
                          'excluded_evidence_files': len(excluded), 'excluded_compressed_bytes': removed_bytes,
                          'retained_content_files': len(content) - len(excluded),
                          'retired_classes_absent': True, 'vanilla_recipes_decoded_by_registry_test': recipes,
                          'preserved_services': [p.name for p in services]})
(OUT / 'artifact-audit.json').write_text(json.dumps({'date': '2026-09-24', 'version': version,
    'source_audit_sha256': hashlib.sha256((OUT/'source-audit.json').read_bytes()).hexdigest(),
    'artifacts': artifacts, 'minecraft_acceptance': 'not run'}, indent=2, ensure_ascii=False) + '\n')
for a in artifacts:
    print(a['path'], a['bytes'], 'bytes;', a['excluded_evidence_files'], 'excluded;', a['excluded_compressed_bytes'], 'compressed bytes removed')
