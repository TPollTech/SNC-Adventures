"""Caixa do mercado: balcão de inox + registradora eletrônica detalhada.

Fonte canônica dos modelos/texturas, inclusive da prévia em memória.
16 unidades = 1 bloco. Cliente em -Z; operador/Gago em +Z.
O teclado e a gaveta são voltados ao OPERADOR; o visor elevado e a
maquininha são voltados ao CLIENTE. Metade superior apoiada em y=16.
UVs são escritos em unidades Minecraft (0..16), NÃO em pixels do atlas.

Executar main() somente depois da aprovação visual: grava recursos e pack.
"""
import base64
import copy
import json
import math
import os
import random
from io import BytesIO

from PIL import Image, ImageDraw
from gen_lampada_led import texto

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__))) + os.sep
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'intoxicantes')
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'intoxicantes')
MIRROR = os.path.normpath(os.path.join(ROOT, '..', 'resourcepacks', 'minhas-texturas',
                                     'assets', 'intoxicantes', 'textures', 'block'))
T = 128
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
INOX = (176, 183, 189)
PRETO = (35, 39, 44)
# Coordenadas locais do visor de cliente, compartilhadas conceitualmente
# com CaixaMercadoRenderer. A prévia usa um total EXEMPLO, não dados do jogo.
VISOR = {'centro': [10.5, 8.38, 4.245], 'largura': 5.9, 'altura': 1.10}


def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)


def png(nome, pixels):
    img = pixels if isinstance(pixels, Image.Image) else Image.new('RGBA', (T, T))
    if not isinstance(pixels, Image.Image):
        img.putdata(pixels)
    for pasta in (os.path.join(ASSETS, 'textures', 'block'), MIRROR):
        os.makedirs(pasta, exist_ok=True)
        img.save(os.path.join(pasta, nome))
    print('  ' + nome)


def inox_escovado(seed=7, vertical=False, base=INOX):
    """Escovação fina e reflexo suave: sem salpicos nem juntas pintadas."""
    rng = random.Random(seed)
    linhas = [rng.uniform(-4, 4) for _ in range(T)]
    img = Image.new('RGBA', (T, T))
    for y in range(T):
        for x in range(T):
            faixa = x if vertical else y
            reflexo = 11 * math.exp(-((x - 42) / 34) ** 2) - 4 * x / T
            k = linhas[faixa] + rng.uniform(-1.5, 1.5) + reflexo
            img.putpixel((x, y), tuple(max(0, min(255, round(v + k))) for v in base) + (255,))
    return img


def preto_fosco(seed=11):
    rng = random.Random(seed)
    img = Image.new('RGBA', (T, T))
    for y in range(T):
        for x in range(T):
            k = rng.uniform(-2, 2) + 4 * (1 - y / T)
            img.putpixel((x, y), tuple(round(v + k) for v in PRETO) + (255,))
    return img


def tex_laminado():
    # Nome conservado por compatibilidade de quem importa o gerador.
    return inox_escovado(7, vertical=True)


def tex_tampo():
    return inox_escovado(17, base=(157, 165, 173))


def tex_metal():
    return preto_fosco(23)


def tex_gaveta():
    return inox_escovado(31, base=(196, 203, 208))


# Atlas eletrônico: vidro, LCD, plaquetas e teclas; cada face tem sua ilha.
TECLAS = ('7', '8', '9', '4', '5', '6', '1', '2', '3', '0', '00',
          'C', 'F', 'OK', 'ENT', 'SNC')


def uv_tecla(label):
    i = TECLAS.index(label)
    x, y = (i % 4) * 24, 64 + (i // 4) * 16
    return (x, y, x + 24, y + 16)


def tex_visor():
    img = preto_fosco(29)
    g = ImageDraw.Draw(img)
    # Vidro verde de cliente: número ausente de propósito, vem do servidor.
    for y in range(24):
        for x in range(128):
            k = 5 * math.exp(-((x - 25) / 38) ** 2) + (2 if y % 3 == 0 else 0)
            img.putpixel((x, y), (10 + int(k), 32 + int(k), 26 + int(k), 255))
    g.line((4, 2, 38, 2), fill='#315448')
    # LCD do operador / terminal de cartão (não simula saldo real).
    g.rectangle((0, 32, 95, 55), fill='#b1c7bb')
    texto(img, 'SNC', 5, 36, '#30473d', 1)
    texto(img, 'TOTAL', 5, 46, '#4f6a5b', 1)
    for x in range(58, 89, 6):
        g.rectangle((x, 45, x + 3, 50), outline='#82998b')
    # Cupom: texto miúdo e código de barras ficam no papel modelado.
    g.rectangle((96, 32, 127, 63), fill='#edece3')
    texto(img, 'SNC', 102, 35, '#5d655f', 1)
    for y in (44, 47, 50):
        g.line((100, y, 122 - (y % 4), y), fill='#95998e')
    for x in range(100, 124, 2):
        g.line((x, 55, x, 60), fill='#535d56', width=1 + int(x % 3 == 0))
    for i, label in enumerate(TECLAS):
        x, y, x1, y1 = uv_tecla(label)
        cor = '#d0d5d5' if i < 11 else '#646f76'
        if label == 'OK':
            cor = '#437c64'
        if label == 'ENT':
            cor = '#587c70'
        g.rectangle((x, y, x1 - 1, y1 - 1), fill=cor)
        g.line((x + 1, y + 1, x1 - 2, y + 1), fill='#e1e5e2' if i < 11 else '#89958f')
        g.line((x + 1, y1 - 2, x1 - 2, y1 - 2), fill='#9ca5a4' if i < 11 else '#394641')
        texto(img, label, x + (24 - len(label) * 6 + 1) // 2, y + 4,
              '#343e40' if i < 11 else '#f0f1e9', 1)
    # Funções do terminal, NFC, fenda, borracha: ilhas independentes.
    for y, cor in ((64, '#a64e4a'), (80, '#b49a56'), (96, '#4b856a')):
        g.rectangle((100, y, 123, y + 15), fill=cor)
        g.line((104, y + 7, 119, y + 7), fill='#e5e8df', width=2)
    g.rectangle((96, 112, 127, 127), fill='#343d43')
    for r in (5, 9, 13):
        g.arc((104 - r, 119 - r, 104 + r, 119 + r), -55, 55, fill='#b9c7c5')
    return img


TEXTURAS = {
    'corpo': 'intoxicantes:block/caixa_mercado',
    'tampo': 'intoxicantes:block/caixa_mercado_tampo',
    'metal': 'intoxicantes:block/caixa_mercado_metal',
    'visor': 'intoxicantes:block/caixa_mercado_visor',
    'gaveta': 'intoxicantes:block/caixa_mercado_gaveta',
    'particle': 'intoxicantes:block/caixa_mercado',
}
DISPLAY = {
    'gui': {'rotation': [25, 135, 0], 'translation': [0, 0, 0], 'scale': [.64] * 3},
    'firstperson_righthand': {'rotation': [0, 165, -8], 'translation': [0, 2, -1], 'scale': [.5] * 3},
    'firstperson_lefthand': {'rotation': [0, 165, -8], 'translation': [0, 2, -1], 'scale': [.5] * 3},
    'thirdperson_righthand': {'rotation': [90, 0, 0], 'translation': [0, 2, 1], 'scale': [.45] * 3},
    'thirdperson_lefthand': {'rotation': [90, 0, 0], 'translation': [0, 2, 1], 'scale': [.45] * 3},
    'ground': {'translation': [0, 0, 0], 'scale': [.4] * 3},
    'fixed': {'rotation': [0, 180, 0], 'scale': [.7] * 3},
}


def elemento(from_, to, faces, nome=None, rotation=None):
    """Entrada UV em pixels; saída sempre NORMALIZADA para 0..16."""
    el = {'from': list(from_), 'to': list(to),
          'faces': {f: {'uv': [v * 16 / T for v in uv], 'texture': '#' + tex}
                    for f, (tex, uv) in faces.items()}}
    if nome:
        el['name'] = nome
    if rotation:
        el['rotation'] = copy.deepcopy(rotation)
    return el


def peca(elements, nome, f, t, tex='metal', overrides=None, rotation=None):
    # Densidade de escovação consistente em superfícies de tamanhos distintos.
    dx, dy, dz = (t[i] - f[i] for i in range(3))
    faces = {}
    for face in FACES:
        a, b = (dx, dz) if face in ('up', 'down') else ((dz, dy) if face in ('east', 'west') else (dx, dy))
        faces[face] = (tex, (8, 8, 8 + min(a * 7, 112), 8 + min(b * 7, 112)))
    faces.update(overrides or {})
    elements.append(elemento(f, t, faces, nome, rotation))


def modelo(elements, credit):
    return {'credit': credit, 'parent': 'minecraft:block/block', 'textures': dict(TEXTURAS),
            'texture_size': [T, T], 'elements': elements, 'display': copy.deepcopy(DISPLAY)}


def modelo_base():
    e = []
    add = lambda nome, f, t, tex='metal': peca(e, nome, f, t, tex)
    # Rodapé RECUADO e pés, não uma faixa pintada no bloco.
    add('rodape_recuado', (1.25, .35, 2.05), (14.75, 1.8, 14.8))
    for x in (1.5, 13.25):
        for z in (2.3, 13.2):
            add('sapata', (x, 0, z), (x + 1.25, .5, z + 1.25))
    add('chassi', (.7, 1.7, 1.7), (15.3, 14.95, 15.55))
    for x in (.45, 15.1):
        add('lateral_inox', (x, 1.9, 1.65), (x + .45, 14.85, 15.6), 'corpo')
    # Duas portas de chapa com junta REAL entre elas e puxadores salientes.
    for a, b in ((.92, 7.83), (8.17, 15.08)):
        add('porta_bisel', (a, 2.05, 1.31), (b, 14.75, 1.73), 'tampo')
        add('porta_painel', (a + .14, 2.22, 1.22), (b - .14, 14.57, 1.32), 'corpo')
    for x in (7.15, 8.62):
        for y in (10.05, 12.35):
            add('fixacao_puxador', (x, y, .82), (x + .25, y + .25, 1.25))
        add('puxador_porta', (x - .04, 10.05, .68), (x + .29, 12.6, .87))
    # Costas de serviço divididas, ferragens e ventilação modeladas.
    add('painel_traseiro', (.94, 2.05, 15.55), (15.06, 14.73, 15.78), 'corpo')
    for x in range(3, 13):
        add('respiro_traseiro', (x, 3.1, 15.79), (x + .32, 4.9, 15.85))
    add('junta_sob_tampo', (.35, 14.85, .92), (15.65, 15.28, 15.85))
    add('tampo_borda_inferior', (0, 15.25, .45), (16, 15.72, 16), 'tampo')
    add('tampo_chanfro', (.12, 15.72, .57), (15.88, 16, 15.88), 'gaveta')
    return modelo(e, 'SNC — balcão inox, portas e ferragens em volume')


def modelo_topo(depois=True):
    """Registradora, gaveta, impressora e terminal. Sem variante cubo antiga."""
    e = []
    def add(nome, f, t, tex='metal', overrides=None, rotation=None):
        if rotation and rotation['angle'] == 22.5:
            # Elevar o deck inteiro evita atravessar a tampa da gaveta.
            f = (f[0], f[1] + .55, f[2])
            t = (t[0], t[1] + .55, t[2])
        peca(e, nome, f, t, tex, overrides, rotation)
        if (nome.startswith('terminal_') or nome == 'NFC') and overrides and 'up' in overrides:
            # Terminal olha para -Z: o texto gira junto, não fica de ponta-cabeça.
            e[-1]['faces']['up']['rotation'] = 180

    # Gaveta de dinheiro: corpo baixo, tampa de chapa e frente ao operador.
    for x in (6.45, 13.7):
        for z in (5, 13.6):
            add('pe_gaveta', (x, .02, z), (x + .65, .38, z + .65))
    add('gaveta_chassi', (6.05, .34, 4.6), (14.85, 1.92, 14.75))
    add('gaveta_tampa', (6.18, 1.92, 4.72), (14.72, 2.08, 14.62), 'gaveta')
    add('gaveta_frente_bisel', (6.18, .5, 14.75), (14.72, 1.77, 14.93), 'tampo')
    add('gaveta_frente_inox', (6.34, .65, 14.94), (14.56, 1.64, 15.02), 'gaveta')
    # Boca para cheque/cupom e puxador vazado com dois suportes.
    add('fenda_documentos', (6.8, 1.36, 15.035), (9.1, 1.48, 15.075))
    for x in (10.3, 12.75):
        add('suporte_puxador', (x, .95, 15.03), (x + .22, 1.35, 15.39))
    add('puxador_gaveta', (10.28, 1.15, 15.35), (12.99, 1.39, 15.58))
    add('fechadura_aro', (13.7, .9, 15.025), (14.15, 1.35, 15.1), 'gaveta')
    add('fechadura_miolo', (13.88, 1.01, 15.105), (13.97, 1.25, 15.13))

    # Carcaça da registradora: traseira alta + cunha por baixo do deck.
    add('registradora_base', (6.42, 2.08, 5.05), (14.38, 2.47, 14.07))
    add('registradora_ombro', (6.52, 2.47, 5.18), (14.28, 4.84, 7.6))
    angulo = math.radians(22.5)
    pivot = [10.45, 3.84, 10.7]
    rot = {'origin': pivot, 'axis': 'x', 'angle': 22.5, 'rescale': False}
    # A cunha acompanha o plano inclinado, sem cubo grande sob as teclas.
    for i in range(14):
        z = 7.6 + i * .45
        altura = pivot[1] - (z + .225 - 10.7) * math.tan(angulo) - .18 / math.cos(angulo)
        add('cunha_teclado', (6.54, 2.30, z), (14.25, altura, z + .45))
    add('deck_borda_inox', (6.48, 3.10, 8.25), (14.42, 3.46, 14.03), 'tampo', rotation=rot)
    add('deck_rebaixo', (6.67, 3.46, 8.41), (14.23, 3.52, 13.87), rotation=rot)

    def tecla(nome, x, z, w=.68, d=.72, label='0', cor=None):
        # Base escura e keycap em relevo: letras só no topo, não nas laterais.
        add('base_' + nome, (x, 3.53, z), (x + w, 3.64, z + d), rotation=rot)
        add(nome, (x + .035, 3.64, z + .035), (x + w - .035, 3.84, z + d - .035),
            'gaveta' if label in TECLAS[:11] else 'metal',
            {'up': ('visor', cor or uv_tecla(label))}, rot)

    # Teclado numérico 3x4 e coluna de funções; tecla ENTER alongada.
    for r, labels in enumerate((('7', '8', '9'), ('4', '5', '6'), ('1', '2', '3'), ('0', '00', 'C'))):
        for c, label in enumerate(labels):
            tecla('tecla_' + label, 9.56 + c * .91, 9.12 + r * 1.00, label=label)
    tecla('funcao_C', 12.47, 9.12, w=1.25, label='C')
    tecla('funcao_F', 12.47, 10.12, w=1.25, label='F')
    tecla('enter', 12.47, 11.12, w=1.25, d=1.72, label='ENT')
    tecla('avanco_cupom', 7.3, 9.38, label='F')
    tecla('funcao_impressora', 8.15, 9.38, label='C')
    # Plaqueta inserida no deck, sem texto gigante esticado.
    add('plaqueta_SNC', (7.15, 3.56, 12.10), (8.84, 3.62, 12.74), 'metal',
        {'up': ('visor', uv_tecla('SNC'))}, rot)

    # Impressora térmica: tampa destacada, fenda e PAPEL saindo em volume.
    add('impressora_corpo', (6.74, 4.66, 5.47), (9.07, 5.12, 8.27))
    add('impressora_tampa', (6.85, 5.12, 5.6), (8.96, 5.28, 7.91), 'tampo')
    add('saida_cupom', (7.01, 5.08, 7.96), (8.82, 5.25, 8.32))
    add('lamina_cupom', (7.04, 5.26, 7.99), (8.79, 5.33, 8.17), 'gaveta')
    papelrot = {'origin': [7.92, 5.22, 8.19], 'axis': 'x', 'angle': -22.5, 'rescale': False}
    add('cupom_impresso', (7.18, 5.20, 8.18), (8.66, 6.51, 8.23), 'gaveta',
        {'south': ('visor', (96, 32, 128, 64))}, papelrot)

    # LCD do operador: painel separado, elevado, olhando para o Gago (+Z).
    add('visor_operador_carcase', (9.43, 4.67, 6.65), (14.06, 6.58, 7.38))
    add('visor_operador_bisel', (9.58, 4.87, 7.385), (13.91, 6.39, 7.45), 'tampo')
    add('visor_operador_LCD', (9.76, 5.05, 7.455), (13.73, 6.19, 7.47), 'visor',
        {'south': ('visor', (0, 32, 96, 56))})
    # Visor do cliente: coluna real, junta de giro e cabeça horizontal fina.
    add('base_coluna', (9.63, 4.75, 5.25), (11.37, 5.08, 6.3), 'tampo')
    add('coluna_visor', (10.17, 5.05, 5.50), (10.83, 7.92, 6.10), 'gaveta')
    add('articulacao_visor', (9.92, 7.55, 4.89), (11.08, 8.16, 6.22))
    add('visor_cliente_carcase', (7.12, 7.43, 4.33), (13.88, 9.31, 5.32))
    add('visor_cliente_chanfro', (7.25, 7.56, 4.27), (13.75, 9.18, 4.34), 'tampo')
    add('visor_cliente_vidro', (7.47, 7.78, 4.255), (13.53, 8.98, 4.27), 'visor',
        {'north': ('visor', (0, 0, 128, 24))})
    for x in (7.38, 13.42):
        add('parafuso_visor', (x, 9.03, 4.245), (x + .17, 9.20, 4.265), 'gaveta')
    # Respiros nas laterais e conexão traseira (fendas com espessura real).
    for z in (5.48, 5.92, 6.36, 6.8):
        add('respiro_lateral', (14.285, 2.92, z), (14.33, 4.04, z + .14), 'tampo')
    add('conector_energia', (8.4, 2.75, 4.94), (9.25, 3.29, 5.06))
    # Cabo em segmentos apoiados no tampo, entre caixa e terminal.
    for f, t in (((5.38, .08, 5.10), (6.06, .22, 5.28)),
                 ((5.22, .08, 5.10), (5.41, .22, 8.53)),
                 ((3.42, .08, 8.35), (5.4, .22, 8.53)),
                 ((3.26, .08, 6.76), (3.45, .22, 8.5))):
        add('cabo_terminal', f, t)

    # Terminal de cartão — forma fina chanfrada, inclinação para o cliente.
    add('terminal_sapata', (1.51, .03, 3.00), (4.61, .33, 6.28))
    add('terminal_suporte', (2.46, .31, 4.14), (3.68, 1.55, 5.65))
    trot = {'origin': [3.06, 1.91, 4.75], 'axis': 'x', 'angle': -22.5, 'rescale': False}
    add('terminal_borda', (1.35, 1.62, 2.00), (4.77, 2.15, 7.39), rotation=trot)
    add('terminal_casca', (1.49, 2.15, 2.13), (4.63, 2.29, 7.26), 'tampo', rotation=trot)
    add('terminal_face', (1.61, 2.29, 2.25), (4.51, 2.35, 7.14), rotation=trot)
    add('terminal_LCD_bisel', (1.82, 2.35, 4.91), (4.30, 2.45, 6.83), 'tampo', rotation=trot)
    add('terminal_LCD', (1.96, 2.455, 5.08), (4.16, 2.47, 6.66), 'visor',
        {'up': ('visor', (0, 32, 96, 56))}, trot)
    add('NFC', (2.61, 2.36, 6.84), (3.59, 2.38, 7.10), 'visor',
        {'up': ('visor', (96, 112, 128, 128))}, trot)
    for r, labels in enumerate((('1', '2', '3'), ('4', '5', '6'), ('7', '8', '9'), ('C', '0', 'OK'))):
        for c, label in enumerate(labels):
            x, z = 1.96 + c * .79, 4.21 - r * .53
            add('terminal_tecla_' + label, (x, 2.36, z), (x + .61, 2.52, z + .40), 'gaveta',
                {'up': ('visor', uv_tecla(label))}, trot)
    for c, y in enumerate((64, 80, 96)):
        add('terminal_funcao', (1.93 + c * .8, 2.36, 2.21), (2.54 + c * .8, 2.52, 2.49), 'metal',
            {'up': ('visor', (100, y, 124, y + 16))}, trot)
    add('terminal_fenda_cartao', (1.99, 1.79, 1.97), (4.15, 1.99, 2.015), 'metal', rotation=trot)
    add('terminal_labio_cartao', (1.88, 1.66, 1.94), (4.26, 1.77, 2.12), 'gaveta', rotation=trot)
    return modelo(e, 'SNC — registradora inox/preto; teclado, gaveta, impressora, visores e POS modelados')


MODELO_BASE = modelo_base()
MODELO_TOPO = modelo_topo()


def empilhada(topo):
    m = copy.deepcopy(MODELO_BASE)
    for el in topo['elements']:
        c = copy.deepcopy(el)
        for limite in ('from', 'to'):
            c[limite][1] += 16
        if 'rotation' in c:
            c['rotation']['origin'][1] += 16
        m['elements'].append(c)
    return m


def shapes_js():
    # Seleção física aproximada, sincronizada com CaixaMercadoBlock.
    return [[0, 0, .45, 16, 16, 16], [6.05, 16, 4.6, 14.85, 18.08, 15.58],
            [6.42, 18.08, 5.05, 14.42, 22.58, 14.07],
            [7.12, 23.43, 4.245, 13.88, 25.31, 6.22],
            [10.17, 21.05, 5.5, 10.83, 23.92, 6.1],
            [1.35, 16, 1.94, 4.77, 19.55, 7.39]]


def preview_entries():
    textures = {}
    for nome, img in (('caixa_mercado', tex_laminado()), ('caixa_mercado_tampo', tex_tampo()),
                      ('caixa_mercado_metal', tex_metal()), ('caixa_mercado_visor', tex_visor()),
                      ('caixa_mercado_gaveta', tex_gaveta())):
        stream = BytesIO()
        img.save(stream, format='PNG')
        textures['intoxicantes:block/' + nome] = 'data:image/png;base64,' + base64.b64encode(stream.getvalue()).decode('ascii')
    return [{'id': 'caixa_mercado', 'name': 'Registradora SNC · inox / preto', 'category': 'Ambiente',
             'model': empilhada(MODELO_TOPO), 'textures': textures, 'shapes': shapes_js(),
             'screen': VISOR,
             'variants': [{'name': 'Conjunto completo', 'model': empilhada(MODELO_TOPO)},
                          {'name': 'Equipamentos em detalhe', 'model': MODELO_TOPO}],
             'description': 'Teclado inclinado do operador, teclas individuais, impressora com cupom, gaveta com puxador e fechadura, visor do cliente em coluna e terminal de cartão. Inox escovado e polímero preto. O total da prévia é ilustrativo; no jogo vem do carrinho.'}]


BLOCKSTATE = {'variants': {
    f'half={half},facing={f}': {'model': 'intoxicantes:block/' + nome, **({'y': y} if y else {})}
    for half, nome in (('lower', 'caixa_mercado'), ('upper', 'caixa_mercado_topo'))
    for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))
}}


def main():
    print('gen_caixa_mercado: texturas')
    for nome, func in (('caixa_mercado', tex_laminado), ('caixa_mercado_tampo', tex_tampo),
                       ('caixa_mercado_metal', tex_metal), ('caixa_mercado_visor', tex_visor),
                       ('caixa_mercado_gaveta', tex_gaveta)):
        png(nome + '.png', func())
    wjson(os.path.join(ASSETS, 'blockstates', 'caixa_mercado.json'), BLOCKSTATE)
    wjson(os.path.join(ASSETS, 'models', 'block', 'caixa_mercado.json'), MODELO_BASE)
    wjson(os.path.join(ASSETS, 'models', 'block', 'caixa_mercado_topo.json'), MODELO_TOPO)
    wjson(os.path.join(ASSETS, 'models', 'item', 'caixa_mercado.json'), {'parent': 'intoxicantes:block/caixa_mercado_topo'})
    wjson(os.path.join(ASSETS, 'items', 'caixa_mercado.json'), {
        'model': {'type': 'minecraft:model', 'model': 'intoxicantes:block/caixa_mercado_topo'}})
    wjson(os.path.join(DATA, 'loot_table', 'blocks', 'caixa_mercado.json'), {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'bonus_rolls': 0,
                   'entries': [{'type': 'minecraft:item', 'name': 'intoxicantes:caixa_mercado'}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'},
                                  {'condition': 'minecraft:block_state_property',
                                   'block': 'intoxicantes:caixa_mercado', 'properties': {'half': 'lower'}}],
                   'random_sequence': 'intoxicantes:blocks/caixa_mercado'}]})
    print('gen_caixa_mercado: OK — 5 texturas espelhadas, modelos e definições')


if __name__ == '__main__':
    main()
