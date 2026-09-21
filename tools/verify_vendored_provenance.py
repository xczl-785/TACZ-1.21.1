"""Vendored NewMod artifacts must be traceable: file name, recorded commit and bytes must agree.

The previous promotion updated the SHA-512 but kept the old commit in the file name and lock, so the
pinned artifact silently stopped matching the commit it claimed. This gate makes that state invalid.
"""
from pathlib import Path
import hashlib
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
VENDOR = ROOT / 'vendor/newmod'
LOCKS = [
    ROOT / 'modules/public-dependency-lock.json',
    ROOT / 'modules/tacz_adapter/public-dependency-lock.json',
]
COMMIT = re.compile(r'[0-9a-f]{40}$')
SHORT_SUFFIX = re.compile(r'-([0-9a-f]{8})\.jar$')


def main():
    failures = []
    claimed = set()
    rows = 0
    for lock in LOCKS:
        relative_lock = lock.relative_to(ROOT)
        data = json.loads(lock.read_text())
        keys = set()
        for row in data['artifacts']:
            rows += 1
            artifact = ROOT / row['artifact']
            key = (row['module'], row['scope'])
            if key in keys:
                failures.append(f'{relative_lock}: duplicate artifact row {key}')
            keys.add(key)
            commit = row.get('source_commit', '')
            if not COMMIT.match(commit):
                failures.append(f'{relative_lock}: {key} has no full source commit: {commit!r}')
                continue
            if not artifact.is_file():
                failures.append(f'{relative_lock}: {key} artifact is missing: {row["artifact"]}')
                continue
            claimed.add(artifact.name)
            match = SHORT_SUFFIX.search(artifact.name)
            if not match or match.group(1) != commit[:8]:
                failures.append(f'{relative_lock}: {key} name does not record commit {commit[:8]}: {artifact.name}')
            digest = hashlib.sha512(artifact.read_bytes()).hexdigest()
            if digest != row['sha512']:
                failures.append(f'{relative_lock}: {key} bytes differ from the recorded SHA-512')
    for stray in sorted(p.name for p in VENDOR.glob('*.jar') if p.name not in claimed):
        failures.append(f'unrecorded artifact in vendor/newmod: {stray}')

    if failures:
        print('VENDORED_PROVENANCE FAIL:')
        for failure in failures:
            print('  -', failure)
        return 1
    print(f'VENDORED_PROVENANCE PASS: {rows} pinned artifacts, each named for its own source commit')
    return 0


if __name__ == '__main__':
    sys.exit(main())
