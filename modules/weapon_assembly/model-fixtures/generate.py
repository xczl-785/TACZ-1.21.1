"""Deterministic offline fixture geometry. Run with a local EFTForge items.json path."""
import json,sys,hashlib,uuid,base64,zlib,struct
from pathlib import Path
OUT=Path(__file__).resolve().parent
raw=Path(sys.argv[1]).read_bytes(); items=json.loads(raw)['data']['items']
IDS={'frame':'5a7ae0c351dfba0017554310','zev_slide':'5a71e22f8dc32e00094b97f4','mos_slide':'615d8dbd290d254f5e6b2ed6','barrel':'5a6b5f868dc32e000a311389','rmr':'5a32aa8bc4a2826c6e06d737','aam_mount':'615d8da4d3a39d50044c10e8','acro':'616442e4faa1272e43152193','tiger_mount':'5a7ad55551dfba0015068f42','rmr_mount':'5a33b2c9c4a282000c5a9511','aimtech_mount':'5a7ad4af51dfba0013379717','magazine':'5a7ad2e851dfba0016153692','front_sight':'5a71e0048dc32e000c52ecc8','rear_sight':'5a71e0fb8dc32e00094b97f2'}
def write(p,v):
 p=OUT/p;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,indent=2)+'\n')
def uid(x):return str(uuid.uuid5(uuid.NAMESPACE_URL,'newmod-model-fixtures/'+x))
# +Z muzzle, +Y up, +X right. Child origin attaches directly to parent locator.
boxes={
'frame':[([-1,-1,-3],[1,0,5]),([-1,-6,-3],[1,-1,-.5]),([-.7,-3,-.5],[.7,-2.5,2])],
'zev_slide':[([-1,0,-3],[1,1,5])], 'mos_slide':[([-1,0,-3],[1,1.2,5])],
'barrel':[([-.4,-.4,0],[.4,.4,8])],
'rmr':[([-1,0,-.8],[1,.3,.8]),([-1,.3,-.2],[-.65,1.5,.2]),([.65,.3,-.2],[1,1.5,.2]),([-1,1.3,-.2],[1,1.6,.2])],
'acro':[([-1,0,-1.3],[-.7,1.6,1.3]),([.7,0,-1.3],[1,1.6,1.3]),([-1,1.3,-1.3],[1,1.6,1.3]),([-1,0,-1.3],[1,.3,1.3])],
'aam_mount':[([-1,0,-1],[1,.35,1])], 'rmr_mount':[([-1,0,-1],[1,.4,1])],
'tiger_mount':[([-1.5,0,-2],[-1.1,3,2]),([-1.5,2.7,-2],[1.5,3,2])],
'aimtech_mount':[([1.1,0,-2],[1.5,3,2]),([-1.5,2.7,-2],[1.5,3,2])],
'magazine':[([-.75,-4,-.8],[.75,0,.8])], 'front_sight':[([-.2,0,-.2],[.2,.5,.2])], 'rear_sight':[([-.8,0,-.2],[-.3,.5,.2]),([.3,0,-.2],[.8,.5,.2])]}
anchors={'frame':{'mod_reciever':[0,0,0],'mod_barrel':[0,.5,-3],'mod_magazine':[0,-2,-1.8],'mod_mount':[0,-.5,0],'mod_tactical':[0,-.5,0]},'zev_slide':{'mod_scope':[0,1,-1.8],'mod_sight_front':[0,1,4.5],'mod_sight_rear':[0,1,-2.7]},'mos_slide':{'mod_mount':[0,1.2,-1.8],'mod_sight_front':[0,1.2,4.5],'mod_sight_rear':[0,1.2,-2.7]},'aam_mount':{'mod_scope':[0,.35,0]},'rmr_mount':{'mod_scope':[0,.4,0]},'tiger_mount':{'mod_scope':[0,3,0]},'aimtech_mount':{'mod_scope':[0,3,0]}}
def texture_data(index):
 colors=[(102,125,151),(229,158,91),(103,182,157),(221,111,104),(145,169,230),(195,151,211),(210,193,106),(114,186,199)]
 rgb=bytes(colors[index%len(colors)])
 def chunk(t,d):return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
 png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,2,0,0,0))+chunk(b'IDAT',zlib.compress((b'\x00'+rgb*16)*16))+chunk(b'IEND',b'')
 return 'data:image/png;base64,'+base64.b64encode(png).decode()
parts=[]; manifest=[]
for name,id in IDS.items():
 item=items[id];p=item.get('properties',{});weapon=name=='frame'
 assert not item.get('conflictingCategories') and not item.get('conflictingSlotIds')
 slots=[]
 for s in p.get('slots',[]):
  f=s['filters'];assert not f.get('allowedCategories') and not f.get('excludedCategories')
  slots.append({'id':s['nameId'],'required':s.get('required',False),'allowedParts':[a for a in f['allowedItems'] if a in IDS.values() and a not in f.get('excludedItems',[])]})
 stats={'weightKg':item['weight'],'ergonomics':p.get('ergonomics',0) if weapon else item.get('ergonomicsModifier',0)}
 if not weapon:stats['recoilFraction']=p.get('recoilModifier') or 0
 if p.get('accuracyModifier') is not None:stats['accuracyPercent']=round(p['accuracyModifier']*100,4)
 if item.get('velocity') is not None:stats['velocityPercent']=item['velocity']
 for k in ['heatFactor','coolingFactor','durabilityBurnFactor']+([] if weapon else ['centerOfImpact','sightingRange']):
  if p.get(k) is not None:stats[k]=p[k]
 part={'id':id,'slots':slots,'stats':stats,'conflictingParts':[a for a in item.get('conflictingItems',[]) if a in IDS.values()]}
 if weapon:part['weapon']={k:p[k] for k in ['recoilVertical','recoilHorizontal','centerOfImpact','sightingRange'] if p.get(k) is not None}
 parts.append(part)
 elements=[]
 for n,(lo,hi) in enumerate(boxes[name]):elements.append({'name':name+'_'+str(n),'uuid':uid(name+'/'+str(n)),'type':'cube','from':lo,'to':hi,'origin':[0,0,0],'rotation':[0,0,0],'color':list(IDS).index(name)%8,'faces':{f:{'uv':[0,0,16,16],'texture':0} for f in ['north','east','south','west','up','down']}})
 locators=[]
 for slot,pos in anchors.get(name,{}).items():locators.append({'name':slot,'uuid':uid(name+'/locator/'+slot),'type':'locator','position':pos,'rotation':[0,0,0],'ignore_inherited_scale':False})
 write('models/'+name+'.bbmodel',{'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':name,'model_identifier':name,'visible_box':[1,1,0],'resolution':{'width':16,'height':16},'elements':elements+locators,'outliner':[{'name':name,'origin':[0,0,0],'uuid':uid(name+'/group'),'children':[e['uuid'] for e in elements+locators]}],'textures':[{'name':name+'.png','id':'0','uuid':uid(name+'/texture'),'width':16,'height':16,'uv_width':16,'uv_height':16,'mode':'bitmap','saved':False,'source':texture_data(list(IDS).index(name))}]})
 lines=['# Generated diagnostic geometry; +Z muzzle, +Y up; 16 units = 1 block','o '+name]
 for n,(lo,hi) in enumerate(boxes[name]):
  for x,y,z in [(0,0,0),(1,0,0),(1,1,0),(0,1,0),(0,0,1),(1,0,1),(1,1,1),(0,1,1)]:lines.append('v %s %s %s'%((hi if x else lo)[0],(hi if y else lo)[1],(hi if z else lo)[2]))
  for face in [(1,4,3,2),(5,6,7,8),(1,2,6,5),(4,8,7,3),(1,5,8,4),(2,3,7,6)]:lines.append('f '+' '.join(str(n*8+i) for i in face))
 (OUT/'models'/f'{name}.obj').write_text('\n'.join(lines)+'\n')
 manifest.append({'name':name,'definitionId':id,'sourceName':item['normalizedName'],'blockbench':'models/'+name+'.bbmodel','obj':'models/'+name+'.obj','attachmentOrigin':[0,0,0],'slots':anchors.get(name,{}),'boxes':boxes[name]})
write('catalog.json',{'schemaVersion':1,'parts':parts})
write('manifest.json',{'schemaVersion':1,'units':'16 units = 1 Minecraft block; arbitrary diagnostic scale','axes':'+X right, +Y up, +Z muzzle','transform':'Child local origin = parent slot position; all fixture rotations zero. Sum ancestor translations.','models':manifest})
base=[('frame',None,None),('barrel',0,'mod_barrel')]
scenes=[('zev_rmr',base+[('zev_slide',0,'mod_reciever'),('rmr',2,'mod_scope'),('magazine',0,'mod_magazine'),('front_sight',2,'mod_sight_front'),('rear_sight',2,'mod_sight_rear')],True,True),('mos_acro',base+[('mos_slide',0,'mod_reciever'),('aam_mount',2,'mod_mount'),('acro',3,'mod_scope')],True,True),('dual_rmr',base+[('zev_slide',0,'mod_reciever'),('rmr',2,'mod_scope'),('tiger_mount',0,'mod_mount'),('rmr_mount',4,'mod_scope'),('rmr',5,'mod_scope')],True,True),('mount_conflict',base+[('zev_slide',0,'mod_reciever'),('tiger_mount',0,'mod_mount'),('aimtech_mount',0,'mod_tactical')],False,False),('missing_barrel',[('frame',None,None),('mos_slide',0,'mod_reciever'),('aam_mount',1,'mod_mount'),('acro',2,'mod_scope')],True,False),('wrong_optic',base+[('mos_slide',0,'mod_reciever'),('aam_mount',2,'mod_mount'),('rmr',3,'mod_scope')],False,False)]
scene_index=[]
for name,seq,valid,complete in scenes:
 nodes=[];placements=[]
 for n,(part,parent,slot) in enumerate(seq):
  node={'instanceId':uid(name+'/'+str(n)),'definitionId':IDS[part]}
  if parent is not None:node.update(parentId=nodes[parent]['instanceId'],slot=slot)
  nodes.append(node)
  pos=[0,0,0] if parent is None else [a+b for a,b in zip(placements[parent]['position'],anchors[seq[parent][0]][slot])]
  path=[] if parent is None else placements[parent]['path']+[slot]
  placements.append({'model':part,'instanceId':node['instanceId'],'position':pos,'path':path})
 write('scenes/'+name+'.json',{'schemaVersion':1,'nodes':nodes})
 scene_index.append({'id':name,'snapshot':'scenes/'+name+'.json','expectValid':valid,'expectComplete':complete,'expectedErrorCodes':({'mount_conflict':['PART_CONFLICT'],'wrong_optic':['INCOMPATIBLE']}.get(name,[])),'placements':placements})
write('scenarios.json',{'schemaVersion':1,'scenarios':scene_index})
write('source.json',{'source':'EFTForge/docs/data/cache/items.json','sha256':hashlib.sha256(raw).hexdigest(),'ids':IDS,'scope':'All source slots/required flags retained; allowedItems and conflictingItems intersect selected IDs. Geometry/anchors authored for testing, not source measurements.'})
print('Generated',len(parts),'models and',len(scenes),'scenarios')
# Self-contained offline scene preview; no network, imports or asset requests.
payload=json.dumps({'models':manifest,'scenarios':scene_index})
html='''<!doctype html><html lang="zh"><meta charset="utf-8"><title>Weapon Assembly 测试低模</title><style>body{background:#111820;color:#ddd;font:16px system-ui;margin:28px}select,input{margin:12px}svg{width:100%;height:65vh;background:#1b2633}pre{white-space:pre-wrap}label{display:inline-block}</style><h1>Glock 装配测试低模</h1><p>原创测试几何 · EFTForge 兼容关系 · 非真实尺寸 · 结构齐全不代表可射击</p><select id="scene"></select><label>水平旋转<input id="angle" type="range" min="0" max="360" value="35"></label><label>拆解距离<input id="explode" type="range" min="0" max="5" step="0.1" value="0"></label><svg viewBox="-320 -210 640 420" id="view"></svg><pre id="info"></pre><script>const data=PAYLOAD;const palette=['#667d97','#e59e5b','#67b69d','#dd6f68','#91a9e6','#c397d3','#d2c16a','#72bac7'];const sel=document.getElementById('scene'),svg=document.getElementById('view');data.scenarios.forEach(s=>sel.add(new Option(s.id,s.id)));function draw(){let s=data.scenarios.find(s=>s.id===sel.value),a=Number(document.getElementById('angle').value)*Math.PI/180,e=Number(document.getElementById('explode').value),faces=[];function project(p){let x=p[0]*Math.cos(a)+p[2]*Math.sin(a),z=-p[0]*Math.sin(a)+p[2]*Math.cos(a);return [x*22,(-p[1]+z*.35)*22,z]};s.placements.forEach((p,i)=>{let m=data.models.find(m=>m.name===p.model);m.boxes.forEach(b=>{let v=[[0,0,0],[1,0,0],[1,1,0],[0,1,0],[0,0,1],[1,0,1],[1,1,1],[0,1,1]].map(c=>project(c.map((n,j)=>b[n][j]+p.position[j]+(j===1?p.path.length*e:0))));[[0,3,2,1],[4,5,6,7],[0,1,5,4],[3,7,6,2],[0,4,7,3],[1,2,6,5]].forEach(f=>faces.push({z:f.reduce((n,k)=>n+v[k][2],0)/4,points:f.map(k=>v[k].slice(0,2).join(',')).join(' '),color:palette[data.models.indexOf(m)%palette.length],title:p.model+' / '+p.path.join('/')}))})});faces.sort((a,b)=>b.z-a.z);svg.innerHTML=faces.map(f=>'<polygon points="'+f.points+'" fill="'+f.color+'" stroke="#142030" stroke-width="1"><title>'+f.title+'</title></polygon>').join('');document.getElementById('info').textContent='合法: '+s.expectValid+' | 必需件齐全: '+s.expectComplete+' | 预期错误: '+s.expectedErrorCodes.join(', ')+'\\n'+s.placements.map(p=>p.model+'  '+(p.path.join(' / ')||'(root)')).join('\\n')}sel.onchange=draw;document.getElementById('angle').oninput=draw;document.getElementById('explode').oninput=draw;draw();</script></html>'''
(OUT/'preview.html').write_text(html.replace('PAYLOAD',payload))
