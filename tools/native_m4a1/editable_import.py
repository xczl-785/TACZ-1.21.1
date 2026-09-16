"""Build default native M4 surfaces, workbench meshes and icons from editable cubes.
The original rig owns animation; per-part bbmodels own editable geometry and PNGs.
"""
from pathlib import Path
import base64,copy,hashlib,json,math
import numpy as np
from PIL import Image
import export_parts as ex
R=ex.R; SOURCE=ex.OUT; EDIT=SOURCE/'editable'; OUT=R/'modules/tacz_adapter/weapon-content/resources'; BASE=OUT/'data/tacz_assembly/m4a1'

def write(p,d):
    p.parent.mkdir(parents=True,exist_ok=True)
    pretty='geo_models' in p.parts or p.name in {'batches.json','geometry-evidence.json'} or p.parent.name=='guns'
    p.write_text(json.dumps(d,ensure_ascii=False,indent=2 if pretty else None,separators=None if pretty else (',',':'))+'\n')
def native_point(v,center,delta):return [-(v[0]+center[0])-delta[0],v[1]+center[1]-delta[1],v[2]+center[2]-delta[2]]
def native_rotation(v):return [-v[0],-v[1],v[2]]

def load_part(row):
    model=ex.read(EDIT/row['model']);groups={g['uuid']:g for g in model['groups']};elements={e['uuid']:e for e in model['elements']}
    original=ex.read(R/row['sourceGeometry']);lookup={b['name']:b for b in original['minecraft:geometry'][0]['bones']}
    center=row['editorCenter'];delta=row['translation'];prefix=row['prefix']+'__';bones=copy.deepcopy(lookup)
    for b in bones.values():b.pop('cubes',None)
    counts=0
    def visit(node,parent=None):
        nonlocal counts
        if isinstance(node,str):
            e=elements[node]
            if e['type']!='cube':raise ValueError('Cube source required: '+row['definitionId'])
            if not e.get('export',True):return
            if e.get('box_uv'):raise ValueError('Use per-face UV on editable cubes')
            lo=native_point(e['from'],center,delta);hi=native_point(e['to'],center,delta)
            uv={}
            for face,f in e['faces'].items():
                if f.get('texture') is None:continue
                if f.get('rotation',0):raise ValueError('Rotated face UV requires explicit UV baking')
                a,b,c,d=f['uv']
                if face in ('up','down'):a,b,c,d=c,d,a,b
                uv[face]={'uv':[a,b],'uv_size':[c-a,d-b]}
            cube={'origin':[hi[0],lo[1],lo[2]],'size':[lo[0]-hi[0],hi[1]-lo[1],hi[2]-lo[2]],'uv':uv}
            if e.get('inflate'):cube['inflate']=e['inflate']
            if any(e.get('rotation',[0,0,0])):
                cube['rotation']=native_rotation(e['rotation']);cube['pivot']=native_point(e['origin'],center,delta)
            if parent not in bones:raise ValueError('Cube must remain under a mapped source bone')
            bones[parent].setdefault('cubes',[]).append(cube);counts+=1
            return
        g=groups[node['uuid']]
        if not g.get('export',True):return
        if g['name']==row['group']:
            if any(g.get('rotation',[0,0,0])):raise ValueError('Do not rotate editor wrapper')
            name=None
        else:
            if not g['name'].startswith(prefix):raise ValueError('Unmapped bone '+g['name'])
            name=g['name'][len(prefix):];old=lookup[name]
            if not np.allclose(native_point(g['origin'],center,delta),old['pivot'],atol=1e-8) or not np.allclose(native_rotation(g.get('rotation',[0,0,0])),old.get('rotation',[0,0,0]),atol=1e-8):
                raise ValueError('Animation bone pivots/rotations must remain unchanged: '+name)
            if old.get('parent')!=parent:raise ValueError('Animation bone parent changed: '+name)
        for child in node['children']:visit(child,name)
    for root in model['outliner']:visit(root)
    uvsize=[model['textures'][0]['uv_width'],model['textures'][0]['uv_height']]
    if not all(v>0 for v in uvsize):raise ValueError('Invalid UV size')
    texture=Image.open(EDIT/row['texture']).convert('RGBA')
    return model,bones,uvsize,texture,counts

def face_uv(cube):
    uv=cube['uv']
    if isinstance(uv,dict):return copy.deepcopy(uv)
    u,v=uv;dx,dy,dz=map(int,cube['size']);a=u+dz;b=a+dx;c=b+dx;d=b+dz;e=d+dx;f=v+dz;g=f+dy
    rect={'down':[a,v,b,f],'up':[b,f,c,v],'west':[u,f,a,g],'north':[a,f,b,g],'east':[b,f,d,g],'south':[d,f,e,g]}
    if cube.get('mirror'):raise ValueError('Mirrored box UV requires explicit source conversion')
    return {k:{'uv':[r[0],r[1]],'uv_size':[r[2]-r[0],r[3]-r[1]]} for k,r in rect.items()}

def atlas_cube(cube,uvsize,cell):
    c=copy.deepcopy(cube);c['uv']=face_uv(c);c.pop('mirror',None)
    for f in c['uv'].values():
        f['uv']=[cell[0]+f['uv'][0]*512/uvsize[0],cell[1]+f['uv'][1]*512/uvsize[1]]
        f['uv_size']=[f['uv_size'][0]*512/uvsize[0],f['uv_size'][1]*512/uvsize[1]]
    return c

def build():
    manifest=ex.read(EDIT/'manifest.json');rows=manifest['parts'];defaults={r['definitionId'] for r in rows}
    scene=ex.read(BASE/'scene.json');assert {n['definitionId'] for n in scene['nodes']} <= defaults
    assert len(defaults)==len(rows), 'Duplicate editable definitions'
    parsed={r['definitionId']:load_part(r) for r in rows}
    highpath=OUT/'assets/tacz_assembly/geo_models/gun/m4a1.json';lowpath=OUT/'assets/tacz_assembly/geo_models/gun/lod/m4a1.json'
    high=ex.read(highpath);low=ex.read(lowpath);batches=ex.read(BASE/'batches.json');hb=high['minecraft:geometry'][0]['bones'];lb=low['minecraft:geometry'][0]['bones']
    original=ex.read(ex.asset('tacz:gun/m4a1_geo','geo_models','.json'));native={b['name']:b for b in original['minecraft:geometry'][0]['bones']}
    highuv=[original['minecraft:geometry'][0]['description'][k] for k in ['texture_width','texture_height']]
    original_texture=ex.asset('tacz:gun/uv/m4a1','textures','.png')
    texture_ids={ex.sha(original_texture):0}
    for row in rows:
        if row.get('runtimeMode')!='native_attachment':texture_ids.setdefault(ex.sha(EDIT/row['texture']),len(texture_ids))
    columns=math.ceil(math.sqrt(len(texture_ids)));atlas_size=(columns*512,math.ceil(len(texture_ids)/columns)*512)
    atlas=Image.new('RGBA',atlas_size);atlas.paste(Image.open(original_texture).convert('RGBA').resize((512,512),Image.Resampling.NEAREST),(0,0))
    def replaced(b):
        info=batches.get(b['name'],{});return info.get('definition') in defaults and info.get('variant')!='folded'
    # Keep native rig nodes and unrelated alternatives. Rebuild only this editable set.
    hb[:]=[b for b in hb if not replaced(b)];lb[:]=[b for b in lb if not replaced(b)]
    batches={k:v for k,v in batches.items() if v.get('definition') not in defaults or v.get('variant')=='folded'}
    for collection in [hb,lb]:
        for b in collection:
            b['cubes']=[atlas_cube(c,highuv,(0,0)) for c in b.get('cubes',[])]
            if not b['cubes']:b.pop('cubes',None)
    # The old reused LOD leaves all belong to the replaced set. Other leaves use native high UV.
    assert not any(b['name'].startswith('assembly_lod_ld_') for b in lb)
    derivedmanifest=ex.read(SOURCE/'manifest.json');sourceparts={p['definitionId']:p for p in derivedmanifest['parts']};proof=[]
    attachment_overrides={}
    attachment_displays={a['id']:a['display'] for a in ex.read(R/'docs/assembly-experiment/native-m4a1-review/audit.json')['attachments']}
    for i,row in enumerate(rows,1):
        d=row['definitionId'];model,bones,uvsize,texture,count=parsed[d]
        detached=row.get('runtimeMode')=='native_attachment'
        cell_id=texture_ids.get(ex.sha(EDIT/row['texture']),0);cell=((cell_id%columns)*512,(cell_id//columns)*512)
        if not detached:atlas.paste(texture.resize((512,512),Image.Resampling.NEAREST),cell)
        if detached:
            geometry=copy.deepcopy(ex.read(R/row['sourceGeometry']))
            geometry['minecraft:geometry'][0]['bones']=list(bones.values())
            geometry['minecraft:geometry'][0]['description'].update(texture_width=uvsize[0],texture_height=uvsize[1])
            model_ref='tacz_assembly:attachments/'+d
            texture_ref='tacz_assembly:attachments/'+d
            write(OUT/('assets/tacz_assembly/geo_models/attachments/'+d+'.json'),geometry)
            texture_path=OUT/('assets/tacz_assembly/textures/attachments/'+d+'.png');texture_path.parent.mkdir(parents=True,exist_ok=True)
            texture_path.write_bytes((EDIT/row['texture']).read_bytes())
            override={'model':model_ref,'texture':texture_ref}
            if attachment_displays[sourceparts[d]['itemId']].get('lod'):
                lod=copy.deepcopy(geometry)
                for bone in lod['minecraft:geometry'][0]['bones']:
                    cubes=bone.get('cubes',[])
                    order=sorted(range(len(cubes)),key=lambda j:-sum(cubes[j]['size'][a]*cubes[j]['size'][b] for a,b in [(0,1),(0,2),(1,2)]))[:2]
                    if cubes:bone['cubes']=[cubes[j] for j in sorted(order)]
                write(OUT/('assets/tacz_assembly/geo_models/attachments/lod/'+d+'.json'),lod)
                override.update(lodModel='tacz_assembly:attachments/lod/'+d,lodTexture=texture_ref)
            attachment_overrides[sourceparts[d]['itemId']]=override

        mount=row.get('nativeMount') or ('stock_pos' if d=='tacz_stock_tactical_ar' else None);stock=mount is not None
        rig_prefix=('editable_stock_' if d=='tacz_stock_tactical_ar' else 'editable_'+d+'_')
        anchor=np.asarray(sourceparts[d]['anchor']);extra=ex.matrix(mount,native) if stock else np.eye(4)
        meshes=[];allvertices=[]
        if stock and not detached:
            for b in bones.values():
                copied={k:copy.deepcopy(v) for k,v in b.items() if k!='cubes'};copied['name']=rig_prefix+b['name'];copied['parent']=rig_prefix+b['parent'] if b.get('parent') else mount
                # Bone pivots are absolute in native Bedrock; offset by the mount's world-art pivot.
                copied['pivot']=[v+row['translation'][a] for a,v in enumerate(b['pivot'])]
                hb.append(copy.deepcopy(copied));lb.append(copy.deepcopy(copied))
        for name,b in bones.items():
            cubes=b.get('cubes',[])
            if not cubes:continue
            vertices={};faces={}
            for cube in cubes:
                vv,ff=ex.cube_geometry(b,cube,bones,extra);allvertices.extend(vv.tolist());base=len(vertices)
                for j,v in enumerate(vv):vertices[str(base+j)]=(v-anchor).round(10).tolist()
                for ids,uv in ff:
                    keys=[str(base+j) for j in ids];uv=[[p[0]*texture.width/uvsize[0],p[1]*texture.height/uvsize[1]] for p in uv]
                    faces[str(len(faces))]={'vertices':keys,'uv':dict(zip(keys,uv)),'texture':0}
            meshes.append({'type':'mesh','name':name,'uuid':ex.uid('editable/'+d+'/'+name),'origin':[0,0,0],'rotation':[0,0,0],'visibility':True,'export':True,'vertices':vertices,'faces':faces})
            if detached:continue
            target=rig_prefix+name if stock else name
            newname='assembly_editable_'+d+'_'+name
            transformed=[]
            for c in cubes:
                c=atlas_cube(c,uvsize,cell)
                if stock:
                    for key in ['origin','pivot']:
                        if key in c:c[key]=[v+row['translation'][a] for a,v in enumerate(c[key])]
                transformed.append(c)
            leaf={'name':newname,'parent':target,'pivot':([v+row['translation'][a] for a,v in enumerate(b['pivot'])] if stock else b['pivot']),'cubes':transformed}
            hb.append(leaf);info={'definition':d,'variant':'upright' if d=='front_sight' else 'always','sourceBone':name};batches[newname]=info
            order=sorted(range(len(transformed)),key=lambda j:-sum(transformed[j]['size'][a]*transformed[j]['size'][b] for a,b in [(0,1),(0,2),(1,2)]))[:2]
            lowleaf=copy.deepcopy(leaf);lowleaf['name']=newname+'_lod';lowleaf['cubes']=[copy.deepcopy(transformed[j]) for j in sorted(order)];lb.append(lowleaf);batches[lowleaf['name']]=info
        entry=sourceparts[d];entry['boundsCenter']=((np.min(allvertices,axis=0)+np.max(allvertices,axis=0))/2).tolist();entry['geometryBounds']=[np.min(allvertices,axis=0).tolist(),np.max(allvertices,axis=0).tolist()]
        data={'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':d,'resolution':{'width':texture.width,'height':texture.height},'elements':meshes,'outliner':[m['uuid'] for m in meshes],'textures':[{'name':'texture.png','uuid':ex.uid('editable-texture/'+d),'id':'0','source':'data:image/png;base64,'+base64.b64encode((EDIT/row['texture']).read_bytes()).decode(),'width':texture.width,'height':texture.height,'uv_width':texture.width,'uv_height':texture.height,'internal':True}]}
        write(SOURCE/entry['model'],data);(SOURCE/entry['texture']).write_bytes((EDIT/row['texture']).read_bytes())
        entry.update(editableSource=str((EDIT/row['model']).relative_to(SOURCE)),meshCount=len(meshes),triangles=sum(len(m['faces']) for m in meshes),modelSha256=ex.sha(SOURCE/entry['model']),sourceTextureSha256=ex.sha(EDIT/row['texture']))
        write(SOURCE/entry['component'],{k:v for k,v in entry.items() if k!='component'})
        proof.append({'definitionId':d,'cubes':count,'source':row['model'],'modelSha256':ex.sha(EDIT/row['model']),'textureSha256':ex.sha(EDIT/row['texture']),'uvSize':uvsize,'atlasCell':None if detached else cell,'runtimeMode':'native_attachment' if detached else 'inline'})
    for geo,path in [(high,highpath),(low,lowpath)]:
        geo['minecraft:geometry'][0]['description'].update(texture_width=atlas.width,texture_height=atlas.height);write(path,geo)
    atlaspath=OUT/'assets/tacz_assembly/textures/gun/editable_m4a1.png';atlaspath.parent.mkdir(parents=True,exist_ok=True);atlas.save(atlaspath)
    displaypath=OUT/'assets/tacz_assembly/display/guns/m4a1.json';display=ex.read(displaypath);display['texture']='tacz_assembly:gun/editable_m4a1';display['lod']['texture']=display['texture'];write(displaypath,display)
    write(BASE/'batches.json',batches)
    inline={}
    for row in rows:
        d=row['definitionId'];mount=row.get('nativeMount') or ('stock_pos' if d=='tacz_stock_tactical_ar' else None)
        if mount and row.get('runtimeMode')!='native_attachment':
            if mount!='stock_pos':raise ValueError('Inline mount requires explicit native attachment type: '+mount)
            inline.setdefault('stock',{})[sourceparts[d]['itemId']]=d
    write(BASE/'inline_attachments.json',inline)
    write(BASE/'native_attachment_overrides.json',attachment_overrides)
    write(SOURCE/'manifest.json',derivedmanifest)
    from build_workbench import build as workbench
    workbench()
    report={'parts':proof,'cubes':sum(p['cubes'] for p in proof),'highCubes':sum(len(b.get('cubes',[])) for b in hb),'lowCubes':sum(len(b.get('cubes',[])) for b in lb),'lodPolicy':'At most two largest current editable cubes per source bone; original non-editable variants retained','gameStarted':False}
    write(SOURCE/'editable-import-report.json',report)
    evidence=ex.read(BASE/'geometry-evidence.json')
    evidence.update(highCubes=report['highCubes'],lowCubes=report['lowCubes'],reusedLowCubes=0,lodPolicy=report['lodPolicy'],editableParts=len(rows),textureAtlas={'size':list(atlas_size),'cellSize':512,'originalCell':[0,0]})
    write(BASE/'geometry-evidence.json',evidence)
    print('Editable native import:',len(rows),'parts;',report['cubes'],'cubes; workbench/icons/native high/low generated')
if __name__=='__main__':
    raise SystemExit('Run tools/native_m4a1/generate.py to regenerate from pristine native rig inputs')
