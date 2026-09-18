"""Read and validate authored assembly relations before generating resources."""
import json


def validate_assembly(source, root_definition='lower_receiver'):
    if source.get('schemaVersion') != 1:
        raise ValueError('unsupported assembly schema')
    definitions = source['physical'] + list(source['external'].values())
    known = set(definitions)
    if len(known) != len(definitions):
        raise ValueError('duplicate definition')
    if root_definition not in known:
        raise ValueError('unknown root: ' + root_definition)
    slots = source['slots']
    for parent, mounts in slots.items():
        if parent not in known:
            raise ValueError('unknown parent: ' + parent)
        for name, candidates in mounts.items():
            if not candidates or len(candidates) != len(set(candidates)):
                raise ValueError('empty or duplicate candidates: ' + name)
            if not set(candidates) <= known:
                raise ValueError('unknown candidate: ' + name)
    preset = source['preset']
    for parent, mounts in preset.items():
        if parent not in known:
            raise ValueError('unknown preset parent: ' + parent)
        for slot, child in mounts.items():
            if child not in slots.get(parent, {}).get(slot, []):
                raise ValueError('incompatible preset: ' + parent + '/' + slot)
    visited = set()

    def visit(part, ancestors):
        if part in ancestors:
            raise ValueError('preset cycle: ' + part)
        visited.add(part)
        for child in preset.get(part, {}).values():
            visit(child, ancestors | {part})

    visit(root_definition, set())
    if not set(preset) <= visited:
        raise ValueError('unreachable preset parent')
    for path in source['critical']:
        part = root_definition
        for slot in path:
            if slot not in preset.get(part, {}):
                raise ValueError('missing critical path: ' + '/'.join(path))
            part = preset[part][slot]
    return source


def load_assembly(path):
    return validate_assembly(json.loads(path.read_text()))
