"""Strict first-person resource contract, also called by normal processResources."""
import math
from build_presentation import read

def vector(v):
    if not isinstance(v,list) or len(v)!=3 or not all(isinstance(n,(int,float)) and math.isfinite(n) for n in v):raise ValueError('Expected finite three-coordinate vector')

def validate(runtime,assets,data,geometry):
    directory=runtime['resourceDirectory'];name=runtime['gunId'].split(':')[1]
    handling=read(data/directory/'handling.json');markers=read(assets/directory/'markers.json')
    if handling['schemaVersion']!=1 or markers['schemaVersion']!=1:raise ValueError('Unknown presentation schema')
    vector(handling['idleView']);vector(handling['fallbackView'])
    for key in ('leftHand','rightHand'):
        hand=handling[key]
        for field in ('rotation','scale','palm'):vector(hand[field])
        if any(v<=0 for v in hand['scale']):raise ValueError('Invalid hand scale')
    recoil=handling['recoil']
    for key in ('referenceVertical','referenceHorizontal','pitchScale','yawScale','springFrequency','maxDisplacement','visualKick','visualPitch','adsMotion'):
        v=recoil[key]
        if not isinstance(v,(int,float)) or not math.isfinite(v) or v<0:raise ValueError('Invalid recoil: '+key)
    if any(recoil[k]<=0 for k in ('referenceVertical','referenceHorizontal','springFrequency','maxDisplacement')) or recoil['adsMotion']>1:raise ValueError('Invalid recoil reference')
    for definition,part in markers['parts'].items():
        if definition not in geometry:raise ValueError('Marker without geometry: '+definition)
        ids=set()
        for marker in part['sights']:
            if not marker['id'] or marker['id'] in ids:raise ValueError('Duplicate/empty marker mode: '+definition)
            ids.add(marker['id']);vector(marker['position']);vector(marker['rotation'])
            if marker['kind'] not in ('optic','rear','front'):raise ValueError('Unknown sight kind')
            if marker['kind']!='optic' and not marker['group']:raise ValueError('Missing iron group')
            if marker['eyeDistance']<0 or (marker['kind']!='front' and marker['eyeDistance']<=0):raise ValueError('Invalid eye relief')
            if not 1<=marker['zoom']<=32 or not 10<=marker['modelFov']<=120:raise ValueError('Invalid sight zoom')
            for k in ('eyeDistance','zoom','modelFov','priority'):
                if not math.isfinite(marker[k]):raise ValueError('Non-finite sight')
        roles=set()
        for contact in part['contacts']:
            vector(contact['position'])
            if contact['role'] not in ('left','right') or contact['role'] in roles:raise ValueError('Duplicate/unknown contact role')
            roles.add(contact['role'])
    bones={b['name']:b for b in read(assets/'geo_models/gun'/f'{name}.json')['minecraft:geometry'][0]['bones']}
    if bones.get('constraint',{}).get('parent')!='root':raise ValueError('Missing ADS constraint chain')
    for key in ('lefthand','righthand'):
        if bones[key]['parent']!='root' or bones[key+'_pos']['parent']!=key or bones[key]['pivot']!=bones[key+'_pos']['pivot']:raise ValueError('Inconsistent hand contact rig')
    animations=read(assets/'animations'/f'{name}.animation.json')['animations']
    if {'root','camera','constraint'}&animations['shoot']['bones'].keys():raise ValueError('Duplicate procedural recoil owner')
    idle=animations['static_idle']['bones']
    for key,hand in [('lefthand','leftHand'),('righthand','rightHand')]:
        if idle[key].get('rotation')!=handling[hand]['rotation'] or idle[key].get('scale')!=handling[hand]['scale']:raise ValueError('Stale hand calibration')
