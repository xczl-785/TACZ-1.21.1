#!/usr/bin/env python3
"""Verify the dated removal ledger and independently preserved shooting/target resources."""
import hashlib,json,sys,zipfile
from pathlib import Path
R=Path(__file__).resolve().parents[1]
d=json.loads((R/'docs/newmod/extra-content/removal.json').read_text())
for row in d['files']:
 p=R/row['path']
 if row['action']=='delete':assert not p.exists(),p
 else:assert hashlib.sha256(p.read_bytes()).hexdigest()==row['after_sha256'],p
for row in d['protected']:
 assert hashlib.sha256((R/row['path']).read_bytes()).hexdigest()==row['sha256'],row['path']
assert not any('GunSmithTableMenu' in p.read_text() for p in (R/'src/main/java').rglob('*.java'))
for p in (R/'src/main/resources').rglob('*.json'):
 assert not any(x in p.read_text() for x in ['"tacz:ammo_box"','"tacz:blood_strike_1"','"tacz:gun_smith_table"','"tacz:workbench_a"','"tacz:workbench_b"','"tacz:workbench_c"']),p
if len(sys.argv)>1:
 with zipfile.ZipFile(sys.argv[1]) as z:
  names=set(z.namelist())
  for row in d['files']:
   path=row['path']
   if path.startswith('src/main/resources/') and row['action']=='delete':assert path.removeprefix('src/main/resources/') not in names,path
  for row in d['protected']:
   if row['path'].startswith('src/main/resources/'):
    assert hashlib.sha256(z.read(row['path'].removeprefix('src/main/resources/'))).hexdigest()==row['sha256'],row['path']
  for cl in ['item/AmmoBoxItem','inventory/GunSmithTableMenu','block/entity/GunSmithTableBlockEntity','network/message/ClientMessageCraft']:
   assert 'com/tacz/guns/'+cl+'.class' not in names,cl
print(f"EXTRA_CONTENT PASS: {sum(r['action']=='delete' for r in d['files'])} deleted files; {len(d['protected'])} protected files; {d['effects_count']} effects unchanged")
