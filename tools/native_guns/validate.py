"""Generic native gun producer contracts; offline geometry evidence, not client acceptance."""
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
import numpy as np
from PIL import Image
import produce as p

def validate(resources=p.RES,weapon=None):
    resources=Path(resources)
    if weapon is None:weapon=p.ex.read(p.DEFAULT)['weapon']
    ns,gun=weapon['gunId'].split(':');base=resources/f'data/{ns}/{weapon["resourceDirectory"]}';assets=resources/f'assets/{ns}'
    source_root=p.R/'modules/tacz_adapter/weapon-sources'/weapon['authoringSource'];config=p.ex.read(source_root/'production.json');p.validate_configuration(config)
    _,_,_,original_display,original_path,original_texture,original_data=p.inputs(config)
    assert p.ex.read(base/'weapon.json')==weapon==config['weapon']
    report=p.ex.read(base/'geometry-evidence.json');assert report==p.ex.read(source_root/'build-report.json')
    for name,sha in report['sourceHashes'].items():assert p.ex.sha(p.R/name)==sha,name
    assert p.ex.read(resources/f'data/{ns}/data/guns/{gun}.json')==p.ex.read(original_data)
    display=p.ex.read(assets/f'display/guns/{gun}.json');assert display['model_type']==weapon['modelType']
    for key,value in original_display.items():
        if key not in ('model','texture','lod','model_type'):assert display[key]==value,key
    mapping=p.ex.read(base/'mapping.json');catalog={q['id']:q for q in p.ex.read(base/'catalog.json')['parts']};external=p.ex.read(base/'native_attachments.json')
    models={q['definitionId']:q for q in p.ex.read(base/'preview.json')['models']};assert set(mapping)==set(catalog)==set(models)
    mounts_path=source_root/'mounts.json'
    mounts=p.load_mounts(mounts_path,list(catalog.values()),config['rootDefinition'])
    assert str(mounts_path.relative_to(p.R)) in report['sourceHashes'],'Missing authored mount fingerprint'
    for definition,frame in mounts.items():
        assert models[definition]['slots']==frame['slots']
        assert models[definition]['attachmentOrigin']==frame['attachmentOrigin']
    assert set(external.values())<=set(mapping)
    for part in catalog.values():
        for slot in part['slots']:assert set(slot['allowedParts'])<=set(mapping)
    scene=p.ex.read(base/'scene.json')['nodes'];assert len({n['instanceId'] for n in scene})==len(scene)
    assert sum('parentId' not in n for n in scene)==1
    for name,key in [('native-profile.json','nativeProfile'),('native-visual-rules.json','visualRules')]:assert p.ex.read(base/name)==config[key]
    original=p.ex.read(original_path)['minecraft:geometry'][0];native={b['name']:b for b in original['bones']}
    rules=config['visualRules']
    dependent=set(rules.get('boneRequirements',{}))|set(rules.get('boneAllRequirements',{}))
    assert (dependent|set(rules['alwaysVisibleBones']))<=set(native),'Visual rule references a nonexistent native bone: '+gun
    assert not dependent&set(rules['alwaysVisibleBones']),'Conflicting native bone visibility rules: '+gun
    high=p.ex.read(assets/f'geo_models/gun/{gun}.json')['minecraft:geometry'][0];low=p.ex.read(assets/f'geo_models/gun/lod/{gun}.json')['minecraft:geometry'][0]
    lowbones={b['name']:b for b in low['bones']}
    assert len(lowbones)==len(low['bones'])
    if not report.get('lod'):assert high==low,'Declared full rig LOD fallback must match high geometry'
    bones={b['name']:b for b in high['bones']};assert len(bones)==len(high['bones'])
    for lookup in (bones,lowbones):
        for name,bone in native.items():assert {k:v for k,v in bone.items() if k!='cubes'}=={k:v for k,v in lookup[name].items() if k!='cubes'},name
    assert set(lowbones)==set(bones)
    for name,bone in bones.items():
        assert {k:v for k,v in bone.items() if k!='cubes'}=={k:v for k,v in lowbones[name].items() if k!='cubes'}
        if name in native:assert lowbones[name].get('cubes',[])==bone.get('cubes',[])
        if bone.get('cubes'):assert lowbones[name].get('cubes'),'LOD omitted complete geometry bone: '+name
    for key,evidence in report.get('lod',{}).items():
        d,variant=key.split('/')
        assert evidence['lowCubes']<=evidence['highCubes']
        assert max(evidence['silhouetteLoss'])<=config['lod']['maxSilhouetteLoss']
        for metric in evidence['textureComparisons']:
            assert metric['meanError']<=config['lod']['maxTextureMeanError']
            assert metric['changedPixelFraction']<=config['lod']['maxTextureChangedFraction']
        for name,indices in evidence['selectedIndices'].items():
            leaf='assembly_'+d+'_'+name
            assert indices and lowbones[leaf]['cubes']==[bones[leaf]['cubes'][i] for i in indices]
    assert report['lowCubes']==sum(len(b.get('cubes',[])) for b in lowbones.values())
    for bone in bones.values():
        if bone.get('parent'):assert bone['parent'] in bones
    batches=p.ex.read(base/'batches.json');owned={part['definitionId'] for part in report['parts']}
    assert {b['definition'] for b in batches.values()}==owned
    assert set(batches)==set(bones)-set(native)
    assert not set(config['visualRules']['alwaysVisibleBones']) & set(config['presentationOnly'])
    atlas=Image.open(assets/f'textures/gun/{gun}.png').convert('RGBA');unit=report['atlasUnit'];anchors=p.ex.read(base/'workbench-anchors.json')['anchors']
    maximum=0;seen=set()
    for part in report['parts']:
        row=part['row'];root=p.R/part['sourceRoot'];d=row['definitionId'];assert p.ex.sha(root/row['model'])==part['modelSha256'];assert p.ex.sha(root/row['texture'])==part['textureSha256']
        _,decoded,uv,texture,_=p.shared.load_part(row,root);cx,cy=part['atlasCell']
        assert atlas.crop((cx,cy,cx+unit,cy+unit)).tobytes()==texture.resize((unit,unit),Image.Resampling.NEAREST).tobytes()
        triangles={m['name']:m['triangles'] for m in models[d]['meshes']}
        for name,indices in row['sourceCubeIndices'].items():
            for index in indices:
                assert (name,index) not in seen,(name,index);seen.add((name,index))
            if not decoded[name].get('cubes'):continue
            leaf='assembly_'+d+'_'+name;assert batches[leaf]['definition']==d
            assert name.endswith('_illuminated')==leaf.endswith('_illuminated')
            flattened=[]
            for i,cube in enumerate(decoded[name]['cubes']):
                vv,ff=p.ex.cube_geometry(decoded[name],cube,decoded,np.eye(4));held,hf=p.ex.cube_geometry(bones[leaf],bones[leaf]['cubes'][i],bones,np.eye(4))
                maximum=max(maximum,float(np.max(np.abs(vv-held))));assert np.allclose(vv,held,atol=1e-8)
                for (ids,uvs),(held_ids,held_uvs) in zip(ff,hf):
                    assert ids==held_ids
                    assert np.allclose(np.asarray(uvs)/uv,(np.asarray(held_uvs)-[cx,cy])/unit,atol=1e-10)
                    flattened.append((vv[ids],np.asarray(uvs)/uv))
                if p.ex.sha(root/row['model'])==row['baselineModelSha256']:
                    source=p.ex.read(p.R/row['sourceGeometry'])['minecraft:geometry'][0];lookup={b['name']:b for b in source['bones']}
                    original_v,original_uv=p.ex.cube_geometry(lookup[name],lookup[name]['cubes'][indices[i]],lookup,np.eye(4))
                    assert np.allclose(vv,original_v,atol=1e-8) and ff==original_uv,d
            if config.get('sourceBoneVariants',{}).get(name) in config.get('previewHiddenVariants',[]):
                assert name not in triangles
                continue
            for tri,(verts,uvs) in zip(triangles[name],flattened):
                assert np.allclose(np.asarray(tri['vertices'])+anchors[d],verts,atol=1e-8)
                assert np.allclose(tri['uv'],uvs,atol=1e-10)
            assert len(triangles[name])==len(flattened)
    presentation={(n,i) for n,indices in config['presentationOnly'].items() for i in indices}
    all_cubes={(b['name'],i) for b in original['bones'] for i in range(len(b.get('cubes',[])))}
    assert not seen&presentation and seen|presentation==all_cubes
    assert report['nativeRigBones']==len(native) and report['highCubes']==sum(len(b.get('cubes',[])) for b in bones.values())
    for target in ('icon_geometry.json','icon_library.json','icon_materials.json'):assert not (assets/gun/target).exists()
    library=p.ex.read(base/'library.json')['materials'];bindings=p.ex.read(base/'materials.json')['parts']
    for d,model in models.items():
        assert model['meshes'] and all(m['triangles'] for m in model['meshes'])
        assert set(model['slots'])=={s['id'] for s in catalog[d]['slots']}
        texture=library[bindings[d]['defaultMaterial']]['texture'];assert (resources/'assets'/texture.replace(':','/')).is_file()
        assert (assets/weapon['partIconDirectory']/f'{d}.png').is_file()
        if mapping[d] not in external:
            item=mapping[d].split(':')[1];assert (resources/f'data/{ns}/item_foundation/items/{item}.json').is_file();assert (assets/f'models/item/{item}.json').is_file()
    assert p.ex.read(assets/f'models/item/{gun}.json')['parent']=='builtin/entity'
    overrides=p.ex.read(base/'native_attachment_overrides.json')
    for aid,override in overrides.items():
        assert aid in external
        assert ('lodModel' in override)==('lodTexture' in override)
        for key,folder,suffix in [('model','geo_models','.json'),('texture','textures','.png'),('lodModel','geo_models','.json'),('lodTexture','textures','.png')]:
            if key in override:
                ns0,path=override[key].split(':');assert (resources/f'assets/{ns0}/{folder}/{path}{suffix}').is_file()
    print('Native gun contract PASS:',gun,len(owned),'editable parts;',len(catalog),'catalog;',len(native),'native bones; maximum geometry error',maximum)
    return report

if __name__=='__main__':validate()
