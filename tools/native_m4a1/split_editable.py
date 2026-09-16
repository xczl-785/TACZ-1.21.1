"""One-time import of an accepted Blockbench cube assembly into editable components."""
import argparse,copy,json,shutil,hashlib
from pathlib import Path
R=Path(__file__).resolve().parents[2]
DEFAULT=R/'modules/tacz_adapter/weapon-sources/native_m4a1/editable'
def split(source,output):
    source=Path(source);output=Path(output);output.mkdir(parents=True,exist_ok=True)
    if (output/'manifest.json').exists():raise ValueError('Editable source exists; refusing to overwrite edits')
    model=json.loads(source.read_text());groups={g['uuid']:g for g in model['groups']}
    rows=json.loads((source.parent/'来源与分件.json').read_text());entries=[]
    for row,root in zip(rows,model['outliner']):
        ids=set()
        def visit(n):
            ids.add(n if isinstance(n,str) else n['uuid'])
            if isinstance(n,dict):
                for child in n['children']:visit(child)
        visit(root)
        meta=json.loads(Path(row['componentMetadata']).read_text());center=meta['boundsCenter'];center=[round(center[0],5),round(center[1],5),round(-center[2],5)]
        d=copy.deepcopy(model);d['name']=row['group'];d['outliner']=[copy.deepcopy(root)];d['groups']=[copy.deepcopy(g) for g in model['groups'] if g['uuid'] in ids];d['elements']=[copy.deepcopy(e) for e in model['elements'] if e['uuid'] in ids]
        index=1 if row['definitionId']=='tacz_stock_tactical_ar' else 0;t=copy.deepcopy(model['textures'][index]);t['id']='0';t['name']='texture.png';t['path']='texture.png';t['relative_path']='texture.png';d['textures']=[t];d['resolution']={'width':t['uv_width'],'height':t['uv_height']}
        for e in d['elements']:
            for key in ['from','to','origin']:
                e[key]=[round(v-center[a],5) for a,v in enumerate(e[key])]
            for f in e['faces'].values():
                if f['texture'] is not None:f['texture']=0
        for g in d['groups']:g['origin']=[round(v-center[a],5) for a,v in enumerate(g['origin'])]
        dest=output/'components'/row['definitionId'];dest.mkdir(parents=True)
        (dest/'model.bbmodel').write_text(json.dumps(d,ensure_ascii=False,separators=(',',':'))+'\n')
        shutil.copyfile(source.parent/row['texture'],dest/'texture.png')
        row={**row,'sourceGeometry':str(Path(row['sourceGeometry']).relative_to(R)),'componentMetadata':str(Path(row['componentMetadata']).relative_to(R))}
        entries.append({**row,'baselineModelSha256':hashlib.sha256((dest/'model.bbmodel').read_bytes()).hexdigest(),'model':str((dest/'model.bbmodel').relative_to(output)),'texture':str((dest/'texture.png').relative_to(output)),'editorCenter':center,'uvSize':[t['uv_width'],t['uv_height']]})
    manifest={'schemaVersion':1,'coordinateSystem':'Blockbench Bedrock editor X-reflection; local centered parts; editorCenter restores assembly coordinates','parts':entries}
    (output/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    print('Created',len(entries),'editable parts')
if __name__=='__main__':
    a=argparse.ArgumentParser();a.add_argument('source');a.add_argument('--output',type=Path,default=DEFAULT);args=a.parse_args();split(args.source,args.output)
