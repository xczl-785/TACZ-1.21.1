"""Native magazine level IDs must map to actual gun-owned surfaces and animation chains."""
import copy,tempfile,unittest,subprocess,sys,os
from pathlib import Path
import numpy as np
import magazines as m
import build as pipeline
shared=m.shared;im=m.im;ex=m.ex

class MagazineVariants(unittest.TestCase):
    def rows(self):return ex.read(m.ROOT/'manifest.json')['parts']
    def test_complete_compatibility_not_one_model_per_level_item(self):
        rows=self.rows();expected={(index.stem,attachment) for index,attachment,_ in m.candidates()}
        self.assertEqual(len(expected),18);self.assertEqual(len({a for _,a in expected}),9)
        self.assertEqual({(r['gunId'].split(':')[1],r['attachmentId']) for r in rows},expected)
        self.assertEqual({r['gunId'] for r in rows},{'tacz:'+g for g in ('ai_awp','glock_17','m700','m870','ump45','uzi')})
        for row in rows:
            for path,digest in row['sourceHashes'].items():self.assertEqual(ex.sha(m.R/path),digest,path)
            source={b['name']:b for b in ex.read(m.R/row['sourceGeometry'])['minecraft:geometry'][0]['bones']}
            for name,indices in row['sourceCubeIndices'].items():
                if indices:self.assertIn(row['variantBone'],ex.ancestors(name,source))
            self.assertTrue(any(row['motionOwnership'].values()) or row['gunId']=='tacz:m870')
            if row['gunId']=='tacz:m870':self.assertIn('Tube extension',row['note'])
    def test_native_vertices_uv_and_blockbench_precision(self):
        for row in self.rows():
            model,bones,_,_,_=shared.load_part(row,m.ROOT)
            native={b['name']:b for b in ex.read(m.R/row['sourceGeometry'])['minecraft:geometry'][0]['bones']}
            if ex.sha(m.ROOT/row['model'])==row['baselineModelSha256']:
                for name,indices in row['sourceCubeIndices'].items():
                    for j,i in enumerate(indices):
                        p,u=ex.cube_geometry(native[name],native[name]['cubes'][i],native,np.eye(4))
                        q,v=ex.cube_geometry(bones[name],bones[name]['cubes'][j],bones,np.eye(4))
                        self.assertLess(float(np.max(np.abs(p-q))),1e-8,row['definitionId']);self.assertEqual(u,v)
            saved=copy.deepcopy(model)
            for obj in saved['groups']+saved['elements']:
                for key in ('origin','from','to','rotation'):
                    if key in obj:obj[key]=[round(v,5) for v in obj[key]]
            with tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp);im.write(root/row['model'],saved);(root/row['texture']).write_bytes((m.ROOT/row['texture']).read_bytes())
                _,again,_,_,_=shared.load_part(row,root)
                for name in bones:
                    self.assertEqual(bones[name],again[name])
    def test_append_does_not_overwrite_edited_source(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);manifest=ex.read(m.ROOT/'manifest.json');im.write(root/'manifest.json',manifest)
            p=root/manifest['parts'][0]['model'];p.parent.mkdir(parents=True);p.write_text('user edited model')
            m.append(root);self.assertEqual(p.read_text(),'user edited model');self.assertEqual(ex.read(root/'manifest.json'),manifest)
    def test_generated_variants_preserve_bones_and_edited_texture(self):
        for row in self.rows():
            _,bones,_,_,_=shared.load_part(row,m.ROOT)
            path='gun_parts/'+row['definitionId'];actual=ex.read(m.RES/('assets/tacz_fork_tarkov/geo_models/'+path+'.json'))['minecraft:geometry'][0]
            self.assertEqual(actual['bones'],[bones[n] for n in row['preservedBones']])
            self.assertEqual((m.RES/('assets/tacz_fork_tarkov/textures/'+path+'.png')).read_bytes(),(m.ROOT/row['texture']).read_bytes())
    def test_unified_build_is_deterministic_and_catalog_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            command=[sys.executable,'-c','import sys;from pathlib import Path;import build;build.build(Path(sys.argv[1]))',str(root)]
            subprocess.run(command,cwd=Path(__file__).parent,env=dict(os.environ,PYTHONHASHSEED='1',PYTHONDONTWRITEBYTECODE='1'),check=True,capture_output=True)
            doc=ex.read(root/'data/tacz_fork_tarkov/native_attachments/catalog.json')
            first={str(p.relative_to(root)):ex.sha(p) for p in root.rglob('*') if p.is_file()}
            subprocess.run(command,cwd=Path(__file__).parent,env=dict(os.environ,PYTHONHASHSEED='2',PYTHONDONTWRITEBYTECODE='1'),check=True,capture_output=True)
            second={str(p.relative_to(root)):ex.sha(p) for p in root.rglob('*') if p.is_file()}
            self.assertEqual(first,second);self.assertEqual(doc['attachmentDefinitions'],15)
            self.assertEqual(doc['standaloneModels'],6);self.assertEqual(doc['magazineVariants'],18)
            self.assertFalse(any('/index/' in p or '/tacz_tags/' in p or '/gun/m4a1' in p for p in first))

if __name__=='__main__':unittest.main()
