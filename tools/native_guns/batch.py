"""Generate and validate explicitly selected guns with per-gun timing; fail fast."""
import argparse
import importlib.util
import json
import time
import produce as p

spec=importlib.util.spec_from_file_location('gun_validation',p.TOOLS/'native_guns/validate.py')
validation=importlib.util.module_from_spec(spec)
spec.loader.exec_module(validation)


def run(guns,check_only=False):
    paths=[]
    for gun in guns:
        if not gun.replace('_','').isalnum():raise ValueError('Invalid gun ID')
        path=p.R/'modules/tacz_adapter/weapon-sources'/('native_'+gun)/'production.json'
        if not path.is_file():raise ValueError('No configured gun: '+gun)
        paths.append(path)
    for path in paths:
        started=time.monotonic()
        print('Starting',path.parent.name,flush=True)
        if not check_only:p.build(path)
        report=validation.validate(p.RES,p.ex.read(path)['weapon'])
        print(json.dumps({'gun':report['gunId'],'seconds':round(time.monotonic()-started,2),
                          'checkOnly':check_only,'minecraftStarted':False}),flush=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('guns',nargs='+')
    parser.add_argument('--check-only',action='store_true')
    args=parser.parse_args()
    run(args.guns,args.check_only)
