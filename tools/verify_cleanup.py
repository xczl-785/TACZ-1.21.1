#!/usr/bin/env python3
"""Validate exact cleanup ledger, resource references, attachment consumers and policy."""
import json,re,hashlib,subprocess,tempfile,collections,zipfile,sys
from pathlib import Path
R=Path(__file__).resolve().parents[1];P=R/'src/main/resources/assets/tacz/custom/tacz_default_gun'
s=(R/'tools/audit_selected_content.py').read_text();ns={'json':json};exec(s[s.index('def read_json'):s.index('\nguns=[]')],ns);read=ns['read_json']
plan=json.loads((R/'docs/newmod/cleanup/plan.json').read_text());selection=json.loads((R/'docs/newmod/selection.json').read_text())
assert sorted('tacz:'+p.stem for p in (P/'data/tacz/index/guns').glob('*.json'))==selection['retain_ids']
assert read(P/'data/tacz/data/guns/m700_data.json')['ammo']=='tacz:308'
for row in plan['deleted']:assert not (P/row['path']).exists(),row['path']
follow_path=R/'docs/newmod/extra-content/removal.json'
follow={r['path']:r for r in json.loads(follow_path.read_text())['files']} if follow_path.exists() else {}
for row in plan['modified']:
 current=P/row['path'];later=follow.get(str(current.relative_to(R)))
 if later:
  assert later['before_sha256']==row['after_sha256'],row['path']
  if later['action']=='delete':assert not current.exists(),row['path'];continue
 expected=later['after_sha256'] if later else row['after_sha256']
 assert hashlib.sha256(current.read_bytes()).hexdigest()==expected,row['path']
lookup=collections.defaultdict(set)
old=json.loads((R/'docs/newmod/inventory/resources.json').read_text())
for row in old:
 path=row['path'];parts=Path(path).parts
 if len(parts)<4:continue
 stem='/'.join(parts[3:]).rsplit('.',1)[0]
 lookup['tacz:'+stem].add(path)
 if parts[2] in ('data','display','index') and '/' in stem:lookup['tacz:'+stem.split('/',1)[1]].add(path)
 if parts[2]=='scripts':lookup['tacz_'+stem].add(path)
dangling=[]
for p in P.rglob('*'):
 if p.suffix not in ('.json','.lua') or '/lang/' in str(p):continue
 text=json.dumps(read(p),ensure_ascii=False) if p.suffix=='.json' else p.read_bytes().decode(errors='replace')
 for ref in set(re.findall(r'tacz:[A-Za-z0-9_./-]+|tacz_[A-Za-z0-9_]+',text)):
  if ref in lookup and not any((P/q).exists() for q in lookup[ref]):dangling.append((str(p.relative_to(P)),ref))
deferred={("data/tacz/data/blocks/gun_smith_table.json","tacz:"+g) for g in plan['deferred_workstations']['known_removed_gun_icon_ids']}
assert set(dangling)<=deferred,dangling
# Match the actual recursive tag rules; allow type is a separate runtime test.
t=P/'data/tacz/tacz_tags/attachments';tags={str(p.relative_to(t)).removesuffix('.json'):read(p) for p in t.rglob('*.json')}
def expand(v,seen=frozenset()):
 out=set()
 for x in v:
  if x.startswith('#tacz:'):
   key=x[6:]
   if key not in seen:out|=expand(tags.get(key,[]),seen|{key})
  elif x.startswith('tacz:'):out.add(x[5:])
 return out
available={p.stem for p in (P/'data/tacz/index/attachments').glob('*.json')}
used=set().union(*(expand(tags.get('allow_attachments/'+g,[])) for g in plan['retained_guns']))
assert available<=used,available-used
assert not available&set(plan['removed_attachments'])
checks=[]
for g in plan['removed_guns']:
 for prefix in ['index/guns/','recipe/gun/']:checks.append(('tacz',prefix+g+'.json',True))
for a in plan['removed_attachments']:
 for prefix in ['index/attachments/','recipe/attachments/']:checks.append(('tacz',prefix+a+'.json',True))
for p in (P/'data/tacz/index').rglob('*.json'):checks.append(('tacz',str(p.relative_to(P/'data/tacz')),False))
checks += [('other','index/guns/ak47.json',False),('tacz','recipe/ammo/future.json',True)]
source='import com.tacz.guns.resource.SelectedContentPolicy; class Verify {public static void main(String[] a){\n'
for n,p,b in checks:source+='if(SelectedContentPolicy.excludesResource('+json.dumps(n)+','+json.dumps(p)+')!='+str(b).lower()+')throw new AssertionError('+json.dumps(p)+');\n'
source+='}}'
with tempfile.TemporaryDirectory() as d:
 f=Path(d)/'Verify.java';f.write_text(source)
 subprocess.run(['javac','-d',d,str(R/'src/main/java/com/tacz/guns/resource/SelectedContentPolicy.java'),str(f)],check=True);subprocess.run(['java','-cp',d,'Verify'],check=True)
if len(sys.argv)>1:
 with zipfile.ZipFile(sys.argv[1]) as z:
  names=set(z.namelist());prefix='assets/tacz/custom/tacz_default_gun/'
  assert all(prefix+r['path'] not in names for r in plan['deleted'])
  assert sum(x.startswith(prefix+'data/tacz/index/guns/') and x.endswith('.json') for x in names)==15
print(f'PASS: 15 guns, {len(available)} consumed attachments, {len(plan["deleted"])} deletions, hashes, explicit references checked (historical workstation exception applies only before its removal), {len(checks)} Java policy cases')
