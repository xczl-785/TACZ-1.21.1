"""Consume standard Blockbench components through the existing Radian mesh/icon pipeline.
Only workbench and item appearance; native held rig, item identities and assembly tree stay intact.
"""
from pathlib import Path
import json,sys,shutil
import numpy as np
from mount_source import load_mounts
R=Path(__file__).resolve().parents[2]
MODULE=R/'modules/tacz_adapter'
sys.path.insert(0,str(MODULE/'tools'))
from build_weapon import convert_component
from render_part_icon import render_part_icon
SOURCE=MODULE/'weapon-sources/native_m4a1'
OUT=MODULE/'weapon-content/resources'
BASE=OUT/'data/tacz_fork_tarkov/m4a1'
ASSETS=OUT/'assets/tacz_fork_tarkov'
def read(p):return json.loads(p.read_text())
def write(p,v):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,separators=(',',':'))+'\n')
def build():
 manifest=read(SOURCE/'manifest.json');parts={p['definitionId']:p for p in manifest['parts']}
 catalog=read(BASE/'catalog.json')['parts'];mapping=read(BASE/'mapping.json')
 assert set(parts)==set(mapping)
 mounts=load_mounts(SOURCE/'mounts.json',catalog)
 anchors={d:np.asarray(frame['frameOrigin'],dtype=float) for d,frame in mounts.items()}
 errors=[]
 models=[];library={'schemaVersion':1,'materials':{}};bindings={'schemaVersion':1,'defaultMaterial':'lower_receiver','parts':{}}
 for part in catalog:
  d=part['id'];entry=parts[d];model=read(SOURCE/entry['model']);anchor=anchors[d]
  meshes=convert_component(model,entry['local_to_assembly'],anchor)
  # Rebasing may move UI handles, never the assembled surfaces or texture coordinates.
  original=convert_component(model,entry['local_to_assembly'],np.asarray(entry['anchor']))
  for before,after in zip(original,meshes):
   for a,b in zip(before['triangles'],after['triangles']):
    error=float(np.max(np.abs(np.asarray(a['vertices'])+entry['anchor']-(np.asarray(b['vertices'])+anchor))))
    assert error<1e-7,(d,error)
    assert a['uv']==b['uv']
    errors.append(error)
  frame=mounts[d]
  view={'definitionId':d,'attachmentOrigin':frame['attachmentOrigin'],'slots':frame['slots'],'boxes':[],'meshes':meshes};models.append(view)
  texture=f'tacz_fork_tarkov:textures/parts/{d}.png'
  target=ASSETS/f'textures/parts/{d}.png';target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(SOURCE/entry['texture'],target)
  library['materials'][d]={'baseColor':'#ffffff','texture':texture,'roughness':.8,'specular':.12,'textureScale':1}
  bindings['parts'][d]={'defaultMaterial':d,'regions':{}}
 write(BASE/'workbench-anchors.json',{'schemaVersion':1,'policy':'Explicit author frames from mounts.json; independent of mesh bounds and candidate order; native motion pivots preserved','maximumRebaseError':max(errors),'anchors':{d:v.tolist() for d,v in anchors.items()}})
 write(BASE/'preview.json' ,{'schemaVersion':3,'models':models});write(BASE/'library.json',library);write(BASE/'materials.json',bindings)
 edited_parts={p['definitionId'] for p in read(SOURCE/'editable/manifest.json')['parts']}
 root_definition=read(BASE/'weapon.json')['rootDefinition']
 for source,target in [('preview.json','icon_geometry.json'),('library.json','icon_library.json'),('materials.json','icon_materials.json')]:write(ASSETS/'m4a1'/target,read(BASE/source))
 for model in models:
  d=model['definitionId'];render_part_icon(model,library,bindings,lambda res:OUT/'assets'/res.replace(':','/'),muzzle_left=d in edited_parts,alpha_cutout=d in edited_parts).save(ASSETS/f'textures/item/{d}.png')
  if not mapping[d].startswith('tacz:'):
   write(ASSETS/f'models/item/{mapping[d].split(":")[1]}.json',{'parent':'builtin/entity','gui_light':'front'} if d==root_definition else {'parent':'minecraft:item/generated','textures':{'layer0':f'tacz_fork_tarkov:item/{d}'}})
 labels={
 'upper':('机匣','Receiver'),'pistol_grip':('手枪握把','Pistol grip'),'buffer':('缓冲管','Buffer tube'),'magazine':('弹匣','Magazine'),
 'barrel_mount':('枪管连接环','Barrel collar'),'bolt':('枪机','Bolt'),'charging_handle':('拉机柄','Charging handle'),'rear_sight':('后照门','Rear sight'),
 'scope':('瞄具','Sight'),'barrel':('枪管','Barrel'),'handguard':('护木','Handguard'),'gas':('导气总成','Gas assembly'),
 'muzzle':('枪口','Muzzle'),'front_sight':('前准星','Front sight'),'stock':('枪托','Stock'),'bayonet':('刺刀','Bayonet'),'grip':('前握把','Foregrip'),'laser':('战术配件','Tactical device')}
 for index,locale in enumerate(['zh_cn','en_us']):
  p=ASSETS/f'lang/{locale}.json';data=read(p)
  for slot,names in labels.items():data['weapon_assembly_ui.slot.'+slot]=names[index]
  write(p,data)
 print('Standard workbench parts:',len(models),'textured components; Radian converter and icon renderer; +Z muzzle')
if __name__=='__main__':build()
