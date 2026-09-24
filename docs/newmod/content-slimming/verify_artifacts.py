#!/usr/bin/env python3
"""Audit the shipped pair against slim2, including retired classes and retained gun assets."""
from pathlib import Path
import hashlib,json,re,subprocess,sys,zipfile
ROOT=Path(__file__).resolve().parents[3]
OUT=Path(__file__).resolve().parent
VERSION=sys.argv[1] if len(sys.argv)>1 else '1.1.8-hotfix-r6-slim3-newmod.963361c2'
BASE='deb3fb91a9e4853f9a15a80da536e7530d38c22c'
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT,text=True)
deleted=git('diff','--diff-filter=D','--name-only',BASE,'--','src','modules').splitlines()
classes=[re.sub(r'^.*?/java/','',p)[:-5] for p in deleted if '/java/' in p and p.endswith('.java')]
resources={p.removeprefix('src/main/resources/') for p in deleted if p.startswith('src/main/resources/')}
required=['com/tacz/guns/resource/BuiltinGunPack.class','com/tacz/guns/resource/GunPackLoader.class',
'com/tacz/guns/entity/EntityKineticBullet.class','com/tacz/guns/client/event/ClientHitMark.class',
'com/tacz/guns/entity/shooter/LivingEntityHeat.class','com/tacz/guns/client/model/functional/BeamRenderer.class',
'dev/tacticaltacz/assembled/AssemblyGunWorkbench.class','dev/tacticaltacz/assembled/AssemblyGunProvider.class',
'dev/tacticaltacz/assembled/NativeAssemblyView.class','dev/tacticaltacz/assembled/NativeAssemblyIcons.class',
'dev/tacticaltacz/assembled/NativeAssemblyGunModel.class','data/tacz/tags/block/bullet_ignore.json','data/tacz/recipe/gunpowder.json']
rows=[]
for suffix in ('','-development'):
 path=ROOT/'build/libs'/f'tacz-neoforge-1.21.1-{VERSION}{suffix}.jar'
 oldpath=ROOT.parent/'source/vendor/tacz'/f'tacz-neoforge-1.21.1-1.1.8-hotfix-r6-slim2-newmod.963361c2{suffix}.jar'
 with zipfile.ZipFile(path) as z,zipfile.ZipFile(oldpath) as old:
  names=set(z.namelist());oldnames=set(old.namelist())
  assert all(n in names for n in required)
  assert not names.intersection(resources)
  for n in names:
   assert not any(n==c+'.class' or n.startswith(c+'$') for c in classes),n
   if n.endswith('.class'):
    b=z.read(n)
    assert not any(c.encode() in b for c in classes),n
  for n in ['com/tacz/guns/resource/GunPackLoader.class','com/tacz/guns/resource/BuiltinGunPack.class']:
   assert not any(x in z.read(n) for x in [b'FMLPaths',b'ZipFile',b'EXTRA_ENTRIES',b'copyModDirectory',b'scanExtensions'])
  kept=0
  for n in oldnames:
   if n in resources or n.endswith('/'):continue
   if n.startswith('assets/tacz/custom/tacz_default_gun/') or n.endswith(('.png','.ogg','.lua')) or n.startswith(('data/tacz_fork_tarkov/','assets/tacz_fork_tarkov/')):
    assert n in names,n
    assert old.read(n)==z.read(n),n
    kept+=1
  indexes=[n for n in names if n.startswith('data/tacz_fork_tarkov/index/guns/') and n.endswith('.json')]
  assert len(indexes)==15,len(indexes)
  assert not any(n.startswith('dev/firearms/') for n in names)
  recipes=sorted(n for n in names if n.startswith('data/tacz/recipe/') and n.endswith('.json'))
  assert recipes==['data/tacz/recipe/gunpowder.json'],recipes
  for n in names:
   if n.startswith('assets/tacz/lang/') and n.endswith('.json'):
    keys=json.loads(z.read(n));assert not any(k.startswith(('message.tacz.converter.','message.tacz.convert_from_legacy','toast.tacz.','commands.tacz.reload.overwrite')) for k in keys)
  rows.append(dict(path=str(path.relative_to(ROOT)),bytes=path.stat().st_size,sha512=hashlib.sha512(path.read_bytes()).hexdigest(),bytes_removed=oldpath.stat().st_size-path.stat().st_size,retained_resource_files=kept,managed_gun_indexes=len(indexes),retired_class_roots=len(classes),retired_resource_files=len(resources)))
report=dict(date='2026-09-24',source_commit=git('rev-parse','HEAD').strip(),baseline=BASE,version=VERSION,artifacts=rows,minecraft_acceptance='not run')
(OUT/'artifact-audit.json').write_text(json.dumps(report,indent=2)+'\n')
for row in rows:print(row)
