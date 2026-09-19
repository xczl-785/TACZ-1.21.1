import copy
import tempfile
import unittest
from pathlib import Path
import numpy as np
import produce as p

class FullCatalogTest(unittest.TestCase):
    def test_every_optic_roundtrips_all_bones_modes_uv_and_pixels(self):
        rows=p.ex.read(p.SOURCES/'catalog.json')['optics']
        self.assertEqual(28,len(rows))
        self.assertEqual(28,len({r['attachmentId'] for r in rows}))
        with tempfile.TemporaryDirectory() as tmp:
            for entry in rows:
                with self.subTest(optic=entry['attachmentId']):
                    source=p.R/entry['source'];config,row,bones,display,count=p.compile_optic(source,Path(tmp))
                    original=p.ex.read(p.R/row['sourceGeometry'])['minecraft:geometry'][0];old={b['name']:b for b in original['bones']}
                    self.assertEqual(set(old),set(bones))
                    self.assertEqual(sum(len(b.get('cubes',[])) for b in old.values()),count)
                    for name,b in old.items():
                        self.assertEqual({k:v for k,v in b.items() if k!='cubes'},{k:v for k,v in bones[name].items() if k!='cubes'})
                        for a,c in zip(b.get('cubes',[]),bones[name].get('cubes',[])):
                            av,af=p.ex.cube_geometry(b,a,old,np.eye(4));cv,cf=p.ex.cube_geometry(bones[name],c,bones,np.eye(4))
                            np.testing.assert_allclose(av,cv,atol=2e-5);np.testing.assert_allclose([v for _,v in af],[v for _,v in cf],atol=1e-8)
                    authored=p.ex.read(source/'display.json')
                    for prop in ('scope','sight','zoom','views','views_fov','fov'):
                        self.assertEqual(authored.get(prop),display.get(prop))
                    for name,digest in config['sourceHashes'].items():self.assertEqual(digest,p.ex.sha(p.R/name))
                    texture=Path(tmp)/'assets/tacz_fork_tarkov/textures/attachment'/(config['attachmentId'].split(':')[1]+'.png')
                    self.assertEqual((source/'editable'/row['texture']).read_bytes(),texture.read_bytes())
                    key=config['attachmentId'].split(':')[1]
                    for relative in (f'assets/tacz_fork_tarkov/geo_models/attachment/{key}.json',f'assets/tacz_fork_tarkov/display/attachments/{key}.json',f'data/tacz_fork_tarkov/index/attachments/{key}.json',f'data/tacz_fork_tarkov/data/attachments/{key}.json',f'assets/tacz_fork_tarkov/geo_models/attachment/lod/{key}.json'):
                        self.assertEqual((Path(tmp)/relative).read_bytes(),(p.OUT/relative).read_bytes())


    def test_invalid_functional_surfaces_and_modes_fail_closed(self):
        source=p.SOURCES/'scope_hamr_authored';config=p.ex.read(source/'optic.json');row=p.ex.read(source/'editable/manifest.json')['parts'][0]
        _,bones,_,_,_=p.shared.load_part(row,source/'editable');display=p.ex.read(source/'display.json')
        for missing in ('scope_view','division','ocular_sight','ocular_scope_2'):
            bad=copy.deepcopy(bones);bad.pop(missing)
            with self.subTest(missing=missing),self.assertRaises(ValueError):p.validate_optics(bad,display,config)
        bad=copy.deepcopy(display);bad['views']=[0,1]
        with self.assertRaises(ValueError):p.validate_optics(bones,bad,config)
        bad=copy.deepcopy(display);bad['zoom']=[float('nan'),1]
        with self.assertRaises(ValueError):p.validate_optics(bones,bad,config)

    def test_published_compatibility_preserves_original_sets_and_default_assembly(self):
        rows=p.ex.read(p.SOURCES/'catalog.json')['optics'];total=0;seen=set()
        for gun in sorted((p.OUT/'data/tacz_fork_tarkov').glob('*/weapon.json')):
            base=gun.parent
            if not (base/'native_attachments.json').exists():continue
            external=p.ex.read(base/'native_attachments.json');catalog=p.ex.read(base/'catalog.json')['parts']
            for row in rows:
                old,new=row['sourceAttachment'],row['attachmentId']
                self.assertEqual(old in external,new in external,(base.name,old))
                if new not in external:continue
                total+=1;seen.add(new)
                for part in catalog:
                    for slot in part['slots']:
                        self.assertEqual(external[old] in slot['allowedParts'],external[new] in slot['allowedParts'])
                self.assertNotIn(new,p.ex.read(base/'native_attachment_overrides.json'))
                self.assertFalse(any(n['definitionId']==external[new] for n in p.ex.read(base/'scene.json')['nodes']))
        self.assertEqual(222,total);self.assertEqual(28,len(seen))
