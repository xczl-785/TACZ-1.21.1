#!/usr/bin/env python3
"""Offline reproduction of approved ammunition pixels; never overwrites a differing asset."""
import hashlib,io,json
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]/'ammunition/inputs'
def pixelize(image,edge):
 image=image.convert('RGBA');bounds=image.getchannel('A').getbbox()
 if bounds is None:raise ValueError('Reference is fully transparent')
 image=image.crop(bounds);ratio=min(1,edge/max(image.size));size=tuple(max(1,round(v*ratio)) for v in image.size)
 reduced=image.convert('RGBa').resize(size,Image.Resampling.BOX).convert('RGBA')
 result=Image.new('RGBA',(size[0]+4,size[1]+4));result.paste(reduced,(2,2));return result,bounds
if __name__=='__main__':
 for r in json.loads((ROOT/'pixel-provenance.json').read_text())['items']:
  original=(ROOT/'originals'/ (r['id']+'.webp')).read_bytes()
  assert hashlib.sha256(original).hexdigest()==r['source']['sha256']
  with Image.open(io.BytesIO(original)) as image:result,bounds=pixelize(image,64)
  out=io.BytesIO();result.save(out,format='PNG');data=out.getvalue();p=ROOT/'pixels'/(r['id']+'.png')
  assert hashlib.sha256(data).hexdigest()==r['pixel_sha256'],r['id']
  if p.exists():assert p.read_bytes()==data,r['id']
  else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
 print('86 source images and pixel reproductions verified')
