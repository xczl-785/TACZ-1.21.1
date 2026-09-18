"""One-time adoption of reviewed assembly frames; never overwrite author edits."""
import argparse
import produce as p
from native_m4a1.mount_source import validate_mounts


def adopt(config_path):
    config=p.ex.read(config_path)
    target=config_path.parent/'mounts.json'
    if target.exists():raise ValueError('Refuse overwriting authored mounts: '+str(target))
    base=p.RES/'data/tacz_assembly'/config['sourceGun']
    anchors=p.ex.read(base/'workbench-anchors.json')['anchors']
    models=p.ex.read(base/'preview.json')['models']
    source={'schemaVersion':1,'coordinateSystem':'assembly +Z muzzle, original model units',
            'parts':{m['definitionId']:{'frameOrigin':anchors[m['definitionId']],
                     'attachmentOrigin':m['attachmentOrigin'],'slots':m['slots']} for m in models}}
    validate_mounts(source,p.ex.read(base/'catalog.json')['parts'],config['rootDefinition'])
    p.write(target,source)
    print('Adopted',config['sourceGun'],len(models),'frames')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('guns',nargs='+',help='Explicit source gun IDs, e.g. scar_l')
    args=parser.parse_args()
    for gun in args.guns:
        if not gun.replace('_','').isalnum():raise ValueError('Invalid gun ID')
        adopt(p.R/'modules/tacz_adapter/weapon-sources'/('native_'+gun)/'production.json')
