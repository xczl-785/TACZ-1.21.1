"""Prepare authoring-only native sources for AWP, M700, M870, P90 and UZI.

This is deliberately append-only: it writes only weapon-sources/native_<gun>.
It never invokes produce.build and therefore cannot change runtime resources.
"""
import argparse, copy, json, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "native_attachments"))
import extract as shared
import magazines
sys.path.insert(0, str(Path(__file__).resolve().parent))
import produce

im = shared.im
ex = im.ex
R = ex.R

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")

def cubes(bones, names):
    return {name: list(range(len(bones[name].get("cubes", [])))) for name in names if bones[name].get("cubes")}

SPECS = {
 "ai_awp": {
  "root":"awp_receiver", "type":"weapon/firearm/bolt_action_rifle", "footprint":[6,2], "fire":"semi", "feed":"detachable_magazine",
  "owners": {
   "awp_receiver":["receiver","trigger","group13","safety"], "awp_barrel":["barrel","rail","group24","group25"],
   "awp_bipod":["octagon4","octagon3","octagon"], "awp_bolt":["bolt_rotate","bone2","ball","group","striker"],
   "awp_stock":["group4","group5","group6","group32","group31","group3","group2","stock","group29","group30","screws"],
   "awp_iron_sights":["group8","sight_illuminated","rearsight"], "awp_magazine_standard":["mag2"]},
  "slots":{"awp_receiver":{"barrel":["awp_barrel"],"bolt":["awp_bolt"],"stock":["awp_stock"],"iron_sights":["awp_iron_sights"],"magazine":["awp_magazine_standard","$extended_mag"]},"awp_barrel":{"bipod":["awp_bipod"],"muzzle":["$muzzle"],"scope":["$scope"]}},
  "required":[["barrel"],["bolt"]], "paths":{"MUZZLE":["barrel","muzzle"],"SCOPE":["barrel","scope"],"EXTENDED_MAG":["magazine"]},
  "presentation":["righthand_pos","lefthand_pos","bullet_shell","shell_part","head","bullet","bullet3","bullet_in_mag"],
  "iron":"Factory iron sights are physical native geometry. Existing scope_pos is read-only; no optical source is authored.",
 },
 "m700": {
  "root":"m700_receiver", "type":"weapon/firearm/bolt_action_rifle", "footprint":[6,2], "fire":"semi", "feed":"detachable_magazine",
  "owners":{"m700_receiver":["main_body"],"m700_barrel":["main_body"],"m700_stock":["main_body"],"m700_bolt":["striker","rotate"],"m700_iron_sights":["sight","sight_illuminated"],"m700_magazine_standard":["mag_default"]},
  "overrides":{"m700_receiver":{"main_body":list(range(58))+list(range(87,113))},"m700_barrel":{"main_body":list(range(113,177))+list(range(201,217))},"m700_stock":{"main_body":list(range(58,87))+list(range(177,201))}},
  "slots":{"m700_receiver":{"barrel":["m700_barrel"],"stock":["m700_stock"],"bolt":["m700_bolt"],"iron_sights":["m700_iron_sights"],"magazine":["m700_magazine_standard","$extended_mag"],"scope":["$scope"],"muzzle":["$muzzle"]}},
  "required":[["barrel"],["bolt"]], "paths":{"MUZZLE":["muzzle"],"SCOPE":["scope"],"EXTENDED_MAG":["magazine"]},
  "presentation":["righthand_pos","lefthand_pos","bullet_shell","head","bullet_shell2","bullet5","bullet_in_mag"],
  "iron":"Receiver body is one native low-part-count physical assembly; its fixed sight stays attached. Existing scope_pos is read-only.",
 },
 "m870": {
  "root":"m870_receiver", "type":"weapon/firearm/shotgun", "footprint":[6,2], "fire":"semi", "feed":"internal_tube",
  "owners":{"m870_receiver":["panel","trigger","safety2","body","group25","safety","screw"],"m870_barrel":["barrel"],"m870_tube":["magazine","group5","group7","group8"],"m870_iron_sights":["sight","group"],"m870_pump":["group9","slide3"],"m870_bolt":["octagon"],"m870_stock":["group21","group22","group23"]},
  "slots":{"m870_receiver":{"barrel":["m870_barrel"],"tube":["m870_tube"],"iron_sights":["m870_iron_sights"],"pump":["m870_pump"],"bolt":["m870_bolt"],"stock":["m870_stock"]},"m870_barrel":{"muzzle":["$muzzle"]},"m870_tube":{"tube_extension":["$extended_mag"]}},
  "required":[["barrel"],["pump"],["bolt"]], "paths":{"MUZZLE":["barrel","muzzle"],"EXTENDED_MAG":["tube","tube_extension"]},
  "presentation":["bullet_shell","bullet_in_barrel","bullet_in_mag","bullet","lefthand_pos","righthand_pos"],
  "iron":"M870 uses its original scripted, interruptible one-shell-at-a-time reload. No detachable-magazine slot or semantic is created.",
 },
 "p90": {
  "root":"p90_body", "type":"weapon/firearm/smg", "footprint":[4,2], "fire":"auto", "feed":"detachable_magazine",
  "owners":{"p90_body":["Body2","side_rail2","hexadecagon2","Trigger2","stock2","grip2","screws2"],"p90_builtin_sight":["group"],"p90_barrel":["barrel2","group4"],"p90_muzzle":["muzzle_default"],"p90_mag_release":["mag_release"],"p90_magazine_standard":["Mag","octagon2"],"p90_charging_handle":["pull"]},
  "slots":{"p90_body":{"iron_sights":["p90_builtin_sight"],"barrel":["p90_barrel"],"muzzle":["p90_muzzle","$muzzle"],"magazine":["p90_magazine_standard"],"mag_release":["p90_mag_release"],"charging_handle":["p90_charging_handle"],"scope":["$scope"],"laser":["$laser"]}},
  "required":[["barrel"],["charging_handle"]], "paths":{"MUZZLE":["muzzle"],"SCOPE":["scope"],"LASER":["laser"]},
  "presentation":["righthand_pos","lefthand_pos"],
  "iron":"The built-in P90 optic remains an existing read-only native candidate; no new optical BBModel/PNG is produced.",
 },
 "uzi": {
  "root":"uzi_receiver", "type":"weapon/firearm/smg", "footprint":[4,2], "fire":"auto", "feed":"detachable_magazine",
  "owners":{"uzi_receiver":["mount","cap","lower","group3","rec","group4","group5","group6","group7","octagon2","deco","group8","screw","octagon"],"uzi_iron_sights":["rearsight","sight_illuminated"],"uzi_stock":["fold_stock","group12","group14","group"],"uzi_barrel":["barrel","group2"],"uzi_handguard":["guard","hg"],"uzi_grip":["grip"],"uzi_mag_release":["mag_release"],"uzi_charging_handle":["pull"],"uzi_bolt":["bolt"],"uzi_magazine_standard":["mag"]},
  "slots":{"uzi_receiver":{"iron_sights":["uzi_iron_sights"],"stock":["uzi_stock"],"barrel":["uzi_barrel"],"handguard":["uzi_handguard"],"grip":["uzi_grip"],"magazine":["uzi_magazine_standard","$extended_mag"],"mag_release":["uzi_mag_release"],"charging_handle":["uzi_charging_handle"],"bolt":["uzi_bolt"],"muzzle":["$muzzle"],"scope":["$scope"]}},
  "required":[["barrel"],["charging_handle"],["bolt"]], "paths":{"MUZZLE":["muzzle"],"SCOPE":["scope"],"EXTENDED_MAG":["magazine"]},
  "presentation":["righthand_pos","lefthand_pos","bullet","bullet_in_mag"],
  "iron":"Receiver-integrated rear sight and folding stock remain native geometry. Existing scope_pos is read-only.",
 },
}

def make(gun, refresh=False):
    spec=SPECS[gun]; root=R/'modules/tacz_adapter/weapon-sources'/('native_'+gun)
    if root.exists() and not refresh: raise ValueError('Refuse to overwrite existing source directory '+str(root))
    _,idx,display,dp,source,texture,data_path=produce.inputs({'sourceGun':gun})
    geo=ex.read(source)['minecraft:geometry'][0]; bones={b['name']:b for b in geo['bones']}; data=ex.read(data_path)
    owners={d:cubes(bones,names) for d,names in spec['owners'].items()}
    owners.update(spec.get('overrides',{}))
    parts=[]
    for definition,indices in owners.items():
        if definition==spec['root']: kind,size=spec['type'],spec['footprint']
        elif 'magazine' in definition or 'tube' in definition: kind,size='weapon_mod/magazine',[1,2]
        elif 'barrel' in definition: kind,size='weapon_mod/barrel',[3,1]
        elif 'stock' in definition: kind,size='weapon_mod/stock',[2,2]
        elif 'grip' in definition: kind,size='weapon_mod/pistol_grip',[1,1]
        elif 'handguard' in definition or 'pump' in definition: kind,size='weapon_mod/handguard',[2,1]
        elif 'iron' in definition: kind,size='weapon_mod/sight/iron',[1,1]
        elif 'bolt' in definition: kind,size='weapon_mod/receiver',[1,1]
        elif 'bipod' in definition: kind,size='weapon_mod/bipod',[2,1]
        elif 'muzzle' in definition: kind,size='weapon_mod/muzzle',[1,1]
        elif 'charging' in definition: kind,size='weapon_mod/charging_handle',[1,1]
        else: kind,size='weapon_mod/receiver',[2,1]
        parts.append({'definitionId':definition,'sourceCubeIndices':indices,'inventoryType':kind,'footprint':size})
    presentation=cubes(bones,spec['presentation'])
    variants={}
    tube_extensions={}
    if spec['feed']=='internal_tube':
        for i in (1,2,3): tube_extensions['m870_tube_extension_'+str(i)]=cubes(bones,['mag_extended_'+str(i)])
    else:
        for i in (1,2,3):
            n='mag_extended_'+str(i)
            if n in bones:
                descendants={n}; changed=True
                while changed:
                    changed=False
                    for b in bones.values():
                        if b.get('parent') in descendants and b['name'] not in descendants:
                            descendants.add(b['name']); changed=True
                variants['tacz_'+gun+'_'+('sniper' if gun in ('ai_awp','m700') else 'light')+'_extended_mag_'+str(i)]=cubes(bones,sorted(descendants))
    assigned={}
    for role, mapping in list(owners.items())+list(variants.items())+list(tube_extensions.items())+[("presentation",presentation)]:
        for name,ids in mapping.items():
            for i in ids:
                if (name,i) in assigned: raise ValueError((gun,'duplicate',name,i,assigned[(name,i)],role))
                assigned[(name,i)]=role
    expected={(name,i) for name,b in bones.items() for i in range(len(b.get('cubes',[])))}
    if expected != set(assigned): raise ValueError((gun,'unassigned',sorted(expected-set(assigned))))
    slots={k:{s:[v if v.startswith('$') else v for v in vals] for s,vals in row.items()} for k,row in spec['slots'].items()}
    preset={k:{s:vs[0] for s,vs in row.items() if not vs[0].startswith('$')} for k,row in slots.items()}; preset={k:v for k,v in preset.items() if v}
    config={'schemaVersion':1,'sourceGun':gun,'gunId':'tacz_fork_tarkov:'+gun,'sourceDirectory':'native_'+gun,'rootDefinition':spec['root'],'parts':parts,'slots':slots,'preset':preset,
      'weapon':{'schemaVersion':1,'gunId':'tacz_fork_tarkov:'+gun,'rootDefinition':spec['root'],'resourceDirectory':gun,'modelType':'tacz_native_'+gun+'_assembly','caliber':None,'requiredPaths':spec['required'],'defaultFireMode':spec['fire'],'feed':spec['feed'],'nativeRig':True,'assemblyIcons':True,'authoringSource':'native_'+gun,'developmentCategory':'tacz_fork_tarkov','wearableSlots':['tactical_inventory:primary_weapon_1','tactical_inventory:primary_weapon_2'],'partIconDirectory':'textures/item/'+gun},
      'nativeProfile':{'schemaVersion':1,'attachmentPaths':spec['paths'],'attachmentOverrides':{},'sightAlternatives':[[spec['paths']['SCOPE']]] if 'SCOPE' in spec['paths'] else []},
      'visualRules':{'schemaVersion':1,'alwaysVisibleBones':['mag_standard','mag_extended_1','mag_extended_2','mag_extended_3'], 'definitionRequirements':{},'variantRequirements':{},'boneRequirements':{}},'presentationOnly':presentation,
      'lodPolicy':'conservative_cube_subset: retain the complete authored native high rig until a reviewed component-preserving low rig exists; never reuse incompatible original low topology.','lod':{'strategy':'conservative_cube_subset','maxSilhouetteLoss':0.01,'maxTextureMeanError':0.02,'maxTextureChangedFraction':0.05},
      'labels':{p['definitionId']:[{'barrel':'枪管','stock':'枪托','grip':'握把','handguard':'护木','pump':'泵动护木','iron':'机械瞄具','magazine':'弹匣','tube':'储弹管','bolt':'枪机','muzzle':'枪口','charging':'拉机柄','bipod':'两脚架'}.get(next((k for k in ('barrel','stock','grip','handguard','pump','iron','magazine','tube','bolt','muzzle','charging','bipod') if k in p['definitionId']),'receiver'),'机匣')+' · '+gun.upper(),p['definitionId']] for p in parts},'slotLabels':({'mag_release':['弹匣卡笋','Magazine release']} if gun in ('p90','uzi') else {}),'gunLabels':[gun.upper()+' · 原生实体组装',gun.upper()+' · Native Assembly'],
      'editableAttachmentCatalogs':['modules/tacz_adapter/weapon-sources/native_attachments/editable/manifest.json','modules/tacz_adapter/weapon-sources/native_m4a1/editable/manifest.json'],'attachmentOverrideCatalogs':['data/tacz_fork_tarkov/native_attachments/standalone.json','data/tacz_fork_tarkov/m4a1/native_attachment_overrides.json'],'integrationFragments':'/tmp/native-gun-integration/'+gun}
    # Caliber is deliberately read from GunAdoption rather than inferred from ammo strings.
    adoption=(R/'modules/tacz_adapter/src/main/java/dev/tacticaltacz/GunAdoption.java').read_text()
    import re
    config['weapon']['caliber']=next(cal for cal, guns in re.findall(r'add\(map,"([^"]+)",(.*?)\);',adoption) if gun in re.findall(r'"([^"]+)"',guns))
    config['visualRules']['alwaysVisibleBones']=[name for name in config['visualRules']['alwaysVisibleBones'] if name in bones]
    config['excludedAttachments']=sorted(a for a in magazines.allowed(gun)[0] if a.startswith('tacz:ammo_mod_'))
    if spec['feed']=='detachable_magazine': config['weapon']['magazinePath']=['magazine']
    physical_sight={'ai_awp':['iron_sights'],'m700':['iron_sights'],'m870':['iron_sights'],'p90':['iron_sights'],'uzi':['iron_sights']}[gun]
    config['nativeProfile']['sightAlternatives']=([config['nativeProfile']['attachmentPaths']['SCOPE']],[physical_sight]) if 'SCOPE' in config['nativeProfile']['attachmentPaths'] else ([physical_sight],)
    if gun=='p90': config['scopeExteriorRoots']={'tacz:sight_p90':['default_sight']}
    mags=[d['definitionId'] for d in parts if 'magazine' in d['definitionId']]
    if spec['feed']=='internal_tube': mags.append('m870_tube')
    mags += list(variants) + list(tube_extensions)
    config['visualRules']['boneRequirements']={
      **({'bullet':mags,'bullet_in_mag':mags} if any(n in bones for n in ('bullet','bullet_in_mag')) else {}),
      **({'bullet_in_barrel':[d['definitionId'] for d in parts if 'barrel' in d['definitionId'] or 'bolt' in d['definitionId']]} if 'bullet_in_barrel' in bones else {}),
      **({'bolt':[d['definitionId'] for d in parts if 'bolt' in d['definitionId']]} if 'bolt' in bones else {}),
      **({'pull':[d['definitionId'] for d in parts if 'charging' in d['definitionId']]} if 'pull' in bones else {}),
      **({'slide2':[d['definitionId'] for d in parts if 'pump' in d['definitionId']]} if 'slide2' in bones else {})}
    # These moving chamber rounds require both independent physical branches.
    # boneRequirements is OR; the runtime reads boneAllRequirements as AND.
    if gun in ('ai_awp','m870'):
        required=[d['definitionId'] for d in parts if 'barrel' in d['definitionId'] or 'bolt' in d['definitionId']]
        config['visualRules']['boneRequirements'].pop('bullet_in_barrel',None)
        config['visualRules']['boneAllRequirements']={'bullet_in_barrel':required}
    if spec['feed']=='internal_tube':
        config['weapon'].update(feedPath=['tube'],capacityPaths=[['tube','tube_extension']],reloadPolicy='preserve_native_scripted_incremental_reload')
        config['nativeTubeVariants']={'defaultDefinition':'m870_tube','capacity':[data['ammo_amount']]+data['extended_mag_ammo_amount'],'variants':tube_extensions,'semantic':'tube_extension_not_detachable_magazine'}
        config['editableAttachmentCatalogs'].append('modules/tacz_adapter/weapon-sources/native_attachments/supplemental/manifest.json')
        config['attachmentOverrideCatalogs'].append('data/tacz_fork_tarkov/native_attachments/supplemental.json')
        config['supplementalAttachmentLibraries']=[{'source':'modules/tacz_adapter/weapon-sources/native_attachments/supplemental','catalog':'data/tacz_fork_tarkov/native_attachments/supplemental.json'}]
    elif variants:
        config['nativeMagazineVariants']=variants
    # produce.append is the shared authoring conversion; it only writes this native_<gun>/editable directory.
    write(root/'production.json',config)
    if refresh:
        # Regenerate only this script's previously registered authoring files.
        # Runtime resources and source geometry remain read-only.
        manifest={'schemaVersion':1,'parts':[]}
        for definition in config['parts']:
            selected=copy.deepcopy(geo)
            for bone in selected['bones']:
                original=bone.pop('cubes',[]); ids=definition['sourceCubeIndices'].get(bone['name'],[])
                if ids: bone['cubes']=[original[i] for i in ids]
            row,model=shared.encode(selected,texture,definition['definitionId'])
            row.update(sourceGeometry=str(source.relative_to(R)),sourceTexture=str(texture.relative_to(R)),sourceSha256=ex.sha(source),sourceTextureSha256=ex.sha(texture),sourceCubeIndices=definition['sourceCubeIndices'],runtimeMode='native_gun_bone_part')
            write(root/'editable'/row['model'],model); (root/'editable'/row['texture']).write_bytes(texture.read_bytes())
            row['baselineModelSha256']=ex.sha(root/'editable'/row['model']); manifest['parts'].append(row)
        write(root/'editable/manifest.json',manifest)
    else:
        produce.append(config,root)
    # Each detachable capacity item gets its own Blockbench/PNG source. M870 is
    # deliberately authored separately as a tube extension, not a magazine.
    catalog_root=root/('tube_extensions' if spec['feed']=='internal_tube' else 'magazine_variants')
    catalog={'schemaVersion':1,'parts':[]}
    if spec['feed']=='internal_tube':
        for definition,indices in tube_extensions.items():
            selected=copy.deepcopy(geo)
            selected_names=set(indices)
            for name in list(selected_names): selected_names.update(ex.ancestors(name,bones))
            selected['bones']=[copy.deepcopy(b) for b in geo['bones'] if b['name'] in selected_names]
            for b in selected['bones']:
                if b['name'] not in indices: b.pop('cubes',None)
                else:
                    old=b.get('cubes',[]); b['cubes']=[old[i] for i in indices[b['name']]]
            row,model=shared.encode(selected,texture,definition)
            row.update(definitionId=definition,gunId='tacz:m870',attachmentId='tacz:shotgun_extended_mag_'+definition[-1],sourceCubeIndices=indices,sourceGeometry=str(source.relative_to(R)),sourceTexture=str(texture.relative_to(R)),sourceSha256=ex.sha(source),sourceTextureSha256=ex.sha(texture),runtimeMode='native_tube_extension',semantic='tube extension, not detachable magazine',capacity=data['extended_mag_ammo_amount'][int(definition[-1])-1])
            write(catalog_root/row['model'],model); (catalog_root/row['texture']).write_bytes(texture.read_bytes()); row['baselineModelSha256']=ex.sha(catalog_root/row['model']); catalog['parts'].append(row)
        config['nativeMagazineCatalogs']=[str((catalog_root/'manifest.json').relative_to(R))]
    elif variants:
        allowed,tags=magazines.allowed(gun)
        for definition in variants:
            aid='tacz:'+definition.removeprefix('tacz_'+gun+'_')
            if aid not in allowed: raise ValueError((gun,'unapproved capacity item',aid))
            row,model,tex=magazines.extract(ex.SRC/'data/tacz/index/guns'/f'{gun}.json',aid,tags)
            write(catalog_root/row['model'],model); (catalog_root/row['texture']).write_bytes(tex.read_bytes())
            for extra in row['auxiliaryTextures']:(catalog_root/extra['file']).write_bytes((R/extra['source']).read_bytes())
            row['baselineModelSha256']=ex.sha(catalog_root/row['model']); catalog['parts'].append(row)
        config['nativeMagazineCatalogs']=[str((catalog_root/'manifest.json').relative_to(R))]
    write(catalog_root/'manifest.json',catalog)
    write(root/'production.json',config)
    anim=ex.read(ex.asset(dp['animation'],'animations','.animation.json'))['animations']; active={n for a in anim.values() for n in a.get('bones',{})}
    audit={'schemaVersion':1,'gunId':'tacz:'+gun,'high':{'path':str(source.relative_to(R)),'bones':len(bones),'cubes':len(expected),'uvSize':[geo['description']['texture_width'],geo['description']['texture_height']]},
      'low':{'path':str(ex.asset(dp['lod']['model'],'geo_models','.json').relative_to(R)) if dp.get('lod') else None,'policy':config['lodPolicy']},'parts':[dict(p,cubes=sum(map(len,p['sourceCubeIndices'].values()))) for p in parts],
      'nativeMagazineVariants':variants,'nativeTubeVariants':config.get('nativeTubeVariants'),'presentationOnly':presentation,'unassignedCubes':[],'duplicateOwnership':[],
      'animations':{n:list(a.get('bones',{})) for n,a in anim.items()},'animationRefsWithoutBones':sorted(active-set(bones)),'reload':{'capacity':[data.get('ammo_amount')]+data.get('extended_mag_ammo_amount',[]),'nativeData':idx['data'],'nativeAnimation':dp['animation'],'feed':spec['feed']},
      'sights':{'policy':spec['iron']},'defaultScene':({'installed':sorted({config['rootDefinition'],*(v for row in config['preset'].values() for v in row.values())}),'magazineRelease':'Independent physical component is installed through the default mag_release slot, not presentation-only.'} if gun in ('p90','uzi') else None),'physicalSplitEvidence':({'receiverMainBodyIndices':list(range(58))+list(range(87,113)),'barrelMainBodyIndices':list(range(113,177))+list(range(201,217)),'stockMainBodyIndices':list(range(58,87))+list(range(177,201)),'basis':'Blockbench temporary rendered review: receiver is the long handguard body; barrel is long tube plus its separate upper rail segment; stock is the rear buttstock/grip shell. The three connected spatial regions were reviewed, not assigned by contiguous index ranges.'} if gun=='m700' else None),'constraints':['Every source cube is assigned once to a physical definition, a gun-specific capacity variant, or presentation-only pose/ammo geometry.','No optical source was created.','No runtime resource, global index, or language file is written by this authoring script.']}
    write(root/'source-audit.json',audit)
    return {'gun':gun,'parts':len(parts),'cubes':len(expected),'source':str(root.relative_to(R)),'feed':spec['feed']}

if __name__=='__main__':
    # A completed directory is immutable authoring evidence; retries only fill
    # untouched guns and never replace a user's edit.
    args=argparse.ArgumentParser();args.add_argument('--refresh',action='store_true');args=args.parse_args()
    pending=list(SPECS) if args.refresh else [g for g in SPECS if not (R/'modules/tacz_adapter/weapon-sources'/('native_'+g)).exists()]
    print(json.dumps([make(g,args.refresh) for g in pending],ensure_ascii=False,indent=2))
