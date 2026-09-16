"""Append repeatable native cube imports without overwriting any existing edit source.
Selection is deliberately restricted to the first non-optic batch. Provenance comes
from audited component ownership, never from guessing cube positions or names.
"""
import argparse,base64,collections,json
from pathlib import Path
from PIL import Image
import numpy as np
import editable_import as im

BATCH=('handguard_tactical','tacz_stock_ak12','tacz_stock_carbon_bone_c5','tacz_stock_hk_slim_line',
       'tacz_stock_m4ss','tacz_stock_militech_b5','tacz_stock_moe','tacz_stock_ripstock','tacz_stock_sba3')

def extract(entry):
    d=entry['definitionId']
    if d not in BATCH:raise ValueError('Not in reviewed non-optic batch: '+d)
    if len(entry['sourceGeometry'])!=1:raise ValueError('Multiple geometry sources need explicit mapping')
    source=im.R/entry['sourceGeometry'][0];geo=im.ex.read(source)['minecraft:geometry'][0]
    bones={b['name']:b for b in geo['bones']};indices=collections.defaultdict(list)
    for group in entry['meshGroups']:
        for c in group['sourceCubes']:indices[c['bone']].append(c['cubeIndex'])
    selected=set()
    for name in indices:selected.update(im.ex.ancestors(name,bones))
    external=d.startswith('tacz_stock_')
    native=im.ex.read(im.ex.asset('tacz:gun/m4a1_geo','geo_models','.json'))['minecraft:geometry'][0]
    mount=next(b for b in native['bones'] if b['name']==entry['anchorBone'])
    delta=mount['pivot'] if external else [0,0,0]
    if external and any(mount.get('rotation',[0,0,0])):raise ValueError('Rotated mount requires explicit rig mapping')
    extra=im.ex.matrix(entry['anchorBone'],{b['name']:b for b in native['bones']}) if external else np.eye(4)
    vertices=np.concatenate([im.ex.cube_geometry(bones[name],bones[name]['cubes'][index],bones,extra)[0] for name,ids in indices.items() for index in ids])
    bounds_center=(vertices.min(axis=0)+vertices.max(axis=0))/2
    # Blockbench saves positions to five decimals. Quantize the center as well
    # so saving a source cannot move a native rig pivot on inverse conversion.
    center=[round(float(bounds_center[0]),5),round(float(bounds_center[1]),5),round(float(-bounds_center[2]),5)]
    def point(v):return [round(n,5) for n in (-(v[0]+delta[0])-center[0],v[1]+delta[1]-center[1],v[2]+delta[2]-center[2])]
    prefix='batch_'+d;wrapper=im.ex.uid(prefix+'/wrapper');groupname=d
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
    texture=im.R/entry['sourceTexture']
    with Image.open(texture) as image:width,height=image.size
    uvsize=[geo['description'][k] for k in ('texture_width','texture_height')]
    t={'name':'texture.png','id':'0','uuid':im.ex.uid(prefix+'/texture'),'path':'texture.png','relative_path':'texture.png','source':'data:image/png;base64,'+base64.b64encode(texture.read_bytes()).decode(),'width':width,'height':height,'uv_width':uvsize[0],'uv_height':uvsize[1],'internal':True}
    model={'meta':{'format_version':'5.0','model_format':'free','box_uv':False},'name':d,'resolution':{'width':uvsize[0],'height':uvsize[1]},'elements':elements,'groups':groups,'outliner':[root],'textures':[t]}
    row={'definitionId':d,'group':groupname,'prefix':prefix,'cubes':len(elements),'sourceGeometry':str(source.relative_to(im.R)),'sourceSha256':im.ex.sha(source),'componentMetadata':str((im.SOURCE/entry['component']).relative_to(im.R)),'sourceCubeIndices':{name:ids for name,ids in indices.items() if ids},'translation':delta,'model':f'components/{d}/model.bbmodel','texture':f'components/{d}/texture.png','editorCenter':center,'uvSize':uvsize,'nativeMount':entry['anchorBone'] if external else None,'batch':'non-optic-stock-handguard-1'}
    return row,model,texture

def append(output=im.EDIT):
    output=Path(output);manifest=im.ex.read(output/'manifest.json');existing={r['definitionId'] for r in manifest['parts']}
    entries={p['definitionId']:p for p in im.ex.read(im.SOURCE/'manifest.json')['parts']}
    pending=[];skipped=[]
    for d in BATCH:
        if d in existing:continue
        if (output/'components'/d).exists():raise ValueError('Unregistered existing edit directory: '+d)
        try:pending.append(extract(entries[d]))
        except ValueError as error:skipped.append({'definitionId':d,'reason':str(error)})
    for row,model,texture in pending:
        im.write(output/row['model'],model);(output/row['texture']).write_bytes(texture.read_bytes())
        row['baselineModelSha256']=im.ex.sha(output/row['model']);manifest['parts'].append(row)
    (output/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    report={'batch':list(BATCH),'imported':[r['definitionId'] for r in manifest['parts'] if r['definitionId'] in BATCH],'skipped':skipped,'existingSourcesOverwritten':False,'sourcePolicy':'Audited native cube ownership; native texture byte copy; UV units from geometry description; original resources read-only'}
    im.write(output/'batch-import-report.json',report)
    print(json.dumps(report))
    return report

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,default=im.EDIT);append(parser.parse_args().output)
