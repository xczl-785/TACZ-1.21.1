"""Reject invalid authored relations before generating any batch outputs."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import batch
import produce as p

SOURCE=p.R/'modules/tacz_adapter/weapon-sources/native_scar_l'


class PreflightTest(unittest.TestCase):
    def check_invalid(self,mutate,pattern):
        config=copy.deepcopy(p.ex.read(SOURCE/'production.json'))
        mutate(config)
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            (root/'production.json').write_text(json.dumps(config))
            (root/'mounts.json').write_bytes((SOURCE/'mounts.json').read_bytes())
            with self.assertRaisesRegex(ValueError,pattern):p.preflight(root/'production.json')

    def test_unknown_candidate(self):
        self.check_invalid(lambda c:c['slots'][c['rootDefinition']]['upper'].append('missing'),'unknown candidate')

    def test_duplicate_definition(self):
        self.check_invalid(lambda c:c['parts'].append(c['parts'][0]),'duplicate definition')

    def test_duplicate_candidate(self):
        self.check_invalid(lambda c:c['slots'][c['rootDefinition']]['upper'].extend(c['slots'][c['rootDefinition']]['upper']),'duplicate candidates')

    def test_incompatible_preset(self):
        self.check_invalid(lambda c:c['preset'][c['rootDefinition']].update(upper=c['rootDefinition']),'incompatible preset')

    def test_preset_cycle(self):
        def cycle(c):
            root=c['rootDefinition']
            c['slots'][root]['loop']=[root]
            c['preset'][root]['loop']=root
        self.check_invalid(cycle,'preset cycle')

    def test_bad_mount_does_not_write_shared_assets(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            config=p.ex.read(SOURCE/'production.json');config['authoredStockAssets']=True
            (root/'production.json').write_text(json.dumps(config))
            (root/'editable').mkdir()
            (root/'editable/manifest.json').write_bytes((SOURCE/'editable/manifest.json').read_bytes())
            mounts=p.ex.read(SOURCE/'mounts.json');mounts['parts'].pop(next(iter(mounts['parts'])))
            (root/'mounts.json').write_text(json.dumps(mounts))
            output=root/'output'
            with self.assertRaisesRegex(ValueError,'Mount definitions'):
                p.build(root/'production.json',resources=output)
            self.assertFalse(output.exists(),'Invalid input must not refresh shared assets')

    def test_late_invalid_gun_stops_batch_before_first_build(self):
        with patch.object(p,'preflight',create=True,side_effect=[{},ValueError('bad second gun')]), \
                patch.object(p,'build') as build, \
                patch.object(batch.validation,'validate',return_value={'gunId':'unused'}):
            with self.assertRaisesRegex(ValueError,'bad second gun'):
                batch.run(['scar_l','m16a1'])
            build.assert_not_called()

    def test_preflight_only_does_not_generate_or_validate_meshes(self):
        with patch.object(p,'build') as build,patch.object(batch.validation,'validate') as validate:
            batch.run(['scar_l','m870'],preflight_only=True)
            build.assert_not_called()
            validate.assert_not_called()

    def test_existing_catalogs_are_preserved_for_all_guns(self):
        for path in sorted((p.R/'modules/tacz_adapter/weapon-sources').glob('native_*/production.json')):
            with self.subTest(gun=path.parent.name):
                config,_,_,_,_,catalog,_=p.preflight(path)
                existing=p.ex.read(p.RES/'data/tacz_fork_tarkov'/config['sourceGun']/'catalog.json')['parts']
                self.assertEqual(catalog,existing)


if __name__=='__main__':unittest.main()
