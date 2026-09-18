"""Prepare the five remaining rifle/shotgun native assembly source packs.

This is deliberately an authoring-only audit.  It writes production inputs,
editable Blockbench/PNG copies, and gun-specific magazine variants; production
resources are owned by ``produce.py`` and are intentionally not generated here.
"""
import argparse
import copy
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'native_attachments'))
import extract as shared
import magazines

im = shared.im
ex = im.ex
R = im.R
LOD = {
    'strategy': 'conservative_cube_subset', 'silhouetteResolution': 96,
    'textureResolution': 48, 'maxSilhouetteLoss': .01,
    'maxTextureMeanError': .02, 'maxTextureChangedFraction': .05,
}
LOD_POLICY = ('Conservative original-cube subset; eight-view silhouette and 48px '
              'textured comparison gates, each geometry bone retained; full native functional rig')

# Each list is a source-bone ownership decision, not a name-pattern heuristic.
# Presentation bones are listed below separately and are never installed parts.
SPECS = {
 'qbz_191': {
  'root':'lower', 'caliber':'58x42', 'fire':'auto',
  'owners':{
   'lower':['gun_body','receiver','bone','body_rail2','pattern','mag_release','bolt_release','selector','bone3','bone6'],
   'upper_kit':['kit_default','deco2','bone15','bone16','rail','Gunrails','body_rail3','bone7'],
   'handguard':['handguard','bone9','bone10','bone11','bone12','bone13','bone14'],
   'barrel':['barrel','bone8'], 'muzzle':['muzzle_default','group2'], 'pistol_grip':['grip'],
   'bolt':['bolt'], 'stock':['buffer_tube','stock'],
   'front_sight':['fore_sight','sight_illuminated','octagon','fore_sight2','sight2_illuminated','octagon2'],
   'rear_sight':['rear_sight','rear_illuminated','rear_sight2','rear2_illuminated'], 'magazine_standard':['mag_standard']},
  'slots':{'lower':{'kit':['upper_kit'],'pistol_grip':['pistol_grip'],'stock':['stock','$stock'],'magazine':['magazine_standard','$extended_mag']},
           'upper_kit':{'handguard':['handguard'],'barrel':['barrel'],'bolt':['bolt'],'front_sight':['front_sight'],'rear_sight':['rear_sight'],'scope':['$scope'],'grip':['$grip'],'laser':['$laser']},
           'barrel':{'muzzle':['muzzle','$muzzle']}},
  'paths':{'SCOPE':['kit','scope'],'MUZZLE':['kit','barrel','muzzle'],'GRIP':['kit','grip'],'LASER':['kit','laser'],'STOCK':['stock'],'EXTENDED_MAG':['magazine']},
  'irons':[['kit','front_sight'],['kit','rear_sight']],
  'required':[['kit'],['kit','barrel'],['kit','bolt']], 'presentationExtra':['ar_stock_adapter'],
  'variants':{'front_sight':{'fore_sight':'upright','sight_illuminated':'upright','octagon':'upright','fore_sight2':'folded','sight2_illuminated':'folded','octagon2':'folded'},
              'rear_sight':{'rear_sight':'upright','rear_illuminated':'upright','rear_sight2':'folded','rear2_illuminated':'folded'}},
  'notes':['QBZ-191 front and rear mechanical-sight geometry retains both original upright and folded bones; scope gates only choose the native pose.','AR-stock adapter geometry is left under the native predicate and is not made a duplicate stock part.']},
 'scar_h': {
  'root':'lower', 'caliber':'762x51', 'fire':'semi',
  'owners':{
   'lower':['lower','magwell','trigger','switch','magrelease','octagon2','holdrelease','pattern'],
   'upper':['metal','group2','upper','group3','group4','screws'],
   'handguard':['handguard_default','Gunrails','Gunrails2','Gun_rails2','Gun_rails3'],
   'barrel':['barrel'], 'gas_system':['gaslock','group','gas_tube'], 'muzzle':['muzzle_default'],
   'pistol_grip':['grip'], 'bolt':['bolt','octagon3'], 'stock':['stock_extended_part','group8','bone','stock','group10','octagon9','bone5'],
   'front_sight':['fore_sight','sight_illuminated','fore_sight2','bone8'],
   'rear_sight':['group6','group7','sight_base'], 'magazine_standard':['mag_standard','group5']},
  'slots':{'lower':{'upper':['upper'],'pistol_grip':['pistol_grip'],'stock':['stock','$stock'],'magazine':['magazine_standard','$extended_mag']},
           'upper':{'handguard':['handguard'],'barrel':['barrel'],'bolt':['bolt'],'front_sight':['front_sight'],'rear_sight':['rear_sight'],'scope':['$scope'],'grip':['$grip'],'laser':['$laser']},
           'barrel':{'gas_system':['gas_system'],'muzzle':['muzzle','$muzzle']}},
  'paths':{'SCOPE':['upper','scope'],'MUZZLE':['upper','barrel','muzzle'],'GRIP':['upper','grip'],'LASER':['upper','laser'],'STOCK':['stock'],'EXTENDED_MAG':['magazine']},
  'irons':[['upper','front_sight'],['upper','rear_sight']],
  'required':[['upper'],['upper','barrel'],['upper','barrel','gas_system'],['upper','bolt']], 'presentationExtra':['ar_stock_adapter'],
  'variants':{'front_sight':{'fore_sight':'upright','sight_illuminated':'upright','fore_sight2':'folded','bone8':'folded'},'rear_sight':{'group6':'upright','group7':'folded'}},
  'notes':['SCAR-H front/rear sights use their original upright/folded branches, selected by the existing scope condition.','The original stock hinge hierarchy stays within the stock entity; no invented folding state is added.']},
 'mk14': {
  'root':'lower', 'caliber':'762x51', 'fire':'semi',
  'owners':{
   'lower':['receiver','group45','group46','magrelease','trigger2','boltrelease','selector','safety'],
   'upper':['mid'], 'handguard':['hg','group48','group49','front_rail','handguard'],
   'muzzle':['muzzle_default'], 'barrel':['sight_illuminated','octagon18','octagon19','octagon20','octagon21','octagon22'],
   'rear_sight':['bone3','octagon3'], 'pistol_grip':['grip2'], 'stock':['stock_default'],
   'mount':['mount'], 'bolt':['bolt2'], 'charging_handle':['charger'], 'bipod':['group47','l2','octagon26','r2','octagon27','octagon28'],
   'magazine_standard':['mb']},
  'slots':{'lower':{'upper':['upper'],'pistol_grip':['pistol_grip'],'stock':['stock','$stock'],'magazine':['magazine_standard','$extended_mag']},
           'upper':{'handguard':['handguard'],'barrel':['barrel'],'rear_sight':['rear_sight'],'mount':['mount'],'bolt':['bolt'],'charging_handle':['charging_handle'],'bipod':['bipod'],'grip':['$grip'],'laser':['$laser']},
           'barrel':{'muzzle':['muzzle','$muzzle']}, 'mount':{'scope':['$scope']}},
  'paths':{'SCOPE':['upper','mount','scope'],'MUZZLE':['upper','barrel','muzzle'],'GRIP':['upper','grip'],'LASER':['upper','laser'],'STOCK':['stock'],'EXTENDED_MAG':['magazine']},
  'irons':[['upper','barrel'],['upper','rear_sight']],
  'required':[['upper'],['upper','barrel'],['upper','bolt'],['upper','charging_handle']], 'presentationExtra':['ar_stock_adapter'],
  'notes':['MK14 optic path uses the original mount node; original rear sight remains as a physical assembly.','The bipod is one native articulated visual assembly; its child pivots remain untouched.']},
 'sks_tactical': {
  'root':'lower', 'caliber':'762x39', 'fire':'semi',
  'owners':{
   'lower':['receiver','trigger','magazinecatch','magrelease','pins','octagon13','octagon14','safety'],
   'handguard':['lower','upper','rail','adapter','bone5'], 'barrel':['bone2'], 'bolt':['bolt','octagon18','octagon19'],
   'front_sight':['sight','sight_front_illuminated'], 'rear_sight':['rearsight','sight_illuminated'],
   'stock':['stock','bone4'], 'heavy_stock':['oem_stock_heavy','bone6'], 'mount':['bone3','rail2'], 'magazine_standard':['mag_standard']},
  'slots':{'lower':{'handguard':['handguard'],'stock':['stock','heavy_stock','$stock'],'magazine':['magazine_standard','$extended_mag']},
           'handguard':{'barrel':['barrel'],'bolt':['bolt'],'front_sight':['front_sight'],'rear_sight':['rear_sight'],'mount':['mount'],'grip':['$grip'],'laser':['$laser']},
           'barrel':{'muzzle':['$muzzle']}, 'mount':{'scope':['$scope']}},
  'paths':{'SCOPE':['handguard','mount','scope'],'MUZZLE':['handguard','barrel','muzzle'],'GRIP':['handguard','grip'],'LASER':['handguard','laser'],'STOCK':['stock'],'EXTENDED_MAG':['magazine']},
  'irons':[['handguard','front_sight'],['handguard','rear_sight']],
  'required':[['handguard'],['handguard','barrel'],['handguard','bolt']],
  'notes':['SKS Tactical preserves both OEM stock source branches as separate physical choices; the default is the tactical OEM stock branch.','Native scope mount is a required physical gate for the original scope_pos path.']},
 'aa12': {
  'root':'lower', 'caliber':'12/70', 'fire':'semi',
  'owners':{
   'lower':['body','bone6','bone3','group4','bone4','safety','group9','group8','guard','octagon7','deco'],
   'upper':['aa12','deco2','gaslock','octagon2'], 'handguard':['handguard_tactical'],
   'barrel':['octagon3','group11'], 'front_sight':['frontsight'], 'rear_sight':['sight_back','sight_illuminated'],
   'mount':['mount_default','top_rail'], 'bolt':['bolt','octagon9','group14','octagon10'],
   'charging_handle':['charge_l','charge_r','base'], 'magazine_standard':['mag']},
  'slots':{'lower':{'upper':['upper'],'magazine':['magazine_standard','$extended_mag']},
           'upper':{'handguard':['handguard'],'barrel':['barrel'],'front_sight':['front_sight'],'rear_sight':['rear_sight'],'mount':['mount'],'bolt':['bolt'],'charging_handle':['charging_handle'],'grip':['$grip']},
           'mount':{'scope':['$scope']}, 'barrel':{'muzzle':['$muzzle']}},
  'paths':{'SCOPE':['upper','mount','scope'],'MUZZLE':['upper','barrel','muzzle'],'GRIP':['upper','grip'],'EXTENDED_MAG':['magazine']},
  'irons':[['upper','front_sight'],['upper','rear_sight']],
  'required':[['upper'],['upper','barrel'],['upper','bolt'],['upper','charging_handle']],
  'notes':['AA-12 retains the open-bolt native animation gate: bolt and charging-handle parts are both required.','No stock or laser path is invented because neither is allowed by the native attachment tag.']},
}

PROPS = {
 'lower':('weapon/firearm/assault_rifle',[5,2]), 'upper':('weapon_mod/receiver',[3,1]), 'upper_kit':('weapon_mod/receiver',[4,1]),
 'handguard':('weapon_mod/handguard',[3,1]), 'barrel':('weapon_mod/barrel',[2,1]), 'gas_system':('weapon_mod/gas_block',[1,1]),
 'muzzle':('weapon_mod/muzzle',[1,1]), 'pistol_grip':('weapon_mod/pistol_grip',[1,1]), 'bolt':('weapon_mod/receiver',[1,1]),
 'stock':('weapon_mod/stock',[2,1]), 'heavy_stock':('weapon_mod/stock',[2,1]), 'front_sight':('weapon_mod/sight/iron',[1,1]),
 'rear_sight':('weapon_mod/sight/iron',[1,1]), 'magazine_standard':('weapon_mod/magazine',[1,2]), 'mount':('weapon_mod/mount',[2,1]),
 'charging_handle':('weapon_mod/charging_handle',[1,1]), 'bipod':('weapon_mod/bipod',[2,1]),
}
CHINESE = {
 'lower':'下机匣与扳机组件','upper':'上机匣','upper_kit':'上机匣与导轨套件','handguard':'护木','barrel':'枪管',
 'gas_system':'导气组件','muzzle':'默认枪口','pistol_grip':'手枪握把','bolt':'枪机','stock':'原厂枪托',
 'heavy_stock':'重型原厂枪托','front_sight':'前机械瞄具','rear_sight':'后机械瞄具','magazine_standard':'标准弹匣',
 'mount':'瞄具安装座','charging_handle':'拉机柄','bipod':'两脚架',
}

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')

def descendants(name, bones):
    result = {name}
    for bone in bones.values():
        if bone.get('parent') == name:
            result |= descendants(bone['name'], bones)
    return result

def indexed(names, bones):
    return {name:list(range(len(bones[name].get('cubes', [])))) for name in names if bones[name].get('cubes')}

def presentation(bones):
    names = {'bullet','bullet_in_mag','bullet_in_barrel','bullet2','lefthand_pos','righthand_pos'}
    return indexed(names & set(bones), bones)

def make(gun, refresh=False):
    spec = SPECS[gun]
    root = R/'modules/tacz_adapter/weapon-sources'/('native_'+gun)
    index = ex.SRC/f'data/tacz/index/guns/{gun}.json'
    idx = ex.read(index); dp = ex.read(ex.asset(idx['display'],'display/guns','.json'))
    source = ex.asset(dp['model'],'geo_models','.json'); texture = ex.asset(dp['texture'],'textures','.png')
    geo = ex.read(source)['minecraft:geometry'][0]; bones = {b['name']:b for b in geo['bones']}
    owners = spec['owners']; parts=[]
    for short, names in owners.items():
        kind, footprint = PROPS[short]
        if short == 'lower' and gun == 'aa12': kind, footprint = 'weapon/firearm/shotgun', [5,2]
        parts.append({'definitionId':gun+'_'+short,'sourceCubeIndices':indexed(names,bones),'inventoryType':kind,'footprint':footprint})
    def full(prefix): return prefix if prefix.startswith('$') else gun+'_'+prefix
    slots = {gun+'_'+d:{slot:[full(v) for v in values] for slot,values in table.items()} for d,table in spec['slots'].items()}
    preset = {d:{slot:values[0] for slot,values in table.items() if not values[0].startswith('$')} for d,table in slots.items()}
    mags=[gun+'_magazine_standard']+[f'tacz_{gun}_extended_mag_{i}' for i in (1,2,3)]
    variants={}
    for owner, mapping in spec.get('variants',{}).items(): variants.update(mapping)
    presentation_only=presentation(bones)
    presentation_only.update(indexed(spec.get('presentationExtra',[]),bones))
    magowners={}
    mr=root/'magazine_variants'; manifest={'schemaVersion':1,'parts':[]}
    allowed,tags=magazines.allowed(gun)
    for level in (1,2,3):
        aid=f'tacz:extended_mag_{level}'
        if aid not in allowed: raise ValueError(f'{gun} does not permit {aid}')
        row, model, tex = magazines.extract(index,aid,tags)
        target=mr/row['model']
        if target.exists() and not refresh: raise ValueError('Refuse overwrite '+str(target))
        write(target,model); (mr/row['texture']).parent.mkdir(parents=True,exist_ok=True); (mr/row['texture']).write_bytes(tex.read_bytes())
        for extra in row['auxiliaryTextures']:
            target=mr/extra['file']; target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes((R/extra['source']).read_bytes())
        row['baselineModelSha256']=ex.sha(mr/row['model']); manifest['parts'].append(row)
        magowners[row['definitionId']]=row['sourceCubeIndices']
    # A cube belongs to one physical default part, one gun-specific extended
    # magazine, or presentation-only native animation geometry.  No fallback.
    assigned={}
    for definition, mapping in [(p['definitionId'],p['sourceCubeIndices']) for p in parts]+list(magowners.items())+[('presentationOnly',presentation_only)]:
        for bone, ids in mapping.items():
            for i in ids:
                if (bone,i) in assigned: raise AssertionError((gun,'duplicate',bone,i,assigned[bone,i],definition))
                assigned[bone,i]=definition
    expected={(name,i) for name,b in bones.items() for i in range(len(b.get('cubes',[])))}
    if set(assigned)!=expected: raise AssertionError((gun,'unassigned',sorted(expected-set(assigned))))
    defaults=[gun+'_'+n for n in owners]
    visual_bones={name:definitions for name,definitions in {'bullet':mags,'bullet_in_mag':mags,'bullet_in_barrel':[gun+'_barrel'],'bolt':[gun+'_bolt']}.items() if name in bones}
    if 'additional_magazine' in bones: visual_bones['additional_magazine']=mags
    always=['mag_standard','mag_extended_1','mag_extended_2','mag_extended_3','muzzle_default','stock_default']
    cfg={'schemaVersion':1,'sourceGun':gun,'gunId':'tacz_fork_tarkov:'+gun,'sourceDirectory':'native_'+gun,
      'rootDefinition':gun+'_'+spec['root'],'parts':parts,'slots':slots,'preset':preset,'presentationOnly':presentation_only,
      'weapon':{'schemaVersion':1,'gunId':'tacz_fork_tarkov:'+gun,'rootDefinition':gun+'_'+spec['root'],'resourceDirectory':gun,
       'modelType':'tacz_native_'+gun+'_assembly','caliber':spec['caliber'],'magazinePath':['magazine'],'requiredPaths':spec['required'],
       'defaultFireMode':spec['fire'],'feed':'detachable_magazine','nativeRig':True,'assemblyIcons':True,'authoringSource':'native_'+gun,'developmentCategory':'tacz_fork_tarkov',
       'wearableSlots':['tactical_inventory:primary_weapon_1','tactical_inventory:primary_weapon_2'],'partIconDirectory':'textures/item/'+gun},
      'nativeProfile':{'schemaVersion':1,'attachmentPaths':spec['paths'],'attachmentOverrides':{},
       'sightAlternatives':[[spec['paths']['SCOPE']],spec['irons']]},
      'visualRules':{'schemaVersion':1,'alwaysVisibleBones':[x for x in always if x in bones]+(['oem_stock_tactical','oem_stock_heavy'] if gun=='sks_tactical' else []),'definitionRequirements':{},
       'variantRequirements':{'upright':{'SCOPE':False},'folded':{'SCOPE':True}} if variants else {},'boneRequirements':visual_bones},
      'sourceBoneVariants':variants,'previewHiddenVariants':['folded'],'nativeMagazineCatalogs':[str((root/'magazine_variants/manifest.json').relative_to(R))],
      'lod':LOD,'lodPolicy':LOD_POLICY,'labels':{gun+'_'+name:[gun.upper()+' '+CHINESE[name],gun.upper()+' '+name.replace('_',' ')] for name in owners},
      'slotLabels':{},'gunLabels':[gun.upper()+' · 原生实体组装',gun.upper()+' · Native Assembly'],
      'excludedAttachments':['tacz:ammo_mod_fmj','tacz:ammo_mod_hp','tacz:ammo_mod_i','tacz:ammo_mod_he','tacz:ammo_mod_slug'],
      'editableAttachmentCatalogs':['modules/tacz_adapter/weapon-sources/native_attachments/editable/manifest.json','modules/tacz_adapter/weapon-sources/native_m4a1/editable/manifest.json'],
      'attachmentOverrideCatalogs':['data/tacz_fork_tarkov/native_attachments/standalone.json','data/tacz_fork_tarkov/m4a1/native_attachment_overrides.json'],
      'integrationFragments':'/tmp/native-gun-integration/'+gun}
    if gun == 'aa12':
        cfg['editableAttachmentCatalogs'].append('modules/tacz_adapter/weapon-sources/native_attachments/supplemental/manifest.json')
        cfg['attachmentOverrideCatalogs'].append('data/tacz_fork_tarkov/native_attachments/supplemental.json')
        cfg['supplementalAttachmentLibraries']=[{'source':'modules/tacz_adapter/weapon-sources/native_attachments/supplemental','catalog':'data/tacz_fork_tarkov/native_attachments/supplemental.json'}]
    # Only exclusions which actually occur in the native tag are retained.
    cfg['excludedAttachments']=[a for a in cfg['excludedAttachments'] if a in allowed]
    if 'STOCK' in spec['paths']:
        cfg['authoredStockAssets']=True
        cfg['attachmentOverrideCatalogs'].append('data/tacz_fork_tarkov/native_attachments/stocks.json')
    write(root/'production.json',cfg); write(root/'magazine_variants/manifest.json',manifest)
    editable=root/'editable'; editmanifest={'schemaVersion':1,'parts':[]}
    for part in parts:
        selected=copy.deepcopy(geo)
        for bone in selected['bones']:
            cubes=bone.pop('cubes',[]); ids=part['sourceCubeIndices'].get(bone['name'],[])
            if ids: bone['cubes']=[cubes[i] for i in ids]
        row,model=shared.encode(selected,texture,part['definitionId'])
        row.update(sourceGeometry=str(source.relative_to(R)),sourceTexture=str(texture.relative_to(R)),sourceSha256=ex.sha(source),sourceTextureSha256=ex.sha(texture),sourceCubeIndices=part['sourceCubeIndices'],runtimeMode='native_gun_bone_part')
        target=editable/row['model']
        if target.exists() and not refresh: raise ValueError('Refuse overwrite '+str(target))
        write(target,model); (editable/row['texture']).parent.mkdir(parents=True,exist_ok=True); (editable/row['texture']).write_bytes(texture.read_bytes())
        row['baselineModelSha256']=ex.sha(target); editmanifest['parts'].append(row)
    write(editable/'manifest.json',editmanifest)
    animation_path=ex.asset(dp['animation'],'animations','.animation.json'); animation=ex.read(animation_path)['animations']; active={n for a in animation.values() for n in a.get('bones',{})}
    low=ex.asset(dp['lod']['model'],'geo_models','.json'); lowgeo=ex.read(low)['minecraft:geometry'][0]
    data=ex.read(ex.SRC/f'data/tacz/data/guns/{idx["data"].split(":")[1]}.json')
    audit={'schemaVersion':1,'gunId':'tacz:'+gun,'sourceGeometry':str(source.relative_to(R)),'sourceTexture':str(texture.relative_to(R)),
      'sourceSha256':ex.sha(source),'nativeBones':len(bones),'totalCubes':len(expected),'defaultDefinitions':len(parts),
      'parts':parts,'magazineParts':magowners,'presentationOnly':presentation_only,'unassignedCubes':[],'duplicateOwnership':[],
      'sourceBoneVariants':variants,'visualRules':cfg['visualRules'],'animations':{name:list(a.get('bones',{})) for name,a in animation.items()},'animationRefsWithoutBones':sorted(active-set(bones)),
      'attachments':sorted(allowed),'nativeProfile':cfg['nativeProfile'],'reload':{'capacity':[data['ammo_amount']]+data['extended_mag_ammo_amount'],'nativeAnimation':dp['animation'],'additionalMagazineBonePresent':'additional_magazine' in bones},
      'sights':{'policy':'Existing native optical candidates only; no optical editable source was created.','nativeIronSightBones':sorted(set(sum((list(v) for v in spec.get('variants',{}).values()),[])) | {x for x in ('sight','sight_back','sight_illuminated','rearsight') if x in bones})},
      'low':{'path':str(low.relative_to(R)),'bones':len(lowgeo['bones']),'cubes':sum(len(b.get('cubes',[])) for b in lowgeo['bones']),'policy':LOD_POLICY},
      'notes':spec['notes']+['Every original cube has exactly one audited owner or presentation-only role.','Editable sources preserve original bones, parents, pivots, UV and animation ownership; icons are rendered muzzle-left by produce.py.']}
    write(root/'source-audit.json',audit)
    print(gun, 'default physical parts',len(parts),'source cubes',len(expected),'editable files',len(editmanifest['parts']))

if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('guns', nargs='*', choices=tuple(SPECS))
    parser.add_argument('--refresh-metadata', action='store_true', help='rewrite only generated production metadata')
    parser.add_argument('--refresh-sources', action='store_true', help='rewrite only this script generated authoring files')
    args=parser.parse_args(); selected=args.guns or list(SPECS)
    if args.refresh_metadata:
        for gun in selected:
            path=R/'modules/tacz_adapter/weapon-sources'/('native_'+gun)/'production.json'
            cfg=ex.read(path); spec=SPECS[gun]
            cfg['labels']={gun+'_'+name:[gun.upper()+' '+CHINESE[name],gun.upper()+' '+name.replace('_',' ')] for name in spec['owners']}
            cfg['nativeProfile']['sightAlternatives']=[[spec['paths']['SCOPE']],spec['irons']]
            if 'STOCK' in spec['paths']: cfg['authoredStockAssets']=True
            if gun == 'aa12':
                cfg['editableAttachmentCatalogs']=list(dict.fromkeys(cfg['editableAttachmentCatalogs']+['modules/tacz_adapter/weapon-sources/native_attachments/supplemental/manifest.json']))
                cfg['attachmentOverrideCatalogs']=list(dict.fromkeys(cfg['attachmentOverrideCatalogs']+['data/tacz_fork_tarkov/native_attachments/supplemental.json']))
                cfg['supplementalAttachmentLibraries']=[{'source':'modules/tacz_adapter/weapon-sources/native_attachments/supplemental','catalog':'data/tacz_fork_tarkov/native_attachments/supplemental.json'}]
            idx=ex.read(ex.SRC/f'data/tacz/index/guns/{gun}.json'); display=ex.read(ex.asset(idx['display'],'display/guns','.json'))
            native_bones={bone['name'] for bone in ex.read(ex.asset(display['model'],'geo_models','.json'))['minecraft:geometry'][0]['bones']}
            cfg['visualRules']['boneRequirements']={bone:definitions for bone,definitions in cfg['visualRules'].get('boneRequirements',{}).items() if bone in native_bones}
            write(path,cfg)
            audit_path=path.with_name('source-audit.json')
            audit=ex.read(audit_path); audit['nativeProfile']=cfg['nativeProfile']; audit['visualRules']=cfg['visualRules']
            write(audit_path,audit)
    else:
        for gun in selected: make(gun,args.refresh_sources)
