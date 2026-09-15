"""Render diagnostic geometry directly; optional Pillow required."""
import json,math
from pathlib import Path
from PIL import Image,ImageDraw
p=Path(__file__).resolve().parent;m=json.loads((p/'manifest.json').read_text())['models'];sc=json.loads((p/'scenarios.json').read_text())['scenarios'];im=Image.new('RGB',(1500,980),'#111820');d=ImageDraw.Draw(im);colors=['#667d97','#e59e5b','#67b69d','#dd6f68','#91a9e6','#c397d3','#d2c16a','#72bac7']
d.text((25,15),'WEAPON ASSEMBLY / ORIGINAL DIAGNOSTIC GEOMETRY / NOT REAL DIMENSIONS',fill='white')
for idx,s in enumerate(sc):
 ox=(idx%3)*500;oy=(idx//3)*460+50;d.rectangle((ox+10,oy,ox+490,oy+445),fill='#1b2633');faces=[]
 for inst in s['placements']:
  model=next(a for a in m if a['name']==inst['model'])
  for b in model['boxes']:
   vertices=[]
   for c in [(0,0,0),(1,0,0),(1,1,0),(0,1,0),(0,0,1),(1,0,1),(1,1,1),(0,1,1)]:
    x,y,z=[b[n][j]+inst['position'][j] for j,n in enumerate(c)];a=.65;xx=x*math.cos(a)+z*math.sin(a);zz=-x*math.sin(a)+z*math.cos(a);vertices.append((ox+220+xx*25,oy+165+(-y+zz*.35)*25,zz))
   for f in [(0,3,2,1),(4,5,6,7),(0,1,5,4),(3,7,6,2),(0,4,7,3),(1,2,6,5)]:faces.append((sum(vertices[k][2] for k in f)/4,[vertices[k][:2] for k in f],colors[m.index(model)%len(colors)]))
 for _,pts,col in sorted(faces,key=lambda f:-f[0]):d.polygon(pts,fill=col,outline='#111820')
 d.text((ox+25,oy+15),s['id'],fill='white');d.text((ox+25,oy+395),'valid='+str(s['expectValid'])+' complete='+str(s['expectComplete']),fill='#eeeeee');d.text((ox+25,oy+415),', '.join(s['expectedErrorCodes']) or 'Valid source-data assembly',fill='#edb86a')
im.save(p/'overview.png')
