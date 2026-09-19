"""Reproducible optical batch. No client launch, runtime lock changes or pushes."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import produce as p

REPORT=p.R/'docs/assembly-experiment/optics-batch/report.json'

def run(check_only=False):
    started=time.monotonic();stages=[]
    p.write(REPORT,{'status':'running','checkOnly':check_only,'minecraftStarted':False})
    env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1')
    def command(label,args):
        begin=time.monotonic()
        try:subprocess.run([sys.executable,*args],cwd=p.R,env=env,check=True)
        except subprocess.CalledProcessError:
            p.write(REPORT,{'status':'failed','stage':label,'completedStages':stages,'minecraftStarted':False})
            raise
        stages.append({'stage':label,'seconds':round(time.monotonic()-begin,3)})
    rows=p.ex.read(p.SOURCES/'catalog.json')['optics'];measurements=[]
    if not check_only:
        for row in rows:
            begin=time.monotonic();p.compile_optic(p.R/row['source']);measurements.append({'id':row['attachmentId'],'seconds':round(time.monotonic()-begin,4)})
        command('m4 generation',['tools/native_m4a1/generate.py'])
    command('m4 validation',['tools/native_m4a1/validate.py'])
    guns=[f.parent.name.removeprefix('native_') for f in sorted((p.R/'modules/tacz_adapter/weapon-sources').glob('native_*/production.json'))]
    command('other guns validation' if check_only else 'other guns generation and validation',['tools/native_guns/batch.py',*guns,*(['--check-only'] if check_only else [])])
    command('optical tests',['-m','unittest','discover','-s','tools/native_optics','-p','test_*.py'])
    entries=[]
    for row in rows:
        source=p.R/row['source'];config=p.ex.read(source/'optic.json');manifest=p.ex.read(source/'editable/manifest.json')['parts'][0]
        _,bones,_,_,count=p.shared.load_part(manifest,source/'editable');display=p.ex.read(source/'display.json')
        compatible=[]
        for path in sorted((p.OUT/'data/tacz_fork_tarkov').glob('*/native_attachments.json')):
            if row['attachmentId'] in p.ex.read(path):compatible.append(path.parent.name)
        entries.append({**row,'cubes':count,'zoom':display['zoom'],'nativeViewMapping':display.get('views'),
                        'resolved':p.validate_optics(bones,display,config),'lodPolicy':config['lod']['policy'],
                        'guns':compatible,'automaticChecks':'passed','ownerVisualAcceptance':'accepted on M4 sample only' if source.name in ('t2_sample','elcan_sample') else 'pending'})
    report={'schemaVersion':1,'checkOnly':check_only,'elapsedSeconds':round(time.monotonic()-started,3),'stages':stages,'compileTimings':measurements,'optics':entries,
            'status':'passed','inputFingerprints':{str(f.relative_to(p.R)):p.ex.sha(f) for f in sorted(p.SOURCES.rglob('*')) if f.is_file() and f.suffix in ('.json','.bbmodel','.png')},'opticCount':len(entries),'compatiblePairs':sum(len(e['guns']) for e in entries),'minecraftStarted':False,
            'limits':['Automatic geometry/data checks are not Blockbench UI export or Minecraft visual acceptance.','Original IDs and default assemblies retained.']}
    p.write(REPORT,report);print('Batch report:',REPORT)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check-only',action='store_true');args=parser.parse_args();run(args.check_only)
