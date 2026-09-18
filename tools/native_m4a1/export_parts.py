"""Export native TaCZ geometry as deterministic, UV-preserving Blockbench meshes.
Read-only native inputs; no editor, held-model, or runtime mutation.
"""
from pathlib import Path
import base64, collections, hashlib, importlib.util, json, math, re, sys, uuid
import numpy as np
from PIL import Image
R=Path(__file__).resolve().parents[2]
SRC=R/'src/main/resources/assets/tacz/custom/tacz_default_gun'
OUT=R/'modules/tacz_adapter/weapon-sources/native_m4a1'
DATA=R/'modules/tacz_adapter/weapon-content/resources/data/tacz_fork_tarkov/m4a1'
S=np.array([1.,-1.,1.])
# Native muzzle is -Z. A proper Y half-turn preserves handedness in art space.
ART=np.diag([-1.,-1.,-1.,1.]) # Java Y-down -> art Y-up and native Y half-turn

def read(p):
 return json.loads(re.sub(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*[\s\S]*?\*/',lambda m:m[0] if m[0].startswith('"') else '',p.read_text()))
def write(p,v):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,separators=(',',':'))+'\n')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def uid(s):return str(uuid.uuid5(uuid.NAMESPACE_URL,'tacz-native-component/'+s))
def trans(v):
 m=np.eye(4);m[:3,3]=v;return m
def rot(v):
 x,y,z=np.radians(v);cx,sx=np.cos(x),np.sin(x);cy,sy=np.cos(y),np.sin(y);cz,sz=np.cos(z),np.sin(z)
 m=np.eye(4);m[:3,:3]=np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]]);return m
def matrix(n,bs):
 b=bs[n];p=b.get('parent');d=np.array(b['pivot'])-(np.array(bs[p]['pivot']) if p else 0)
 return (matrix(p,bs) if p else np.eye(4))@trans(S*d)@rot(b.get('rotation',[0,0,0]))
def ancestors(n,bs):
 result=[]
 while n:result.append(n);n=bs[n].get('parent')
 return result[::-1]
def asset(ref,kind,ext):
 ns,p=ref.split(':');return SRC/f'assets/{ns}/{kind}/{p}{ext}'
# Exact vertex order and pixel UV assignment from BedrockCubeBox/PerFace/Polygon.
QUADS={'down':[5,4,0,1],'up':[2,3,7,6],'west':[0,4,7,3],'north':[1,0,3,2],'east':[5,1,2,6],'south':[4,5,6,7]}
def cube_geometry(b,c,bs,extra):
 pivot=np.array(c.get('pivot',b['pivot']));m=ART@extra@matrix(b['name'],bs)@trans(S*(pivot-np.array(b['pivot'])))@rot(c.get('rotation',[0,0,0]))
 origin=np.array(c['origin']);size=np.array(c['size']);o=(origin-pivot)*S;o[1]-=size[1]
 lo=o-c.get('inflate',0);hi=o+size+c.get('inflate',0)
 box=isinstance(c['uv'],list);mirror=box and c.get('mirror',False)
 if mirror:lo[0],hi[0]=hi[0],lo[0]
 v=np.array([[lo[0],lo[1],lo[2]],[hi[0],lo[1],lo[2]],[hi[0],hi[1],lo[2]],[lo[0],hi[1],lo[2]],[lo[0],lo[1],hi[2]],[hi[0],lo[1],hi[2]],[hi[0],hi[1],hi[2]],[lo[0],hi[1],hi[2]]])
 v=np.array([(m@np.append(p,1))[:3] for p in v])
 if box:
  u,w=c['uv'];dx,dy,dz=map(int,size);a=u+dz;b1=a+dx;c1=b1+dx;d=b1+dz;e=d+dx;f=w+dz;g=f+dy
  rect={'down':[a,w,b1,f],'up':[b1,f,c1,w],'west':[u,f,a,g],'north':[a,f,b1,g],'east':[b1,f,d,g],'south':[d,f,e,g]}
 else:rect={k:[*q['uv'],q['uv'][0]+q['uv_size'][0],q['uv'][1]+q['uv_size'][1]] for k,q in c['uv'].items()}
 faces=[]
 for direction,ids in QUADS.items():
  if direction not in rect:continue
  u1,v1,u2,v2=rect[direction];uv=[[u2,v1],[u1,v1],[u1,v2],[u2,v2]];ids=list(ids)
  if mirror:ids.reverse();uv.reverse()
  # ART changes handedness relative to native Java quads.
  ids.reverse();uv.reverse()
  for idx in [(0,1,2),(0,2,3)]:faces.append(([ids[i] for i in idx],[uv[i] for i in idx]))
 return v,faces

def main():
 mapping=read(DATA/'mapping.json');catalog={p['id']:p for p in read(DATA/'catalog.json')['parts']}
 audit=read(R/'docs/assembly-experiment/native-m4a1-review/audit.json');ownership=read(R/'docs/assembly-experiment/native-m4a1-review/ownership-proposal.json')['boneOwner']
 attachments={a['id'].replace(':','_'):a for a in audit['attachments'] if a['tagAllowed'] and a['id'] not in {'tacz:ammo_mod_fmj','tacz:ammo_mod_hp','tacz:ammo_mod_i'}}
 hip=asset('tacz:gun/m4a1_geo','geo_models','.json');hi=read(hip);bs={b['name']:b for b in hi['minecraft:geometry'][0]['bones']}
 animation=asset('tacz:m4a1','animations','.animation.json');an=read(animation)
 active={bone for anim in an['animations'].values() for bone in anim.get('bones',{})}
 anchors={'lower_receiver':None,'upper_receiver':'upper2','barrel_mount_collar':'bone38','barrel':'group2','gas_block_and_tube':'fore_sight3','front_sight':'fore_sights','rear_sight':'rear_sight','bolt':'m4a1_bolt','charging_mechanism':'m4a1_pull','handguard_default':'handguard_default','handguard_tactical':'handguard_default','pistol_grip':'grip2','buffer':'octagon2','magazine_standard':'magazine','muzzle_default':'muzzle_pos'}
 for d,a in attachments.items():anchors[d]={'scope':'scope_pos','stock':'stock_pos','grip':'grip_pos','laser':'laser_pos','muzzle':'muzzle_pos','extended_mag':'magazine'}[a['type']]
 anchorpos={d:(ART@matrix(n,bs))[:3,3] if n else np.zeros(3) for d,n in anchors.items()}
 def owner(n):
  o=ownership[n]
  if o=='handguard':return 'handguard_tactical' if 'handguard_tactical' in ancestors(n,bs) else 'handguard_default'
  if o=='muzzle':return 'muzzle_default'
  if o=='magazine':return {'mag_standard':'magazine_standard','mag_extended_1':'tacz_extended_mag_1','mag_extended_2':'tacz_extended_mag_2','extend_magazine':'tacz_extended_mag_3'}[n]
  return o
 records=collections.defaultdict(list)
 for b in bs.values():
  if not b.get('cubes'):continue
  d=owner(b['name'])
  if d not in mapping:continue
  variant='folded' if 'sight_folded' in ancestors(b['name'],bs) else 'default'
  chain=[n for n in ancestors(b['name'],bs) if n in active]
  for i,c in enumerate(b.get('cubes',[])):records[(d,variant)].append((b,c,bs,np.eye(4),hip,'tacz:gun/uv/m4a1',chain,i))
 for d,a in attachments.items():
  if (d,'default') in records:continue
  modelp=asset(a['display']['model'],'geo_models','.json');model=read(modelp);lookup={b['name']:b for b in model['minecraft:geometry'][0]['bones']}
  for b in lookup.values():
   for i,c in enumerate(b.get('cubes',[])):records[(d,'default')].append((b,c,lookup,matrix(anchors[d],bs),modelp,a['display']['texture'],[],i))
 parts=[];errors=[];triangles=0
 for (d,variant),rec in sorted(records.items()):
  groups=collections.defaultdict(list)
  for r in rec:groups[tuple(r[6])].append(r)
  elements=[];groupmeta=[];texturep=asset(rec[0][5],'textures','.png');pixels=texturep.read_bytes();width,height=Image.open(texturep).size
  for k,rows in sorted(groups.items()):
   name='static' if not k else 'motion_'+'_'.join(k);verts={};faces={};bones=[]
   for b,c,lookup,extra,source,texture,chain,ci in rows:
    vv,ff=cube_geometry(b,c,lookup,extra);base=len(verts);bones.append({'bone':b['name'],'cubeIndex':ci})
    for j,p in enumerate(vv):verts[str(base+j)]=(p-anchorpos[d]).round(10).tolist()
    for ids,uv in ff:
     keys=[str(base+i) for i in ids];faces[str(len(faces))]={'vertices':keys,'uv':dict(zip(keys,uv)),'texture':0}
    errors.append(float(np.max(np.abs(np.array(list(verts.values())[-8:])+anchorpos[d]-vv))))
   meshid=uid(d+'/'+variant+'/'+name);elements.append({'name':name,'type':'mesh','uuid':meshid,'origin':[0,0,0],'rotation':[0,0,0],'visibility':True,'export':True,'vertices':verts,'faces':faces})
   groupmeta.append({'mesh':name,'animationAncestors':list(k),'sourceCubes':bones,'staticMerge':True})
  rel=Path('source-pack/components')/d;filename='model.bbmodel' if variant=='default' else 'model-'+variant+'.bbmodel';p=OUT/rel/filename;p.parent.mkdir(parents=True,exist_ok=True)
  (p.parent/'texture.png').write_bytes(pixels)
  model={'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':d+('' if variant=='default' else '_'+variant),'resolution':{'width':width,'height':height},'elements':elements,'outliner':[e['uuid'] for e in elements],'textures':[{'name':'texture.png','id':'0','uuid':uid(str(texturep.relative_to(R))),'path':'texture.png','relative_path':'texture.png','source':'data:image/png;base64,'+base64.b64encode(pixels).decode(),'width':width,'height':height,'uv_width':width,'uv_height':height,'mode':'bitmap','render_mode':'default','visible':True,'internal':True}],'animations':[]}
  write(p,model);matrixout=trans(anchorpos[d]).tolist();slotanchors={s['id']:anchorpos[s['allowedParts'][0]].tolist() for s in catalog[d]['slots']}
  all_vertices=np.concatenate([np.array(list(e['vertices'].values())) for e in elements])+anchorpos[d]
  bounds=[all_vertices.min(axis=0).tolist(),all_vertices.max(axis=0).tolist()]
  meta={'geometryBounds':bounds,'geometryCenter':all_vertices.mean(axis=0).tolist(),'boundsCenter':((all_vertices.min(axis=0)+all_vertices.max(axis=0))/2).tolist(),'definitionId':d,'itemId':mapping[d],'variant':variant,'model':str(rel/filename),'texture':str(rel/'texture.png'),'anchor':anchorpos[d].tolist(),'local_to_assembly':matrixout,'slots':slotanchors,'anchorBone':anchors[d],'meshGroups':groupmeta,'sourceGeometry':sorted({str(r[4].relative_to(R)) for r in rec}),'sourceGeometryHashes':{str(r[4].relative_to(R)):sha(r[4]) for r in rec},'boneTransforms':{b['name']:{'pivot':b['pivot'],'rotation':b.get('rotation',[0,0,0]),'parent':b.get('parent'),'nativeNeutralMatrix':matrix(b['name'],lookup).tolist()} for b,c,lookup,extra,source,texture,chain,ci in rec},'sourceTexture':str(texturep.relative_to(R)),'sourceTextureSha256':sha(texturep),'sourceAnimation':str(animation.relative_to(R)),'sourceAnimationSha256':sha(animation),'modelSha256':sha(p),'meshCount':len(elements),'triangles':sum(len(e['faces']) for e in elements),'neutralOnly':True}
  component='component.json' if variant=='default' else 'component-'+variant+'.json';write(p.parent/component,meta);meta['component']=str(rel/component);parts.append(meta);triangles+=meta['triangles']
 assert len([p for p in parts if p['variant']=='default'])==len(mapping)==67
 manifest={'schemaVersion':1,'coordinateSystem':'X right / Y up / +Z muzzle; native model units, proper 180 degree Y rotation from native Bedrock art frame','parts':[p for p in parts if p['variant']=='default'],'variants':[p for p in parts if p['variant']!='default'],'sourceAnimationSha256':sha(animation),'limits':['Neutral editor assets; animated ownership retained in component metadata; original held rig and animation files untouched.','Native scope lens and reticle special passes remain runtime responsibilities.']}
 write(OUT/'manifest.json',manifest)
 # The same strict free-mesh converter used by Radian is the downstream contract.
 sys.path.insert(0,str(R/'modules/tacz_adapter/tools'))
 spec=importlib.util.spec_from_file_location('weapon_builder',R/'modules/tacz_adapter/tools/build_weapon.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
 for p in parts:
  meshes=module.convert_component(read(OUT/p['model']),p['local_to_assembly'],np.array(p['anchor']));assert sum(len(m['triangles']) for m in meshes)==p['triangles']
 assert max(errors)<1e-8
 report={'parts':67,'variants':len(manifest['variants']),'meshes':sum(p['meshCount'] for p in parts),'triangles':triangles,'maximumBakeRoundtripError':max(errors),'sameMotionMerged':True,'differentMotionMerged':False,'radianConverterPassed':True,'texturesByteIdentical':all(sha(OUT/p['texture'])==p['sourceTextureSha256'] for p in parts),'blockbenchOpened':False,'gameStarted':False}
 write(OUT/'validation.json',report);print(json.dumps(report))
if __name__=='__main__':main()
