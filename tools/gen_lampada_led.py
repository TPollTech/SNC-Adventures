"""TuboLED SNC: geometria voxel nativa e materiais pintados128.
preview_entries() não grava recursos; main() é a única etapa de produção.
"""
import base64
import copy
import json
import math
import os
import random
from io import BytesIO
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'intoxicantes')
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'intoxicantes')
MIRROR = os.path.normpath(os.path.join(ROOT, '..', 'resourcepacks', 'minhas-texturas',
                                     'assets', 'intoxicantes', 'textures', 'block'))
T = 128
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
GLYPHS = {
    '0': ['11111','10001','10011','10101','11001','10001','11111'],
    '1': ['00100','01100','00100','00100','00100','00100','01110'],
    '2': ['11111','00001','00001','11111','10000','10000','11111'],
    '3': ['11111','00001','00001','01111','00001','00001','11111'],
    '4': ['10010','10010','10010','11111','00010','00010','00010'],
    '5': ['11111','10000','10000','11111','00001','00001','11111'],
    '6': ['11111','10000','10000','11111','10001','10001','11111'],
    '7': ['11111','00001','00010','00100','01000','01000','01000'],
    '8': ['11111','10001','10001','11111','10001','10001','11111'],
    '9': ['11111','10001','10001','11111','00001','00001','11111'],'S': ['11111', '10000', '10000', '11111', '00001', '00001', '11111'],
    'T': ['11111', '00100', '00100', '00100', '00100', '00100', '00100'],
    'N': ['10001','11001','11001','10101','10011','10011','10001'],
    'C': ['11111','10000','10000','10000','10000','10000','11111'],
    'L': ['10000','10000','10000','10000','10000','10000','11111'],
    'E': ['11111','10000','10000','11110','10000','10000','11111'],
    'D': ['11110','10001','10001','10001','10001','10001','11110'],'W': ['10001', '10001', '10001', '10101', '10101', '11011', '10001'],
    # Alfabeto completo (a fonte pixelada é compartilhada pelos geradores):
    # faltavam letras para as plaquetas/etiquetas dos blocos novos.
    'A': ['01110', '10001', '10001', '11111', '10001', '10001', '10001'],
    'B': ['11110', '10001', '10001', '11110', '10001', '10001', '11110'],
    'F': ['11111', '10000', '10000', '11110', '10000', '10000', '10000'],
    'G': ['01111', '10000', '10000', '10111', '10001', '10001', '01111'],
    'H': ['10001', '10001', '10001', '11111', '10001', '10001', '10001'],
    'I': ['01110', '00100', '00100', '00100', '00100', '00100', '01110'],
    'J': ['00111', '00010', '00010', '00010', '00010', '10010', '01100'],
    'K': ['10001', '10010', '10100', '11000', '10100', '10010', '10001'],
    'M': ['10001', '11011', '10101', '10101', '10001', '10001', '10001'],
    'O': ['01110', '10001', '10001', '10001', '10001', '10001', '01110'],
    'P': ['11110', '10001', '10001', '11110', '10000', '10000', '10000'],
    'Q': ['01110', '10001', '10001', '10001', '10101', '10010', '01101'],
    'R': ['11110', '10001', '10001', '11110', '10100', '10010', '10001'],
    'U': ['10001', '10001', '10001', '10001', '10001', '10001', '01110'],
    'V': ['10001', '10001', '10001', '10001', '10001', '01010', '00100'],
    'X': ['10001', '10001', '01010', '00100', '01010', '10001', '10001'],
    'Y': ['10001', '10001', '01010', '00100', '00100', '00100', '00100'],
    'Z': ['11111', '00001', '00010', '00100', '01000', '10000', '11111'],
    ' ': ['00000'] * 7,
}


def texto(img, text, x, y, color='#e5e6dd', scale=2):
    """Fonte pixelada determinística, sem depender das fontes do computador."""
    g = ImageDraw.Draw(img)
    for ch in text:
        for py, row in enumerate(GLYPHS[ch]):
            for px, bit in enumerate(row):
                if bit == '1':
                    g.rectangle((x + px * scale, y + py * scale,
                                 x + (px + 1) * scale - 1, y + (py + 1) * scale - 1), fill=color)
        x += scale * 6


def material(rgb, kind='metal', seed=19, stamp=None):
    """Pintura128: grão, reflexo, bordas, estrias e região de identificação."""
    img = Image.new('RGBA', (T, T))
    rng = random.Random(seed)
    for y in range(T):
        for x in range(T):
            spec = max(0, 1 - abs(x - 30) / 38) * (14 if kind == 'metal' else 7)
            shade = -9 * y / T + spec + rng.randrange(-3, 4)
            if kind == 'diffuser':
                shade += 3 * math.cos(x / 4) - (5 if x % 16 == 15 else 0)
            elif kind == 'metal':
                shade -= 4 if y % 4 == 3 else 0
            img.putpixel((x, y), tuple(max(0, min(255, int(c + shade))) for c in rgb) + (255,))
    g = ImageDraw.Draw(img)
    g.rectangle((0, 0, 127, 95), outline=tuple(max(0, c - 30) for c in rgb), width=2)
    g.line((3, 2, 125, 2), fill=tuple(min(255, c + 22) for c in rgb), width=2)
    if stamp:
        g.rectangle((0, 96, 127, 127), fill='#23363e', outline='#a5b6b7', width=2)
        texto(img, stamp, 5, 103, scale=2)
    return img


def data_url(img):
    stream = BytesIO()
    img.save(stream, format='PNG')
    return 'data:image/png;base64,' + base64.b64encode(stream.getvalue()).decode('ascii')


def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as stream:
        json.dump(obj, stream, indent=2, ensure_ascii=False)
        stream.write('\n')


def png(name, img):
    for folder in (os.path.join(ASSETS, 'textures', 'block'), MIRROR):
        os.makedirs(folder, exist_ok=True)
        img.save(os.path.join(folder, name))


def caixa(f, t, tex, uv=(0, 0, 16, 12), faces=FACES):
    return {'from': list(f), 'to': list(t),
            'faces': {face: {'texture': tex, 'uv': list(uv)} for face in faces}}


def display(scale=.75, gui_rotation=(25, 225, 0)):
    return {
        'gui': {'rotation': list(gui_rotation), 'translation': [0, 1, 0], 'scale': [scale] * 3},
        'ground': {'translation': [0, 0, 0], 'scale': [.4] * 3},
        'fixed': {'rotation': [0, 180, 0], 'scale': [.65] * 3},
        'firstperson_righthand': {'rotation': [0, 165, -8], 'translation': [0, 2, -1], 'scale': [.55] * 3},
        'firstperson_lefthand': {'rotation': [0, 165, -8], 'translation': [0, 2, -1], 'scale': [.55] * 3},
        'thirdperson_righthand': {'rotation': [90, 0, 0], 'translation': [0, 2, 1], 'scale': [.5] * 3},
        'thirdperson_lefthand': {'rotation': [90, 0, 0], 'translation': [0, 2, 1], 'scale': [.5] * 3},
    }


def tex_difusor():
    return material((245, 243, 219), 'diffuser', 192)


def tex_ponteira():
    return material((181, 190, 193), 'metal', 210, 'SNC LED')


def tex_suporte():
    return material((63, 76, 79), 'metal', 46)


def modelo():
    elements = [
        caixa((2, 7, 6), (14, 9, 10), '#difusor'),
        caixa((2, 6, 7), (14, 7, 9), '#difusor'),
        caixa((2, 9, 7), (14, 10, 9), '#difusor'),
        caixa((1, 10, 5.8), (15, 10.6, 10.2), '#ponteira'),
        caixa((4, 10.6, 6.9), (12, 11.1, 9.1), '#suporte'),
        caixa((4, 9.4, 5.77), (12, 10.4, 5.82), '#ponteira', (0, 12, 16, 16), ('north',)),
    ]
    for x in (0, 14):
        elements.extend([
            caixa((x, 6, 6), (x + 2, 10, 10), '#ponteira'),
            caixa((x + .25, 5.5, 6.5), (x + 1.75, 6, 9.5), '#suporte'),
            caixa((x + .25, 10, 6.5), (x + 1.75, 11, 9.5), '#ponteira'),
            caixa((x + .7, 11, 7.6), (x + 1.3, 15.5, 8.4), '#suporte'),
            caixa((x, 15.5, 6.5), (x + 2, 16, 9.5), '#ponteira'),
        ])
        for z in (6.6, 9):
            elements.append(caixa((x + .6, 10.96, z), (x + 1.4, 11.16, z + .4), '#suporte'))
    return {'parent': 'minecraft:block/block', 'texture_size': [T, T],
            'credit': 'SNC — tubo facetado, bandeja, ponteiras, parafusos e suspensões',
            'textures': {'difusor': 'intoxicantes:block/lampada_led',
                         'ponteira': 'intoxicantes:block/lampada_led_fim',
                         'suporte': 'intoxicantes:block/lampada_led_suporte',
                         'particle': 'intoxicantes:block/lampada_led_fim'},
            'elements': elements, 'display': display()}


MODELO_BLOCO = modelo()


def preview_entries():
    textures = {'intoxicantes:block/' + name: data_url(img) for name, img in (
        ('lampada_led', tex_difusor()), ('lampada_led_fim', tex_ponteira()),
        ('lampada_led_suporte', tex_suporte()))}
    return [{'id': 'lampada_led', 'name': 'TuboLED do Esquinão', 'category': 'Iluminação',
             'model': copy.deepcopy(MODELO_BLOCO), 'textures': textures,
             'description': 'Tubo facetado de acrílico leitoso, alumínio escovado, ponteiras e fixações de teto.'}]


def main():
    png('lampada_led.png', tex_difusor())
    png('lampada_led_fim.png', tex_ponteira())
    png('lampada_led_suporte.png', tex_suporte())
    wjson(os.path.join(ASSETS, 'blockstates', 'lampada_led.json'),
          {'variants': {'': {'model': 'intoxicantes:block/lampada_led'}}})
    wjson(os.path.join(ASSETS, 'models', 'block', 'lampada_led.json'), MODELO_BLOCO)
    wjson(os.path.join(ASSETS, 'models', 'item', 'lampada_led.json'), {'parent': 'intoxicantes:block/lampada_led'})
    wjson(os.path.join(ASSETS, 'items', 'lampada_led.json'),
          {'model': {'type': 'minecraft:model', 'model': 'intoxicantes:block/lampada_led'}})
    wjson(os.path.join(DATA, 'loot_table', 'blocks', 'lampada_led.json'), {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'bonus_rolls': 0,
        'entries': [{'type': 'minecraft:item', 'name': 'intoxicantes:lampada_led'}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}],
        'random_sequence': 'intoxicantes:blocks/lampada_led'}]})
    print('gen_lampada_led: tubo detalhado + 3 materiais128 sincronizados no pack')


if __name__ == '__main__':
    main()
