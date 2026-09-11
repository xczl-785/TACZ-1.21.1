#!/usr/bin/env python3
"""Reproduce selection/dependency inventory without changing the upstream asset tree."""
import hashlib,json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PACK=ROOT/'src/main/resources/assets/tacz/custom/tacz_default_gun'
OUT=ROOT/'docs/content-isolation'
s=json.loads((OUT/'selection.json').read_text())
def read_json(path):
    # Preserve strings and escapes while removing JavaScript comments in gun JSONC.
    text=path.read_text(); out=[]; i=0; string=False
    while i<len(text):
        c=text[i]
        if string:
            out.append(c)
            if c=='\\' and i+1<len(text): i+=1;out.append(text[i])
            elif c=='"': string=False
        elif c=='"': string=True;out.append(c)
        elif text[i:i+2]=='//':
            i=text.find('\n',i)
            if i<0: break
            out.append('\n')
        elif text[i:i+2]=='/*':
            i=text.index('*/',i+2)+1;out.append(' ')
        else:out.append(c)
        i+=1
    return json.loads(''.join(out))
guns=[]
for p in sorted((PACK/'data/tacz/index/guns').glob('*.json')):
    idx=read_json(p); data=read_json(PACK/'data/tacz/data/guns'/ (idx['data'].split(':')[1]+'.json'))
    guns.append(dict(id='tacz:'+p.stem,selection='retain' if 'tacz:'+p.stem in s['retain_ids'] else 'exclude',index=str(p.relative_to(PACK)),data=idx['data'],display=idx['display'],ammo=data['ammo'],script=data.get('script')))
assert {g['id'] for g in guns} == set(s['retain_ids']+s['exclude_ids'])
ammo=sorted('tacz:'+p.stem for p in (PACK/'data/tacz/index/ammo').glob('*.json'))
recipes=[]
for p in sorted((PACK/'data/tacz/recipe').rglob('*.json')):
    d=read_json(p); result=d.get('result',{}); result_id=result.get('id')
    blocked=result.get('type')=='ammo' or result_id in s['exclude_ids']
    if blocked:
        assert p.parent.name=='ammo' or (p.parent.name=='gun' and 'tacz:'+p.stem==result_id)
    recipes.append(dict(path=str(p.relative_to(PACK)),result=result_id,type=result.get('type'),isolate=blocked))
# All files are retained: this conservative closure preserves indirect Lua/model/sound refs.
assets=[dict(path=str(p.relative_to(PACK)),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in sorted(PACK.rglob('*')) if p.is_file()]
references=[]
needles=set(s['exclude_ids']+ammo)
for p in sorted(PACK.rglob('*')):
    if p.suffix not in ('.json','.lua'):continue
    text=p.read_text(errors="replace")
    matches=sorted(x for x in needles if re.search(re.escape(x)+r'(?![\w/])',text))
    if matches:references.append(dict(path=str(p.relative_to(PACK)),ids=matches))
r=dict(base_commit='ff715d80176f9ca61f5b2e6f029864f5adf209c2',counts=dict(guns=len(guns),retain=sum(g['selection']=='retain' for g in guns),exclude=sum(g['selection']=='exclude' for g in guns),native_ammo=len(ammo),recipes=len(recipes),isolated_recipes=sum(r['isolate'] for r in recipes),preserved_files=len(assets)),guns=guns,native_ammo=ammo,additional_native_ammo=sorted(set(ammo)-set(s['discard_tacz_ammo_ids'])),recipes=recipes,references=references,preserved_assets=assets)
(OUT/'inventory.json').write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(r['counts'],indent=2))
