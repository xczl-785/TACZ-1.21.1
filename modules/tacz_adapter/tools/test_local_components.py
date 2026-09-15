"""Regression cases for the local-product source adapter and actual M4 pack."""
import copy, unittest
from pathlib import Path
import numpy as np
from build_weapon import read, convert_component, ROOT
from prepare_local_components import local_socket, C

class LocalComponentsTest(unittest.TestCase):
    def test_source_root_is_removed_before_nested_socket_install(self):
        root=np.eye(4);root[:3,3]=[.2,1.5,-.3]
        socket=root.copy();socket[:3,3]+=[0,.1,.2]
        bbroot=C@root@np.linalg.inv(C)
        actual=local_socket({'local_to_source_baked':bbroot.tolist()},{'rootMatrix':socket.tolist()})
        np.testing.assert_allclose(actual[:3,3],[0,10,20],atol=1e-10)
        self.assertAlmostEqual(np.linalg.det(actual[:3,:3]),1)

    def test_flat_products_preserve_uv_and_mirrored_winding(self):
        model={'meta':{'model_format':'free'},'resolution':{'width':100,'height':50},'outliner':['mesh'],'elements':[{'uuid':'mesh','type':'mesh','name':'steel','vertices':{'a':[0,0,0],'b':[1,0,0],'c':[0,1,0]},'faces':{'face':{'vertices':['a','b','c'],'uv':{'a':[0,0],'b':[100,0],'c':[0,50]}}}}]}
        mirrored=np.diag([-1.,1,1,1]);t=convert_component(model,mirrored,np.zeros(3))[0]['triangles'][0]
        self.assertEqual(t['vertices'],[[0,1,0],[-1,0,0],[0,0,0]])
        self.assertEqual(t['uv'],[[0,1],[1,0],[0,0]])
        broken=copy.deepcopy(model);broken['outliner'].append('mesh')
        with self.assertRaises(ValueError):convert_component(broken,mirrored,np.zeros(3))

    def test_packaged_m4_geometry_matches_source_after_reassembly(self):
        source=ROOT/'modules/tacz_adapter/weapon-sources/m4a1'
        if not source.exists():self.skipTest('M4 snapshot not present')
        res=ROOT/'modules/tacz_adapter/weapon-content/resources'
        models={m['definitionId']:m for m in read(res/'assets/newmod_m4a1/m4a1/manifest.json')['models']}
        rows=read(source/'source-pack/manifest.json');by_number={r['number']:r for r in rows}
        origins={};root_anchor=np.asarray(read(ROOT/'modules/tacz_adapter/weapon-authoring/m4a1/import.json')['rootAnchor'])
        for row in rows:
            id=row['item_id'];model=models[id]
            if row['parent_number']:
                parent=by_number[row['parent_number']]['item_id'];origins[id]=origins[parent]+models[parent]['slots'][row['slot']]
            else:origins[id]=np.zeros(3)
            folder=source/'source-pack'/Path(row['model']).parent;meta=read(folder/'component.json');matrix=np.array(meta['local_to_assembly']);original=read(folder/'model.bbmodel')
            for mesh,raw in zip(model['meshes'],original['elements'],strict=True):
                self.assertEqual(len(mesh['triangles']),len(raw['faces']))
                for tri,face in zip(mesh['triangles'],raw['faces'].values(),strict=True):
                    expected=[(matrix@[*raw['vertices'][k],1])[:3]-root_anchor for k in face['vertices']]
                    np.testing.assert_allclose(np.asarray(tri['vertices'])+origins[id],expected,atol=2e-8)
        # A separate lower guard remains nested, not attached directly to the weapon.
        lower=next(r for r in rows if r['item_id']=='637f57a68d137b27f70c4968')
        self.assertEqual(by_number[lower['parent_number']]['item_id'],'5ae30db85acfc408fb139a05')

if __name__=='__main__':unittest.main()
