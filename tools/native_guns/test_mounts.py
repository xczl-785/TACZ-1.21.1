"""Stable authored frames must survive mesh edits and candidate reordering."""
import copy
import unittest
import tempfile
from pathlib import Path
import produce as p


class AuthoredMountTest(unittest.TestCase):
    def test_unchanged_json_keeps_original_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'value.json'
            original='{"v": 0.000001}\n'
            path.write_text(original)
            p.write(path,{'v':1e-6})
            self.assertEqual(path.read_text(),original)
            p.write(path,{'v':2})
            self.assertEqual(p.ex.read(path),{'v':2})

    def test_all_guns_have_valid_authored_frames(self):
        for path in sorted((p.R/'modules/tacz_adapter/weapon-sources').glob('native_*/production.json')):
            with self.subTest(gun=path.parent.name):
                config=p.ex.read(path)
                base=p.RES/'data/tacz_assembly'/config['sourceGun']
                catalog=p.ex.read(base/'catalog.json')['parts']
                frames=p.load_mounts(path.parent/'mounts.json',catalog,config['rootDefinition'])
                anchors=p.ex.read(base/'workbench-anchors.json')['anchors']
                preview={m['definitionId']:m for m in p.ex.read(base/'preview.json')['models']}
                for definition,frame in frames.items():
                    self.assertEqual(frame['frameOrigin'],anchors[definition])
                    self.assertEqual(frame['slots'],preview[definition]['slots'])
                reordered=copy.deepcopy(catalog)
                for part in reordered:
                    for slot in part['slots']:slot['allowedParts'].reverse()
                self.assertEqual(frames,p.load_mounts(path.parent/'mounts.json',reordered,config['rootDefinition']))

    def test_invalid_mount_is_rejected(self):
        from native_m4a1.mount_source import validate_mounts
        catalog=[{'id':'receiver','slots':[]}]
        source={'schemaVersion':1,'parts':{'receiver':{'frameOrigin':[1,0,0],'attachmentOrigin':[0,0,0],'slots':{}}}}
        with self.assertRaisesRegex(ValueError,'Root frame'):
            validate_mounts(source,catalog,'receiver')


if __name__=='__main__':unittest.main()
