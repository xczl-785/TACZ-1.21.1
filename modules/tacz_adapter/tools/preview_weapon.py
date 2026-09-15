"""Offline material preview from the exact packaged preset. Not a game screenshot."""
from pathlib import Path
import argparse
import json
import numpy as np
from render_part_icon import render_part_icon

MODULE = Path(__file__).resolve().parents[1]
RESOURCES = MODULE/'weapon-content/resources'


def preview(author, output):
    read = lambda p: json.loads(p.read_text())
    runtime = read(author/'runtime.json'); namespace = runtime['gunId'].split(':')[0]; folder = runtime['resourceDirectory']
    assets = RESOURCES/'assets'/namespace; data = RESOURCES/'data'/namespace
    models = {m['definitionId']: m for m in read(assets/folder/'manifest.json')['models']}
    library = read(assets/folder/'library.json'); bindings = read(assets/folder/'materials.json')
    scene = read(data/folder/'scene.json')['nodes']; instances = {n['instanceId']: n for n in scene}
    triangles, regions = [], {}

    def origin(node):
        if 'parentId' not in node:
            return -np.asarray(models[node['definitionId']]['attachmentOrigin'])
        parent = instances[node['parentId']]; geometry = models[parent['definitionId']]
        return origin(parent) + np.asarray(geometry['slots'][node['slot']]) - np.asarray(models[node['definitionId']]['attachmentOrigin'])

    for node in scene:
        id = node['definitionId']; model = models[id]; binding = bindings['parts'][id]; offset = origin(node)
        for mesh in model['meshes']:
            for triangle in mesh['triangles']:
                key = id+'/'+triangle['region']
                regions[key] = binding['regions'].get(triangle['region'], binding['defaultMaterial'])
                triangles.append(triangle | {'region': key, 'vertices': [(np.asarray(v)+offset).tolist() for v in triangle['vertices']]})
    model = {'definitionId': 'preview', 'meshes': [{'name': 'assembly', 'triangles': triangles}]}
    binding = {'defaultMaterial': bindings['defaultMaterial'], 'parts': {'preview': {'defaultMaterial': bindings['defaultMaterial'], 'regions': regions}}}

    def texture_path(resource):
        ns, path = resource.split(':')
        local=RESOURCES/'assets'/ns/path
        if not local.is_file():
            raise ValueError('Missing content texture '+resource)
        return local

    output.parent.mkdir(parents=True, exist_ok=True)
    render_part_icon(model, library, binding, texture_path, size=1000).save(output)
    print('Offline material preview:', output)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('author', type=Path); parser.add_argument('output', type=Path)
    args = parser.parse_args(); preview(args.author.resolve(), args.output.resolve())
