#!/usr/bin/env python3
"""Apply only revision47 mithril geometry/textures and crimson highlights; keep blue frozen."""
from pathlib import Path
import base64,hashlib,io,json,shutil,zipfile
from PIL import Image
P=Path(__file__).resolve().parents[1];A=P/'art/revision47-mithril-rose';R=P/'src/main/resources/assets/arcane_industry'
E=P/'testing/native-resource47-20261020';B=P/'testing/before-native-resource47-20261020'
M=json.loads((A/'preview-colour-mapping.json').read_text());M46=json.loads((P/'art/revision46-native-resource-fix/preview-colour-mapping.json').read_text());handoff=json.loads((A/'resource-handoff.json').read_text());payload=json.loads((A/'resource-payload.json').read_text())
assert M['blueFrozenFromRevision']==handoff['blueFrozenRevision']==46
assert M['defaultCrimsonShape']=='source_crimson'
assert M['alternativeOnly']==['nugget_arcane','nugget_crimson']
sha=lambda data:hashlib.sha256(data).hexdigest()
cei='create-enchantment-industry-2.5.4b.jar';create='create-1.20.1-6.0.8.jar';iron='irons_spellbooks-1.20.1-3.16.3.jar'
archives={name:zipfile.ZipFile(P/'build/deps'/name) for name in [cei,create,iron]}
entries={
 'arcane:block':(cei,'assets/create_enchantment_industry/textures/block/super_experience_block.png','block/arcane_essence_block.png'),
 'arcane:hatch_base':(cei,'assets/create_enchantment_industry/textures/block/experience_hatch.png','block/arcane_hatch.png'),
 'arcane:hatch_open':(cei,'assets/create_enchantment_industry/textures/block/experience_hatch_open.png','block/arcane_hatch_open.png'),
 'crimson:block':(cei,'assets/create_enchantment_industry/textures/block/super_experience_block.png','block/crimson_essence_block.png'),
 'source_crimson':(iron,'assets/irons_spellbooks/textures/item/arcane_essence.png','item/crimson_essence.png'),
 'arcane:mithril_plate':(create,'assets/create/textures/block/crushing_wheel_plates.png','block/mithril_grindstone_plate.png'),
 'arcane:mithril_axis_top':(create,'assets/create/textures/block/axis_top.png','block/mithril_grindstone_axis_top.png'),
 'arcane:mithril_axis_side':(create,'assets/create/textures/block/cogwheel_axis.png','block/mithril_grindstone_axis_side.png')}
report={'status':'PASS','mapping':str(A/'preview-colour-mapping.json'),'mappingSha256':sha((A/'preview-colour-mapping.json').read_bytes()),'blueFrozenFromRevision':46,'resources':[],'models':[],'gameStarted':False,'artWritten':False,'alternativeNuggetsInstalled':False}
metal=Image.open(io.BytesIO(archives[iron].read('assets/irons_spellbooks/textures/item/mithril_ingot.png'))).convert('RGBA')
metal_palette={tuple(rgb) for rgb in handoff['mithrilPalette']};assert metal_palette<={p[:3] for p in metal.get_flattened_data() if p[3]}
blue_keys=['arcane:block','arcane:hatch_base','arcane:hatch_open']
for key in blue_keys+['nugget_arcane']:assert M['maps'][key]==M46['maps'][key]
luma=lambda p:p[0]*.2126+p[1]*.7152+p[2]*.0722
for key,(jar,source,target) in entries.items():
 raw=archives[jar].read(source);image=Image.open(io.BytesIO(raw)).convert('RGBA')
 from_payload=Image.open(io.BytesIO(base64.b64decode(payload['images'][M['maps'][key]['source']].split(',',1)[1]))).convert('RGBA')
 assert image.size==from_payload.size and image.tobytes()==from_payload.tobytes()
 lookup={tuple(x['source']):tuple(x['target']) for x in M['maps'][key]['pixels']}
 pixels=[]
 for pixel in image.get_flattened_data():
  normalized=pixel if pixel[3] else (0,0,0,0)
  assert normalized in lookup,(key,normalized)
  mapped=lookup[normalized];assert mapped[3]==pixel[3]
  if 'mithril' in key and mapped[3]:assert mapped[:3] in metal_palette
  pixels.append(mapped)
 result=Image.new('RGBA',image.size);result.putdata(pixels);dest=R/'textures'/target
 row={'mappingKey':key,'sourceJar':jar,'sourceEntry':source,'target':str(dest),'dimensions':list(image.size),'alphaPixelChecks':len(pixels),'sourceSha256':sha(raw)}
 if key in blue_keys:
  frozen=Image.open(dest).convert('RGBA');assert frozen.size==image.size and frozen.tobytes()==result.tobytes()
  assert dest.read_bytes()==(B/'src/main/resources/assets/arcane_industry/textures'/target).read_bytes()
  row['written']=False;row['frozenByteExact']=True
 else:
  if key in ['crimson:block','source_crimson']:
   old=Image.open(B/'src/main/resources/assets/arcane_industry/textures'/target).convert('RGBA')
   source_pixels=list(image.get_flattened_data());old_pixels=list(old.get_flattened_data());levels=[luma(p) for p in source_pixels if p[3]];lo=min(levels);hi=max(levels)
   dark=bright=0
   for src,before,after in zip(source_pixels,old_pixels,pixels):
    assert before[3]==after[3]
    value=(luma(src)-lo)/(hi-lo or 1)
    if src[3] and value>=.97:assert after[:3]==(250,127,174);bright+=1
    elif src[3] and value>=.90:assert after[:3]==(218,74,125);bright+=1
    else:assert after==before;dark+=1
   row.update({'darkAndTransparentPixelsUnchanged':dark,'roseHighlightPixels':bright})
  result.save(dest);assert Image.open(dest).convert('RGBA').tobytes()==result.tobytes();row['written']=True
 row['sha256']=sha(dest.read_bytes())
 if key.endswith(':block'):
  assert image.size==(16,256)
  hashes=[sha(result.crop((0,f*16,16,f*16+16)).tobytes()) for f in range(16)]
  assert len(set(hashes))==16
  meta=dest.with_suffix('.png.mcmeta');assert meta.read_bytes()==archives[cei].read('assets/create_enchantment_industry/textures/block/super_experience_block.png.mcmeta')
  row.update({'frames':16,'frametimeTicks':3,'interpolate':False,'uniqueFrames':16,'nativeMcmetaUnchanged':True})
 report['resources'].append(row)
prepared=A/'prepared-resources/assets/arcane_industry'
for rel in handoff['preparedModelFiles']:
 source=prepared/rel;dest=R/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
 assert source.read_bytes()==dest.read_bytes();j=json.loads(dest.read_text())
 report['models'].append({'target':str(dest),'sha256':sha(dest.read_bytes()),'elements':len(j.get('elements',[])),'parent':j.get('parent')})
wheel=json.loads((R/'models/block/arcane_grindstone.json').read_text());combo=json.loads((R/'models/block/arcane_grindstone_drain_item.json').read_text())
native_wheel=json.loads(archives[cei].read('assets/create_enchantment_industry/models/block/mechanical_grindstone.json'));native_combo=json.loads(archives[cei].read('assets/create_enchantment_industry/models/block/grindstone_drain/item.json'))
assert len(wheel['elements'])==20 and wheel['elements'][:10]==native_wheel['elements']
assert len(combo['elements'])==26 and combo['elements'][:16]==native_combo['elements']
assert (R/'models/block/arcane_grindstone_drain.json').read_bytes()==(B/'src/main/resources/assets/arcane_industry/models/block/arcane_grindstone_drain.json').read_bytes()
assert json.loads((R/'models/block/arcane_grindstone_drain.json').read_text())=={'parent':'create_enchantment_industry:block/grindstone_drain/block'}
assert len(json.loads(archives[cei].read('assets/create_enchantment_industry/models/block/grindstone_drain/block.json'))['elements'])==6
for filename in ['models/block/arcane_hatch.json','blockstates/arcane_hatch.json']:
 assert (R/filename).read_bytes()==(B/'src/main/resources/assets/arcane_industry'/filename).read_bytes()
assert not (P/'src/main/resources/assets/irons_spellbooks/textures/item/arcane_essence.png').exists()
report.update({'originalWheelElementsRetained':10,'addedMithrilElements':10,'worldStaticDrainElements':6,'inventoryComboElements':26,'pipePreviewGeometryInstalled':False,'hatchGeometryUnchanged':True,'nativeArcaneEssenceOverridden':False,'mithrilPaletteAllFromNativeIngot':True})
E.mkdir(parents=True,exist_ok=True);(E/'resource-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
for z in archives.values():z.close()
print(json.dumps({'status':'PASS','blueTexturesFrozen':3,'mappedTextureUpdates':5,'modelFiles':3,'wheelElements':20,'inventoryComboElements':26,'worldStaticDrainElements':6,'roseHighlightPixels':sum(r.get('roseHighlightPixels',0) for r in report['resources'])}))
