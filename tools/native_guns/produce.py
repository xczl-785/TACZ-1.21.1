"""Config-driven native gun assembly producer; source pack and other guns stay read-only."""
import argparse,copy,json,math,re,sys,uuid
from pathlib import Path
import numpy as np
from PIL import Image
TOOLS=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(TOOLS/'native_attachments'))
import extract as shared
import magazines
im=shared.im;ex=im.ex;R=im.R
RES=R/'modules/tacz_adapter/weapon-content/resources'
DEFAULT=R/'modules/tacz_adapter/weapon-sources/native_glock_17/production.json'
sys.path.insert(0,str(R/'modules/tacz_adapter/tools'))
from render_part_icon import render_part_icon
import lod

def write(p,value):
    p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def validate_configuration(config):
    adoption=(R/'modules/tacz_adapter/src/main/java/dev/tacticaltacz/GunAdoption.java').read_text()
    calibers={gun:caliber for caliber,gun_text in re.findall(r'add\(map,"([^"]+)",(.*?)\);',adoption) for gun in re.findall(r'"([^"]+)"',gun_text)}
    gun=config['sourceGun']
    for part in config['parts']:
        if not part.get('inventoryType') or len(part.get('footprint',[]))!=2:raise ValueError('Missing inventory type/footprint for '+part['definitionId'])
    if config['weapon']['caliber']!=calibers.get(gun):raise ValueError('Caliber differs from GunAdoption for '+gun)
    if config['weapon']['partIconDirectory']!='textures/item/'+gun:raise ValueError('Part icon directory must be gun-scoped: '+gun)

def inputs(config):
    gun=config['sourceGun'];index=ex.SRC/f'data/tacz/index/guns/{gun}.json';idx=ex.read(index)
    display=ex.asset(idx['display'],'display/guns','.json');dp=ex.read(display)
    source=ex.asset(dp['model'],'geo_models','.json');texture=ex.asset(dp['texture'],'textures','.png')
    data=ex.SRC/f"data/tacz/data/guns/{idx['data'].split(':')[1]}.json"
    return index,idx,display,dp,source,texture,data

def append(config,source_root):
    index,idx,display,dp,source,texture,data=inputs(config)
    original=ex.read(source)['minecraft:geometry'][0]
    edit=source_root/'editable';manifest=ex.read(edit/'manifest.json') if (edit/'manifest.json').exists() else {'schemaVersion':1,'parts':[]}
    existing={r['definitionId'] for r in manifest['parts']}
    for definition in config['parts']:
        d=definition['definitionId']
        if d in existing:continue
        if (edit/'components'/d).exists():raise ValueError('Refuse overwriting unregistered source '+d)
        selected=copy.deepcopy(original)
        for bone in selected['bones']:
            cubes=bone.pop('cubes',[]);indices=definition['sourceCubeIndices'].get(bone['name'],[])
            if indices:bone['cubes']=[cubes[i] for i in indices]
        row,model=shared.encode(selected,texture,d)
        row.update(sourceGeometry=str(source.relative_to(R)),sourceTexture=str(texture.relative_to(R)),sourceSha256=ex.sha(source),sourceTextureSha256=ex.sha(texture),sourceCubeIndices=definition['sourceCubeIndices'],runtimeMode='native_gun_bone_part')
        write(edit/row['model'],model);(edit/row['texture']).write_bytes(texture.read_bytes())
        row['baselineModelSha256']=ex.sha(edit/row['model']);manifest['parts'].append(row)
    write(edit/'manifest.json',manifest)
    return manifest

def authored_stock(row):
    if '/attachment/' not in row.get('sourceGeometry',''):return False
    mount=row.get('nativeMount')
    if mount is None and row.get('componentMetadata'):
        mount=ex.read(R/row['componentMetadata']).get('anchorBone')
    return mount=='stock_pos'

def attachment_records(config):
    allowed,tags=magazines.allowed(config['sourceGun']);records=[]
    variants=[]
    for catalog in config.get('nativeMagazineCatalogs',[])+[str((magazines.ROOT/'manifest.json').relative_to(R))]:
        root=(R/catalog).parent
        variants.extend((row,root) for row in ex.read(R/catalog)['parts'])
    sources=[]
    for catalog in config['editableAttachmentCatalogs']:
        root=(R/catalog).parent
        for row in ex.read(R/catalog)['parts']:
            if row.get('runtimeMode')=='native_attachment' or authored_stock(row):sources.append((row,root))
    for aid in sorted(allowed-set(config.get('excludedAttachments',[]))):
        path=ex.SRC/f"data/tacz/index/attachments/{aid.split(':')[1]}.json";idx=ex.read(path)
        typ=idx['type']
        if typ not in config['nativeProfile']['attachmentPaths'] and typ.upper() not in config['nativeProfile']['attachmentPaths']:continue
        display=ex.asset(idx['display'],'display/attachments','.json');dp=ex.read(display)
        attachment_data=ex.SRC/f"data/tacz/data/attachments/{idx['data'].split(':')[1]}.json"
        provenance=[path,display,attachment_data]
        if typ=='extended_mag':
            row,root=next((r,root) for r,root in variants if r['gunId']=='tacz:'+config['sourceGun'] and r['attachmentId']==aid)
            records.append({'definitionId':row['definitionId'],'attachmentId':aid,'type':typ,'row':row,'root':root,'native':True,'display':dp,'index':path,'provenance':provenance})
        else:
            d=aid.replace(':','_');found=next(((row,root) for row,root in sources if row['definitionId']==d),None)
            rec={'definitionId':d,'attachmentId':aid,'type':typ,'display':dp,'native':False,'index':path,'provenance':provenance}
            if found:rec.update(row=found[0],root=found[1])
            else:rec.update(source=ex.asset(dp['model'],'geo_models','.json'),texture=ex.asset(dp['texture'],'textures','.png'))
            records.append(rec)
    return records,tags

def exterior_scope_geometry(bones):
    """Match BedrockAttachmentModel's non-first-person scope projection.

    Optical division/ocular planes belong to special first-person passes, not to
    inventory geometry or its framing bounds. Keep every bone's transform metadata.
    """
    roots={'scope_body','ocular_ring'} & bones.keys()
    if not roots:raise ValueError('Scope has no native non-first-person body or ocular ring')
    result=copy.deepcopy(bones)
    for name,bone in result.items():
        if not roots.intersection(ex.ancestors(name,bones)):bone.pop('cubes',None)
    if not any(bone.get('cubes') for bone in result.values()):
        raise ValueError('Scope native non-first-person projection has no geometry')
    return result

def mesh_for(d,bones,uvsize,texture,extra):
    meshes=[];points=[]
    for name,bone in bones.items():
        triangles=[]
        for cube in bone.get('cubes',[]):
            vv,ff=ex.cube_geometry(bone,cube,bones,extra);points.extend(vv.tolist())
            for ids,uv in ff:triangles.append({'vertices':[vv[i].tolist() for i in ids],'uv':[[v[0]/uvsize[0],v[1]/uvsize[1]] for v in uv],'region':name})
        if triangles:meshes.append({'name':name,'triangles':triangles})
    bounds=np.asarray(points);return meshes,(bounds.min(axis=0)+bounds.max(axis=0))/2

def atlas_cube(cube,uvsize,cell,unit):
    result=copy.deepcopy(cube);result['uv']=im.face_uv(cube);result.pop('mirror',None)
    for face in result['uv'].values():
        face['uv']=[cell[a]+face['uv'][a]*unit/uvsize[a] for a in range(2)]
        face['uv_size']=[face['uv_size'][a]*unit/uvsize[a] for a in range(2)]
    return result

def write_lod_review(source_root,config,high,low,batches,nodes,attachments,texture):
    from PIL import ImageDraw
    defaults={n['definitionId'] for n in nodes};magazine_node=next((n['definitionId'] for n in nodes if n.get('slot')==config['weapon'].get('magazinePath',['magazine'])[-1]),None)
    scenes=[('default',defaults)]
    if magazine_node:
        scenes.extend((a['definitionId'],(defaults-{magazine_node})|{a['definitionId']}) for a in attachments if a['native'])
    image=Image.new('RGB',(768,len(scenes)*170),(80,80,80));draw=ImageDraw.Draw(image)
    for i,(label,enabled) in enumerate(scenes):
        for j,model in enumerate((high,low)):
            geo=model['minecraft:geometry'][0];bones=copy.deepcopy({b['name']:b for b in geo['bones']})
            for name,b in bones.items():
                info=batches.get(name)
                if not info or info['definition'] not in enabled or info['variant'] in config.get('previewHiddenVariants',[]):b.pop('cubes',None)
            meshes,_=mesh_for('review',bones,[geo['description']['texture_width'],geo['description']['texture_height']],texture,np.eye(4))
            library={'materials':{'review':{'baseColor':'#ffffff','texture':'source','textureScale':1}}};binding={'defaultMaterial':'review','parts':{}}
            for size,x in ((144,j*180),(48,380+j*185)):
                rendered=render_part_icon({'definitionId':'review','meshes':meshes},library,binding,lambda _:texture,size=size,muzzle_left=True,alpha_cutout=True)
                if size==48:rendered=rendered.resize((144,144),Image.Resampling.NEAREST)
                image.paste(rendered,(x,i*170),rendered)
        draw.text((8,i*170+145),label+' | high / low | 48px high / low',fill='white')
    image.save(source_root/'lod-comparison.png')

def build(config_path=DEFAULT,resources=RES,append_sources=False):
    config_path=Path(config_path).resolve();config=ex.read(config_path);validate_configuration(config);source_root=config_path.parent;resources=Path(resources)
    if append_sources:append(config,source_root)
    if config.get('authoredStockAssets',False):
        import stock_assets
        stock_assets.build(resources=resources)
    index,idx,display_path,dp,source,original_texture,data_path=inputs(config)
    gun=config['sourceGun'];ns=config['gunId'].split(':')[0];base=resources/f'data/{ns}/{gun}';assets=resources/f'assets/{ns}'
    original=ex.read(source);geo=original['minecraft:geometry'][0];native={b['name']:b for b in geo['bones']};native_uv=[geo['description'][k] for k in ('texture_width','texture_height')]
    editable=ex.read(source_root/'editable/manifest.json')['parts'];attachments,tags=attachment_records(config)
    mapping={r['definitionId']:(config['gunId'] if r['definitionId']==config['rootDefinition'] else ns+':'+r['definitionId']) for r in editable}
    mapping.update({r['definitionId']:r['attachmentId'] for r in attachments});external={r['attachmentId']:r['definitionId'] for r in attachments}
    types={typ:[r['definitionId'] for r in attachments if r['type']==typ] for typ in (name.lower() for name in config['nativeProfile']['attachmentPaths'])}
    slots={d:{slot:[item for c in candidates for item in (types[c[1:]] if c.startswith('$') else [c])] for slot,candidates in byslot.items()} for d,byslot in config['slots'].items()}
    catalog=[]
    for d in mapping:
        entry={'id':d,'stats':{'weightKg':0,'ergonomics':0},'slots':[{'id':slot,'required':False,'allowedParts':candidates} for slot,candidates in slots.get(d,{}).items()],'conflictingParts':[]}
        if d==config['rootDefinition']:entry['weapon']={'recoilVertical':0,'recoilHorizontal':0,'centerOfImpact':0,'sightingRange':0}
        catalog.append(entry)
    nodes=[]
    def node(d,path='',parent=None,slot=None):
        ident=str(uuid.uuid5(uuid.NAMESPACE_URL,config['gunId']+'/'+path));item={'instanceId':ident,'definitionId':d}
        if parent:item.update(parentId=parent,slot=slot)
        nodes.append(item)
        for childslot,child in config['preset'].get(d,{}).items():node(child,path+'/'+childslot,ident,childslot)
    node(config['rootDefinition'])
    parsed={};owned=[];source_paths={config_path,index,display_path,source,original_texture,data_path}|tags
    for row,root in [(r,source_root/'editable') for r in editable]+[(r['row'],r['root']) for r in attachments if r['native']]:
        _,bones,uv,texture,count=shared.load_part(row,root);d=row['definitionId'];parsed[d]=(bones,uv,root/row['texture'],np.eye(4));owned.append((row,root,bones,uv,count));source_paths.add(R/row['sourceGeometry'])
    for rec in attachments:
        source_paths.update(rec['provenance'])
        if rec['native']:continue
        source_paths.add(rec['index']);mount=rec['type']+'_pos';extra=ex.matrix(mount,native)
        if 'row' in rec:
            _,bones,uv,_,_=shared.load_part(rec['row'],rec['root']);texture=rec['root']/rec['row']['texture'];source_paths.add(R/rec['row']['sourceGeometry']);source_paths.add(rec['root']/rec['row']['model'])
        else:
            shape=ex.read(rec['source'])['minecraft:geometry'][0];bones={b['name']:b for b in shape['bones']};uv=[shape['description'][k] for k in ('texture_width','texture_height')];texture=rec['texture'];source_paths.add(rec['source'])
        if rec['type']=='scope':bones=exterior_scope_geometry(bones)
        source_paths.add(texture);parsed[rec['definitionId']]=(bones,uv,texture,extra)
    # Preserve the original rig exactly. Move editable geometry to gated leaves only.
    high=copy.deepcopy(original);highgeo=high['minecraft:geometry'][0];hb=highgeo['bones'];batches={};proof=[]
    ownership={(bone,index) for p in config['parts'] for bone,indices in p['sourceCubeIndices'].items() for index in indices}
    for row,_,_,_,_ in owned:
        for bone,indices in row['sourceCubeIndices'].items():ownership.update((bone,i) for i in indices)
    for bone in hb:
        bone['cubes']=[copy.deepcopy(c) for i,c in enumerate(bone.get('cubes',[])) if (bone['name'],i) not in ownership]
        if not bone['cubes']:bone.pop('cubes',None)
    textures=[original_texture]
    for row,root,_,_,_ in owned:
        tex=root/row['texture']
        if ex.sha(tex) not in {ex.sha(p) for p in textures}:textures.append(tex)
    sizes=[]
    for p in textures:
        with Image.open(p) as image:sizes.append(max(image.size))
    unit=max(sizes);columns=math.ceil(math.sqrt(len(textures)));atlas=Image.new('RGBA',(columns*unit,math.ceil(len(textures)/columns)*unit))
    cells={ex.sha(p):((i%columns)*unit,(i//columns)*unit) for i,p in enumerate(textures)}
    for p in textures:
        with Image.open(p) as image:atlas.paste(image.convert('RGBA').resize((unit,unit),Image.Resampling.NEAREST),cells[ex.sha(p)])
    for bone in hb:
        if bone.get('cubes'):bone['cubes']=[atlas_cube(c,native_uv,(0,0),unit) for c in bone['cubes']]
    for row,root,bones,uv,count in owned:
        d=row['definitionId'];cell=cells[ex.sha(root/row['texture'])]
        for name,bone in bones.items():
            if not bone.get('cubes'):continue
            leaf='assembly_'+d+'_'+name
            # Fullbright is determined by the original suffix, not inherited from parent.
            target={'name':leaf,'parent':name,'pivot':bone['pivot'],'cubes':[atlas_cube(c,uv,cell,unit) for c in bone['cubes']]}
            hb.append(target);batches[leaf]={'definition':d,'variant':config.get('sourceBoneVariants',{}).get(name,'always'),'sourceBone':name}
        proof.append({'definitionId':d,'cubes':count,'sourceRoot':str(root.relative_to(R)),'row':row,'atlasCell':cell,'modelSha256':ex.sha(root/row['model']),'textureSha256':ex.sha(root/row['texture'])})
    highgeo['description'].update(identifier='geometry.assembly.'+gun,texture_width=atlas.width,texture_height=atlas.height)
    low=copy.deepcopy(high);lowbones={b['name']:b for b in low['minecraft:geometry'][0]['bones']};lod_evidence={}
    if config.get('lod',{}).get('strategy')=='conservative_cube_subset':
        for row,root,bones,uv,count in owned:
            d=row['definitionId'];variants={config.get('sourceBoneVariants',{}).get(name,'always') for name,b in bones.items() if b.get('cubes')}
            for variant in sorted(variants):
                group=copy.deepcopy(bones)
                for name,b in group.items():
                    if config.get('sourceBoneVariants',{}).get(name,'always')!=variant:b.pop('cubes',None)
                selection,evidence,_,_=lod.simplify(group,uv,root/row['texture'],config['lod'],ex.cube_geometry)
                lod_evidence[d+'/'+variant]=evidence
                for name,indices in selection.items():
                    leaf=lowbones['assembly_'+d+'_'+name];leaf['cubes']=[leaf['cubes'][i] for i in indices]
    elif config.get('lod',{}).get('strategy','full')!='full':raise ValueError('Unsupported LOD policy')
    write(assets/f'geo_models/gun/{gun}.json',high);write(assets/f'geo_models/gun/lod/{gun}.json',low)
    atlaspath=assets/f'textures/gun/{gun}.png';atlaspath.parent.mkdir(parents=True,exist_ok=True);atlas.save(atlaspath)
    display=copy.deepcopy(dp);display.update(model_type=config['weapon']['modelType'],model=ns+':gun/'+gun,texture=ns+':gun/'+gun,lod={'model':ns+':gun/lod/'+gun,'texture':ns+':gun/'+gun});write(assets/f'display/guns/{gun}.json',display)
    write(resources/f'data/{ns}/data/guns/{gun}.json',ex.read(data_path));newindex=copy.deepcopy(idx);newindex.update(name='gun.'+ns+'.'+gun,display=ns+':'+gun,data=ns+':'+gun,item_type=ns+':'+gun,sort=103);write(resources/f'data/{ns}/index/guns/{gun}.json',newindex)
    # Each preview uses the same neutral native transform and normalized texture coordinates.
    meshes={};anchors={}
    for d,(bones,uv,texture,extra) in parsed.items():
        visible=copy.deepcopy(bones)
        for name,b in visible.items():
            if config.get('sourceBoneVariants',{}).get(name) in config.get('previewHiddenVariants',[]):b.pop('cubes',None)
        meshes[d],anchors[d]=mesh_for(d,visible,uv,texture,extra)
    for d,byslot in slots.items():
        for slot,candidates in byslot.items():
            if not candidates:continue
            representative=config['preset'].get(d,{}).get(slot,candidates[0]);center=anchors[representative].copy()
            for candidate in candidates:anchors[candidate]=center
    anchors[config['rootDefinition']]=np.zeros(3);models=[];library={'schemaVersion':1,'materials':{}};bindings={'schemaVersion':1,'defaultMaterial':config['rootDefinition'],'parts':{}}
    for d in mapping:
        anchor=anchors[d]
        for mesh in meshes[d]:
            for tri in mesh['triangles']:tri['vertices']=(np.asarray(tri['vertices'])-anchor).tolist()
        models.append({'definitionId':d,'attachmentOrigin':[0,0,0],'slots':{slot:(anchors[choices[0]]-anchor).tolist() for slot,choices in slots.get(d,{}).items() if choices},'boxes':[],'meshes':meshes[d]})
        texture=assets/f'textures/parts/{gun}/{d}.png';texture.parent.mkdir(parents=True,exist_ok=True);texture.write_bytes(parsed[d][2].read_bytes())
        library['materials'][d]={'baseColor':'#ffffff','texture':f'{ns}:textures/parts/{gun}/{d}.png','roughness':.8,'specular':.12,'textureScale':1};bindings['parts'][d]={'defaultMaterial':d,'regions':{}}
    for filename,value in [('preview.json',{'schemaVersion':3,'models':models}),('library.json',library),('materials.json',bindings)]:
        write(base/filename,value);write(assets/gun/{'preview.json':'icon_geometry.json','library.json':'icon_library.json','materials.json':'icon_materials.json'}[filename],value)
    write(base/'workbench-anchors.json',{'schemaVersion':1,'anchors':{d:v.tolist() for d,v in anchors.items()}})
    for model in models:
        d=model['definitionId'];icon=assets/config['weapon']['partIconDirectory']/f'{d}.png';icon.parent.mkdir(parents=True,exist_ok=True)
        render_part_icon(model,library,bindings,lambda ref:resources/'assets'/ref.replace(':','/'),muzzle_left=True,alpha_cutout=True).save(icon)
        if not mapping[d].startswith('tacz:'):write(assets/f'models/item/{mapping[d].split(":")[1]}.json',{'parent':'builtin/entity','gui_light':'front'} if d==config['rootDefinition'] else {'parent':'minecraft:item/generated','textures':{'layer0':f"{ns}:{config['weapon']['partIconDirectory'].removeprefix('textures/')}/{d}"}})
    overrides={};available_overrides={}
    for catalog_path in config['attachmentOverrideCatalogs']:
        path=resources/catalog_path;entries=ex.read(path);source_paths.add(path)
        available_overrides.update(entries.get('attachments',entries))
    for rec in attachments:
        if rec['native'] or 'row' not in rec:continue
        candidate=available_overrides[rec['attachmentId']]
        overrides[rec['attachmentId']]={k:candidate[k] for k in ('model','texture','lodModel','lodTexture') if k in candidate}
    for name,value in [('weapon.json',config['weapon']),('catalog.json',{'schemaVersion':1,'parts':catalog}),('mapping.json',mapping),('scene.json',{'schemaVersion':1,'nodes':nodes}),('native_attachments.json',external),('native-profile.json',config['nativeProfile']),('native-visual-rules.json',config['visualRules']),('batches.json',batches),('inline_attachments.json',{}),('native_attachment_overrides.json',overrides)]:write(base/name,value)
    write(resources/f'data/{ns}/tacz_tags/attachments/allow_attachments/{gun}.json',sorted(external))
    identities=[];foundation=[];part_properties={r['definitionId']:r for r in config['parts']}
    for d in [r['definitionId'] for r in editable]:
        item=mapping[d];isroot=d==config['rootDefinition'];props=part_properties[d]
        identities.append({'item':item,'tags':['item_foundation:type/'+props['inventoryType']]})
        itemdata={'schema_version':3,'item':item,'footprint':props['footprint'],'weight_kg':0}
        if isroot:itemdata['wearable_slots']=config['weapon']['wearableSlots']
        write(resources/f'data/{ns}/item_foundation/items/{item.split(":")[1]}.json',itemdata)
        foundation.append({'itemId':item,'slots':[{'id':slot,'compatibleItems':sorted({'tacz:attachment' if mapping[c] in external else mapping[c] for c in choices}),'requiredSiblingSlots':[],'conflictingSiblingSlots':[],'toggleable':False} for slot,choices in slots.get(d,{}).items()]})
    write(resources/f'data/{ns}/item_foundation/identities/{gun}.json',{'schema_version':1,'items':identities});write(resources/f'data/{ns}/assembly/{gun}.json',{'schemaVersion':1,'items':foundation})
    animation=ex.asset(dp['animation'],'animations','.animation.json');source_paths.add(animation)
    for ref,folder in ((ex.read(data_path).get('script'),'data'),(dp.get('state_machine'),'assets')):
        if ref:
            ns0,path=ref.split(':');candidate=ex.SRC/f'{folder}/{ns0}/scripts/{path}.lua'
            if candidate.exists():source_paths.add(candidate)
    report={'schemaVersion':1,'gunId':config['gunId'],'configuration':str(config_path.relative_to(R)),'parts':proof,'sourceHashes':{str(p.relative_to(R)):ex.sha(p) for p in sorted(source_paths)},'nativeRigBones':len(native),'highCubes':sum(len(b.get('cubes',[])) for b in hb),'lowCubes':sum(len(b.get('cubes',[])) for b in lowbones.values()),'lod':lod_evidence,'highTriangles':sum(2*len(im.face_uv(c)) for b in hb for c in b.get('cubes',[])),'lowTriangles':sum(2*len(im.face_uv(c)) for b in lowbones.values() for c in b.get('cubes',[])),'lodPolicy':config['lodPolicy'],'atlasUnit':unit,'atlasSize':list(atlas.size),'presentationOnly':config['presentationOnly'],'gameStarted':False}
    write(base/'geometry-evidence.json',report);write(source_root/'build-report.json',report)
    if lod_evidence:write_lod_review(source_root,config,high,low,batches,nodes,attachments,atlaspath)
    fragments=Path(config['integrationFragments']);fragments.mkdir(parents=True,exist_ok=True)
    write(fragments/'weapon-index-entry.json',config['weapon'])
    for i,locale in enumerate(('zh_cn','en_us')):
        language={'gun.'+ns+'.'+gun:config['gunLabels'][i],'item.'+config['gunId'].replace(':','.'):config['gunLabels'][i]}
        for slot,labels in config.get('slotLabels',{}).items():language['weapon_assembly_ui.slot.'+slot]=labels[i]
        for d,labels in config['labels'].items():
            if d!=config['rootDefinition']:language['item.'+mapping[d].replace(':','.')]=labels[i]
        write(fragments/(locale+'.json'),language)
    print('Native gun',gun,'editable owned parts',len(owned),'catalog',len(catalog),'rig bones',len(native),'high/low cubes',report['highCubes'],report['lowCubes'])
    return report

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--config',type=Path,default=DEFAULT);parser.add_argument('--append',action='store_true');args=parser.parse_args();build(args.config,append_sources=args.append)
