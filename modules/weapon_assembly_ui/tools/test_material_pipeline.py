"""Appearance roundtrip checks; never write runtime resources."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
import import_material_project as pipeline


class MaterialPipelineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.project = json.loads((pipeline.geometry.MODULE / 'material-authoring/ADAR-original-materials.bbmodel').read_text())

    def convert(self, project):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'test.bbmodel'
            path.write_text(json.dumps(project))
            return pipeline.convert(path)

    def test_roundtrip_preserves_geometry_and_uv(self):
        manifest, _, count = self.convert(self.project)
        original = json.loads((pipeline.geometry.OUT / 'assembly-adar/manifest.json').read_text())
        self.assertEqual(count, 4714)
        for before, after in zip(original['models'], manifest['models']):
            for bm, am in zip(before['meshes'], after['meshes']):
                self.assertEqual(len(bm['triangles']), len(am['triangles']))
                for bt, at in zip(bm['triangles'], am['triangles']):
                    self.assertEqual(bt['vertices'], at['vertices'])
                    self.assertEqual(bt['region'], at['region'])
                    for bu, au in zip(bt['uv'], at['uv']):
                        for b, a in zip(bu, au):
                            self.assertAlmostEqual(b, a, places=8)

    def test_new_region_and_material_assignment(self):
        project = copy.deepcopy(self.project)
        pad = next(e for e in project['elements'] if e['name'] == 'butt_pad')
        pad['name'] = 'custom_pad'
        index = next(i for i, t in enumerate(project['textures']) if t['name'] == 'steel.png')
        for face in pad['faces'].values():
            face['texture'] = index
        manifest, bindings, _ = self.convert(project)
        self.assertTrue(any(p['regions'].get('custom_pad') == 'steel' for p in bindings['parts'].values()))
        self.assertTrue(any(t['region'] == 'custom_pad' for m in manifest['models'] for mesh in m['meshes'] for t in mesh['triangles']))

    def test_invalid_shape_and_uv_rejected(self):
        for invalid in ('shape', 'uv'):
            project = copy.deepcopy(self.project)
            element = project['elements'][0]
            face = next(iter(element['faces'].values()))
            vertex = face['vertices'][0]
            if invalid == 'shape':
                element['vertices'][vertex][0] += 1
            else:
                face['uv'][vertex][0] = float('nan')
            with self.assertRaises(AssertionError):
                self.convert(project)


if __name__ == '__main__':
    unittest.main()
