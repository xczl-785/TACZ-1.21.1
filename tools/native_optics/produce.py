"""Compile complete editable optics, including functional bones, as new native IDs.

The native attachment index owns both rendering and ADS. Never substitute only
the exterior through a gun's non-optical attachment override table.
"""
import argparse
import base64
import copy
import json
from pathlib import Path
import sys

import numpy as np

R = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(R / 'tools/native_attachments'))
import extract as shared

ex = shared.im.ex
OUT = R / 'modules/tacz_adapter/weapon-content/resources'
SOURCES = R / 'modules/tacz_adapter/weapon-sources/optics'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def compile_optic(source, output=OUT):
    source, output = Path(source), Path(output)
    config = ex.read(source / 'optic.json')
    namespace, key = config['attachmentId'].split(':')
    if namespace != 'tacz_fork_tarkov':
        raise ValueError('Authored optics must have their own fork identity')
    row = ex.read(source / 'editable/manifest.json')['parts'][0]
    _, bones, uv, _, count = shared.load_part(row, source / 'editable')
    if not {'scope_body', 'scope_view', 'division', 'ocular'} <= bones.keys():
        raise ValueError('Missing optical functional bones')
    geometry = copy.deepcopy(ex.read(R / row['sourceGeometry']))
    geo = geometry['minecraft:geometry'][0]
    geo['description'].update(identifier='geometry.' + key, texture_width=uv[0], texture_height=uv[1])
    geo['bones'] = list(bones.values())
    assets = output / 'assets' / namespace
    data = output / 'data' / namespace
    write(assets / 'geo_models/attachment' / (key + '.json'), geometry)
    texture_path = assets / 'textures/attachment' / (key + '.png')
    texture_path.parent.mkdir(parents=True, exist_ok=True)
    texture_path.write_bytes((source / 'editable' / row['texture']).read_bytes())
    for suffix in ('_n', '_s'):
        extra = source / 'editable' / Path(row['texture']).with_name('texture' + suffix + '.png')
        if extra.exists():
            texture_path.with_stem(key + suffix).write_bytes(extra.read_bytes())
    display = ex.read(source / 'display.json')
    display.update(model=f'{namespace}:attachment/{key}', texture=f'{namespace}:attachment/{key}')
    # Sample LOD is independently retained from its source, not guessed from
    # optical stencil/reticle geometry. First person always uses the full model.
    lod = config['lod']
    write(assets / 'geo_models/attachment/lod' / (key + '.json'), ex.read(source / lod['model']))
    low_texture = assets / 'textures/attachment/lod' / (key + '.png')
    low_texture.parent.mkdir(parents=True, exist_ok=True)
    low_texture.write_bytes((source / lod['texture']).read_bytes())
    display['lod'] = {'model': f'{namespace}:attachment/lod/{key}', 'texture': f'{namespace}:attachment/lod/{key}'}
    display['slot'] = f'{namespace}:attachment/slot/{key}'
    slot = assets / 'textures/attachment/slot' / (key + '.png')
    slot.parent.mkdir(parents=True, exist_ok=True)
    slot.write_bytes((source / 'slot.png').read_bytes())
    write(assets / 'display/attachments' / (key + '.json'), display)
    write(data / 'data/attachments' / (key + '.json'), ex.read(source / 'data.json'))
    label = f'attachment.{namespace}.{key}'
    write(data / 'index/attachments' / (key + '.json'), {
        'name': label, 'display': config['attachmentId'], 'data': config['attachmentId'], 'type': 'scope', 'sort': 110})
    for locale, name in config['names'].items():
        path = assets / 'lang' / (locale + '.json')
        lang = ex.read(path) if path.exists() else {}
        lang[label] = name
        write(path, lang)
    sources = [p for p in source.rglob('*') if p.is_file() and p.suffix in {'.json', '.bbmodel', '.png'}]
    write(data / 'optics' / (key + '.json'), {
        'schemaVersion': 1, 'attachmentId': config['attachmentId'], 'cubes': count,
        'sources': {str(p.relative_to(R)): ex.sha(p) for p in sorted(sources)},
        'policy': 'Complete optical model compiled from editable cubes; native index owns ADS and rendering; originals preserved'})
    return config, row, bones, display, count


def build_for_gun(author, output=OUT):
    """Explicit opt-in; the gun still authors compatibility and installation frame."""
    author = Path(author)
    config_path = author / 'optics.json'
    if not config_path.exists():
        return []
    gun_config = ex.read(config_path)
    entries = gun_config['optics']
    gun_bones = {b['name']: b for b in ex.read(R / gun_config['nativeGunModel'])['minecraft:geometry'][0]['bones']}
    manifest = ex.read(author / 'manifest.json')
    mounts = ex.read(author / 'mounts.json')['parts']
    result = []
    for entry in entries:
        source = R / entry['source']
        config, row, bones, display, count = compile_optic(source, output)
        definition = entry['definitionId']
        frame = mounts[definition]
        native_mount = ex.matrix(entry['nativeMount'], gun_bones)
        # Exterior preview only. Reticle and stencil geometry remain intact in
        # the runtime model, but must never inflate workbench fitting or icons.
        vertices, faces = {}, {}
        for bone in bones.values():
            if 'scope_body' not in ex.ancestors(bone['name'], bones) and 'ocular_ring' not in ex.ancestors(bone['name'], bones):
                continue
            for cube in bone.get('cubes', []):
                points, triangles = ex.cube_geometry(bone, cube, bones, native_mount)
                offset = len(vertices)
                for i, point in enumerate(points):
                    vertices[str(offset + i)] = (point - frame['frameOrigin']).tolist()
                for indices, uv in triangles:
                    keys = [str(offset + i) for i in indices]
                    faces[str(len(faces))] = {'vertices': keys, 'uv': dict(zip(keys, uv)), 'texture': 0}
        if not vertices:
            raise ValueError('No optical exterior geometry')
        mesh = {'name': 'exterior', 'type': 'mesh', 'uuid': ex.uid(definition), 'origin': [0, 0, 0], 'rotation': [0, 0, 0], 'vertices': vertices, 'faces': faces}
        texture = (source / 'editable' / row['texture']).read_bytes()
        uv = row['uvSize']
        model = {'meta': {'format_version': '5.0', 'model_format': 'free', 'box_uv': False}, 'name': definition,
                 'resolution': {'width': uv[0], 'height': uv[1]}, 'elements': [mesh], 'outliner': [mesh['uuid']],
                 'textures': [{'id': '0', 'name': 'texture.png', 'uuid': ex.uid(definition + '/texture'), 'source': 'data:image/png;base64,' + base64.b64encode(texture).decode(), 'uv_width': uv[0], 'uv_height': uv[1], 'internal': True}]}
        relative = Path('source-pack/components') / definition
        write(author / relative / 'model.bbmodel', model)
        (author / relative / 'texture.png').write_bytes(texture)
        matrix = np.eye(4)
        matrix[:3, 3] = frame['frameOrigin']
        part = {'definitionId': definition, 'itemId': config['attachmentId'], 'variant': 'default',
                'model': str(relative / 'model.bbmodel'), 'texture': str(relative / 'texture.png'),
                'anchor': frame['frameOrigin'], 'local_to_assembly': matrix.tolist(), 'slots': {}, 'anchorBone': entry['nativeMount'],
                'modelSha256': ex.sha(author / relative / 'model.bbmodel'), 'editableSource': str(source.relative_to(R)),
                'meshCount': 1, 'triangles': len(faces), 'neutralOnly': True}
        manifest['parts'] = [p for p in manifest['parts'] if p['definitionId'] != definition] + [part]
        result.append({'id': config['attachmentId'], 'type': 'scope', 'tagAllowed': True, 'display': display})
    write(author / 'manifest.json', manifest)
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('sample')
    args = parser.parse_args()
    print(compile_optic(SOURCES / args.sample)[0]['attachmentId'])
