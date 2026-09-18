"""Optical roundtrip and isolated registration; no Minecraft visual claims."""
import json
from pathlib import Path
import tempfile
import unittest

import numpy as np
import produce as p


class OpticsTest(unittest.TestCase):
    def test_complete_optical_geometry_survives_editable_roundtrip(self):
        for name in ('t2_sample', 'elcan_sample'):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                source = p.SOURCES / name
                config, row, bones, display, count = p.compile_optic(source, Path(directory))
                original = p.ex.read(p.R / row['sourceGeometry'])['minecraft:geometry'][0]
                lookup = {b['name']: b for b in original['bones']}
                self.assertEqual(set(lookup), set(bones))
                self.assertEqual(sum(len(b.get('cubes', [])) for b in lookup.values()), count)
                for key, old in lookup.items():
                    new = bones[key]
                    self.assertEqual({k: v for k, v in old.items() if k != 'cubes'},
                                     {k: v for k, v in new.items() if k != 'cubes'})
                    for a, b in zip(old.get('cubes', []), new.get('cubes', [])):
                        va, fa = p.ex.cube_geometry(old, a, lookup, np.eye(4))
                        vb, fb = p.ex.cube_geometry(new, b, bones, np.eye(4))
                        np.testing.assert_allclose(va, vb, atol=2e-5)
                        np.testing.assert_allclose([uv for _, uv in fa], [uv for _, uv in fb], atol=1e-8)
                output = Path(directory)
                key = config['attachmentId'].split(':')[1]
                self.assertEqual((source / 'editable' / row['texture']).read_bytes(),
                                 (output / f'assets/tacz_fork_tarkov/textures/attachment/{key}.png').read_bytes())
                for prop in ('scope', 'sight', 'views', 'zoom', 'fov'):
                    self.assertEqual(p.ex.read(source / 'display.json').get(prop), display.get(prop))
                self.assertFalse((output / 'assets/tacz').exists())
                self.assertFalse((output / 'data/tacz').exists())

    def test_new_ids_are_candidates_without_replacing_originals_or_default_tree(self):
        base = p.OUT / 'data/tacz_fork_tarkov/m4a1'
        catalog = p.ex.read(base / 'catalog.json')['parts']
        scope = next(s for part in catalog if part['id'] == 'upper_receiver' for s in part['slots'] if s['id'] == 'scope')
        self.assertTrue({'tacz_sight_t2', 'tacz_scope_elcan_4x', 'fork_sight_t2_sample', 'fork_scope_elcan_sample'} <= set(scope['allowedParts']))
        self.assertFalse(any(n.get('slot') == 'scope' for n in p.ex.read(base / 'scene.json')['nodes']))
        for name in ('t2_sample', 'elcan_sample'):
            config = p.ex.read(p.SOURCES / name / 'optic.json')
            for path, digest in config['sourceHashes'].items():
                self.assertEqual(digest, p.ex.sha(p.R / path), 'Original source changed: ' + path)

    def test_shipped_optics_match_author_sources_and_preview_excludes_distant_reticles(self):
        for name in ('t2_sample', 'elcan_sample'):
            with tempfile.TemporaryDirectory() as directory:
                output = Path(directory)
                p.compile_optic(p.SOURCES / name, output)
                for path in output.rglob('*'):
                    if path.is_file() and 'lang' not in path.parts:
                        self.assertEqual(path.read_bytes(), (p.OUT / path.relative_to(output)).read_bytes(), str(path))
        preview = p.ex.read(p.OUT / 'data/tacz_fork_tarkov/m4a1/preview.json')['models']
        for model in preview:
            if model['definitionId'] not in {'fork_sight_t2_sample', 'fork_scope_elcan_sample'}:
                continue
            points = np.array([v for mesh in model['meshes'] for t in mesh['triangles'] for v in t['vertices']])
            self.assertLess(float(np.ptp(points[:, 2])), 20, 'Reticle/stencil leaked into exterior fitting')


if __name__ == '__main__':
    unittest.main()
