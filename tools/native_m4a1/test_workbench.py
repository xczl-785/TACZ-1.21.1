"""Regression checks for authored M4 workbench mount points."""
import json
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'modules/tacz_adapter/weapon-sources/native_m4a1/manifest.json'
PREVIEW=ROOT/'modules/tacz_adapter/weapon-content/resources/data/tacz_assembly/m4a1/preview.json'

class WorkbenchMountContract(unittest.TestCase):
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

if __name__=='__main__':unittest.main()
