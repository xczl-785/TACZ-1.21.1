"""Append-only complete native optic author sources and explicit gun opt-ins."""
import argparse
import copy
from pathlib import Path
import produce as p

CATALOG=p.SOURCES/'catalog.json'

def discover():
    return [(path,p.ex.read(path)) for path in sorted((p.ex.SRC/'data/tacz/index/attachments').glob('*.json')) if p.ex.read(path).get('type')=='scope']

def initialize():
    existing={p.ex.read(f)['sourceAttachment']:f.parent for f in p.SOURCES.glob('*/optic.json')}
    rows=[]
    for index_path,index in discover():
        original='tacz:'+index_path.stem
        source=existing.get(original,p.SOURCES/(index_path.stem+'_authored'))
        display_path=p.ex.asset(index['display'],'display/attachments','.json');display=p.ex.read(display_path)
        if source.exists() and not (source/'optic.json').is_file():
            raise ValueError('Unregistered partial source; review before retry: '+str(source))
        if not source.exists():
            geometry=p.ex.asset(display['model'],'geo_models','.json');texture=p.ex.asset(display['texture'],'textures','.png')
            data=p.ex.SRC/f"data/tacz/data/attachments/{index['data'].split(':')[1]}.json"
            lod_model=p.ex.asset(display['lod']['model'],'geo_models','.json');lod_texture=p.ex.asset(display['lod']['texture'],'textures','.png')
            slot=p.ex.asset(display['slot'],'textures','.png')
            lod_policy='Preserve original LOD; full functional model in first person'
            if not lod_model.is_file() or not lod_texture.is_file():
                lod_model,lod_texture=geometry,texture
                lod_policy='Source LOD incomplete: explicit full model and matching texture fallback'
            inputs=[index_path,display_path,geometry,texture,data,lod_model,lod_texture,slot]
            row,model=p.shared.encode(p.ex.read(geometry)['minecraft:geometry'][0],texture,source.name)
            row.update(sourceGeometry=str(geometry.relative_to(p.R)),sourceSha256=p.ex.sha(geometry),sourceTextureSha256=p.ex.sha(texture))
            p.write(source/'editable'/row['model'],model);(source/'editable'/row['texture']).write_bytes(texture.read_bytes())
            for suffix in ('_n','_s'):
                extra=texture.with_stem(texture.stem+suffix)
                if extra.exists():
                    (source/'editable'/Path(row['texture']).with_name('texture'+suffix+'.png')).write_bytes(extra.read_bytes());inputs.append(extra)
            p.write(source/'editable/manifest.json',{'schemaVersion':1,'parts':[row]})
            p.write(source/'display.json',display);p.write(source/'data.json',p.ex.read(data))
            p.write(source/'lod/model.json',p.ex.read(lod_model));(source/'lod/texture.png').write_bytes(lod_texture.read_bytes());(source/'slot.png').write_bytes(slot.read_bytes())
            names={}
            for locale in ('zh_cn','en_us'):
                lang=p.ex.read(p.ex.SRC/f'assets/tacz/lang/{locale}.json')
                names[locale]=lang.get(index['name'],index_path.stem)+(' · 光学制作' if locale=='zh_cn' else ' · Authored optic')
            roots=[n for n in ('scope_body','ocular_ring') if n in row['sourceCubeIndices']]
            if index_path.stem=='sight_p90':roots=['default_sight']
            p.write(source/'optic.json',{'schemaVersion':1,'attachmentId':'tacz_fork_tarkov:'+source.name,'sourceAttachment':original,'names':names,'exteriorRoots':roots,'lod':{'model':'lod/model.json','texture':'lod/texture.png','policy':lod_policy},'sourceHashes':{str(f.relative_to(p.R)):p.ex.sha(f) for f in inputs}})
        config=p.ex.read(source/'optic.json')
        rows.append({'sourceAttachment':original,'attachmentId':config['attachmentId'],'source':str(source.relative_to(p.R)),'kind':'hybrid' if display.get('scope') and display.get('sight') else 'scope' if display.get('scope') else 'sight'})
    p.write(CATALOG,{'schemaVersion':1,'policy':'Additive; inherit compatibility from original ID; originals and presets unchanged','optics':rows})
    return rows

def bind_guns(rows):
    root=p.R/'modules/tacz_adapter/weapon-sources'
    author=root/'native_m4a1';assembly=p.ex.read(author/'assembly.json');mounts=p.ex.read(author/'mounts.json');config=p.ex.read(author/'optics.json');entries={e['source']:e for e in config['optics']}
    for row in rows:
        old=assembly['external'].get(row['sourceAttachment'])
        if old is None:continue
        entry=entries.get(row['source'],{'definitionId':row['attachmentId'].replace(':','_'),'source':row['source'],'nativeMount':'scope_pos'})
        entries[row['source']]=entry;new=entry['definitionId'];assembly['external'][row['attachmentId']]=new
        for slots in assembly['slots'].values():
            for choices in slots.values():
                if old in choices and new not in choices:choices.append(new)
        if new not in mounts['parts']:mounts['parts'][new]=copy.deepcopy(mounts['parts'][old])
    config['optics']=list(entries.values());p.write(author/'optics.json',config);p.write(author/'assembly.json',assembly);p.write(author/'mounts.json',mounts)
    # Derive only from each gun's already published native compatibility; never widen it.
    for path in sorted(root.glob('native_*/production.json')):
        config=p.ex.read(path);base=p.OUT/f"data/tacz_fork_tarkov/{config['sourceGun']}";external=p.ex.read(base/'native_attachments.json');mounts=p.ex.read(path.parent/'mounts.json');selected=[]
        for row in rows:
            old=external.get(row['sourceAttachment'])
            if old is None:continue
            selected.append(row['source']);new=row['attachmentId'].replace(':','_')
            if new not in mounts['parts']:mounts['parts'][new]=copy.deepcopy(mounts['parts'][old])
            roots=config.get('scopeExteriorRoots',{}).get(row['sourceAttachment'])
            if roots:config['scopeExteriorRoots'][row['attachmentId']]=roots
        config['authoredOptics']=selected;p.write(path,config);p.write(path.parent/'mounts.json',mounts)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--bind-guns',action='store_true');args=parser.parse_args()
    rows=initialize()
    if args.bind_guns:bind_guns(rows)
    print('Complete author sources:',len(rows))
