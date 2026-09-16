"""Author actual per-gun magazine variants; level items have no standalone model.
Only prepares isolated sources/assets. Does not change native gun rigs or compatibility.
"""
import argparse,copy,json
from pathlib import Path
import extract as shared
im=shared.im;ex=im.ex;R=shared.R
ROOT=R/'modules/tacz_adapter/weapon-sources/native_attachments/magazine_variants'
RES=R/'modules/tacz_adapter/weapon-content/resources'
DATA=ex.SRC/'data/tacz'
IDS={f'tacz:{family}_extended_mag_{level}' for family in ('light','sniper','shotgun') for level in (1,2,3)}

def allowed(gun):
    paths=set()
    def expand(ref,active=()):
        if not ref.startswith('#'):return {ref}
        if ref in active:raise ValueError('Cyclic attachment tag '+ref)
        ns,name=ref[1:].split(':');path=ex.SRC/f'data/{ns}/tacz_tags/attachments/{name}.json';paths.add(path)
        return set().union(*(expand(v,active+(ref,)) for v in ex.read(path)))
    return expand('#tacz:allow_attachments/'+gun),paths

def candidates():
    result=[]
    for index in sorted((DATA/'index/guns').glob('*.json')):
        matches,tags=allowed(index.stem)
        for attachment in sorted(IDS&matches):result.append((index,attachment,tags))
    return result

def extract(index,attachment,tags):
    gun=index.stem;item=attachment.split(':')[1];idx=ex.read(index)
    display=ex.asset(idx['display'],'display/guns','.json');dp=ex.read(display)
    data=DATA/'data/guns'/f"{idx['data'].split(':')[1]}.json";gd=ex.read(data)
    if 'extended_mag' not in gd['allow_attachment_types']:raise ValueError('Magazine type disabled '+gun)
    attachment_index=DATA/'index/attachments'/f'{item}.json';ai=ex.read(attachment_index)
    attachment_data=DATA/'data/attachments'/f"{ai['data'].split(':')[1]}.json"
    attachment_display=ex.asset(ai['display'],'display/attachments','.json')
    if ex.read(attachment_display).get('model'):raise ValueError('Expected gun-owned magazine geometry')
    level=ex.read(attachment_data)['extended_mag_level'];variant='mag_extended_'+str(level)
    source=ex.asset(dp['model'],'geo_models','.json');original=ex.read(source);geo=original['minecraft:geometry'][0]
    bones={b['name']:b for b in geo['bones']}
    if variant not in bones:raise ValueError('Missing visible variant '+gun+' '+variant)
    def descendants(name):return {name}|set().union(*(descendants(b['name']) for b in bones.values() if b.get('parent')==name))
    geometry_nodes=descendants(variant)
    selected=set().union(*(set(ex.ancestors(n,bones)) for n in geometry_nodes))
    selected_geo=copy.deepcopy(geo);selected_geo['bones']=[copy.deepcopy(b) for b in geo['bones'] if b['name'] in selected]
    for b in selected_geo['bones']:
        if b['name'] not in geometry_nodes:b.pop('cubes',None)
    texture=ex.asset(dp['texture'],'textures','.png');definition='tacz_'+gun+'_'+item
    row,model=shared.encode(selected_geo,texture,definition)
    animation=ex.asset(dp['animation'],'animations','.animation.json');anim=ex.read(animation)
    active={n for a in anim['animations'].values() for n in a.get('bones',{})}
    paths={index,display,data,source,texture,animation,attachment_index,attachment_data,attachment_display}|tags
    if gd.get('script'):
        script=DATA/'scripts'/f"{gd['script'].split(':')[1]}.lua"
        if script.exists():paths.add(script)
    auxiliary=[]
    for suffix in ('_n','_s'):
        p=texture.with_name(texture.stem+suffix+'.png')
        if p.exists():paths.add(p);auxiliary.append({'source':str(p.relative_to(R)),'file':f'components/{definition}/texture{suffix}.png'})
    row.update(gunId='tacz:'+gun,attachmentId=attachment,level=level,variantBone=variant,
       sourceGeometry=str(source.relative_to(R)),sourceTexture=str(texture.relative_to(R)),
       sourceSha256=ex.sha(source),sourceTextureSha256=ex.sha(texture),
       sourceHashes={str(p.relative_to(R)):ex.sha(p) for p in sorted(paths)},
       model=f'components/{definition}/model.bbmodel',texture=f'components/{definition}/texture.png',
       preservedBones=[b['name'] for b in selected_geo['bones']],auxiliaryTextures=auxiliary,
       motionOwnership={n:[v for v in ex.ancestors(n,bones) if v in active] for n in sorted(geometry_nodes) if bones[n].get('cubes')},
       nativeAnimation=dp['animation'],nativeGunData=idx['data'],nativeReloadScript=gd.get('script'),
       capacity=gd['extended_mag_ammo_amount'][level-1],runtimeMode='native_gun_bone_variant',
       coordinateSpace='native gun rig; preserve pivots and parents; no attachment mount transform',
       integrationStatus='prepared; target gun assembly integration pending',
       note='Tube extension, not a detachable magazine' if gun=='m870' else 'Gun-specific level variant; retain native reload bone ownership')
    return row,model,texture

def append(root=ROOT):
    root=Path(root);p=root/'manifest.json';manifest=ex.read(p) if p.exists() else {'schemaVersion':1,'parts':[]}
    existing={r['definitionId'] for r in manifest['parts']}
    for index,attachment,tags in candidates():
        definition='tacz_'+index.stem+'_'+attachment.split(':')[1]
        if definition in existing:continue
        if (root/'components'/definition).exists():raise ValueError('Unregistered edit directory '+definition)
        row,model,texture=extract(index,attachment,tags)
        im.write(root/row['model'],model);(root/row['texture']).write_bytes(texture.read_bytes())
        for extra in row['auxiliaryTextures']:(root/extra['file']).write_bytes((R/extra['source']).read_bytes())
        row['baselineModelSha256']=ex.sha(root/row['model']);manifest['parts'].append(row)
    im.write(p,manifest);return manifest

def build(root=ROOT,resources=RES):
    root=Path(root);resources=Path(resources);manifest=ex.read(root/'manifest.json');entries=[]
    for row in manifest['parts']:
        _,bones,uv,_,count=shared.load_part(row,root)
        geo=copy.deepcopy(ex.read(R/row['sourceGeometry']))
        geo['minecraft:geometry'][0]['bones']=[bones[n] for n in row['preservedBones']]
        geo['minecraft:geometry'][0]['description'].update(identifier='geometry.native_magazine.'+row['definitionId'],texture_width=uv[0],texture_height=uv[1])
        path='gun_parts/'+row['definitionId']
        im.write(resources/('assets/tacz_assembly/geo_models/'+path+'.json'),geo)
        p=resources/('assets/tacz_assembly/textures/'+path+'.png');p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((root/row['texture']).read_bytes())
        for extra in row['auxiliaryTextures']:
            suffix=Path(extra['file']).stem.removeprefix('texture')
            p.with_name(p.stem+suffix+'.png').write_bytes((root/extra['file']).read_bytes())
        entries.append({k:row[k] for k in ('definitionId','gunId','attachmentId','level','variantBone','preservedBones','motionOwnership','nativeAnimation','nativeReloadScript','capacity','runtimeMode','integrationStatus')}
                       |{'model':'tacz_assembly:'+path,'texture':'tacz_assembly:'+path,'cubes':count})
    im.write(resources/'data/tacz_assembly/native_attachments/magazine_variants.json',{'schemaVersion':1,'variants':entries})
    print('Prepared magazine variants:',len(entries),'cubes:',sum(e['cubes'] for e in entries))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--append',action='store_true');args=parser.parse_args()
    if args.append:append()
    build()
