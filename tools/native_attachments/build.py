"""Single build entry for the reviewed cross-gun non-optic source library."""
import argparse
import extract,import_assets,magazines

def build(output=magazines.RES):
    standalone=import_assets.build(output=output);magazines.build(resources=output)
    compat={}
    tag_hashes={}
    for p in sorted((magazines.DATA/'index/guns').glob('*.json')):
        allowed,paths=magazines.allowed(p.stem)
        tag_hashes.update({str(path.relative_to(extract.R)):extract.im.ex.sha(path) for path in sorted(paths)})
        for item in standalone:
            if item in allowed:compat.setdefault(item,[]).append('tacz:'+p.stem)
    for item in standalone:
        if not compat.get(item):raise ValueError('No retained gun accepts '+item)
    variants=extract.im.ex.read(output/'data/tacz_fork_tarkov/native_attachments/magazine_variants.json')['variants']
    document={'schemaVersion':1,'scope':'Remaining retained native non-optic attachment definitions',
       'attachmentDefinitions':len(standalone)+len(magazines.IDS),'standaloneModels':len(standalone),'magazineVariants':len(variants),
       'standaloneCompatibility':compat,'compatibilitySourceHashes':tag_hashes,
       'integrationStatus':'Assets prepared and packaged; activate only in each future assembled-gun profile. No native gun or M4 compatibility modified.',
       'excluded':{'opticalDefinitions':28,'ammoEffectDefinitions':5},
       'standaloneManifest':'tacz_fork_tarkov:native_attachments/standalone.json',
       'magazineManifest':'tacz_fork_tarkov:native_attachments/magazine_variants.json'}
    extract.im.write(output/'data/tacz_fork_tarkov/native_attachments/catalog.json',document)
    return document

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--extract',action='store_true');args=parser.parse_args()
    if args.extract:extract.append();magazines.append()
    print(build())
