"""Visit reachable slot candidates through the production Java worker, without Minecraft."""
import argparse
import importlib.util
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('workbench_server',ROOT/'modules/tacz_adapter/tools/workbench/server.py')
server=importlib.util.module_from_spec(spec)
spec.loader.exec_module(server)


def verify(guns):
    worker=server.Worker()
    try:
        for gun in guns:
            weapon='tacz_fork_tarkov:'+gun
            def request(**kwargs):
                result=worker.call({'op':'assembly','weapon':weapon,**kwargs})
                assert result['validation']['valid'],result['validation']
                assert result['parts'],'Missing preview geometry'
                return result
            original=request()
            queue=[original];visited=set();definitions=set()
            while queue:
                baseline=queue.pop()
                for slot in baseline['slots']:
                    for candidate in slot['candidates']:
                        key=(slot['path'],candidate['id'])
                        if key in visited:continue
                        visited.add(key)
                        changed=request(selection=baseline['selection'],edit={'path':slot['path'],'definition':candidate['id']})
                        assert changed['selection'][slot['path']]==candidate['id']
                        removed=request(selection=changed['selection'],edit={'path':slot['path'],'definition':''})
                        assert not any(p==slot['path'] or p.startswith(slot['path']+'/') for p in removed['selection'])
                        definitions.add(candidate['id'])
                        queue.append(changed)
            external=json.loads((ROOT/f'modules/tacz_adapter/weapon-content/resources/data/tacz_fork_tarkov/{gun}/native_attachments.json').read_text())
            assert set(external.values())<=definitions,'Native candidates were not visited'
            assert request()['selection']==original['selection'],'Temporary edits changed preset'
            print(json.dumps({'gun':weapon,'slotCandidateCases':len(visited),'distinctCandidates':len(definitions),
                              'nativeCandidates':len(external),'minecraftStarted':False}),flush=True)
    finally:
        worker.close()
        worker.process.wait(timeout=10)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('guns',nargs='+')
    verify(parser.parse_args().guns)
