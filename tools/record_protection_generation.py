#!/usr/bin/env python3
"""Record everything that changed under the protected roots as a new change-protection generation.

The generation ledger is what makes an unrecorded edit to a protected root fail verifyWeaponModules.
Reusing the previous generation's baseline keeps the successor chain resolving paths deleted earlier.
"""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[1]
LEDGER = ROOT / 'docs/assembly-experiment/migration-protection-generations.json'


def git(*arguments, check=True):
    return subprocess.run(['git', *arguments], cwd=ROOT, capture_output=True, check=check)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--id', required=True, help='generation id; an existing id is refreshed in place')
    parser.add_argument('--baseline', help='commit to diff against; defaults to the previous generation baseline')
    arguments = parser.parse_args()

    ledger = json.loads(LEDGER.read_text())
    baseline = arguments.baseline or ledger['generations'][-1]['baseline']
    roots = ledger['protected_roots']
    if git('cat-file', '-e', baseline + '^{commit}', check=False).returncode:
        raise SystemExit(f'Unknown baseline commit {baseline}')

    changed = git('diff', '--no-renames', '--name-only', '-z', baseline, '--', *roots).stdout.decode()
    untracked = git('ls-files', '--others', '--exclude-standard', '-z', '--', *roots).stdout.decode()
    paths = {p for p in (changed + untracked).split('\0') if p}

    rows = []
    for path in sorted(paths):
        before = git('show', f'{baseline}:{path}', check=False)
        current = ROOT / path
        rows.append({'path': path,
                     'before_sha256': hashlib.sha256(before.stdout).hexdigest() if before.returncode == 0 else None,
                     'after_sha256': hashlib.sha256(current.read_bytes()).hexdigest() if current.is_file() else None})

    generation = {'id': arguments.id, 'baseline': baseline, 'files': rows}
    for index, existing in enumerate(ledger['generations']):
        if existing['id'] == arguments.id:
            ledger['generations'][index] = generation
            break
    else:
        ledger['generations'].append(generation)
    LEDGER.write_text(json.dumps(ledger, indent=2, ensure_ascii=False) + '\n')
    print(f'Recorded generation {arguments.id}: {len(rows)} paths against {baseline[:12]}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
