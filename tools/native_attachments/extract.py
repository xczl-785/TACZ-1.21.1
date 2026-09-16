"""Append-only editable native attachment extraction, independent of any gun rig.
Original TaCZ pack remains read-only. Native cube/UV conversion is shared with the
proven M4 converter; no M4 anchor or compatibility data is used here.
"""
import argparse,base64,json,sys,copy
from pathlib import Path
from PIL import Image
import numpy as np
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'native_m4a1'))
import editable_import as im
R=im.R
OUT=R/'modules/tacz_adapter/weapon-sources/native_attachments/editable'
IDS=tuple('muzzle_'+s for s in ('silencer_sg','silencer_ptilopsis','choke_sg','silencer_mirage','brake_mastiff_sg','silencer_wraith'))

def encode(geo,texture,d):
    """Encode a complete (or caller-selected) native geometry preserving every bone.
    Caller records sourceGeometry and sourceCubeIndices for provenance/readback.
    """
    bones={b['name']:b for b in geo['bones']}
    indices={n:list(range(len(b.get('cubes',[])))) for n,b in bones.items()}
    selected=set(bones);delta=[0,0,0]
    vertices=np.concatenate([im.ex.cube_geometry(b,b['cubes'][i],bones,np.eye(4))[0] for b in bones.values() for i in range(len(b.get('cubes',[])))])
    bounds_center=(vertices.min(axis=0)+vertices.max(axis=0))/2
    # Blockbench saves positions to five decimals. Quantize the center as well
    # so saving a source cannot move a native rig pivot on inverse conversion.
    center=[round(float(bounds_center[0]),5),round(float(bounds_center[1]),5),round(float(-bounds_center[2]),5)]
    def point(v):return [round(n,5) for n in (-(v[0]+delta[0])-center[0],v[1]+delta[1]-center[1],v[2]+delta[2]-center[2])]
    prefix='native_attachment_'+d;wrapper=im.ex.uid(prefix+'/wrapper');groupname=d
    groups=[{'name':groupname,'uuid':wrapper,'origin':[0,0,0],'rotation':[0,0,0],'export':True}]
    tree={};elements=[]
    for name,b in bones.items():
        if name not in selected:continue
        ident=im.ex.uid(prefix+'/bone/'+name)
        groups.append({'name':prefix+'__'+name,'uuid':ident,'origin':point(b['pivot']),'rotation':im.native_rotation(b.get('rotation',[0,0,0])),'export':True})
        tree[name]={'uuid':ident,'children':[]}
        for index in indices[name]:
            c=b['cubes'][index];uv=im.face_uv(c)
            ident=im.ex.uid(prefix+'/cube/'+name+'/'+str(index));origin=c['origin'];size=c['size']
            lo=point([origin[0]+size[0],origin[1],origin[2]]);hi=point([origin[0],origin[1]+size[1],origin[2]+size[2]])
            faces={}
            for face in im.ex.QUADS:
                f=uv.get(face)
                if f is None:faces[face]={'uv':[0,0,0,0],'texture':None};continue
                a,bv=f['uv'];w,h=f['uv_size'];rect=[a,bv,a+w,bv+h]
                if face in ('up','down'):rect=[rect[2],rect[3],rect[0],rect[1]]
                faces[face]={'uv':rect,'texture':0}
            e={'name':name,'type':'cube','uuid':ident,'from':lo,'to':hi,'origin':point(c.get('pivot',b['pivot'])),'rotation':im.native_rotation(c.get('rotation',[0,0,0])),'faces':faces,'box_uv':False,'export':True}
            if c.get('inflate'):e['inflate']=c['inflate']
            elements.append(e);tree[name]['children'].append(ident)
    root={'uuid':wrapper,'children':[]}
    for name,node in tree.items():
        parent=bones[name].get('parent');(tree[parent] if parent else root)['children'].append(node)
    with Image.open(texture) as image:width,height=image.size
    uvsize=[geo['description'][k] for k in ('texture_width','texture_height')]
    t={'name':'texture.png','id':'0','uuid':im.ex.uid(prefix+'/texture'),'path':'texture.png','relative_path':'texture.png','source':'data:image/png;base64,'+base64.b64encode(texture.read_bytes()).decode(),'width':width,'height':height,'uv_width':uvsize[0],'uv_height':uvsize[1],'internal':True}
    model={'meta':{'format_version':'5.0','model_format':'free','box_uv':False},'name':d,'resolution':{'width':uvsize[0],'height':uvsize[1]},'elements':elements,'groups':groups,'outliner':[root],'textures':[t]}
    row={'definitionId':d,'group':groupname,'prefix':prefix,'cubes':len(elements),
         'sourceCubeIndices':indices,'translation':delta,'editorCenter':center,'uvSize':uvsize,
         'model':f'components/{d}/model.bbmodel','texture':f'components/{d}/texture.png'}
    return row,model

def extract(name):
    if name not in IDS:raise ValueError('Unreviewed attachment '+name)
    index_path=im.ex.SRC/'data/tacz/index/attachments'/f'{name}.json'
    idx=im.ex.read(index_path)
    display=im.ex.asset(idx['display'],'display/attachments','.json')
    data=im.ex.SRC/'data/tacz/data/attachments'/f"{idx['data'].split(':')[1]}.json"
    dp=im.ex.read(display)
    source=im.ex.asset(dp['model'],'geo_models','.json')
    texture=im.ex.asset(dp['texture'],'textures','.png')
    geo=im.ex.read(source)['minecraft:geometry'][0]
    d='tacz_'+name;encoding,model=encode(geo,texture,d)
    source_paths=[index_path,display,data,source,texture]
    extras=[]
    for suffix in ('_n','_s'):
        path=texture.with_name(texture.stem+suffix+'.png')
        if path.exists():source_paths.append(path);extras.append({'source':str(path.relative_to(R)),'file':f'components/{d}/texture{suffix}.png'})
    lod={k:v for k,v in dp.items() if 'lod' in k.lower()}
    if lod:raise ValueError('Source LOD requires explicit reviewed mapping')
    row={**encoding,'attachmentId':'tacz:'+name,
         'sourceGeometry':str(source.relative_to(R)),'sourceTexture':str(texture.relative_to(R)),
         'sourceSha256':im.ex.sha(source),'sourceTextureSha256':im.ex.sha(texture),
         'sourceHashes':{str(p.relative_to(R)):im.ex.sha(p) for p in source_paths},
         'auxiliaryTextures':extras,'nativeDisplay':dp,'sourceLod':None,
         'lodPolicy':'No native LOD reference; retain full geometry fallback',
         'functionalBones':[b['name'] for b in geo['bones'] if not b.get('cubes')],
         'runtimeMode':'native_attachment','coordinateSpace':'native attachment local; mount supplied by gun'}
    return row,model,texture

def append(output=OUT):
    output=Path(output);manifest=im.ex.read(output/'manifest.json') if (output/'manifest.json').exists() else {'schemaVersion':1,'parts':[]}
    existing={r['attachmentId'] for r in manifest['parts']}
    for name in IDS:
        if 'tacz:'+name in existing:continue
        row,model,texture=extract(name)
        if (output/row['model']).parent.exists():raise ValueError('Unregistered existing edit directory: '+name)
        im.write(output/row['model'],model);(output/row['texture']).write_bytes(texture.read_bytes())
        for item in row['auxiliaryTextures']:(output/item['file']).write_bytes((R/item['source']).read_bytes())
        row['baselineModelSha256']=im.ex.sha(output/row['model']);manifest['parts'].append(row)
    im.write(output/'manifest.json',manifest)
    return manifest

def load_part(row,root=OUT):
    # Absolute paths avoid changing the shared converter's M4 default directory.
    return im.load_part({**row,'model':str(Path(root)/row['model']),'texture':str(Path(root)/row['texture'])})

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,default=OUT);a=p.parse_args()
    m=append(a.output);print(json.dumps({'parts':len(m['parts']),'cubes':sum(r['cubes'] for r in m['parts'])}))
