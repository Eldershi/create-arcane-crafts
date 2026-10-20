#!/usr/bin/env python3
"""Exact revision46 native palette application; no code/recipe/art changes and no game launch."""
from pathlib import Path
import hashlib,io,json,zipfile
from PIL import Image
P=Path(__file__).resolve().parents[1]
A=P/'art/revision46-native-resource-fix'
R=P/'src/main/resources/assets/arcane_industry'
E=P/'testing/native-resource46-20261020'
M=json.loads((A/'preview-colour-mapping.json').read_text())
provenance=json.loads((A/'palette-provenance.json').read_text())
assert M['defaultCrimsonShape']=='source_crimson'
assert M['alternativeOnly']==['nugget_arcane','nugget_crimson']
palettes={key:{tuple(rgb) for rgb in values} for key,values in provenance['palettes'].items()}
cei='create-enchantment-industry-2.5.4b.jar';iron='irons_spellbooks-1.20.1-3.16.3.jar'
entries=[('arcane:block',cei,'assets/create_enchantment_industry/textures/block/super_experience_block.png','block/arcane_essence_block.png','arcane'),
 ('crimson:block',cei,'assets/create_enchantment_industry/textures/block/super_experience_block.png','block/crimson_essence_block.png','crimson'),
 ('arcane:hatch_base',cei,'assets/create_enchantment_industry/textures/block/experience_hatch.png','block/arcane_hatch.png','arcane'),
 ('arcane:hatch_open',cei,'assets/create_enchantment_industry/textures/block/experience_hatch_open.png','block/arcane_hatch_open.png','arcane'),
 ('source_crimson',iron,'assets/irons_spellbooks/textures/item/arcane_essence.png','item/crimson_essence.png','crimson')]
archives={name:zipfile.ZipFile(P/'build/deps'/name) for name in [cei,iron]}
sha=lambda data:hashlib.sha256(data).hexdigest()
blue=Image.open(io.BytesIO(archives[iron].read('assets/irons_spellbooks/textures/item/arcane_essence.png'))).convert('RGBA')
blood=Image.open(io.BytesIO(archives[iron].read('assets/irons_spellbooks/textures/item/blood_vial.png'))).convert('RGBA')
assert palettes['arcane']<={p[:3] for p in blue.get_flattened_data() if p[3]}
red_pixels=[blood.getpixel((x,int(y))) for y,(left,right) in provenance['redLiquidMask'].items() for x in range(left,right+1)]
assert len(red_pixels)==provenance['redLiquidPixelCount']==31
assert palettes['crimson']=={p[:3] for p in red_pixels}
report={'status':'PASS','mapping':str(A/'preview-colour-mapping.json'),'mappingSha256':sha((A/'preview-colour-mapping.json').read_bytes()),
 'paletteProvenance':str(A/'palette-provenance.json'),'paletteProvenanceSha256':sha((A/'palette-provenance.json').read_bytes()),
 'defaultCrimsonShape':'Iron arcane_essence outline with source_crimson mapping',
 'nativeArcaneEssenceOverridden':False,'alternativeNuggetsInstalled':False,'newItemsOrRecipes':False,
 'redPalettePixelMaskCount':31,'resources':[],'artWritten':False,'gameStarted':False}
for key,jar,source,target,family in entries:
 data=archives[jar].read(source)
 image=Image.open(io.BytesIO(data)).convert('RGBA')
 lookup={tuple(entry['source']):tuple(entry['target']) for entry in M['maps'][key]['pixels']}
 original=list(image.get_flattened_data());pixels=[];changed=0;copper=0
 for pixel in original:
  normalized=pixel if pixel[3] else (0,0,0,0)
  assert normalized in lookup,(key,normalized)
  mapped=lookup[normalized]
  assert mapped[3]==pixel[3]
  if mapped[3] and ('hatch' not in key or mapped!=normalized):assert mapped[:3] in palettes[family]
  if 'hatch' in key and pixel[3] and pixel[0]>pixel[1] and pixel[0]>pixel[2]:
   assert mapped==normalized
   copper+=1
  changed+=mapped!=normalized
  pixels.append(mapped)
 result=Image.new('RGBA',image.size);result.putdata(pixels)
 destination=R/'textures'/target
 result.save(destination)
 loaded=Image.open(destination).convert('RGBA')
 assert list(loaded.get_flattened_data())==pixels
 assert loaded.size==image.size
 entry={'mappingKey':key,'target':str(destination),'sourceJar':jar,'sourceEntry':source,
  'sourceSha256':sha(data),'sha256':sha(destination.read_bytes()),'dimensions':list(image.size),
  'alphaPixelChecks':len(pixels),'changedPixels':changed,'nativeCopperPixelsPreserved':copper,'pixelMappingExact':True}
 if key.endswith(':block'):
  assert image.size==(16,256)
  frame_hashes=[sha(loaded.crop((0,f*16,16,f*16+16)).tobytes()) for f in range(16)]
  assert len(set(frame_hashes))==16
  meta=archives[jar].read(source+'.mcmeta')
  assert json.loads(meta)['animation']=={'interpolate':False,'frametime':3}
  destination.with_suffix('.png.mcmeta').write_bytes(meta)
  entry.update({'frameHashes':frame_hashes,'uniqueFrames':16,'frames':16,'frametimeTicks':3,'interpolate':False,'nativeMcmetaByteExact':True})
 report['resources'].append(entry)
# Default native blue essence stays supplied by Iron; no namespace override is emitted.
assert not (P/'src/main/resources/assets/irons_spellbooks/textures/item/arcane_essence.png').exists()
report['alphaPixelChecks']=sum(entry['alphaPixelChecks'] for entry in report['resources'])
E.mkdir(parents=True,exist_ok=True)
(E/'resource-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
for z in archives.values():z.close()
print(json.dumps({'status':'PASS','mappedTextures':len(entries),'alphaPixelChecks':report['alphaPixelChecks'],'crimsonShape':'source_crimson','nativeCopperPixelsPreserved':sum(e['nativeCopperPixelsPreserved'] for e in report['resources'])}))
