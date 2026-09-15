"""Build the ADAR integration from accepted geometry and EFTForge identities.
Only own geometry/UV/materials are used. TaCZ identifiers below reference existing effects, not balance data.
"""
from pathlib import Path
import json, hashlib, shutil
from PIL import Image, ImageDraw
from render_part_icon import render_part_icon
MODULE=Path(__file__).resolve().parents[1]; ROOT=MODULE.parents[1]
def build(author, output, reports):
    PRESENTATION=json.loads((author/'presentation.json').read_text())
    recipe=json.loads((author/'import.json').read_text()); source=ROOT/recipe['source']
    NAMES=json.loads((author/'names.json').read_text())
    OUT=Path(output); NS='newmod_adar'
    def write(path,data):
     p=OUT/path
     p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
    cat=json.loads((source/'catalog.json').read_text())
    manifest=json.loads((source/'manifest.json').read_text())
    scene=json.loads((source/'scene.json').read_text())
    raw=json.loads((ROOT/recipe['items']).read_text())['data']['items']
    models=manifest['models']; ids={p['id']:NS+':'+('adar' if i==0 else 'part_'+p['id']) for i,p in enumerate(cat['parts'])}
    write(Path('data')/NS/'adar/catalog.json',cat);write(Path('data')/NS/'adar/scene.json',scene)
    write(Path('assets')/NS/'adar/manifest.json',manifest)
    bindings=json.loads((author/'materials.json').read_text())
    write(Path('assets')/NS/'adar/materials.json',bindings)
    library=json.loads((author/'library.json').read_text())
    write(Path('assets')/NS/'adar/library.json',library)
    # Copy accepted pixels byte-for-byte into content ownership, before rendering icons.
    for resource, entry in recipe['textureCopies'].items():
        original=source/entry['path']
        if hashlib.sha256(original.read_bytes()).hexdigest()!=entry['sha256']:
            raise ValueError('Material source hash mismatch: '+resource)
        target=OUT/'assets'/resource.replace(':','/')
        target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(original,target)

    write(Path('data')/NS/'adar/mapping.json',ids)
    # Foundation must see concrete item identities, including the dedicated TaCZ subclass item.
    write(Path('data')/NS/'assembly/adar.json',{'schemaVersion':1,'items':[{'itemId':ids[p['id']],'slots':[{'id':s['id'],'compatibleItems':[ids[i] for i in s['allowedParts']],'requiredSiblingSlots':[],'conflictingSiblingSlots':[],'toggleable':False} for s in p['slots'] if s['allowedParts']]} for p in cat['parts']]})
    zh={};en={}
    for m in models:
     id=m['definitionId'];item=ids[id].split(':')[1]
     zh['item.'+NS+'.'+item]=NAMES[id]['zh'];en['item.'+NS+'.'+item]=NAMES[id]['en']
     write(Path('data')/NS/f'item_foundation/items/{item}.json',{'schema_version':3,'item':ids[id],'footprint':recipe['footprint'] if item=='adar' else [raw[id]['width'],raw[id]['height']],'weight_kg':raw[id]['weight'],**({'wearable_slots':['tactical_inventory:primary_weapon_1','tactical_inventory:primary_weapon_2']} if item=='adar' else {})})
     write(Path('assets')/NS/f'models/item/{item}.json',{'parent':'builtin/entity'} if item=='adar' else {'parent':'minecraft:item/generated','textures':{'layer0':NS+':item/'+item}})
     # Orthographic geometry icon, original shape silhouette, independent of Tarkov pictures.
     points=[v for mesh in m['meshes'] for t in mesh['triangles'] for v in t['vertices']]
     lo=[min(p[a] for p in points) for a in range(3)];hi=[max(p[a] for p in points) for a in range(3)]
     im=Image.new('RGBA',(128,128));draw=ImageDraw.Draw(im);scale=110/max(hi[2]-lo[2],hi[1]-lo[1],1)
     for mesh in m['meshes']:
      for t in mesh['triangles']:
       draw.polygon([(9+(v[2]-lo[2])*scale,64-(v[1]-(hi[1]+lo[1])/2)*scale) for v in t['vertices']],fill=(160,173,180,255))
     p=OUT/'assets'/NS/f'textures/item/{item}.png';p.parent.mkdir(parents=True,exist_ok=True)
     if item!='adar':im=render_part_icon(m,library,bindings,lambda resource: OUT/'assets'/resource.replace(':','/'))
     im.save(p)
    root_id=next(id for id,item in ids.items() if item==NS+':adar')
    zh['gun.'+NS+'.adar']=NAMES[root_id]['zh'];en['gun.'+NS+'.adar']=NAMES[root_id]['en']
    zh.update(json.loads((author/'lang/zh_cn.json').read_text()));en.update(json.loads((author/'lang/en_us.json').read_text()))
    write(Path('assets')/NS/'lang/zh_cn.json',zh);write(Path('assets')/NS/'lang/en_us.json',en)
    # Legacy rig/content are authored data; the converter does not own balance or animation values.
    for target, config in [('index/guns/adar.json','index.json'), ('data/guns/adar.json','execution.json')]:
        write(Path('data')/NS/target,json.loads((author/config).read_text()))
    for target, config in [('geo_models/gun/adar.json','rig.json'), ('animations/adar.animation.json','animations.json')]:
        write(Path('assets')/NS/target,json.loads((author/config).read_text()))
    display=json.loads((author/'display.json').read_text())
    display['zoom_model_fov']=PRESENTATION['aimModelFov']
    write(Path('assets')/NS/'display/guns/adar.json',display)
    p=OUT/'assets'/NS/'textures/gun/white.png';p.parent.mkdir(parents=True,exist_ok=True);Image.new('RGB',(16,16),'white').save(p)
    print('Generated ADAR gun resources, 10 physical parts and original rig/animations')


    write(Path('data')/NS/'item_foundation/identities/adar.json',json.loads((author/'identities.json').read_text()))
    from build_presentation import build as build_presentation
    build_presentation(author, OUT)
    report=Path(reports)/author.name/'import-report.json';report.parent.mkdir(parents=True,exist_ok=True)
    report.write_text(json.dumps({'converter':'legacy-adar','models':len(models),'triangles':sum(len(mesh['triangles']) for model in models for mesh in model['meshes']),'source':recipe['source']},indent=2)+'\n')

if __name__ == '__main__':
    from weapon_pipeline import main
    main(default_weapon='adar')
