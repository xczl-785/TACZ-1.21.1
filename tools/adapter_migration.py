"""Exact second-stage successors; historical migration ledgers stay immutable."""
import hashlib,json,subprocess
from functools import lru_cache
from pathlib import Path
from native_m4a1_migration import native_successor
ROOT=Path(__file__).resolve().parents[1]
ALLOWED={
 'GunMod.java','AbstractGunItem.java','TarkovAmmoItem.java','ModernKineticGunScriptAPI.java',
 'LivingEntityAim.java','LivingEntityShoot.java','LocalPlayerAim.java','LocalPlayerShoot.java',
 'GunHudOverlay.java','FirstPersonRenderGunEvent.java','CameraSetupEvent.java','EntityUtil.java',
 'EntityKineticBullet.java','neoforge.mods.toml','bypasses_armor.json'}
@lru_cache(maxsize=1)
def adapter_rows():
 data=json.loads((ROOT/'docs/assembly-experiment/adapter-source-integration.json').read_text())
 rows={r['path']:r for r in data['files']}
 assert len(rows)==len(ALLOWED) and {Path(p).name for p in rows}==ALLOWED
 for p,r in rows.items():
  old=subprocess.check_output(['git','show',data['baseline']+':'+p],cwd=ROOT)
  assert hashlib.sha256(old).hexdigest()==r['before_sha256'],p
  current=ROOT/p
  actual=hashlib.sha256(current.read_bytes()).hexdigest() if current.is_file() else None
  assert actual==native_successor(p,r['after_sha256']),p
 return rows

def adapter_successor(path,expected):
 row=adapter_rows().get(path)
 if row:
  assert row['before_sha256']==expected,(path,'second-stage predecessor mismatch')
  return native_successor(path,row['after_sha256'])
 return native_successor(path,expected)
