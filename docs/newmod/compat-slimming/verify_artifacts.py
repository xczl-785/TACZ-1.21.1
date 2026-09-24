"""Recheck the two local slimming candidates against committed source and lock evidence."""
from pathlib import Path
import os
import json,zipfile,hashlib,subprocess
root=Path(__file__).resolve().parents[3]
os.chdir(root)
out=root/'docs/newmod/compat-slimming'
c=json.loads((out/'source-audit.json').read_text())
retired=[f['path'].removeprefix('src/main/java/').removesuffix('.java') for f in c['whole_file_retirements']]
required=['com/tacz/guns/compat/'+p+'.class' for p in ['iris/IrisCompat','ar/ARCompat','playeranimator/PlayerAnimatorCompat','shouldersurfing/ShoulderSurfingCompat','optifine/OptifineCompat']]
required+=['com/tacz/guns/config/'+p+'.class' for p in ['PreLoadConfig','CommonConfig','ServerConfig','ClientConfig']]
required+=['com/tacz/guns/command/sub/ConfigCommand.class','com/tacz/guns/command/sub/ConfigCommand$ConfigKey.class','com/tacz/guns/client/input/ShootKey.class','com/tacz/guns/client/input/AimKey.class','com/tacz/guns/api/event/common/GunFireEvent.class','com/tacz/guns/resource/modifier/AttachmentPropertyManager.class','shouldersurfing_plugin.json']
services=list(Path('modules/tacz_adapter/src/main/resources/META-INF/services').glob('*'))
required += ['META-INF/services/'+p.name for p in services]
required += [str(p.relative_to('src/main/java')).replace('.java','.class') for p in Path('src/main/java/com/tacz/guns').rglob('*.java') if p.stem in ('TargetBlock','TargetMinecart','StatueBlock')]
artifacts=[]
for p in sorted(Path('build/libs').glob('*-compat-slimming.20260924*.jar')):
 if p.name.endswith('-sources.jar'):continue
 with zipfile.ZipFile(p) as z:
  names=z.namelist();ns=set(names)
  # Exclude unrelated stale build outputs, never silently choose a stale archive.
  assert all(n in ns for n in required),(p,'missing',set(required)-ns)
  violations=[n for n in names if any(n==x+'.class' or n.startswith(x+'$') for x in retired) or n in ('kubejs.plugins.txt','kubejs.classfilter.txt')]
  assert not violations,(p,violations)
  for n in names:
   if n.endswith('.class') and (n.startswith('com/tacz/') or n.startswith('dev/tacticaltacz/')):
    b=z.read(n)
    assert not any(t in b for t in [b'dev/latvian/mods/',b'mezz/jei/',b'me/shedaniel/clothconfig2/',b'com/mrcrayfish/controllable/',b'com/mrcrayfish/framework/',b'KubeJSGunEventPoster',b'ControllableData',b'BindingContextMixin']),n
  mixins=json.loads(z.read('tacz.mixins.json'));assert 'common.BindingContextMixin' not in mixins['mixins']
  for s in services:assert z.read('META-INF/services/'+s.name)==s.read_bytes()
  indices=[n for n in names if n.startswith('data/tacz_fork_tarkov/index/guns/') and n.endswith('.json')]
  assert len(indices)==15,(p,len(indices))
  lua=[n for n in names if n.endswith('.lua')];assert lua
  m870='assets/tacz_fork_tarkov/display/guns/m870.json'
  assert z.read(m870)==Path('modules/tacz_adapter/weapon-content/resources/'+m870).read_bytes()
  assert 'controllable' in json.loads(z.read(m870))
  for n in names:
   if n.startswith('assets/tacz/lang/') and n.endswith('.json'):
    data=json.loads(z.read(n));assert not any(k.startswith(('jei.tacz.attachment_query.','gui.tacz.cloth_config_warning.')) or k=='key.tacz.open_config.desc' for k in data)
  nested=[n for n in names if n.startswith('META-INF/jarjar/') and n.endswith('.jar')]
  assert any('luaj' in n for n in nested),nested
  artifacts.append({'path':str(p),'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'managed_gun_indices':len(indices),'lua_files':len(lua),'required_entries':required,'nested_jars':nested,'retired_entries_absent':True,'retired_bytecode_references_absent':True})
assert len(artifacts)==2,len(artifacts)
protected=json.loads((out/'artifact-audit.json').read_text())['protected_locks_and_vendor_sha256']
for name,digest in protected.items():assert hashlib.sha256(Path(name).read_bytes()).hexdigest()==digest,name
report={'date':'2026-09-24','scope':'Offline artifact inspection only; no Minecraft acceptance','source_audit_sha256':hashlib.sha256((out/'source-audit.json').read_bytes()).hexdigest(),'artifacts':artifacts,'protected_locks_and_vendor_sha256':protected}
(out/'artifact-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps([{'path':a['path'],'bytes':a['bytes'],'guns':a['managed_gun_indices'],'lua':a['lua_files']} for a in artifacts],indent=2))
print('Both Jars pass retirement/retention checks; protected locks and vendor files unchanged')
