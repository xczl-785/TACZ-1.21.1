#!/usr/bin/env python3
"""Verify historical protection through exact, source-backed successor ledgers."""
import hashlib,json,sys,zipfile
from pathlib import Path
R=Path(__file__).resolve().parents[1]
d=json.loads((R/'docs/newmod/extra-content/removal.json').read_text())
first=R/'docs/assembly-experiment/ammunition-cleanup.json'
orphans={r['path']:r for r in json.loads(first.read_text())['deleted']} if first.exists() else {}
assert set(orphans)<= {
 'src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/textures/ammo/slot/20x82.png',
 'src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/textures/ammo/slot/font.png'}
second=R/'docs/assembly-experiment/native-ammo-retirement.json'
retirement={r['path']:r for r in json.loads(second.read_text())['files']} if second.exists() else {}

def current_hash(path, original):
 if path in orphans:
  assert orphans[path]['before_sha256']==original,path
  return None
 if path in retirement:
  row=retirement[path]
  assert row['before_sha256']==original,path
  return row['after_sha256']
 return original

def verify_file(path, expected, jar=None):
 if expected is None:
  assert not (R/path).exists(),path
  if jar is not None and path.startswith('src/main/resources/'):
   assert path.removeprefix('src/main/resources/') not in jar.namelist(),path
 else:
  assert hashlib.sha256((R/path).read_bytes()).hexdigest()==expected,path
  if jar is not None and path.startswith('src/main/resources/'):
   assert hashlib.sha256(jar.read(path.removeprefix('src/main/resources/'))).hexdigest()==expected,path

for row in d['files']:
 expected=None if row['action']=='delete' else current_hash(row['path'],row['after_sha256'])
 verify_file(row['path'],expected)
for row in d['protected']:
 verify_file(row['path'],current_hash(row['path'],row['sha256']))
assert not any('GunSmithTableMenu' in p.read_text() for p in (R/'src/main/java').rglob('*.java'))
for p in (R/'src/main/resources').rglob('*.json'):
 assert not any(x in p.read_text() for x in ['"tacz:ammo_box"','"tacz:blood_strike_1"','"tacz:gun_smith_table"','"tacz:workbench_a"','"tacz:workbench_b"','"tacz:workbench_c"']),p
if len(sys.argv)>1:
 with zipfile.ZipFile(sys.argv[1]) as z:
  for row in d['files']:
   expected=None if row['action']=='delete' else current_hash(row['path'],row['after_sha256'])
   verify_file(row['path'],expected,z)
  for row in d['protected']:
   verify_file(row['path'],current_hash(row['path'],row['sha256']),z)
  for cl in ['item/AmmoBoxItem','inventory/GunSmithTableMenu','block/entity/GunSmithTableBlockEntity','network/message/ClientMessageCraft']:
   assert 'com/tacz/guns/'+cl+'.class' not in z.namelist(),cl
print(f"EXTRA_CONTENT PASS: {len(d['protected'])} historical protection entries reconciled through exact successor hashes; earlier deletions remain absent")
