"""Independent contracts for editable native gun projection and scope boundaries."""
import importlib.util,tempfile,unittest
from pathlib import Path
import numpy as np
import produce as p
_spec=importlib.util.spec_from_file_location('native_gun_validation',Path(__file__).with_name('validate.py'))
_validation=importlib.util.module_from_spec(_spec);_spec.loader.exec_module(_validation)
validate=_validation.validate

class NativeGunProductionTest(unittest.TestCase):
    def test_complete_runtime_contract(self):
        report=validate();self.assertEqual(report['nativeRigBones'],57)
        self.assertEqual(report['highCubes'],264);self.assertEqual(report['highCubes'],report['lowCubes'])

    def test_blockbench_rounding_preserves_native_rig_and_uv(self):
        source_root=p.DEFAULT.parent;manifest=p.ex.read(source_root/'editable/manifest.json')
        for row in manifest['parts']:
            model=p.ex.read(source_root/'editable'/row['model'])
            for obj in model['groups']+model['elements']:
                for key in ('origin','from','to','rotation'):
                    if key in obj:obj[key]=[round(v,5) for v in obj[key]]
            with tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp);p.write(root/row['model'],model);(root/row['texture']).write_bytes((source_root/'editable'/row['texture']).read_bytes())
                _,bones,_,_,_=p.shared.load_part(row,root)
            original=p.ex.read(p.R/row['sourceGeometry'])['minecraft:geometry'][0];lookup={b['name']:b for b in original['bones']}
            for name,indices in row['sourceCubeIndices'].items():
                for j,index in enumerate(indices):
                    before,uv=p.ex.cube_geometry(lookup[name],lookup[name]['cubes'][index],lookup,np.eye(4));after,actual_uv=p.ex.cube_geometry(bones[name],bones[name]['cubes'][j],bones,np.eye(4))
                    self.assertLess(float(np.max(np.abs(before-after))),1e-8);self.assertEqual(uv,actual_uv)

    def test_shared_magazines_are_gun_specific_and_optics_are_not_new_sources(self):
        config=p.ex.read(p.DEFAULT);records,_=p.attachment_records(config)
        magazines=[r for r in records if r['native']];self.assertEqual(len(magazines),3)
        for r in magazines:
            self.assertEqual(r['row']['gunId'],'tacz:glock_17')
            self.assertIn('glock_17_geo',r['row']['sourceGeometry']);self.assertNotIn('m4a1',r['row']['sourceGeometry'])
        optics=[r for r in records if r['type']=='scope'];self.assertEqual(len(optics),6)
        self.assertTrue(all('row' not in r for r in optics))
        editable=p.ex.read(p.DEFAULT.parent/'editable/manifest.json')['parts']
        self.assertEqual(len(editable),6);self.assertFalse(any('scope' in r['definitionId'] for r in editable))

    def test_scope_projection_excludes_first_person_planes_and_preserves_bones(self):
        records,_=p.attachment_records(p.ex.read(p.DEFAULT))
        preview={m['definitionId']:m for m in p.ex.read(p.RES/'data/tacz_assembly/glock_17/preview.json')['models']}
        for record in records:
            if record['type']!='scope':continue
            source=p.ex.read(record['source'])['minecraft:geometry'][0]
            bones={b['name']:b for b in source['bones']}
            filtered=p.exterior_scope_geometry(bones)
            self.assertEqual(set(bones),set(filtered))
            for name,bone in bones.items():
                self.assertEqual({k:v for k,v in bone.items() if k!='cubes'},
                                 {k:v for k,v in filtered[name].items() if k!='cubes'})
                self.assertEqual(bone,next(b for b in source['bones'] if b['name']==name))
            names={m['name'] for m in preview[record['definitionId']]['meshes']}
            self.assertTrue(names)
            self.assertTrue(all({'scope_body','ocular_ring'}.intersection(p.ex.ancestors(name,bones)) for name in names))
            self.assertFalse(any(name.startswith(('division','ocular_illuminated')) or name=='ocular' for name in names))
            self.assertTrue(any(b.get('cubes') for n,b in bones.items() if n.startswith('division')))
            self.assertTrue(all(not filtered[n].get('cubes') for n in bones if n.startswith('division') or n=='ocular'))
        with self.assertRaises(ValueError):p.exterior_scope_geometry({'division':{'name':'division','pivot':[0,0,0],'cubes':[]}})

    def test_source_append_is_idempotent_and_does_not_overwrite_edit(self):
        config=p.ex.read(p.DEFAULT)
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);manifest=p.append(config,root);first=manifest['parts'][0]
            path=root/'editable'/first['model'];path.write_text('user edit sentinel')
            self.assertEqual(p.append(config,root),manifest);self.assertEqual(path.read_text(),'user edit sentinel')

    def test_rig_projection_handles_rotated_laser_mount(self):
        config=p.ex.read(p.DEFAULT);_,_,_,_,source,_,_=p.inputs(config)
        bones={b['name']:b for b in p.ex.read(source)['minecraft:geometry'][0]['bones']}
        matrix=p.ex.matrix('laser_pos',bones);self.assertFalse(np.allclose(matrix[:3,:3],np.eye(3)))
        records,_=p.attachment_records(config);laser=next(r for r in records if r['attachmentId']=='tacz:laser_compact')
        _,geometry,uv,_,_=p.shared.load_part(laser['row'],laser['root'])
        meshes,_=p.mesh_for(laser['definitionId'],geometry,uv,laser['root']/laser['row']['texture'],matrix)
        model=next(m for m in p.ex.read(p.RES/'data/tacz_assembly/glock_17/preview.json')['models'] if m['definitionId']==laser['definitionId'])
        anchor=p.ex.read(p.RES/'data/tacz_assembly/glock_17/workbench-anchors.json')['anchors'][laser['definitionId']]
        for expected,actual in zip(meshes,model['meshes']):
            for e,a in zip(expected['triangles'],actual['triangles']):
                self.assertTrue(np.allclose(e['vertices'],np.asarray(a['vertices'])+anchor,atol=1e-8));self.assertEqual(e['uv'],a['uv'])

if __name__=='__main__':unittest.main()
