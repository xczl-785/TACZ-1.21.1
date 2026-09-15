"""Reproduce the four-part test slice from an explicitly supplied local EFTForge items cache."""
import argparse
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('items_cache', type=Path)
args = parser.parse_args()
raw = args.items_cache.read_bytes()
items = json.loads(raw)['data']['items']
ids = ['5a7ae0c351dfba0017554310', '5a71e22f8dc32e00094b97f4',
       '5a32aa8bc4a2826c6e06d737', '5a6b5b8a8dc32e001207faf3']
parts = []
for item_id in ids:
    item = items[item_id]
    props = item.get('properties', {})
    weapon = props.get('propertiesType') == 'ItemPropertiesWeapon'
    # Match sync_tarkov_dev.py: top-level recoilModifier is percent, properties is a fraction.
    stats = {'weightKg': item['weight'],
             'ergonomics': props.get('ergonomics', 0) if weapon else item.get('ergonomicsModifier', 0)}
    if not weapon:
        stats['recoilFraction'] = props.get('recoilModifier') or 0
    if props.get('accuracyModifier') is not None:
        stats['accuracyPercent'] = round(props['accuracyModifier'] * 100, 4)
    if item.get('velocity') is not None:
        stats['velocityPercent'] = item['velocity']
    for key in ['heatFactor', 'coolingFactor', 'durabilityBurnFactor']:
        if props.get(key) is not None:
            stats[key] = props[key]
    if not weapon:
        for key in ['centerOfImpact', 'sightingRange']:
            if props.get(key) is not None:
                stats[key] = props[key]
    slots = []
    for slot in props.get('slots', []):
        assert not slot['filters'].get('allowedCategories'), 'Resolve category filters before extending fixture'
        slots.append({'id': slot['nameId'], 'required': slot.get('required', False),
                      'allowedParts': [i for i in slot['filters']['allowedItems']
                                       if i in ids and i not in slot['filters'].get('excludedItems', [])]})
    # This selected slice has no internal conflicts; adding new parts must explicitly map slot conflicts.
    assert not set(item.get('conflictingItems', [])) & set(ids)
    assert not item.get('conflictingSlotIds')
    part = {'id': item_id, 'slots': slots, 'stats': stats}
    if weapon:
        part['weapon'] = {k: props[k] for k in ['recoilVertical', 'recoilHorizontal', 'centerOfImpact', 'sightingRange']
                          if props.get(k) is not None}
    parts.append(part)
module = Path(__file__).resolve().parents[1]
(module/'src/test/resources/glock-optics.json').write_text(json.dumps({'schemaVersion': 1, 'parts': parts}, indent=2)+'\n')
(module/'docs/fixture-source.json').write_text(json.dumps({
    'source': 'EFTForge/docs/data/cache/items.json', 'sha256': hashlib.sha256(raw).hexdigest(), 'ids': ids,
    'scope': 'Test-only four-part slice. All source slots and required flags preserved; allowed edges intersect selected IDs. No models or runtime items.',
    'normalization': 'recoilFraction = properties.recoilModifier; accuracyPercent = properties.accuracyModifier * 100; own weight only; no factory preset shortcut'
}, indent=2)+'\n')
