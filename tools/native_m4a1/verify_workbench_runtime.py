"""Exercise every M4 candidate through the real external-workbench Java worker."""
import importlib.util
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('workbench_server', ROOT / 'modules/tacz_adapter/tools/workbench/server.py')
server = importlib.util.module_from_spec(spec)
spec.loader.exec_module(server)


def verify():
    worker = server.Worker()
    cases = 0
    checked = set()
    try:
        catalog = worker.call({'op': 'catalog'})
        weapon = next(w['id'] for w in catalog['weapons'] if w['id'].endswith(':m4a1'))

        def request(**kwargs):
            result = worker.call({'op': 'assembly', 'weapon': weapon, **kwargs})
            assert result['validation']['valid'], result['validation']
            assert result['parts'], 'missing preview geometry'
            return result

        original = request()
        assert len(original['parts']) == 15
        tactical = request(edit={'path': 'upper/barrel_mount/handguard', 'definition': 'handguard_tactical'})
        for baseline in (original, tactical):
            for slot in baseline['slots']:
                for candidate in slot['candidates']:
                    changed = request(selection=baseline['selection'], edit={'path': slot['path'], 'definition': candidate['id']})
                    assert changed['selection'][slot['path']] == candidate['id']
                    removed = request(selection=changed['selection'], edit={'path': slot['path'], 'definition': ''})
                    assert not any(p == slot['path'] or p.startswith(slot['path'] + '/') for p in removed['selection'])
                    checked.add(candidate['id'])
                    cases += 1
        source = json.loads((ROOT / 'modules/tacz_adapter/weapon-sources/native_m4a1/assembly.json').read_text())
        assert set(source['external'].values()) <= checked
        assert request()['selection'] == original['selection'], 'temporary edits leaked into preset'
        print(json.dumps({'weapon': weapon, 'installRemoveCases': cases, 'distinctCandidates': len(checked),
                          'nativeCandidates': len(source['external']), 'presetUnchanged': True,
                          'minecraftStarted': False}, ensure_ascii=False))
    finally:
        worker.close()
        worker.process.wait(timeout=10)


if __name__ == '__main__':
    verify()
