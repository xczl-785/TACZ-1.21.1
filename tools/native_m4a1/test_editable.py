"""Source/renderer-facing checks: geometry roundtrip, UV units, rig and texture projection."""
import copy,unittest
import numpy as np
from PIL import Image
import editable_import as im

class EditableContract(unittest.TestCase):
    def test_split_preserves_native_corners_and_face_uv(self):
        # Baseline source comparison detects sign, pivot and up/down UV conversion errors.
        maximum=0
        for row in im.ex.read(im.EDIT/'manifest.json')['parts']:
            _,bones,uv,texture,count=im.load_part(row)
            if im.ex.sha(im.EDIT/row['model'])!=row['baselineModelSha256']:continue
            old={b['name']:b for b in im.ex.read(im.R/row['sourceGeometry'])['minecraft:geometry'][0]['bones']}
            for name,indices in row['sourceCubeIndices'].items():
                self.assertEqual(len(indices),len(bones[name]['cubes']))
                for j,index in enumerate(indices):
                    a,fa=im.ex.cube_geometry(old[name],old[name]['cubes'][index],old,np.eye(4))
                    b,fb=im.ex.cube_geometry(bones[name],bones[name]['cubes'][j],bones,np.eye(4))
                    maximum=max(maximum,float(np.max(np.abs(a-b))))
                    self.assertEqual(fa,fb)
        self.assertLess(maximum,1e-8)

    def test_preview_and_held_use_same_normalized_uv_and_positions(self):
        report=im.ex.read(im.SOURCE/'editable-import-report.json');parts=im.ex.read(im.SOURCE/'manifest.json')['parts'];parts={p['definitionId']:p for p in parts}
        high=im.ex.read(im.OUT/'assets/tacz_assembly/geo_models/gun/m4a1.json')['minecraft:geometry'][0];bones={b['name']:b for b in high['bones']}
        atlas=Image.open(im.OUT/'assets/tacz_assembly/textures/gun/editable_m4a1.png').convert('RGBA')
        for row in report['parts']:
            d=row['definitionId'];entry=parts[d];mesh=im.ex.read(im.SOURCE/entry['model']);src=Image.open(im.SOURCE/entry['texture']).convert('RGBA')
            detached=row.get('runtimeMode')=='native_attachment'
            cx,cy=(0,0) if detached else row['atlasCell']
            if detached:
                geo=im.ex.read(im.OUT/('assets/tacz_assembly/geo_models/attachments/'+d+'.json'))['minecraft:geometry'][0]
                current_bones={b['name']:b for b in geo['bones']}
                extra=im.ex.matrix(entry['anchorBone'],bones)
                uvwidth,uvheight=geo['description']['texture_width'],geo['description']['texture_height']
            else:current_bones=bones;extra=np.eye(4);uvwidth=uvheight=512
            for element in mesh['elements']:
                leaf=current_bones[element['name'] if detached else 'assembly_editable_'+d+'_'+element['name']];faces=[];vertices=[]
                for c in leaf['cubes']:
                    vv,ff=im.ex.cube_geometry(leaf,c,current_bones,extra);start=len(vertices);vertices.extend(vv)
                    faces.extend(([i+start for i in ids],uv) for ids,uv in ff)
                actual=np.asarray(list(element['vertices'].values()))+entry['anchor']
                self.assertTrue(np.allclose(actual,vertices,atol=1e-8),d)
                for face,(indices,uv) in zip(element['faces'].values(),faces):
                    for key,held_uv in zip(face['vertices'],uv):
                        u,v=face['uv'][key];u/=src.width;v/=src.height
                        self.assertAlmostEqual(u,(held_uv[0]-cx)/uvwidth,places=8)
                        self.assertAlmostEqual(v,(held_uv[1]-cy)/uvheight,places=8)
                # Cell content matches source resampling, including transparent areas.
            if detached:continue
            self.assertEqual(atlas.crop((cx,cy,cx+512,cy+512)).tobytes(),src.resize((512,512),Image.Resampling.NEAREST).tobytes())

    def test_native_uv_units_are_not_png_pixels(self):
        rows=im.ex.read(im.EDIT/'manifest.json')['parts'];r=next(r for r in rows if r['definitionId']=='handguard_default')
        _,_,uv,texture,_=im.load_part(r)
        self.assertEqual(uv,[256,256]);self.assertEqual(texture.size,(512,512))
        self.assertEqual(im.atlas_cube({'origin':[0,0,0],'size':[1,1,1],'uv':{'north':{'uv':[128,64],'uv_size':[8,4]}}},uv,(512,0))['uv']['north'],{'uv':[768,128],'uv_size':[16,8]})

if __name__=='__main__':unittest.main()
