"""Audited UMP45 source ownership; only writes native_ump45 authoring inputs.
The upper receiver retains its integrated iron-sight/protective-ear geometry.
"""
import argparse,copy,json,sys
from pathlib import Path
import numpy as np
sys.path.insert(0,str(Path(__file__).resolve().parent))
import produce as p
R=p.R;ROOT=R/'modules/tacz_adapter/weapon-sources/native_ump45'

def create():
    index,idx,display,dp,source,texture,data=p.inputs({'sourceGun':'ump45'})
    geo=p.ex.read(source)['minecraft:geometry'][0];bones={b['name']:b for b in geo['bones']}
    animation=p.ex.asset(dp['animation'],'animations','.animation.json');anim=p.ex.read(animation)
    active={n for a in anim['animations'].values() for n in a.get('bones',{})}
    ownership={
      'ump_lower':['lower','pattern','mag_release','bolt_release','ump45_safety'],
      'ump_upper':['group2','group3','sight_illuminated','hinge2','octagon2'],
      'ump_barrel':['octagon3','octagon4'],
      'ump_bolt':['ump45_bolt'],
      'ump_charging_handle':['ump45_charge_handle'],
      'ump_stock':['octagon','group6','group','hinge'],
      'ump_rails':['rails','side_rail2','side_rail','bottom_rail','top_rail'],
      'ump_magazine_standard':['mag_standard']}
    props={
      'ump_lower':('weapon/firearm/smg',[3,2],'UMP45 下机匣与扳机组件','UMP45 lower receiver and trigger assembly'),
      'ump_upper':('weapon_mod/receiver',[4,1],'UMP45 上机匣（原厂机械瞄具）','UMP45 upper receiver with factory iron sights'),
      'ump_barrel':('weapon_mod/barrel',[2,1],'UMP45 枪管','UMP45 barrel'),
      'ump_bolt':('weapon_mod/receiver',[2,1],'UMP45 枪机','UMP45 bolt'),
      'ump_charging_handle':('weapon_mod/charging_handle',[1,1],'UMP45 拉机柄','UMP45 charging handle'),
      'ump_stock':('weapon_mod/stock',[2,2],'UMP45 原厂枪托组件','UMP45 factory stock assembly'),
      'ump_rails':('weapon_mod/mount',[3,1],'UMP45 导轨套件','UMP45 rail set'),
      'ump_magazine_standard':('weapon_mod/magazine',[1,2],'UMP45 标准25发弹匣','UMP45 standard 25-round magazine')}
    parts=[]
    for d,names in ownership.items():
        typ,size,*_=props[d]
        parts.append({'definitionId':d,'sourceCubeIndices':{n:list(range(len(bones[n]['cubes']))) for n in names},'inventoryType':typ,'footprint':size})
    magazines=['ump_magazine_standard']+['tacz_ump45_light_extended_mag_'+str(i) for i in (1,2,3)]
    config={
      'schemaVersion':1,'sourceGun':'ump45','gunId':'tacz_assembly:ump45','sourceDirectory':'native_ump45','rootDefinition':'ump_lower','parts':parts,
      'slots':{'ump_lower':{'upper':['ump_upper'],'magazine':['ump_magazine_standard','$extended_mag']},
               'ump_upper':{'barrel':['ump_barrel'],'bolt':['ump_bolt'],'charging_handle':['ump_charging_handle'],'stock':['ump_stock'],'rails':['ump_rails']},
               'ump_barrel':{'muzzle':['$muzzle']},'ump_rails':{'scope':['$scope'],'grip':['$grip'],'laser':['$laser']}},
      'preset':{'ump_lower':{'upper':'ump_upper','magazine':'ump_magazine_standard'},
                'ump_upper':{'barrel':'ump_barrel','bolt':'ump_bolt','charging_handle':'ump_charging_handle','stock':'ump_stock','rails':'ump_rails'}},
      'weapon':{'schemaVersion':1,'gunId':'tacz_assembly:ump45','rootDefinition':'ump_lower','resourceDirectory':'ump45','modelType':'tacz_native_ump45_assembly',
        'caliber':'45acp','magazinePath':['magazine'],'requiredPaths':[['upper'],['upper','barrel'],['upper','bolt'],['upper','charging_handle']],
        'defaultFireMode':'auto','feed':'detachable_magazine','nativeRig':True,'assemblyIcons':True,'developmentSource':'native_ump45',
        'wearableSlots':['tactical_inventory:primary_weapon_1','tactical_inventory:primary_weapon_2'],'partIconDirectory':'textures/item/ump45'},
      'nativeProfile':{'schemaVersion':1,'attachmentPaths':{'SCOPE':['upper','rails','scope'],'MUZZLE':['upper','barrel','muzzle'],
          'GRIP':['upper','rails','grip'],'LASER':['upper','rails','laser'],'EXTENDED_MAG':['magazine']},'attachmentOverrides':{},
          'sightAlternatives':[[['upper','rails','scope']],[['upper']]]},
      'visualRules':{'schemaVersion':1,'alwaysVisibleBones':['mag_standard','mag_extended_1','mag_extended_2','mag_extended_3'],
          'definitionRequirements':{},'variantRequirements':{},'boneRequirements':{'bullet':['ump_magazine_standard']+magazines[1:],'bullet_in_mag':magazines,'bullet_in_barrel':['ump_barrel'],'ump45_bolt':['ump_bolt']}},
      'presentationOnly':{n:list(range(len(b['cubes']))) for n,b in bones.items() if b.get('cubes') and n in {'lefthand_pos','righthand_pos','4','2','3'}},
      'lodPolicy':'Complete editable geometry with full native high rig in both high and LOD; no unsafe original low topology reuse',
      'labels':{d:list(props[d][2:]) for d in ownership},
      'excludedAttachments':['tacz:ammo_mod_fmj','tacz:ammo_mod_hp','tacz:ammo_mod_i'],
      'editableAttachmentCatalogs':['modules/tacz_adapter/weapon-sources/native_attachments/editable/manifest.json','modules/tacz_adapter/weapon-sources/native_m4a1/editable/manifest.json'],
      'attachmentOverrideCatalogs':['data/tacz_assembly/native_attachments/standalone.json','data/tacz_assembly/m4a1/native_attachment_overrides.json'],
      'gunLabels':['UMP45 · 原生实体组装','UMP45 · Native Assembly'],
      'slotLabels':{'rails':['导轨套件','Rail set'],'charging_handle':['拉机柄','Charging handle']},'integrationFragments':'/tmp/ump45-integration'}
    allowed,tags=p.magazines.allowed('ump45')
    public=[row for row in p.ex.read(p.magazines.ROOT/'manifest.json')['parts'] if row['gunId']=='tacz:ump45']
    assert len(public)==3
    assigned={}
    for row in parts:
        for n,indices in row['sourceCubeIndices'].items():
            for i in indices:assert (n,i) not in assigned;assigned[n,i]=row['definitionId']
    for row in public:
        for n,indices in row['sourceCubeIndices'].items():
            for i in indices:assert (n,i) not in assigned;assigned[n,i]=row['definitionId']
    for n,indices in config['presentationOnly'].items():
        for i in indices:assert (n,i) not in assigned;assigned[n,i]='presentationOnly'
    full={(n,i) for n,b in bones.items() for i in range(len(b.get('cubes',[])))}
    assert set(assigned)==full,(full-set(assigned),set(assigned)-full)
    low=p.ex.asset(dp['lod']['model'],'geo_models','.json');lowgeo=p.ex.read(low)['minecraft:geometry'][0]
    bounds={}
    for d,names in ownership.items():
        points=[]
        for n in names:
            for c in bones[n].get('cubes',[]):points.extend(p.ex.cube_geometry(bones[n],c,bones,np.eye(4))[0].tolist())
        a=np.asarray(points);bounds[d]={'min':a.min(0).tolist(),'max':a.max(0).tolist()}
    files={index,display,source,texture,data,animation,low}|tags
    audit={'schemaVersion':1,'gunId':'tacz:ump45','high':{'path':str(source.relative_to(R)),'bones':len(bones),'cubes':len(full),'uvSize':[geo['description']['texture_width'],geo['description']['texture_height']]},
      'low':{'path':str(low.relative_to(R)),'bones':len(lowgeo['bones']),'cubes':sum(len(b.get('cubes',[])) for b in lowgeo['bones']),'policy':config['lodPolicy']},
      'parts':[dict(row,cubes=sum(len(v) for v in row['sourceCubeIndices'].values()),bounds=bounds[row['definitionId']],motionOwnership={n:[v for v in p.ex.ancestors(n,bones) if v in active] for n in row['sourceCubeIndices']}) for row in parts],
      'publicMagazineVariants':[{k:row[k] for k in ['definitionId','attachmentId','capacity','sourceCubeIndices','sourceGeometry','sourceSha256']} for row in public],
      'presentationOnly':config['presentationOnly'],'unassignedCubes':[],'duplicateOwnership':[],
      'allBones':[{k:v for k,v in b.items() if k!='cubes'}|{'cubeCount':len(b.get('cubes',[]))} for b in geo['bones']],
      'animations':{n:list(a.get('bones',{})) for n,a in anim['animations'].items()},
      'animationRefsWithoutBones':sorted(active-set(bones)),
      'attachments':sorted(allowed),'tagSources':[str(f.relative_to(R)) for f in sorted(tags)],'sourceHashes':{str(f.relative_to(R)):p.ex.sha(f) for f in sorted(files)},
      'reload':{'capacity':[25,32,40,48],'nativeData':idx['data'],'nativeAnimation':dp['animation'],'additionalMagazineBonePresent':'additional_magazine' in bones,'bulletAnimation':{n:a['bones']['bullet'] for n,a in anim['animations'].items() if 'bullet' in a.get('bones',{})}},
      'sights':{'policy':'Integrated factory mechanical sights remain in upper receiver; optical models are read-only existing candidates, no optical authoring',
         'geometricEvidence':{'front':{'group3':list(range(54,71)),'sight_illuminated':[0]},'rear':{'group3':list(range(24,54)),'sight_illuminated':[1,2]}},
         'reasonNotSeparate':'group3 contains protective ears and bases continuous with receiver; geometry ranges are not proof of separable replacement parts',
         'nativeOpticBehavior':'No sight/sight_folded functional nodes. Preserve fixed original iron geometry with installed scope.'},
      'stock':{'nativeAncestor':'ump45_stock_extended','sourceNodes':ownership['ump_stock'],'animatedNodes':sorted(set(ownership['ump_stock']+['ump45_stock_extended'])&active),'policy':'Keep native extended pose and hinge tree. No invented folding or external stock compatibility.'},
      'mounts':{n:bones[n] for n in ['muzzle_pos','laser_pos','grip_pos','scope_pos','muzzle_flash','shell','iron_view']},
      'constraints':['No synthetic split of receiver-integrated iron sights. Sight authoring remains deferred.','Rail set is one assembly, not individual decorative screws.','Public UMP-specific light magazine authoring sources are reused unchanged.','Laser native Z rotation is +90 degrees. Preserve original mount and do not apply twice.','bullet_in_barrel follows native moving bolt; physical barrel presence plus the bolt ancestor gate require both parts without replacing ammo/animation logic.']}
    return config,audit

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--append-editable',action='store_true');args=parser.parse_args()
    config,audit=create();p.write(ROOT/'production.json',config);p.write(ROOT/'source-audit.json',audit)
    if args.append_editable:p.append(config,ROOT)
    print('UMP45 audited:',audit['high'],'default physical parts:',len(config['parts']))
