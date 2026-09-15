#!/usr/bin/env python3
"""Execute the actual pure-Java refund policy without launching Minecraft."""
import json, os, subprocess, tempfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
policy = ROOT/'src/main/java/com/tacz/guns/ammunition/TemporaryAmmoRefundPolicy.java'
assert policy.exists(), 'No temporary fixed-caliber refund policy exists; old native ammo refund is still active'
catalog = json.loads((ROOT/'ammunition/runtime/data/tarkov_content/catalog/ammunition.json').read_text())
by_source = {row['sourceId']: row for row in catalog}
mappings = json.loads((ROOT/'docs/assembly-experiment/temporary-refund-mapping.json').read_text())['mappings']
assert len(mappings) == 12
args = []
for row in mappings:
    assert by_source[row['source_id']]['caliber'] == row['caliber'], row
    args += [row['native_caliber_id'], row['source_id']]
java_home = os.environ.get('JAVA_HOME')
def executable(name):
    return str(Path(java_home)/'bin'/name) if java_home else name
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run([executable('javac'), '-d', tmp, str(policy), str(ROOT/'tools/tests/TemporaryAmmoRefundPolicyTest.java')], check=True)
    subprocess.run([executable('java'), '-cp', tmp, 'TemporaryAmmoRefundPolicyTest', *args], check=True)
