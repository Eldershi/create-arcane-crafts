#!/usr/bin/env python3
"""Deterministic original placeholder pixels and data recipes for the prototype."""
from pathlib import Path
import json, struct, zlib

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
MOD = 'arcane_industry'

def write(path, value):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')

def png(name, pixel):
    target = ROOT / f'assets/{MOD}/textures/{name}.png'
    target.parent.mkdir(parents=True, exist_ok=True)
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    raw = b''.join(b'\x00' + b''.join(bytes(pixel(x, y)) for x in range(16)) for y in range(16))
    target.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16,16,8,6,0,0,0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND',b''))

BRASS=(156,115,61,255); GOLD=(218,178,99,255); DARK=(43,44,49,255); PURPLE=(142,85,224,255); CLEAR=(0,0,0,0)
png('block/casing', lambda x,y: GOLD if (x,y) in [(1,1),(14,1),(1,14),(14,14)] else BRASS if x in [0,1,14,15] or y in [0,1,14,15] else (60+(x+y)%3*4,58,62,255))
for name,color in [('idle',(62,57,72,255)),('active',PURPLE),('super',(242,202,113,255))]:
    png('block/core_'+name,lambda x,y,c=color: (min(255,c[0]+25),min(255,c[1]+25),min(255,c[2]+25),255) if (x+y)%5==0 else c)
png('item/ascension_template',lambda x,y: CLEAR if not (2<=x<=13 and 2<=y<=13) else GOLD if x in [2,13] or y in [2,13] else PURPLE if x==y or x+y==15 or (x in [7,8] and 4<=y<=11) else DARK)
png('item/mana_supply_upgrade',lambda x,y: CLEAR if not (1<=x<=14 and 1<=y<=14) else BRASS if x in [1,2,13,14] or y in [1,2,13,14] else PURPLE if (x-7.5)**2+(y-7.5)**2<18 else DARK)
png('item/arcane_solution_bottle',lambda x,y: BRASS if 6<=x<=9 and 1<=y<=3 else (193,207,218,255) if ((x in [3,12] and 7<=y<=13) or (y==14 and 4<=x<=11) or (4<=x<=11 and y==6)) else PURPLE if 4<=x<=11 and 8<=y<=13 else (157,179,195,230) if 6<=x<=9 and 4<=y<=6 else CLEAR)
png('item/arcane_solution_bucket',lambda x,y: (180,190,200,255) if ((x in [2,13] and 5<=y<=10) or (x in [3,12] and 11<=y<=13) or (y==14 and 4<=x<=11) or (y==4 and 3<=x<=12)) else PURPLE if 3<=x<=12 and 5<=y<=10 or 4<=x<=11 and 11<=y<=13 else CLEAR)

def generate_chamber_models():
    """Native Burner silhouette: double-sided alpha sheets, never solid cage pillars."""
    import math

    # The approved top is an immutable art input, separate from generated
    # src models/textures. Re-running this function never reads its own output.
    approved_top = Path(__file__).resolve().parents[1] / 'art/revision4-low-eyes/base/textures/chamber_base_top.png'
    top_texture = ROOT / f'assets/{MOD}/textures/block/chamber_base_top.png'
    top_texture.parent.mkdir(parents=True, exist_ok=True)
    top_texture.write_bytes(approved_top.read_bytes())
    textures = {
        'cage': f'{MOD}:block/chamber_cage',
        'base_top': f'{MOD}:block/chamber_base_top',
        'particle': 'create:block/brass_block',
    }
    # UV values below use Minecraft's 0..16 convention. chamber_cage is a 32x32
    # atlas, so multiply each value by two for the original Create pixel position.
    wall_uv = [0.5, 3, 7.5, 7.5]       # Native pixels x1..14, y6..14; 14x9 model units.
    upper_uv = [0.5, 2.5, 7.5, 3]      # Native pixels x1..14, y5; one-pixel folded edge.
    lower_uv = [0.5, 7.5, 7.5, 8]      # Native pixels x1..14, y15.
    foot_uv = [14.5, 8, 16, 10.5]      # Native pixels x29..31, y16..20.

    def sheet(name, start, end, normal_axis, uv, axis=None, angle=0, origin=None):
        # The paired faces intentionally have no cullface: a cage must also be
        # visible through the opposite wall. Exactly one dimension stays zero.
        sides = ('north', 'south') if normal_axis == 'z' else ('west', 'east')
        value = {
            'name': name, 'from': start, 'to': end,
            'faces': {side: {'uv': list(uv), 'texture': '#cage'} for side in sides},
        }
        if axis is not None:
            value['rotation'] = {'origin': origin, 'axis': axis, 'angle': angle, 'rescale': False}
        return value

    # The original 12x4x12 base keeps its geometry. Its side/underside UVs
    # are restored from the native OBJ onto the approved brass cage atlas.
    # The 12x12 top has a native-pixel brass rim and an 8x8 amethyst inset;
    # four-unit sides use precisely four texels, never a compressed 16px tile.
    base = {
        'name': 'brass_wrapped_amethyst_base', 'from': [2, 0, 2], 'to': [14, 4, 14],
        'faces': {side: {'uv': [0, 0, 12, 12] if side == 'up' else [9, 8, 15, 10],
                         'texture': '#base_top' if side == 'up' else '#cage'}
                  for side in ('down', 'up', 'north', 'south', 'west', 'east')},
    }
    elements = [base]
    diagonal = math.sqrt(2)
    # Positions match Create's blaze_cage.obj: four main walls at x/z1 and15,
    # y5..14, folded in to y4 and out to y15, all zero-thickness surfaces.
    for name, fixed, axis, direction in (
            ('south', 15, 'z', 1), ('north', 1, 'z', -1),
            ('east', 15, 'x', 1), ('west', 1, 'x', -1)):
        if axis == 'z':
            elements.append(sheet(name + '_wall', [1, 5, fixed], [15, 14, fixed], 'z', wall_uv))
            rotation_axis = 'x'
            rotation_angle = 45 * direction
            for edge, y, z, uv in (
                    ('upper_fold', 14, fixed, upper_uv),
                    ('lower_fold', 4, fixed - direction, lower_uv)):
                elements.append(sheet(name + '_' + edge, [1, y, z], [15, y + diagonal, z],
                                      'z', uv, rotation_axis, rotation_angle, [8, y, z]))
        else:
            elements.append(sheet(name + '_wall', [fixed, 5, 1], [fixed, 14, 15], 'x', wall_uv))
            rotation_axis = 'z'
            rotation_angle = -45 * direction
            for edge, y, x, uv in (
                    ('upper_fold', 14, fixed, upper_uv),
                    ('lower_fold', 4, fixed - direction, lower_uv)):
                elements.append(sheet(name + '_' + edge, [x, y, 1], [x, y + diagonal, 15],
                                      'x', uv, rotation_axis, rotation_angle, [x, y, 8]))
    # Four diagonal corner feet from the original OBJ. Width sqrt(8) becomes
    # two units on each horizontal axis after +/-45-degree rotation. Height 5.
    for name, x, z, angle in (
            ('south_east', 14, 14, -45), ('north_east', 14, 2, 45),
            ('north_west', 0, 0, -45), ('south_west', 0, 16, 45)):
        elements.append(sheet(name + '_diagonal_foot', [x, 0, z], [x + 2 * diagonal, 5, z],
                              'z', foot_uv, 'y', angle, [x, 0, z]))

    model = {
        'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
        'textures': textures, 'elements': elements,
        'display': {
            'gui': {'rotation': [30, 225, 0], 'scale': [0.7, 0.7, 0.7]},
            'fixed': {'rotation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
        },
    }
    # Derive each front/back UV orientation from the original quad as well as
    # its rectangle bounds. Back faces must use the same physical alpha mask,
    # rather than mirroring a foot's sparse pixels to its other edge.
    reference = Path(__file__).resolve().parents[1] / 'art/reference/assets/create/models/block/blaze_burner/blaze_cage.obj'
    native_vertices, native_uv, native_faces = [], [], []
    for line in reference.read_text().splitlines():
        if line.startswith('v '): native_vertices.append(tuple(float(v) * 16 for v in line.split()[1:]))
        elif line.startswith('vt '):
            u, v = map(float, line.split()[1:])
            native_uv.append((u * 16, (1 - v) * 16))
        elif line.startswith('f '):
            pairs = [token.split('/') for token in line.split()[1:]]
            native_faces.append([(native_vertices[int(pair[0])-1], native_uv[int(pair[1])-1]) for pair in pairs])
    def vertex_key(point): return tuple(round(value, 5) for value in point)
    def quad_key(points): return tuple(sorted(vertex_key(point) for point in points))
    def normal(points):
        a = [points[1][i] - points[0][i] for i in range(3)]
        b = [points[2][i] - points[0][i] for i in range(3)]
        return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
    native_by_quad = {}
    for face in native_faces:
        native_by_quad.setdefault(quad_key([vertex for vertex, uv in face]), []).append(face)

    # Explicit, already-transformed quads for the art-only offline comparison.
    # UVs remain 0..16 regardless of the corresponding image's pixel dimensions.
    quads = []
    for element in elements:
        x0, y0, z0 = element['from']; x1, y1, z1 = element['to']
        corners = {
            'north': [(x1,y1,z0),(x1,y0,z0),(x0,y0,z0),(x0,y1,z0)],
            'south': [(x0,y1,z1),(x0,y0,z1),(x1,y0,z1),(x1,y1,z1)],
            'west': [(x0,y1,z0),(x0,y0,z0),(x0,y0,z1),(x0,y1,z1)],
            'east': [(x1,y1,z1),(x1,y0,z1),(x1,y0,z0),(x1,y1,z0)],
            'up': [(x0,y1,z0),(x0,y1,z1),(x1,y1,z1),(x1,y1,z0)],
            'down': [(x0,y0,z1),(x0,y0,z0),(x1,y0,z0),(x1,y0,z1)],
        }
        def transform(point):
            rotation = element.get('rotation')
            if rotation is None:
                return list(point)
            origin = rotation['origin']
            x, y, z = [point[i] - origin[i] for i in range(3)]
            angle = math.radians(rotation['angle']); c, sn = math.cos(angle), math.sin(angle)
            if rotation['axis'] == 'x': point = (x, y*c-z*sn, y*sn+z*c)
            elif rotation['axis'] == 'y': point = (x*c+z*sn, y, -x*sn+z*c)
            else: point = (x*c-y*sn, x*sn+y*c, z)
            return [round(point[i] + origin[i], 8) for i in range(3)]
        for face, settings in element['faces'].items():
            vertices = [transform(point) for point in corners[face]]
            if settings['texture'] == '#cage':
                ours = normal(vertices)
                candidates = native_by_quad[quad_key(vertices)]
                original = next(candidate for candidate in candidates
                                if sum(a*b for a,b in zip(ours, normal([point for point, uv in candidate]))) > 0)
                uv_by_position = {vertex_key(point): uv for point, uv in original}
                exact_uv = [uv_by_position[vertex_key(point)] for point in vertices]
                settings['uv'] = [exact_uv[0][0], exact_uv[0][1], exact_uv[2][0], exact_uv[2][1]]
                u0, v0, u1, v1 = settings['uv']
                assert exact_uv == [(u0,v0),(u0,v1),(u1,v1),(u1,v0)]
            u0, v0, u1, v1 = settings['uv']
            quads.append({
                'element': element['name'], 'face': face,
                'vertices': vertices,
                'uv': [[u0,v0],[u0,v1],[u1,v1],[u1,v0]],
                'texture': textures[settings['texture'][1:]],
            })
    for state in ('idle', 'active', 'super'):
        write(f'assets/{MOD}/models/block/ascension_chamber_{state}.json', model)
    preview = Path(__file__).resolve().parents[1] / 'art' / 'cage-preview-mesh.json'
    preview.parent.mkdir(parents=True, exist_ok=True)
    preview.write_text(json.dumps({'units': 'model_0_to_16', 'uv_units': '0_to_16',
                                   'texture_sizes': {'cage': [32,32], 'base_top': [16,16]},
                                   'quads': quads}, ensure_ascii=False, indent=2) + '\n')

generate_chamber_models()
write(f'assets/{MOD}/blockstates/ascension_chamber.json',{'variants':{f'state={i}':{'model':f'{MOD}:block/ascension_chamber_{s}'} for i,s in enumerate(['idle','active','super'])}})
write(f'assets/{MOD}/models/item/ascension_chamber.json',{'parent':f'{MOD}:block/ascension_chamber_active'})
for name in ['ascension_template','mana_supply_upgrade','arcane_solution_bottle','arcane_solution_bucket']:
    write(f'assets/{MOD}/models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'{MOD}:item/{name}'}})
write(f'data/{MOD}/loot_tables/blocks/ascension_chamber.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'{MOD}:ascension_chamber'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
write('data/minecraft/tags/blocks/mineable/pickaxe.json',{'replace':False,'values':[f'{MOD}:ascension_chamber']})
write(f'data/{MOD}/tags/fluids/arcane_solution.json',{'replace':False,'values':[f'{MOD}:arcane_solution',f'{MOD}:flowing_arcane_solution']})

zh={
 'itemGroup.arcane_industry':'奥术工业 · 初始原型',
 f'block.{MOD}.ascension_chamber':'奥术升华室',
 f'block.{MOD}.ascension_chamber.super':'超越升华室',
 f'block.{MOD}.arcane_solution':'奥术原液',
 f'fluid_type.{MOD}.arcane_solution':'奥术原液',
 f'item.{MOD}.arcane_solution_bottle':'奥术原液瓶',
 f'item.{MOD}.arcane_solution_bucket':'奥术原液桶',
 f'item.{MOD}.ascension_template':'升华模板',
 f'item.{MOD}.mana_supply_upgrade':'奥术补给升级',
}
en={
 'itemGroup.arcane_industry':'Arcane Industry · Prototype',
 f'block.{MOD}.ascension_chamber':'Arcane Ascension Chamber',
 f'block.{MOD}.ascension_chamber.super':'Transcendent Ascension Chamber',
 f'block.{MOD}.arcane_solution':'Arcane Solution',
 f'fluid_type.{MOD}.arcane_solution':'Arcane Solution',
 f'item.{MOD}.arcane_solution_bottle':'Arcane Solution Bottle',
 f'item.{MOD}.arcane_solution_bucket':'Arcane Solution Bucket',
 f'item.{MOD}.ascension_template':'Ascension Template',
 f'item.{MOD}.mana_supply_upgrade':'Arcane Supply Upgrade',
}
write(f'assets/{MOD}/lang/zh_cn.json',zh);write(f'assets/{MOD}/lang/en_us.json',en)

def recipe(name,data):write(f'data/{MOD}/recipes/{name}.json',data)
def mixing(name,ingredients,results):recipe(name,{'type':'create:mixing','ingredients':ingredients,'results':results})
def fluid(name,amount):return {'fluid':name,'amount':amount}
def item(name):return {'item':name}
solution=f'{MOD}:arcane_solution'
mixing('mix_arcane_solution',[fluid('minecraft:water',250),item('irons_spellbooks:arcane_essence')],[fluid(solution,250)])
mixing('mix_blood_solution',[fluid('irons_spellbooks:blood',250),item('irons_spellbooks:arcane_essence')],[fluid(solution,500)])
mixing('mix_common_ink',[fluid(solution,250),item('minecraft:ink_sac')],[fluid('irons_spellbooks:common_ink',250)])
for lower,upper,catalyst in [('common','uncommon','copper'),('uncommon','rare','iron'),('rare','epic','gold'),('epic','legendary','amethyst')]:
    tag='forge:gems/amethyst' if catalyst=='amethyst' else 'forge:ingots/'+catalyst
    mixing('mix_'+upper+'_ink',[fluid('irons_spellbooks:'+lower+'_ink',1000),{'tag':tag}],[fluid('irons_spellbooks:'+upper+'_ink',250)])
for name,base,amount in [('brew_arcane_solution','minecraft:water',250),('brew_blood_solution','irons_spellbooks:blood',500)]:
    recipe(name,{'type':'irons_spellbooks:alchemist_cauldron_brew','base_fluid':{'FluidName':base,'Amount':250},'input':item('irons_spellbooks:arcane_essence'),'results':[{'FluidName':solution,'Amount':amount}]})
for container,empty,amount in [('bottle','minecraft:glass_bottle',250),('bucket','minecraft:bucket',1000)]:
    full=f'{MOD}:arcane_solution_{container}'
    recipe('fill_solution_'+container,{'type':'create:filling','ingredients':[item(empty),fluid(solution,amount)],'results':[item(full)]})
    recipe('empty_solution_'+container,{'type':'create:emptying','ingredients':[item(full)],'results':[item(empty),fluid(solution,amount)]})
    recipe('cauldron_fill_solution_'+container,{'type':'irons_spellbooks:alchemist_cauldron_fill','fluid':{'FluidName':solution,'Amount':amount},'input':item(full),'result':item(empty)})
    recipe('cauldron_empty_solution_'+container,{'type':'irons_spellbooks:alchemist_cauldron_empty','fluid':{'FluidName':solution,'Amount':amount},'input':item(empty),'result':item(full)})

recipe('ascension_template',{'type':'minecraft:crafting_shapeless','ingredients':[item('create:brass_sheet'),item('irons_spellbooks:blank_rune'),item('create:precision_mechanism'),item('irons_spellbooks:arcane_essence')],'result':item(f'{MOD}:ascension_template')})
recipe('ascension_chamber',{'type':'minecraft:crafting_shaped','pattern':['BRB','PAP','BEB'],'key':{'B':item('create:brass_ingot'),'R':item('irons_spellbooks:blank_rune'),'P':item('create:precision_mechanism'),'A':item('irons_spellbooks:arcane_anvil'),'E':item('irons_spellbooks:arcane_essence')},'result':item(f'{MOD}:ascension_chamber')})
recipe('mana_supply_upgrade',{'type':'minecraft:crafting_shaped','pattern':[' R ','BUB',' E '],'key':{'R':item('irons_spellbooks:blank_rune'),'B':item('create:brass_sheet'),'U':item('sophisticatedbackpacks:upgrade_base'),'E':item('irons_spellbooks:arcane_essence')},'result':item(f'{MOD}:mana_supply_upgrade')})
print('Generated prototype resources and recipes.')
