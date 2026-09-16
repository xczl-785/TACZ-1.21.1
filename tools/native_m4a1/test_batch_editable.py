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
            if row['definitionId'] not in batch.BATCH:continue
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

if __name__=='__main__':unittest.main()
