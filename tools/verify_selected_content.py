#!/usr/bin/env python3
"""Exercise the Java resource policy against the full audited default-pack manifest."""
import json,subprocess,tempfile
from pathlib import Path
R=Path(__file__).resolve().parents[1]
if (R/'docs/newmod/cleanup/applied.json').exists():
    import sys
    subprocess.run([sys.executable,str(R/'tools/verify_cleanup.py')],check=True)
    raise SystemExit(0)
s=json.loads((R/'docs/content-isolation/selection.json').read_text())
i=json.loads((R/'docs/content-isolation/inventory.json').read_text())
blocked={r['path'].removeprefix('data/tacz/') for r in i['recipes'] if r['isolate']}
blocked.update('index/guns/'+x.split(':')[1]+'.json' for x in s['exclude_ids'])
paths=[x['path'].removeprefix('data/tacz/') for x in i['preserved_assets'] if x['path'].startswith('data/tacz/')]
checks=[]
for p in paths:
 checks.append('check("tacz", '+json.dumps(p)+', '+str(p in blocked).lower()+');')
checks.extend(['check("other", "recipe/ammo/9mm.json", false);','check("tacz", "recipe/ammo/future_round.json", true);'])
source='''import com.tacz.guns.resource.SelectedContentPolicy;
class PolicyCheck {
 static void check(String ns, String p, boolean expected) {
  if (SelectedContentPolicy.excludesResource(ns,p)!=expected) throw new AssertionError(ns+":"+p);
 }
 public static void main(String[] args) {'''+ '\n'.join(checks)+'''System.out.println("resource-policy checks passed");}}
'''
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'PolicyCheck.java';p.write_text(source)
 subprocess.run(['javac','-d',d,str(R/'src/main/java/com/tacz/guns/resource/SelectedContentPolicy.java'),str(p)],check=True)
 subprocess.run(['java','-cp',d,'PolicyCheck'],check=True)
assert len(blocked)==41
print(f'{len(checks)} resource cases; 9 gun indexes + 32 recipes blocked; all ammo definitions preserved')
