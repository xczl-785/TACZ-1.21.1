"""Native sample resource contract. This checks data, not Minecraft playback."""
from pathlib import Path
import json,re,hashlib
R=Path(__file__).resolve().parents[2];DEFAULT=R/'modules/tacz_adapter/weapon-content/resources';SRC=R/'src/main/resources/assets/tacz/custom/tacz_default_gun'
def read(p):return json.loads(re.sub(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*[\s\S]*?\*/',lambda m:m[0] if m[0].startswith('"') else '',p.read_text()))
def validate(resources=DEFAULT):
 out=Path(resources);base=out/'data/tacz_assembly/m4a1';a=out/'assets/tacz_assembly'
 assert read(a/'models/item/m4a1.json')['parent']=='builtin/entity'
 for source,target in [('preview.json','icon_geometry.json'),('library.json','icon_library.json'),('materials.json','icon_materials.json')]:assert read(base/source)==read(a/'m4a1'/target)
 config=read(base/'weapon.json');mapping=read(base/'mapping.json');external=read(base/'native_attachments.json');catalog={p['id']:p for p in read(base/'catalog.json')['parts']};nodes=read(base/'scene.json')['nodes']
 contract=read(base/'authoring-contract.json');assert contract['schemaVersion']==1 and contract['gunId']==config['gunId']
 for name,digest in contract['sources'].items():
  assert hashlib.sha256((R/'modules/tacz_adapter/weapon-sources/native_m4a1'/name).read_bytes()).hexdigest()==digest,('Stale M4 author source',name)
 assert len(nodes)==15 and len(external)==52 and len(catalog)==67
 assert not {'tacz:ammo_mod_fmj','tacz:ammo_mod_hp','tacz:ammo_mod_i'}&external.keys()
 assert set(mapping)==set(catalog)=={m['definitionId'] for m in read(base/'preview.json')['models']}
 assert set(external.values())<=catalog.keys()
 assert config['assemblyIcons'] is True
 assert read(base/'native-profile.json')==read(R/'modules/tacz_adapter/weapon-sources/native_m4a1/native-profile.json')
 assert read(base/'native-visual-rules.json')==read(R/'modules/tacz_adapter/weapon-sources/native_m4a1/native-visual-rules.json')
 editable=read(R/'modules/tacz_adapter/weapon-sources/native_m4a1/editable/manifest.json')['parts']
 edited={p['definitionId'] for p in editable}
 inline={v for variants in read(base/'inline_attachments.json').values() for v in variants.values()}
 assert inline<=edited and all(mapping[d] in external for d in inline)
 overrides=read(base/'native_attachment_overrides.json')
 detached={p['definitionId'] for p in editable if p.get('runtimeMode')=='native_attachment'}
 assert set(overrides)=={mapping[d] for d in detached}
 assert not detached&inline
 for item,entry in overrides.items():
  for key,folder,suffix in [('model','geo_models','.json'),('texture','textures','.png'),('lodModel','geo_models','.json'),('lodTexture','textures','.png')]:
   if key in entry:
    namespace,path=entry[key].split(':',1)
    assert namespace=='tacz_assembly'
    assert (out/'assets'/namespace/folder/(path+suffix)).is_file(),(item,key)
  assert ('lodModel' in entry)==('lodTexture' in entry)
 # Standard component contract: local geometry, textured UVs, and translated physical mount labels.
 preview=read(base/'preview.json');assert preview['schemaVersion']==3
 models={m['definitionId']:m for m in preview['models']}
 library=read(base/'library.json')['materials'];bindings=read(base/'materials.json')['parts']
 for d,m in models.items():
  assert m['attachmentOrigin']==[0,0,0]
  assert set(m['slots'])=={s['id'] for s in catalog[d]['slots']}
  assert m['meshes'] and all(mesh['triangles'] for mesh in m['meshes'])
  for mesh in m['meshes']:
   for triangle in mesh['triangles']:
    assert len(triangle['uv'])==3 and all(len(uv)==2 for uv in triangle['uv'])
    assert triangle['region']==mesh['name']
  texture=library[bindings[d]['defaultMaterial']]['texture']
  assert (out/'assets'/texture.replace(':','/')).is_file()
 # Radian's art frame points along +Z, independent of the native held rig frame.
 assert models['lower_receiver']['slots']['magazine'][2]>models['lower_receiver']['slots']['buffer'][2]
 assert models['barrel']['slots']['muzzle'][2]>0
 for locale in ['zh_cn','en_us']:
  lang=read(a/f'lang/{locale}.json')
  for m in models.values():
   for slot in m['slots']:assert 'weapon_assembly_ui.slot.'+slot in lang

 assert read(out/'data/tacz_assembly/data/guns/m4a1.json')==read(SRC/'data/tacz/data/guns/m4a1_data.json')
 native=read(SRC/'assets/tacz/display/guns/m4a1_display.json');display=read(a/'display/guns/m4a1.json')
 for k,v in native.items():
  if k not in {'model','model_type','lod','texture'}:assert display[k]==v,k
 original=read(SRC/'assets/tacz/geo_models/gun/m4a1_geo.json')['minecraft:geometry'][0]['bones'];batches=read(base/'batches.json');high=read(a/'geo_models/gun/m4a1.json')['minecraft:geometry'][0]['bones'];low=read(a/'geo_models/gun/lod/m4a1.json')['minecraft:geometry'][0]['bones']
 for bones in [high,low]:
  lookup={b['name']:b for b in bones};assert len(lookup)==len(bones)
  for b in original:
   assert {k:v for k,v in b.items() if k!='cubes'}=={k:v for k,v in lookup[b['name']].items() if k!='cubes'},b['name']
  assert {v['definition'] for k,v in batches.items() if k in lookup}==set(mapping)-set(external.values())|{'tacz_extended_mag_1','tacz_extended_mag_2','tacz_extended_mag_3'}|inline
  for b in bones:
   if b.get('parent'):assert b['parent'] in lookup
   if b['name'].startswith('assembly_'):assert b['name'] in batches
 for definition,id in mapping.items():
  assert (a/f'textures/item/{definition}.png').is_file()
  if id in external:continue
  assert (out/f'data/tacz_assembly/item_foundation/items/{id.split(":")[1]}.json').is_file()
  assert (a/f'models/item/{id.split(":")[1]}.json').is_file()
 evidence=read(base/'geometry-evidence.json');assert evidence['editableParts']==len(edited) and evidence['reusedLowCubes']==0 and evidence['maximumNeutralMatrixError']<1e-10 and evidence['lowCubes']<evidence['highCubes']
 print('Native M4A1 contract PASS: 15 default nodes, 52 attachments, 140 preserved rig nodes; high/low',evidence['highCubes'],evidence['lowCubes'])
if __name__=='__main__':validate()
