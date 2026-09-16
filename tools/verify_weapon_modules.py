#!/usr/bin/env python3
"""Check migrated boundaries and exact original resources; optional packaged artifact check."""
import argparse,hashlib,json,re,zipfile
from pathlib import Path
from weapon_migration import source_rows
from native_m4a1_migration import native_successor, predecessor_text
R=Path(__file__).resolve().parents[1]
MODULES=['weapon_assembly','weapon_models','weapon_runtime','weapon_assembly_ui']
def verify(jar=None, newmod=None):
    source_rows()
    count=0
    for module in MODULES:
        root=R/'modules'/module
        assert not list(root.glob('src/main/resources/META-INF/*mods.toml')),module
        for p in (root/'src/main/java').rglob('*.java'):
            s=p.read_text();count+=1
            assert not re.search(r'@(?:[\w.]+\.)?Mod\(',s),p
            assert not re.search(r'import\s+.*\.(development|verification)\.',s),p
            assert not re.search(r'dev\.(?:tacticalinventory|tacticalcombat|tacticalcharacter|raidgameplay|tacticaltacz)\.|com\.tacz\.',s),p
            if module=='weapon_assembly': assert not re.search(r'dev\.itemfoundation\.|net\.(?:minecraft|neoforged)\.',s),p
            if module=='weapon_models': assert not re.search(r'net\.(?:minecraft|neoforged)\.|dev\.itemfoundation\.',s),p
            if module=='weapon_runtime': assert not re.search(r'dev\.weaponassemblyui\.|net\.minecraft\.client\.',s),p
            assert not re.search(r'dev\.itemfoundation\.(internal|neoforge|client\.internal)\.',s),p
    runtime=(R/'modules/weapon_runtime/src/main/java/dev/weaponruntime/WeaponRuntime.java').read_text()
    assert 'Registries.DATA_COMPONENT_TYPE,"weapon_runtime"' in runtime and 'registerComponentType("profile"' in runtime
    gun=(R/'src/main/java/com/tacz/guns/GunMod.java').read_text()
    assert gun.count('dev.weaponruntime.WeaponRuntime.register(bus);')==1
    assert 'weaponassemblyui' not in gun
    # All moved files are accounted for, including retired entrypoints and test-only changes.
    ledger=json.loads((R/'docs/assembly-experiment/weapon-module-migration.json').read_text())
    for row in ledger['files']:
        target=R/row['new']
        if row.get('action')=='retire':
            assert not target.exists(),row['new']
        elif 'after_sha256' in row:
            assert hashlib.sha256(target.read_bytes()).hexdigest()==native_successor(row['new'],row['after_sha256']),row['new']
        if '/src/main/resources/' in row['new'] and not row['new'].endswith('neoforge.mods.toml'):
            if row['new'] in {f'modules/weapon_assembly_ui/src/main/resources/assets/weapon_assembly_ui/lang/{locale}.json' for locale in ('en_us','zh_cn')}:
                current=target.read_text();before=json.loads(predecessor_text(row['new'],current));after=json.loads(current)
                assert all(after.get(k)==v for k,v in before.items()),row['new']
                expected={'weapon_assembly_ui.'+k for k in ('edit_preset','exit_preset','temporary_preset','catalog_unlimited','no_catalog_candidates')}
                assert set(after)-set(before)==expected,row['new']
                assert hashlib.sha256(target.read_bytes()).hexdigest()==native_successor(row['new'],row['before_sha256']),row['new']
            else:
                assert hashlib.sha256(target.read_bytes()).hexdigest()==row['before_sha256'],row['new']
    fixture_sources=json.loads((R/'modules/fixture-sources.json').read_text())
    for row in fixture_sources['files']:
        assert hashlib.sha256((R/row['new']).read_bytes()).hexdigest()==row['sha256'],row['new']
        if newmod:
            origin=newmod/row['old']
            if not origin.exists() and row['old'].startswith('source/mods/tacz_adapter/'):
                origin=R/row['old'].replace('source/mods/tacz_adapter/','modules/tacz_adapter/',1)
            assert hashlib.sha256(origin.read_bytes()).hexdigest()==row['sha256'],row['old']
    if newmod:
        for row in ledger['files']:
            assert not (newmod/row['old']).exists(),row['old']
        # Prove this item did not alter public module, adapter behavior, or gun content.
        import subprocess
        changed=subprocess.check_output(['git','diff','--name-only','-z',ledger['newmod_baseline'],'--','source/mods'],cwd=newmod).decode().rstrip('\0').split('\0')
        allowed={r['old'] for r in ledger['files']}
        allowed.update(r['old'] for r in json.loads((R/'docs/assembly-experiment/adapter-migration.json').read_text())['files'])
        allowed.update(['source/mods/README.md','source/mods/tacz_adapter/README.md','source/mods/tacz_adapter/build.gradle',
            'source/mods/tacz_adapter/dependency-lock.json','source/mods/tacz_adapter/src/main/resources/META-INF/neoforge.mods.toml'])
        assert all(p in allowed or p.startswith('source/mods/tacz_adapter/docs/') for p in changed),changed
    if jar:
        with zipfile.ZipFile(jar) as z:
            names=z.namelist();assert len(names)==len(set(names)), 'Duplicate entries'
            meta=z.read('META-INF/neoforge.mods.toml').decode()
            assert len(re.findall(r'^\[\[mods\]\]',meta,re.M))==1 and 'modId = "tacz"' in meta
            assert not re.search(r'modId\s*=\s*"weapon_',meta)
            assert not any(n.startswith(('dev/itemfoundation/','dev/tacticalinventory/','dev/tacticalcombat/')) for n in names)
            assert not any('/development/' in n or '/demo-parts/' in n or '/textures/adar/' in n or n.endswith('/materials/adar.json') or (n.startswith('dev/weapon') and n.endswith('Test.class')) or n.startswith(('assembly-fixtures/','assembly-adar/','model-fixtures/')) for n in names)
            for module in MODULES:
                for p in (R/'modules'/module/'src/main/resources').rglob('*'):
                    if p.is_file(): assert z.read(str(p.relative_to(R/'modules'/module/'src/main/resources')))==p.read_bytes(),p
                for p in (R/'modules'/module/'src/main/java').rglob('*.java'):
                    name=str(p.relative_to(R/'modules'/module/'src/main/java')).removesuffix('.java')+'.class'
                    assert name in names,name
                    # Annotation descriptor must not survive in class files.
                    assert b'Lnet/neoforged/fml/common/Mod;' not in z.read(name),name
            assert 'META-INF/licenses/EFTForge-MIT.txt' in names
    print(f'WEAPON_MODULES PASS: {count} production sources, four internal boundaries, persistent profile identity, original resources preserved with audited additive preset labels'+(', single Mod Jar' if jar else ''))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--jar',type=Path);p.add_argument('--newmod',type=Path);a=p.parse_args();verify(a.jar,a.newmod)
