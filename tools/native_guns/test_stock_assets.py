import shutil,tempfile,unittest
from pathlib import Path
import numpy as np
import stock_assets as stocks

class AuthoredStocksTest(unittest.TestCase):
    def test_nine_reviewed_stocks_include_explicitly_anchored_ctr(self):
        rows=stocks.selected_rows()
        self.assertEqual(9,len(rows))
        ctr=next(row for row,aid in rows if aid=='tacz:stock_tactical_ar')
        self.assertEqual('stock_pos',ctr['nativeMount'])
        self.assertEqual('stock_pos',stocks.ex.read(stocks.R/ctr['componentMetadata'])['anchorBone'])
        self.assertTrue(all('/attachment/' in row['sourceGeometry'] for row,_ in rows))
        self.assertTrue(all(stocks.mount_offset(row).tolist()==[0,0,-3] for row,_ in rows))

    def test_export_has_local_geometry_authored_uv_and_texture_without_m4_mount(self):
        with tempfile.TemporaryDirectory() as directory:
            target=Path(directory);result=stocks.build(target)
            self.assertEqual(9,len(result['attachments']))
            for row,aid in stocks.selected_rows():
                _,expected,uv,image,count=stocks.shared.load_part(row,stocks.SOURCE);image.close()
                expected=stocks.shared.im.shifted_bones(expected,stocks.mount_offset(row))
                entry=result['attachments'][aid];resource=entry['model'].split(':')[1]
                shape=stocks.ex.read(target/f'assets/tacz_assembly/geo_models/{resource}.json')['minecraft:geometry'][0]
                actual={b['name']:b for b in shape['bones']}
                self.assertEqual(expected,actual)
                self.assertEqual(uv,[shape['description'][k] for k in ('texture_width','texture_height')])
                self.assertEqual((stocks.SOURCE/row['texture']).read_bytes(),(target/f'assets/tacz_assembly/textures/{resource}.png').read_bytes())
                for name,bone in expected.items():
                    for before,after in zip(bone.get('cubes',[]),actual[name].get('cubes',[])):
                        a,_=stocks.ex.cube_geometry(bone,before,expected,np.eye(4));b,_=stocks.ex.cube_geometry(actual[name],after,actual,np.eye(4))
                        np.testing.assert_allclose(a,b,atol=1e-10)
                        self.assertEqual(before['uv'],after['uv'])
                self.assertNotIn('lodModel',entry)

    def test_moe_export_follows_edited_cube_and_png_not_original_fallback(self):
        row,aid=next((r,a) for r,a in stocks.selected_rows() if a=='tacz:stock_moe')
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)/'source';out=Path(directory)/'out';root.mkdir()
            stocks.write(root/'manifest.json',{'parts':[row]})
            for key in ['model','texture']:
                dst=root/row[key];dst.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(stocks.SOURCE/row[key],dst)
            baseline,_=stocks.geometry(row,root)
            model=stocks.ex.read(root/row['model']);cube=next(e for e in model['elements'] if e['type']=='cube')
            face=next(k for k,v in cube['faces'].items() if v.get('texture') is not None)
            cube['from'][0]+=.5;cube['to'][0]+=.5;cube['faces'][face]['uv'][0]+=1
            stocks.write(root/row['model'],model)
            from PIL import Image
            with Image.open(root/row['texture']) as image:
                changed=image.convert('RGBA');changed.putpixel((0,0),(11,22,33,255));changed.save(root/row['texture'])
            result=stocks.build(out,root);entry=result['attachments'][aid];path=entry['model'].split(':')[1]
            actual=stocks.ex.read(out/f'assets/tacz_assembly/geo_models/{path}.json')
            self.assertNotEqual(baseline,actual)
            before=next(b for b in baseline['minecraft:geometry'][0]['bones'] if b.get('cubes'))['cubes'][0]
            after=next(b for b in actual['minecraft:geometry'][0]['bones'] if b.get('cubes'))['cubes'][0]
            self.assertAlmostEqual(before['origin'][0]-.5,after['origin'][0])
            self.assertNotEqual(before['uv'][face],after['uv'][face])
            self.assertEqual((root/row['texture']).read_bytes(),(out/f'assets/tacz_assembly/textures/{path}.png').read_bytes())

if __name__=='__main__':unittest.main()
