#!/usr/bin/env python3
"""Compare moved combat method bodies and native event flow against pinned predecessors.
Source equivalence complements unit tests; it is not a Minecraft runtime test.
"""
import json,re,subprocess,unittest
from pathlib import Path
R=Path(__file__).resolve().parents[1];N=R.parent/'NewMod'
ledger=json.loads((R/'docs/assembly-experiment/adapter-migration.json').read_text())
def old_mixin(name):return subprocess.check_output(['git','show',ledger['newmod_baseline']+':source/mods/tacz_adapter/src/main/java/dev/tacticaltacz/mixin/'+name+'.java'],cwd=N).decode()
def body(s,sig):
 start=s.index('{',s.index(sig));masked=re.sub(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*[\s\S]*?\*/',lambda m:' '*len(m[0]),s)
 d=1;i=start+1
 while d:d+=(masked[i]=='{')-(masked[i]=='}');i+=1
 return s[start+1:i-1]
def normalized(s):return re.sub(r'\s+','',re.sub(r'//[^\n]*|/\*[\s\S]*?\*/','',s))
class HookEquivalence(unittest.TestCase):
 def test_combat_commit_and_continuation_bodies(self):
  old=old_mixin('BulletMixin');new=(R/'src/main/java/com/tacz/guns/entity/EntityKineticBullet.java').read_text()
  for method in ['private void adapter$apply(', 'public void tacticalInitializeContinuation(', 'private void adapter$continue(']:
   self.assertEqual(normalized(body(old,method).replace('(EntityKineticBullet) (Object) this','this')),normalized(body(new,method)))
 def test_native_hit_order_preserved_except_redirect(self):
  p='src/main/java/com/tacz/guns/entity/EntityKineticBullet.java'
  old=subprocess.check_output(['git','show',ledger['experiment_baseline']+':'+p],cwd=R).decode();new=(R/p).read_text()
  expected=body(old,'protected void onHitEntity(').replace('tacAttackEntity(parts, damage, sources);','adapter$apply(this, parts, damage, sources);')
  self.assertEqual(normalized(expected),normalized(body(new,'private void onHitEntityNative(')))
  self.assertEqual(normalized(body(new,'protected void onHitEntity(')),normalized('if (adapter$quote(result,startVec,endVec)) return; onHitEntityNative(result,startVec,endVec); adapter$impact=null;'))
 def test_precise_refund_and_reload_body(self):
  old=old_mixin('ReloadMixin');new=(R/'src/main/java/com/tacz/guns/api/item/gun/AbstractGunItem.java').read_text()
  expected=body(old,'private void adapter$returnRealVariant(').replace('        if (!AmmoBridge.managed(gun)) return;\n','').replace('        ci.cancel();\n','')
  expected=re.sub(r'\bgun\b','gunItem',expected)
  actual=body(body(new,'public void dropAllAmmo('),'if (AmmoBridge.managed(gunItem))')
  self.assertEqual(normalized(expected+'return;'),normalized(actual))
 def test_ammo_identity_methods(self):
  old=old_mixin('ContentAmmoMixin');new=(R/'src/main/java/com/tacz/guns/ammunition/TarkovAmmoItem.java').read_text()
  for sig in ['public ResourceLocation getAmmoId(', 'public void setAmmoId(', 'public boolean isAmmoOfGun(']:self.assertEqual(normalized(body(old,sig)),normalized(body(new,sig)))
if __name__=='__main__':unittest.main()
