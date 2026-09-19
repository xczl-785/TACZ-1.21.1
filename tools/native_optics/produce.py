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
import re
import math

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


def validate_optics(bones, display, config):
    # Match BedrockAttachmentModel's numbered view/division and typed ocular nodes.
    for root in ('scope_view', 'division'):
        names={name for name in bones if re.fullmatch(root+r'(_(?:[2-9]|[1-9][0-9]+))?',name)}
        expected={root}|{root+'_'+str(i) for i in range(2,len(names)+1)}
        if not names or names!=expected:raise ValueError('Missing/non-contiguous optical nodes: '+root)
    ocular={}
    for name in bones:
        match=re.fullmatch(r'(ocular|ocular_sight|ocular_scope)(?:_([0-9]+))?',name)
        if match:
            index=int(match[2] or 1)
            if index<1 or index in ocular:raise ValueError('Ambiguous ocular index')
            ocular[index]=name
    if not ocular or set(ocular)!=set(range(1,len(ocular)+1)):raise ValueError('Missing/non-contiguous ocular nodes')
    if display.get('scope') and display.get('sight'):
        if not any(n.startswith('ocular_scope') for n in ocular.values()) or not any(n.startswith('ocular_sight') for n in ocular.values()):
            raise ValueError('Hybrid optic needs scope and sight apertures')
    for name in [n for n in bones if re.fullmatch(r'division(_(?:[2-9]|[1-9][0-9]+))?',n)]+list(ocular.values()):
        if not any(b.get('cubes') and name in ex.ancestors(b['name'],bones) for b in bones.values()):raise ValueError('Empty optical surface: '+name)
    roots=config.get('exteriorRoots',[n for n in ('scope_body','ocular_ring') if n in bones])
    if not roots or not set(roots)<=bones.keys():raise ValueError('Missing optical exterior roots')
    zoom=display.get('zoom',[])
    if not zoom or any(type(z) not in (int,float) or not math.isfinite(z) or z<1 for z in zoom):raise ValueError('Invalid optic zoom')
    if not (display.get('scope') or display.get('sight')):raise ValueError('Missing optic type')
    views=display.get('views',[1]*len(zoom))
    if len(views)!=len(zoom) or any(type(v) is not int or v<1 for v in views):raise ValueError('Invalid view mode mapping')
    # Native renderer intentionally falls back to scope_view when a view is out of range.
    count=sum(bool(re.fullmatch(r'scope_view(_(?:[2-9]|[1-9][0-9]+))?',n)) for n in bones)
    return {'views':[('scope_view' if v==1 or v>count else 'scope_view_'+str(v)) for v in views], 'oculars':list(ocular.values()),'exteriorRoots':roots}


def compile_optic(source, output=OUT):
    source, output = Path(source), Path(output)
    config = ex.read(source / 'optic.json')
    namespace, key = config['attachmentId'].split(':')
    if namespace != 'tacz_fork_tarkov':
        raise ValueError('Authored optics must have their own fork identity')
    row = ex.read(source / 'editable/manifest.json')['parts'][0]
    _, bones, uv, _, count = shared.load_part(row, source / 'editable')
    display = ex.read(source / 'display.json')
    validate_optics(bones, display, config)
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
        roots=validate_optics(bones,display,config)['exteriorRoots']
        for bone in bones.values():
            if not set(roots).intersection(ex.ancestors(bone['name'], bones)):
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
