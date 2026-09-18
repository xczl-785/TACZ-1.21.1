"""Regression checks for authored M4 workbench mount points."""
import json
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'modules/tacz_adapter/weapon-sources/native_m4a1/manifest.json'
PREVIEW=ROOT/'modules/tacz_adapter/weapon-content/resources/data/tacz_assembly/m4a1/preview.json'

class WorkbenchMountContract(unittest.TestCase):
    @staticmethod
    def bounds(model):
        points=[point for mesh in model['meshes'] for triangle in mesh['triangles'] for point in triangle['vertices']]
        return [min(point[axis] for point in points) for axis in range(3)],[max(point[axis] for point in points) for axis in range(3)]

    def test_scope_uses_the_authored_upper_receiver_mount(self):
        source=json.loads(SOURCE.read_text())
        parts={part['definitionId']:part for part in source['parts']}
        preview=json.loads(PREVIEW.read_text())
        models={model['definitionId']:model for model in preview['models']}
        expected=[a-b for a,b in zip(parts['upper_receiver']['slots']['scope'],parts['upper_receiver']['boundsCenter'])]
        for wanted,actual in zip(expected,models['upper_receiver']['slots']['scope']):
            self.assertAlmostEqual(wanted,actual,places=7)
        self.assertLess(abs(models['upper_receiver']['slots']['scope'][2]),10)

    def test_scope_override_does_not_replace_other_readable_card_anchors(self):
        source=json.loads(SOURCE.read_text())
        parts={part['definitionId']:part for part in source['parts']}
        anchors=json.loads((PREVIEW.parent/'workbench-anchors.json').read_text())['anchors']
        for wanted,actual in zip(parts['front_sight']['boundsCenter'],anchors['front_sight']):
            self.assertAlmostEqual(wanted,actual,places=7)

    def test_default_ar_stock_is_seated_on_the_buffer_tube(self):
        models={model['definitionId']:model for model in json.loads(PREVIEW.read_text())['models']}
        _,buffer_max=self.bounds(models['buffer'])
        _,stock_max=self.bounds(models['tacz_stock_tactical_ar'])
        exposed=buffer_max[2]-(models['buffer']['slots']['stock'][2]+stock_max[2])
        self.assertGreaterEqual(exposed,-.5)
        self.assertLessEqual(exposed,.1)

if __name__=='__main__':unittest.main()
