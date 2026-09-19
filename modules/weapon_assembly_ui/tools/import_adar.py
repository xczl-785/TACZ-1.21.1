"""Offline import of the supplied static Blockbench mesh pack. Originals are read-only.

This pack has identity group/mesh transforms. Reject other transforms rather than
silently flattening them incorrectly. The runtime geometry format is vendor-independent.
"""
import os
import hashlib
import json
from pathlib import Path
import sys
import uuid

MODULE = Path(__file__).resolve().parents[1]
ROOT = Path(os.environ.get("NEWMOD_ROOT", str(MODULE.parents[1].parent)))
DATA = ROOT / 'docs/参考资料/adar-20260913-data'
OUT = MODULE / 'src/development/resources'
PACK = Path(os.environ.get('ADAR_MODEL_PACK', str(ROOT.parent / 'adar-20260913')))
# Authored in the pack's common art space; these are visual attachment references,
# not real dimensions, animation pivots, or compatibility rules.
ANCHORS = [[0, 0, 30], [0, -2, 23], [0, -.5, 36], [0, 0, 30],
           [0, 0, 40], [0, 0, 79], [0, 0, 67], [0, 0, 42],
           [0, 0, 22], [0, 2, 24], [0, 3, 35]]


def sub(a, b):
    return [a[i] - b[i] for i in range(3)]


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def face_uv(vertices, positions):
    a, b, c = (positions[v] for v in vertices[:3])
    u, v = sub(b, a), sub(c, a)
    normal = [u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0]]
    axis = max(range(3), key=lambda i: abs(normal[i]))
    # Original box projection, aligned with the longitudinal Z axis. No source UVs used.
    return {key: ([positions[key][2]/24, positions[key][1]/8] if axis == 0 else
                  [positions[key][2]/24, positions[key][0]/8] if axis == 1 else
                  [positions[key][0]/8, positions[key][1]/8]) for key in vertices}


def region_indices(component, item_id):
    rules = json.loads((MODULE / 'tools/adar-regions.json').read_text())['parts'].get(item_id, {})
    merge = json.loads((component / 'merge-map.json').read_text())
    result = {}
    for mesh, names in rules.items():
        record = next(m for m in merge if m['mesh'] == mesh)
        found = set()
        for i, source in enumerate(record['source_elements']):
            if source['name'] in names:
                result[(mesh, f'e{i}')] = names[source['name']]
                found.add(source['name'])
        assert found == set(names), f'Unknown source regions: {set(names) - found}'
    return result


def region_for(element, face, rules):
    prefixes = {key.split('_')[0] for key in face['vertices']}
    assert len(prefixes) == 1, 'Merged face crosses source parts'
    return rules.get((element['name'], next(iter(prefixes))), element['name'])


def sorted_vertices(face, vertices):
    # Blockbench MeshFace.getSortedVertices: quad vertex arrays need not be cyclic.
    n = face['vertices']
    if len(n) != 4:
        assert len(n) == 3, 'Only triangle and quad faces supported'
        return n

    def opposite(a, b, c, d):
        a, b, c, d = (vertices[k] for k in (a, b, c, d))
        ab = sub(b, a)
        denom = dot(ab, ab)
        if denom == 0:
            return False
        t = dot(sub(c, a), ab) / denom
        normal = sub([a[i] + ab[i] * t for i in range(3)], c)
        return dot(normal, sub(d, b)) > 0

    if opposite(n[1], n[2], n[0], n[3]):
        return [n[2], n[0], n[1], n[3]]
    if opposite(n[0], n[1], n[2], n[3]):
        return [n[0], n[2], n[1], n[3]]
    return n


def write(relative, value):
    path = OUT / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n')


def main():
    global PACK
    if len(sys.argv)>1:
        PACK=Path(sys.argv[1])
    mappings = json.loads((DATA / 'model-mapping.json').read_text())['models']
    items = json.loads((DATA / 'items.raw.json').read_text())['data']['items']
    anchors = {m['itemId']: anchor for m, anchor in zip(mappings, ANCHORS)}
    models, parts, nodes, evidence = [], [], [], []
    for m in mappings:
        item_id = m['itemId']
        anchor = anchors[item_id]
        path = PACK / m['whiteModel']
        raw = path.read_bytes()
        assert hashlib.sha256(raw).hexdigest() == m['modelSha256']['white_file'], 'Re-extract changed model pack first'
        model = json.loads(raw)
        region_rules = region_indices(path.parent, item_id)
        assert model['meta']['model_format'] == 'free'
        for group in model['outliner']:
            assert group.get('origin', [0, 0, 0]) == [0, 0, 0]
            assert group.get('rotation', [0, 0, 0]) == [0, 0, 0]
            assert all(isinstance(child, str) for child in group['children']), 'Nested groups not supported by this pack importer'
            assert group.get('visibility', True)
        meshes = []
        source_faces = 0
        for element in model['elements']:
            assert element['type'] == 'mesh'
            assert element.get('origin', [0, 0, 0]) == [0, 0, 0]
            assert element.get('rotation', [0, 0, 0]) == [0, 0, 0]
            if not element.get('visibility', True) or not element.get('export', True):
                continue
            triangles = []
            for face in element['faces'].values():
                vertices = sorted_vertices(face, element['vertices'])
                uv = face_uv(vertices, element['vertices'])
                region = region_for(element, face, region_rules)
                source_faces += 1
                for indices in ([0, 1, 2], [0, 2, 3]) if len(vertices) == 4 else ([0, 1, 2],):
                    points = []
                    for index in indices:
                        key = vertices[index]
                        local = sub(element['vertices'][key], anchor)
                        points.append(local)
                    triangles.append({'vertices': points, 'uv': [uv[vertices[i]] for i in indices], 'region': region})
            meshes.append({'name': element['name'], 'triangles': triangles})
        item = items[item_id]
        props = item['properties']
        slots = {}
        for s in props.get('slots', []):
            child = next((c for c in mappings if c['parentItemId'] == item_id and c['slot'] == s['nameId']), None)
            # Unmodelled optional slots remain visible at the owner's anchor; no fake geometry.
            slots[s['nameId']] = sub(anchors[child['itemId']], anchor) if child else [0, 0, 0]
        models.append({'definitionId': item_id, 'name': m['modelLabel'], 'attachmentOrigin': [0, 0, 0],
                       'slots': slots, 'boxes': [], 'meshes': meshes})
        weapon = m['parentItemId'] is None
        stats = {'weightKg': item['weight'], 'ergonomics': props.get('ergonomics', 0) if weapon else item.get('ergonomicsModifier', 0)}
        if not weapon:
            stats['recoilFraction'] = props.get('recoilModifier') or 0
        if props.get('accuracyModifier') is not None:
            stats['accuracyPercent'] = props['accuracyModifier'] * 100
        if item.get('velocity') is not None:
            stats['velocityPercent'] = item['velocity']
        for key in ['heatFactor', 'coolingFactor', 'durabilityBurnFactor'] + ([] if weapon else ['centerOfImpact', 'sightingRange']):
            if props.get(key) is not None:
                stats[key] = props[key]
        assert not item.get('conflictingCategories') and not item.get('conflictingSlotIds')
        part = {'id': item_id, 'stats': stats, 'slots': [],
                'conflictingParts': [i for i in item.get('conflictingItems', []) if i in items]}
        for slot in props.get('slots', []):
            f = slot['filters']
            assert not f.get('allowedCategories') and not f.get('excludedCategories')
            part['slots'].append({'id': slot['nameId'], 'required': slot.get('required', False),
                                  'allowedParts': [i for i in f['allowedItems'] if i in items and i not in f.get('excludedItems', [])]})
        if weapon:
            part['weapon'] = {key: props[key] for key in ['recoilVertical', 'recoilHorizontal', 'centerOfImpact', 'sightingRange'] if props.get(key) is not None}
        parts.append(part)
        uid = lambda key: str(uuid.uuid5(uuid.NAMESPACE_URL, 'newmod/adar/model-instance/' + key))
        node = {'instanceId': uid(item_id), 'definitionId': item_id}
        if not weapon:
            node.update(parentId=uid(m['parentItemId']), slot=m['slot'])
        nodes.append(node)
        evidence.append({'itemId': item_id, 'sourceSha256': hashlib.sha256(raw).hexdigest(),
                         'artAnchor': anchor, 'meshes': len(meshes), 'sourceFaces': source_faces,
                         'triangles': sum(len(mesh['triangles']) for mesh in meshes)})
    write('assembly-adar/catalog.json', {'schemaVersion': 1, 'parts': parts})
    write('assembly-adar/scene.json', {'schemaVersion': 1, 'nodes': nodes})
    write('assembly-adar/manifest.json', {'schemaVersion': 3, 'models': models})
    write('assembly-adar/import-report.json', {'source': str(PACK), 'parts': evidence,
          'scope': 'Selected-model development catalog; all source slots/required flags retained, allowed/conflict IDs intersect modelled IDs. Raw full rules remain in docs reference data.',
          'coordinates': 'Each vertex = source art vertex minus authored part anchor. Parent slot = child anchor minus parent anchor. Assembled world position = source art vertex minus root anchor.'})
    print('Imported', len(models), 'models;', sum(e['triangles'] for e in evidence), 'triangles')


if __name__ == '__main__':
    main()
