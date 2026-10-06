# -*- coding: utf-8 -*-
"""Gera os assets da DESTILARIA (v1.2.50) — modelos 3D, texturas, blockstates,
loot tables e item definitions.

Filosofia dos modelos (mesma das armas 3D do projeto):
- JSON elements nativos do Minecraft (sem OBJ/Blender);
- curvas simuladas com caixas sobrepostas;
- cada barril tem IDENTIDADE VISUAL própria (spec 8) — nada de recolorir o
  barril vanilla;
- barris: texturas 64x64 com atlas de materiais;
- máquinas de matéria-prima: atlas 128x128 (células de material de 64px) — a
  densidade de pixels acompanha o nível de detalhe do modelo 3D.

Uso: python tools/gen_bebidas.py  (a partir da raiz do projeto do mod)
"""
import json
import os
import struct
import zlib

from random import Random

ASSETS = os.path.join("src", "main", "resources", "assets", "intoxicantes")
MODELS = os.path.join(ASSETS, "models", "block")
TEX = os.path.join(ASSETS, "textures", "block")
TEX_ITEM = os.path.join(ASSETS, "textures", "item")
DATA = os.path.join("src", "main", "resources", "data", "intoxicantes")


def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=1, ensure_ascii=False)
        f.write("\n")


# ============================================================ TEXTURAS (64x64)

def _png(path, pixels):
    """Grava um PNG RGBA de width x height a partir de uma lista de linhas de
    tuplas (r,g,b,a). Sem dependência do Pillow (chunk IHDR/IDAT/IEND à mão)."""
    h = len(pixels)
    w = len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(struct.pack("4B", *px) for px in row)
                   for row in pixels)

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    body = chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + body)


def _mistura(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(len(c1)))


def _hex(s, a=255):
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def textura_barril(nome, madeira_clara, madeira_media, madeira_escura,
                   aro, aro_escuro, detalhe, carbonizada=False):
    """Atlas 64x64 v2 do barril:
    [0..15]x[0..15]  LADO — tábuas verticais (4 tábuas de 4px com frestas),
                     grão por pixel e sombra de barriga (linhas 0-3 e 12-15
                     mais escuras: a curva lê na textura);
    [16..31]x[0..15] TOPO/CABEÇA — anéis concêntricos + frestas radiais;
    [32..47]x[0..15] FUNDO — versão escura da cabeça;
    [48..63]x[0..15] AROS/ROLHA — aro (2 tons) e rolha/cano.
    """
    from random import Random
    rng = Random(hash(nome) & 0xFFFF)
    claro, medio, escuro = _hex(madeira_clara), _hex(madeira_media), _hex(madeira_escura)
    met, met_esc = _hex(aro), _hex(aro_escuro)

    def grao(cor, forca=10):
        d = rng.randint(-forca, forca // 2)
        return (max(0, min(255, cor[0] + d)), max(0, min(255, cor[1] + d)),
                max(0, min(255, cor[2] + d)), 255)

    px = [[(0, 0, 0, 0)] * 64 for _ in range(64)]

    # ---------- célula 0: LADO (tábuas verticais + sombra de barriga)
    for y in range(16):
        # sombreamento vertical: a barriga (meio) reflete mais luz
        if y < 3:
            luz = 0.78 + y * 0.06
        elif y > 12:
            luz = 0.72 + (15 - y) * 0.05
        else:
            luz = 1.0 - abs(y - 8) * 0.012
        for x in range(16):
            fresta = x % 4 == 3          # junta entre tábuas
            borda = x % 4 == 0           # lado iluminado da tábua
            base = claro if x % 8 < 4 else medio  # alterna tom por tábua
            c = grao(base, 12)
            if fresta:
                c = grao(escuro, 6)
            elif borda:
                c = tuple(min(255, int(v * 1.10)) for v in c[:3]) + (255,)
            c = tuple(max(0, min(255, int(v * luz))) for v in c[:3]) + (255,)
            px[y][x] = c
    if carbonizada:
        # rum: carbonização — manchas escuras irregulares sobre a madeira
        for _ in range(90):
            x, y = rng.randint(0, 15), rng.randint(0, 15)
            prof = rng.choice((0.45, 0.6, 0.75))
            px[y][x] = tuple(int(v * prof) for v in px[y][x][:3]) + (255,)
        for _ in range(26):  # brasas vivas discretas no grão queimado
            x, y = rng.randint(0, 15), rng.randint(0, 15)
            px[y][x] = grao((92, 52, 28), 8)

    # ---------- células 1/2: CABEÇA (topo) e FUNDO (anéis concêntricos)
    centro = 7.5
    for cx, fator in ((16, 1.0), (32, 0.55)):
        for y in range(16):
            for x in range(16):
                dx, dy = x - centro, y - centro
                r = (dx * dx + dy * dy) ** 0.5
                if r > 7.6:
                    px[y][cx + x] = (0, 0, 0, 0)          # canto transparente
                    continue
                anel = int(r)
                base = claro if anel % 2 == 0 else medio   # anéis alternados
                c = grao(base, 9)
                if abs(dx) > 6.2 or abs(dy) > 6.2:          # aro externo da cabeça
                    c = grao(escuro, 6)
                if int(dx) == int(dy) and abs(dx) > 3:      # fresta radial (tábua diagonal)
                    c = grao(escuro, 4)
                if fator < 1.0:
                    c = tuple(int(v * fator) for v in c[:3]) + (255,)
                px[y][cx + x] = c

    # ---------- célula 3: AROS + ROLHA (colunas 48..63)
    for y in range(16):
        for x in range(16):
            if x < 8:
                base = met if x < 4 else tuple(int(v * 0.78) for v in met)
                c = grao(base, 7)
                if y in (0, 15):
                    c = grao(met_esc, 5)
            else:
                # rolha = BUJÃO DE MADEIRA (cortiça), não a cor de identidade
                c = grao(_escurecer(medio, 0.12) if y % 5 else _escurecer(medio, 0.38), 8)
            px[y][48 + x] = c

    _png(os.path.join(TEX, "barril_%s.png" % nome), px)
    return "barril_%s" % nome


def _escurecer(cor, fator):
    return (int(cor[0] * (1 - fator)), int(cor[1] * (1 - fator)),
            int(cor[2] * (1 - fator)), cor[3])


# ============================================================ MODELOS 3D

def _faces(uv, tex):
    return {
        "north": {"uv": uv, "texture": "#%s" % tex},
        "south": {"uv": uv, "texture": "#%s" % tex},
        "east": {"uv": uv, "texture": "#%s" % tex},
        "west": {"uv": uv, "texture": "#%s" % tex},
        "up": {"uv": uv, "texture": "#%s" % tex},
        "down": {"uv": uv, "texture": "#%s" % tex},
    }


def barril_modelo(nome, tex, detalhe_altura=1.0, torneira=False):
    """Barril v2 — barriga REAL (spec 7): 7 bandas de raio graduado formando
    o bojudo (chime → barriga → chime), 5 aros GEOMÉTRICOS que acompanham a
    curva (o central mais grosso, como nos barris de carvalho reais), cabeças
    finas com anéis concêntricos, rolha em degraus e torneira opcional.
    UVs mapeiam a ALTURA REAL de cada banda: as tábuas correm contínuas.
    ~50 elements, todos legíveis no preview.
    """
    els = []
    # -------- perfil do corpo: (y0, y1, inset) — inset a partir de 0.9px
    # chimes (bordas) estreitos, barriga larga no meio (y 6..10)
    bandas = [
        (1.2, 3.0, 2.9),   # chime inferior
        (3.0, 4.6, 2.15),  # barriga baixa
        (4.6, 6.0, 1.5),
        (6.0, 10.0, 1.05), # BARRIGA (quatro px de ventre pleno)
        (10.0, 11.4, 1.5),
        (11.4, 13.0, 2.15),# barriga alta
        (13.0, 14.8, 2.9), # chime superior
    ]
    total_y = 14.8 - 1.2   # altura pintada do corpo (13.6)
    for i, (y0, y1, inset) in enumerate(bandas):
        x0, x1 = 0.9 + inset, 15.1 - inset
        # texture_size 64: 1 unidade de UV = 4 px do atlas. A célula LADO é
        # 16x16 px => 4x4 unidades. UV x 0..1 = a linha de tábuas inteira;
        # v0..v1 = a fração da altura da banda (tábua contínua, sem dobra).
        v0 = (y0 - 1.2) / total_y * 4
        v1 = (y1 - 1.2) / total_y * 4
        uv_lado = (0.0, v0, 4.0, v1)
        els.append({
            "from": [x0, y0, x0], "to": [x1, y1, x1],
            "faces": {
                "north": {"uv": uv_lado, "texture": "#lado"},
                "south": {"uv": uv_lado, "texture": "#lado"},
                "east": {"uv": uv_lado, "texture": "#lado"},
                "west": {"uv": uv_lado, "texture": "#lado"},
                # o degrau entre bandas é VINCO (linha escura da fresta),
                # não prateleira: faixa fina da célula LADO (fundo escuro)
                "up": {"uv": (0.0, 3.7, 4.0, 3.85), "texture": "#lado"},
                "down": {"uv": (0.0, 3.85, 4.0, 4.0), "texture": "#lado"},
            },
        })
    # -------- cabeças: tampa e fundo (célula topo/fundo com anéis)
    # cabeça RECESSADA (afundada dentro do chime) — anéis concêntricos de cima
    els.append({"from": [3.9, 14.8, 3.9], "to": [12.1, 15.6, 12.1],
                "faces": {"up": {"uv": (4.0, 0.0, 8.0, 4.0), "texture": "#topo"},
                          "down": {"uv": (4.0, 0.0, 8.0, 4.0), "texture": "#lado"},
                          "north": {"uv": (12.0, 0.0, 13.0, 2.0), "texture": "#aro"},
                          "south": {"uv": (12.0, 0.0, 13.0, 2.0), "texture": "#aro"},
                          "east": {"uv": (12.0, 0.0, 13.0, 2.0), "texture": "#aro"},
                          "west": {"uv": (12.0, 0.0, 13.0, 2.0), "texture": "#aro"}}})
    els.append({"from": [3.9, 0.4, 3.9], "to": [12.1, 1.2, 12.1],
                "faces": {"up": {"uv": (0.0, 0.0, 4.0, 4.0), "texture": "#lado"},
                          "down": {"uv": (8.0, 0.0, 12.0, 4.0), "texture": "#fundo"},
                          "north": {"uv": (12.0, 0.0, 13.0, 2.0), "texture": "#aro"},
                          "south": {"uv": (12.0, 0.0, 13.0, 2.0), "texture": "#aro"},
                          "east": {"uv": (12.0, 0.0, 13.0, 2.0), "texture": "#aro"},
                          "west": {"uv": (12.0, 0.0, 13.0, 2.0), "texture": "#aro"}}})
    # -------- 5 AROS GEOMÉTRICOS que seguem a curva (o central mais grosso)
    # (y, espessura) posicionados nas juntas das bandas; raio = banda + 0.35
    aros = [
        (2.0, 0.9),    # chime inferior
        (4.2, 0.8),    # quartos
        (7.1, 1.4),    # CENTRAL (bilge hoop, grosso como no barril real)
        (10.0, 0.8),   # quartos
        (13.6, 0.9),   # chime superior
    ]
    for y, esp in aros:
        # raio do aro: acompanha o corpo (interpolado do perfil)
        if y < 3.0:
            inset = 2.9
        elif y < 4.6:
            inset = 2.15
        elif y < 6.0:
            inset = 1.5
        elif y < 10.0:
            inset = 1.05
        elif y < 11.4:
            inset = 1.5
        elif y < 13.0:
            inset = 2.15
        else:
            inset = 2.9
        x0, x1 = 0.9 + inset - 0.35, 15.1 - inset + 0.35
        # célula ARO = colunas 48..55 px => unidades 12..14; altura do aro
        # na textura proporcional à espessura (célula tem 4 unidades)
        uv_aro = (12.0, 0.0, 14.0, esp / 1.4 * 4.0)
        els.append({
            "from": [x0, y - esp / 2, x0], "to": [x1, y + esp / 2, x1],
            "faces": {
                "north": {"uv": uv_aro, "texture": "#aro"},
                "south": {"uv": uv_aro, "texture": "#aro"},
                "east": {"uv": uv_aro, "texture": "#aro"},
                "west": {"uv": uv_aro, "texture": "#aro"},
            },
        })
    # -------- rolha (bung) em degraus no topo — cresce com a fase
    h = max(0.6, detalhe_altura)
    uv_rolha = (14.0, 0.0, 16.0, 4.0)  # célula ROLHA (cortiça)
    els.append({"from": [6.9, 15.6, 6.9], "to": [9.1, 15.6 + h * 0.55, 9.1],
                "faces": {"up": {"uv": uv_rolha, "texture": "#aro"},
                          "down": {"uv": uv_rolha, "texture": "#aro"},
                          "north": {"uv": uv_rolha, "texture": "#aro"},
                          "south": {"uv": uv_rolha, "texture": "#aro"},
                          "east": {"uv": uv_rolha, "texture": "#aro"},
                          "west": {"uv": uv_rolha, "texture": "#aro"}}})
    els.append({"from": [7.4, 15.6 + h * 0.55, 7.4], "to": [8.6, 15.6 + h, 8.6],
                "faces": {"up": {"uv": uv_rolha, "texture": "#aro"},
                          "north": {"uv": uv_rolha, "texture": "#aro"},
                          "south": {"uv": uv_rolha, "texture": "#aro"},
                          "east": {"uv": uv_rolha, "texture": "#aro"},
                          "west": {"uv": uv_rolha, "texture": "#aro"}}})
    # -------- TORNEIRA (cerveja): bico de metal na barriga frontal + almofada
    if torneira:
        uv_t = (12.0, 0.0, 14.0, 2.0)   # metal do aro
        els.append({"from": [10.9, 5.4, -1.2], "to": [13.1, 7.6, 0.95],
                    "faces": {"north": {"uv": uv_t, "texture": "#aro"},
                              "south": {"uv": uv_t, "texture": "#aro"},
                              "east": {"uv": uv_t, "texture": "#aro"},
                              "west": {"uv": uv_t, "texture": "#aro"},
                              "up": {"uv": uv_t, "texture": "#aro"},
                              "down": {"uv": uv_t, "texture": "#aro"}}})
        els.append({"from": [11.5, 5.0, -1.6], "to": [12.5, 5.4, 0.2],
                    "faces": {"north": {"uv": uv_t, "texture": "#aro"},
                              "south": {"uv": uv_t, "texture": "#aro"},
                              "east": {"uv": uv_t, "texture": "#aro"},
                              "west": {"uv": uv_t, "texture": "#aro"},
                              "down": {"uv": uv_t, "texture": "#aro"}}})
    modelo = {
        "texture_size": [64, 64],
        "textures": {
            "lado": "intoxicantes:block/" + tex,
            "topo": "intoxicantes:block/" + tex,
            "fundo": "intoxicantes:block/" + tex,
            "aro": "intoxicantes:block/" + tex,
            "particle": "intoxicantes:block/" + tex,
        },
        "elements": els,
    }
    # display do item (barril no inventário: iso padrão de bloco, mantém tamanho)
    modelo["display"] = {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0],
                 "scale": [0.85, 0.85, 0.85]},
        "ground": {"translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 0, 0], "scale": [1, 1, 1]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                   "scale": [0.5, 0.5, 0.5]},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                   "scale": [0.5, 0.5, 0.5]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 1, 0],
                                   "scale": [0.6, 0.6, 0.6]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 1, 0],
                                   "scale": [0.6, 0.6, 0.6]},
    }
    wjson(os.path.join(MODELS, "barril_%s.json" % nome), modelo)
    return modelo


def _fatiar_em_2(nome, els, texturas, texture_size, display_escala=0.75):
    """v1.2.53 — TAMANHO REAL: recebe os elements desenhados no espaço COMPLETO
    da máquina (2 blocos de altura: y 0..32) e fatia em DOIS modelos:
    <nome>_baixo (y 0..16) e <nome>_alto (y 16..32 deslocado -16).
    Elementos que CRUZAM a fronteira (ex.: parede da tina y 6.6..23) são
    CORTADOS em dois na altura 16 (a textura é célula de material, então a
    emenda é invisível). O item usa `<nome>_item` com os elements completos
    (0..32 — modelo de item aceita) pra mostrar a máquina inteira."""
    def corta(e, y_split):
        """Divide o elemento na altura y_split → (parte_baixa, parte_alta)."""
        eb = dict(e); eb["to"] = [e["to"][0], float(y_split), e["to"][2]]
        ea = dict(e); ea["from"] = [e["from"][0], float(y_split), e["from"][2]]
        return eb, ea
    baixo, alto = [], []
    for e in els:
        y0, y1 = e["from"][1], e["to"][1]
        if y1 <= 16.0:
            baixo.append(e)
        elif y0 >= 16.0:
            alto.append(e)
        else:
            eb, ea = corta(e, 16.0)
            baixo.append(eb); alto.append(ea)
    def desloca(lista, dy):
        out = []
        for e in lista:
            e2 = dict(e)
            e2["from"] = [e["from"][0], e["from"][1] - dy, e["from"][2]]
            e2["to"] = [e["to"][0], e["to"][1] - dy, e["to"][2]]
            out.append(e2)
        return out
    base = {"texture_size": texture_size, "textures": texturas}
    m_baixo = dict(base); m_baixo["elements"] = desloca(baixo, 0)
    m_baixo["display"] = _display_padrao(display_escala)
    m_alto = dict(base); m_alto["elements"] = desloca(alto, 16)
    m_item = dict(base); m_item["elements"] = els
    m_item["display"] = _display_padrao(display_escala)
    wjson(os.path.join(MODELS, "%s_baixo.json" % nome), m_baixo)
    wjson(os.path.join(MODELS, "%s_alto.json" % nome), m_alto)
    wjson(os.path.join(MODELS, "%s_item.json" % nome), m_item)


def dorna_modelo():
    """Tina aberta de fermentação — v1.2.53 TAMANHO REAL: 2 blocos de altura.
    Espaço completo y 0..32: pernas+corpo com líquido embaixo, boca+tampa
    entreaberta em cima (o vapor escapa entre a tampa e o aro).
    Atlas 128x128: C0 tábuas, C1 mosto, C2 aro/pernas, C3 base."""
    C0, C1, C2 = (0.0, 0.0, 8.0, 8.0), (8.0, 0.0, 16.0, 8.0), (0.0, 8.0, 8.0, 16.0)
    els = []
    # pernas (4 cantos) — mais longas, tina de verdade
    for (x, z) in ((1.6, 1.6), (12.8, 1.6), (1.6, 12.8), (12.8, 12.8)):
        els.append({"from": [x, 0.0, z], "to": [x + 1.6, 5.0, z + 1.6],
                    "faces": _faces(C2, "lado")})
    # base de sustentação
    els.append({"from": [1.0, 5.0, 1.0], "to": [15.0, 6.6, 15.0], "faces": _faces(C2, "lado")})
    # corpo da tina (4 paredes) — dobra de altura: y 6.6..24
    els.append({"from": [0.6, 6.6, 0.6], "to": [15.4, 23.0, 2.0], "faces": _faces(C0, "lado")})
    els.append({"from": [0.6, 6.6, 14.0], "to": [15.4, 23.0, 15.4], "faces": _faces(C0, "lado")})
    els.append({"from": [0.6, 6.6, 2.0], "to": [2.0, 23.0, 14.0], "faces": _faces(C0, "lado")})
    els.append({"from": [14.0, 6.6, 2.0], "to": [15.4, 23.0, 14.0], "faces": _faces(C0, "lado")})
    # fundo interno (boca aberta não pode ser buraco)
    els.append({"from": [2.0, 7.0, 2.0], "to": [14.0, 8.2, 14.0], "faces": _faces(C2, "lado")})
    # líquido (mosto vivo, visível pela boca lá em cima)
    els.append({"from": [2.0, 20.6, 2.0], "to": [14.0, 21.6, 14.0],
                "faces": _faces(C1, "liquido")})
    # aro de reforço no meio do corpo (tina real tem 2)
    els.append({"from": [0.3, 13.4, 0.3], "to": [15.7, 14.6, 15.7],
                "faces": _faces(C2, "aro")})
    # boca (aro superior) no topo do corpo
    els.append({"from": [0.3, 22.9, 0.3], "to": [15.7, 24.1, 15.7],
                "faces": _faces(C2, "aro")})
    # tampa entreaberta (meia tampa APOIADA no aro da boca — o vapor escapa
    # pela metade aberta; a tampa cobre z 1.2..8.2 e descansa no aro 22.9..24.1)
    els.append({"from": [1.2, 24.1, 1.2], "to": [15.2, 25.5, 8.2],
                "faces": _faces(C0, "lado")})
    els.append({"from": [1.2, 25.5, 1.2], "to": [3.2, 26.3, 8.2],
                "faces": _faces(C2, "aro")})
    modelo_tex = {
        "texture_size": [128, 128],
        "textures": {
            "lado": "intoxicantes:block/dorna",
            "liquido": "intoxicantes:block/dorna",
            "aro": "intoxicantes:block/dorna",
            "particle": "intoxicantes:block/dorna",
        },
    }
    _fatiar_em_2("dorna_bebida", els, modelo_tex["textures"], [128, 128], 0.62)


def alambique_modelo():
    """Alambique de cobre — v1.2.53 TAMANHO REAL: 2 blocos de altura.
    Espaço completo y 0..32: caldeira gorda embaixo (com o balde condensador
    na lateral), domo + pescoço + serpentina em cima; o braço leva o vapor
    pra fora e o tubo desce MERGULHANDO no balde. ~26 elements.
    Atlas 128x128: C0 cobre, C1 tampa/pescoço, C2 serpentina com pátina,
    C3 madeira do balde."""
    C0, C1, C2, C3 = ((0.0, 0.0, 8.0, 8.0), (8.0, 0.0, 16.0, 8.0),
                      (0.0, 8.0, 8.0, 16.0), (8.0, 8.0, 16.0, 16.0))
    PESCOCO = (10.0, 1.0, 14.0, 7.0)   # faixa vertical da célula C1
    SERP = (1.0, 9.0, 7.0, 15.0)       # faixa da célula C2 (tubo fino)
    BICA = (1.0, 1.0, 5.0, 5.0)        # quadrante da célula C0 (cobre polido)
    els = []
    # ---- PARTE BAIXA: caldeira (4 fatias de curva do pote de cobre, y 0..13)
    els.append({"from": [2.4, 0.0, 2.4], "to": [13.6, 2.5, 13.6], "faces": _faces(C0, "cobre")})
    els.append({"from": [1.6, 2.5, 1.6], "to": [14.4, 9.5, 14.4], "faces": _faces(C0, "cobre")})
    els.append({"from": [1.1, 9.5, 1.1], "to": [14.9, 12.5, 14.9], "faces": _faces(C0, "cobre")})
    els.append({"from": [2.4, 12.5, 2.4], "to": [13.6, 13.5, 13.6], "faces": _faces(C1, "cobre")})
    # balde condensador (o tubo mergulha nele pela boca)
    els.append({"from": [9.6, 0.0, 8.0], "to": [15.6, 8.0, 13.4],
                "faces": _faces(C3, "madeira")})
    # bica de saída no balde
    els.append({"from": [13.6, 3.4, 10.2], "to": [15.8, 4.6, 11.4],
                "faces": _faces(BICA, "cobre")})
    # fogo abre o caminho? não — o queimador é o bloco de baixo (campfire)
    # ---- PARTE ALTA: domo abaulado + pescoço + serpentina (y 13.5..30)
    # tampa abaulada (degraus com recuo crescente = leitura de domo)
    els.append({"from": [3.4, 13.5, 3.4], "to": [12.6, 17.5, 12.6], "faces": _faces(C1, "cobre")})
    els.append({"from": [5.4, 17.5, 5.4], "to": [10.6, 20.5, 10.6], "faces": _faces(C1, "cobre")})
    els.append({"from": [7.0, 20.5, 7.0], "to": [9.0, 22.0, 9.0], "faces": _faces(C1, "cobre")})
    # pescoço/vapor sobe do domo
    els.append({"from": [7.0, 22.0, 7.0], "to": [9.0, 26.5, 9.0], "faces": _faces(PESCOCO, "cobre")})
    # circuito: braço dobrado do pescoço leva o vapor pra FORA da caldeira;
    # o tubo desce na lateral leste e MERGULHA no balde; anéis marcam o enrolamento
    els.append({"from": [8.0, 25.7, 7.4], "to": [14.3, 26.5, 8.6], "faces": _faces(SERP, "cobre")})
    els.append({"from": [13.5, 6.5, 10.8], "to": [14.3, 25.7, 11.6], "faces": _faces(SERP, "cobre")})
    for y in (23.5, 20.0, 17.5, 14.5):   # anéis do enrolamento descendo pro balde
        els.append({"from": [13.3, y, 10.6], "to": [14.5, y + 0.6, 12.0],
                    "faces": _faces(PESCOCO, "cobre")})
    modelo_tex = {
        "texture_size": [128, 128],
        "textures": {
            "cobre": "intoxicantes:block/alambique",
            "madeira": "intoxicantes:block/alambique",
            "particle": "intoxicantes:block/alambique",
        },
    }
    _fatiar_em_2("alambique", els, modelo_tex["textures"], [128, 128], 0.72)


def maquina_modelo(nome, tex, tipo):
    """Moenda (rolos + manivela), prensa (fuso + cesto) e caldeirão (pote de
    ferro com alça). ~14-18 elements cada.
    Atlas 128x128: C0 material principal, C1 metal claro, C2 escuro/base,
    C3 detalhe (metal escuro ou líquido). Tubos finos usam faixas da célula."""
    C0, C1, C2, C3 = ((0.0, 0.0, 8.0, 8.0), (8.0, 0.0, 16.0, 8.0),
                      (0.0, 8.0, 8.0, 16.0), (8.0, 8.0, 16.0, 16.0))
    EIXO = (8.0, 2.0, 16.0, 6.0)       # faixa horizontal do metal C1
    MANIVELA = (8.0, 9.0, 16.0, 15.0)  # faixa do metal escuro C3
    FUSO = (10.0, 0.0, 14.0, 8.0)      # faixa vertical do metal C1
    BORDA = (8.0, 1.0, 16.0, 5.0)      # faixa do metal claro C1
    ALCAS = (0.0, 10.0, 8.0, 14.0)     # faixa do escuro C2
    els = []
    if tipo == "moenda":
        # v1.2.53 TAMANHO REAL (y 0..30): base pesada + engrenagem de tração +
        # rolos altos + cabeçote + manivela longa (esmagadora de cana de rua)
        els.append({"from": [1.0, 0.0, 1.0], "to": [15.0, 4.0, 15.0], "faces": _faces(C2, "madeira")})
        # colunas-guia (o rolo desce preso nelas)
        els.append({"from": [2.6, 4.0, 4.4], "to": [4.2, 22.0, 11.6], "faces": _faces(C2, "madeira")})
        els.append({"from": [11.8, 4.0, 4.4], "to": [13.4, 22.0, 11.6], "faces": _faces(C2, "madeira")})
        # dois rolos verticais COM VÃO entre eles (o coração da esmagadora:
        # a cana desce pelo vão e os rolos a mordem dos dois lados)
        els.append({"from": [4.2, 6.0, 5.0], "to": [7.4, 22.0, 11.0], "faces": _faces(C0, "madeira")})
        els.append({"from": [8.6, 6.0, 5.0], "to": [11.8, 22.0, 11.0], "faces": _faces(C0, "madeira")})
        # estriado: anéis ENVOLVEM CADA rolo (não atravessam o vão — sem
        # virar prateleira)
        for y in (9.0, 13.0, 17.0):
            els.append({"from": [3.9, y, 4.7], "to": [7.7, y + 0.8, 11.3],
                        "faces": _faces(C2, "madeira")})
            els.append({"from": [8.3, y, 4.7], "to": [12.1, y + 0.8, 11.3],
                        "faces": _faces(C2, "madeira")})
        # cabeçote superior com eixo
        els.append({"from": [4.2, 22.0, 4.4], "to": [11.8, 24.5, 11.6], "faces": _faces(C1, "ferro")})
        els.append({"from": [6.4, 24.5, 7.2], "to": [9.6, 25.5, 8.8], "faces": _faces(EIXO, "ferro")})
        # engrenagem de tração (disco de madeira no eixo)
        els.append({"from": [5.6, 18.0, 3.0], "to": [10.4, 22.8, 4.6], "faces": _faces(C2, "madeira")})
        # manivela longa com punho
        els.append({"from": [11.8, 22.6, 7.4], "to": [15.0, 24.2, 8.6], "faces": _faces(MANIVELA, "ferro")})
        els.append({"from": [14.6, 22.2, 6.8], "to": [15.6, 24.6, 9.2], "faces": _faces(C3, "ferro")})
        # calha de saída do caldo (o caldo escorre pro balde)
        els.append({"from": [6.4, 3.2, 11.0], "to": [9.6, 4.6, 14.6], "faces": _faces(C2, "madeira")})
    elif tipo == "prensa":
        # v1.2.53 TAMANHO REAL (y 0..30): cesto alto + viga superior + fuso
        # descendo do parafuso de madeira — prensa de vinho de vila
        # base pesada + 4 colunas do cesto
        els.append({"from": [2.0, 0.0, 2.0], "to": [14.0, 3.0, 14.0], "faces": _faces(C2, "madeira")})
        for (x0, z0, x1, z1) in ((2.0, 2.0, 3.6, 14.0), (12.4, 2.0, 14.0, 14.0),
                                  (3.6, 2.0, 12.6, 3.6), (3.6, 12.4, 12.6, 14.0)):
            els.append({"from": [x0, 3.0, z0], "to": [x1, 17.0, z1], "faces": _faces(C0, "madeira")})
        # aro no topo do cesto
        els.append({"from": [1.6, 16.6, 1.6], "to": [14.4, 18.0, 14.4], "faces": _faces(C2, "madeira")})
        # mosto coado no fundo do cesto (visível entre as colunas)
        els.append({"from": [3.6, 6.4, 3.6], "to": [12.6, 7.4, 12.6], "faces": _faces(C3, "liquido")})
        # prato de pressão (desce esmagando)
        els.append({"from": [3.6, 12.0, 3.6], "to": [12.6, 13.4, 12.6], "faces": _faces(C2, "madeira")})
        # fuso central + porca do parafuso
        els.append({"from": [7.2, 13.4, 7.2], "to": [8.8, 25.5, 8.8], "faces": _faces(FUSO, "ferro")})
        els.append({"from": [6.4, 18.0, 6.4], "to": [9.6, 19.4, 9.6], "faces": _faces(C2, "madeira")})
        # viga superior (o parafuso rosca nela)
        els.append({"from": [1.0, 25.5, 1.0], "to": [15.0, 28.5, 15.0], "faces": _faces(C2, "madeira")})
        els.append({"from": [6.6, 24.0, 6.6], "to": [9.4, 25.5, 9.4], "faces": _faces(BORDA, "ferro")})
        # travessas ligando as colunas à viga
        for (x0, z0, x1, z1) in ((2.4, 2.4, 3.6, 13.6), (12.4, 2.4, 13.6, 13.6)):
            els.append({"from": [x0, 18.0, z0], "to": [x1, 25.5, z1], "faces": _faces(C0, "madeira")})
        # bica de saída do mosto
        els.append({"from": [6.8, 1.2, 11.6], "to": [9.2, 2.6, 14.6], "faces": _faces(C2, "madeira")})
    else:  # caldeirão
        # v1.2.53 TAMANHO REAL (y 0..31): pote ABERTO de ferro de altura de
        # gente — fundo + 4 paredes + mosto visível + aro + alças + tripé
        els.append({"from": [2.0, 0.0, 2.0], "to": [14.0, 4.0, 14.0], "faces": _faces(C2, "ferro")})
        els.append({"from": [2.6, 4.0, 2.6], "to": [13.4, 5.2, 13.4], "faces": _faces(C2, "ferro")})
        for (x0, z0, x1, z1) in ((1.4, 1.4, 14.6, 2.8), (1.4, 13.2, 14.6, 14.6),
                                  (1.4, 2.8, 2.8, 13.2), (13.2, 2.8, 14.6, 13.2)):
            els.append({"from": [x0, 5.2, z0], "to": [x1, 22.5, z1], "faces": _faces(C0, "ferro")})
        # superfície do mosto visível pela boca (perto da borda)
        els.append({"from": [2.8, 21.0, 2.8], "to": [13.2, 22.0, 13.2], "faces": _faces(C3, "liquido")})
        # aro da boca: 4 faixas acompanhando as paredes (não flutua)
        for (x0, z0, x1, z1) in ((1.0, 1.0, 15.0, 2.6), (1.0, 13.4, 15.0, 15.0),
                                  (1.0, 2.6, 2.6, 13.4), (13.4, 2.6, 15.0, 13.4)):
            els.append({"from": [x0, 22.5, z0], "to": [x1, 23.7, z1], "faces": _faces(BORDA, "ferro")})
        # alças laterais grandes (grudadas nas paredes)
        els.append({"from": [0.0, 16.0, 6.6], "to": [1.4, 18.0, 9.4], "faces": _faces(ALCAS, "ferro")})
        els.append({"from": [14.6, 16.0, 6.6], "to": [15.0, 18.0, 9.4], "faces": _faces(ALCAS, "ferro")})
        # colherão pendurado no aro (o cozinheiro esqueceu)
        els.append({"from": [11.8, 18.5, 6.9], "to": [12.4, 23.5, 7.5], "faces": _faces(ALCAS, "ferro")})
        els.append({"from": [11.2, 17.5, 6.5], "to": [13.0, 18.7, 7.9], "faces": _faces(C2, "ferro")})
    modelo_tex = {
        "texture_size": [128, 128],
        "textures": {
            "madeira": "intoxicantes:block/" + tex,
            "ferro": "intoxicantes:block/" + tex,
            "liquido": "intoxicantes:block/" + tex,
            "particle": "intoxicantes:block/" + tex,
        },
    }
    _fatiar_em_2(nome, els, modelo_tex["textures"], [128, 128], 0.68)


def _display_padrao(escala=1.0):
    s = escala
    return {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0],
                 "scale": [0.85 * s, 0.85 * s, 0.85 * s]},
        "ground": {"translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"scale": [1, 1, 1]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                   "scale": [0.5, 0.5, 0.5]},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                  "scale": [0.5, 0.5, 0.5]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 1, 0],
                                   "scale": [0.6, 0.6, 0.6]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 1, 0],
                                  "scale": [0.6, 0.6, 0.6]},
    }


# ============================================================ TEXTURAS 64x64 DOS BLOCOS

def gerar_texturas_blocos():
    # barris — paletas por identidade (spec 8)
    textura_barril("cachaca", "C8A86A", "A8874C", "7A5C30", "B8B0A4", "6E6A60", "D9C27E")
    textura_barril("cerveja", "D8B978", "BC9C58", "8A6C38", "3E3830", "241F1A", "9BC44D")
    textura_barril("rum", "5A4028", "452F1C", "2E1F12", "2A2420", "151210", "3A2A18", carbonizada=True)
    textura_barril("vinho", "8A6A48", "6E5436", "4A3620", "C0B4A4", "6E6A60", "7A2E52")

    # dorna, alambique, moenda, prensa e caldeirão: geradas em
    # gerar_texturas_maquinas() — atlas 128x128 com células de material de 64px.


def _clarear(cor, t):
    return (int(cor[0] + (255 - cor[0]) * t), int(cor[1] + (255 - cor[1]) * t),
            int(cor[2] + (255 - cor[2]) * t), cor[3])


# ============================================================ MÁQUINAS 128x128
# Atlas 2x2 de células de 64px (um material por célula): a densidade de pixels
# acompanha o modelo 3D — mesma filosofia das armas 3D (regra §8 do AGENTS.md).
# Células em pixels: 0=(0,0) 1=(64,0) 2=(0,64) 3=(64,64); em unidades de UV
# (texture_size 128): C0=(0,0,8,8) C1=(8,0,16,8) C2=(0,8,8,16) C3=(8,8,16,16).

_CEL = 64


def _atlas_maquina(nome, c0, c1, c2, c3):
    px = [[(0, 0, 0, 0)] * (_CEL * 2) for _ in range(_CEL * 2)]
    for y in range(_CEL):
        for x in range(_CEL):
            px[y][x] = c0[y][x]
            px[y][_CEL + x] = c1[y][x]
            px[_CEL + y][x] = c2[y][x]
            px[_CEL + y][_CEL + x] = c3[y][x]
    _png(os.path.join(TEX, nome + ".png"), px)
    print("máquina 128x128:", nome)


def _celula_madeira(rng, claro, medio, escuro, vertical=True):
    """Tábuas de 16px com fresta, canto iluminado, grão fibroso e 2 nós."""
    cel = []
    nos = ((18, 22), (46, 44))
    for y in range(_CEL):
        linha = []
        for x in range(_CEL):
            a, b = (x, y) if vertical else (y, x)
            base = _mistura(claro, medio, (a % 32) / 31.0)
            if (a // 16) % 2:
                base = _mistura(base, escuro, 0.18)      # alterna tom por tábua
            borda = a % 16
            if borda == 15:
                base = _mistura(base, escuro, 0.55)      # fresta entre tábuas
            elif borda == 0:
                base = _clarear(base, 0.10)              # canto iluminado
            g = ((b * 7 + (a // 16) * 31) % 9) - 4       # grão ao longo da fibra
            if b % 3 == 0:
                g -= 2
            c = tuple(max(0, min(255, v + g * 2)) for v in base[:3])
            for (nx, ny) in nos:                         # nós de madeira
                r = ((x - nx) ** 2 + (y - ny) ** 2) ** 0.5
                if r < 4.5:
                    c = tuple(int(v * (0.62 + r * 0.09)) for v in c)
            linha.append(c + (255,))
        cel.append(linha)
    return cel


def _celula_metal(rng, claro, escuro, pates=0.0):
    """Chapas metálicas: degradê, vinco a cada 32px, rebites nos encontros,
    estrias de escovado; pates > 0 mistura pátina (cobre envelhecido)."""
    patina = (74, 122, 98)
    cel = []
    for y in range(_CEL):
        linha = []
        for x in range(_CEL):
            base = _mistura(claro, escuro, y / 63.0)
            if y % 32 == 31:
                base = _mistura(base, escuro, 0.5)       # vinco entre chapas
            elif y % 32 == 0:
                base = _clarear(base, 0.12)
            s = ((x * 5 + y * 13) % 11) - 5              # escovado
            c = tuple(max(0, min(255, v + s * 2)) for v in base[:3])
            if pates and ((x * 7 + y * 11) % 23) < pates * 23:
                c = _mistura(c, patina, 0.35)[:3]
            if y % 32 in (30, 31) and x % 32 in (15, 16, 47, 48):
                c = tuple(max(0, v - 32) for v in c)     # sombra do rebite
            if y % 32 == 1 and x % 32 in (15, 16, 47, 48):
                c = tuple(min(255, v + 46) for v in c)   # brilho do rebite
            linha.append(c + (255,))
        cel.append(linha)
    return cel


def _celula_liquido(rng, topo, fundo, brilho=(240, 244, 210)):
    """Líquido vivo: gradiente + bolhas claras com borda (fermentação)."""
    bolhas = [(6, 10, 3), (20, 4, 2), (34, 18, 4), (52, 8, 2), (12, 30, 3),
              (40, 34, 5), (58, 26, 2), (26, 50, 3), (50, 52, 4), (8, 52, 2)]
    cel = []
    for y in range(_CEL):
        linha = []
        for x in range(_CEL):
            c = _mistura(topo, fundo, y / 63.0)[:3]
            for (bx, by, br) in bolhas:
                r = ((x - bx) ** 2 + (y - by) ** 2) ** 0.5
                if r < br:
                    c = _mistura(c, brilho, 0.30 + 0.25 * (1 - r / br))[:3]
                elif r < br + 1:
                    c = tuple(int(v * 0.88) for v in c)
            linha.append(c + (255,))
        cel.append(linha)
    return cel


def gerar_texturas_maquinas():
    """As 5 máquinas em alta resolução: atlas 128x128, células de 64px."""
    # ---- dorna: tábuas claras (C0) + mosto verde vivo (C1) + aro/pernas (C2)
    r = Random(41)
    _atlas_maquina("dorna",
                   _celula_madeira(r, _hex("D2B072"), _hex("A8874C"), _hex("6E5436")),
                   _celula_liquido(r, _hex("C4CE74"), _hex("7E9038")),
                   _celula_madeira(r, _hex("9A7A48"), _hex("6E5436"), _hex("4A3620")),
                   _celula_metal(r, _hex("5A5048"), _hex("38322C")))

    # ---- alambique: cobre polido (C0) + tampa/pescoço (C1) + serpentina com
    #      pátina (C2) + balde de madeira (C3)
    r = Random(42)
    _atlas_maquina("alambique",
                   _celula_metal(r, _hex("D08448"), _hex("A85C2E"), pates=0.10),
                   _celula_metal(r, _hex("B87038"), _hex("8A4E24")),
                   _celula_metal(r, _hex("A86430"), _hex("6E3E1E"), pates=0.45),
                   _celula_madeira(r, _hex("9A7A48"), _hex("6E5436"), _hex("4A3620"), vertical=False))

    # ---- moenda: spruce (C0) + ferro dos rolos (C1) + madeira escura (C2) +
    #      ferro escuro da manivela (C3)
    r = Random(43)
    _atlas_maquina("moenda_cana",
                   _celula_madeira(r, _hex("C09A5E"), _hex("9A7A44"), _hex("6E5430")),
                   _celula_metal(r, _hex("9A9AA0"), _hex("5E5E64")),
                   _celula_madeira(r, _hex("9A7A44"), _hex("6E5430"), _hex("4A3620")),
                   _celula_metal(r, _hex("6E6E74"), _hex("3E3E44")))

    # ---- prensa: bétula clara (C0) + fuso de ferro (C1) + vigas (C2) +
    #      mosto bordô (C3)
    r = Random(44)
    _atlas_maquina("prensa_uvas",
                   _celula_madeira(r, _hex("E0D2A4"), _hex("BCA468"), _hex("8A7440")),
                   _celula_metal(r, _hex("A8A8AE"), _hex("66666C")),
                   _celula_madeira(r, _hex("C8B078"), _hex("9A8248"), _hex("66543A")),
                   _celula_liquido(r, _hex("8E3C60"), _hex("5A1E3C"), brilho=(228, 190, 205)))

    # ---- caldeirão: ferro do corpo (C0) + borda clara (C1) + base/alças (C2) +
    #      mostura dourada (C3)
    r = Random(45)
    _atlas_maquina("caldeirao_mostura",
                   _celula_metal(r, _hex("6A6A70"), _hex("42424A")),
                   _celula_metal(r, _hex("8A8A92"), _hex("56565E")),
                   _celula_metal(r, _hex("4A4A52"), _hex("2C2C32")),
                   _celula_liquido(r, _hex("E8BC50"), _hex("B07E2A")))


# ============================================================ ITENS (16x16)

# ============================================================ PINTURA HD (128×128)
# Regra do AGENTS.md: toda textura nasce em ALTA RESOLUÇÃO e bem acabada.
# Pintores vetoriais simples sobre grade RGBA 128×128: formas, shading,
# gradiente, bolhas, brilho e contorno — nada de arte 16×16 esticada.

HD = 128


def _grade_hd():
    """Grade HD vazia (pixels transparentes)."""
    return [[(0, 0, 0, 0)] * HD for _ in range(HD)]


def _ret(g, x0, y0, x1, y1, cor):
    """Retângulo preenchido."""
    for y in range(max(int(y0), 0), min(int(y1) + 1, HD)):
        for x in range(max(int(x0), 0), min(int(x1) + 1, HD)):
            g[y][x] = cor


def _elipse(g, cx, cy, rx, ry, cor):
    """Elipse preenchida."""
    for y in range(max(int(cy - ry) - 1, 0), min(int(cy + ry) + 2, HD)):
        for x in range(max(int(cx - rx) - 1, 0), min(int(cx + rx) + 2, HD)):
            dx, dy = (x - cx) / rx, (y - cy) / ry
            if dx * dx + dy * dy <= 1.0:
                g[y][x] = cor


def _linha(g, x0, y0, x1, y1, cor, esp=2):
    """Linha com espessura (nervuras, brilhos, costuras)."""
    passos = int(max(abs(x1 - x0), abs(y1 - y0), 1)) * 2
    for i in range(passos + 1):
        t = i / passos
        x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
        m = esp / 2
        for yy in range(int(y - m), int(y + m) + 1):
            for xx in range(int(x - m), int(x + m) + 1):
                if 0 <= xx < HD and 0 <= yy < HD:
                    g[yy][xx] = cor


def _folha(g, x0, y0, x1, y1, cor, esp0=8, curva=12):
    """Lâmina afinada em arco (bezier quadrática) — folha de cereal."""
    px, py = float(y0 - y1), float(x1 - x0)          # perpendicular ao eixo
    norm = (px * px + py * py) ** 0.5 or 1.0
    px, py = px / norm, py / norm
    n = int(max(abs(x1 - x0), abs(y1 - y0), 4)) * 2
    for i in range(n + 1):
        t = i / n
        bx = x0 + (x1 - x0) * t + px * curva * 4 * t * (1 - t)
        by = y0 + (y1 - y0) * t + py * curva * 4 * t * (1 - t)
        m = (esp0 * (1 - t) + 1) / 2
        for yy in range(int(by - m), int(by + m) + 1):
            for xx in range(int(bx - m), int(bx + m) + 1):
                if 0 <= xx < HD and 0 <= yy < HD:
                    g[yy][xx] = cor


def _contorno_hd(g, cor=(30, 26, 22, 210)):
    """Contorno de 1px em volta da silhueta (padrão de item do mod, em HD)."""
    sil = [[g[y][x][3] != 0 for x in range(HD)] for y in range(HD)]
    for y in range(HD):
        for x in range(HD):
            if sil[y][x]:
                continue
            for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                yy, xx = y + dy, x + dx
                if 0 <= yy < HD and 0 <= xx < HD and sil[yy][xx]:
                    g[y][x] = cor
                    break


def _salva_hd(path, g, min_opacos=400):
    """Grava a grade HD com guardas de regressão (textura fraca = erro)."""
    _png(path, g)
    opacos = sum(1 for linha in g for p in linha if p[3] > 0)
    if opacos < min_opacos:
        raise SystemExit("BUG: %s saiu com %d pixels opacos (<%d) — arte HD falhou"
                         % (path, opacos, min_opacos))


def _garrafa_hd(path, cor_liq, cor_clara, bolhas=4, sedimento=False):
    """Garrafinha de vidro HD: corpo bojudo, pescoço, tampa de lata, líquido
    com gradiente vertical, bolhas subindo, menisco e brilho de vidro."""
    g = _grade_hd()
    VIDRO = _hex("9FC4CC", 130)
    LATA, LATA_ESC = _hex("B8B4AC"), _hex("8A867E")
    # vidro: pescoço + corpo bojudo
    _ret(g, 53, 14, 74, 50, VIDRO)
    _elipse(g, 64, 80, 36, 40, VIDRO)
    # líquido (só onde há vidro), escuro embaixo -> claro em cima
    esc = _mistura(cor_liq, (10, 8, 6, 255), 0.35)
    for y in range(HD):
        for x in range(HD):
            if g[y][x][3] == 0 or y < 24:
                continue
            dx, dy = (x - 64) / 31.0, (y - 80) / 35.0
            if dx * dx + dy * dy > 1.0 and not (57 <= x <= 70 and 16 <= y <= 50):
                continue
            t = min(max((y - 24) / 100.0, 0.0), 1.0)
            g[y][x] = _mistura(esc, cor_clara, 0.5 - t * 0.5)
    # menisco no pescoço + bolhas de fermentação
    _elipse(g, 64, 24, 8, 3, cor_clara)
    for i in range(bolhas):
        bx = 52 + (i * 23) % 26
        by = 100 - i * 14
        raio = 2 + i % 2
        _elipse(g, bx, by, raio, raio, _mistura(cor_clara, (255, 255, 255, 255), 0.35))
    if sedimento:
        _elipse(g, 64, 112, 26, 6, _mistura(esc, (0, 0, 0, 255), 0.25))
    # brilho do vidro (coluna esquerda + reflexo no ombro)
    _linha(g, 44, 52, 44, 106, _hex("FFFFFF", 70), 5)
    _linha(g, 50, 44, 58, 36, _hex("FFFFFF", 90), 4)
    # tampa de lata com vinco
    _ret(g, 49, 4, 79, 16, LATA)
    _linha(g, 49, 10, 79, 10, LATA_ESC, 2)
    _ret(g, 49, 4, 79, 6, _hex("D4D0C8"))
    _contorno_hd(g)
    _salva_hd(path, g)


def _pote_hd(path, cor, cor_clara):
    """Pote bojudo de melaço: vidro grosso, rolha de cortiça, conteúdo denso."""
    g = _grade_hd()
    VIDRO = _hex("9FC4CC", 130)
    CORCO, CORCO_ESC = _hex("B08850"), _hex("8A6838")
    _ret(g, 50, 30, 78, 52, VIDRO)
    _elipse(g, 64, 86, 40, 34, VIDRO)
    esc = _mistura(cor, (8, 6, 4, 255), 0.4)
    for y in range(40, HD):
        for x in range(HD):
            if g[y][x][3] == 0:
                continue
            dx, dy = (x - 64) / 35.0, (y - 86) / 29.0
            if dx * dx + dy * dy > 1.0 and not (54 <= x <= 74 and 40 <= y <= 60):
                continue
            t = min(max((y - 40) / 90.0, 0.0), 1.0)
            g[y][x] = _mistura(esc, cor_clara, 0.45 - t * 0.45)
    _elipse(g, 64, 40, 11, 4, cor_clara)             # menisco sob a rolha
    _linha(g, 42, 62, 42, 108, _hex("FFFFFF", 60), 5)
    _elipse(g, 64, 28, 17, 8, CORCO)                 # rolha
    _elipse(g, 64, 26, 12, 5, _mistura(CORCO, (255, 255, 255, 255), 0.2))
    _linha(g, 50, 30, 78, 30, CORCO_ESC, 2)
    _contorno_hd(g)
    _salva_hd(path, g)


def _espiga_hd(g, x, y_base, y_topo, escala, cores):
    """Espiga de cereal: grãos aos pares ao longo do caule + arestas no topo."""
    clara, media, escura = (_hex(c) for c in cores)
    _linha(g, x, y_base, x, y_topo + 10,
           _mistura(media, (90, 60, 20, 255), 0.3), 4)
    for i in range(5):
        gy = y_topo + 12 + i * int(9 * escala)
        lado = 1 if i % 2 == 0 else -1
        _elipse(g, x + lado * 5 * escala, gy, 5 * escala, 8 * escala,
                clara if i % 2 == 0 else media)
        _elipse(g, x - lado * 3 * escala, gy + 3, 3 * escala, 5 * escala, escura)
    _elipse(g, x, y_topo + 6, 4 * escala, 7 * escala, media)
    for dx, dy in ((-8, -20), (0, -26), (8, -20)):    # arestas (barbas)
        _linha(g, x, y_topo + 8, x + dx, y_topo + 8 + dy, _hex("F0E0A0"), 2)


def _sheaf_hd(path, cores):
    """Feixe de 3 espigas amarrado com fibra + grãos soltos na base."""
    g = _grade_hd()
    _espiga_hd(g, 34, 116, 36, 0.95, cores)
    _espiga_hd(g, 64, 118, 22, 1.05, cores)
    _espiga_hd(g, 94, 116, 38, 0.9, cores)
    _linha(g, 22, 98, 106, 94, _hex("8A5A2A"), 6)    # amarra
    _linha(g, 22, 104, 106, 100, _hex("6E4420"), 3)
    for (sx, sy) in ((26, 120), (48, 123), (86, 122), (106, 118)):
        _elipse(g, sx, sy, 5, 3, _hex(cores[1]))
        _linha(g, sx + 3, sy - 2, sx + 7, sy - 4, _hex("F0E0A0"), 1)
    _contorno_hd(g)
    _salva_hd(path, g)


def _bagaco_hd(path):
    """Bagaço de cana: montinho de fibra esmagada com feixes e cacos de folha."""
    g = _grade_hd()
    F_ESC, F_MED, F_CLA = _hex("8A7A48"), _hex("A89860"), _hex("C4B478")
    _elipse(g, 64, 112, 44, 9, (0, 0, 0, 60))         # sombra no chão
    _elipse(g, 64, 94, 46, 26, F_ESC)
    _elipse(g, 62, 88, 38, 22, F_MED)
    _elipse(g, 58, 82, 24, 14, _mistura(F_MED, F_CLA, 0.5))
    feixes = [((30, 108), (44, 70)), ((52, 112), (60, 66)), ((74, 110), (68, 62)),
              ((92, 106), (80, 72)), ((104, 100), (90, 84)), ((40, 96), (54, 80)),
              ((78, 96), (92, 78)), ((64, 104), (76, 92))]
    for (x0, y0), (x1, y1) in feixes:
        _linha(g, x0, y0, x1, y1, F_ESC, 3)
        _linha(g, x0 + 3, y0 - 2, x1 + 2, y1 - 2, F_CLA, 2)
    _folha(g, 46, 72, 24, 56, _hex("9A8C4A"), esp0=6, curva=10)
    _folha(g, 84, 70, 104, 54, _hex("8F8244"), esp0=6, curva=-10)
    _contorno_hd(g)
    _salva_hd(path, g, min_opacos=600)


def gerar_texturas_itens():
    """Itens intermediários da destilaria em ALTA RESOLUÇÃO (regra do AGENTS.md):
    garrafas com gradiente/bolhas/brilho, pote de melaço, feixes de cereal e
    fibra do bagaço — tudo pintado 128×128 pela fonte.

    v1.2.65: os PRODUTOS DE ETAPA (caldo, mostos, melaço, jovens) viraram
    frascos 3D do gen_garrafas.py — a arte abaixo permanece como referência,
    mas NÃO é gravada (o gen_garrafas é quem pinta o atlas deles)."""
    APOSENTADOS = {"caldo_de_cana", "mosto_cana_fermentado", "mosto_rum_fermentado",
                   "mosto_de_uva", "mosto_cerveja_lupulado", "cachaca_jovem",
                   "rum_jovem", "melaco"}
    # Cevada, malte e bagaço são gerados exclusivamente por gen_catalogo.py.
    for nome in sorted(APOSENTADOS):
        print(f"  SKIP (frasco 3D do gen_garrafas.py desde a v1.2.65): {nome}.png")




# ============================================================ BLOCKSTATE / LOOT / ITEMS

BARRIS = ["cachaca", "cerveja", "rum", "vinho"]
MAQUINAS = [("moenda_cana",), ("prensa_uvas",), ("caldeirao_mostura",)]


MAQUINAS_2BLOCOS = ["moenda_cana", "prensa_uvas", "caldeirao_mostura",
                    "dorna_bebida", "alambique"]


def blockstate_2_blocos(nome):
    """v1.2.53: blockstate das máquinas de tamanho real — a propriedade
    `metade` do MaquinaGrandeBlock escolhe o modelo da parte."""
    return {"variants": {
        "metade=lower": {"model": "intoxicantes:block/%s_baixo" % nome},
        "metade=upper": {"model": "intoxicantes:block/%s_alto" % nome},
    }}


def gerar_blockstates():
    for b in BARRIS:
        wjson(os.path.join(ASSETS, "blockstates", "barril_%s.json" % b),
              {"variants": {"": {"model": "intoxicantes:block/barril_" + b}}})
    for (m,) in MAQUINAS:
        wjson(os.path.join(ASSETS, "blockstates", m + ".json"),
              blockstate_2_blocos(m))
    wjson(os.path.join(ASSETS, "blockstates", "dorna_bebida.json"),
          blockstate_2_blocos("dorna_bebida"))
    wjson(os.path.join(ASSETS, "blockstates", "alambique.json"),
          blockstate_2_blocos("alambique"))
    # cevada: crop vanilla de 8 ages — um modelo por age (nenhum par sem modelo)
    cevada_variants = {}
    for age in range(8):
        cevada_variants["age=%d" % age] = {
            "model": "intoxicantes:block/cevada_stage%d" % age}
    wjson(os.path.join(ASSETS, "blockstates", "cevada_plant.json"),
          {"variants": cevada_variants})
    # modelos cross dos 8 estágios da cevada (referenciados acima)
    for st in range(8):
        wjson(os.path.join(ASSETS, "models", "block", "cevada_stage%d.json" % st),
              {"parent": "minecraft:block/cross",
               "textures": {"cross": "intoxicantes:block/cevada_stage%d" % st}})


def gerar_item_defs():
    # barris e máquinas: item = o modelo 3D do bloco
    todos = ["barril_" + b for b in BARRIS] + [m for (m,) in MAQUINAS] \
        + ["dorna_bebida", "alambique"]
    for nome in todos:
        # máquinas de 2 blocos: o item mostra a máquina INTEIRA (modelo _item)
        if nome in MAQUINAS_2BLOCOS:
            wjson(os.path.join(ASSETS, "items", nome + ".json"),
                  {"model": {"type": "minecraft:model",
                              "model": "intoxicantes:block/" + nome + "_item"}})
            wjson(os.path.join(ASSETS, "models", "item", nome + ".json"),
                  {"parent": "intoxicantes:block/" + nome + "_item"})
            continue
        wjson(os.path.join(ASSETS, "items", nome + ".json"),
              {"model": {"type": "minecraft:model",
                          "model": "intoxicantes:block/" + nome}})
        wjson(os.path.join(ASSETS, "models", "item", nome + ".json"),
              {"parent": "intoxicantes:block/" + nome})
    # cevada plant (item do bloco = espiga madura)
    wjson(os.path.join(ASSETS, "items", "cevada_plant.json"),
          {"model": {"type": "minecraft:model",
                      "model": "intoxicantes:item/cevada_plant"}})
    wjson(os.path.join(ASSETS, "models", "item", "cevada_plant.json"),
          {"parent": "minecraft:item/generated",
           "textures": {"layer0": "intoxicantes:block/cevada_stage7"}})
    # Frascos pertencem a gen_garrafas.py; os três sólidos, a gen_catalogo.py.
    # O saquinho de cevada 3D pertence a gen_sementes.py, junto das outras sementes.


def gerar_loot():
    def dropa_si(nome):
        return {
            "type": "minecraft:block",
            "pools": [{"rolls": 1,
                        "entries": [{"type": "minecraft:item",
                                      "name": "intoxicantes:" + nome}]}],
            "random_sequence": "intoxicantes:blocks/" + nome,
        }
    for b in BARRIS:
        wjson(os.path.join(DATA, "loot_table", "blocks", "barril_%s.json" % b),
              dropa_si("barril_" + b))
    for (m,) in MAQUINAS:
        wjson(os.path.join(DATA, "loot_table", "blocks", m + ".json"), dropa_si(m))
    wjson(os.path.join(DATA, "loot_table", "blocks", "dorna_bebida.json"),
          dropa_si("dorna_bebida"))
    wjson(os.path.join(DATA, "loot_table", "blocks", "alambique.json"),
          dropa_si("alambique"))
    # cevada: semente sempre; grão no estágio >= 4 (age 4..7, escalando)
    pools = [
        {"rolls": 1, "entries": [{"type": "minecraft:item",
                                   "name": "intoxicantes:semente_cevada"}]},
    ]
    for age in range(4, 8):
        qtd = age - 3  # age4=1 ... age7=4 grãos
        pools.append({
            "rolls": 1,
            "condition": {"type": "minecraft:match_block",
                           "blocks": "intoxicantes:cevada_plant",
                           "state": {"age": str(age)}},
            "entries": [{"type": "minecraft:item", "name": "intoxicantes:cevada",
                          "functions": [{"function": "minecraft:set_count",
                                          "count": qtd}]}],
        })
    wjson(os.path.join(DATA, "loot_table", "blocks", "cevada_plant.json"),
          {"type": "minecraft:block", "pools": pools,
           "random_sequence": "intoxicantes:blocks/cevada_plant"})


def main():
    gerar_texturas_blocos()
    gerar_texturas_maquinas()
    barril_modelo("cachaca", "barril_cachaca")
    barril_modelo("cerveja", "barril_cerveja", torneira=True)
    barril_modelo("rum", "barril_rum")
    barril_modelo("vinho", "barril_vinho")
    dorna_modelo()
    alambique_modelo()
    maquina_modelo("moenda_cana", "moenda_cana", "moenda")
    maquina_modelo("prensa_uvas", "prensa_uvas", "prensa")
    maquina_modelo("caldeirao_mostura", "caldeirao_mostura", "caldeirao")
    gerar_texturas_itens()
    gerar_cevada_texturas()
    gerar_blockstates()
    gerar_item_defs()
    gerar_loot()
    print("OK: assets da destilaria gerados (8 blocos 3D + 11 itens + cevada)")


def gerar_cevada_texturas():
    """8 estágios da cevada em ALTA RESOLUÇÃO (cross, 1:1 com os ages).

    Colmos com nós e lâminas em arco, espigas douradas com arestas (barbas),
    plantio maduro amarelecendo — pintado 128×128 pela fonte (regra do
    AGENTS.md). O dourado continua sendo a assinatura visual do 'pronto'.
    """
    VERDE, VERDE_MED, VERDE_ESC = "74A84E", "5C8C3C", "456E2E"
    AMARELO, PALHA = "CDB85E", "E0CC80"
    DOURADO, DOURADO_ESC, ARESTA = "E4BC54", "B4882E", "F0E0A0"
    V, VM, VE = _hex(VERDE), _hex(VERDE_MED), _hex(VERDE_ESC)

    def altura_stage(stage):
        return min(44 + stage * 10, 106)          # topo: 78 (s1) .. 16 (s7)

    def colmos(g, stage):
        """Desenha os colmos do estágio; devolve as posições x deles."""
        if stage == 0:
            # broto: caule curto + dois cotilédones em V
            _linha(g, 64, 122, 64, 100, VM, 6)
            _folha(g, 64, 104, 42, 82, V, esp0=9, curva=14)
            _folha(g, 64, 104, 86, 82, VE, esp0=9, curva=-14)
            return []
        xs = [64] if stage == 1 else ([44, 84] if stage == 2 else
             ([28, 64, 100] if stage <= 4 else [20, 52, 76, 108]))
        topo = 122 - altura_stage(stage)
        for x in xs:
            _linha(g, x, 122, x, topo, VM, 5)
            for ny in range(114, topo, -22):      # nós claros a cada 22px
                if ny > topo + 6:
                    _linha(g, x - 2, ny, x + 2, ny, V, 3)
            lado = -1 if x < 64 else (1 if x > 64 else 0)
            if lado == 0:
                lado = 1 if stage % 2 else -1
            for i in range(min(1 + stage // 2, 3)):
                fy = 112 - i * 26
                if fy < topo + 8:
                    break
                s_lado = lado if i % 2 == 0 else -lado
                _folha(g, x, fy, x + s_lado * (18 + stage * 2), fy + 14,
                       VE if i % 2 else VM, esp0=7, curva=s_lado * 12)
        return xs

    def espigas(g, xs, stage):
        """Espigas douradas com arestas no topo de cada colmo (stage 6/7)."""
        topo = 122 - altura_stage(stage)
        for x in xs:
            for i in range(5):
                gy = topo + 40 - i * 9
                l = 1 if i % 2 == 0 else -1
                _elipse(g, x + l * 5, gy, 5, 8,
                        _hex(DOURADO if i % 2 == 0 else PALHA))
                _elipse(g, x - l * 3, gy + 4, 3, 5, _hex(DOURADO_ESC))
            _elipse(g, x, topo + 8, 4, 8, _hex(DOURADO))
            for dx, dy in ((-7, -16), (0, -22), (7, -16)):
                _linha(g, x, topo + 8, x + dx, topo + 8 + dy, _hex(ARESTA), 2)

    def amarelecer(g):
        """Folhas e colmo amarelando no plantio maduro (verde -> palha)."""
        mapa = {V: _hex(AMARELO), VM: _hex(PALHA), VE: _hex("A89448")}
        for y in range(HD):
            for x in range(HD):
                if g[y][x] in mapa:
                    g[y][x] = mapa[g[y][x]]

    for stage in range(8):
        g = _grade_hd()
        xs = colmos(g, stage)
        if stage >= 6:                            # espigas aparecem e firmam
            espigas(g, xs, stage)
        if stage == 7:
            amarelecer(g)                         # plantio maduro: palha+dourado
        if xs:                                    # sombra de contato na base
            _elipse(g, sum(xs) / len(xs), 120, 6 + 8 * len(xs), 6, (0, 0, 0, 55))
        _salva_hd(os.path.join(TEX, "cevada_stage%d.png" % stage), g,
                  min_opacos=250)
    print("cevada: 8 estágios HD regenerados (cross, 128×128)")
    # Não recriar o sprite antigo: atlas/modelo de sementes vêm de gen_sementes.py.


if __name__ == "__main__":
    main()
