"""Author relations must preserve the currently shipped M4 assembly contract."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'modules/tacz_adapter/weapon-sources/native_m4a1/assembly.json'
OUTPUT = ROOT / 'modules/tacz_adapter/weapon-content/resources/data/tacz_fork_tarkov/m4a1'


class AssemblySourceTest(unittest.TestCase):
    def test_authored_relations_preserve_catalog_and_default_tree(self):
        self.assertTrue(SOURCE.exists(), 'M4 needs an authored assembly source')
        from assembly_source import load_assembly
        source = load_assembly(SOURCE)
        catalog = json.loads((OUTPUT / 'catalog.json').read_text())['parts']
        self.assertEqual(source['physical'] + list(source['external'].values()),
                         [part['id'] for part in catalog])
        self.assertEqual(source['slots'], {
            part['id']: {slot['id']: slot['allowedParts'] for slot in part['slots']}
            for part in catalog if part['slots']})
        nodes = json.loads((OUTPUT / 'scene.json').read_text())['nodes']
        by_id = {node['instanceId']: node for node in nodes}
        preset = {}
        for node in nodes:
            if 'parentId' in node:
                parent = by_id[node['parentId']]['definitionId']
                preset.setdefault(parent, {})[node['slot']] = node['definitionId']
        self.assertEqual(source['preset'], preset)
        self.assertEqual(source['critical'], json.loads((OUTPUT / 'weapon.json').read_text())['requiredPaths'])

    def test_invalid_candidate_and_default_cycle_are_rejected(self):
        self.assertTrue(SOURCE.exists(), 'M4 needs an authored assembly source')
        from assembly_source import validate_assembly
        source = json.loads(SOURCE.read_text())
        source['slots']['lower_receiver']['upper'].append('missing_part')
        with self.assertRaisesRegex(ValueError, 'unknown candidate'):
            validate_assembly(source)
        source = json.loads(SOURCE.read_text())
        source['slots']['upper_receiver']['loop'] = ['lower_receiver']
        source['preset']['upper_receiver']['loop'] = 'lower_receiver'
        with self.assertRaisesRegex(ValueError, 'cycle'):
            validate_assembly(source)


if __name__ == '__main__':
    unittest.main()
