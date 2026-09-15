import json,math,base64,zlib,struct
from pathlib import Path
p=Path(__file__).resolve().parent
load=lambda n:json.loads((p/n).read_text())
manifest=load('manifest.json');catalog={d['id']:d for d in load('catalog.json')['parts']}
for m in manifest['models']:
 bb=load(m['blockbench']);ids=[x['uuid'] for x in bb['elements']];assert len(ids)==len(set(ids))
 assert bb['outliner'][0]['children']==ids
 png=base64.b64decode(bb['textures'][0]['source'].split(',')[1]);assert png[:8]==b'\x89PNG\r\n\x1a\n'
 assert struct.unpack('>II',png[16:24])==(16,16)
 pos=8;pixels=b''
 while pos<len(png):
  size=struct.unpack('>I',png[pos:pos+4])[0];kind=png[pos+4:pos+8];data=png[pos+8:pos+8+size]
  assert zlib.crc32(kind+data)&0xffffffff==struct.unpack('>I',png[pos+8+size:pos+12+size])[0]
  if kind==b'IDAT':pixels+=data
  pos+=12+size
 assert len(zlib.decompress(pixels))==16*(1+16*3)
 for e in bb['elements']:
  if e['type']=='cube':assert all(f['texture']==0 for f in e['faces'].values())
 assert {e['name'] for e in bb['elements'] if e['type']=='locator'}==set(m['slots'])
 for lo,hi in m['boxes']:assert all(math.isfinite(x) for x in lo+hi) and all(a<b for a,b in zip(lo,hi))
 obj=(p/m['obj']).read_text().splitlines();nv=sum(l.startswith('v ') for l in obj);nf=sum(l.startswith('f ') for l in obj)
 assert nv==8*len(m['boxes']) and nf==6*len(m['boxes'])
 for l in obj:
  if l.startswith('f '):assert all(1<=int(i)<=nv for i in l.split()[1:])
for s in load('scenarios.json')['scenarios']:
 nodes=load(s['snapshot'])['nodes'];byid={n['instanceId']:n for n in nodes};assert len(byid)==len(nodes)
 errors=set();missing=[]
 for n in nodes:
  part=catalog[n['definitionId']]
  children=[a for a in nodes if a.get('parentId')==n['instanceId']]
  slots={a['id']:a for a in part['slots']}
  for child in children:
   assert child['slot'] in slots
   if child['definitionId'] not in slots[child['slot']]['allowedParts']:errors.add('INCOMPATIBLE')
  missing.extend(a['id'] for a in part['slots'] if a['required'] and not any(c['slot']==a['id'] for c in children))
  if any(other is not n and other['definitionId'] in part['conflictingParts'] for other in nodes):errors.add('PART_CONFLICT')
 assert (not errors)==s['expectValid'],s['id']
 assert (not errors and not missing)==s['expectComplete'],s['id']
 assert errors==set(s['expectedErrorCodes']),s['id']
 for n in nodes[1:]:assert nodes.index(byid[n['parentId']])<nodes.index(n)
print('PASS: 13 Blockbench/OBJ models; finite boxes, 6-face cuboids, locators, 6 scenario edge/conflict/completeness expectations, unique instances and parent order.')
