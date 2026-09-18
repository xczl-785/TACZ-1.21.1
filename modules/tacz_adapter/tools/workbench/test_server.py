import importlib.util,json,unittest
from pathlib import Path

HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('tacz_workbench_server',HERE/'server.py')
server=importlib.util.module_from_spec(spec);spec.loader.exec_module(server)

class WorkbenchLayoutTest(unittest.TestCase):
    def test_entrypoints_and_ui_are_local(self):
        self.assertTrue((server.PROJECT/'启动TaCZ枪械检查工作台.command').is_file())
        html=(HERE/'index.html').read_text()
        self.assertIn('TaCZ 枪械检查工作台',html)
        self.assertNotIn('http://',html)
        self.assertNotIn('https://',html)
    def test_native_resource_contract_is_complete(self):
        root=server.PROJECT/'modules/tacz_adapter/weapon-content/resources/data/tacz_fork_tarkov'
        weapons=list(root.glob('*/weapon.json'));self.assertGreaterEqual(len(weapons),15)
        for weapon in weapons:
            data=json.loads(weapon.read_text())
            if not data.get('nativeRig'):continue
            for name in ('catalog.json','scene.json','mapping.json','preview.json','materials.json','library.json','native-profile.json'):
                self.assertTrue((weapon.parent/name).is_file(),f'{weapon.parent.name}/{name}')

if __name__=='__main__':unittest.main()
