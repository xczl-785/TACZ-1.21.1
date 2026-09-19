"""Independent contracts for editable native gun projection and scope boundaries."""
import copy,importlib.util,tempfile,unittest
from pathlib import Path
import numpy as np
import produce as p
_spec=importlib.util.spec_from_file_location('native_gun_validation',Path(__file__).with_name('validate.py'))
_validation=importlib.util.module_from_spec(_spec);_spec.loader.exec_module(_validation)
validate=_validation.validate

class NativeGunProductionTest(unittest.TestCase):
    @staticmethod
    def bounds(model):
        points=[point for mesh in model['meshes'] for triangle in mesh['triangles'] for point in triangle['vertices']]
        return [min(point[axis] for point in points) for axis in range(3)],[max(point[axis] for point in points) for axis in range(3)]

    def test_complete_runtime_contract(self):
        report=validate();self.assertEqual(report['nativeRigBones'],57)
        self.assertEqual(report['highCubes'],264);self.assertLess(report['lowCubes'],report['highCubes']);self.assertTrue(report['lod'])

    def test_configuration_owns_caliber_and_rejects_cross_gun_icons(self):
        config=p.ex.read(p.DEFAULT)
        wrong=copy.deepcopy(config);wrong['weapon']['caliber']=''
        with self.assertRaisesRegex(ValueError,'Missing canonical caliber'):p.validate_configuration(wrong)
        wrong=copy.deepcopy(config);wrong['weapon']['partIconDirectory']='textures/item/m4a1'
        with self.assertRaises(ValueError):p.validate_configuration(wrong)

    def test_exported_physical_parts_must_be_installable(self):
        config=copy.deepcopy(p.ex.read(p.DEFAULT))
        config['parts'].append(dict(config['parts'][0],definitionId='orphan_release'))
        with self.assertRaisesRegex(ValueError,'Unreachable physical parts: orphan_release'):
            p.validate_configuration(config)

    def test_lod_retains_each_physical_entity_and_exact_uv_cubes(self):
        report=p.ex.read(p.DEFAULT.parent/'build-report.json')
        self.assertTrue(report['lod']);self.assertLess(report['lowCubes'],report['highCubes'])
        for proof in report['lod'].values():
            self.assertGreater(proof['lowCubes'],0)
            self.assertTrue(all(indices for indices in proof['selectedIndices'].values()))
            self.assertLessEqual(max(proof['silhouetteLoss']),.01)
            self.assertTrue(all(metric['meanError']<=.02 and metric['changedPixelFraction']<=.05 for metric in proof['textureComparisons']))

    def test_native_nonstandard_scope_body_uses_explicit_readonly_projection(self):
        index=p.ex.read(p.ex.SRC/'data/tacz/index/attachments/sight_p90.json')
        display=p.ex.read(p.ex.asset(index['display'],'display/attachments','.json'))
        geo=p.ex.read(p.ex.asset(display['model'],'geo_models','.json'))['minecraft:geometry'][0]
        bones={b['name']:b for b in geo['bones']}
        result=p.exterior_scope_geometry(bones,['default_sight'])
        self.assertEqual(result['default_sight']['cubes'],bones['default_sight']['cubes'])
        self.assertFalse(result['ocular'].get('cubes'))
        self.assertFalse(result['division_illuminated'].get('cubes'))
        self.assertTrue(bones['ocular']['cubes'])
        with self.assertRaises(ValueError):p.exterior_scope_geometry(bones,['missing'])

    def test_registered_batch_resources_use_distinct_configured_paths(self):
        types=set();directories=set()
        for entry in p.ex.read(p.RES/'data/tactical_tacz_adapter/assembled_weapons.json')['weapons']:
            weapon=p.ex.read(p.RES/entry)
            if not weapon.get('nativeRig') or weapon['authoringSource']=='native_m4a1':continue
            gun=weapon['gunId'].split(':')[1]
            root=p.R/'modules/tacz_adapter/weapon-sources'/('native_'+gun)
            config=p.ex.read(root/'production.json');weapon=config['weapon']
            validate(p.RES,weapon)
            self.assertNotIn(weapon['modelType'],types);types.add(weapon['modelType'])
            self.assertNotIn(weapon['partIconDirectory'],directories);directories.add(weapon['partIconDirectory'])
            report=p.ex.read(root/'build-report.json')
            self.assertLessEqual(report['lowCubes'],report['highCubes'])
            self.assertTrue((root/'lod-comparison.png').is_file())

    def test_scar_reuses_authored_stocks_without_baking_m4_mount(self):
        source_root=p.R/'modules/tacz_adapter/weapon-sources/native_scar_l'
        records,_=p.attachment_records(p.ex.read(source_root/'production.json'))
        stocks=[r for r in records if r['type']=='stock']
        self.assertEqual(len(stocks),9)
        overrides=p.ex.read(p.RES/'data/tacz_fork_tarkov/scar_l/native_attachment_overrides.json')
        catalog=p.ex.read(p.RES/'data/tacz_fork_tarkov/native_attachments/stocks.json')['attachments']
        for stock in stocks:
            self.assertIn('row',stock);self.assertTrue(p.authored_stock(stock['row']))
            self.assertEqual(overrides[stock['attachmentId']],catalog[stock['attachmentId']])
            self.assertIn('/authored_stock/',overrides[stock['attachmentId']]['model'])

    def test_scar_ar_stock_reaches_the_receiver_adapter_boundary(self):
        models={model['definitionId']:model for model in p.ex.read(p.RES/'data/tacz_fork_tarkov/scar_l/preview.json')['models']}
        lower_min,_=self.bounds(models['scar_l_lower'])
        _,stock_max=self.bounds(models['tacz_stock_tactical_ar'])
        gap=lower_min[2]-(models['scar_l_lower']['slots']['stock'][2]+stock_max[2])
        self.assertGreaterEqual(gap,-.5)
        self.assertLessEqual(gap,.1)

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

    def test_shared_magazines_are_gun_specific_and_optics_have_independent_sources(self):
        config=p.ex.read(p.DEFAULT);records,_=p.attachment_records(config)
        magazines=[r for r in records if r['native']];self.assertEqual(len(magazines),3)
        for r in magazines:
            self.assertEqual(r['row']['gunId'],'tacz:glock_17')
            self.assertIn('glock_17_geo',r['row']['sourceGeometry']);self.assertNotIn('m4a1',r['row']['sourceGeometry'])
        optics=[r for r in records if r['type']=='scope'];self.assertEqual(len(optics),12)
        originals=[r for r in optics if r['attachmentId'].startswith('tacz:')]
        authored=[r for r in optics if r['attachmentId'].startswith('tacz_fork_tarkov:')]
        self.assertEqual(len(originals),6);self.assertEqual(len(authored),6)
        self.assertTrue(all('row' not in r for r in originals))
        self.assertTrue(all('row' in r and '/optics/' in str(r['root']) for r in authored))
        editable=p.ex.read(p.DEFAULT.parent/'editable/manifest.json')['parts']
        self.assertEqual(len(editable),6);self.assertFalse(any('scope' in r['definitionId'] for r in editable))

    def test_scope_projection_excludes_first_person_planes_and_preserves_bones(self):
        records,_=p.attachment_records(p.ex.read(p.DEFAULT))
        preview={m['definitionId']:m for m in p.ex.read(p.RES/'data/tacz_fork_tarkov/glock_17/preview.json')['models']}
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
        model=next(m for m in p.ex.read(p.RES/'data/tacz_fork_tarkov/glock_17/preview.json')['models'] if m['definitionId']==laser['definitionId'])
        anchor=p.ex.read(p.RES/'data/tacz_fork_tarkov/glock_17/workbench-anchors.json')['anchors'][laser['definitionId']]
        for expected,actual in zip(meshes,model['meshes']):
            for e,a in zip(expected['triangles'],actual['triangles']):
                self.assertTrue(np.allclose(e['vertices'],np.asarray(a['vertices'])+anchor,atol=1e-8));self.assertEqual(e['uv'],a['uv'])

if __name__=='__main__':unittest.main()
