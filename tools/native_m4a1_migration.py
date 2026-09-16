"""Exact item-three successors. Never weakens protection of native gun resources."""
from pathlib import Path
from functools import lru_cache
import hashlib,json,subprocess
ROOT=Path(__file__).resolve().parents[1]
LEDGER=ROOT/'docs/assembly-experiment/native-m4a1-integration.json'
ROOT_EDITS={'src/main/java/com/tacz/guns/client/renderer/item/GunItemRendererWrapper.java','src/main/java/com/tacz/guns/api/item/gun/AbstractGunItem.java','src/main/java/com/tacz/guns/api/client/other/GunModelTypeManager.java','src/main/java/com/tacz/guns/client/resource/GunDisplayInstance.java','src/main/java/com/tacz/guns/api/item/builder/GunItemBuilder.java','src/main/java/com/tacz/guns/client/gameplay/LocalPlayerAim.java','src/main/java/com/tacz/guns/entity/shooter/LivingEntityAim.java'}
# Original gun art/data remain immutable; this missing index restores an existing allowed attachment.
SUPPLEMENTAL_INDEX='src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/index/attachments/muzzle_duckbill_sg.json'
ROOT_EDITS.add(SUPPLEMENTAL_INDEX)
@lru_cache(maxsize=1)
def native_rows():
 data=json.loads(LEDGER.read_text());rows={r['path']:r for r in data['files']}
 assert {p for p in rows if p.startswith('src/')}==ROOT_EDITS
 assert rows[SUPPLEMENTAL_INDEX]['before_sha256'] is None, 'Supplemental index must be an addition, never an original resource overwrite'
 actual=set(subprocess.check_output(['git','diff','--name-only','-z',data['baseline'],'--','src','modules','build-logic'],cwd=ROOT).decode().strip('\0').split('\0'))
 actual.update(subprocess.check_output(['git','ls-files','--others','--exclude-standard','-z','--','src','modules','build-logic'],cwd=ROOT).decode().strip('\0').split('\0'))
 assert actual-{''}==set(rows),('Unrecorded item-three change',actual^set(rows))
 for p,r in rows.items():
  if r['before_sha256'] is not None:
   old=subprocess.check_output(['git','show',data['baseline']+':'+p],cwd=ROOT)
   assert hashlib.sha256(old).hexdigest()==r['before_sha256'],p
  assert hashlib.sha256((ROOT/p).read_bytes()).hexdigest()==r['after_sha256'],p
 return rows

def native_successor(path,expected):
 row=native_rows().get(path)
 if row:
  assert row['before_sha256']==expected,(path,'item-three predecessor mismatch')
  return row['after_sha256']
 return expected

def predecessor_text(path,current):
 row=native_rows().get(path)
 if row and row['before_sha256'] is not None:
  baseline=json.loads(LEDGER.read_text())['baseline']
  return subprocess.check_output(['git','show',baseline+':'+path],cwd=ROOT).decode()
 return current
