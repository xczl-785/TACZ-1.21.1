"""Compile editable standalone attachments to isolated adapter assets."""
import copy,json
from pathlib import Path
import extract as source
im=source.im
OUT=source.R/'modules/tacz_adapter/weapon-content/resources'

def build(root=source.OUT,output=OUT):
    records={}
    for row in im.ex.read(Path(root)/'manifest.json')['parts']:
        _,bones,uv,_,count=source.load_part(row,root)
        original=im.ex.read(source.R/row['sourceGeometry'])
        result=copy.deepcopy(original);geo=result['minecraft:geometry'][0]
        key=row['attachmentId'].split(':')[1]
        model='attachments/'+key;texture='attachments/'+key
        geo['description']['identifier']='geometry.native_attachment.'+key
        geo['description']['texture_width'],geo['description']['texture_height']=uv
        geo['bones']=list(bones.values())
        im.write(output/'assets/tacz_assembly/geo_models'/f'{model}.json',result)
        dst=output/'assets/tacz_assembly/textures'/f'{texture}.png';dst.parent.mkdir(parents=True,exist_ok=True)
        dst.write_bytes((Path(root)/row['texture']).read_bytes())
        for aux in row['auxiliaryTextures']:
            suffix=Path(aux['file']).stem.removeprefix('texture')
            dst.with_name(dst.stem+suffix+'.png').write_bytes((Path(root)/aux['file']).read_bytes())
        records[row['attachmentId']]={'model':'tacz_assembly:'+model,'texture':'tacz_assembly:'+texture,
            'definitionId':row['definitionId'],'cubes':count,'sourceLod':row['sourceLod'],'lodPolicy':row['lodPolicy']}
    im.write(output/'data/tacz_assembly/native_attachments/standalone.json',{'schemaVersion':1,'attachments':records,
        'activation':'catalog only; assembled gun profile must explicitly opt in; native pack untouched'})
    return records

if __name__=='__main__':print(json.dumps(build()))
