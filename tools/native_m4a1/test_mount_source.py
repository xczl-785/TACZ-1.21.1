"""Fixed author frames must not depend on model bounds or candidate order."""
import copy
import hashlib
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'modules/tacz_adapter/weapon-sources/native_m4a1'
BASE = ROOT / 'modules/tacz_adapter/weapon-content/resources/data/tacz_assembly/m4a1'


class MountSourceTest(unittest.TestCase):
    def test_native_reference_matches_preserved_inputs(self):
        source = json.loads((SOURCE / 'native-reference.json').read_text())
        base = ROOT / 'src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz'
        inputs = {'model': 'geo_models/gun/m4a1_geo.json',
                  'animation': 'animations/m4a1.animation.json',
                  'texture': 'textures/gun/uv/m4a1.png',
                  'display': 'display/guns/m4a1_display.json'}
        for key, path in inputs.items():
            self.assertEqual(source['sourceHashes'][key], hashlib.sha256((base / path).read_bytes()).hexdigest(), key)

    def test_default_stock_declares_its_native_mount(self):
        manifest = json.loads((SOURCE / 'editable/manifest.json').read_text())
        stock = next(p for p in manifest['parts'] if p['definitionId'] == 'tacz_stock_tactical_ar')
        self.assertEqual('stock_pos', stock.get('nativeMount'))

    def test_all_existing_frames_and_slots_have_an_author(self):
        self.assertTrue((SOURCE / 'mounts.json').exists(), 'explicit mounts are missing')
        from mount_source import load_mounts
        catalog = json.loads((BASE / 'catalog.json').read_text())['parts']
        mounts = load_mounts(SOURCE / 'mounts.json', catalog)
        preview = json.loads((BASE / 'preview.json').read_text())['models']
        for part in preview:
            authored = mounts[part['definitionId']]
            self.assertEqual(authored['slots'], part['slots'])
            self.assertEqual(authored['attachmentOrigin'], part['attachmentOrigin'])
        for part in catalog:
            for slot in part['slots']:
                slot['allowedParts'].reverse()
        self.assertEqual(mounts, load_mounts(SOURCE / 'mounts.json', catalog))

    def test_missing_nonfinite_and_disconnected_mounts_are_rejected(self):
        self.assertTrue((SOURCE / 'mounts.json').exists(), 'explicit mounts are missing')
        from mount_source import validate_mounts
        source = json.loads((SOURCE / 'mounts.json').read_text())
        catalog = json.loads((BASE / 'catalog.json').read_text())['parts']
        bad = copy.deepcopy(source)
        del bad['parts']['upper_receiver']['slots']['scope']
        with self.assertRaises(ValueError):
            validate_mounts(bad, catalog)
        bad = copy.deepcopy(source)
        bad['parts']['buffer']['frameOrigin'][0] = float('nan')
        with self.assertRaises(ValueError):
            validate_mounts(bad, catalog)
        bad = copy.deepcopy(source)
        bad['parts']['buffer']['slots']['stock'][0] += 1
        with self.assertRaisesRegex(ValueError, 'native rest placement'):
            validate_mounts(bad, catalog)


if __name__ == '__main__':
    unittest.main()
