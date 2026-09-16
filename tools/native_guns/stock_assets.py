"""Reuse reviewed M4 stock authoring in native attachment-local coordinates.
No weapon mount transform is baked here; each target gun supplies its own stock_pos.
"""
import argparse,copy,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'native_attachments'))
import extract as shared
R=shared.R;ex=shared.im.ex
SOURCE=R/'modules/tacz_adapter/weapon-sources/native_m4a1/editable'
RESOURCES=R/'modules/tacz_adapter/weapon-content/resources'
CATALOG=Path('data/tacz_assembly/native_attachments/stocks.json')

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')

def selected_rows(root=SOURCE):
    rows=[]
    for row in ex.read(Path(root)/'manifest.json')['parts']:
        if '/attachment/' not in row.get('sourceGeometry',''):continue
        metadata=ex.read(R/row['componentMetadata']) if row.get('componentMetadata') else {}
        mount=row.get('nativeMount',metadata.get('anchorBone'))
        if mount!='stock_pos':continue
        if metadata.get('anchorBone') not in (None,'stock_pos'):raise ValueError('Conflicting stock source mount')
        aid=metadata.get('itemId',row.get('attachmentId'))
        if not aid or not aid.startswith('tacz:'):raise ValueError('Stock source lacks native item identity')
        index=ex.read(ex.SRC/f"data/tacz/index/attachments/{aid.split(':')[1]}.json")
        if index['type']!='stock':raise ValueError('Reviewed stock source is not a stock: '+aid)
        rows.append((row,aid))
    return rows

def geometry(row,root=SOURCE):
    _,bones,uv,image,count=shared.load_part(row,root)
    image.close()
    if not count:raise ValueError('Empty authored stock: '+row['definitionId'])
    model=copy.deepcopy(ex.read(R/row['sourceGeometry']))
    shape=model['minecraft:geometry'][0]
    shape['bones']=list(bones.values())
    shape['description'].update(identifier='geometry.authored_stock.'+row['definitionId'],texture_width=uv[0],texture_height=uv[1])
    return model,count

def build(resources=RESOURCES,source_root=SOURCE):
    resources=Path(resources);source_root=Path(source_root)
    attachments={};evidence=[]
    for row,aid in selected_rows(source_root):
        if aid in attachments:raise ValueError('Duplicate authored stock ID: '+aid)
        model,count=geometry(row,source_root)
        path='attachments/authored_stock/'+row['definitionId']
        write(resources/f'assets/tacz_assembly/geo_models/{path}.json',model)
        target=resources/f'assets/tacz_assembly/textures/{path}.png';target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes((source_root/row['texture']).read_bytes())
        attachments[aid]={'model':'tacz_assembly:'+path,'texture':'tacz_assembly:'+path}
        evidence.append({'definitionId':row['definitionId'],'attachmentId':aid,'sourceModel':str((source_root/row['model']).relative_to(R)) if (source_root/row['model']).is_relative_to(R) else str(source_root/row['model']),
          'sourceModelSha256':ex.sha(source_root/row['model']),'sourceTextureSha256':ex.sha(source_root/row['texture']),
          'sourceGeometry':row['sourceGeometry'],'sourceGeometrySha256':ex.sha(R/row['sourceGeometry']),
          'cubes':count,'uvSize':[model['minecraft:geometry'][0]['description'][k] for k in ('texture_width','texture_height')],
          'coordinateSpace':'native attachment local; inverse authoring translation/editorCenter applied exactly once; target gun mount not baked',
          'lodPolicy':'full authored geometry fallback; no reduced stock LOD produced in this batch'})
    result={'schemaVersion':1,'attachments':attachments,'sources':evidence}
    write(resources/CATALOG,result)
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--resources',type=Path,default=RESOURCES);args=parser.parse_args()
    result=build(args.resources);print('Exported authored stocks:',len(result['attachments']))
