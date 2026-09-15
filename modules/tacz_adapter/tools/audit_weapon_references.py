"""Audit content-owned assets and explicit engine/domain references, without workspace fallback."""
import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import re
import zipfile

MODULE = Path(__file__).resolve().parents[1]
RESOURCE_ID = re.compile(r'[a-z0-9_.-]+:[a-z0-9_./-]+')


def read(path):
    return json.loads(path.read_text())


def audit(resources):
    resources = Path(resources)
    references = defaultdict(list)
    def visit(value, file, location=''):
        if isinstance(value, dict):
            for key, child in value.items():
                visit(child, file, location+'/'+key)
        elif isinstance(value, list):
            for i, child in enumerate(value):
                visit(child, file, location+'/'+str(i))
        elif isinstance(value, str) and RESOURCE_ID.fullmatch(value):
            references[value].append(str(file)+location)
    for file in sorted(resources.rglob('*.json')):
        visit(read(file), file.relative_to(resources))

    definitions = [read(resources/p) for p in read(resources/'data/tactical_tacz_adapter/assembled_weapons.json')['weapons']]
    namespaces = {d['gunId'].split(':')[0] for d in definitions}
    material_files = set()
    required_tacz = set()
    custom = 'assets/tacz/custom/tacz_default_gun/'
    for definition in definitions:
        ns, gun = definition['gunId'].split(':')
        assets = resources/'assets'/ns
        for material in read(assets/definition['resourceDirectory']/'library.json')['materials'].values():
            if material.get('texture'):
                relative = Path('assets')/material['texture'].replace(':','/')
                if not (resources/relative).is_file():
                    raise ValueError('Missing content texture '+material['texture'])
                material_files.add(str(relative))
        display = read(assets/'display/guns'/f'{gun}.json')
        for sound in display.get('sounds', {}).values():
            namespace, path = sound.split(':')
            if namespace != 'tacz':
                raise ValueError('Declare a verified sound owner for '+sound)
            required_tacz.add(custom+f'assets/tacz/tacz_sounds/{path}.ogg')
        flash = display.get('muzzle_flash', {}).get('texture')
        if flash:
            namespace, path = flash.split(':')
            if namespace != 'tacz':
                raise ValueError('Declare a verified muzzle flash owner for '+flash)
            required_tacz.add(custom+f'assets/tacz/textures/{path}.png')
        if display.get('use_default_animation'):
            required_tacz.add('assets/tacz/animations/'+display['use_default_animation']+'_default.animation.json')
        # Preserve existing ammunition routing; only verify the referenced data exists.
        ammo = read(resources/'data'/ns/'data/guns'/f'{gun}.json')['ammo']
        namespace, path = ammo.split(':')
        if namespace != 'tacz':
            raise ValueError('Declare a verified ammunition resource owner for '+ammo)
        required_tacz.add(custom+f'data/tacz/index/ammo/{path}.json')

    source_root = MODULE.parents[1]/'src/main/resources'
    missing = {name for name in required_tacz if not (source_root/name).is_file()}
    if missing:
        raise ValueError('Missing owned TaCZ resources: '+str(sorted(missing)))
    source_hashes = {name: hashlib.sha256((source_root/name).read_bytes()).hexdigest() for name in sorted(required_tacz)}

    inventory = []
    for reference, uses in sorted(references.items()):
        namespace, path = reference.split(':')
        if namespace in namespaces:
            owner = 'weapon-content: gun identity or local asset'
        elif (resources/'assets'/namespace/path).is_file():
            owner = 'weapon-content: shared material namespace'
        elif namespace == 'tacz':
            owner = 'TaCZ: execution, sound or effect contract'
        elif namespace == 'item_foundation':
            owner = 'foundation: item identity classification'
        elif namespace == 'tactical_inventory':
            owner = 'tactical: equipment slot contract'
        elif reference == 'minecraft:item/generated':
            owner = 'Minecraft: built-in item model parent'
        else:
            raise ValueError('Unowned external resource reference: '+reference)
        inventory.append({'reference':reference,'owner':owner,'uses':uses})
    return {'resources':str(resources), 'materialFiles':sorted(material_files),
            'materialSha256':{p:hashlib.sha256((resources/p).read_bytes()).hexdigest() for p in sorted(material_files)},
            'taCZSource':str(source_root), 'taCZResourceSha256':source_hashes,
            'verifiedTaCZResources':sorted(required_tacz), 'references':inventory,
            'note':'Materials resolve only within this content root. UI Java dependency is independent; no game was launched.'}


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resources',type=Path,default=MODULE/'weapon-content/resources')
    parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args(); report=audit(args.resources)
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print('Content materials:',len(report['materialFiles']),'; verified TaCZ resource paths:',len(report['verifiedTaCZResources']))
