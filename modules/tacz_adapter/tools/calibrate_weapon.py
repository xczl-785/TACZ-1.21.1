"""Offline authoring report: mesh envelopes, contacts, sight axes and eye relief.
Not a Minecraft screenshot or runtime acceptance. Reads packaged truth, never source masters.
"""
import argparse
import html
import json
import math
from pathlib import Path
import numpy as np
from build_presentation import read

MODULE=Path(__file__).resolve().parents[1]
def frames(author):
    runtime=read(author/'runtime.json');ns,gun=runtime['gunId'].split(':');folder=runtime['resourceDirectory'];res=MODULE/'weapon-content/resources'
    geometry={m['definitionId']:m for m in read(res/'assets'/ns/folder/'manifest.json')['models']}
    nodes=read(res/'data'/ns/folder/'scene.json')['nodes'];byid={n['instanceId']:n for n in nodes};located={}
    def locate(node):
        id=node['instanceId']
        if id in located:return located[id]
        model=geometry[node['definitionId']]
        if 'parentId' not in node:origin=-np.array(model['attachmentOrigin'],float);path=''
        else:
            parent=byid[node['parentId']];p,path=locate(parent)
            origin=p+np.array(geometry[parent['definitionId']]['slots'][node['slot']])-np.array(model['attachmentOrigin']);path=path+'/'+node['slot']
        located[id]=(origin,path);return origin,path
    for node in nodes:locate(node)
    return runtime,geometry,nodes,located

def rotation(degrees):
    x,y,z=np.radians(degrees);sx,cx=math.sin(x),math.cos(x);sy,cy=math.sin(y),math.cos(y);sz,cz=math.sin(z),math.cos(z)
    return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])

def hull(points):
    p=sorted(set(map(tuple,points)))
    if len(p)<3:return p
    def cross(a,b,c):return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
    lo=[];hi=[]
    for v in p:
        while len(lo)>=2 and cross(lo[-2],lo[-1],v)<=0:lo.pop()
        lo.append(v)
    for v in reversed(p):
        while len(hi)>=2 and cross(hi[-2],hi[-1],v)<=0:hi.pop()
        hi.append(v)
    return lo[:-1]+hi[:-1]

def report(author,destination):
    runtime,geometry,nodes,located=frames(author);markers=read(author/'markers.json')['parts'];rows=[];envelopes=[]
    for node in nodes:
        definition=node['definitionId'];origin,path=located[node['instanceId']];m=geometry[definition]
        vertices=np.array([v for mesh in m['meshes'] for t in mesh['triangles'] for v in t['vertices']])+origin
        envelopes.append(vertices)
        for kind,values in markers.get(definition,{}).items():
            for value in values:
                position=origin+value['position'];row=dict(path=path,definition=definition,kind=value.get('kind',value.get('role')),position=position.tolist())
                if kind=='sights':
                    axis=rotation(value['rotation']);row.update(id=value['id'],eye=(position-axis[:,2]*value['eyeDistance']).tolist(),eyeDistance=value['eyeDistance'],forward=axis[:,2].tolist())
                rows.append(row)
    allpoints=np.concatenate(envelopes+[np.array([r['eye'] for r in rows if 'eye' in r])]);zmin,zmax=allpoints[:,2].min()-4,allpoints[:,2].max()+4;ymin,ymax=allpoints[:,1].min()-4,allpoints[:,1].max()+4
    width=1100;height=420;scale=min((width-70)/(zmax-zmin),(height-60)/(ymax-ymin))
    def point(v):return (35+(v[2]-zmin)*scale,height-30-(v[1]-ymin)*scale)
    svg=[]
    for points in envelopes:
        pairs=hull([point(p) for p in points]);svg.append('<polygon points="'+' '.join(f'{x:.2f},{y:.2f}' for x,y in pairs)+'" fill="#434e59" fill-opacity=".48" stroke="#8193a5" stroke-width=".6"/>')
    for row in rows:
        x,y=point(row['position']);color='#ffcb6b' if row['kind'] in ('left','right') else '#56d8ce'
        if 'eye' in row and row['eyeDistance']>0:
            ex,ey=point(row['eye']);fx,fy=point(np.array(row['position'])+np.array(row['forward'])*20)
            svg.append(f'<path d="M {ex},{ey} L {fx},{fy}" stroke="{color}" stroke-dasharray="6 4"/><circle cx="{ex}" cy="{ey}" r="6" fill="none" stroke="{color}"><title>眼睛 / eye</title></circle>')
        title=html.escape(row['path']+' '+row['kind']+' '+str([round(v,2) for v in row['position']]))
        svg.append(f'<circle cx="{x}" cy="{y}" r="4" fill="{color}"><title>{title}</title></circle><text x="{x+6}" y="{y-8}" fill="{color}" font-size="12">{row["kind"]}</text>')
    output={'gun':runtime['gunId'],'scope':'offline authoring, not game acceptance','markers':rows}
    destination.parent.mkdir(parents=True,exist_ok=True);destination.with_suffix('.json').write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n')
    table=''.join('<tr><td>'+html.escape(r['path'])+'</td><td>'+r['kind']+'</td><td>'+', '.join(f'{v:.2f}' for v in r['position'])+'</td><td>'+str(r.get('eyeDistance','—'))+'</td></tr>' for r in rows)
    destination.write_text(f'''<!doctype html><meta charset="utf-8"><title>{runtime['gunId']} 定位校准</title><style>body{{background:#18212b;color:#e0e8ef;font:15px system-ui;max-width:1150px;margin:36px auto}}svg{{width:100%;background:#111922;border-radius:12px}}td,th{{padding:10px;text-align:left;border-bottom:1px solid #3a4653}}td:first-child{{font:12px monospace}}p{{line-height:1.8}}</style><h1>{runtime['gunId']} · 定位校准</h1><p>静态数据报告，不是游戏截图。轮廓是各配件侧面包络；青色实点为瞄准点、空心圆为眼睛，黄色为手掌接触点。悬停显示装配路径。坐标为已烘焙美术单位。</p><svg viewBox="0 0 {width} {height}">{''.join(svg)}</svg><table><tr><th>装配路径</th><th>用途</th><th>整枪坐标 x, y, z</th><th>眼距</th></tr>{table}</table><p>编辑 weapon-authoring/{author.name}/markers.json 后通过 weapon_pipeline.py 隔离生成、检查并更新生产内容，再生成本报告。运行握持姿势见 handling.json；其参考后坐力保持固定，修改装配时不重新归一化。</p>''',encoding='utf-8')
    print('Calibration:',destination)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('author',type=Path);parser.add_argument('output',type=Path);args=parser.parse_args();report(args.author,args.output)
