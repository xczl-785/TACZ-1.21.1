"""Build-time content checks; no Minecraft process or world is started."""
from pathlib import Path
import json, hashlib, zipfile

MODULE = Path(__file__).resolve().parents[1]
RESOURCES = MODULE/'weapon-content/resources'


def read(path):
    return json.loads(path.read_text())


def validate(resources=RESOURCES):
    RESOURCES = Path(resources)
    root = MODULE.parents[1]
    lock = read(root/'modules/public-dependency-lock.json')
    artifact = root/lock['artifact']
    assert hashlib.sha512(artifact.read_bytes()).hexdigest() == lock['sha512'], 'Foundation artifact mismatch'
    with zipfile.ZipFile(artifact) as jar:
        tags = {tag['id'] for name in jar.namelist() if name.startswith('data/item_foundation/item_foundation/identities/catalog/') and name.endswith('.json') for tag in json.loads(jar.read(name))['tags']}
    index = read(RESOURCES/'data/tactical_tacz_adapter/assembled_weapons.json')
    seen_guns, seen_items, seen_types = set(), set(), set()
    for path in index['weapons']:
        weapon = read(RESOURCES/path); gun = weapon['gunId']; namespace, name = gun.split(':'); directory = weapon['resourceDirectory']
        if gun in seen_guns or weapon['modelType'] in seen_types:
            raise ValueError('Duplicate gun/model registration: '+gun)
        seen_guns.add(gun); seen_types.add(weapon['modelType'])
        data, assets = RESOURCES/'data'/namespace, RESOURCES/'assets'/namespace
        mapping = read(data/directory/'mapping.json'); root = weapon['rootDefinition']
        if mapping[root] != gun:
            raise ValueError('Root identity differs from gun ID')
        catalog = {part['id']: part for part in read(data/directory/'catalog.json')['parts']}
        if set(mapping) != set(catalog):
            raise ValueError('Catalog and physical mappings disagree')
        nodes = read(data/directory/'scene.json')['nodes']; by_instance = {n['instanceId']: n for n in nodes}
        if len(by_instance) != len(nodes):
            raise ValueError('Duplicate preset instance')
        roots = [n for n in nodes if 'parentId' not in n]
        if len(roots) != 1 or roots[0]['definitionId'] != root:
            raise ValueError('Invalid preset root')
        for node in nodes:
            definition = node['definitionId']
            if definition not in catalog:
                raise ValueError('Preset definition missing')
            if 'parentId' in node:
                parent = by_instance[node['parentId']]
                slot = next(s for s in catalog[parent['definitionId']]['slots'] if s['id'] == node['slot'])
                if definition not in slot['allowedParts']:
                    raise ValueError('Incompatible preset edge')
        for node in nodes:
            occupied = {n['slot'] for n in nodes if n.get('parentId') == node['instanceId']}
            if not {s['id'] for s in catalog[node['definitionId']]['slots'] if s['required']}.issubset(occupied):
                raise ValueError('Preset lacks required components')
        if weapon.get('nativeRig'):
            import runpy
            if weapon['authoringSource'] == 'native_m4a1':
                runpy.run_path(str(MODULE.parents[1]/'tools/native_m4a1/validate.py'))['validate'](RESOURCES)
            else:
                runpy.run_path(str(MODULE.parents[1]/'tools/native_guns/validate.py'))['validate'](RESOURCES, weapon)
            slots = weapon.get('wearableSlots', ['tactical_inventory:primary_weapon_1', 'tactical_inventory:primary_weapon_2'])
            assert slots and len(slots) == len(set(slots)), 'Invalid equipment qualifications'
            assert read(data/'item_foundation/items'/f'{name}.json')['wearable_slots'] == slots, 'Equipment config and physical definition disagree'
            external=read(data/directory/'native_attachments.json')
            identity_rows=read(data/'item_foundation/identities'/f'{name}.json')['items']
            native_identities={row['item']:row['tags'] for row in identity_rows}
            for physical_item in mapping.values():
                if physical_item not in external:
                    declared=native_identities.get(physical_item,[])
                    assert declared and set(declared)<=tags, 'Missing/unknown native inventory identity: '+physical_item+' '+str(declared)
            for item in mapping.values():
                if item not in external:
                    assert item not in seen_items
                    seen_items.add(item)
            continue
        geometry = {m['definitionId']: m for m in read(assets/directory/'manifest.json')['models']}
        from validate_presentation import validate as validate_presentation
        validate_presentation(weapon,assets,data,geometry)
        library = read(assets/directory/'library.json')['materials']; bindings = read(assets/directory/'materials.json')
        if set(geometry) != set(mapping):
            raise ValueError('Missing per-part geometry')
        for material in library.values():
            if material.get('texture'):
                ns, resource = material['texture'].split(':')
                # Gun materials belong to this generated content tree, including shared namespaces.
                # Never hide an incomplete isolated build by searching UI or old module outputs.
                if not (RESOURCES/'assets'/ns/resource).is_file():
                    raise ValueError('Missing content texture '+material['texture'])
        for definition, binding in bindings['parts'].items():
            regions = {t['region'] for mesh in geometry[definition]['meshes'] for t in mesh['triangles']}
            if binding['defaultMaterial'] not in library or not set(binding['regions']).issubset(regions) or any(m not in library for m in binding['regions'].values()):
                raise ValueError('Unknown material binding')
        identities = {entry['item']: entry['tags'] for p in (data/'item_foundation/identities').glob('*.json') for entry in read(p)['items']}
        locales = [read(assets/'lang'/f'{lang}.json') for lang in ['zh_cn','en_us']]
        for item in mapping.values():
            if item in seen_items:
                raise ValueError('Duplicate registered physical item '+item)
            seen_items.add(item)
            if not identities.get(item) or not set(identities[item]).issubset(tags):
                raise ValueError('Missing/unknown inventory identity '+item)
            item_path = item.split(':')[1]
            read(data/'item_foundation/items'/f'{item_path}.json')
            read(assets/'models/item'/f'{item_path}.json')
            key = 'item.'+item.replace(':','.')
            if any(not locale.get(key) or locale[key] == key for locale in locales):
                raise ValueError('Missing official translation '+item)
        display = read(assets/'display/guns'/f'{name}.json'); execution = read(data/'data/guns'/f'{name}.json'); entry = read(data/'index/guns'/f'{name}.json')
        if display['model_type'] != weapon['modelType'] or entry['item_type'] != weapon.get('itemType',gun) or weapon['defaultFireMode'] not in execution['fire_mode']:
            raise ValueError('Model/item/fire mode routing mismatch')
        for field, folder, suffix in [('model','geo_models','.json'),('animation','animations','.animation.json'),('texture','textures','.png'),('slot','textures','.png'),('hud','textures','.png')]:
            ns, asset = display[field].split(':')
            if not (RESOURCES/'assets'/ns/folder/(asset+suffix)).is_file():
                raise ValueError('Missing display resource '+display[field])
    print(f'Configured weapons: {len(seen_guns)}; physical items: {len(seen_items)}; names, preset, material and registration resources valid')


if __name__ == '__main__':
    import argparse
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--resources',type=Path,default=RESOURCES)
    validate(parser.parse_args().resources)
