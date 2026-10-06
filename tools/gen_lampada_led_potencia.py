"""FamíliaLED SNC:10potências, três silhuetas e materiais próprios128.
Apenas arte: estados wattage/lit, receitas e IDs existentes são preservados.
preview_entries() é puro e não grava recursos; executar main() exige aprovação.
"""
import copy
import os
from PIL import ImageDraw
try:
    from tools.gen_lampada_led import ASSETS, DATA, T, FACES, caixa as _caixa, data_url, display, material, png, texto, wjson
except ModuleNotFoundError:
    from gen_lampada_led import ASSETS, DATA, T, FACES, caixa as _caixa, data_url, display, material, png, texto, wjson

LAMPADAS = [
    (5, 7, 'bulbo', (233, 235, 238)), (9, 8, 'bulbo', (235, 237, 240)),
    (12, 8, 'bulbo', (238, 240, 242)), (15, 9, 'bulbo', (240, 242, 244)),
    (20, 10, 'bulbo', (242, 244, 246)), (30, 11, 'refletor', (196, 200, 206)),
    (50, 13, 'refletor', (206, 210, 216)), (100, 14, 'high_bay', (216, 220, 226)),
    (150, 15, 'high_bay', (226, 230, 234)), (200, 15, 'high_bay', (234, 236, 240)),
]
DEGRAU_INGREDIENTE = {5: 'torch', 9: 'glowstone_dust', 12: 'glowstone_dust',
    15: 'glowstone_dust', 20: 'glowstone_dust', 30: 'iron_ingot',
    50: 'iron_ingot', 100: 'diamond', 150: 'diamond', 200: 'diamond'}


def caixa(f, t, tex, uv=None, faces=FACES):
    # Carcaça usa só o material0..8. Rosca, borracha e selo têm UV próprios,
    # para não espalhar as quatro regiões do atlas por todas as caixas.
    if uv is None:
        uv = (0, 0, 16, 8) if tex == '#metal' else (0, 0, 16, 12)
    return _caixa(f, t, tex, uv, faces)


def tex_cupula(rgb, familia='bulbo', watts=5):
    img = material((244, 244, 229) if familia == 'bulbo' else (237, 243, 237),
                   'diffuser', 500 + watts)
    if familia != 'bulbo':
        g = ImageDraw.Draw(img)
        # MatrizLED dentro do difusor: células recortadas com rebordo metálico.
        for y in range(9, 90, 18):
            for x in range(9, 124, 18):
                g.rectangle((x, y, x + 10, y + 10), fill='#c1c7aa', outline='#a5b099')
                g.rectangle((x + 2, y + 2, x + 8, y + 8), fill='#fff7c6')
                g.line((x + 2, y + 2, x + 8, y + 2), fill='#fffef0')
    return img


def tex_metal(watts=5, familia='bulbo'):
    rgb = (220, 223, 212) if familia == 'bulbo' else ((61, 73, 80) if familia == 'refletor' else (99, 112, 118))
    img = material(rgb, 'metal', 810 + watts, f'SNC {watts}W')
    g = ImageDraw.Draw(img)
    # Faixas separadas no atlas: UV0..8 é carcaça,8..10 é rosca/alumínio,
    #10..12 são borracha e rebaixo,12..16 é a placaSNC/potência.
    for y in range(64, 80):
        for x in range(128):
            val = 152 + (19 if y % 4 == 0 else 0) + ((x + y * 3) % 7)
            img.putpixel((x, y), (val + 8, val + 11, val + 10, 255))
    g.rectangle((0, 80, 127, 95), fill='#263238')
    g.line((0, 80, 127, 80), fill='#64767b')
    return img


def aro(elements, y0, y1, raio, tex, corte=.7, uv=(0, 0, 16, 8)):
    """Seção octogonal em três cuboides que só se tocam, sem faces sobrepostas."""
    elements.extend([
        caixa((8 - raio + corte, y0, 8 - raio), (8 + raio - corte, y1, 8 + raio), tex, uv),
        caixa((8 - raio, y0, 8 - raio + corte), (8 - raio + corte, y1, 8 + raio - corte), tex, uv),
        caixa((8 + raio - corte, y0, 8 - raio + corte), (8 + raio, y1, 8 + raio - corte), tex, uv),
    ])


def _base(cup, met, elements, family):
    return {'parent': 'minecraft:block/block', 'texture_size': [128, 128],
            'credit': 'SNC — ' + family, 'textures': {'cupula': cup, 'metal': met, 'particle': met},
            'elements': elements, 'display': display(.8 if family == 'bulbo' else .7)}


# v1.2.78 — ATENÇÃO: esta função já está corrigida, mas os assets do JOGO ainda
# são os VELHOS (o usuário pediu "deixar o bulbo como está por enquanto"). Rodar
# main() grava a peça nova de uma vez. Isso é de propósito, não é drift esquecido:
# o perfil antigo empilhava anéis que só cresciam e saía como pilha de pratos.
def modelo_bulbo(cup, met, watts=5):
    """Bulbo E27 pendurado: rosca fina no TOPO, vidro em baixo com ponta.

    Duas tentativas. A primeira empilhava onze anéis octogonais que só
    CRESCIAM (1.15 -> 2.2): bolo de casamento, com a base larga e reta no
    chão. A segunda acertou a ORDEM (ponta embaixo, rosca em cima) mas ficou
    com 8 degraus, que leem como escada. Aqui o vidro tem perfil de gota
    verdadeiramente continuo: uma curva de controle interpolada em FAZES
    finas, para a silhueta arredondar em vez de escalar.
    """
    index = [5, 9, 12, 15, 20].index(watts)
    bojo = 3.1 + index * .16             # raio máximo do vidro
    altura = 11.8 + index * .55          # altura total da peça
    ombro = altura - 3.1                 # altura onde o vidro vira metal
    colar = 1.85                         # raio do colar metálico
    # curva de controle do vidro: (fração da altura, raio / bojo)
    curva = ((0.00, .10), (0.08, .40), (0.18, .64), (0.30, .82), (0.42, .93),
             (0.54, 1.00), (0.66, .97), (0.76, .88), (0.85, .74), (0.92, .60),
             (0.97, .49), (1.00, colar / bojo))
    el = []
    # 18 fatias finas: a curva e reamostrada e o raio de cada uma e a media
    # das duas pontas, para nao ficar degrau grosso entre elas
    fases = 18
    for i in range(fases):
        t0, t1 = i / fases, (i + 1) / fases
        r = (_raio_na_curva(curva, t0, bojo) + _raio_na_curva(curva, t1, bojo)) / 2
        aro(el, t0 * ombro, t1 * ombro, r, '#cupula', .7, (0, 0, 16, 12))
    # rosca metálica fina no topo, com filetes
    aro(el, ombro, ombro + .7, colar, '#metal', .5)
    topo = ombro + .7
    passo = (altura - topo) / 4.4
    aro(el, topo, topo + passo * .8, 1.62, '#metal', .45)
    for i in range(3):
        aro(el, topo + passo * (.8 + i * .8), topo + passo * (1.6 + i * .8),
            1.48, '#metal', .35, (0, 8, 16, 10))
    aro(el, topo + passo * 3.2, altura, 1.36, '#metal', .3)
    zc = 8 - colar
    el.append(caixa((6.6, ombro + .1, zc - .06), (9.4, ombro + .56, zc), '#metal',
                    (0, 12, 16, 16), ('north',)))
    return _base(cup, met, el, 'bulbo')


def _raio_na_curva(curva, t, bojo):
    for (t0, r0), (t1, r1) in zip(curva, curva[1:]):
        if t0 <= t <= t1:
            f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
            return (r0 + (r1 - r0) * f) * bojo
    return curva[-1][1] * bojo


def modelo_refletor(cup, met, watts=30):
    width = 9 if watts == 30 else 13
    x0, x1 = 8 - width / 2, 8 + width / 2
    y0, y1 = 2.8, 8.5 if watts == 30 else 10.4
    el = [caixa((5.5, 0, 6.5), (10.5, .7, 9.5), '#metal', (0, 8, 16, 10)),
          caixa((6.4, .7, 7.2), (9.6, 1.6, 8.8), '#metal', (0, 8, 16, 10)),
          caixa((x0 + .6, y0 + .6, 7), (x1 - .6, y1 - .6, 9.3), '#metal')]
    for x in (x0 - .4, x1 - .2):
        el.append(caixa((x, .7, 7.4), (x + .6, 6.1, 8.4), '#metal'))
        el.append(caixa((x - .15, 5.1, 7.0), (x + .75, 5.9, 8.8), '#metal', (0, 8, 16, 10)))
    for x in (x0, x1 - .6):
        el.append(caixa((x, y0, 6.4), (x + .6, y1, 9.4), '#metal'))
    for y in (y0, y1 - .6):
        el.append(caixa((x0 + .6, y, 6.4), (x1 - .6, y + .6, 9.4), '#metal'))
    el.append(caixa((x0 + .65, y0 + .65, 6.55), (x1 - .65, y1 - .65, 6.9), '#cupula', (0, 0, 16, 12)))
    for x in range(int(x0 + 1), int(x1 - 1)):
        el.append(caixa((x, y0 + .7, 9.3), (x + .35, y1 - .7, 10.2), '#metal', (0, 8, 16, 10)))
    for x in (x0 + .2, x1 - .5):
        for y in (y0 + .2, y1 - .5):
            el.append(caixa((x, y, 6.32), (x + .3, y + .3, 6.4), '#metal', (0, 8, 16, 10)))
    el.append(caixa((x0 + .9, y0 - .8, 6.4), (x1 - .9, y0, 7.1), '#metal'))
    el.append(caixa((x0 + .9, y0 - .75, 6.36), (x1 - .9, y0 - .05, 6.4),
                    '#metal', (0, 12, 16, 16), ('north',)))
    return _base(cup, met, el, 'refletor')


def modelo_high_bay(cup, met, watts=100):
    index = [100, 150, 200].index(watts)
    radius = 5.0 + index * .7
    el = []
    # Fixação superior, driver, dissipador e campânula industrial voltada para baixo.
    aro(el, 11, 13, 1.35, '#metal', .3, (0, 8, 16, 10))
    el.extend([caixa((6, 13, 7.3), (6.7, 15.5, 8.7), '#metal'),
               caixa((9.3, 13, 7.3), (10, 15.5, 8.7), '#metal'),
               caixa((6.7, 14.8, 7.3), (9.3, 15.5, 8.7), '#metal')])
    aro(el, 8.5, 11, 2.9 + index * .2, '#metal', .6)
    for x in range(5, 12):
        el.append(caixa((x, 8.7, 4.5), (x + .35, 10.7, 11.5), '#metal', (0, 8, 16, 10)))
    for y0, y1, r in ((7.5, 8.5, radius - 2), (6.3, 7.5, radius - 1.2),
                       (5.2, 6.3, radius - .5), (4.6, 5.2, radius)):
        aro(el, y0, y1, r, '#metal', .8)
    aro(el, 4.2, 4.6, radius - .45, '#cupula', .8, (0, 0, 16, 12))
    # Aro com borda realmente vazada: quatro peças em torno do difusor.
    for x in (8 - radius, 8 + radius - .45):
        el.append(caixa((x, 4, 8 - radius + .8), (x + .45, 4.6, 8 + radius - .8), '#metal', (0, 8, 16, 10)))
    for z in (8 - radius, 8 + radius - .45):
        el.append(caixa((8 - radius + .8, 4, z), (8 + radius - .8, 4.6, z + .45), '#metal', (0, 8, 16, 10)))
    el.append(caixa((5, 7.55, 8 - radius + 1.16), (11, 8.35, 8 - radius + 1.2),
                    '#metal', (0, 12, 16, 16), ('north',)))
    return _base(cup, met, el, 'high_bay')


MODELOS = {'bulbo': modelo_bulbo, 'refletor': modelo_refletor, 'high_bay': modelo_high_bay}


def receita(watts, anterior):
    if watts == 5:
        return {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'group': 'intoxicantes',
                'pattern': ['G', 'T'], 'key': {'G': 'minecraft:glass', 'T': 'minecraft:torch'},
                'result': {'id': 'intoxicantes:lampada_led_5w', 'count': 1}}
    ing = DEGRAU_INGREDIENTE[watts]
    return {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'group': 'intoxicantes',
            'pattern': ['G', 'P'], 'key': {'G': 'minecraft:glass',
                'P': [f'intoxicantes:lampada_led_{anterior}w', f'minecraft:{ing}']},
            'result': {'id': f'intoxicantes:lampada_led_{watts}w', 'count': 1}}


def preview_entries():
    entries = []
    for watts, luz, family, rgb in LAMPADAS:
        name = f'lampada_led_{watts}w'
        cup, met = f'intoxicantes:block/{name}', f'intoxicantes:block/{name}_metal'
        entries.append({'id': name, 'name': f'LED {watts}W · ' + {'bulbo': 'bulbo', 'refletor': 'refletor', 'high_bay': 'industrial'}[family],
            'category': 'Iluminação', 'model': MODELOS[family](cup, met, watts),
            'textures': {cup: data_url(tex_cupula(rgb, family, watts)), met: data_url(tex_metal(watts, family))},
            'description': 'Rosca e difusor facetado.' if family == 'bulbo' else (
                'Refletor com matrizLED, moldura, suporte e dissipador.' if family == 'refletor' else
                'Campânula industrial, driver, aletas de alumínio e aro inferior.')})
    return entries


def main():
    for i, (watts, luz, family, rgb) in enumerate(LAMPADAS):
        name = f'lampada_led_{watts}w'
        cup, met = f'intoxicantes:block/{name}', f'intoxicantes:block/{name}_metal'
        png(name + '.png', tex_cupula(rgb, family, watts))
        png(name + '_metal.png', tex_metal(watts, family))
        wjson(os.path.join(ASSETS, 'models', 'block', name + '.json'), MODELOS[family](cup, met, watts))
        # Mesmos20pares de estado; nenhum efeito sobre luz, consumo ou rede.
        variants = {f'wattage={idx},lit={lit}': {'model': f'intoxicantes:block/{name}'}
                    for idx in range(10) for lit in ('true', 'false')}
        wjson(os.path.join(ASSETS, 'blockstates', name + '.json'), {'variants': variants})
        wjson(os.path.join(ASSETS, 'models', 'item', name + '.json'), {'parent': f'intoxicantes:block/{name}'})
        wjson(os.path.join(ASSETS, 'items', name + '.json'),
              {'model': {'type': 'minecraft:model', 'model': f'intoxicantes:block/{name}'}})
        wjson(os.path.join(DATA, 'loot_table', 'blocks', name + '.json'), {
            'type': 'minecraft:block', 'pools': [{'rolls': 1, 'bonus_rolls': 0,
            'entries': [{'type': 'minecraft:item', 'name': f'intoxicantes:{name}'}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}],
            'random_sequence': f'intoxicantes:blocks/{name}'}]})
        previous = LAMPADAS[i - 1][0] if i else None
        wjson(os.path.join(DATA, 'recipe', name + '.json'), receita(watts, previous))
    print('gen_lampada_led_potencia:10modelos detalhados +20pinturas128; estados/receitas preservados')


if __name__ == '__main__':
    main()
