#!/usr/bin/env python3
"""Check migrated boundaries and exact original resources; optional packaged artifact check."""
import argparse,hashlib,json,re,zipfile
from pathlib import Path
from weapon_migration import source_rows
from native_m4a1_migration import native_successor, predecessor_text
R=Path(__file__).resolve().parents[1]
MODULES=['weapon_runtime']
# The workbench UI, the rule engine, the model maths and their tests now live in the firearms Mod;
# the TaCZ side must keep no source for them.
EXTRACTED_MODULES=['weapon_assembly_ui','weapon_assembly','weapon_models']
def verify(jar=None, newmod=None):
    source_rows()
    for module in EXTRACTED_MODULES:
        assert not list((R/'modules'/module/'src').rglob('*')),f'Extracted module still has sources: {module}'
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
    assert gun.count('com.tacz.guns.api.extension.GunPlatformExtensions.register(bus);')==1
    assert 'dev.weaponruntime' not in gun and 'weaponassemblyui' not in gun
    provider=(R/'modules/tacz_adapter/src/main/java/dev/tacticaltacz/TacticalGunPlatformExtension.java').read_text()
    assert provider.count('dev.weaponruntime.WeaponRuntime.register(modBus);')==1
    # All moved files are accounted for, including retired entrypoints and test-only changes.
    ledger=json.loads((R/'docs/assembly-experiment/weapon-module-migration.json').read_text())
    import subprocess
    subprocess.check_call(['git','cat-file','-e',ledger['tacz_baseline']+'^{commit}'],cwd=R)
    for row in ledger['files']:
        target=R/row['new']
        if row.get('action')=='retire':
            assert not target.exists(),row['new']
        elif 'after_sha256' in row:
            preserved=subprocess.check_output(['git','show',ledger['tacz_baseline']+':'+row['new']],cwd=R)
            assert hashlib.sha256(preserved).hexdigest()==row['after_sha256'],row['new']
        if '/src/main/resources/' in row['new'] and not row['new'].endswith('neoforge.mods.toml'):
            if row['new'] in {f'modules/weapon_assembly_ui/src/main/resources/assets/weapon_assembly_ui/lang/{locale}.json' for locale in ('en_us','zh_cn')}:
                current=preserved.decode();before=json.loads(predecessor_text(row['new'],current));after=json.loads(current)
                assert all(after.get(k)==v for k,v in before.items()),row['new']
                assert hashlib.sha256(preserved).hexdigest()==row['after_sha256'],row['new']
            else:
                assert hashlib.sha256(preserved).hexdigest()==row['before_sha256'],row['new']
    fixture_sources=json.loads((R/'modules/fixture-sources.json').read_text())
    # The migrated fixtures live with the firearms tests that consume them, inside the NewMod workspace.
    fixture_workspace=newmod or R.parent
    for row in fixture_sources['files']:
        assert hashlib.sha256((fixture_workspace/row['new']).read_bytes()).hexdigest()==row['sha256'],row['new']
        if newmod:
            origin=newmod/row['old']
            if not origin.exists() and row['old'].startswith('source/mods/tacz_adapter/'):
                origin=R/row['old'].replace('source/mods/tacz_adapter/','modules/tacz_adapter/',1)
            assert hashlib.sha256(origin.read_bytes()).hexdigest()==row.get('source_sha256',row['sha256']),row['old']
    if newmod:
        for row in ledger['files']:
            assert not (newmod/row['old']).exists(),row['old']
        # Prove this item did not alter public module, adapter behavior, or gun content.
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
            # The assembly-rule notice moved to the firearms Mod with WeaponStats, so this Jar no longer ships it.
            assert 'META-INF/licenses/EFTForge-MIT.txt' not in names
    print(f'WEAPON_MODULES PASS: {count} production sources, three internal boundaries, one extracted workbench UI plus the rule and presentation tests, persistent profile identity, original resources preserved with audited additive preset labels'+(', single Mod Jar' if jar else ''))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--jar',type=Path);p.add_argument('--newmod',type=Path);a=p.parse_args();verify(a.jar,a.newmod)
