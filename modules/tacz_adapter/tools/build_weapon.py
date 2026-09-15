"""Build a configured matrix-component gun pack. No firearm IDs or counts in code.

Usage: python3 build_weapon.py ../weapon-authoring/<gun>
Original input is read-only; all runtime paths derive from the recipe namespace.
"""
from pathlib import Path
import argparse
import hashlib
import json
import uuid
import numpy as np
from PIL import Image
from render_part_icon import render_part_icon

MODULE = Path(__file__).resolve().parents[1]
ROOT = MODULE.parents[1]


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')) + '\n')


def transform_point(matrix, point):
    m = np.asarray(matrix, dtype=float)
    if m.shape != (4, 4) or not np.isfinite(m).all() or not np.allclose(m[3], [0, 0, 0, 1]):
        raise ValueError('Expected a finite affine component matrix')
    return (m @ np.asarray([*point, 1.0]))[:3]


def convert_component(model, matrix, anchor):
    """Bake the entire affine transform into the shared art frame, preserving UV.

    r2 already bakes internal node transforms. Reject unsupported internal transforms
    instead of accidentally applying them twice. Mirrored transforms flip winding.
    """
    if model['meta']['model_format'] != 'free':
        raise ValueError('Expected free mesh component')
    seen = set()
    for group in model['outliner']:
        if isinstance(group, str):
            if group in seen:
                raise ValueError('Duplicate mesh in outliner')
            seen.add(group)
            continue
        if not isinstance(group, dict) or any(group.get('origin', [0, 0, 0])) or any(group.get('rotation', [0, 0, 0])):
            raise ValueError('Internal group transforms must be baked')
        if not all(isinstance(c, str) for c in group['children']) or not group.get('visibility', True):
            raise ValueError('Nested/hidden group requires an explicit source conversion')
        seen.update(group['children'])
    meshes = []
    resolution = model['resolution']
    width, height = resolution['width'], resolution['height']
    if width <= 0 or height <= 0:
        raise ValueError('Invalid UV dimensions')
    mirrored = np.linalg.det(np.asarray(matrix)[:3, :3]) < 0
    for element in model['elements']:
        if element['uuid'] not in seen:
            raise ValueError('Unassigned mesh')
        if element['type'] != 'mesh' or any(element.get('origin', [0, 0, 0])) or any(element.get('rotation', [0, 0, 0])):
            raise ValueError('Expected baked mesh with zero origin (r2 or later)')
        if not element.get('visibility', True) or not element.get('export', True):
            continue
        vertices = {key: transform_point(matrix, v) - anchor for key, v in element['vertices'].items()}
        triangles = []
        for face in element['faces'].values():
            keys = face['vertices']
            if len(keys) != 3:
                raise ValueError('Matrix component contract requires triangulated input')
            keys = list(reversed(keys)) if mirrored else keys
            uv = [[face['uv'][k][0] / width, face['uv'][k][1] / height] for k in keys]
            if not np.isfinite(uv).all():
                raise ValueError('Non-finite UV')
            triangles.append({'vertices': [vertices[k].round(8).tolist() for k in keys], 'uv': uv, 'region': element['name']})
        meshes.append({'name': element['name'], 'triangles': triangles})
    return meshes


def texture(material, seed, size=256):
    """Original restrained surface grain; tint is supplied once by the material."""
    rng = np.random.default_rng(seed)
    rough = float(material['roughness'])
    yy, xx = np.mgrid[:size, :size]
    fine = rng.normal(0, 1.8 + rough * 1.2, (size, size))
    # Several octaves avoid flat color while keeping wear subtle and tileable.
    cloud = np.zeros((size, size))
    for wavelength, amplitude in [(128, 2), (64, 1.2), (16, .6)]:
        cloud += amplitude * np.sin(xx * (2*np.pi/wavelength) + rng.uniform(0, 6.28)) * np.cos(yy*(2*np.pi/wavelength))
    brushed = (1-rough) * 3 * np.sin(yy * np.pi/2)
    values = np.clip(243 + fine + cloud + brushed, 225, 255).astype(np.uint8)
    return Image.fromarray(np.repeat(values[:, :, None], 3, axis=2))


def build(author, output, reports):
    OUT = Path(output)
    recipe, runtime = read(author/'import.json'), read(author/'runtime.json')
    ns, gun = runtime['gunId'].split(':')
    directory = runtime['resourceDirectory']
    assets, data = OUT/'assets'/ns, OUT/'data'/ns
    source = ROOT/recipe['source']; pack = source/'source-pack'
    rows = read(pack/'manifest.json'); raw = read(source/'items.raw.json')['data']['items']; names = read(source/'names.json')
    numbers = {str(r['number']): r for r in rows}
    root_id = runtime['rootDefinition']
    roots = [r for r in rows if not r['parent_number']]
    if len(roots) != 1 or roots[0]['item_id'] != root_id or len({r['item_id'] for r in rows}) != len(rows):
        raise ValueError('Expected one root and one model per definition')
    if set(recipe['identityTags']) != set(raw):
        raise ValueError('Every physical item needs a classification')
    original_preset = read(source/'preset.source.json')
    source_instances = {p['_id']: p for p in original_preset['parts']}
    if len(source_instances) != len(rows) or original_preset['weapon'] != root_id:
        raise ValueError('Preset and component count/root mismatch')
    library, bindings, presentation = read(author/'library.json'), read(author/'materials.json'), read(author/'presentation.json')
    models, parts, nodes, report = [], [], [], []
    anchors = {}
    components = {}
    for row in rows:
        id = row['item_id']; path = pack/row['model']
        if hashlib.sha256(path.read_bytes()).hexdigest() != row['sha256']:
            raise ValueError('Source model hash mismatch: '+id)
        meta = read(path.parent/'component.json'); model = read(path)
        if meta['item_id'] != id or meta['slot'] != row['slot'] or str(meta['parent']) != str(row['parent_number']):
            raise ValueError('Component metadata and manifest disagree')
        components[id] = (model, meta)
        anchors[id] = np.asarray(recipe['rootAnchor']) if id == root_id else transform_point(meta['local_to_assembly'], [0, 0, 0])
    ids = {r['item_id']: ns+':'+(gun if r['item_id'] == root_id else 'part_'+r['item_id']) for r in rows}
    for row in rows:
        id = row['item_id']; model, meta = components[id]; item = raw[id]; props = item['properties']
        instance = source_instances[row['instance']]
        if instance['_tpl'] != id or instance.get('slotId', '') != row['slot']:
            raise ValueError('Preset identity/slot mismatch')
        if row['parent_number']:
            parent = numbers[str(row['parent_number'])]
            if instance['parentId'] != parent['instance']:
                raise ValueError('Preset parent mismatch')
            slot = next(s for s in raw[parent['item_id']]['properties']['slots'] if s['nameId'] == row['slot'])
            if id not in slot['filters']['allowedItems'] or id in slot['filters'].get('excludedItems', []):
                raise ValueError('Preset edge forbidden by source data')
        if any(i in raw for i in item.get('conflictingItems', [])):
            raise ValueError('Conflicting preset')
        if item.get('conflictingCategories') or item.get('conflictingSlotIds'):
            raise ValueError('Unimplemented category/slot conflict semantics')
        slots, positions = [], {}
        for slot in props.get('slots', []):
            filt = slot['filters']
            if filt.get('allowedCategories') or filt.get('excludedCategories'):
                raise ValueError('Category filters need explicit expansion')
            children = [r for r in rows if str(r['parent_number']) == str(row['number']) and r['slot'] == slot['nameId']]
            if slot.get('required') and not children:
                raise ValueError('Preset missing required slot '+slot['nameId'])
            positions[slot['nameId']] = (anchors[children[0]['item_id']] - anchors[id]).round(8).tolist() if children else [0, 0, 0]
            slots.append({'id': slot['nameId'], 'required': slot.get('required', False), 'allowedParts': [i for i in filt['allowedItems'] if i in ids and i not in filt.get('excludedItems', [])]})
        meshes = convert_component(model, meta['local_to_assembly'], anchors[id])
        # Optional authored regions use bounds in the common assembly frame.
        for rule in recipe.get('regionRules', {}).get(id, []):
            low, high = np.asarray(rule['min']), np.asarray(rule['max'])
            for mesh in meshes:
                for triangle in mesh['triangles']:
                    center = np.mean(triangle['vertices'], axis=0) + anchors[id]
                    if np.all(center >= low) and np.all(center <= high):
                        triangle['region'] = rule['name']
        models.append({'definitionId': id, 'name': names[id]['en'], 'attachmentOrigin': [0, 0, 0], 'slots': positions, 'boxes': [], 'meshes': meshes})
        stats = {'weightKg': item['weight'], 'ergonomics': props.get('ergonomics', 0) if id == root_id else item.get('ergonomicsModifier', 0)}
        if id != root_id:
            stats['recoilFraction'] = props.get('recoilModifier') or 0
        if props.get('accuracyModifier') is not None:
            stats['accuracyPercent'] = props['accuracyModifier'] * 100
        if item.get('velocity') is not None:
            stats['velocityPercent'] = item['velocity']
        for key in ['heatFactor', 'coolingFactor', 'durabilityBurnFactor'] + ([] if id == root_id else ['centerOfImpact', 'sightingRange']):
            if props.get(key) is not None:
                stats[key] = props[key]
        part = {'id': id, 'stats': stats, 'slots': slots, 'conflictingParts': [i for i in item.get('conflictingItems', []) if i in raw]}
        if id == root_id:
            part['weapon'] = {k: props[k] for k in ['recoilVertical', 'recoilHorizontal', 'centerOfImpact', 'sightingRange'] if k in props}
        parts.append(part)
        uid = lambda value: str(uuid.uuid5(uuid.NAMESPACE_URL, runtime['gunId']+'/'+value))
        node = {'instanceId': uid(row['instance']), 'definitionId': id}
        if row['parent_number']:
            node.update(parentId=uid(numbers[str(row['parent_number'])]['instance']), slot=row['slot'])
        nodes.append(node)
        report.append({'itemId': id, 'sourceHash': row['sha256'], 'localToAssembly': meta['local_to_assembly'], 'anchor': anchors[id].tolist(), 'meshes': len(meshes), 'triangles': sum(len(m['triangles']) for m in meshes)})
    for i, (key, material) in enumerate(library['materials'].items()):
        if key in recipe.get('textureInputs', {}):
            entry = recipe['textureInputs'][key]
            original = source/entry['path']
            if hashlib.sha256(original.read_bytes()).hexdigest() != entry['sha256']:
                raise ValueError('Material source hash mismatch: '+key)
            texture_ns, texture_path = material['texture'].split(':')
            path = OUT/'assets'/texture_ns/texture_path
            path.parent.mkdir(parents=True, exist_ok=True)
            with Image.open(original) as img:
                img.convert('RGB').resize((256, 256), Image.Resampling.NEAREST).save(path)
        else:
            path = assets/'textures/materials'/f'{key}.png'; path.parent.mkdir(parents=True, exist_ok=True)
            texture(material, recipe['materialSeed']+i).save(path)
    write(assets/directory/'manifest.json', {'schemaVersion': 3, 'models': models})
    write(assets/directory/'library.json', library); write(assets/directory/'materials.json', bindings)
    write(data/directory/'catalog.json', {'schemaVersion': 1, 'parts': parts})
    write(data/directory/'scene.json', {'schemaVersion': 1, 'nodes': nodes}); write(data/directory/'mapping.json', ids)
    write(data/'assembly'/f'{gun}.json', {'schemaVersion': 1, 'items': [{'itemId': ids[p['id']], 'slots': [{'id': s['id'], 'compatibleItems': [ids[i] for i in s['allowedParts']], 'requiredSiblingSlots': [], 'conflictingSiblingSlots': [], 'toggleable': False} for s in p['slots'] if s['allowedParts']]} for p in parts]})
    write(data/'item_foundation/identities'/f'{gun}.json', {'schema_version': 1, 'items': [{'item': ids[id], 'tags': ['item_foundation:type/'+tag]} for id, tag in recipe['identityTags'].items()]})
    for model in models:
        id = model['definitionId']; path = ids[id].split(':')[1]; item = raw[id]
        write(data/'item_foundation/items'/f'{path}.json', {'schema_version': 3, 'item': ids[id], 'footprint': recipe['footprint'] if id == root_id else [item['width'], item['height']], 'weight_kg': item['weight'], **({'wearable_slots': ['tactical_inventory:primary_weapon_1', 'tactical_inventory:primary_weapon_2']} if id == root_id else {})})
        write(assets/'models/item'/f'{path}.json', {'parent': 'builtin/entity'} if id == root_id else {'parent': 'minecraft:item/generated', 'textures': {'layer0': ns+':item/'+path}})
        icon = render_part_icon(model, library, bindings, lambda res: OUT/'assets'/res.replace(':', '/'), size=128)
        target = assets/'textures/item'/f'{path}.png'; target.parent.mkdir(parents=True, exist_ok=True); icon.save(target)
    for locale, source_locale in [('zh_cn', 'zh'), ('en_us', 'en')]:
        strings = {'item.'+ids[id].replace(':', '.'): names[id][source_locale] for id in ids}
        strings['gun.'+runtime['gunId'].replace(':', '.')] = names[root_id][source_locale]
        write(assets/'lang'/f'{locale}.json', strings)
    write(data/'index/guns'/f'{gun}.json', {'name': 'gun.'+runtime['gunId'].replace(':', '.'), 'display': runtime['gunId'], 'data': runtime['gunId'], 'type': 'rifle', 'item_type': runtime['gunId'], 'sort': 101})
    execution = read(author/'execution.json')
    if execution['ammo_amount'] != recipe['capacity'] or execution['fire_mode'] != recipe['fireModes'] or execution['rpm'] != recipe['rpm']:
        raise ValueError('Execution config and source recipe disagree')
    write(data/'data/guns'/f'{gun}.json', execution)
    bones = [{'name': 'root', 'pivot': [0, 8, 0]}]
    for model in models:
        parent = next((bone for bone, definitions in recipe['boneParts'].items() if model['definitionId'] in definitions), 'root')
        bones.append({'name': 'part_'+model['definitionId'], 'parent': parent, 'pivot': [0, 8, 0]})
    for bone in recipe['boneParts']:
        bones.append({'name': bone, 'parent': 'root', 'pivot': [0, 8, 0]})
    for name, parent, pivot in [('lefthand', 'root', presentation['leftHand']['pivot']), ('lefthand_pos', 'lefthand', presentation['leftHand']['rendererPivot']), ('righthand', 'root', presentation['rightHand']['pivot']), ('righthand_pos', 'righthand', presentation['rightHand']['rendererPivot']), ('muzzle_flash', 'root', recipe['muzzlePosition']), ('shell', 'root', [-1, 8, -2]), ('idle_view', None, presentation['idleView']), ('iron_view', None, presentation['aimView']), ('refit_view', None, [15, 12, 4]), ('thirdperson_hand', None, [0, 6, 1]), ('ground', None, [0, 5, -5]), ('fixed', None, [0, 8, -5])]:
        bones.append({'name': name, 'pivot': pivot, **({'parent': parent} if parent else {})})
    write(assets/'geo_models/gun'/f'{gun}.json', {'format_version': '1.12.0', 'minecraft:geometry': [{'description': {'identifier': 'geometry.'+ns, 'texture_width': 16, 'texture_height': 16, 'visible_bounds_width': 4, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, .5, 0]}, 'bones': bones}]})
    animations = read(author/'animations.json')
    animations['animations']['static_idle']['bones'] = {'lefthand': {'rotation': presentation['leftHand']['rotation']}, 'righthand': {'rotation': presentation['rightHand']['rotation']}}
    write(assets/'animations'/f'{gun}.animation.json', animations)
    white = assets/'textures/gun/white.png'; white.parent.mkdir(parents=True, exist_ok=True); Image.new('RGB', (16, 16), 'white').save(white)
    display = read(author/'display.json')
    display.update(model_type=runtime['modelType'], model=ns+':gun/'+gun, texture=ns+':gun/white', slot=ns+':item/'+gun, hud=ns+':item/'+gun, animation=runtime['gunId'], zoom_model_fov=presentation['aimModelFov'])
    write(assets/'display/guns'/f'{gun}.json', display)
    write(Path(reports)/author.name/'import-report.json', {'parts': report, 'triangles': sum(p['triangles'] for p in report), 'preset': original_preset['preset'], 'scope': 'Full matrix baked; original UV preserved; selected model catalog only; authored opaque material sources.', 'textureInputs': recipe.get('textureInputs', {})})
    from build_presentation import build as build_presentation
    build_presentation(author, OUT)
    print('Generated', runtime['gunId'], len(parts), 'definitions;', sum(p['triangles'] for p in report), 'triangles', flush=True)


if __name__ == '__main__':
    from weapon_pipeline import main
    main(legacy_author=True)
