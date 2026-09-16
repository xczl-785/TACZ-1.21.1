"""Deterministic native M4A1 content and immutable rig-batch producer.
Original rig and resources are inputs only. High/low share physical definitions.
"""
from pathlib import Path
import json,re,copy,uuid,hashlib,math,collections
import numpy as np
from PIL import Image
R=Path(__file__).resolve().parents[2]
OUT=R/'modules/tacz_adapter/weapon-content/resources'
SRC=R/'src/main/resources/assets/tacz/custom/tacz_default_gun'
REVIEW=R/'docs/assembly-experiment/native-m4a1-review'
NS='tacz_assembly'; GUN='m4a1'; BASE=OUT/f'data/{NS}/m4a1'
def read(p):
 return json.loads(re.sub(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*[\s\S]*?\*/',lambda m:m[0] if m[0].startswith('"') else '',p.read_text()))
def write(p,v):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n')
audit=read(REVIEW/'audit.json');ownership=read(REVIEW/'ownership-proposal.json')['boneOwner']
attachments=[a for a in audit['attachments'] if a['tagAllowed'] and a['id'] not in {'tacz:ammo_mod_fmj','tacz:ammo_mod_hp','tacz:ammo_mod_i'}]
assert len(attachments)==52
external={a['id']:a['id'].replace(':','_') for a in attachments}
types={t:[external[a['id']] for a in attachments if a['type']==t] for t in {a['type'] for a in attachments}}
slots={
 'lower_receiver':{'upper':['upper_receiver'],'pistol_grip':['pistol_grip'],'buffer':['buffer'],'magazine':['magazine_standard']+types['extended_mag']},
 'upper_receiver':{'barrel_mount':['barrel_mount_collar'],'bolt':['bolt'],'charging_handle':['charging_mechanism'],'rear_sight':['rear_sight'],'scope':types['scope']},
 'barrel_mount_collar':{'barrel':['barrel'],'handguard':['handguard_default','handguard_tactical']},
 'barrel':{'gas':['gas_block_and_tube'],'muzzle':['muzzle_default']+[s for s in types['muzzle'] if s!='tacz_bayonet_m9']},
 'gas_block_and_tube':{'front_sight':['front_sight']},'buffer':{'stock':types['stock']},
 'muzzle_default':{'bayonet':['tacz_bayonet_m9']},
 'handguard_tactical':{'grip':types['grip'],'laser':types['laser']}}
physical=['lower_receiver','upper_receiver','barrel_mount_collar','barrel','gas_block_and_tube','front_sight','rear_sight','bolt','charging_mechanism','handguard_default','handguard_tactical','pistol_grip','buffer','magazine_standard','muzzle_default']
critical=[['upper'],['upper','barrel_mount'],['upper','barrel_mount','barrel'],['upper','barrel_mount','barrel','gas'],['upper','bolt'],['upper','charging_handle'],['buffer']]
catalog=[]
for name in physical+list(external.values()):
 entry={'id':name,'stats':{'weightKg':0,'ergonomics':0},'slots':[{'id':slot,'required':False,'allowedParts':allowed} for slot,allowed in slots.get(name,{}).items()],'conflictingParts':[]}
 if name=='lower_receiver':entry['weapon']={'recoilVertical':0,'recoilHorizontal':0,'centerOfImpact':0,'sightingRange':0}
 catalog.append(entry)
write(BASE/'catalog.json',{'schemaVersion':1,'parts':catalog})
mapping={p:f'{NS}:{GUN}' if p=='lower_receiver' else f'{NS}:m4a1_{p}' for p in physical};mapping.update({v:k for k,v in external.items()})
write(BASE/'mapping.json',mapping);write(BASE/'native_attachments.json',external)
preset={
 'lower_receiver':{'upper':'upper_receiver','pistol_grip':'pistol_grip','buffer':'buffer','magazine':'magazine_standard'},
 'upper_receiver':{'barrel_mount':'barrel_mount_collar','bolt':'bolt','charging_handle':'charging_mechanism','rear_sight':'rear_sight'},
 'barrel_mount_collar':{'barrel':'barrel','handguard':'handguard_default'},'barrel':{'gas':'gas_block_and_tube','muzzle':'muzzle_default'},
 'gas_block_and_tube':{'front_sight':'front_sight'},'buffer':{'stock':'tacz_stock_tactical_ar'}}
nodes=[]
def node(name,path,parent=None,slot=None):
 id=str(uuid.uuid5(uuid.NAMESPACE_URL,NS+'/'+GUN+'/'+path));v={'instanceId':id,'definitionId':name}
 if parent:v.update(parentId=parent,slot=slot)
 nodes.append(v)
 for s,c in preset.get(name,{}).items():node(c,path+'/'+s,id,s)
node('lower_receiver','');assert len(nodes)==15
write(BASE/'scene.json',{'schemaVersion':1,'nodes':nodes})
write(BASE/'weapon.json',{'schemaVersion':1,'gunId':f'{NS}:{GUN}','rootDefinition':'lower_receiver','resourceDirectory':'m4a1','modelType':'tacz_native_assembly','caliber':'556x45','magazinePath':['magazine'],'requiredPaths':critical,'defaultFireMode':'auto','feed':'detachable_magazine','nativeRig':True,'assemblyIcons':True,'developmentSource':'native_m4a1'})
write(BASE/'native-profile.json',read(R/'modules/tacz_adapter/weapon-sources/native_m4a1/native-profile.json'))
# Resource references intentionally inherit native actions and server feed times byte-for-byte semantically.
data=read(SRC/'data/tacz/data/guns/m4a1_data.json');write(OUT/f'data/{NS}/data/guns/m4a1.json',data)
index=read(SRC/'data/tacz/index/guns/m4a1.json');index.update(name=f'gun.{NS}.m4a1',display=f'{NS}:m4a1',data=f'{NS}:m4a1',item_type=f'{NS}:m4a1',sort=102);write(OUT/f'data/{NS}/index/guns/m4a1.json',index)
write(OUT/f'data/{NS}/tacz_tags/attachments/allow_attachments/m4a1.json',[a['id'] for a in attachments])
display=read(SRC/'assets/tacz/display/guns/m4a1_display.json');display.update(model_type='tacz_native_assembly',model=f'{NS}:gun/m4a1',lod={'model':f'{NS}:gun/lod/m4a1','texture':f'{NS}:gun/lod/m4a1'})
write(OUT/f'assets/{NS}/display/guns/m4a1.json',display)
hi=read(SRC/'assets/tacz/geo_models/gun/m4a1_geo.json');hb=hi['minecraft:geometry'][0]['bones'];by={b['name']:b for b in hb}
lo=read(SRC/'assets/tacz/geo_models/gun/lod/m4a1.json');lb=lo['minecraft:geometry'][0]['bones'];lby={b['name']:b for b in lb}
def under(name,parent):
 while name:
  if name==parent:return True
  name=by[name].get('parent')
 return False
def owner(name):
 o=ownership[name]
 if o=='handguard':return 'handguard_tactical' if under(name,'handguard_tactical') else 'handguard_default'
 if o=='muzzle':return 'muzzle_default'
 if o=='magazine':return {'mag_standard':'magazine_standard','mag_extended_1':'tacz_extended_mag_1','mag_extended_2':'tacz_extended_mag_2','extend_magazine':'tacz_extended_mag_3'}[name]
 return o
def variant(name):
 return 'folded' if owner(name)=='front_sight' and under(name,'sight_folded') else 'upright' if owner(name)=='front_sight' else 'always'
# Preserve every rig node and parent; attach immutable leaves for only that node's geometry.
manifest={};high=copy.deepcopy(hi);highbones=high['minecraft:geometry'][0]['bones'];leaves=[]
for b in highbones:
 if not b.get('cubes'):continue
 o=owner(b['name'])
 if o.startswith('presentation_'):continue
 cubes=b.pop('cubes')
 if o=='dormant_oem_stock_geometry':continue
 name='assembly_'+b['name'];leaves.append({'name':name,'parent':b['name'],'pivot':b['pivot'],'cubes':cubes})
 manifest[name]={'definition':o,'variant':variant(b['name']),'sourceBone':b['name']}
highbones.extend(leaves)
write(OUT/f'assets/{NS}/geo_models/gun/m4a1.json',high)
# Rebind the 47 semantically isolated old LOD cubes into their native moving bones.
S=np.array([1,-1,1])
def trans(v):m=np.eye(4);m[:3,3]=v;return m
def rot(v):
 x,y,z=np.radians(v);cx,sx=np.cos(x),np.sin(x);cy,sy=np.cos(y),np.sin(y);cz,sz=np.cos(z),np.sin(z)
 m=np.eye(4);m[:3,:3]=np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]]);return m
def matrix(name,lookup):
 b=lookup[name];parent=b.get('parent');p=np.array(b['pivot']);delta=p-np.array(lookup[parent]['pivot']) if parent else p
 return (matrix(parent,lookup) if parent else np.eye(4))@trans(S*delta)@rot(b.get('rotation',[0,0,0]))
def angles(m):
 y=math.asin(max(-1,min(1,-m[2,0])))
 if abs(math.cos(y))<1e-8:x=0;z=math.atan2(-m[0,1],m[1,1])
 else:x=math.atan2(m[2,1],m[2,2]);z=math.atan2(m[1,0],m[0,0])
 return np.degrees([x,y,z]).tolist()
reuse={'ld_upper':('upper_receiver','upper2'),'ld_toprails':('upper_receiver','body_rail'),'ld_portgroup':('upper_receiver','cap2'),'ld_magwell':('lower_receiver','lower2'),'ld_lower':('lower_receiver','lower2'),'ld_switch':('lower_receiver','selector'),'ld_trigger':('lower_receiver','group34'),'ld_grip':('pistol_grip','grip2'),'ld_buffer':('buffer','octagon2'),'sight':('rear_sight','rear_sight'),'ld_pull':('charging_mechanism','pull'),'ld_mag':('magazine_standard','mag_standard')}
low=copy.deepcopy(hi);lowbones=low['minecraft:geometry'][0]['bones']
for b in lowbones:b.pop('cubes',None)
texhi=Image.open(SRC/'assets/tacz/textures/gun/uv/m4a1.png').convert('RGBA');texlo=Image.open(SRC/'assets/tacz/textures/gun/lod/m4a1.png').convert('RGBA')
atlas=Image.new('RGBA',(max(texhi.width,texlo.width),texhi.height+texlo.height));atlas.paste(texhi,(0,0));atlas.paste(texlo,(0,texhi.height))
def uvshift(c):
 if isinstance(c['uv'],list):c['uv'][1]+=texhi.height
 else:
  for face in c['uv'].values():face['uv'][1]+=texhi.height
errors=[];reused=0
for name,(definition,target) in reuse.items():
 b=lby[name];oldpivot=np.array(b['pivot']);local=np.linalg.inv(matrix(target,by))@matrix(name,lby);newpivot=np.array(by[target]['pivot'])+S*local[:3,3];offset=newpivot-oldpivot
 cubes=copy.deepcopy(b.get('cubes',[]));reused+=len(cubes)
 for c in cubes:
  c['origin']=(np.array(c['origin'])+offset).tolist()
  if 'pivot' in c:c['pivot']=(np.array(c['pivot'])+offset).tolist()
  uvshift(c)
 newname='assembly_lod_'+name;bone={'name':newname,'parent':target,'pivot':newpivot.tolist(),'rotation':angles(local),'cubes':cubes};lowbones.append(bone)
 lookup={**by,newname:bone}
 errors.append(float(np.max(np.abs(matrix(newname,lookup)-matrix(name,lby)))))
 manifest[newname]={'definition':definition,'variant':'always','sourceBone':name}
assert reused==47 and max(errors)<1e-10
# For replaced mixed regions retain native per-bone surface cubes, reducing detail without mixing owners.
reusedowners={v[0] for v in reuse.values()};derived=[]
for b in hb:
 if not b.get('cubes'):continue
 o=owner(b['name'])
 if o in reusedowners or o=='dormant_oem_stock_geometry':continue
 if o=='presentation_hands':continue # third-person/ground LOD has no first-person hands
 if o=='presentation_rounds':
  # These remain presentation-only and retain their native visibility/animation ancestors.
  lowtarget=next(n for n in lowbones if n['name']==b['name']);lowtarget['cubes']=copy.deepcopy(b['cubes'][:1]);continue
 cubes=sorted(enumerate(b['cubes']),key=lambda pair:-sum(float(pair[1]['size'][i])*float(pair[1]['size'][j]) for i,j in [(0,1),(0,2),(1,2)]))[:2]
 selected=[copy.deepcopy(c) for i,c in sorted(cubes)]
 name='assembly_lod_derived_'+b['name'];lowbones.append({'name':name,'parent':b['name'],'pivot':b['pivot'],'cubes':selected})
 manifest[name]={'definition':o,'variant':variant(b['name']),'sourceBone':b['name']};derived.append({'bone':b['name'],'definition':o,'retainedIndices':[i for i,c in sorted(cubes)],'inputCubes':len(b['cubes']),'outputCubes':len(selected)})
desc=low['minecraft:geometry'][0]['description'];desc['texture_width']=atlas.width;desc['texture_height']=atlas.height
write(OUT/f'assets/{NS}/geo_models/gun/lod/m4a1.json',low)
p=OUT/f'assets/{NS}/textures/gun/lod/m4a1.png';p.parent.mkdir(parents=True,exist_ok=True);atlas.save(p)
write(BASE/'batches.json',manifest)
write(BASE/'geometry-evidence.json',{'nativeRigBones':len(hb),'highCubes':sum(len(b.get('cubes',[])) for b in highbones),'lowCubes':sum(len(b.get('cubes',[])) for b in lowbones),'reusedLowCubes':reused,'maximumNeutralMatrixError':max(errors),'derivedRegions':derived,'lodPolicy':'47 isolated old cubes rebound; mixed regions replaced by largest native surfaces per original bone, at most two; no baked stock; physical attachment uses native renderer','sourceHashes':audit['sourceHashes'],'textureAtlas':{'high':[0,0,texhi.width,texhi.height],'oldLow':[0,texhi.height,texlo.width,texlo.height]},'limits':['No game, animation playback or FPS measurement; LOD surface reduction requires owner visual acceptance']})
for locale in ['en_us','zh_cn']:
 labels={f'item.{NS}.{id.split(":")[1]}':name.replace('_',' ').capitalize() for name,id in mapping.items() if not id.startswith('tacz:')}
 labels[f'gun.{NS}.m4a1']='M4A1 · Native Assembly' if locale=='en_us' else 'M4A1 · 原生实体组装'
 write(OUT/f'assets/{NS}/lang/{locale}.json',labels)
print('Generated native M4A1:',len(nodes),'default physical nodes;',len(attachments),'native candidates;',reused,'rebound LOD cubes; error',max(errors))
labels={'lower_receiver':'下机匣','upper_receiver':'上机匣','barrel_mount_collar':'枪管连接环','barrel':'枪管','gas_block_and_tube':'导气总成','front_sight':'前瞄具','rear_sight':'后瞄具','bolt':'枪机总成','charging_mechanism':'拉机柄','handguard_default':'标准护木','handguard_tactical':'导轨护木','pistol_grip':'手枪握把','buffer':'缓冲管','magazine_standard':'标准30发弹匣','muzzle_default':'默认枪口装置'}
p=OUT/f'assets/{NS}/lang/zh_cn.json';data=read(p)
for d,label in labels.items():data['item.'+mapping[d].replace(':','.')]=label
# Root name describes the gun, even though its physical root is the lower receiver.
data[f'item.{NS}.m4a1']='M4A1 · 原生实体组装';data['tacz_assembly.workbench']='打开组装工作台';write(p,data)
p=OUT/f'assets/{NS}/lang/en_us.json';data=read(p);data['tacz_assembly.workbench']='Assembly workbench';write(p,data)
identity_types={'lower_receiver':'weapon/firearm/assault_rifle','upper_receiver':'weapon_mod/receiver','barrel':'weapon_mod/barrel','gas_block_and_tube':'weapon_mod/gas_block','front_sight':'weapon_mod/sight/iron','rear_sight':'weapon_mod/sight/iron','handguard_default':'weapon_mod/handguard','handguard_tactical':'weapon_mod/handguard','pistol_grip':'weapon_mod/pistol_grip','buffer':'weapon_mod/stock','charging_mechanism':'weapon_mod/charging_handle','magazine_standard':'weapon_mod/magazine','muzzle_default':'weapon_mod/muzzle/brake','barrel_mount_collar':'weapon_mod/receiver','bolt':'weapon_mod/receiver'}
write(OUT/f'data/{NS}/item_foundation/identities/m4a1.json',{'schema_version':1,'items':[{'item':mapping[d],'tags':['item_foundation:type/'+identity_types[d]]} for d in physical]})
for d in physical:
 v={'schema_version':3,'item':mapping[d],'footprint':[5,2] if d=='lower_receiver' else [2,1] if d in ['barrel','upper_receiver','handguard_default','handguard_tactical','charging_mechanism','buffer'] else [1,1],'weight_kg':0}
 if d=='lower_receiver':v['wearable_slots']=['tactical_inventory:primary_weapon_1','tactical_inventory:primary_weapon_2']
 write(OUT/f'data/{NS}/item_foundation/items/{mapping[d].split(":")[1]}.json',v)
# Foundation owns serialization/structural safety. Specific native model rules stay in this catalog.
write(OUT/f'data/{NS}/assembly/m4a1.json',{'schemaVersion':1,'items':[{'itemId':mapping[d],'slots':[{'id':slot,'compatibleItems':sorted({'tacz:attachment' if mapping[child] in external else mapping[child] for child in allowed}),'requiredSiblingSlots':[],'conflictingSiblingSlots':[],'toggleable':False} for slot,allowed in slots.get(d,{}).items()]} for d in physical]})

for locale,text in [('zh_cn','FMJ/HP/I 效果附件不兼容；前握把和激光需要导轨护木；M9 需要默认枪口装置。'),('en_us','FMJ/HP/I effect attachments are incompatible. Grips/lasers need the rail handguard; M9 needs the default muzzle.')]:
 p=OUT/f'assets/{NS}/lang/{locale}.json';data=read(p);data['tacz_assembly.restrictions']=text;write(p,data)
for locale,text in [('zh_cn','使用原生枪械属性；此处不换算为塔科夫后坐力数值。'),('en_us','Native gun properties apply; no conversion to Tarkov recoil units.')]:
 p=OUT/f'assets/{NS}/lang/{locale}.json';data=read(p);data['tacz_assembly.native_stats']=text;write(p,data)

# Standard editable components share the Radian workbench and icon producer.
from build_workbench import build as build_workbench
build_workbench()

# Editable default components are the final geometry/texture authority. Native rig,
# non-default candidates and presentation-only bones remain the preserved inputs.
from editable_import import build as build_editable
build_editable()
