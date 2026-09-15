"""Build an editable Blockbench COPY using only source white geometry and original tiles.

Regions become named meshes. Replacing appearances does not require this export;
edit basic.json / adar.json for runtime material changes.
"""
import base64
import copy
import json
import io
from PIL import Image
from pathlib import Path
import uuid
import import_adar as geometry

OUT = geometry.MODULE / 'material-authoring'


def main():
    materials = json.loads((geometry.MODULE / 'src/main/resources/assets/weapon_assembly_ui/materials/basic.json').read_text())['materials']
    bindings = json.loads((geometry.OUT / 'assets/weapon_assembly_ui/materials/adar.json').read_text())
    mappings = json.loads((geometry.DATA / 'model-mapping.json').read_text())['models']
    textures = []
    for name, material in materials.items():
        rgb = [int(material['baseColor'][i:i+2], 16)/255 for i in (1, 3, 5)]
        if material.get('texture'):
            namespace, resource = material['texture'].split(':', 1)
            candidates = [geometry.MODULE / f'src/{source}/resources/assets' / namespace / resource
                          for source in ('main', 'development')]
            path = next((p for p in candidates if p.is_file()), None)
            if path is None:
                raise ValueError(f'Missing material texture: {material["texture"]}')
            with Image.open(path) as image:
                preview = image.convert('RGBA')
            if preview.getextrema()[3] != (255, 255):
                raise ValueError('This material pipeline supports opaque textures only')
            preview = preview.convert('RGB')
        else:
            preview = Image.new('RGB', (16, 16), 'white')
        preview = Image.merge('RGB', tuple(channel.point([round(v * tint) for v in range(256)])
                                           for channel, tint in zip(preview.split(), rgb)))
        buffer = io.BytesIO()
        preview.save(buffer, format='PNG')
        width, height = preview.size
        textures.append({'name': name + '.png', 'id': str(len(textures)),
                         'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'newmod-material/' + name)),
                         'width': width, 'height': height, 'uv_width': width, 'uv_height': height,
                         'mode': 'bitmap', 'saved': False, 'source': 'data:image/png;base64,' + base64.b64encode(buffer.getvalue()).decode()})
    texture_indices = {name: i for i, name in enumerate(materials)}
    project = {'meta': {'format_version': '4.12', 'model_format': 'free'}, 'name': 'ADAR-original-materials',
               'resolution': {'width': 128, 'height': 128}, 'elements': [], 'outliner': [], 'textures': textures}
    for mapping in mappings:
        item_id = mapping['itemId']
        path = geometry.PACK / mapping['whiteModel']
        source = json.loads(path.read_text())
        rules = geometry.region_indices(path.parent, item_id)
        binding = bindings['parts'][item_id]
        group = {'name': mapping['modelLabel'], 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'newmod-material-group/' + item_id)), 'origin': [0, 0, 0], 'children': []}
        for element in source['elements']:
            if not element.get('visibility', True) or not element.get('export', True):
                continue
            regions = {}
            for key, face in element['faces'].items():
                region = geometry.region_for(element, face, rules)
                regions.setdefault(region, {})[key] = copy.deepcopy(face)
            for region, faces in regions.items():
                material_id = binding['regions'].get(region, binding['defaultMaterial'])
                scale = materials[material_id].get('textureScale', 1)
                e = copy.deepcopy(element)
                e['name'] = region
                e['uuid'] = str(uuid.uuid5(uuid.NAMESPACE_URL, f'newmod-material-region/{item_id}/{element["uuid"]}/{region}'))
                used = {v for f in faces.values() for v in f['vertices']}
                e['vertices'] = {k: v for k, v in element['vertices'].items() if k in used}
                for face in faces.values():
                    ordered = geometry.sorted_vertices(face, element['vertices'])
                    uv = geometry.face_uv(ordered, element['vertices'])
                    texture = textures[texture_indices[material_id]]
                    face['uv'] = {k: [uv[k][0] * texture['width'] * scale, uv[k][1] * texture['height'] * scale] for k in face['vertices']}
                    face['texture'] = texture_indices[material_id]
                e['faces'] = {element['uuid'] + '__' + key: face for key, face in faces.items()}
                project['elements'].append(e)
                group['children'].append(e['uuid'])
        project['outliner'].append(group)
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / 'ADAR-original-materials.bbmodel').write_text(json.dumps(project, ensure_ascii=False, separators=(',', ':')))
    print('Exported editable Blockbench copy:', len(project['elements']), 'regions')


if __name__ == '__main__':
    main()
