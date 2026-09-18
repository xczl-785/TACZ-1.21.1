"""Assembly-space frames, independent of mesh bounds and native animation pivots."""
import json
import math


def validate_mounts(source, catalog, root_definition='lower_receiver'):
    if source.get('schemaVersion') != 1:
        raise ValueError('Unsupported mount schema')
    parts = source['parts']
    if set(parts) != {part['id'] for part in catalog}:
        raise ValueError('Mount definitions differ from catalog')

    def vector(value):
        if not isinstance(value, list) or len(value) != 3 or not all(
                type(v) in (int, float) and math.isfinite(v) for v in value):
            raise ValueError('Expected finite mount vector')

    for part in catalog:
        frame = parts[part['id']]
        vector(frame['frameOrigin'])
        vector(frame['attachmentOrigin'])
        if set(frame['slots']) != {slot['id'] for slot in part['slots']}:
            raise ValueError('Mount slots differ from catalog: ' + part['id'])
        for point in frame['slots'].values():
            vector(point)
    root = parts[root_definition]
    if any(abs(a + b) > 1e-7 for a, b in zip(root['frameOrigin'], root['attachmentOrigin'])):
        raise ValueError('Root frame must preserve native rest placement')
    for part in catalog:
        parent = parts[part['id']]
        for slot in part['slots']:
            mount = [a + b for a, b in zip(parent['frameOrigin'], parent['slots'][slot['id']])]
            for candidate in slot['allowedParts']:
                child = parts[candidate]
                attachment = [a + b for a, b in zip(child['frameOrigin'], child['attachmentOrigin'])]
                if any(abs(a - b) > 1e-7 for a, b in zip(mount, attachment)):
                    raise ValueError('Mount differs from native rest placement: ' + part['id'] + '/' + slot['id'] + '/' + candidate)
    return parts


def load_mounts(path, catalog, root_definition='lower_receiver'):
    return validate_mounts(json.loads(path.read_text()), catalog, root_definition)
