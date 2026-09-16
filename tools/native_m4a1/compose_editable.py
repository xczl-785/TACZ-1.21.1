"""Create disposable assembled/exploded review projects from independent sources."""
import argparse,base64,copy,json
from pathlib import Path
from editable_import import EDIT,ex,write

def compose(output):
    output=Path(output);output.mkdir(parents=True,exist_ok=True);rows=ex.read(EDIT/'manifest.json')['parts']
    for exploded,filename in [(False,'01-M4A1标准整枪.bbmodel'),(True,'02-M4A1默认配件拆解.bbmodel')]:
        combined={'meta':{'format_version':'5.0','model_format':'free','box_uv':False},'name':filename[:-8],'resolution':{'width':256,'height':256},'elements':[],'groups':[],'outliner':[],'textures':[]}
        for i,row in enumerate(rows):
            m=copy.deepcopy(ex.read(EDIT/row['model']));offset=[0,(1-i//5)*14,(i%5-2)*25] if exploded else row['editorCenter']
            for e in m['elements']:
                for k in ['from','to','origin']:e[k]=[v+offset[a] for a,v in enumerate(e[k])]
                for face in e['faces'].values():
                    if face.get('texture') is not None:face['texture']=i
            for g in m['groups']:g['origin']=[v+offset[a] for a,v in enumerate(g['origin'])]
            t=m['textures'][0];t.update(id=str(i),uuid=ex.uid('review-texture/'+row['definitionId']),name=row['definitionId']+'.png',source='data:image/png;base64,'+base64.b64encode((EDIT/row['texture']).read_bytes()).decode(),path=str(EDIT/row['texture']),internal=True)
            t.pop('relative_path',None)
            combined['elements'].extend(m['elements']);combined['groups'].extend(m['groups']);combined['outliner'].extend(m['outliner']);combined['textures'].append(t)
        write(output/filename,combined)
    print('Review views:',output)
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',required=True);compose(p.parse_args().output)
