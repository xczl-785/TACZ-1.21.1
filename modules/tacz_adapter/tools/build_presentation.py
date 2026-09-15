"""Shared first-person authoring pass; consumes already-built geometry for any gun."""
import json
from pathlib import Path

def read(p): return json.loads(p.read_text())
def write(p,d):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(d,ensure_ascii=False,separators=(',',':'))+'\n')

def build(author, out):
    author=Path(author); out=Path(out)
    runtime=read(author/'runtime.json'); ns,gun=runtime['gunId'].split(':'); folder=runtime['resourceDirectory']
    handling=read(author/'handling.json'); markers=read(author/'markers.json')
    assets=out/'assets'/ns
    write(assets/folder/'markers.json',markers)
    write(out/'data'/ns/folder/'handling.json',handling)
    rig=read(assets/'geo_models/gun'/f'{gun}.json'); bones=rig['minecraft:geometry'][0]['bones']
    for b in bones:
        if b['name'] in ('lefthand','righthand','lefthand_pos','righthand_pos'): b['pivot']=[0,8,0]
        if b['name']=='idle_view':b['pivot']=handling['idleView']
        if b['name']=='iron_view':b['pivot']=handling['fallbackView']
    bones[:]=[b for b in bones if b['name']!='constraint']
    bones.append({'name':'constraint','parent':'root','pivot':[0,8,0]})
    write(assets/'geo_models/gun'/f'{gun}.json',rig)
    animations=read(assets/'animations'/f'{gun}.animation.json')
    idle=animations['animations']['static_idle']['bones']
    for name,key in [('lefthand','leftHand'),('righthand','rightHand')]:
        idle[name]={'rotation':handling[key]['rotation'],'scale':handling[key]['scale']}
    idle['constraint']={'rotation':[0,0,0],'position':[0,0,0]}
    # Procedural recoil owns root kick; retain mechanical shoot channels if any.
    shoot=animations['animations']['shoot']['bones']
    for name in ('root','camera','constraint'):shoot.pop(name,None)
    write(assets/'animations'/f'{gun}.animation.json',animations)
    print(f'Presentation: {runtime["gunId"]}; {len(markers["parts"])} annotated parts')

if __name__=='__main__':
    import argparse
    p=argparse.ArgumentParser();p.add_argument('author',type=Path);p.add_argument('--output',type=Path,required=True);args=p.parse_args();build(args.author,args.output)
