#!/usr/bin/env python3
"""Generate approved ammunition from preserved source data and standard pixel assets."""
import argparse, gzip, hashlib, json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/"ammunition/generated"
INPUTS=Path(__file__).resolve().parents[1]/"ammunition/inputs"
def build():
 source=json.loads((INPUTS/'approved-ammunition.json').read_text())['items']
 locales=json.loads((INPUTS/'descriptions.json').read_text())
 files={};catalog=[];langs={'zh_cn':{},'en_us':{}}
 images={r['id']:r for r in json.loads((INPUTS/'pixel-provenance.json').read_text())['items']}
 def emit(path,value):files[ROOT/path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
 base='src/main/resources/'
 for r in source:
  path='ammo_'+r['id'];ident='tarkov_content:'+path
  catalog.append(dict(id=ident,sourceId=r['id'],category='ammunition',caliber=r['project_caliber'],fleshDamage=r['Damage'],penetrationPower=r['PenetrationPower'],armorDamage=r['ArmorDamage'],stackMaxSize=r['StackMaxSize'],initialSpeed=r['InitialSpeed'],recoilModifier=r['ammoRec'],source=r))
  emit(base+'data/tarkov_content/item_foundation/items/'+path+'.json',dict(schema_version=3,item=ident,footprint=[r['Width'],r['Height']],weight_kg=r['Weight'],description={'translate':'item.tarkov_content.'+path+'.description'},category='tarkov_content:ammunition',container_areas=None,wearable_slots=[]))
  emit(base+'assets/tarkov_content/models/item/'+path+'.json',dict(parent='minecraft:item/generated',textures={'layer0':'tarkov_content:item/'+path}))
  image=INPUTS/'pixels'/f'{r["id"]}.png'
  image_bytes=image.read_bytes()
  assert hashlib.sha256(image_bytes).hexdigest()==images[r['id']]['pixel_sha256'], 'Pixel hash mismatch'
  files[ROOT/(base+'assets/tarkov_content/textures/item/'+path+'.png')]=image_bytes
  for lang,key in [('zh_cn','name_zh'),('en_us','name_en')]:
   langs[lang]['item.tarkov_content.'+path]=r[key]
   description=locales[lang][r['id']]
   assert description.strip(), 'Missing source description: '+r['id']
   langs[lang]['item.tarkov_content.'+path+'.description']=description
 emit(base+'data/tarkov_content/catalog/ammunition.json',catalog)
 emit(base+'data/tarkov_content/item_foundation/categories/ammunition.json',dict(schema_version=1,name={'translate':'category.tarkov_content.ammunition','fallback':'子弹'}))
 for lang in langs:
  langs[lang]['category.tarkov_content.ammunition']='子弹' if lang=='zh_cn' else 'Ammunition'
  langs[lang]['itemGroup.tarkov_content.ammunition']='塔科夫子弹' if lang=='zh_cn' else 'Tarkov Ammunition'
 for key,zh,en in [('damage','威力','Damage'),('recoil','后坐力','Recoil'),('caliber','口径','Caliber'),('penetration','穿透力','Penetration'),('speed','子弹初速','Muzzle velocity'),('speed_unit',' 米/秒',' m/s')]:
  langs['zh_cn']['inspection.tarkov_content.ammunition.'+key]=zh
  langs['en_us']['inspection.tarkov_content.ammunition.'+key]=en
 emit('scripts/ammunition/generated_lang.json',langs)
 return files
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
 for path,data in build().items():
  if args.check:
   if not path.exists() or path.read_bytes()!=data:raise SystemExit('Ammunition output drift: '+str(path))
  else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
 print('Ammunition resources OK')
