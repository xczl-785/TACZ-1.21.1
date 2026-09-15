"""Protect source directories and prove the review compares resource meaning."""
import json
from pathlib import Path
import tempfile
import unittest
from weapon_pipeline import AUTHORS, PACKAGED, compare, generate, write


class WeaponPipelineTest(unittest.TestCase):
    def test_rejects_production_and_nonempty_output_before_writing(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for protected in (PACKAGED, AUTHORS):
                with self.assertRaisesRegex(ValueError, 'overlaps'):
                    generate([], protected.resolve(), root/'reports')
            output = root/'resources'; output.mkdir()
            sentinel = output/'user-file'; sentinel.write_text('keep')
            with self.assertRaisesRegex(ValueError, 'empty output'):
                generate([], output, root/'reports')
            self.assertEqual(sentinel.read_text(), 'keep')

    def test_compare_preserves_array_order_and_checks_pixels(self):
        from PIL import Image
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); author = root/'author'
            write(author/'runtime.json', {'gunId':'example:gun','resourceDirectory':'gun'})
            a, b = root/'before', root/'after'
            index = Path('data/tactical_tacz_adapter/assembled_weapons.json')
            mesh = Path('assets/example/mesh.json'); icon = Path('assets/example/icon.png')
            for directory in (a,b):
                write(directory/index, {'weapons':['data/example/gun/weapon.json']})
                write(directory/mesh, {'vertices':[1,2,3]})
                Image.new('RGBA',(2,2),'red').save(directory/icon)
            (b/mesh).write_text('{"vertices": [1, 2, 3]}')
            self.assertFalse(compare(a,b,[author])['semanticDifferences'])
            write(b/mesh, {'vertices':[3,2,1]})
            Image.new('RGBA',(2,2),'blue').save(b/icon)
            self.assertEqual({d['path'] for d in compare(a,b,[author])['semanticDifferences']}, {str(mesh),str(icon)})

    def test_comparison_includes_shared_material_namespace(self):
        from PIL import Image
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); author=root/'author'
            write(author/'runtime.json', {'gunId':'example:gun','resourceDirectory':'gun'})
            a,b=root/'before',root/'after'
            shared=Path('assets/shared/textures/metal.png')
            for directory in (a,b):
                write(directory/'data/tactical_tacz_adapter/assembled_weapons.json', {'weapons':['data/example/gun/weapon.json']})
                write(directory/'assets/example/gun/library.json', {'materials':{'metal':{'texture':'shared:textures/metal.png'}}})
                (directory/shared).parent.mkdir(parents=True)
                Image.new('RGBA',(2,2),'red').save(directory/shared)
            (b/shared).unlink()
            differences=compare(a,b,[author])['semanticDifferences']
            self.assertEqual(differences,[{'path':str(shared),'kind':'missing'}])

    def test_isolated_validation_does_not_borrow_ui_or_old_resources(self):
        import shutil
        from validate_weapon_resources import validate
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)/'resources';shutil.copytree(PACKAGED,root)
            library=root/'assets/newmod_adar/adar/library.json'
            value=json.loads(library.read_text())
            # The real UI texture exists, but it must not satisfy this content build.
            value['materials']['walnut']['texture']='weapon_assembly_ui:textures/materials/wood.png'
            write(library,value)
            with self.assertRaisesRegex(ValueError,'Missing content texture weapon_assembly_ui'):
                validate(root)
            shutil.copy2(PACKAGED/'assets/newmod_adar/adar/library.json',library)
            (root/'assets/firearm_materials/textures/materials/steel_dark.png').unlink()
            with self.assertRaisesRegex(ValueError,'Missing content texture firearm_materials'):
                validate(root)
