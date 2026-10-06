"""Mobília do mercado — modelos nativos com o piso 3D da caixa aprovada.

preview_entries() pinta tudo em memória; main() só após aprovação visual.
Frente = -Z; blockstate gira o modelo completo (incluindo UVs e pivôs).
UV de entrada em pixels do atlas 128; saída Minecraft normalizada 0..16.
"""
import copy
import math
import os
import random

from PIL import Image, ImageDraw
from gen_lampada_led import ASSETS, DATA, T, data_url, display, png, texto, wjson

FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def _base(rgb, seed, brilho=0):
    rng = random.Random(seed)
    img = Image.new('RGBA', (T, T))
    for y in range(T):
        for x in range(T):
            k = rng.uniform(-1.8, 1.8) + brilho + 5 * math.exp(-((x - 38) / 36) ** 2)
            img.putpixel((x, y), tuple(max(0, min(255, round(c + k))) for c in rgb) + (255,))
    return img


def madeira(rgb, seed, tabuas=1, horizontal=True):
    """Uma tábua por peça: frestas são geometria, nunca linhas pintadas."""
    img = _base(rgb, seed)
    g = ImageDraw.Draw(img)
    rng = random.Random(seed)
    for i in range(80):
        y = rng.randrange(T)
        x = rng.randrange(90)
        tom = tuple(max(0, c + rng.randrange(-18, 9)) for c in rgb)
        g.line((x, y, min(127, x + rng.randrange(8, 65)), y + rng.choice((-1, 0, 1))), fill=tom)
    g.ellipse((73, 52, 108, 65), outline=tuple(c - 18 for c in rgb), width=1)
    g.ellipse((81, 56, 100, 62), outline=tuple(c - 26 for c in rgb), width=1)
    return img


def inox(rgb, seed, escovado='x'):
    img = _base(rgb, seed)
    g = ImageDraw.Draw(img)
    rng = random.Random(seed + 3)
    for _ in range(90):
        k = rng.randrange(-5, 6)
        c = tuple(max(0, min(255, v + k)) for v in rgb)
        p = rng.randrange(T)
        g.line((0, p, 127, p) if escovado == 'x' else (p, 0, p, 127), fill=c)
    return img


def _plastico(rgb, seed, faixa=None):
    return _base(rgb, seed)


def etiqueta(titulo='ESTOQUE', fundo=(228, 218, 193), cor='#473b2d'):
    img = _base(fundo, 37)
    g = ImageDraw.Draw(img)
    # Ilha 128x64 com a mesma proporção da plaqueta física.
    g.rectangle((0, 0, 127, 63), outline='#aa9776', width=2)
    texto(img, titulo, (128 - len(titulo) * 12 + 2) // 2, 8, cor, 2)
    texto(img, 'SNC', 9, 30, '#6a5e49', 1)
    texto(img, 'LOTE 04', 59, 30, '#6a5e49', 1)
    rng = random.Random(73)
    x = 9
    while x < 119:
        w = rng.choice((1, 2, 3))
        g.rectangle((x, 43, x + w, 55), fill='#5a4d3b')
        x += w + rng.choice((2, 3))
    return img


def elemento(f, t, faces, nome='', rotation=None):
    el = {'from': list(f), 'to': list(t), 'name': nome,
          'faces': {face: {'texture': tex, 'uv': [v / 8 for v in uv]}
                    for face, (tex, uv) in faces.items()}}
    if rotation:
        el['rotation'] = copy.deepcopy(rotation)
    return el


def peca(e, nome, f, t, tex, overrides=None, rotation=None):
    dx, dy, dz = (t[i] - f[i] for i in range(3))
    faces = {}
    for face in FACES:
        a, b = (dx, dz) if face in ('up', 'down') else ((dz, dy) if face in ('east', 'west') else (dx, dy))
        faces[face] = ('#' + tex, (4, 4, 4 + min(120, a * 7), 4 + min(120, b * 7)))
    faces.update(overrides or {})
    e.append(elemento(f, t, faces, nome, rotation))


def modelo(e, textures, credit):
    refs = {k: 'intoxicantes:block/' + v for k, v in textures.items()}
    refs['particle'] = next(iter(refs.values()))
    return {'parent': 'minecraft:block/block', 'texture_size': [T, T], 'credit': credit,
            'textures': refs, 'elements': e, 'display': display()}


def circular(e, nome, centro, raio, inicio, fim, tex, eixo='y', interno=0, passos=24):
    """Disco/anel voxelizado por faixas, sem cubo com círculo pintado.

    Cuboides disjuntos em seção: cilindro em Y ou aro/tambor em Z.
    O raio interno produz um vão REAL (porta, encaixe, pingadeira).
    """
    passo = 2 * raio / passos
    for i in range(passos):
        a, b = -raio + i * passo, -raio + (i + 1) * passo
        meio = (a + b) / 2
        externo = math.sqrt(max(0, raio * raio - meio * meio))
        dentro = math.sqrt(max(0, interno * interno - meio * meio)) if abs(meio) < interno else 0
        bandas = [(-externo, -dentro), (dentro, externo)] if dentro else [(-externo, externo)]
        for c, d in bandas:
            if d - c < .015:
                continue
            if eixo == 'y':
                f, t = (centro[0] + a, inicio, centro[1] + c), (centro[0] + b, fim, centro[1] + d)
            else:
                f, t = (centro[0] + a, centro[1] + c, inicio), (centro[0] + b, centro[1] + d, fim)
            peca(e, nome, f, t, tex)


# ============================================================ ENGRADADO

def tex_engradado():
    return {'engradado_corpo': madeira((150, 111, 69), 11),
            'engradado_tampa': madeira((127, 89, 51), 23),
            'engradado_metal': inox((91, 100, 105), 31),
            'engradado_etiqueta': etiqueta()}


def modelo_engradado():
    e = []
    add = lambda nome, f, t, tex='corpo', faces=None: peca(e, nome, f, t, tex, faces)
    # Fundo ripado, aberto em cima. Postes chegam a y16 para empilhar sem voo.
    for i in range(5):
        x = 1 + i * 2.82
        add('tabua_fundo', (x, .5, 1), (x + 2.66, 1.35, 15), 'tampa')
    for z in (2.3, 12.6):
        add('travessa_fundo', (.7, 0, z), (15.3, .5, z + 1.1), 'tampa')
    for x in (.65, 14.15):
        for z in (.65, 14.15):
            add('poste_canto', (x, .5, z), (x + 1.2, 16, z + 1.2), 'tampa')
    for y in (1.7, 5.25, 8.8):
        for z in (.5, 14.6):
            add('ripa_frente_costas', (.8, y, z), (15.2, y + 3.1, z + .9))
        for x in (.5, 14.6):
            add('ripa_lateral', (x, y, 1.4), (x + .9, y + 3.1, 14.6))
    # Aro superior com alças VAZADAS nos quatro lados.
    for z in (.5, 14.6):
        for x0, x1, y0, y1 in ((.8, 15.2, 12.35, 13.15), (.8, 15.2, 15.05, 16),
                               (.8, 4.85, 13.15, 15.05), (11.15, 15.2, 13.15, 15.05)):
            add('alca_vazada', (x0, y0, z), (x1, y1, z + .9), 'tampa')
    for x in (.5, 14.6):
        for z0, z1, y0, y1 in ((1.4, 14.6, 12.35, 13.15), (1.4, 14.6, 15.05, 16),
                               (1.4, 4.85, 13.15, 15.05), (11.15, 14.6, 13.15, 15.05)):
            add('alca_vazada', (x, y0, z0), (x + .9, y1, z1), 'tampa')
    # Cantoneiras exteriores, fixadores aparentes e papel fino nas quatro faces.
    for x in (.6, 14.15):
        for z in (.39, 15.50):
            add('cantoneira', (x, 1.65, z), (x + 1.25, 11.95, z + .11), 'metal')
            for y in (2.3, 6.1, 10.3):
                add('parafuso', (x + .46, y, z - .025), (x + .71, y + .25, z + .14), 'metal')
    for face, f, t in (('north', (4.95, 5.58, .455), (11.05, 8.63, .485)),
                       ('south', (4.95, 5.58, 15.515), (11.05, 8.63, 15.545)),
                       ('west', (.455, 5.58, 4.95), (.485, 8.63, 11.05)),
                       ('east', (15.515, 5.58, 4.95), (15.545, 8.63, 11.05))):
        add('etiqueta_' + face, f, t, 'tampa', {face: ('#etiqueta', (0, 0, 128, 64))})
    return modelo(e, {'corpo': 'engradado_corpo', 'tampa': 'engradado_tampa',
                       'metal': 'engradado_metal', 'etiqueta': 'engradado_etiqueta'},
                  'SNC — engradado aberto ripado, alças vazadas, cantoneiras e etiqueta legível em quatro lados')


# ============================================================ BEBEDOURO

def tex_bebedouro():
    atlas = _base((42, 49, 55), 51)
    g = ImageDraw.Draw(atlas)
    g.rectangle((0, 0, 127, 31), fill='#e4e9e9')
    texto(atlas, 'SNC AGUA', 16, 8, '#516e7c', 2)
    for box, color in (((0, 40, 31, 71), '#416fa3'), ((40, 40, 71, 71), '#b95953'),
                       ((80, 40, 111, 71), '#b8c0c6')):
        g.rectangle(box, fill=color)
    g.rectangle((0, 80, 127, 111), fill='#334047')
    for x in range(5, 125, 8):
        g.line((x, 83, x, 108), fill='#7b8b94', width=2)
    return {'bebedouro_armario': _plastico((225, 230, 232), 41),
            'bebedouro_inox': inox((167, 176, 182), 53),
            'bebedouro_torneira': _plastico((45, 52, 58), 59),
            'bebedouro_painel': atlas}


def modelo_bebedouro():
    e = []
    def add(nome, f, t, tex='armario', faces=None):
        peca(e, nome, f, t, tex, faces)
    for x in (3.16, 11.75):
        for z in (3.03, 11.80):
            add('pe_borracha', (x, 0, z), (x + 1.08, .55, z + 1.08), 'torneira')
    add('rodape_recuado', (3.12, .45, 3.02), (12.88, 1.25, 12.92), 'torneira')
    add('gabinete_inferior', (2.8, 1.05, 2.65), (13.2, 8.76, 13.2))
    add('painel_porta_bisel', (3.01, 1.52, 2.43), (12.99, 8.51, 2.66), 'inox')
    add('painel_porta', (3.20, 1.70, 2.34), (12.80, 8.32, 2.44))
    add('puxador_porta', (6.3, 7.64, 2.18), (9.7, 7.88, 2.36), 'torneira')
    # Nicho realmente aberto: laterais + fundo, sem gabinete sólido detrás das bicas.
    add('lateral_esquerda', (2.8, 8.7, 2.65), (3.62, 15.42, 13.2))
    add('lateral_direita', (12.38, 8.7, 2.65), (13.2, 15.42, 13.2))
    add('costas_superiores', (3.62, 8.7, 6.65), (12.38, 15.42, 13.2))
    add('fundo_nicho', (3.75, 9.01, 6.52), (12.25, 13.50, 6.65), 'torneira')
    add('faixa_superior', (3.5, 13.52, 2.53), (12.5, 15.43, 6.75))
    add('marca_frontal', (5.12, 14.08, 2.49), (10.88, 15.52, 2.53), 'armario',
        {'north': ('#painel', (0, 0, 128, 32))})
    add('tampo_inferior', (2.63, 15.38, 2.48), (13.37, 15.76, 13.38), 'inox')
    add('tampo_capa', (2.78, 15.76, 2.63), (13.22, 16, 13.22))
    circular(e, 'encaixe_gargalo', (8, 8), 2.0, 15.88, 16.10, 'torneira', interno=1.10, passos=16)
    # Duas torneiras: acionador azul (gelada), vermelho (natural), bico para baixo.
    for x, uv in ((5.38, (0, 40, 32, 72)), (9.30, (40, 40, 72, 72))):
        add('torneira_fixacao', (x - .16, 11.63, 5.66), (x + 1.28, 12.98, 6.56), 'torneira')
        add('torneira_corpo', (x, 11.86, 4.80), (x + 1.12, 12.60, 5.83), 'armario')
        add('bico_vertical', (x + .29, 11.33, 4.72), (x + .81, 12.03, 5.23), 'torneira')
        add('boca_bico', (x + .37, 11.28, 4.81), (x + .73, 11.33, 5.14), 'torneira')
        add('alavanca_cor', (x + .02, 12.64, 4.33), (x + 1.10, 12.87, 5.75), 'torneira',
            {'up': ('#painel', uv), 'north': ('#painel', uv)})
        add('haste_alavanca', (x + .43, 12.52, 5.26), (x + .69, 13.13, 5.49), 'torneira')
    # Bandeja/pingadeira larga e baixa com grade horizontal, não um copo gigante.
    add('bandeja_base', (3.70, 8.82, 1.83), (12.30, 9.11, 6.70), 'torneira')
    for x in (3.7, 12.05):
        add('bandeja_lateral', (x, 9.10, 1.83), (x + .25, 9.35, 6.70), 'inox')
    add('bandeja_labio', (3.7, 9.10, 1.83), (12.3, 9.35, 2.1), 'inox')
    for i in range(10):
        x = 4.13 + i * .78
        add('grade_pingadeira', (x, 9.16, 2.24), (x + .18, 9.24, 6.28), 'inox')
    for y in (2.0, 2.6, 3.2, 3.8):
        add('respiro_costas', (4.6, y, 13.21), (11.4, y + .15, 13.27), 'torneira')
    return modelo(e, {'armario': 'bebedouro_armario', 'inox': 'bebedouro_inox',
                       'torneira': 'bebedouro_torneira', 'painel': 'bebedouro_painel'},
                  'SNC — bebedouro de piso com nicho, bicas acionáveis, bandeja gradeada e encaixe de garrafão')


def tex_garrafao():
    pet = _base((82, 145, 182), 79)
    g = ImageDraw.Draw(pet)
    # PET estilizado OPACO: leitura do plástico sem depender de blend no jogo.
    for x in range(T):
        k = 20 * math.exp(-((x - 29) / 18) ** 2) - 7 * x / T
        for y in range(T):
            rgb = pet.getpixel((x, y))[:3]
            pet.putpixel((x, y), tuple(max(0, min(255, round(v + k))) for v in rgb) + (255,))
    g.line((22, 4, 22, 123), fill='#aacdde', width=2)
    return {'garrafao_pet': pet,
            'garrafao_nervura': _base((97, 160, 197), 81),
            'garrafao_tampa': _plastico((51, 87, 127), 73)}


def modelo_garrafao():
    e = []
    # INVERTIDO: gargalo embaixo no encaixe, fundo arredondado em cima.
    circular(e, 'gargalo_tampa', (8, 8), 1.05, 0, .54, 'tampa', passos=12)
    circular(e, 'gargalo', (8, 8), 1.0, .54, 1.63, 'pet', passos=12)
    for y0, y1, r in ((1.63, 2.02, 1.40), (2.02, 2.45, 1.91), (2.45, 2.90, 2.48),
                       (2.90, 3.39, 3.01), (3.39, 3.91, 3.52), (3.91, 4.35, 3.83),
                       (4.35, 11.63, 3.97), (11.63, 12.03, 3.83),
                       (12.03, 12.38, 3.51), (12.38, 12.66, 3.10)):
        circular(e, 'ombro' if y0 < 4.35 else 'corpo', (8, 8), r, y0, y1, 'pet')
    for y in (4.65, 6.38, 8.91, 10.70):
        circular(e, 'nervura_circular', (8, 8), 4.12, y, y + .30, 'nervura', interno=3.96)
    circular(e, 'fundo_invertido', (8, 8), 2.43, 12.66, 12.82, 'nervura')
    return modelo(e, {'pet': 'garrafao_pet', 'nervura': 'garrafao_nervura', 'tampa': 'garrafao_tampa'},
                  'SNC — garrafão invertido facetado com gargalo para baixo, ombros e nervuras em volume')


# ============================================================ LAVADORA

def tex_lavadora():
    painel = _base((221, 226, 227), 83)
    g = ImageDraw.Draw(painel)
    # Atlas com ilhas proporcionais a cada superfície. Nada de painel quadrado esticado.
    g.rectangle((0, 0, 95, 23), fill='#d5dedf')
    texto(painel, 'SNC', 6, 5, '#4c6570', 2)
    texto(painel, 'LAVAR', 50, 8, '#647983', 1)
    g.rectangle((0, 32, 95, 63), fill='#182d33')
    texto(painel, '35', 7, 38, '#a5d6cf', 2)
    texto(painel, 'C', 39, 39, '#a5d6cf', 1)
    texto(painel, '0 45', 55, 43, '#81b4ae', 1)
    g.rectangle((0, 72, 95, 95), fill='#d9dfe0')
    texto(painel, 'PROGRAMA', 7, 80, '#60727a', 1)
    g.rectangle((0, 104, 95, 127), fill='#e3e7e6')
    texto(painel, '8 KG', 27, 112, '#6c7a80', 1)
    g.rectangle((104, 0, 127, 23), fill='#638c84')
    g.rectangle((111, 5, 116, 16), fill='#e7f4ea')
    vidro = _base((43, 69, 82), 93)
    vg = ImageDraw.Draw(vidro)
    # Um só vidro no disco voxelizado; UVs serão projetados, sem repetir reflexo por faixa.
    vg.arc((6, 6, 120, 120), 202, 302, fill='#7eacbc', width=5)
    vg.arc((21, 21, 105, 105), 220, 277, fill='#accbd3', width=2)
    vg.arc((21, 21, 105, 105), 25, 80, fill='#4f879a', width=4)
    return {'lavadora_corpo': _plastico((224, 227, 225), 87),
            'lavadora_painel': painel, 'lavadora_porta': vidro,
            'lavadora_inox': inox((171, 179, 185), 101),
            'lavadora_borracha': _base((38, 43, 47), 103)}


def modelo_lavadora():
    e = []
    def add(nome, f, t, tex='corpo', faces=None):
        peca(e, nome, f, t, tex, faces)
    for x in (1.8, 12.9):
        for z in (2.15, 12.85):
            add('pe_nivelador', (x, 0, z), (x + 1.15, .6, z + 1.15), 'borracha')
    add('rodape_recuado', (1.7, .5, 1.4), (14.3, 1.2, 14.55), 'borracha')
    # O túnel do tambor atravessa a carcaça; chapa traseira permanece atrás dele.
    add('gabinete_lateral_esquerda', (1.1, 1.05, 3.1), (3.3, 14.7, 15.2))
    add('gabinete_lateral_direita', (12.7, 1.05, 3.1), (14.9, 14.7, 15.2))
    add('gabinete_tampa', (3.3, 11.9, 3.1), (12.7, 14.7, 15.2))
    add('gabinete_base', (3.3, 1.05, 3.1), (12.7, 2.5, 15.2))
    add('lateral_esquerda', (.9, .85, 1.15), (1.52, 14.85, 15.18))
    add('lateral_direita', (14.48, .85, 1.15), (15.1, 14.85, 15.18))
    add('tampo_borda', (.65, 14.65, .85), (15.35, 15.65, 15.4), 'inox')
    add('tampo_capa', (.80, 15.65, 1.00), (15.20, 16, 15.25))
    cx, cy, r = 8, 7.20, 4.70
    # As chapas frontais e traseiras contornam a porta, sem fechar o tambor.
    add('frente_chapa', (1.45, 1.0, 1.45), (14.55, 14.68, 3.1))
    add('frente_chapa', (1.45, 1.0, 1.0), (3.30, 14.68, 1.45))
    add('frente_chapa', (12.70, 1.0, 1.0), (14.55, 14.68, 1.45))
    add('chapa_traseira', (1.1, 1.05, 15.2), (14.9, 14.7, 15.38), 'inox')
    add('chapa_superior', (1.1, 14.55, 1.45), (14.9, 14.72, 15.2), 'inox')
    add('chapa_inferior', (1.1, 1.02, 1.45), (14.9, 1.20, 15.2), 'inox')
    add('chapa_esquerda', (1.1, 1.2, 1.45), (3.3, 14.55, 15.2), 'inox')
    add('chapa_direita', (12.7, 1.2, 1.45), (14.9, 14.55, 15.2), 'inox')
    add('frente_base', (1.45, .96, 1.0), (14.55, 2.50, 1.45))
    add('frente_alto', (1.45, 11.86, 1.0), (14.55, 14.68, 1.45))
    add('frente_esquerda', (1.45, 2.50, 1.0), (3.30, 11.86, 1.45))
    add('frente_direita', (12.70, 2.50, 1.0), (14.55, 11.86, 1.45))
    # A frente conserva o recorte circular real; as placas de contorno deixam
    # o vidro à vista em vez de escondê-lo atrás de uma chapa contínua.
    circular(e, 'junta_porta', (cx, cy), 4.70, .78, 1.03, 'borracha', eixo='z', interno=4.24)
    circular(e, 'aro_inox', (cx, cy), 4.45, .23, .79, 'inox', eixo='z', interno=3.72)
    circular(e, 'aro_chanfro', (cx, cy), 4.23, .09, .25, 'corpo', eixo='z', interno=3.66)
    circular(e, 'guarnicao_vidro', (cx, cy), 3.73, .18, .51, 'borracha', eixo='z', interno=3.42)
    circular(e, 'aro_tambor', (cx, cy), 3.02, 2.80, 3.10, 'inox', eixo='z', interno=2.43)
    circular(e, 'fundo_tambor', (cx, cy), 2.40, 2.72, 2.84, 'borracha', eixo='z')
    add('cavidade_tambor', (3.5, 4.1, 1.46), (12.5, 10.3, 2.7), 'borracha')
    for x in (6.0, 7.35, 8.70, 10.05):
        add('nervura_tambor', (x, 4.85, 2.42), (x + .34, 9.55, 2.78), 'inox')
    # Vidro afundado com UV projetado no disco inteiro, não uma textura por ripa.
    n = len(e)
    circular(e, 'vidro_porta', (cx, cy), 3.43, .42, .52, 'porta', eixo='z')
    for el in e[n:]:
        x0,y0,_ = el['from']; x1,y1,_ = el['to']
        el['faces']['north']['uv'] = [(x0-cx+3.43)/6.86*16, (cy+3.43-y1)/6.86*16,
                                      (x1-cx+3.43)/6.86*16, (cy+3.43-y0)/6.86*16]
    add('dobradica_superior', (3.56, 8.70, .38), (4.36, 9.57, .94), 'inox')
    add('dobradica_inferior', (3.56, 4.81, .38), (4.36, 5.68, .94), 'inox')
    # Faixa de comando: gaveta detergente, seletor físico, LCD separado, botões.
    add('painel_bisel', (1.72, 12.10, .76), (14.27, 14.51, 1.05), 'inox')
    add('painel_face', (1.86, 12.24, .68), (14.13, 14.37, .78))
    add('gaveta_detergente', (2.03, 12.45, .43), (5.40, 14.19, .69), 'corpo',
        {'north': ('#painel', (0, 0, 96, 24))})
    add('guia_gaveta_superior', (2.02, 14.20, .68), (5.42, 14.31, .96), 'inox')
    add('guia_gaveta_inferior', (2.02, 12.33, .68), (5.42, 12.44, .96), 'inox')
    for bx in (8.50, 9.45, 10.40, 11.35):
        add('botao_comando', (bx, 12.30, .38), (bx + .24, 12.55, .64), 'inox')
    for by in (12.08, 14.54):
        add('rebite_painel_a', (1.83, by, .66), (2.02, by + .18, .86), 'borracha')
        add('rebite_painel_b', (13.98, by, .66), (14.17, by + .18, .86), 'borracha')
    # Puxador escavado em U: montantes e pega projetada, deixando vão real.
    add('puxador_esquerdo', (11.82, 6.57, .06), (12.10, 7.94, .34), 'inox')
    add('puxador_direito', (12.38, 6.57, .06), (12.66, 7.94, .34), 'inox')
    add('puxador_ponte', (12.06, 7.72, -.12), (12.43, 7.99, .35), 'inox')
    add('moldura_externa_porta', (3.05, 2.45, .72), (12.95, 11.95, .92), 'inox')
    add('fecho_porta', (11.80, 6.45, .28), (12.22, 7.15, .55), 'inox')
    add('trava_porta', (11.70, 6.75, .15), (11.94, 6.98, .39), 'borracha')
    for x in (3.8, 12.2):
        for y in (2.4, 11.9):
            add('rebite_frente', (x, y, 1.0), (x + .18, y + .18, 1.2), 'inox')
    # Mangueira de drenagem conectada ao fundo, sem sair da caixa.
    for f,t in (((11.55,2.0,15.36),(11.86,5.1,15.72)),
                ((11.80,4.85,15.42),(13.2,5.16,15.72)),
                ((13.08,4.95,15.39),(13.38,8.0,15.70))):
        add('mangueira_drenagem', f,t,'borracha')
    add('entrada_agua_quente', (5.8, 12.2, 15.36), (6.55, 12.95, 15.82), 'inox')
    add('entrada_agua_fria', (7.0, 12.2, 15.36), (7.75, 12.95, 15.82), 'inox')
    add('puxador_detergente', (2.41, 12.48, .20), (5.04, 12.69, .43), 'inox')
    circular(e, 'seletor_bisel', (6.88, 13.35), .87, .45, .70, 'borracha', eixo='z', passos=16)
    circular(e, 'seletor_programa', (6.88, 13.35), .71, .10, .47, 'inox', eixo='z', passos=16)
    add('indicador_seletor', (6.81, 13.68, .065), (6.95, 13.97, .11), 'borracha')
    add('LCD_bisel', (8.22, 12.56, .47), (12.38, 14.07, .70), 'borracha')
    add('selecao_linha', (8.58, 12.84, .43), (8.78, 13.83, .47), 'inox')
    add('LCD', (8.38, 12.70, .45), (12.22, 13.98, .475), 'painel',
        {'north': ('#painel', (0, 32, 96, 64))})
    add('botao_inicio', (12.83, 12.96, .30), (13.65, 13.78, .69), 'inox',
        {'north': ('#painel', (104, 0, 128, 24))})
    add('marca_capacidade', (3.1, 1.38, .95), (6.2, 2.15, 1.005), 'corpo',
        {'north': ('#painel', (0, 104, 96, 128))})
    add('portinhola_filtro', (11.65, 1.16, .89), (13.8, 2.24, 1.02), 'inox')
    add('portinhola_inserto', (11.82, 1.32, .84), (13.63, 2.08, .9))
    # Costas: chapa, conexão e mangueira segmentada, apoiada no gabinete.
    add('painel_costas', (2.3, 2.0, 15.2), (13.7, 13.9, 15.37), 'inox')
    for x in (3, 12.7):
        for y in (2.6, 13.1):
            add('parafuso_costas', (x, y, 15.37), (x + .23, y + .23, 15.43), 'borracha')
    add('conector_agua', (3.7, 12.1, 15.35), (4.45, 12.9, 15.86), 'inox')
    for f,t in (((4.06, 6.0, 15.48),(4.39,12.6,15.80)),
                ((4.1,5.7,15.48),(10.5,6.02,15.80)),
                ((10.18,5.8,15.48),(10.50,9.9,15.80))):
        add('mangueira', f,t,'borracha')
    return modelo(e, {'corpo': 'lavadora_corpo', 'painel': 'lavadora_painel', 'porta': 'lavadora_porta',
                       'inox': 'lavadora_inox', 'borracha': 'lavadora_borracha'},
                  'SNC — lavadora frontal com aro circular em volume, vidro rebaixado, seletor, gaveta, LCD e mangueira')


PECAS = (
    {'id': 'engradado_mercado', 'nome': 'Engradado de estoque', 'categoria': 'Mobiliário do salão',
     'descricao': 'Caixote aberto de ripas reais, fundo e cantoneiras, alças vazadas e etiqueta legível nos quatro lados. Empilhável sem rótulo escondido na parede.',
     'tex': tex_engradado, 'modelo': modelo_engradado},
    {'id': 'bebedouro_mercado', 'nome': 'Bebedouro de piso', 'categoria': 'Mobiliário do salão',
     'descricao': 'Gabinete branco com nicho aberto, duas torneiras com acionadores azul/vermelho, bandeja gradeada e encaixe no topo. O garrafão invertido encaixa pelo gargalo.',
     'tex': tex_bebedouro, 'modelo': modelo_bebedouro, 'orientado': True},
    {'id': 'bebedouro_garrafao', 'nome': 'Garrafão invertido', 'categoria': 'Mobiliário do salão',
     'descricao': 'Garrafão azul facetado, ombros e nervuras circulares em volume; gargalo para baixo. PET estilizado opaco para não prometer transparência não validada no jogo.',
     'tex': tex_garrafao, 'modelo': modelo_garrafao},
    {'id': 'lavadora_mercado', 'nome': 'Máquina de lavar frontal', 'categoria': 'Mobiliário do salão',
     'descricao': 'Porta circular modelada com aro, guarnição, vidro rebaixado e puxador. Gaveta de detergente, seletor e botões em relevo; LCD com ilha proporcional, sem letras esticadas.',
     'tex': tex_lavadora, 'modelo': modelo_lavadora, 'orientado': True},
)


def preview_entries():
    return [{'id': p['id'], 'name': p['nome'], 'category': p['categoria'],
             'model': p['modelo'](), 'textures': {'intoxicantes:block/' + nome: data_url(img)
                                                for nome,img in p['tex']().items()},
             'description': p['descricao']} for p in PECAS]


def main():
    for p in PECAS:
        for nome,img in p['tex']().items():
            png(nome + '.png', img)
        alvo = 'intoxicantes:block/' + p['id']
        wjson(os.path.join(ASSETS, 'models', 'block', p['id'] + '.json'), p['modelo']())
        variantes = {f'facing={f}': {'model': alvo, **({'y': y} if y else {})}
                     for f,y in (('north',0),('east',90),('south',180),('west',270))} \
                    if p.get('orientado') else {'': {'model': alvo}}
        wjson(os.path.join(ASSETS, 'blockstates', p['id'] + '.json'), {'variants': variantes})
        wjson(os.path.join(ASSETS, 'models', 'item', p['id'] + '.json'), {'parent': alvo})
        wjson(os.path.join(ASSETS, 'items', p['id'] + '.json'),
              {'model': {'type': 'minecraft:model', 'model': alvo}})
        wjson(os.path.join(DATA, 'loot_table', 'blocks', p['id'] + '.json'), {
            'type': 'minecraft:block', 'pools': [{'rolls': 1, 'bonus_rolls': 0,
                'entries': [{'type': 'minecraft:item', 'name': 'intoxicantes:' + p['id']}],
                'conditions': [{'condition': 'minecraft:survives_explosion'}],
                'random_sequence': 'intoxicantes:blocks/' + p['id']}]})
    print('gen_mobilia_mercado: 4 modelos nativos, blockstates e texturas 128 sincronizados')


if __name__ == '__main__':
    main()
