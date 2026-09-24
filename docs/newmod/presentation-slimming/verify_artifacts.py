#!/usr/bin/env python3
"""Verify slim4 removals, dev isolation, and preserved resources against the accepted slim3 pair."""
from pathlib import Path
import hashlib,json,re,subprocess,sys,zipfile
R=Path(__file__).resolve().parents[3];O=Path(__file__).resolve().parent
VERSION=sys.argv[1];SOURCE=sys.argv[2];BASE='799a6683c96f618b3d5efb897e4c297cd09a8a13'
def git(*args):return subprocess.check_output(['git',*args],cwd=R,text=True)
assert not git('diff','--name-only',SOURCE,'--','src','modules','tools','build-logic','vendor').strip(),'Source differs from the built revision'
audit=json.loads((O/'source-audit.json').read_text())
retired=[]
for row in audit['files']:
 p=row['path']
 if row['after_sha256'] is None and p.endswith('.java') and '/java/' in p:retired.append(p.split('/java/',1)[1][:-5])
devonly=['com/tacz/guns/command/sub/DebugCommand','com/tacz/guns/debug/GunMeleeDebug']
retired=[c for c in retired if c not in devonly]
required=['com/tacz/guns/entity/EntityKineticBullet.class','com/tacz/guns/client/event/ClientHitMark.class',
'com/tacz/guns/network/message/event/ServerMessageGunKill.class','com/tacz/guns/resource/BuiltinGunPack.class',
'com/tacz/guns/util/ExplodeUtil.class','com/tacz/guns/entity/shooter/LivingEntityHeat.class',
'com/tacz/guns/command/sub/DummyAmmoCommand.class','com/tacz/guns/command/sub/AttachmentLockCommand.class',
'com/tacz/guns/event/LoadingConfigEvent.class','dev/tacticaltacz/assembled/AssemblyGunProvider.class',
'dev/tacticaltacz/assembled/NativeAssemblyView.class','dev/tacticaltacz/assembled/NativeAssemblyIcons.class']
rows=[]
for suffix in ('','-development'):
 p=R/'build/libs'/f'tacz-neoforge-1.21.1-{VERSION}{suffix}.jar'
 oldpath=R.parent/'source/vendor/tacz'/f'tacz-neoforge-1.21.1-1.1.8-hotfix-r6-slim3-newmod.963361c2{suffix}.jar'
 with zipfile.ZipFile(p) as z,zipfile.ZipFile(oldpath) as old:
  names=set(z.namelist());oldnames=set(old.namelist());assert all(n in names for n in required)
  assert 'META-INF/services/com.tacz.guns.api.extension.AssemblyEntryExtension' not in names
  for n in names:
   assert not any(n==c+'.class' or n.startswith(c+'$') for c in retired),n
   assert not ('/modifier/custom/' in n and 'JsonProperty' in n),n
   if n.endswith('.class'):
    b=z.read(n);assert not any(c.encode() in b for c in retired),n
    if not suffix:assert not any(c.encode() in b for c in devonly),n
  for c in devonly:
   assert (c+'.class' in names)==bool(suffix)
   if suffix:assert b'Lnet/neoforged/fml/common/EventBusSubscriber;' in z.read(c+'.class')
  assert b'1.0.5-slim4' in z.read('com/tacz/guns/network/NetworkHandler.class')
  bullet=z.read('com/tacz/guns/entity/EntityKineticBullet.class')
  assert all(s not in bullet for s in [b'igniteEntity',b'igniteBlock',b'igniteEntityTime',b'BaseFireBlock'])
  assert b'ExplodeUtil' in bullet
  preserved=0;updated=[]
  for n in oldnames:
   if n.endswith('/'):continue
   if n.endswith(('.png','.ogg','.lua')):
    assert old.read(n)==z.read(n),n;preserved+=1
   elif n.startswith(('assets/tacz/custom/tacz_default_gun/','data/tacz_fork_tarkov/','assets/tacz_fork_tarkov/')):
    assert n in names,n
    if old.read(n)!=z.read(n):
     candidates=[R/'src/main/resources'/n,R/'modules/tacz_adapter/weapon-content/resources'/n]
     source=next((x for x in candidates if x.is_file()),None);assert source and z.read(n)==source.read_bytes(),n;updated.append(n)
    else:preserved+=1
  guns=[n for n in names if n.startswith('data/tacz_fork_tarkov/index/guns/') and n.endswith('.json')]
  physical=[n for n in names if n.startswith('data/tacz_fork_tarkov/item_foundation/items/') and n.endswith('.json')]
  assert len(guns)==15 and len(physical)==146
  assert not any(n.startswith('dev/firearms/') for n in names)
  assert not any(n.endswith(('/geometry-evidence.json','/workbench-anchors.json','/authoring-contract.json')) and n.startswith('data/tacz_fork_tarkov/') for n in names)
  rows.append(dict(path=str(p.relative_to(R)),bytes=p.stat().st_size,sha512=hashlib.sha512(p.read_bytes()).hexdigest(),bytes_removed=oldpath.stat().st_size-p.stat().st_size,unchanged_resource_files=preserved,updated_resource_files=updated,managed_guns=len(guns),physical_items=len(physical),development_debug_present=bool(suffix)))
(O/'artifact-audit.json').write_text(json.dumps(dict(date='2026-09-24',source_commit=SOURCE,version=VERSION,baseline=BASE,artifacts=rows,minecraft_acceptance='pending'),indent=2)+'\n')
for row in rows:print({k:v for k,v in row.items() if k!='updated_resource_files'},'updated resources',len(row['updated_resource_files']))
