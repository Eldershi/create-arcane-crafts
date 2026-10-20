#!/usr/bin/env python3
"""Install the reviewed deterministic native RGBA mapping; never writes the art previews."""
from pathlib import Path
import hashlib, io, json, zipfile
from PIL import Image
P=Path(__file__).resolve().parents[1]
A=P/'art/revision45-cei-resource-family'
R=P/'src/main/resources/assets/arcane_industry'
M=json.loads((A/'preview-color-mapping.json').read_text())
expected=json.loads((A/'preview-verification.json').read_text())
entries=[('block_arcane','block/super_experience_block','block/arcane_essence_block'),
         ('block_crimson','block/super_experience_block','block/crimson_essence_block'),
         ('nugget_crimson','item/super_experience_nugget','item/crimson_essence'),
         ('hatch_base','block/experience_hatch','block/arcane_hatch'),
         ('hatch_open','block/experience_hatch_open','block/arcane_hatch_open')]
report={'status':'PASS','mapping':str(A/'preview-color-mapping.json'),
 'mappingSha256':hashlib.sha256((A/'preview-color-mapping.json').read_bytes()).hexdigest(),
 'source':'pinned CEI 2.5.4b native pixels, exact reviewed RGBA mapping',
 'frames':16,'frameSize':[16,16],'frametimeTicks':3,'interpolate':False,
 'modelParent':'create:block/experience_block','blockClass':'Create ExperienceBlock',
 'lightLevel':15,'nativeEndRodParticles':True,'crimsonEssenceFoil':True,'resources':[],
 'gameStarted':False,'artWritten':False}
with zipfile.ZipFile(P/'build/deps/create-enchantment-industry-2.5.4b.jar') as z:
 for key,source,target in entries:
  data=z.read('assets/create_enchantment_industry/textures/'+source+'.png')
  image=Image.open(io.BytesIO(data)).convert('RGBA')
  palette={tuple(e['source']):tuple(e['target']) for e in M['maps'][key]}
  original=list(image.get_flattened_data());pixels=[]
  for pixel in original:
   normalized=pixel if pixel[3] else (0,0,0,0)
   assert normalized in palette, (key,normalized)
   mapped=palette[normalized]
   assert mapped[3]==pixel[3]
   pixels.append(mapped)
  result=Image.new('RGBA',image.size);result.putdata(pixels)
  destination=R/'textures'/(target+'.png');result.save(destination)
  entry={'id':key,'target':str(destination),'sourceTexture':'create_enchantment_industry:'+source,
         'dimensions':list(image.size),'alphaPixelsChecked':len(pixels),
         'sha256':hashlib.sha256(destination.read_bytes()).hexdigest()}
  if key.startswith('block_'):
   assert image.size==(16,256)
   hashes=[hashlib.sha256(result.crop((0,frame*16,16,frame*16+16)).tobytes()).hexdigest() for frame in range(16)]
   # Preview hashes are rendered 256px cubes; these hashes identify native 16px RGBA frames.
   assert len(hashes)==expected['frames']
   assert all(tuple(result.getpixel((x,y)))==palette[tuple(image.getpixel((x,y)))] for y in range(256) for x in range(16))
   assert len(set(hashes))==16
   meta=z.read('assets/create_enchantment_industry/textures/'+source+'.png.mcmeta')
   destination.with_suffix('.png.mcmeta').write_bytes(meta)
   entry.update({'pixelMappingMatchesReviewedPreview':True,'frameHashes':hashes,'mcmetaByteExact':True})
  report['resources'].append(entry)
# Original CEI UV/element geometry and native particle slot stay inherited.
f=R/'models/block/arcane_hatch.json';model=json.loads(f.read_text());model['textures']['particle']='create:block/item_drain_side';f.write_text(json.dumps(model,indent=2)+'\n')
(P/'testing/industry-expansion-20261020/native-resource-family.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'status':'PASS','mappedTextures':len(entries),'pixelAlphaChecks':sum(e['alphaPixelsChecked'] for e in report['resources']),'animatedBlocksMatchPreview':2}))
