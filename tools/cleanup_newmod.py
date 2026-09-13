#!/usr/bin/env python3
"""Generate a hash-guarded cleanup plan; --apply executes that exact local plan."""
import json,re,hashlib,sys,collections
from pathlib import Path
R=Path(__file__).resolve().parents[1]; P=R/'src/main/resources/assets/tacz/custom/tacz_default_gun'; O=R/'docs/newmod/cleanup';O.mkdir(exist_ok=True)
def sha(b):return hashlib.sha256(b).hexdigest()
s=(R/'tools/audit_selected_content.py').read_text();ns={'json':json};exec(s[s.index('def read_json'):s.index('\nguns=[]')],ns);read=ns['read_json']
keep=set('glock_17 m16a1 m4a1 m870 p90 qbz_191 scar_h scar_l ump45 uzi aa12 m700 sks_tactical ai_awp mk14'.split())
if '--apply' in sys.argv:
 plan=json.loads((O/'plan.json').read_text())
 for row in plan['deleted']+plan['modified']:
  assert sha((P/row['path']).read_bytes())==row['before_sha256'],row['path']
 for row in plan['modified']:(P/row['path']).write_text(row['after_text'])
 for row in plan['deleted']:(P/row['path']).unlink()
 print('applied',len(plan['deleted']),'deletions',len(plan['modified']),'edits');sys.exit()
assert not (O/'applied.json').exists(),'Cleanup already applied; do not overwrite original plan'
guns={p.stem:read(p) for p in (P/'data/tacz/index/guns').glob('*.json')};assert len(guns)==54
removed=set(guns)-keep
attachments={p.stem:read(p) for p in (P/'data/tacz/index/attachments').glob('*.json')}
tagroot=P/'data/tacz/tacz_tags/attachments'
tags={str(p.relative_to(tagroot)).removesuffix('.json'):read(p) for p in tagroot.rglob('*.json')}
def expand(vals,seen=None):
 seen=set() if seen is None else seen;result=set()
 for v in vals:
  if v.startswith('#tacz:'):
   k=v[6:]
   if k not in seen:result|=expand(tags.get(k,[]),seen|{k})
  elif v.startswith('tacz:'):result.add(v[5:])
 return result
users={a:[] for a in attachments}
for g in sorted(keep):
 # Keep every tag-compatible attachment, including types a future configuration may expose.
 for a in expand(tags.get('allow_attachments/'+g,[])):
  if a in users:users[a].append(g)
unused={a for a,u in users.items() if not u}
files={str(p.relative_to(P)):p.read_bytes() for p in P.rglob('*') if p.is_file()}
# Semantic roots of removed definitions, plus name-associated asset candidates.
forced=set(); candidates=set(); reasons={}
for kind,names in [('guns',removed),('attachments',unused)]:
 for name in names:
  idx=(guns if kind=='guns' else attachments)[name]
  for rel in [f'data/tacz/index/{kind}/{name}.json',f'data/tacz/data/{kind}/'+idx['data'].split(':')[1]+'.json',f'assets/tacz/display/{kind}/'+idx['display'].split(':')[1]+'.json',f'data/tacz/recipe/'+('gun' if kind=='guns' else 'attachments')+f'/{name}.json',f'data/tacz/tacz_tags/attachments/allow_attachments/{name}.json']:
   if rel in files:forced.add(rel);reasons[rel]=kind+':'+name
for path,b in files.items():
 if '/recipe/' in path and path.endswith('.json'):
  d=read(P/path);result=d.get('result',{});rid=result.get('id','').removeprefix('tacz:')
  if result.get('type')=='gun' and rid in removed or result.get('type')=='attachment' and rid in unused:forced.add(path);reasons[path]='removed result '+rid
 if '/tacz_loot_injectors/' in path and any(('tacz:'+g).encode() in b for g in removed):forced.add(path);reasons[path]='removed-gun loot entry'
 if path.startswith('assets/tacz/') and '/lang/' not in path:
  for name in sorted(removed|unused,key=len,reverse=True):
   if any(re.match(re.escape(name)+r'(?:[_.]|$)',part) for part in Path(path).parts[3:]):candidates.add(path);reasons[path]='asset-name association '+name;break
candidates|=forced
# Clean only removed keys/tag members, keeping unmodified formatting elsewhere.
modified={}
for path,b in files.items():
 if path in forced or not path.endswith('.json'):continue
 if '/lang/' in path:
  d=read(P/path);new={k:v for k,v in d.items() if not any(k.startswith('tacz.gun.'+g+'.') for g in removed) and not any(k.startswith('tacz.attachment.'+a+'.') for a in unused)}
  if new!=d:modified[path]=json.dumps(new,ensure_ascii=False,indent=2)+'\n'
 elif '/tacz_tags/attachments/' in path:
  d=read(P/path)
  if isinstance(d,list):
   new=[v for v in d if v.removeprefix('tacz:') not in unused]
   if new!=d:modified[path]=json.dumps(new,ensure_ascii=False,indent=2)+'\n'
m700='data/tacz/data/guns/m700_data.json';modified[m700]=files[m700].decode().replace('"tacz:30_06"','"tacz:308"');assert modified[m700]!=files[m700].decode()
# Resolve full resource IDs across loader folders, plus Lua require aliases and bare sound/script names.
lookup=collections.defaultdict(set)
for path in files:
 parts=Path(path).parts
 if len(parts)<4:continue
 tail='/'.join(parts[3:]);stem=tail.rsplit('.',1)[0]
 lookup['tacz:'+stem].add(path)
 if parts[2] in ('data','display','index') and '/' in stem:lookup['tacz:'+stem.split('/',1)[1]].add(path)
 if parts[2]=='scripts':lookup['tacz_'+stem].add(path)
edges={}
for path,b in files.items():
 if Path(path).suffix not in ('.json','.lua'):continue
 text=modified.get(path,b.decode(errors='replace'))
 if path.endswith('.json') and '/lang/' not in path:
  try:text=json.dumps(json.loads(text) if path in modified else read(P/path),ensure_ascii=False)
  except Exception:pass
 refs=set(re.findall(r'tacz:[A-Za-z0-9_./-]+|tacz_[A-Za-z0-9_]+',text))
 edges[path]=set().union(*(lookup.get(x,set()) for x in refs)) if refs else set()
# Everything not a candidate is a root, conservatively including defaults and attachment assets.
protected=set(files)-candidates;todo=list(protected)
# Workstation icons deliberately deferred by owner; do not preserve removed guns for UI samples.
edges['data/tacz/data/blocks/gun_smith_table.json']=set()
while todo:
 p=todo.pop()
 for q in edges.get(p,set()):
  if q not in protected:protected.add(q);todo.append(q)

if forced&protected:
 for q in sorted(forced&protected):
  print(q, [p for p in protected-forced if q in edges.get(p,set())])
 raise RuntimeError("protected roots")
deleted=candidates-protected
plan={'date':'2026-09-13','baseline_commit':'01e24c1e4ea4ba63428f0e564ce5af3d47f3969d','retained_guns':sorted(keep),'removed_guns':sorted(removed),'attachment_users':users,'removed_attachments':sorted(unused),'protected_named_assets':sorted(candidates&protected),'deleted':[{'path':p,'before_sha256':sha(files[p]),'bytes':len(files[p]),'reason':reasons[p]} for p in sorted(deleted)],'modified':[{'path':p,'before_sha256':sha(files[p]),'after_sha256':sha(v.encode()),'after_text':v,'reason':'M700 caliber .30-06 -> 7.62x51' if p==m700 else 'remove retired translations/tag members'} for p,v in sorted(modified.items())]}
(O/'plan.json').write_text(json.dumps(plan,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'guns_keep':len(keep),'guns_remove':len(removed),'attachments_total':len(attachments),'attachments_remove':sorted(unused),'delete_files':len(deleted),'preserve_shared_candidates':len(candidates&protected),'modified':len(modified),'removed_bytes':sum(len(files[p]) for p in deleted)},ensure_ascii=False))
