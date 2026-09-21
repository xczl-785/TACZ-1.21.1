"""Promote the public NewMod artifacts TaCZ compiles against.

Every pinned artifact is rebuilt from a clean NewMod worktree at HEAD, named after that same commit
and recorded with its own SHA-512, so the lock, the file name and the bytes can never drift apart.
Run tools/verify_vendored_provenance.py afterwards (it is wired into `check`).
"""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
NEWMOD = ROOT.parent
VENDOR = ROOT / 'vendor/newmod'
# (lock file, module, scope, gradle project) in promotion order.
PINNED = [
    ('modules/public-dependency-lock.json', 'foundation', 'main', 'foundation'),
    ('modules/public-dependency-lock.json', 'firearms', 'main', 'firearms'),
    ('modules/tacz_adapter/public-dependency-lock.json', 'tactical', 'main', 'tactical'),
    ('modules/tacz_adapter/public-dependency-lock.json', 'character', 'main', 'character'),
    ('modules/tacz_adapter/public-dependency-lock.json', 'combat', 'main', 'combat'),
    ('modules/tacz_adapter/public-dependency-lock.json', 'tarkov_content', 'main', 'tarkov_content'),
    ('modules/tacz_adapter/public-dependency-lock.json', 'tactical', 'development', 'tactical'),
    ('modules/tacz_adapter/public-dependency-lock.json', 'tarkov_content', 'development', 'tarkov_content'),
]
LEGACY_KEYS = ['source_commit', 'artifact', 'sha512', 'firearms_source_commit', 'firearms_artifact', 'firearms_sha512']
GRADLE = NEWMOD / 'source/gradlew'
MOD_ID = re.compile(r"mod_id\s*=\s*'([^']+)'")
MOD_VERSION = re.compile(r'^mod_version\s*=\s*(\S+)$', re.MULTILINE)


def git(*arguments):
    return subprocess.run(['git', '-C', str(NEWMOD), *arguments], check=True, capture_output=True, text=True).stdout.strip()


def mod_id(project):
    text = (NEWMOD / f'source/mods/{project}/build.gradle').read_text()
    match = MOD_ID.search(text)
    if not match:
        raise SystemExit(f'No mod_id in source/mods/{project}/build.gradle')
    return match.group(1)


def main():
    if not GRADLE.is_file():
        raise SystemExit(f'Not a NewMod workspace: {NEWMOD}')
    if git('status', '--porcelain', '--', 'source'):
        raise SystemExit('NewMod source tree is dirty; commit or stash before promoting')
    commit = git('rev-parse', 'HEAD')
    short = commit[:8]
    version = MOD_VERSION.search((NEWMOD / 'source/gradle.properties').read_text()).group(1)

    identifiers = {project: mod_id(project) for project in {row[3] for row in PINNED}}
    tasks = sorted({f':{project}:{"developmentJar" if scope == "development" else "jar"}'
                    for _, _, scope, project in PINNED})
    command = [str(GRADLE), '-p', str(NEWMOD / 'source'), *tasks, '--offline']
    print('Building', commit[:12], '→', ' '.join(tasks))
    subprocess.run(command, check=True, cwd=NEWMOD)

    locks = {name: json.loads((ROOT / name).read_text()) for name, _, _, _ in PINNED}
    rows = []
    promoted = set()
    for lock_name, module, scope, project in PINNED:
        identifier = identifiers[project]
        built = NEWMOD / f'source/mods/{project}/build/libs/{identifier}-{version}.jar'
        if scope == 'development':
            built = built.with_name(f'{identifier}-{version}-development.jar')
        if not built.is_file():
            raise SystemExit(f'Missing build output: {built}')
        name = f'{identifier}-{"development-" if scope == "development" else ""}{short}.jar'
        target = VENDOR / name
        target.write_bytes(built.read_bytes())
        promoted.add(name)
        rows.append((lock_name, {'module': module, 'artifact': f'vendor/newmod/{name}', 'scope': scope,
                                 'source_commit': commit,
                                 'sha512': hashlib.sha512(target.read_bytes()).hexdigest()}))
        print(f'  {module:<15} {scope:<12} {name}')

    for stray in sorted(p for p in VENDOR.glob('*.jar') if p.name not in promoted):
        print('  removing stale artifact', stray.name)
        stray.unlink()

    for lock_name, lock in locks.items():
        for key in LEGACY_KEYS:
            lock.pop(key, None)
        lock['artifacts'] = [row for name, row in rows if name == lock_name]
        lock['build_command'] = ('JAVA_HOME=<JDK21> ./source/gradlew -p source ' + ' '.join(tasks) + ' --offline')
        (ROOT / lock_name).write_text(json.dumps(lock, indent=2, ensure_ascii=False) + '\n')
        print('  wrote', lock_name)

    unknown = [project for _, _, _, project in PINNED if not (NEWMOD / f'source/mods/{project}').is_dir()]
    if unknown:
        raise SystemExit(f'Unknown modules: {unknown}')
    print(f'PROMOTED {len(promoted)} artifacts from NewMod {commit}')


if __name__ == '__main__':
    sys.exit(main())
