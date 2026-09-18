"""UV-preserving baked mesh conversion shared by the current M4 authoring path.
Extracted from the retired legacy gun producer; no legacy content dependency.
"""
import numpy as np


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

