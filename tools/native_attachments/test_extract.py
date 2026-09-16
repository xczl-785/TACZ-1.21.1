import copy,json,tempfile,unittest
from pathlib import Path
import numpy as np
import extract as s
import import_assets
im=s.im

class NativeAttachmentContract(unittest.TestCase):
    def test_identity_counts_and_no_optics(self):
        rows=im.ex.read(s.OUT/'manifest.json')['parts']
        self.assertEqual({r['attachmentId'] for r in rows},{'tacz:'+n for n in s.IDS})
        self.assertEqual(sum(r['cubes'] for r in rows),215)
        self.assertTrue(all(r['sourceLod'] is None for r in rows))

    def test_source_roundtrip_and_blockbench_five_decimal_precision(self):
        for name in s.IDS:
            row,model,texture=s.extract(name)
            self.assertEqual((row,model,texture),s.extract(name))
            for obj in model['groups']+model['elements']:
                for key in ('origin','from','to','rotation'):
                    if key in obj:obj[key]=[round(v,5) for v in obj[key]]
            with tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp);im.write(root/row['model'],model);(root/row['texture']).write_bytes(texture.read_bytes())
                _,bones,uv,_,count=s.load_part(row,root)
            original=im.ex.read(s.R/row['sourceGeometry'])['minecraft:geometry'][0]
            native={b['name']:b for b in original['bones']}
            self.assertEqual(uv,row['uvSize']);self.assertEqual(count,row['cubes'])
            self.assertEqual(list(native),list(bones))
            for n,b in native.items():
                self.assertEqual({k:v for k,v in b.items() if k!='cubes'},{k:v for k,v in bones[n].items() if k!='cubes'})
                for i,c in enumerate(b.get('cubes',[])):
                    before,uv1=im.ex.cube_geometry(b,c,native,np.eye(4))
                    after,uv2=im.ex.cube_geometry(bones[n],bones[n]['cubes'][i],bones,np.eye(4))
                    self.assertLess(np.max(np.abs(before-after)),1e-8,name)
                    self.assertEqual(uv1,uv2,name)

    def test_append_preserves_edits_and_source_hashes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);m=s.append(root);row=m['parts'][0]
            target=root/row['model'];target.write_text('user edit')
            again=s.append(root);self.assertEqual(m,again);self.assertEqual(target.read_text(),'user edit')
            for r in m['parts']:
                for path,digest in r['sourceHashes'].items():self.assertEqual(im.ex.sha(s.R/path),digest)
                self.assertEqual((root/r['texture']).read_bytes(),(s.R/r['sourceTexture']).read_bytes())

    def test_generated_assets_keep_native_nodes_and_texture(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp);records=import_assets.build(output=out)
            self.assertEqual(len(records),6)
            for row in im.ex.read(s.OUT/'manifest.json')['parts']:
                asset=records[row['attachmentId']]
                geo=im.ex.read(out/'assets/tacz_assembly/geo_models'/f"{asset['model'].split(':')[1]}.json")['minecraft:geometry'][0]
                original=im.ex.read(s.R/row['sourceGeometry'])['minecraft:geometry'][0]
                self.assertEqual([b['name'] for b in geo['bones']],[b['name'] for b in original['bones']])
                tex=out/'assets/tacz_assembly/textures'/f"{asset['texture'].split(':')[1]}.png"
                self.assertEqual(tex.read_bytes(),(s.OUT/row['texture']).read_bytes())

if __name__=='__main__':unittest.main()
