"""Single entry point for configured weapon content; never launches Minecraft."""
import argparse
import hashlib
import json
from pathlib import Path
import sys

MODULE = Path(__file__).resolve().parents[1]
ROOT = MODULE.parents[1]
AUTHORS = MODULE/'weapon-authoring'
PACKAGED = MODULE/'weapon-content/resources'


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n')


def file_hashes(directory):
    return {str(p.relative_to(directory)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(directory.rglob('*')) if p.is_file() and not p.name.startswith('._')}


def selected_files(root, authors):
    paths = set()
    for author in authors:
        namespace = read(author/'runtime.json')['gunId'].split(':')[0]
        for kind in ('assets', 'data'):
            paths.update(p.relative_to(root) for p in (root/kind/namespace).rglob('*') if p.is_file())
        # Include referenced shared material namespaces, not just the gun's own namespace.
        runtime = read(author/'runtime.json')
        library = root/'assets'/namespace/runtime['resourceDirectory']/'library.json'
        if library.is_file():
            for material in read(library)['materials'].values():
                if material.get('texture'):
                    relative = Path('assets')/material['texture'].replace(':','/')
                    if (root/relative).is_file():
                        paths.add(relative)

    paths.add(Path('data/tactical_tacz_adapter/assembled_weapons.json'))
    return paths


def compare(before, after, authors):
    """Full JSON values (array order preserved), decoded PNG pixels, other bytes."""
    from PIL import Image
    paths = selected_files(before, authors) | selected_files(after, authors)
    differences, formatting = [], []
    for relative in sorted(paths):
        a, b = before/relative, after/relative
        if not a.is_file() or not b.is_file():
            differences.append({'path':str(relative), 'kind':'added' if b.is_file() else 'missing'})
            continue
        if a.read_bytes() == b.read_bytes():
            continue
        if relative.suffix == '.json':
            left, right = read(a), read(b)
            if relative == Path('data/tactical_tacz_adapter/assembled_weapons.json'):
                expected = {f'data/{read(author/"runtime.json")["gunId"].split(":")[0]}/{read(author/"runtime.json")["resourceDirectory"]}/weapon.json' for author in authors}
                left = dict(left, weapons=[entry for entry in left['weapons'] if entry in expected])
            equal = left == right
        elif relative.suffix == '.png':
            with Image.open(a) as ia, Image.open(b) as ib:
                equal = ia.size == ib.size and ia.convert('RGBA').tobytes() == ib.convert('RGBA').tobytes()
        else:
            equal = False
        (formatting if equal else differences).append({'path':str(relative), 'kind':'serialization-only' if equal else 'value-changed'})
    return {'filesCompared':len(paths), 'semanticDifferences':differences, 'encodingDifferences':formatting}


def generate(authors, output, reports):
    # Check before writing anything. Inputs and packaged resources cannot be destinations.
    protected = [AUTHORS, MODULE/'weapon-sources', MODULE/'src', PACKAGED, ROOT/'docs']
    for directory in (output, reports):
        if any(directory == p.resolve() or directory.is_relative_to(p.resolve()) or p.resolve().is_relative_to(directory) for p in protected):
            raise ValueError('Output overlaps production inputs/resources: '+str(directory))
    if output == reports or output.is_relative_to(reports) or reports.is_relative_to(output):
        raise ValueError('Resources and reports must be separate directories')
    if output.exists() and any(output.iterdir()):
        raise ValueError('Use an empty output directory; existing files are never overwritten')
    registrations, guns, models, types, namespaces = [], set(), set(), set(), set()
    for author in authors:
        runtime = read(author/'runtime.json'); ns = runtime['gunId'].split(':')[0]
        for value, seen in [(runtime['gunId'], guns), (runtime['modelType'], models), (runtime.get('itemType',runtime['gunId']), types), (ns,namespaces)]:
            if value in seen:
                raise ValueError('Duplicate content registration/namespace: '+value)
            seen.add(value)
    for author in authors:
        recipe = read(author/'import.json'); source = ROOT/recipe['source']
        original = file_hashes(source); config = file_hashes(author)
        extra = {recipe['items']:hashlib.sha256((ROOT/recipe['items']).read_bytes()).hexdigest()} if 'items' in recipe else {}
        if recipe['converter'] == 'legacy-adar':
            for entry in read(source/'provenance.json')['files']:
                if hashlib.sha256((source/entry['snapshot']).read_bytes()).hexdigest() != entry['sha256']:
                    raise ValueError('ADAR snapshot hash mismatch: '+entry['snapshot'])
            from build_adar import build
        elif recipe['converter'] == 'matrix-components':
            from build_weapon import build
        else:
            raise ValueError('Unsupported source converter: '+recipe['converter'])
        build(author, output, reports)
        runtime = read(author/'runtime.json'); ns = runtime['gunId'].split(':')[0]
        relative = f'data/{ns}/{runtime["resourceDirectory"]}/weapon.json'
        write(output/relative, runtime); registrations.append(relative)
        if original != file_hashes(source) or config != file_hashes(author):
            raise ValueError('Build mutated source or authoring inputs')
        write(reports/author.name/'inputs.json', {'source':recipe['source'], 'sourceHashes':original, 'authoringHashes':config, 'externalInputHashes':extra})
    write(output/'data/tactical_tacz_adapter/assembled_weapons.json', {'schemaVersion':1,'weapons':registrations})
    from validate_weapon_resources import validate
    validate(output)
    from audit_weapon_references import audit
    write(reports/'references.json', audit(output))


def main(default_weapon=None, legacy_author=False):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('weapon', nargs='?', default=default_weapon or 'all', help='all or author directory name')
    parser.add_argument('--output', type=Path, required=True, help='empty isolated runtime resources directory')
    parser.add_argument('--reports', type=Path, required=True, help='separate generated report directory')
    parser.add_argument('--compare', type=Path, help='compare with a previous runtime resource root; nonzero exit on semantic difference')
    args = parser.parse_args()
    weapon = Path(args.weapon).name if legacy_author else args.weapon
    authors = sorted(p.parent for p in AUTHORS.glob('*/runtime.json')) if weapon == 'all' else [AUTHORS/weapon]
    if not authors or any(not p.is_dir() or p.parent.resolve() != AUTHORS.resolve() for p in authors):
        parser.error('Unknown weapon author directory')
    generate(authors, args.output.resolve(), args.reports.resolve())
    if args.compare:
        result = compare(args.compare.resolve(), args.output.resolve(), authors)
        write(args.reports/'comparison.json',result)
        print(f"Compared {result['filesCompared']} resources: {len(result['semanticDifferences'])} semantic differences; {len(result['encodingDifferences'])} encoding differences")
        if result['semanticDifferences']:
            sys.exit(1)


if __name__ == '__main__':
    main()
