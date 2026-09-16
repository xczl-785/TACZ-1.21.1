"""Reviewed M16A1 / SCAR-L native cube ownership and append-only authoring inputs.
Only writes each gun's source directory. Runtime production belongs to produce.py.
"""
import copy,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'native_attachments'))
import extract as shared
import magazines
im=shared.im;ex=im.ex;R=ex.R

def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n')
def make(gun):
 root=R/'modules/tacz_adapter/weapon-sources'/('native_'+gun)
 dp=ex.read(ex.asset('tacz:'+gun+'_display','display/guns','.json'));source=ex.asset(dp['model'],'geo_models','.json');texture=ex.asset(dp['texture'],'textures','.png');geo=ex.read(source)['minecraft:geometry'][0];bs={b['name']:b for b in geo['bones']}
 def sub(n):return [b['name'] for b in geo['bones'] if n in ex.ancestors(b['name'],bs)]
 def indices(names):return {n:list(range(len(bs[n]['cubes']))) for n in names if bs[n].get('cubes')}
 if gun=='m16a1':
  owners={'lower':['lower','bolt_release','mag_release','deco','safety','octagon','light_part','group4','group5'],'upper':['upper','group2','cap'],'barrel':['octagon2'],'front_sight':['fix_sight','sight_illuminated','bone','bone2'],'pistol_grip':['grip'],'muzzle':['muzzle_default','bone3'],'charging_handle':['pull'],'bolt':['bolt','group'],'rear_sight':['fix_carry','group7','octagon5'],'stock':['stock','group16','group17','group18'],'barrel_ring':['octagon3','octagon4'],'handguard':['handguard'],'magazine_standard':['mag2']}
  slots={'lower':{'upper':['upper'],'pistol_grip':['pistol_grip'],'stock':['stock'],'magazine':['magazine_standard','$extended_mag']},'upper':{'barrel_ring':['barrel_ring'],'bolt':['bolt'],'charging_handle':['charging_handle'],'rear_sight':['rear_sight']},'barrel_ring':{'barrel':['barrel'],'handguard':['handguard']},'barrel':{'front_sight':['front_sight'],'muzzle':['muzzle','$muzzle']},'rear_sight':{'scope':['$scope']}}
  paths={'MUZZLE':['upper','barrel_ring','barrel','muzzle'],'SCOPE':['upper','rear_sight','scope'],'EXTENDED_MAG':['magazine']}
  front=['upper','barrel_ring','barrel','front_sight'];rear=['upper','rear_sight'];required=[['upper'],['upper','barrel_ring'],['upper','barrel_ring','barrel'],['upper','bolt'],['upper','charging_handle']]
  presentation=indices(['bullet','bullet_in_mag','bullet_in_barrel','lefthand_pos','righthand_pos']);variants={};always=['mag_standard','mag_extended_1','mag_extended_2','mag_extended_3','muzzle_default']
 else:
  owners={'lower':['lower','trigger','switch','magrelease','octagon2','holdrelease'],'upper':['metal','group2','upper','group3','group4','pattern','Gun_rails2','Gun_rails3','Gun_rails4','Gunrails','screws','group5','base','handguard_default'],'barrel':['barrel'],'gas_system':['gaslock','gas_tube'],'pistol_grip':['grip'],'stock':['stock_extended_part','group8','bone','stock','octagon9','bone5'],'muzzle':['muzzle_default','octagon4'],'bolt':['bolt','octagon3'],'front_sight':['fore_sight','sight_illuminated','fore_sight2','bone8'],'rear_sight':['rear_sight','octagon5','octagon6','octagon7','octagon8','octagon','rear_sight2'],'magazine_standard':['mag_standard']}
  slots={'lower':{'upper':['upper'],'pistol_grip':['pistol_grip'],'stock':['stock','$stock'],'magazine':['magazine_standard','$extended_mag']},'upper':{'barrel':['barrel'],'bolt':['bolt'],'front_sight':['front_sight'],'rear_sight':['rear_sight'],'scope':['$scope'],'grip':['$grip'],'laser':['$laser']},'barrel':{'gas_system':['gas_system'],'muzzle':['muzzle','$muzzle']}}
  paths={'MUZZLE':['upper','barrel','muzzle'],'SCOPE':['upper','scope'],'EXTENDED_MAG':['magazine'],'STOCK':['stock'],'GRIP':['upper','grip'],'LASER':['upper','laser']}
  front=['upper','front_sight'];rear=['upper','rear_sight'];required=[['upper'],['upper','barrel'],['upper','barrel','gas_system'],['upper','bolt']]
  presentation=indices(['bullet','bullet_in_mag','bullet2','lefthand_pos','righthand_pos','ar_stock_adapter']);variants={n:('folded' if 'sight_folded' in ex.ancestors(n,bs) else 'upright') for n in owners['front_sight']+owners['rear_sight']};always=['mag_standard','mag_extended_1','mag_extended_2','mag_extended_3','muzzle_default','stock_default','sight','sight_folded']
 d=lambda x:gun+'_'+x
 inventory_metadata={'lower': ('weapon/firearm/assault_rifle', [5, 2]), 'upper': ('weapon_mod/receiver', [2, 1]), 'barrel': ('weapon_mod/barrel', [2, 1]), 'front_sight': ('weapon_mod/sight/iron', [1, 1]), 'rear_sight': ('weapon_mod/sight/iron', [1, 1]), 'pistol_grip': ('weapon_mod/pistol_grip', [1, 1]), 'muzzle': ('weapon_mod/muzzle', [1, 1]), 'charging_handle': ('weapon_mod/charging_handle', [2, 1]), 'bolt': ('weapon_mod/receiver', [1, 1]), 'stock': ('weapon_mod/stock', [2, 1]), 'barrel_ring': ('weapon_mod/receiver', [1, 1]), 'handguard': ('weapon_mod/handguard', [2, 1]), 'magazine_standard': ('weapon_mod/magazine', [1, 1]), 'gas_system': ('weapon_mod/gas_block', [1, 1])}
 parts=[{'definitionId':d(n),'sourceCubeIndices':indices(v)} for n,v in owners.items()]
 for part in parts:
  kind,size=inventory_metadata[part['definitionId'][len(gun)+1:]];part.update(inventoryType=kind,footprint=size)
 prefix=lambda x:x if x.startswith('$') else d(x)
 converted={d(n):{slot:[prefix(v) for v in choices] for slot,choices in ss.items()} for n,ss in slots.items()}
 preset={n:{s:c[0] for s,c in ss.items() if not c[0].startswith('$')} for n,ss in converted.items()};preset={k:v for k,v in preset.items() if v}
 mags=[d('magazine_standard')]+['tacz_'+gun+'_extended_mag_'+str(i) for i in (1,2,3)]
 bone_req={'bolt':[d('bolt')],'bullet':mags,'bullet_in_mag':mags,'bullet_in_barrel':[d('barrel')],'muzzle_flash':[d('barrel')]}
 if 'additional_magazine' in bs:bone_req['additional_magazine']=mags
 cfg=copy.deepcopy(ex.read(R/'modules/tacz_adapter/weapon-sources/native_glock_17/production.json'))
 cfg.update(sourceGun=gun,gunId='tacz_assembly:'+gun,sourceDirectory='native_'+gun,rootDefinition=d('lower'),parts=parts,slots=converted,preset=preset,presentationOnly=presentation,integrationFragments='/tmp/'+gun+'-integration',gunLabels=[gun.upper()+' · 原生实体组装',gun.upper()+' · Native Assembly'])
 cfg['weapon'].update(gunId=cfg['gunId'],rootDefinition=d('lower'),resourceDirectory=gun,modelType='tacz_native_'+gun+'_assembly',caliber='556x45',requiredPaths=required,developmentSource='native_'+gun,defaultFireMode='auto',wearableSlots=['tactical_inventory:primary_weapon_1','tactical_inventory:primary_weapon_2'])
 cfg['nativeProfile']={'schemaVersion':1,'attachmentPaths':paths,'attachmentOverrides':{},'sightAlternatives':[[paths['SCOPE']],[front,rear]]}
 cfg['visualRules']={'schemaVersion':1,'alwaysVisibleBones':always,'definitionRequirements':{},'variantRequirements':{'upright':{'SCOPE':False},'folded':{'SCOPE':True}} if variants else {},'boneRequirements':bone_req}
 cfg['sourceBoneVariants']=variants;cfg['previewHiddenVariants']=['folded'];cfg['nativeMagazineCatalogs']=[str((root/'magazine_variants/manifest.json').relative_to(R))]
 labels={'lower':'下机匣','upper':'上机匣与导轨','barrel':'枪管','front_sight':'前准星总成','rear_sight':'后照门总成','pistol_grip':'手枪握把','muzzle':'默认枪口','charging_handle':'拉机柄','bolt':'枪机','stock':'默认枪托','barrel_ring':'枪管连接环','handguard':'护木','magazine_standard':'标准弹匣','gas_system':'导气总成'}
 cfg['labels']={d(n):[gun.upper()+' '+labels[n],gun.upper()+' '+n.replace('_',' ')] for n in owners};cfg['slotLabels']={};cfg['weapon']['partIconDirectory']='textures/item/'+gun
 # Exact physical provenance: every native cube has one owner or presentation role.
 magowners={}
 for i in (1,2,3):magowners['tacz_'+gun+'_extended_mag_'+str(i)]=indices(sub('mag_extended_'+str(i)))
 pairs=[(n,i) for mapping in [p['sourceCubeIndices'] for p in parts]+list(magowners.values())+[presentation] for n,ids in mapping.items() for i in ids]
 expected={(n,i) for n,b in bs.items() for i in range(len(b.get('cubes',[])))}
 assert len(pairs)==len(set(pairs)) and set(pairs)==expected,(gun,expected-set(pairs))
 write(root/'production.json',cfg)
 animation=ex.read(ex.asset(dp['animation'],'animations','.animation.json'))['animations'];active={n for a in animation.values() for n in a.get('bones',{})}
 allowed,tags=magazines.allowed(gun);data=ex.read(magazines.DATA/'data/guns'/f'{gun}_data.json')
 audit={'gunId':'tacz:'+gun,'sourceGeometry':str(source.relative_to(R)),'sourceSha256':ex.sha(source),'nativeBones':len(bs),'totalCubes':len(expected),'defaultDefinitions':len(parts),'parts':parts,'magazineParts':magowners,'presentationOnly':presentation,'sourceBoneVariants':variants,'unassigned':[],'duplicateCubes':[],'animations':{n:list(a.get('bones',{})) for n,a in animation.items()},'animationRefsWithoutBones':sorted(active-set(bs)),'attachments':sorted(allowed),'capacity':[data['ammo_amount']]+data['extended_mag_ammo_amount'],'notes':['Preserve every original bone pivot,parent and animation; edit cubes only.','Optics are existing native read-only projections; no optical edit source created.','SCAR handguard_default cover retains native grip/laser conditional; ar_stock_adapter retains native adapter predicate.' if gun=='scar_l' else 'Fixed front gas/sight assembly and fixed carry/rear sight remain upright with original optic behavior; do not apply M4 folding.']}
 write(root/'source-audit.json',audit)
 # Authoring only; no runtime paths changed.
 edit=root/'editable';manifest={'schemaVersion':1,'parts':[]}
 for part in parts:
  selected=copy.deepcopy(geo)
  for b in selected['bones']:
   cubes=b.pop('cubes',[]);ids=part['sourceCubeIndices'].get(b['name'],[])
   if ids:b['cubes']=[cubes[i] for i in ids]
  row,model=shared.encode(selected,texture,part['definitionId']);row.update(sourceGeometry=str(source.relative_to(R)),sourceTexture=str(texture.relative_to(R)),sourceSha256=ex.sha(source),sourceTextureSha256=ex.sha(texture),sourceCubeIndices=part['sourceCubeIndices'],runtimeMode='native_gun_bone_part')
  target=edit/row['model']
  if target.exists():raise ValueError('Refuse overwrite '+str(target))
  write(target,model);(edit/row['texture']).write_bytes(texture.read_bytes());row['baselineModelSha256']=ex.sha(target);manifest['parts'].append(row)
 write(edit/'manifest.json',manifest)
 mm={'schemaVersion':1,'parts':[]}
 for i in (1,2,3):
  aid='tacz:extended_mag_'+str(i);row,model,tex=magazines.extract(magazines.DATA/'index/guns'/f'{gun}.json',aid,tags);mr=root/'magazine_variants';target=mr/row['model']
  if target.exists():raise ValueError('Refuse overwrite '+str(target))
  write(target,model);(mr/row['texture']).write_bytes(tex.read_bytes())
  for extra in row['auxiliaryTextures']:(mr/extra['file']).write_bytes((R/extra['source']).read_bytes())
  row['baselineModelSha256']=ex.sha(target);mm['parts'].append(row)
 write(root/'magazine_variants/manifest.json',mm)
 print(gun,len(parts),len(expected),'cubes',len(bs),'bones')
if __name__=='__main__':
 for gun in ('m16a1','scar_l'):make(gun)
