"""Refresh only the reviewed item-three implementation paths against its fixed baseline."""
from pathlib import Path
import hashlib,json,subprocess
R=Path(__file__).resolve().parents[2];BASE='2de081b3d16c6517a2db3da7ff2ce2886223fdfe'
allowed_names={'GunItemRendererWrapper.java','render_part_icon.py','test_render_part_icon.py','AbstractGunItem.java','WorkbenchAccess.java','WorkbenchScreen.java','AssemblyViewport.java','AssemblyMeshRenderTypes.java','GunModelTypeManager.java','GunDisplayInstance.java','ClientAimResource.java','TacticalTaczAdapter.java','AssembledWeapon.java','AssembledWeapons.java','AssemblyGunClient.java','AssemblyGunClientRegistration.java','AssemblyGunExchange.java','AssemblyGunItem.java','AssemblyGunWorkbench.java','AssemblyPresentationClient.java','RefitBridge.java','RefitClient.java','WeaponCapabilities.java','GunItemBuilder.java','LocalPlayerAim.java','LivingEntityAim.java','validate_weapon_resources.py','assembled_weapons.json','adapter-integration.gradle'}
preset_paths={
 'src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/index/attachments/muzzle_duckbill_sg.json',
 'modules/weapon_assembly_ui/src/main/java/dev/weaponassemblyui/session/AssemblySession.java',
 'modules/weapon_assembly_ui/src/test/java/dev/weaponassemblyui/session/AssemblySessionTest.java',
 'modules/weapon_assembly_ui/src/main/resources/assets/weapon_assembly_ui/lang/zh_cn.json',
 'modules/weapon_assembly_ui/src/main/resources/assets/weapon_assembly_ui/lang/en_us.json',
 'modules/tacz_adapter/src/main/java/dev/tacticaltacz/assembled/AssemblyWorkbenchLifecycle.java',
 'modules/tacz_adapter/src/test/java/dev/tacticaltacz/assembled/AssemblyWorkbenchLifecycleTest.java',
 'modules/weapon_assembly_ui/src/main/java/dev/weaponassemblyui/client/AssemblyTextureQuality.java',
 'modules/weapon_assembly_ui/src/main/java/dev/weaponassemblyui/client/WorkbenchBackdrop.java',
 'modules/weapon_assembly_ui/src/test/java/dev/weaponassemblyui/client/AssemblyTextureQualityTest.java',
 'modules/weapon_assembly_ui/src/main/java/dev/weaponassemblyui/client/WorkbenchModelBackend.java',
 'modules/weapon_assembly_ui/src/main/java/dev/weaponassemblyui/client/WorkbenchViewportFrame.java',
 'modules/weapon_assembly_ui/src/main/java/dev/weaponassemblyui/client/NativeWorkbenchTransform.java',
 'modules/weapon_assembly_ui/src/test/java/dev/weaponassemblyui/client/WorkbenchViewportFrameTest.java',
}
new_prefixes=['modules/tacz_adapter/weapon-sources/native_aa12/','modules/tacz_adapter/weapon-sources/native_ai_awp/','modules/tacz_adapter/weapon-sources/native_m700/','modules/tacz_adapter/weapon-sources/native_m870/','modules/tacz_adapter/weapon-sources/native_mk14/','modules/tacz_adapter/weapon-sources/native_p90/','modules/tacz_adapter/weapon-sources/native_qbz_191/','modules/tacz_adapter/weapon-sources/native_scar_h/','modules/tacz_adapter/weapon-sources/native_sks_tactical/','modules/tacz_adapter/weapon-sources/native_uzi/','modules/tacz_adapter/weapon-sources/native_m16a1/','modules/tacz_adapter/weapon-sources/native_scar_l/','modules/tacz_adapter/weapon-sources/native_ump45/','modules/tacz_adapter/weapon-sources/native_glock_17/','modules/tacz_adapter/weapon-sources/native_attachments/','modules/tacz_adapter/weapon-sources/native_m4a1/','modules/tacz_adapter/weapon-content/resources/assets/tacz_assembly/','modules/tacz_adapter/weapon-content/resources/data/tacz_assembly/','modules/tacz_adapter/src/main/java/dev/tacticaltacz/assembled/Native','modules/tacz_adapter/src/main/java/dev/tacticaltacz/assembled/AssemblyProposalSessions.java','modules/tacz_adapter/src/test/java/dev/tacticaltacz/assembled/Native','modules/tacz_adapter/src/minecraftTest/java/dev/tacticaltacz/assembled/Native']
paths=set(subprocess.check_output(['git','diff','--name-only','-z',BASE,'--','src','modules','build-logic'],cwd=R).decode().strip('\0').split('\0'))
paths.update(subprocess.check_output(['git','ls-files','--others','--exclude-standard','-z','--','src','modules','build-logic'],cwd=R).decode().strip('\0').split('\0'))
rows=[]
for rel in sorted(paths-{''}):
 old=subprocess.run(['git','show',BASE+':'+rel],cwd=R,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL)
 if old.returncode==0:
  assert Path(rel).name in allowed_names or rel in preset_paths,rel
  before=hashlib.sha256(old.stdout).hexdigest()
 else:
  assert any(rel.startswith(prefix) for prefix in new_prefixes) or rel in preset_paths,rel
  before=None
 rows.append({'path':rel,'before_sha256':before,'after_sha256':hashlib.sha256((R/rel).read_bytes()).hexdigest(),'reason':'Native M4A1 sample and required internal integration; original gun resources retained'})
p=R/'docs/assembly-experiment/native-m4a1-integration.json';p.write_text(json.dumps({'baseline':BASE,'newmod_baseline':'e45dd1f896adb4342781c88716c690d34a850372','contract':'整合与组装路线.md#第三项完整实施合同2026-09-16已授权','files':sorted(rows,key=lambda r:r['path'])},ensure_ascii=False,indent=2)+'\n');print('Recorded',len(rows),'exact item-three paths')
