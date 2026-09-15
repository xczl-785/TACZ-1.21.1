"""Exact successor for the two integration edits in the original TaCZ source tree."""
import hashlib,json,subprocess
from functools import lru_cache
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
LEDGER=ROOT/'docs/assembly-experiment/weapon-source-integration.json'
@lru_cache(maxsize=1)
def source_rows():
    if not LEDGER.exists(): return {}
    data=json.loads(LEDGER.read_text());rows={r['path']:r for r in data['files']}
    assert set(rows)=={'src/main/java/com/tacz/guns/GunMod.java','src/main/resources/META-INF/neoforge.mods.toml'}
    for p,r in rows.items():
        old=subprocess.check_output(['git','show',data['baseline']+':'+p],cwd=ROOT)
        assert hashlib.sha256(old).hexdigest()==r['before_sha256'],p
        assert hashlib.sha256((ROOT/p).read_bytes()).hexdigest()==r['after_sha256'],p
        if p.endswith('GunMod.java'):
            assert (ROOT/p).read_text()==old.decode().replace('        CapabilityRegistry.ATTACHMENT_TYPES.register(bus);','        dev.weaponruntime.WeaponRuntime.register(bus);\n        CapabilityRegistry.ATTACHMENT_TYPES.register(bus);')
    return rows

def successor_hash(path,expected):
    row=source_rows().get(path)
    if row:
        assert row['before_sha256']==expected,path
        return row['after_sha256']
    return expected
