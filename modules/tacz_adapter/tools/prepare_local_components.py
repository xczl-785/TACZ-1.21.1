"""Snapshot a selected SPT preset of component-local white products.

Explicit install matrices are author input, never inferred from a version string.
Only input decoding belongs here; runtime remains the common schema-3 gun path.
"""
from pathlib import Path
import argparse, hashlib, json, shutil
import numpy as np
from build_weapon import read, write, ROOT

C = np.diag([-100., 100., 100., 1.])

def affine(value):
    m = np.asarray(value, dtype=float)
    if m.shape != (4, 4) or not np.isfinite(m).all() or not np.allclose(m[3], [0,0,0,1]) or abs(np.linalg.det(m)) < 1e-10:
        raise ValueError('Invalid affine matrix')
    return m

def local_socket(meta, node):
    return np.linalg.inv(affine(meta['local_to_source_baked'])) @ C @ affine(node['rootMatrix']) @ np.linalg.inv(C)

def prepare(author, products, database):
    recipe = read(author/'import.json'); cfg = read(author/'placement.json')
    dest = ROOT/recipe['source']; dest.mkdir(parents=True, exist_ok=True)
    globals_path = database/'globals.json'; preset = read(globals_path)['ItemPresets'][cfg['preset']]
    raw = read(database/'templates/items.json')
    parts = preset['_items']; ids = {p['_tpl'] for p in parts}
    if len(ids) != len(parts): raise ValueError('Current model manifest requires unique definitions')
    root_props = raw[preset['_encyclopedia']]['_props']
    modes = {'single':'semi', 'fullauto':'auto'}
    if recipe['rpm'] != root_props['bFirerate'] or recipe['fireModes'] != [modes[m] for m in root_props['weapFireType']]:
        raise ValueError('Recipe firing parameters differ from source')
    if root_props['ReloadMode'] != 'ExternalMagazine':
        raise ValueError('This source adapter currently supports external magazines')
    magazines = [p for p in parts if p.get('slotId') == 'mod_magazine']
    if len(magazines) != 1 or recipe['capacity'] != raw[magazines[0]['_tpl']]['_props']['Cartridges'][0]['_max_count']:
        raise ValueError('Recipe magazine capacity differs from source')
    write(dest/'preset.spt.json', preset)
    write(dest/'preset.source.json', {'preset':preset['_id'], 'weapon':preset['_encyclopedia'], 'parts':parts})
    write(dest/'items.spt.json', {id:raw[id] for id in sorted(ids)})
    locales = {key:read(database/'locales/global'/filename) for key,filename in [('zh','ch.json'),('en','en.json')]}
    names = {id:{locale:values[id+' Name'] for locale,values in locales.items()} for id in ids}
    write(dest/'names.json', names)
    normalized = {}
    for id in ids:
        p = raw[id]['_props']; slots=[]
        for s in p.get('Slots', []):
            filters = s['_props']['filters']
            if len(filters)!=1: raise ValueError('Multiple slot filters need an explicit adapter')
            f=filters[0]
            if any(v not in raw for v in f['Filter']): raise ValueError('Unknown allowed template')
            slots.append({'nameId':s['_name'],'required':s.get('_required',False), 'filters':{'allowedItems':f['Filter'],'excludedItems':f.get('ExcludedFilter',[])}})
        properties={'slots':slots}
        for src,key in [('Ergonomics','ergonomics'),('RecoilForceUp','recoilVertical'),('RecoilForceBack','recoilHorizontal'),('CenterOfImpact','centerOfImpact'),('SightingRange','sightingRange'),('HeatFactor','heatFactor'),('CoolFactor','coolingFactor'),('DurabilityBurnModificator','durabilityBurnFactor')]:
            if src in p:properties[key]=p[src]
        if id!=preset['_encyclopedia']:properties['recoilModifier']=p.get('Recoil',0)/100
        normalized[id]={'weight':p['Weight'],'width':p['Width'],'height':p['Height'],'ergonomicsModifier':p.get('Ergonomics',0),'velocity':p.get('Velocity',0),'conflictingItems':p.get('ConflictingItems',[]),'properties':properties}
    write(dest/'items.raw.json', {'sourceFormat':'normalized SPT 5.0; complete original selected records in items.spt.json','data':{'items':normalized}})
    records={}; hashes={}; index={p['_id']:i for i,p in enumerate(parts,1)}
    for part in parts:
        id=part['_tpl']; folder=products/id; meta=read(folder/'component.json'); model=folder/'model.bbmodel'
        if hashlib.sha256(model.read_bytes()).hexdigest()!=meta['output_files_sha256']['product_model']:raise ValueError('Product hash mismatch: '+id)
        manuscript=(folder/meta['source_manuscript']).resolve()
        if hashlib.sha256((manuscript/'main.bbmodel').read_bytes()).hexdigest()!=meta['output_files_sha256']['manuscript_main']:raise ValueError('Manuscript hash mismatch: '+id)
        for name,digest in meta['source_files_sha256'].items():
            if hashlib.sha256((manuscript/'input'/name).read_bytes()).hexdigest()!=digest:raise ValueError('Input hash mismatch: '+id+'/'+name)
        target=dest/'source-pack/components'/id; target.mkdir(parents=True,exist_ok=True)
        for src,name in [(model,'model.bbmodel'),(folder/'component.json','source-component.json'),(folder/'source-evidence.json','source-evidence.json'),(manuscript/'input/nodes.json','nodes.json'),(manuscript/'input/bindings.json','bindings.json')]:
            shutil.copyfile(src,target/name); hashes[str(src)] = hashlib.sha256(src.read_bytes()).hexdigest()
        nodes=read(target/'nodes.json'); roots=[n for n in nodes if n['path']==meta['source_root']]
        if len(roots)!=1 or not np.allclose(C@affine(roots[0]['rootMatrix'])@np.linalg.inv(C),meta['local_to_source_baked']):raise ValueError('Source root mismatch')
        records[part['_id']]={'part':part,'meta':meta,'nodes':nodes,'target':target}
    active=set()
    def visit(instance):
        row=records[instance]
        if 'matrix' in row:return row['matrix']
        if instance in active:raise ValueError('Preset cycle')
        active.add(instance);p=row['part']
        if 'parentId' in p:
            parent=records[p['parentId']];pm=visit(p['parentId'])
            path=cfg.get('socketPaths',{}).get(instance)
            matches=[n for n in parent['nodes'] if (n['path']==path if path else n['path'].split('/')[-1]==p['slotId'])]
            if len(matches)!=1:raise ValueError('Missing/ambiguous socket '+instance)
            install=affine(cfg['installationLocal'][p['_tpl']])
            m=pm@local_socket(parent['meta'],matches[0])@C@install@np.linalg.inv(C)
            row['socket']=matches[0]['path'];row['installationLocal']=install.tolist()
        else:m=np.eye(4)
        row['matrix']=m;active.remove(instance);return m
    manifest=[];report=[]
    for part in parts:
        row=records[part['_id']];m=visit(part['_id']);id=part['_tpl'];parent=index.get(part.get('parentId'),'')
        meta=row['meta']|{'local_to_assembly':m.tolist(),'slot':part.get('slotId',''),'parent':parent}
        write(row['target']/'component.json',meta)
        manifest.append({'number':index[part['_id']],'parent_number':parent,'instance':part['_id'],'item_id':id,'slot':part.get('slotId',''),'model':f'components/{id}/model.bbmodel','sha256':meta['output_files_sha256']['product_model']})
        report.append({'instance':part['_id'],'item':id,'socket':row.get('socket'),'installationLocal':row.get('installationLocal'),'localToAssembly':m.tolist()})
    write(dest/'source-pack/manifest.json',manifest)
    write(dest/'assembly-transforms.json',{'policy':cfg['policy'],'parts':report})
    for path in [globals_path,database/'templates/items.json',database/'locales/global/ch.json',database/'locales/global/en.json']:
        hashes[str(path)]=hashlib.sha256(path.read_bytes()).hexdigest()
    write(dest/'source.json',{'version':'5.0','preset':preset['_id'],'inputs':hashes,'sourceProduct':'bulk-white-20260914','status':'snapshot; integration and acceptance tracked separately'})
    print('Snapshot verified:',len(parts),'products and manuscripts; matrices composed from explicit placement and sockets')

if __name__=='__main__':
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('author',type=Path);ap.add_argument('products',type=Path);ap.add_argument('database',type=Path);a=ap.parse_args();prepare(a.author,a.products,a.database)
