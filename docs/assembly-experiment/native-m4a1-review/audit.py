"""Read-only native M4A1 audit. Writes only this report directory; never edits assets."""
from pathlib import Path
import hashlib,json,math,re,collections,subprocess
import numpy as np
from PIL import Image,ImageDraw,ImageFont
OUT=Path(__file__).resolve().parent;ROOT=OUT.parents[2]
PACK=ROOT/'src/main/resources/assets/tacz/custom/tacz_default_gun';A=PACK/'assets/tacz';D=PACK/'data/tacz'
PILOT=ROOT.parent/'docs/参考资料/tacz-m4a1-assembly-pilot/output'
read_hashes={}
def read(p):
 read_hashes[str(p.resolve())]=hashlib.sha256(p.read_bytes()).hexdigest()
 s=p.read_text();s=re.sub(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*[\s\S]*?\*/',lambda m:m[0] if m[0].startswith('"') else '',s)
 return json.loads(s)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
paths={'model':A/'geo_models/gun/m4a1_geo.json','animation':A/'animations/m4a1.animation.json','texture':A/'textures/gun/uv/m4a1.png','display':A/'display/guns/m4a1_display.json'}
pilot=read(PILOT/'report.json');assert {k:sha(p) for k,p in paths.items()}==pilot['sourceHashes']
geo=read(paths['model']);assert geo==read(PILOT/'reassembled.geo.json');bones=geo['minecraft:geometry'][0]['bones'];by={b['name']:b for b in bones};owners={r['bone']:r['part'] for r in pilot['cubeOwnership']}
S=np.array([1,-1,1]);matrices={}
def trans(v):m=np.eye(4);m[:3,3]=v;return m
def rot(v):
 x,y,z=np.radians(v);cx,sx=np.cos(x),np.sin(x);cy,sy=np.cos(y),np.sin(y);cz,sz=np.cos(z),np.sin(z)
 m=np.eye(4);m[:3,:3]=np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
 return m
def matrix(name):
 if name not in matrices:
  b=by[name];p=np.array(b['pivot']);parent=b.get('parent');delta=p-np.array(by[parent]['pivot']) if parent else p
  matrices[name]=(matrix(parent) if parent else np.eye(4))@trans(S*delta)@rot(b.get('rotation',[0,0,0]))
 return matrices[name]
faces=[[0,1,3,2],[4,6,7,5],[0,4,5,1],[2,3,7,6],[0,2,6,4],[1,5,7,3]]
vertices={};rows=[]
for b in bones:
 verts=[]
 for i,c in enumerate(b.get('cubes',[])):
  pivot=np.array(c.get('pivot',b['pivot']));m=matrix(b['name'])@trans(S*(pivot-np.array(b['pivot'])))@rot(c.get('rotation',[0,0,0]))
  o=np.array(c['origin']);size=np.array(c['size']);inflate=c.get('inflate',0)
  corners=[o+size*np.array([x,y,z])+inflate*(2*np.array([x,y,z])-1) for x in [0,1] for y in [0,1] for z in [0,1]]
  v=np.array([(m@np.append(S*(corner-pivot),1))[:3]*S for corner in corners]);verts.append(v)
 vertices[b['name']]=verts
 if verts:
  allv=np.concatenate(verts);rows.append({'bone':b['name'],'parent':b.get('parent'),'cubes':len(verts),'oldPartition':owners[b['name']],'worldBounds':[allv.min(axis=0).round(6).tolist(),allv.max(axis=0).round(6).tolist()],'cubeSha256':hashlib.sha256(json.dumps(b['cubes'],sort_keys=True).encode()).hexdigest()})
# Offline geometry-only plates: native bone/cube transforms, no animation/texture/client claims.
rem=[r for r in rows if r['oldPartition']=='receiver_remainder']
font=ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial.ttf',18)
small=ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial.ttf',14)
im=Image.new('RGB',(2000,math.ceil(len(rem)/4)*280),'#f3f5f7');draw=ImageDraw.Draw(im)
for index,row in enumerate(rem):
 x0=(index%4)*500;y0=(index//4)*280;draw.text((x0+12,y0+8),row['bone']+' ('+str(row['cubes'])+' cubes)',fill='#142638',font=font)
 vv=vertices[row['bone']];allv=np.concatenate(vv);center=(allv.min(axis=0)+allv.max(axis=0))/2
 # Project mostly from side, slight x exposure, for physical position identification.
 camera=np.array([[0.35,0,-1],[0.12,-1,0],[1,0.12,0.35]])
 projected=[(v-center)@camera.T for v in vv];points=np.concatenate(projected);extent=np.ptp(points[:,:2],axis=0);scale=min(450/max(extent[0],.5),205/max(extent[1],.5))
 polys=[]
 for v in projected:
  for face in faces:
   f=v[face];polys.append((f[:,2].mean(),[(x0+250+p[0]*scale,y0+145+p[1]*scale) for p in f]))
 for depth,poly in sorted(polys,key=lambda p:p[0]):draw.polygon(poly,fill='#7fa7b9',outline='#244859',width=1)
 lo,hi=row['worldBounds'];draw.text((x0+12,y0+254),f'XYZ {tuple(round(a,2) for a in lo)} .. {tuple(round(a,2) for a in hi)}',fill='#304455',font=small)
im.save(OUT/'receiver-groups.png')
animations=[]
for name,a in read(paths['animation'])['animations'].items():
 animations.append({'name':name,'length':a.get('animation_length'),'loop':a.get('loop'), 'targets':list(a.get('bones',{})), 'channels':{b:list(v) for b,v in a.get('bones',{}).items()},'soundEffects':a.get('sound_effects',{})})
# Resolve the exact retained attachment catalog and nested allow tags, preserving provenance.
tagroot=D/'tacz_tags/attachments'
def resolve(values,seen=()):
 out=set()
 for v in values:
  if v.startswith('#'):
   assert v not in seen
   out|=resolve(read(tagroot/(v.split(':')[1]+'.json')),seen+(v,))
  else:out.add(v)
 return out
allowed=resolve(read(tagroot/'allow_attachments/m4a1.json'));gun=read(D/'data/guns/m4a1_data.json');attachments=[]
for p in sorted((D/'index/attachments').glob('*.json')):
 idx=read(p);id='tacz:'+p.stem;data=read(D/('data/attachments/'+idx['data'].split(':')[1]+'.json'));display=read(A/('display/attachments/'+idx['display'].split(':')[1]+'.json'))
 attachments.append({'id':id,'type':data.get('type',idx.get('type')),'tagAllowed':id in allowed,'data':data,'display':display})
report={'sourceHashes':{k:sha(p) for k,p in paths.items()},'pilotMatches':True,'bones':len(bones),'cubes':sum(len(b.get('cubes',[])) for b in bones),'receiverCubes':sum(r['cubes'] for r in rem),'geometry':rows,'animations':animations,'anchors':[{k:b[k] for k in ['name','parent','pivot','rotation'] if k in b} for b in bones if not b.get('cubes') and ('pos' in b['name'] or 'view' in b['name'] or b['name'] in ['shell','muzzle_flash','additional_magazine','camera'])],'attachments':attachments,'allowTypes':gun['allow_attachment_types'],'allowedTagIds':sorted(allowed),'limits':['Offline geometry projection, not textured render or animation playback','Physical naming inferred from geometry and hierarchy; decisions documented separately']}
(OUT/'audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print('PASS',report['bones'],report['cubes'],report['receiverCubes'],len(animations),len(attachments))
# Evidence-backed ownership proposal. Geometric memberships do not rewrite rig parents.
receiver_groups={
 'lower_receiver':['gun_body','lower2','deco2','bolt_release','mag_release','group34','selector','octagon42'],
 'upper_receiver':['upper2','cap2','group35','group31','body_rail'],
 'gas_block_and_tube':['sight2','fore_sight3'],
 'barrel_mount_collar':['bone38','bone61','bone62','bone70']}
assert set(sum(receiver_groups.values(),[]))=={r['bone'] for r in rem}
assert len(sum(receiver_groups.values(),[]))==len(rem)
newowner={b:part for part,items in receiver_groups.items() for b in items}
def under(name,root):
 while name:
  if name==root:return True
  name=by[name].get('parent')
 return False
for r in rows:
 name=r['bone']
 if name in newowner:continue
 if under(name,'rear_sight'):newowner[name]='rear_sight'
 elif under(name,'fore_sights') or under(name,'fore_sights2'):newowner[name]='front_sight'
 elif r['oldPartition']=='stock':newowner[name]='dormant_oem_stock_geometry'
 elif r['oldPartition'].startswith('handguard_'):newowner[name]='handguard'
 elif r['oldPartition'].startswith('magazine_'):newowner[name]='magazine'
 else:newowner[name]=r['oldPartition']
counts=collections.Counter()
for r in rows:counts[newowner[r['bone']]]+=r['cubes']
assert sum(counts.values())==1006
proposal={'receiverGroups':{p:{'bones':ns,'cubes':sum(len(by[n].get('cubes',[])) for n in ns)} for p,ns in receiver_groups.items()},'allGeometryCounts':dict(counts),'boneOwner':newowner,'animationOwnership':[{**a,'affectedGeometry':sorted({newowner[n] for n in newowner if any(under(n,target) for target in a['targets'])})} for a in animations],'nativeDefault':{'magazineLevel':0,'handguard':'default','scope':'none','muzzle':'default','stock':'none (no builtin or builder attachment)','hiddenOemStock':68},'pending':'Owner must approve physical grouping, loadout and state projection before production changes'}
(OUT/'ownership-proposal.json').write_text(json.dumps(proposal,ensure_ascii=False,indent=2)+'\n')
# Default untextured orthographic reference; excludes alternate/dormant/presentation geometry.
keep=[r for r in rows if r['oldPartition'] not in ['stock','presentation_hands','presentation_rounds','handguard_tactical','sights_folded','magazine_extended_1','magazine_extended_2','magazine_extended_3']]
im=Image.new('RGB',(1600,500),'#f3f5f7');draw=ImageDraw.Draw(im);allv=np.concatenate([v for r in keep for v in vertices[r['bone']]]);center=(allv.min(axis=0)+allv.max(axis=0))/2
palette=['#597f9c','#d38a48','#4d9b80','#ae6e92','#9c9452','#986db1','#719cc0','#bd745d','#747b8b','#3f918c','#4c7089','#9c5656'];kinds=sorted({newowner[r['bone']] for r in keep});colors={k:palette[i%len(palette)] for i,k in enumerate(kinds)}
camera=np.array([[0,0,-1],[0,-1,0],[1,0,0]]);extent=np.ptp((allv-center)@camera.T,axis=0);scale=min(1450/extent[0],320/extent[1]);polys=[]
for r in keep:
 for v in vertices[r['bone']]:
  projected=(v-center)@camera.T
  for face in faces:
   f=projected[face];polys.append((f[:,2].mean(),[(800+p[0]*scale,225+p[1]*scale) for p in f],colors[newowner[r['bone']]]))
for _,poly,col in sorted(polys,key=lambda p:p[0]):draw.polygon(poly,fill=col,outline='#243b48',width=1)
draw.text((20,10),'Native M4A1 unmodified default geometry (no attachments / no texture / no animation)',fill='#142638',font=font)
for i,k in enumerate(kinds):
 x=20+(i%5)*310;y=417+(i//5)*25;draw.rectangle((x,y,x+15,y+15),fill=colors[k]);draw.text((x+20,y),k,fill='#23394b',font=small)
im.save(OUT/'native-default-ownership.png')
print('Receiver resolved:',{k:v['cubes'] for k,v in proposal['receiverGroups'].items()})

assert len(animations)==17 and len(attachments)==85
assert not {t for a in animations for t in a['targets']} - set(by)
assert len(allowed)==55 and allowed <= {a['id'] for a in attachments}
source_paths=[
'src/main/java/com/tacz/guns/client/model/BedrockGunModel.java',
'src/main/java/com/tacz/guns/client/model/bedrock/BedrockModel.java',
'src/main/java/com/tacz/guns/client/model/bedrock/BedrockPart.java',
'src/main/java/com/tacz/guns/client/model/BedrockAttachmentModel.java',
'src/main/java/com/tacz/guns/client/model/functional/AttachmentRender.java',
'src/main/java/com/tacz/guns/api/item/nbt/GunItemDataAccessor.java',
'src/main/java/com/tacz/guns/api/item/gun/AbstractGunItem.java',
'src/main/java/com/tacz/guns/api/item/builder/GunItemBuilder.java',
'src/main/java/com/tacz/guns/util/AllowAttachmentTagMatcher.java',
'src/main/java/com/tacz/guns/util/AttachmentDataUtils.java',
'src/main/java/com/tacz/guns/client/renderer/item/GunItemRendererWrapper.java',
'src/main/java/com/tacz/guns/client/event/FirstPersonRenderGunEvent.java',
'src/main/java/com/tacz/guns/client/event/CameraSetupEvent.java',
'src/main/java/com/tacz/guns/client/animation/statemachine/GunAnimationStateContext.java',
'modules/tacz_adapter/src/main/java/dev/tacticaltacz/refit/RefitBridge.java',
'modules/tacz_adapter/src/main/java/dev/tacticaltacz/assembled/AssemblyGunClient.java',
'modules/tacz_adapter/src/main/java/dev/tacticaltacz/assembled/AssemblyGunModel.java',
'modules/tacz_adapter/src/main/java/dev/tacticaltacz/assembled/AssemblyGunExchange.java',
'modules/tacz_adapter/src/main/java/dev/tacticaltacz/assembled/AssembledWeapon.java',
'modules/tacz_adapter/src/development/java/dev/tacticaltacz/development/TaczDevelopmentCatalog.java']
for path in [A/'scripts/m4a1_state_machine.lua', A/'scripts/default_state_machine.lua', D/'scripts/xmag_reload_logic.lua', A/'geo_models/gun/lod/m4a1.json']:
 source_paths.append(str(path.relative_to(ROOT)))
refs=[]
for path in source_paths:
 p=ROOT/path
 refs.append({'path':path,'sha256':sha(p)})
(OUT/'source-evidence.json').write_text(json.dumps({'date':'2026-09-16','baseline':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip(),'codeAndExecutionFiles':refs,'readResourceHashes':read_hashes,'productionDiff':subprocess.check_output(['git','diff','HEAD','--','src','modules','build.gradle.kts','build-logic'],cwd=ROOT).decode(),'blockbenchModified':False,'gameStarted':False},indent=2)+'\n')
