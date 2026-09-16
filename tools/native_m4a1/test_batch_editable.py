"""Regression checks for append-only native non-optic source production."""
import tempfile,unittest
from pathlib import Path
from unittest.mock import patch
import numpy as np
import batch_editable as batch
import editable_import as im

class BatchImportContract(unittest.TestCase):
    def test_reviewed_batch_has_no_optic_or_existing_default(self):
        defaults={n['definitionId'] for n in im.ex.read(im.BASE/'scene.json')['nodes']}
        self.assertFalse(defaults & set(batch.BATCH))
        self.assertEqual(len(batch.BATCH),9)
        self.assertTrue(all(d=='handguard_tactical' or d.startswith('tacz_stock_') for d in batch.BATCH))

    def test_repeat_preserves_every_existing_edit(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);manifest=im.ex.read(im.EDIT/'manifest.json')
            im.write(root/'manifest.json',manifest)
            sentinel=root/'components'/batch.BATCH[0]/'model.bbmodel';sentinel.parent.mkdir(parents=True);sentinel.write_text('user edited model')
            batch.append(root)
            self.assertEqual(sentinel.read_text(),'user edited model')
            self.assertEqual(im.ex.read(root/'manifest.json'),manifest)

    def test_extraction_is_deterministic_and_preserves_native_uv_units(self):
        parts={p['definitionId']:p for p in im.ex.read(im.SOURCE/'manifest.json')['parts']}
        for d in batch.BATCH:
            a,model,texture=batch.extract(parts[d]);b,again,other=batch.extract(parts[d])
            self.assertEqual(a,b);self.assertEqual(model,again)
            source=im.ex.read(im.R/a['sourceGeometry'])['minecraft:geometry'][0]
            self.assertEqual(a['uvSize'],[source['description'][k] for k in ('texture_width','texture_height')])
            self.assertEqual((im.EDIT/a['texture']).read_bytes(),texture.read_bytes())
            self.assertEqual(len(model['elements']),sum(map(len,a['sourceCubeIndices'].values())))

    def test_blockbench_five_decimal_save_preserves_rig_and_surfaces(self):
        for row in im.ex.read(im.EDIT/'manifest.json')['parts']:
            if row['definitionId'] not in batch.BATCH+batch.REMAINING:continue
            model=im.ex.read(im.EDIT/row['model'])
            for obj in model['groups']+model['elements']:
                for key in ('origin','from','to','rotation'):
                    if key in obj:obj[key]=[round(v,5) for v in obj[key]]
            with tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp);im.write(root/row['model'],model)
                (root/row['texture']).write_bytes((im.EDIT/row['texture']).read_bytes())
                with patch.object(im,'EDIT',root):_,bones,_,_,_=im.load_part(row)
            native={b['name']:b for b in im.ex.read(im.R/row['sourceGeometry'])['minecraft:geometry'][0]['bones']}
            for name,indices in row['sourceCubeIndices'].items():
                for j,index in enumerate(indices):
                    before,old_uv=im.ex.cube_geometry(native[name],native[name]['cubes'][index],native,np.eye(4))
                    after,new_uv=im.ex.cube_geometry(bones[name],bones[name]['cubes'][j],bones,np.eye(4))
                    self.assertLess(float(np.max(np.abs(before-after))),1e-8,row['definitionId'])
                    self.assertEqual(old_uv,new_uv)

    def test_all_selected_stocks_have_distinct_inline_rig(self):
        rows=im.ex.read(im.EDIT/'manifest.json')['parts']
        inline=im.ex.read(im.BASE/'inline_attachments.json')['stock']
        high=im.ex.read(im.OUT/'assets/tacz_assembly/geo_models/gun/m4a1.json')['minecraft:geometry'][0]['bones']
        names=[b['name'] for b in high];self.assertEqual(len(names),len(set(names)))
        for row in rows:
            if row['definitionId'].startswith('tacz_stock_'):
                d=row['definitionId'];self.assertEqual(inline[d.replace('tacz_','tacz:',1)],d)
                self.assertTrue(any(n.startswith('assembly_editable_'+d+'_') for n in names))

class RemainingImportContract(unittest.TestCase):
    def test_scope_and_append_preserve_existing_sources(self):
        self.assertEqual(len(batch.REMAINING),28)
        self.assertFalse(set(batch.BATCH)&set(batch.REMAINING))
        self.assertFalse(any('scope' in d or 'sight' in d for d in batch.REMAINING))
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);manifest=im.ex.read(im.EDIT/'manifest.json')
            im.write(root/'manifest.json',manifest)
            sentinel=root/'components'/batch.REMAINING[0]/'model.bbmodel';sentinel.parent.mkdir(parents=True);sentinel.write_text('edited source')
            report=batch.append(root,batch.REMAINING)
            self.assertEqual(sentinel.read_text(),'edited source')
            self.assertEqual(im.ex.read(root/'manifest.json'),manifest)
            self.assertEqual(report['skipped'],[])

    def test_detached_sources_keep_functional_bones_and_native_texture(self):
        rows={r['definitionId']:r for r in im.ex.read(im.EDIT/'manifest.json')['parts']}
        sourceparts={p['definitionId']:p for p in im.ex.read(im.SOURCE/'manifest.json')['parts']}
        for d in batch.REMAINING:
            row=rows[d];again,model,texture=batch.extract(sourceparts[d])
            self.assertEqual(im.ex.read(im.EDIT/row['model']),model)
            self.assertEqual((im.EDIT/row['texture']).read_bytes(),texture.read_bytes())
            _,bones,uv,_,_=im.load_part(row)
            source=im.ex.read(im.R/row['sourceGeometry'])['minecraft:geometry'][0]
            self.assertEqual(uv,[source['description'][k] for k in ('texture_width','texture_height')])
            if row['runtimeMode']=='native_attachment':
                self.assertEqual(list(bones),[b['name'] for b in source['bones']])
                for bone in source['bones']:
                    for k,v in bone.items():
                        if k!='cubes':self.assertEqual(bones[bone['name']][k],v)
                if d.startswith('tacz_laser_') or d=='tacz_grip_vertical_ranger':self.assertIn('laser_beam',bones)
            else:
                self.assertEqual(row['anchorBone'] if 'anchorBone' in row else sourceparts[d]['anchorBone'],'magazine')
                self.assertTrue(any('mag_and_bullet' in g['animationAncestors'] for g in row['motionOwnership']))

    def test_generated_detached_contract_preserves_all_source_nodes(self):
        overrides=im.ex.read(im.BASE/'native_attachment_overrides.json')
        rows={r['definitionId']:r for r in im.ex.read(im.EDIT/'manifest.json')['parts']}
        self.assertEqual(len(overrides),25)
        for d in batch.REMAINING:
            row=rows[d]
            if row['runtimeMode']!='native_attachment':continue
            mapping=overrides[d.replace('tacz_','tacz:',1)]
            model=im.ex.read(im.OUT/('assets/tacz_assembly/geo_models/'+mapping['model'].split(':')[1]+'.json'))['minecraft:geometry'][0]
            source=im.ex.read(im.R/row['sourceGeometry'])['minecraft:geometry'][0]
            self.assertEqual([b['name'] for b in model['bones']],[b['name'] for b in source['bones']])
            self.assertEqual((im.OUT/('assets/tacz_assembly/textures/'+mapping['texture'].split(':')[1]+'.png')).read_bytes(),(im.EDIT/row['texture']).read_bytes())
            if 'lodModel' in mapping:
                lod=im.ex.read(im.OUT/('assets/tacz_assembly/geo_models/'+mapping['lodModel'].split(':')[1]+'.json'))['minecraft:geometry'][0]
                self.assertEqual(mapping['lodTexture'],mapping['texture'])
                self.assertEqual([b['name'] for b in lod['bones']],[b['name'] for b in model['bones']])
                for low,high in zip(lod['bones'],model['bones']):
                    self.assertEqual({k:v for k,v in low.items() if k!='cubes'},{k:v for k,v in high.items() if k!='cubes'})
                    self.assertLessEqual(len(low.get('cubes',[])),2)
                    self.assertTrue(all(c in high.get('cubes',[]) for c in low.get('cubes',[])))
        self.assertEqual(sum('lodModel' in v for v in overrides.values()),3)

if __name__=='__main__':unittest.main()
