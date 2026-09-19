#!/usr/bin/env python3
"""V6 source boundary checks for the TaCZ-owned platform extension route."""
import unittest
from pathlib import Path

R=Path(__file__).resolve().parents[1]

class PlatformExtensionBoundary(unittest.TestCase):
 def test_core_has_no_newmod_reverse_dependencies(self):
  forbidden=('dev.tacticaltacz','dev.tacticalcombat','dev.tacticalinventory','dev.tacticalcharacter','dev.firearms','dev.weaponruntime')
  violations=[]
  for source in (R/'src/main/java').rglob('*.java'):
   text=source.read_text()
   violations += [(source.relative_to(R),prefix) for prefix in forbidden if prefix in text]
  self.assertEqual([],violations)

 def test_core_routes_through_tacz_owned_extensions(self):
  gun=(R/'src/main/java/com/tacz/guns/GunMod.java').read_text()
  bullet=(R/'src/main/java/com/tacz/guns/entity/EntityKineticBullet.java').read_text()
  ammo=(R/'src/main/java/com/tacz/guns/ammunition/TarkovAmmoItem.java').read_text()
  self.assertEqual(1,gun.count('GunPlatformExtensions.register(bus);'))
  for method in ('initializeProjectile','quoteImpact','applyImpact','scaleDamage','initializeContinuation'):
   self.assertIn('.'+method+'(',bullet)
  self.assertIn('.matchesAmmunition(gun, ammo)',ammo)

 def test_adapter_owns_platform_specific_behavior(self):
  provider=(R/'modules/tacz_adapter/src/main/java/dev/tacticaltacz/TacticalGunPlatformExtension.java').read_text()
  client=(R/'modules/tacz_adapter/src/main/java/dev/tacticaltacz/TacticalGunClientExtension.java').read_text()
  for marker in ('AmmoBridge.','AssemblyFireGate.','BulletImpactEvent','BulletTraceEvent','FirearmBallistics.profile','PlayerResources.canAim'):
   self.assertIn(marker,provider)
  for marker in ('AssemblyPresentationClient.','NativeAssemblyIcons.','ClientCharacterDisplay.resources()'):
   self.assertIn(marker,client)

 def test_single_service_provider_and_retired_bridges(self):
  services=R/'modules/tacz_adapter/src/main/resources/META-INF/services'
  self.assertEqual(['dev.tacticaltacz.TacticalGunPlatformExtension'],
   (services/'com.tacz.guns.api.extension.GunPlatformExtension').read_text().splitlines())
  self.assertEqual(['dev.tacticaltacz.TacticalGunClientExtension'],
   (services/'com.tacz.guns.api.extension.GunClientExtension').read_text().splitlines())
  for retired in ('ImpactCarrier.java','ContinuationState.java'):
   self.assertFalse((R/'modules/tacz_adapter/src/main/java/dev/tacticaltacz'/retired).exists())

if __name__=='__main__':unittest.main()
