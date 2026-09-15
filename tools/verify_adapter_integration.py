#!/usr/bin/env python3
"""Migration provenance and package ownership, without starting Minecraft."""
import argparse,hashlib,json,re,subprocess,zipfile
from pathlib import Path
from adapter_migration import ROOT as R,adapter_rows

def verify(jar=None,development=None,newmod=None):
 adapter_rows()
 data=json.loads((R/'docs/assembly-experiment/adapter-migration.json').read_text())
 rows=data['files'];assert len(rows)==386
 for row in json.loads((R/'docs/assembly-experiment/adapter-authoring-migration.json').read_text())['files']:
  assert hashlib.sha256((R/row['new']).read_bytes()).hexdigest()==row['sha256'],row['new']
 mixins=[];changed=[]
 for row in rows:
  p=R/row['new']
  if newmod:
   old=subprocess.check_output(['git','show',data['newmod_baseline']+':'+row['old']],cwd=newmod)
   assert hashlib.sha256(old).hexdigest()==row['before_sha256'],row['old']
   assert not (newmod/row['old']).exists(),row['old']
  if row['action']=='retire':
   assert not p.exists(),p
   if '/mixin/' in row['old']:mixins.append(p.name)
   continue
  assert hashlib.sha256(p.read_bytes()).hexdigest()==row['after_sha256'],p
  if '/weapon-content/' in row['new'] or '/src/main/resources/' in row['new']:
   assert row['before_sha256']==row['after_sha256'],p
  if '/src/main/java/' in row['new'] or '/src/development/java/' in row['new']:
   s=p.read_text();assert '@Mod(' not in s and '@Mixin(' not in s,p
   if row['before_sha256']!=row['after_sha256']:
    changed.append(p.name)
    if newmod:
     expected=re.sub(r'modid\s*=\s*(?:"tactical_tacz_adapter"|TacticalTaczAdapter.MOD_ID)', 'modid="tacz"',old.decode())
     if p.name=='TacticalTaczAdapter.java':
      expected=expected.replace('import net.neoforged.fml.common.Mod;\n','').replace('@Mod(TacticalTaczAdapter.MOD_ID)\n','').replace('    public TacticalTaczAdapter(net.neoforged.bus.api.IEventBus bus) {','    private TacticalTaczAdapter() {}\n    public static void register(net.neoforged.bus.api.IEventBus bus) {')
     assert s==expected,p
 assert len(mixins)==12,mixins
 gun=(R/'src/main/java/com/tacz/guns/GunMod.java').read_text()
 assert gun.count('dev.tacticaltacz.TacticalTaczAdapter.register(bus);')==1
 assert gun.index('AttachmentPropertyManager.registerModifier();')<gun.index('dev.tacticaltacz.TacticalTaczAdapter.register(bus);')
 for row in json.loads((R/'modules/tacz_adapter/development-commands.json').read_text())['registrars']:
  s=(R/row['source']).read_text()
  for literal in row['literals']:assert '"'+literal+'"' in s,(row['source'],literal)
 for candidate,isdev in [(jar,False),(development,True)]:
  if not candidate:continue
  with zipfile.ZipFile(candidate) as z:
   names=z.namelist();assert len(names)==len(set(names))
   meta=z.read('META-INF/neoforge.mods.toml').decode()
   assert meta.count('[[mods]]')==1 and 'modId = "tacz"' in meta
   assert not re.search(r'modId\s*=\s*"(?:weapon_\w+|tactical_tacz_adapter)"',meta)
   assert not any(n.startswith(('dev/itemfoundation/','dev/tacticalinventory/','dev/tacticalcharacter/','dev/tacticalcombat/','dev/tarkovcontent/','dev/tacticaltacz/mixin/')) for n in names)
   assert 'tactical_tacz_adapter.mixins.json' not in names
   assert ('dev/tacticaltacz/development/TaczDevelopmentCatalog.class' in names)==isdev
   if not isdev:assert not any('/development/' in n or '/verification/' in n for n in names)
   for row in rows:
    if row['action']=='retire':continue
    p=R/row['new']
    for marker in ['/src/main/resources/','/weapon-content/resources/']:
     if marker in row['new']:
      n=row['new'].split(marker)[1]
      if isdev and n.startswith('assets/tactical_tacz_adapter/lang/'):
       assert all(json.loads(z.read(n)).get(k)==v for k,v in json.loads(p.read_bytes()).items())
      else:assert z.read(n)==p.read_bytes(),n
    if '/src/main/java/' in row['new']:
     n=row['new'].split('/src/main/java/')[1].removesuffix('.java')+'.class'
     assert n in names and b'Lnet/neoforged/fml/common/Mod;' not in z.read(n),n
 print(f'ADAPTER_INTEGRATION PASS: {len(rows)} paths, {len(mixins)} injections retired; unchanged gun content and public ownership; {len(changed)} bootstrap/subscriber source edits')
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--jar',type=Path);p.add_argument('--development',type=Path);p.add_argument('--newmod',type=Path);a=p.parse_args();verify(a.jar,a.development,a.newmod)
