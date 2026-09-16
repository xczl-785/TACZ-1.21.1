import unittest
import numpy as np
from render_part_icon import render_part_icon


class PartIconTest(unittest.TestCase):
    def scene(self):
        mesh={'definitionId':'part','meshes':[{'triangles':[{'vertices':[[0,-1,-1],[0,-1,1],[0,1,0]],'uv':[[0,0],[1,0],[.5,1]],'region':'pad'}]}]}
        library={'materials':{'wood':{'baseColor':'#A06030'},'rubber':{'baseColor':'#202020'},'green':{'baseColor':'#00FF00'}}}
        bindings={'defaultMaterial':'wood','parts':{'part':{'defaultMaterial':'wood','regions':{}}}}
        return mesh,library,bindings

    def test_uses_material_color_and_transparency(self):
        args=self.scene(); image=np.asarray(render_part_icon(*args,lambda _:None))
        self.assertEqual(image.shape,(128,128,4));self.assertEqual(image[0,0,3],0)
        rgb=image[image[:,:,3]==255,:3]
        self.assertTrue(np.all(rgb[:,0]>rgb[:,1]));self.assertTrue(np.all(rgb[:,1]>rgb[:,2]))

    def test_region_override_changes_actual_preview(self):
        mesh,lib,bind=self.scene();bind['parts']['part']['regions']['pad']='green'
        image=np.asarray(render_part_icon(mesh,lib,bind,lambda _:None));rgb=image[image[:,:,3]==255,:3]
        self.assertTrue(np.all(rgb[:,1]>100));self.assertTrue(np.all(rgb[:,0]==0))

    def test_front_surface_occludes_later_back_surface(self):
        mesh,lib,bind=self.scene();front=mesh['meshes'][0]['triangles'][0]
        front['region']='front';front['vertices']=[[1,y,z] for _,y,z in front['vertices']]
        back={'vertices':[[0,-1,-1],[0,-1,1],[0,1,0]],'uv':front['uv'],'region':'pad'}
        bind['parts']['part']['regions']['front']='green'
        mesh['meshes'][0]['triangles'].append(back)
        image=np.asarray(render_part_icon(mesh,lib,bind,lambda _:None))
        r,g,b,a=image[64,64];self.assertEqual(a,255);self.assertGreater(g,100);self.assertEqual(r,0)

    def test_texture_is_multiplied_with_base_color(self):
        import tempfile
        from PIL import Image
        from pathlib import Path
        mesh,lib,bind=self.scene();lib['materials']['wood']['texture']='test:texture.png'
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'texture.png';Image.new('RGB',(2,2),(0,255,0)).save(path)
            image=np.asarray(render_part_icon(mesh,lib,bind,lambda _:path));rgb=image[image[:,:,3]==255,:3]
            self.assertTrue(np.all(rgb[:,0]==0));self.assertTrue(np.all(rgb[:,1]>0));self.assertTrue(np.all(rgb[:,2]==0))

    def test_missing_bound_texture_fails_instead_of_white_fallback(self):
        mesh,lib,bind=self.scene();lib['materials']['wood']['texture']='missing:texture.png'
        with self.assertRaises(FileNotFoundError):render_part_icon(mesh,lib,bind,lambda _: '/nonexistent/adar-texture.png')



class MountedIconTest(unittest.TestCase):
    scene = PartIconTest.scene
    def test_muzzle_left_and_camera_side(self):
        mesh,lib,bind=self.scene()
        lib['materials'].update(red={'baseColor':'#FF0000'},blue={'baseColor':'#0000FF'})
        bind['parts']['part']['regions'].update(muzzle='red',rear='blue',top='green')
        def mark(x,y,z,region):
            return {'vertices':[[x,y-.2,z-.2],[x,y-.2,z+.2],[x,y+.2,z]],'uv':[[0,0]]*3,'region':region}
        mesh['meshes'][0]['triangles']=[mark(0,0,2,'muzzle'),mark(0,0,-2,'rear'),mark(0,1,0,'top')]
        image=np.asarray(render_part_icon(mesh,lib,bind,lambda _:None,muzzle_left=True))
        red=np.argwhere((image[:,:,0]>100)&(image[:,:,1]<10)&(image[:,:,3]>240))
        blue=np.argwhere((image[:,:,2]>100)&(image[:,:,0]<10)&(image[:,:,3]>240))
        green=np.argwhere((image[:,:,1]>100)&(image[:,:,0]<10)&(image[:,:,3]>240))
        self.assertLess(red[:,1].mean(),blue[:,1].mean())
        self.assertLess(green[:,0].mean(),red[:,0].mean())
        # A camera rotation sees the opposite side; mirroring a bitmap does not.
        a=mark(-1,0,0,'muzzle');a['vertices']=[[-1,-2,-2],[-1,-2,2],[-1,2,0]]
        b=mark(1,0,0,'rear');b['vertices']=[[1,-2,-2],[1,-2,2],[1,2,0]]
        mesh['meshes'][0]['triangles']=[a,b]
        image=np.asarray(render_part_icon(mesh,lib,bind,lambda _:None,muzzle_left=True))
        self.assertGreater(image[64,64,0],100);self.assertEqual(image[64,64,2],0)

    def test_transparent_front_does_not_hide_back(self):
        import tempfile
        from pathlib import Path
        from PIL import Image
        mesh,lib,bind=self.scene();front=mesh['meshes'][0]['triangles'][0]
        front['region']='hole';front['vertices']=[[-1,y,z] for _,y,z in front['vertices']]
        back={'vertices':[[0,-1,-1],[0,-1,1],[0,1,0]],'uv':front['uv'],'region':'pad'}
        lib['materials']['transparent']={'baseColor':'#FFFFFF','texture':'hole'}
        bind['parts']['part']['regions']['hole']='transparent'
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'hole.png';Image.new('RGBA',(2,2),(255,0,0,0)).save(path)
            empty=np.asarray(render_part_icon(mesh,lib,bind,lambda _:path,muzzle_left=True,alpha_cutout=True))
            self.assertFalse(empty[:,:,3].any())
            mesh['meshes'][0]['triangles'].append(back)
            image=np.asarray(render_part_icon(mesh,lib,bind,lambda _:path,muzzle_left=True,alpha_cutout=True))
            self.assertEqual(image[64,64,3],255);self.assertGreater(image[64,64,1],10)
            mesh['meshes'][0]['triangles'].reverse()
            reverse=np.asarray(render_part_icon(mesh,lib,bind,lambda _:path,muzzle_left=True,alpha_cutout=True))
            np.testing.assert_array_equal(image,reverse)

if __name__=='__main__':unittest.main()
