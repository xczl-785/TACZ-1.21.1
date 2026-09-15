"""Import appearance-only edits from the exported Blockbench project.

Region = mesh name; material = texture filename (matching basic.json). Shape and
face identities must match the accepted white originals. Never consume source textures.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import uuid
import import_adar as geometry


def convert(project_path):
    project = json.loads(project_path.read_text())
    materials = json.loads((geometry.MODULE / 'src/main/resources/assets/weapon_assembly_ui/materials/basic.json').read_text())['materials']
    mappings = json.loads((geometry.DATA / 'model-mapping.json').read_text())['models']
    manifest = json.loads((geometry.OUT / 'assembly-adar/manifest.json').read_text())
    bindings = json.loads((geometry.OUT / 'assets/weapon_assembly_ui/materials/adar.json').read_text())
    models = {m['definitionId']: m for m in manifest['models']}
    elements = {e['uuid']: e for e in project['elements']}
    assert len(elements) == len(project['elements']), 'Duplicate element UUID'
    groups = {g['uuid']: g for g in project['outliner']}
    assert len(groups) == len(mappings), 'Expected one top-level group per model'
    consumed_elements = set()
    total_faces = 0
    for mapping, anchor in zip(mappings, geometry.ANCHORS):
        item_id = mapping['itemId']
        group = groups[str(uuid.uuid5(uuid.NAMESPACE_URL, 'newmod-material-group/' + item_id))]
        assert group.get('origin', [0, 0, 0]) == [0, 0, 0] and group.get('rotation', [0, 0, 0]) == [0, 0, 0]
        assert group.get('visibility', True)
        imported = {}
        region_materials = {}
        for element_id in group['children']:
            assert isinstance(element_id, str) and element_id not in consumed_elements
            consumed_elements.add(element_id)
            element = elements[element_id]
            assert element['type'] == 'mesh' and element.get('visibility', True) and element.get('export', True)
            assert element.get('origin', [0, 0, 0]) == [0, 0, 0] and element.get('rotation', [0, 0, 0]) == [0, 0, 0]
            region = element['name']
            assert region.strip(), 'Region name cannot be empty'
            for key, face in element['faces'].items():
                assert key not in imported, 'Duplicate source face'
                texture = project['textures'][int(face['texture'])]
                material_id = Path(texture['name']).stem
                assert material_id in materials, f'Add material {material_id} to basic.json first'
                assert region not in region_materials or region_materials[region] == material_id, f'Rename regions with mixed materials: {region}'
                region_materials[region] = material_id
                imported[key] = (element, face, region, material_id, texture)
        raw_path = geometry.PACK / mapping['whiteModel']
        assert hashlib.sha256(raw_path.read_bytes()).hexdigest() == mapping['modelSha256']['white_file']
        raw = json.loads(raw_path.read_text())
        seen = set()
        meshes = []
        for original in raw['elements']:
            if not original.get('visibility', True) or not original.get('export', True):
                continue
            triangles = []
            for key, source_face in original['faces'].items():
                source_key = original['uuid'] + '__' + key
                element, face, region, material_id, texture = imported[source_key]
                assert set(face['vertices']) == set(source_face['vertices']), 'Topology changed; this importer handles appearance only'
                for vertex in face['vertices']:
                    assert all(abs(a-b)<1e-5 for a,b in zip(element['vertices'][vertex], original['vertices'][vertex])), 'Geometry changed; use a model revision workflow'
                seen.add(source_key)
                order = geometry.sorted_vertices(source_face, original['vertices'])
                scale = materials[material_id].get('textureScale', 1)
                width = texture.get('uv_width', texture['width'])
                height = texture.get('uv_height', texture['height'])
                assert all(math.isfinite(v) and v > 0 for v in (width, height, scale)), 'Invalid texture scale or dimensions'
                assert all(len(face['uv'][v]) == 2 and all(math.isfinite(n) for n in face['uv'][v]) for v in order), 'Invalid UV coordinates'
                for indices in ([0,1,2], [0,2,3]) if len(order) == 4 else ([0,1,2],):
                    triangles.append({'vertices': [geometry.sub(original['vertices'][order[i]], anchor) for i in indices],
                                      'uv': [[face['uv'][order[i]][0]/width/scale, face['uv'][order[i]][1]/height/scale] for i in indices],
                                      'region': region})
            meshes.append({'name': original['name'], 'triangles': triangles})
        assert seen == set(imported), 'Faces added or removed'
        total_faces += len(seen)
        models[item_id]['meshes'] = meshes
        bindings['parts'][item_id]['regions'] = region_materials
    assert consumed_elements == set(elements), 'Unassigned elements'
    return manifest, bindings, total_faces


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('project', type=Path)
    parser.add_argument('--check', action='store_true', help='Validate without writing runtime resources')
    args = parser.parse_args()
    manifest, bindings, count = convert(args.project)
    if not args.check:
        geometry.write('assembly-adar/manifest.json', manifest)
        geometry.write('assets/weapon_assembly_ui/materials/adar.json', bindings)
    print('Validated' if args.check else 'Imported', count, 'source faces; geometry unchanged')


if __name__ == '__main__':
    main()
