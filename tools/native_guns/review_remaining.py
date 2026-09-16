"""Independent resource-reference review of the ten-gun delivery (does not invoke producer validation)."""
from pathlib import Path
import json,sys
from PIL import Image
R=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(R/'tools/native_m4a1'))
import export_parts as native
SRC=R/'src/main/resources/assets/tacz/custom/tacz_default_gun'
RES=R/'modules/tacz_adapter/weapon-content/resources'
GUNS=('aa12','ai_awp','m700','m870','mk14','p90','qbz_191','scar_h','sks_tactical','uzi')
def read(p):
    # Native data allows comments; use the existing permissive reader only for decoding.
    return native.read(p)
def asset(ref,folder,suffix):
    ns,path=ref.split(':');return SRC/'assets'/ns/folder/(path+suffix)
def review():
    results=[]
    for gun in GUNS:
        index=read(SRC/'data/tacz/index/guns'/f'{gun}.json');display=read(asset(index['display'],'display/guns','.json'))
        original=read(asset(display['model'],'geo_models','.json'))['minecraft:geometry'][0]
        target=read(RES/f'assets/tacz_assembly/display/guns/{gun}.json')
        assert {k:v for k,v in display.items() if k not in ('model','texture','model_type','lod')}=={k:v for k,v in target.items() if k not in ('model','texture','model_type','lod')},gun
        ns,path=index['data'].split(':');assert read(SRC/'data'/ns/'data/guns'/(path+'.json'))==read(RES/f'data/tacz_assembly/data/guns/{gun}.json'),gun
        metadata={b['name']:{k:v for k,v in b.items() if k!='cubes'} for b in original['bones']}
        counts=[]
        for lod in ('','lod/'):
            geo=read(RES/f'assets/tacz_assembly/geo_models/gun/{lod}{gun}.json')['minecraft:geometry'][0];bones={b['name']:b for b in geo['bones']}
            assert all({k:v for k,v in bones[name].items() if k!='cubes'}==before for name,before in metadata.items()),gun
            counts.append(sum(len(b.get('cubes',[])) for b in bones.values()))
        assert counts[1]<=counts[0]
        root=R/'modules/tacz_adapter/weapon-sources'/('native_'+gun);cfg=read(root/'production.json')
        assert read(RES/f'data/tacz_assembly/{gun}/native-visual-rules.json')==cfg['visualRules']
        assert read(RES/f'data/tacz_assembly/{gun}/native-profile.json')==cfg['nativeProfile']
        report=read(root/'build-report.json');atlas=Image.open(RES/f'assets/tacz_assembly/textures/gun/{gun}.png').convert('RGBA');unit=report['atlasUnit']
        for part in report['parts']:
            texture=Image.open(R/part['sourceRoot']/part['row']['texture']).convert('RGBA');x,y=part['atlasCell']
            assert atlas.crop((x,y,x+unit,y+unit)).tobytes()==texture.resize((unit,unit),Image.Resampling.NEAREST).tobytes(),(gun,part['definitionId'])
        results.append({'gun':gun,'nativeBones':len(metadata),'highCubes':counts[0],'lowCubes':counts[1],'dataAndNonModelDisplayFieldsUnchanged':True,'nativeRigMetadataUnchanged':True,'atlasPixelCopiesExact':True,'visualAndRoutingConfigurationCurrent':True})
    return {'schemaVersion':1,'status':'passed','method':'Independent original pack versus final generated references/rig metadata/data/pixels; does not invoke produce.validate','guns':results,'clientStarted':False}
if __name__=='__main__':
    result=review();out=R/'docs/assembly-experiment/native-remaining/evidence/source-review.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
