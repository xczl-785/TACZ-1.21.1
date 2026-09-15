"""Read-only ammo-effect/LOD evidence, with exact cube dispositions and resource hashes."""
from pathlib import Path
import json,re,hashlib,subprocess,collections
O=Path(__file__).resolve().parent;R=O.parents[2];P=R/'src/main/resources/assets/tacz/custom/tacz_default_gun';A=P/'assets/tacz';D=P/'data/tacz';hashes={}
def read(p):
 hashes[str(p.relative_to(R))]=hashlib.sha256(p.read_bytes()).hexdigest()
 return json.loads(re.sub(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*[\s\S]*?\*/',lambda m:m[0] if m[0].startswith('"') else '',p.read_text()))
g=read(D/'data/guns/m4a1_data.json');ammo=read(R/'ammunition/generated/src/main/resources/data/tarkov_content/catalog/ammunition.json');m855=next(x for x in ammo if x['sourceId']=='54527a984bdc2d4e668b4567')
effects={}
for name in ['ammo_mod_fmj','ammo_mod_hp','ammo_mod_i']:
 data=read(D/f'data/attachments/{name}_data.json');display=read(A/f'display/attachments/{name}_display.json')
 effects[name]={'data':data,'display':display,'m4a1Capacity':g['extended_mag_ammo_amount'][data['extended_mag_level']-1],'isolatedNativeCacheExample':{'weight':g['weight']+data['weight'],'ads':g['aim_time']+data['ads']['addend'],'damageAtNearRange':round(6.5*data['damage']['multiplier'],6),'armorIgnore':.35 if name=='ammo_mod_fmj' else .2*data['armor_ignore']['multiplier'],'pierceAfterClamp':1},'validM855Snapshot':{k:m855[k] for k in ['id','fleshDamage','penetrationPower','armorDamage','initialSpeed','projectileCount']}}
assert len(ammo)==86 and all(v['m4a1Capacity']==60 for v in effects.values())
hi=read(A/'geo_models/gun/m4a1_geo.json')['minecraft:geometry'][0]['bones'];lo=read(A/'geo_models/gun/lod/m4a1.json')['minecraft:geometry'][0]['bones'];by={b['name']:b for b in lo}
reuse={'ld_upper':'upper_receiver','ld_toprails':'upper_receiver','ld_portgroup':'upper_receiver','ld_magwell':'lower_receiver','ld_lower':'lower_receiver','ld_switch':'lower_receiver','ld_trigger':'lower_receiver','ld_grip':'pistol_grip','ld_buffer':'buffer','sight':'rear_sight','ld_pull':'charging_mechanism','ld_mag':'magazine_standard'}
regenerate={'ld_handguard':'5 mix collar/handguard and lack two explicit types','ld_foresight':'10 mix gas block and upright front sight; no folded variant','ld_barrel':'3 include long cube crossing barrel/muzzle; regenerate separated surfaces','ld_stock':'10 baked unrelated stock; use actual installed native attachment representation'}
rows=[]
for b in lo:
 for i,c in enumerate(b.get('cubes',[])):
  assert b['name'] in reuse or b['name'] in regenerate
  rows.append({'bone':b['name'],'cubeIndex':i,'disposition':'candidate_reuse_after_binding' if b['name'] in reuse else 'replace_in_derived_lod','owner':reuse.get(b['name']),'reason':regenerate.get(b['name']),'cubeSha256':hashlib.sha256(json.dumps(c,sort_keys=True).encode()).hexdigest()})
assert len(rows)==75 and len({(r['bone'],r['cubeIndex']) for r in rows})==75
counts=collections.Counter(r['disposition'] for r in rows);assert counts['candidate_reuse_after_binding']==47
anchors=['root','muzzle_pos','muzzle_flash','scope_pos','stock_pos','grip_pos','laser_pos','shell','magazine','additional_magazine','sight','sight_folded','handguard_default','handguard_tactical','iron_view','thirdperson_hand','ground','fixed']
anims=read(A/'animations/m4a1.animation.json')['animations']
refs=[
'src/main/java/com/tacz/guns/item/ModernKineticGunScriptAPI.java','src/main/java/com/tacz/guns/entity/EntityKineticBullet.java','src/main/java/com/tacz/guns/util/AttachmentDataUtils.java','src/main/java/com/tacz/guns/resource/modifier/AttachmentPropertyManager.java','src/main/java/com/tacz/guns/resource/modifier/custom/WeightModifier.java','src/main/java/com/tacz/guns/resource/modifier/custom/AdsModifier.java','src/main/java/com/tacz/guns/resource/modifier/custom/PierceModifier.java','src/main/java/com/tacz/guns/resource/modifier/custom/DamageModifier.java','src/main/java/com/tacz/guns/resource/modifier/custom/IgniteModifier.java','src/main/java/com/tacz/guns/client/model/BedrockGunModel.java','src/main/java/com/tacz/guns/client/model/bedrock/BedrockPart.java','src/main/java/com/tacz/guns/client/model/functional/AttachmentRender.java','src/main/java/com/tacz/guns/client/renderer/item/GunItemRendererWrapper.java','src/main/java/com/tacz/guns/mixin/client/ar/BedrockPartMixin.java','src/main/java/com/tacz/guns/compat/ar/ARCompat.java','modules/tacz_adapter/src/main/java/dev/tacticaltacz/AmmoBridge.java','modules/tacz_adapter/src/main/java/dev/tacticaltacz/TacticalTaczAdapter.java']
for rel in refs:hashes[rel]=hashlib.sha256((R/rel).read_bytes()).hexdigest()
combat=R.parent/'NewMod/source/mods/combat/src/main/java/dev/tacticalcombat/player/PlayerCombat.java'
result={'date':'2026-09-16','baseline':subprocess.check_output(['git','rev-parse','HEAD'],cwd=R).decode().strip(),'effects':effects,'lod':{'highBones':len(hi),'lowBones':len(lo),'lowCubes':75,'dispositions':dict(counts),'cubeLedger':rows,'missingFunctionalNodes':[n for n in anchors if n not in by],'animationTargetsAbsent':sorted({n for v in anims.values() for n in v.get('bones',{}) if n not in by}),'sourceBoneCounts':{b['name']:len(b.get('cubes',[])) for b in lo}},'sourceHashes':hashes,'combatSourceHash':hashlib.sha256(combat.read_bytes()).hexdigest(),'limits':['Candidate reuse is semantic partition evidence, not converted or runtime-validated LOD','Cache calculations assume no other modifiers, damage base multiplier 1, near-range native base 6.5','No production source/model/assets changed; no game or editor launched']}
(O/'supplement-evidence.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print('PASS: 3 effect definitions, 86 rounds, 75 LOD cubes accounted (47 candidate / 28 replace)')
