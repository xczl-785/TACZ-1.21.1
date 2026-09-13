import json,re,hashlib,csv,collections
from pathlib import Path
if (Path(__file__).resolve().parents[1]/"docs/newmod/cleanup/applied.json").exists():
    raise SystemExit("Pre-cleanup inventory is frozen; use tools/verify_cleanup.py for current state")
root=Path(__file__).resolve().parents[2]; fork=root/'TACZ-1.21.1'; out=fork/'docs/newmod/inventory';out.mkdir(exist_ok=True)
pack=fork/'src/main/resources/assets/tacz/custom/tacz_default_gun'
# Reuse only the existing JSONC parser, without executing its inventory writes.
src=(fork/'tools/audit_selected_content.py').read_text(); ns={'json':json};exec(src[src.index('def read_json'):src.index('\nguns=[]')],ns);read=ns['read_json']
w=json.loads((root/'docs/参考资料/ammunition-audit/adoption/eft-weapons.json').read_text())
sel=json.loads((fork/'docs/content-isolation/selection.json').read_text())
lang=read(pack/'assets/tacz/lang/en_us.json')
# Exact source IDs are retained below; model-family candidates never become accepted automatically.
match={
'aa12':('candidate','66ffa9b66e19cc902401c5e8','AA-12已存在；TaCZ代际需确认'),
'ai_awp':('candidate','627e14b21713922ded6f2c15','AWM/AWP与AXMC不是同一型号'),
'ak47':('candidate','59d6088586f774275f37482f','AK47与AKM不能自动等同'),
'aug':('candidate','62e7c4fba689e8c9c50dfc38','AUG需核对A1/A3外观与配置'),
'db_long':('candidate','5580223e4bdc2d1c128b457f','通用双管名不能证明MP-43型号'),
'db_short':('candidate','64748cb8de82c85eaf0a273a','短双管需核对是否对应MP-43'),
'deagle':('candidate','668fe5a998b5ad715703ddd6','.50AE一致，需核对Mk XIX/L5/L6'),
'deagle_golden':('candidate','669fa409933e898cce0c2166','.357一致，金色外观不证明L5型号'),
'fn_fal':('candidate','5b0bbe4e5acfc40dc528a72d','FN FAL与DS Arms SA58同系但非同款'),
'g36k':('candidate','623063e994fc3f7b302a9696','G36K需核对G36短型组件配置'),
'glock_17':('retain','5a7ae0c351dfba0017554310','名称和口径对应'),
'hk416d':('candidate','5bb2475ed4351e00853264e3','416D与416A5代际不同'),
'hk_mp5a5':('candidate','5926bb2186f7744b1c6c6e60','需核对MP5枪托与点射/全自动配置'),
'm1014':('candidate','6259b864ebedf17603599e88','M1014与M3 Super90不能自动等同'),
'm16a1':('retain','68a639748e1fe612970728e9','快照已含M16A1，不按旧印象删除'),
'm16a4':('candidate','68a6399922b1e0bd360afe56','快照有M16A2，不是M16A4'),
'm1911':('candidate','5e81c3cbac2bb513793cdc75','需核对M1911/A1具体型'),
'm4a1':('retain','5447a9cd4bdc2dbd208b4567','名称和口径对应'),
'm700':('exclude','5bfea6e90db834001b7347f3','既有排除；TaCZ为.30-06，EFT为7.62x51，不因同名恢复'),
'm870':('retain','5a7828548dc32e5a9c28b516','名称和口径对应'),
'm9a4':('candidate','5cadc190ae921500103bb3b6','M9A4与M9A3不同；现有桥覆盖不等于筛选通过'),
'mk14':('candidate','5aafa857e5b5b00018480968','Mk14与M1A不自动等同'),
'p90':('retain','5cc82d76e24e8d00134b4b83','名称和口径对应'),
'qbz_191':('retain','69f9ebbcaae020b0db02f65d','公开快照已有QBZ191/5.8x42'),
'rhino357':('candidate','61a4c8884f95bc3b2c5dc96f','.357对应；需核对50DS而非其他枪管型'),
'rpk':('candidate','5beed0f50db834001c062b12','TaCZ7.62x39 RPK与EFT5.45 RPK16不同'),
'scar_h':('retain','6183afd850224f204c1da514','名称和口径对应；颜色另核'),
'scar_l':('retain','6184055050224f204c1da540','名称和口径对应；颜色另核'),
'sks_tactical':('candidate','574d967124597745970e7c94','SKS改装配置需映射，不直接等同基础枪'),
'spr15hb':('candidate','5d43021ca4b9362eab4b5e25','SPR15与TX15为候选近似，不能自动等同'),
'ump45':('retain','5fc3e272f8b6a877a729eac5','名称和口径对应'),
'uzi':('retain','66992b349950f5f4cd06029f','快照有原型UZI，不限于UZI PRO'),
'vector45':('candidate','5fb64bc92b1b027b1f50bcf2','.45一致；需核对Gen2配置')}
byid={x['id']:x for x in w}
files=sorted(p for p in pack.rglob('*') if p.is_file());texts={str(p.relative_to(pack)):p.read_text(errors='replace') for p in files if p.suffix in ['.json','.lua']}
# Build resolvable explicit resource references. Dynamic Lua/bytecode remains a declared gap.
logical=collections.defaultdict(list)
for p in files:
 rel=p.relative_to(pack); parts=rel.parts
 if len(parts)>3 and parts[0] in ('assets','data'):
  tail='/'.join(parts[3:]); stem=tail.rsplit('.',1)[0]
  logical[parts[1]+':'+stem].append(str(rel))
  if parts[2] in ('index','data','display') and len(parts)>4: logical[parts[1]+':'+stem.split('/',1)[1]].append(str(rel))
refs={path:sorted(set(re.findall(r'(?<![\w])(?:tacz|minecraft):[a-zA-Z0-9_./-]+',text))) for path,text in texts.items()}
rows=[];owners=collections.defaultdict(set)
for p in sorted((pack/'data/tacz/index/guns').glob('*.json')):
 idx=read(p);d=read(pack/'data/tacz/data/guns'/(idx['data'].split(':')[1]+'.json'));disp=read(pack/'assets/tacz/display/guns'/(idx['display'].split(':')[1]+'.json'))
 name=p.stem;status,eid,note=match.get(name,('exclude' if 'tacz:'+name in sel['exclude_ids'] else 'not_found','','既有隔离项，保留原排除决定' if 'tacz:'+name in sel['exclude_ids'] else '177条本地来源并集中未找到同款；不是对最新线上版本的断言'))
 seeds={str(p.relative_to(pack)), 'data/tacz/data/guns/'+idx['data'].split(':')[1]+'.json','assets/tacz/display/guns/'+idx['display'].split(':')[1]+'.json'}
 seen=set();todo=list(seeds)
 while todo:
  a=todo.pop()
  if a in seen:continue
  seen.add(a)
  for ref in refs.get(a,[]):todo.extend(q for q in logical.get(ref,[]) if q not in seen)
 for a in seen:owners[a].add(name)
 target=byid.get(eid)
 rows.append(dict(id='tacz:'+name,name=lang.get(idx['name'],idx['name']),ammo=d['ammo'],previous='retain' if 'tacz:'+name in sel['retain_ids'] else 'exclude',proposal=status,reason=note,eft=None if not target else {k:target.get(k) for k in ['id','name_en','name_zh','caliber','source_kind','base_properties','provenance']},index=idx,display_summary={k:disp.get(k) for k in ['model','texture','animation','state_machine','use_default_animation','player_animator_3rd','sounds']},explicit_resource_closure=sorted(seen),unresolved_ids=sorted({r for a in seen for r in refs.get(a,[]) if r not in logical})))
for g in rows:
 n=g['id'].split(':')[1];g['single_gun_references']=[a for a in g['explicit_resource_closure'] if owners[a]=={n}];g['shared_gun_references']={a:sorted(owners[a]) for a in g['explicit_resource_closure'] if len(owners[a])>1}
inputs=['docs/参考资料/ammunition-audit/adoption/eft-weapons.json','docs/参考资料/ammunition-audit/adoption/approved-ammunition.json','TACZ-1.21.1/docs/content-isolation/selection.json']
manifest={f:hashlib.sha256((root/f).read_bytes()).hexdigest() for f in inputs}
(out/'guns.json').write_text(json.dumps({'date':'2026-09-13','scope':'local snapshot comparison; proposals only','input_sha256':manifest,'counts':dict(collections.Counter(g['proposal'] for g in rows)),'graph_limit':'Explicit text references only. Single-gun reference is NOT proof of exclusive ownership or deletion safety; Lua bytecode, dynamic names, shared attachments and external packs require review.','guns':rows},ensure_ascii=False,indent=2)+'\n')
(out/'resources.json').write_text(json.dumps([{'path':str(p.relative_to(pack)),'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'explicit_gun_referrers':sorted(owners.get(str(p.relative_to(pack)),[])),'text_ids':refs.get(str(p.relative_to(pack)),[])} for p in files],ensure_ascii=False,indent=2)+'\n')
with (out/'guns.csv').open('w') as f:
 writer=csv.writer(f);writer.writerow(['TaCZ ID','名称','原ammo_id','旧选择','本次建议','EFT候选ID','EFT候选名称','理由','单枪引用文件数_非删除许可','共享引用文件数'])
 for g in rows:writer.writerow([g['id'],g['name'],g['ammo'],g['previous'],g['proposal'],(g['eft'] or {}).get('id',''),(g['eft'] or {}).get('name_en',''),g['reason'],len(g['single_gun_references']),len(g['shared_gun_references'])])
md=['# 逐枪盘点（本地快照）','','2026-09-13。仅盘点提案，没有改变选择或资源。retain=名称口径对应建议保留；candidate=相近型号待判定；exclude=沿用既有排除；not_found=本地177条来源未找到同款。','', '|TaCZ ID / 名称|口径ID|建议|EFT对应或候选|依据|','|---|---|---|---|---|']
for g in rows:md.append('|'+ '|'.join([g['id']+' / '+g['name'],g['ammo'],g['proposal'],(g['eft'] or {}).get('name_en','—'),g['reason']])+'|')
(out/'逐枪盘点.md').write_text('\n'.join(md)+'\n')
ammo=json.loads((root/inputs[1]).read_text())['items']; grouped=collections.Counter(x['project_caliber'] for x in ammo)
(out/'ammunition.json').write_text(json.dumps({'count':len(ammo),'calibers':dict(grouped),'items':[{'id':x['id'],'name':x['name_zh'],'caliber':x['project_caliber'],'provenance':x['provenance']} for x in ammo]},ensure_ascii=False,indent=2)+'\n')
print('Guns',len(rows),collections.Counter(g['proposal'] for g in rows),'resources',len(files),'ammo',len(ammo),grouped)
