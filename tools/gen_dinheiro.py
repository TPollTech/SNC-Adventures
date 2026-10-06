#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""gen_dinheiro.py — FAMÍLIA R$ completa do SNC Adventures (v1.2.60).

Fonte principal das texturas da economia (regra do AGENTS.md: editar AQUI,
nunca só o PNG gerado). Gera em ALTA RESOLUÇÃO:

  moeda_1  : 512×512  — moeda de aço, valor 1
  real     : 1024×432 — azul-petróleo, a tartaruga-marinha (valor 2)
  nota_5   : 1024×432 — violeta, a garça
  nota_10  : 1024×432 — vermelho, o papagaio
  nota_20  : 1024×432 — ocre, o sagui-leão
  nota_50  : 1024×432 — ocre-escuro, a onça-pintada
  nota_100 : 1024×432 — azul profundo, a arara-azul
  nota_200 : 1024×432 — cinza-esverdeado, o lobo-guará
  nota_500 : 1024×432 — dourada, o golfinho (edição comemorativa: só o
             Wither dropa — troféu de boss, não entra na bancada)

Cada cédula ganha TAMBÉM o VERSO ({id}_verso.png): meandro de segurança
vertical, guilloché por toda a largura, valor por extenso, R$ translúcido
e o mesmo animal no medalhão — como a segunda face das cédulas de verdade.

NÃO gera mais a `real.png` antiga por cima sem backup: na 1ª execução
após a migração, copia a arte manual do usuário pra `artes-manuais/`
e passa a gerar a cédula de 2 no padrão da família (pedido do usuário:
"faz a nota de 2 igual").

Acabamento de cédula (o "cara de verdade"): papel com fibras e degradê,
guilloché de ondas, moldura com rosácea nos cantos, número em relevo
(sombra+brilho), marca d'água, faixa de microtexto, serial, gravação
hachurada do animal — universo SNC (República do SNC, fauna própria,
nada de réplica de cédula real).

Saída:
  src/main/resources/assets/intoxicantes/textures/item/<id>.png
  models/item/<id>.json + items/<id>.json (os 7 novos)
  espelho no pack resourcepacks/minhas-texturas (sprite 2D — regra do pack)

Uso: python tools/gen_dinheiro.py   (a partir da raiz do projeto do mod)
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageChops

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(RAIZ, "src", "main", "resources", "assets", "intoxicantes",
                   "textures", "item")
MODELOS = os.path.join(RAIZ, "src", "main", "resources", "assets",
                       "intoxicantes", "models", "item")
DEFS = os.path.join(RAIZ, "src", "main", "resources", "assets", "intoxicantes",
                    "items")
PACK = os.path.join(RAIZ, "..", "resourcepacks", "minhas-texturas", "assets",
                    "intoxicantes", "textures", "item")

W, H = 1024, 432          # cédula (proporção da nota de 2: ~2.37:1)
MOEDA = 512               # canvas quadrado da moeda

FONTE_NUM = "C:/Windows/Fonts/arialbd.ttf"
FONTE_TXT = "C:/Windows/Fonts/arialbd.ttf"
FONTE_MICRO = "C:/Windows/Fonts/arial.ttf"

# ---------------------------------------------------------------------------
# paletas — tom base do papel, tinta de gravação, tinta clara e acento
# ---------------------------------------------------------------------------
NOTAS = {
    "real": {
        "valor": 2, "animal": "tartaruga",
        "papel": (216, 230, 236), "tinta": (30, 78, 96), "clara": (128, 176, 192),
        "acento": (46, 104, 124), "numero": (18, 60, 76),
        "legenda": "a tartaruga-marinha",
    },
    "nota_5": {
        "valor": 5, "animal": "garca",
        "papel": (236, 226, 238), "tinta": (74, 44, 96), "clara": (176, 150, 196),
        "acento": (112, 64, 140), "numero": (58, 30, 78), "legenda": "a garça",
    },
    "nota_10": {
        "valor": 10, "animal": "papagaio",
        "papel": (238, 222, 216), "tinta": (132, 32, 32), "clara": (206, 140, 128),
        "acento": (166, 52, 44), "numero": (104, 22, 22), "legenda": "o papagaio",
    },
    "nota_20": {
        "valor": 20, "animal": "sagui",
        "papel": (240, 230, 204), "tinta": (140, 96, 28), "clara": (214, 184, 122),
        "acento": (176, 124, 40), "numero": (116, 76, 18), "legenda": "o sagui",
    },
    "nota_50": {
        "valor": 50, "animal": "onca",
        "papel": (236, 222, 204), "tinta": (108, 62, 34), "clara": (198, 156, 118),
        "acento": (140, 84, 46), "numero": (88, 48, 24), "legenda": "a onça",
    },
    "nota_100": {
        "valor": 100, "animal": "arara",
        "papel": (218, 226, 236), "tinta": (36, 60, 118), "clara": (140, 162, 200),
        "acento": (58, 88, 152), "numero": (26, 44, 96), "legenda": "a arara",
    },
    "nota_200": {
        "valor": 200, "animal": "lobo",
        "papel": (226, 232, 224), "tinta": (76, 92, 82), "clara": (166, 182, 168),
        "acento": (100, 118, 104), "numero": (58, 72, 62), "legenda": "o lobo-guará",
    },
    "nota_500": {
        "valor": 500, "animal": "golfinho",
        "papel": (238, 224, 186), "tinta": (122, 84, 24), "clara": (218, 186, 118),
        "acento": (156, 112, 40), "numero": (96, 64, 16),
        "legenda": "edição comemorativa",
    },
}

MOEDA_1 = {
    "valor": 1,
    "aco": (206, 208, 212), "aco_escuro": (128, 132, 140),
    "aco_claro": (240, 242, 246), "tinta": (88, 94, 104),
}


def fnt(caminho, tamanho):
    try:
        return ImageFont.truetype(caminho, tamanho)
    except Exception:
        return ImageFont.load_default(tamanho)


def rnd(cor, alpha):
    return (cor[0], cor[1], cor[2], alpha)


# ---------------------------------------------------------------------------
# papel, guilloché, moldura, textos — a "cara de cédula"
# ---------------------------------------------------------------------------
def papel_base(p):
    """Papel com degradê vertical suave + fibras aleatórias discretas."""
    img = Image.new("RGBA", (W, H))
    topo = tuple(min(255, c + 10) for c in p["papel"])
    baixo = tuple(max(0, c - 22) for c in p["papel"])
    for y in range(H):
        t = y / (H - 1)
        # onda leve no degradê (o papel não é gradiente perfeito)
        k = t + 0.04 * math.sin(t * 9)
        cor = tuple(int(topo[i] + (baixo[i] - topo[i]) * k) for i in range(3))
        for x in range(W):
            img.putpixel((x, y), cor + (255,))
    rng = random.Random(42 + p["valor"])
    fibras = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    df = ImageDraw.Draw(fibras)
    for _ in range(1400):
        x, y = rng.randrange(W), rng.randrange(H)
        l = rng.randint(2, 7)
        c = rng.choice([rnd((255, 255, 255), 26), rnd(p["tinta"], 14)])
        df.line([(x, y), (x + l, y + rng.randint(-1, 1))], fill=c, width=1)
    img.alpha_composite(fibras)
    return img


def guilloche(img, p):
    """Ondas gravadas nas laterais (o padrão entalhado das cédulas)."""
    d = ImageDraw.Draw(img)
    for base in range(56, H - 40, 22):
        pts = [(x, base + 7 * math.sin(x / 17.0 + base / 9.0))
               for x in range(24, 250, 4)]
        d.line(pts, fill=rnd(p["tinta"], 46), width=2)
        pts = [(W - x, base + 7 * math.sin(x / 19.0 + base / 11.0))
               for x in range(24, 250, 4)]
        d.line(pts, fill=rnd(p["tinta"], 40), width=2)
    # microtexto vertical na faixa direita
    fm = fnt(FONTE_MICRO, 13)
    d.text((W - 44, 30), ("SNC ADVENTURES • REPÚBLICA DO SNC • " * 9),
           font=fm, fill=rnd(p["tinta"], 70))


def moldura(img, p):
    """Moldura interna + rosáceas de cantos + vinhetas de borda."""
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([10, 10, W - 11, H - 11], radius=26,
                        outline=rnd(p["tinta"], 120), width=4)
    d.rounded_rectangle([22, 22, W - 23, H - 23], radius=20,
                        outline=rnd(p["tinta"], 60), width=2)
    for cx, cy in [(52, 52), (W - 52, 52), (52, H - 52), (W - 52, H - 52)]:
        for r, a in ((30, 130), (22, 90), (14, 70)):
            d.arc([cx - r, cy - r, cx + r, cy + r], 0, 360,
                  fill=rnd(p["tinta"], a), width=3)
        for ang in range(0, 360, 30):
            x = cx + 26 * math.cos(math.radians(ang))
            y = cy + 26 * math.sin(math.radians(ang))
            d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=rnd(p["tinta"], 110))
    # vinheta de borda: escurece levemente o contorno (profundidade)
    borda = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    db = ImageDraw.Draw(borda)
    db.rounded_rectangle([0, 0, W - 1, H - 1], radius=30, outline=(0, 0, 0, 60),
                         width=6)
    db.rounded_rectangle([4, 4, W - 5, H - 5], radius=28, outline=(0, 0, 0, 28),
                         width=10)
    img.alpha_composite(borda.filter(ImageFilter.GaussianBlur(3)))


def textos(img, p):
    d = ImageDraw.Draw(img)
    v = p["valor"]
    # número GRANDE em relevo (canto sup. direito) — sombra, brilho, tinta.
    # Alinhado pela MARGEM DIREITA: com âncora fixa, 3 dígitos (100/200/500)
    # a 132px estouravam pra fora da cédula.
    fn = fnt(FONTE_NUM, 132)
    txt = str(v)
    larg_num_g = d.textlength(txt, font=fn)
    pos = (W - 54 - larg_num_g, 26)
    d.text((pos[0] + 4, pos[1] + 5), txt, font=fn, fill=rnd(p["tinta"], 70))
    d.text((pos[0] - 3, pos[1] - 3), txt, font=fn, fill=(255, 255, 255, 90))
    d.text(pos, txt, font=fn, fill=p["numero"] + (255,))
    # número pequeno (canto inf. esquerdo) + REAIS (depois do número)
    fp = fnt(FONTE_NUM, 58)
    d.text((56, H - 96), txt, font=fp, fill=rnd(p["tinta"], 80))
    d.text((54, H - 98), txt, font=fp, fill=(255, 255, 255, 70))
    d.text((56, H - 100), txt, font=fp, fill=p["numero"] + (255,))
    ft = fnt(FONTE_TXT, 26)
    larg_num = d.textlength(txt, font=fp)
    d.text((56 + larg_num + 14, H - 74), "REAIS", font=ft,
           fill=rnd(p["numero"], 220))
    # topo: república + linha
    fr = fnt(FONTE_TXT, 30)
    titulo = "REPÚBLICA DO SNC"
    larg = d.textlength(titulo, font=fr)
    d.text(((W - larg) / 2, 30), titulo, font=fr, fill=rnd(p["numero"], 235))
    d.line([((W - larg) / 2, 70), ((W + larg) / 2, 70)],
           fill=rnd(p["tinta"], 90), width=2)
    fs = fnt(FONTE_MICRO, 17)
    sub = "SNC ADVENTURES · CÉDULA OFICIAL"
    larg2 = d.textlength(sub, font=fs)
    d.text(((W - larg2) / 2, 76), sub, font=fs, fill=rnd(p["tinta"], 130))
    # marca d'água: número pálido na área LIVRE (entre a coluna de plantas
    # e o oval do animal) — atrás de tudo, sem brigar com a gravação
    fw = fnt(FONTE_NUM, 170)
    larg3 = d.textlength(txt, font=fw)
    d.text((250 - larg3 / 2, 128), txt, font=fw, fill=rnd(p["clara"], 42))
    # seriais
    fser = fnt(FONTE_TXT, 20)
    d.text((46, 34), f"SNC {420000 + v * 7}A", font=fser, fill=rnd(p["tinta"], 170))
    d.text((W - 200, H - 48), f"BC {900 + v}SNC", font=fser,
           fill=(150, 40, 40, 200))
    # legenda do animal (baixo, centro-direita; acima do serial vermelho
    # pra legendas longas tipo "a tartaruga-marinha" não colidirem)
    fleg = fnt(FONTE_MICRO, 22)
    leg = p["legenda"]
    largl = d.textlength(leg, font=fleg)
    d.text((W - 250 - largl / 2, H - 74), leg, font=fleg,
           fill=rnd(p["tinta"], 150))


def faixa_plantas(img, p):
    """Coluna de folhas gravadas na esquerda (assinatura das cédulas do real)."""
    d = ImageDraw.Draw(img)
    rng = random.Random(7 + p["valor"])
    for i in range(9):
        y = 60 + i * 36
        x = 40 + (i % 2) * 8
        folha = [(x, y), (x + 20, y - 14), (x + 42, y - 4), (x + 46, y + 12),
                 (x + 24, y + 20)]
        d.polygon(folha, fill=rnd(p["clara"], 120), outline=rnd(p["tinta"], 160))
        d.line([(x, y + 6), (x + 44, y + 4)], fill=rnd(p["tinta"], 140), width=2)
        for _ in range(3):
            x2 = x + rng.randint(8, 34)
            d.line([(x2, y + 2), (x2 + 5, y - 6)], fill=rnd(p["tinta"], 90), width=1)


# ---------------------------------------------------------------------------
# animais gravados — silhueta com hachura de gravação
# ---------------------------------------------------------------------------
def _gravar(img, mascara, tinta, clara):
    """Aplica hachura horizontal (estilo metal) dentro da máscara."""
    g = Image.new("RGBA", img.size, (0, 0, 0, 0))
    dg = ImageDraw.Draw(g)
    for y in range(0, img.size[1], 6):
        dg.line([(0, y), (img.size[0], y)], fill=rnd(clara, 110), width=2)
    silhueta = Image.new("RGBA", img.size, tinta + (255,))
    sombra = silhueta.copy()
    alpha = sombra.split()[3]
    sombra.putalpha(alpha.point(lambda a: 190))
    img.paste(sombra, (0, 0), Image.composite(sombra.split()[3],
                                              Image.new("L", img.size, 0), mascara))
    # hachura só dentro da silhueta
    hachura = Image.composite(g, Image.new("RGBA", img.size, (0, 0, 0, 0)),
                              mascara)
    img.alpha_composite(hachura)
    # contorno
    contorno = Image.new("RGBA", img.size, (0, 0, 0, 0))
    dc = ImageDraw.Draw(contorno)
    m = mascara.filter(ImageFilter.MaxFilter(5))
    dc.bitmap((0, 0), m.point(lambda a: 60 if a > 0 else 0).convert("L"),
              fill=tinta + (160,))
    img.alpha_composite(contorno)


def _mascara_animal(id_animal, cx, cy):
    m = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(m)
    if id_animal == "tartaruga":
        # vista de cima — como na cédula real brasileira
        d.polygon([(cx - 150, cy - 36), (cx - 92, cy - 14),
                   (cx - 96, cy + 18), (cx - 148, cy + 40)], fill=255)   # nadadeira tras.
        d.polygon([(cx + 6, cy - 92), (cx + 42, cy - 138),
                   (cx + 84, cy - 88)], fill=255)                        # nadadeira sup.
        d.polygon([(cx + 6, cy + 92), (cx + 42, cy + 138),
                   (cx + 84, cy + 88)], fill=255)                        # nadadeira inf.
        d.ellipse([cx - 110, cy - 78, cx + 116, cy + 78], fill=255)      # casco
        d.ellipse([cx + 106, cy - 34, cx + 196, cy + 34], fill=255)      # cabeca
        d.polygon([(cx - 108, cy - 10), (cx - 142, cy + 2),
                   (cx - 108, cy + 14)], fill=255)                       # rabo
    elif id_animal == "garca":
        d.ellipse([cx - 90, cy - 20, cx + 110, cy + 70], fill=255)      # corpo
        d.polygon([(cx - 80, cy + 30), (cx - 160, cy + 30),
                   (cx - 160, cy + 42), (cx - 80, cy + 44)], fill=255)  # cauda
        d.polygon([(cx + 30, cy - 10), (cx + 40, cy - 90),
                   (cx + 58, cy - 92), (cx + 66, cy - 6)], fill=255)    # pescoço
        d.ellipse([cx + 40, cy - 116, cx + 92, cy - 68], fill=255)      # cabeça
        d.polygon([(cx + 86, cy - 100), (cx + 150, cy - 86),
                   (cx + 86, cy - 84)], fill=255)                       # bico
        d.line([(cx - 40, cy + 60), (cx - 46, cy + 120)], fill=255, width=8)
        d.line([(cx + 10, cy + 60), (cx + 12, cy + 120)], fill=255, width=8)
    elif id_animal == "papagaio":
        d.ellipse([cx - 70, cy - 40, cx + 80, cy + 90], fill=255)       # corpo
        d.ellipse([cx + 50, cy - 92, cx + 120, cy - 22], fill=255)      # cabeça
        d.polygon([(cx + 112, cy - 74), (cx + 168, cy - 52),
                   (cx + 112, cy - 44)], fill=255)                      # bico
        d.polygon([(cx - 70, cy + 20), (cx - 240, cy + 130),
                   (cx - 180, cy + 150), (cx - 50, cy + 80)], fill=255)  # cauda
        d.line([(cx + 10, cy + 80), (cx + 30, cy + 112)], fill=255, width=7)
        d.line([(cx - 20, cy + 82), (cx - 26, cy + 112)], fill=255, width=7)
        d.line([(cx - 140, cy + 140), (cx + 140, cy + 140)], fill=255, width=10)
    elif id_animal == "sagui":
        d.ellipse([cx - 60, cy - 50, cx + 60, cy + 60], fill=255)       # corpo
        d.ellipse([cx + 30, cy - 96, cx + 96, cy - 26], fill=255)       # cabeça
        for ang in range(0, 360, 30):                                   # juba
            x = cx + 62 + 34 * math.cos(math.radians(ang))
            y = cy - 62 + 34 * math.sin(math.radians(ang))
            d.ellipse([x - 8, y - 8, x + 8, y + 8], fill=255)
        d.arc([cx - 170, cy - 120, cx + 40, cy + 60], 60, 300, fill=255,
              width=16)                                                 # cauda
        d.line([(cx - 34, cy + 52), (cx - 40, cy + 108)], fill=255, width=8)
        d.line([(cx + 26, cy + 52), (cx + 30, cy + 108)], fill=255, width=8)
    elif id_animal == "onca":
        d.ellipse([cx - 130, cy - 60, cx + 90, cy + 60], fill=255)      # corpo
        d.ellipse([cx + 60, cy - 96, cx + 150, cy - 6], fill=255)       # cabeça
        d.polygon([(cx + 66, cy - 96), (cx + 74, cy - 130),
                   (cx + 96, cy - 100)], fill=255)
        d.polygon([(cx + 118, cy - 100), (cx + 132, cy - 130),
                   (cx + 144, cy - 96)], fill=255)
        d.polygon([(cx - 120, cy - 30), (cx - 210, cy - 60),
                   (cx - 200, cy + 20)], fill=255)                      # cauda
        d.line([(cx - 90, cy + 50), (cx - 94, cy + 116)], fill=255, width=11)
        d.line([(cx - 30, cy + 52), (cx - 28, cy + 116)], fill=255, width=11)
        d.line([(cx + 40, cy + 50), (cx + 48, cy + 116)], fill=255, width=11)
        rng = random.Random(50)
        for _ in range(26):                                             # rosetas
            x = rng.randint(cx - 115, cx + 70)
            y = rng.randint(cy - 45, cy + 40)
            d.ellipse([x - 9, y - 6, x + 9, y + 6], fill=0)
            d.ellipse([x - 4, y - 2, x + 4, y + 2], fill=255)
    elif id_animal == "arara":
        d.ellipse([cx - 80, cy - 50, cx + 70, cy + 80], fill=255)       # corpo
        d.ellipse([cx + 44, cy - 110, cx + 112, cy - 40], fill=255)     # cabeça
        d.polygon([(cx + 104, cy - 92), (cx + 160, cy - 60),
                   (cx + 104, cy - 52)], fill=255)                      # bico
        d.arc([cx - 60, cy - 150, cx + 130, cy + 40], 180, 340, fill=255,
              width=34)                                                 # asa
        d.polygon([(cx - 60, cy + 40), (cx - 250, cy + 150),
                   (cx - 170, cy + 168), (cx - 30, cy + 90)], fill=255)  # cauda
        d.line([(cx + 4, cy + 72), (cx + 20, cy + 110)], fill=255, width=8)
    elif id_animal == "lobo":
        d.ellipse([cx - 120, cy - 46, cx + 60, cy + 44], fill=255)      # corpo
        d.ellipse([cx + 40, cy - 92, cx + 116, cy - 12], fill=255)      # cabeça
        d.polygon([(cx + 42, cy - 92), (cx + 48, cy - 136),
                   (cx + 76, cy - 96)], fill=255)
        d.polygon([(cx + 84, cy - 98), (cx + 100, cy - 136),
                   (cx + 114, cy - 90)], fill=255)
        d.polygon([(cx + 108, cy - 60), (cx + 168, cy - 50),
                   (cx + 108, cy - 40)], fill=255)                      # focinho
        d.polygon([(cx - 110, cy - 26), (cx - 220, cy - 46),
                   (cx - 205, cy + 6)], fill=255)                       # cauda
        d.polygon([(cx - 60, cy - 46), (cx - 20, cy - 70),
                   (cx + 20, cy - 44)], fill=255)                       # crina
        for x in (cx - 96, cx - 56, cx + 8, cx + 44):
            d.line([(x, cy + 40), (x + 4, cy + 128)], fill=255, width=9)
    elif id_animal == "golfinho":
        # saltando sobre a onda — nota comemorativa de 500
        dorso = [(cx + 170, cy - 40), (cx + 120, cy - 95), (cx + 40, cy - 120),
                 (cx - 60, cy - 95), (cx - 130, cy - 40), (cx - 165, cy + 15)]
        barriga = [(cx - 140, cy + 55), (cx - 60, cy + 20), (cx + 40, cy + 35),
                   (cx + 120, cy + 10), (cx + 170, cy - 40)]
        d.polygon(dorso + barriga, fill=255)                             # corpo
        d.polygon([(cx - 20, cy - 112), (cx + 5, cy - 172),
                   (cx + 48, cy - 105)], fill=255)                       # nadadeira dorsal
        d.polygon([(cx - 148, cy + 28), (cx - 196, cy + 12),
                   (cx - 172, cy + 62)], fill=255)                       # cauda (lobo sup.)
        d.polygon([(cx - 158, cy + 40), (cx - 188, cy + 86),
                   (cx - 132, cy + 62)], fill=255)                       # cauda (lobo inf.)
        d.polygon([(cx + 30, cy + 28), (cx + 8, cy + 82),
                   (cx + 72, cy + 45)], fill=255)                        # nadadeira peitoral
        d.polygon([(cx + 148, cy - 66), (cx + 196, cy - 44),
                   (cx + 146, cy - 28)], fill=255)                       # rostro (bico)
    return m


def _gravar_detalhes(img, p, m):
    """Traços internos por espécie — a gravação de cédula de verdade:
    linhas de luz CLIPADAS na silhueta, por cima da hachura."""
    cx, cy = 560, 220
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dd = ImageDraw.Draw(layer)
    luz = rnd(p["clara"], 190)
    sombra = rnd(p["tinta"], 170)
    id_animal = p["animal"]
    if id_animal == "tartaruga":
        # placas do casco: coluna central + fileiras laterais
        for dx in (-62, 0, 62):
            dd.ellipse([cx + dx - 34, cy - 52, cx + dx + 34, cy + 52],
                       outline=luz, width=4)
        for dy in (-26, 26):
            dd.line([(cx - 96, cy + dy), (cx + 96, cy + dy)], fill=luz, width=3)
        # veios nas nadadeiras + cabeça
        for x0, y0, x1, y1 in ((cx - 130, cy - 20, cx - 95, cy - 8),
                               (cx - 130, cy + 24, cx - 95, cy + 12),
                               (cx + 30, cy - 108, cx + 42, cy - 70),
                               (cx + 50, cy - 110, cx + 56, cy - 70),
                               (cx + 25, cy + 108, cx + 40, cy + 70)):
            dd.line([(x0, y0), (x1, y1)], fill=luz, width=3)
        dd.ellipse([cx + 140, cy - 14, cx + 156, cy + 2], fill=luz)      # olho
        dd.arc([cx + 118, cy + 2, cx + 180, cy + 22], 20, 150, fill=luz, width=2)
    elif id_animal == "garca":
        for i in range(4):                                               # penas do corpo
            dd.arc([cx - 70 + i * 14, cy - 6, cx + 90 + i * 10, cy + 62],
                   220, 340, fill=luz, width=3)
        dd.line([(cx + 44, cy - 80), (cx + 50, cy - 20)], fill=luz, width=3)
        dd.arc([cx + 30, cy - 130, cx + 70, cy - 100], 180, 320, fill=luz, width=3)
        dd.ellipse([cx + 56, cy - 100, cx + 68, cy - 88], fill=luz)      # olho
        dd.arc([cx - 48, cy + 86, cx - 30, cy + 108], 0, 180, fill=luz, width=3)
        dd.arc([cx + 2, cy + 86, cx + 20, cy + 108], 0, 180, fill=luz, width=3)
    elif id_animal == "papagaio":
        for i in range(4):                                               # escalopas da asa
            dd.arc([cx - 55 + i * 12, cy - 20, cx + 60 + i * 10, cy + 55],
                   250, 60, fill=luz, width=3)
        for i in range(3):                                               # penas da cauda
            dd.line([(cx - 60, cy + 40), (cx - 200 + i * 18, cy + 132)],
                    fill=luz, width=3)
        dd.ellipse([cx + 74, cy - 72, cx + 92, cy - 54], outline=luz, width=3)
        dd.ellipse([cx + 79, cy - 67, cx + 87, cy - 59], fill=luz)
        dd.arc([cx + 108, cy - 78, cx + 160, cy - 44], 190, 340, fill=luz, width=3)
    elif id_animal == "sagui":
        for ang in range(0, 360, 20):                                    # juba radiada
            x0 = cx + 52 + 18 * math.cos(math.radians(ang))
            y0 = cy - 60 + 18 * math.sin(math.radians(ang))
            x1 = cx + 52 + 44 * math.cos(math.radians(ang))
            y1 = cy - 60 + 44 * math.sin(math.radians(ang))
            dd.line([(x0, y0), (x1, y1)], fill=luz, width=3)
        for i in range(5):                                               # anéis da cauda
            t = 0.25 + i * 0.14
            dd.line([(cx - 120 + 190 * t, cy - 20 - 70 * math.sin(math.pi * t)),
                     (cx - 120 + 190 * t, cy + 24 - 70 * math.sin(math.pi * t))],
                    fill=luz, width=3)
        dd.ellipse([cx + 58, cy - 72, cx + 72, cy - 58], fill=luz)
        dd.line([(cx + 78, cy - 40), (cx + 92, cy - 36)], fill=luz, width=3)
    elif id_animal == "onca":
        rng = random.Random(77)
        for _ in range(30):                                              # pintas miúdas
            x = rng.randint(cx - 115, cx + 70)
            y = rng.randint(cy - 45, cy + 40)
            dd.ellipse([x - 2, y - 2, x + 2, y + 2], fill=luz)
        dd.ellipse([cx + 96, cy - 66, cx + 112, cy - 50], fill=luz)      # olho
        dd.arc([cx + 96, cy - 40, cx + 130, cy - 16], 0, 140, fill=luz, width=3)
        for _ in range(6):                                               # bigodes
            x0 = rng.randint(cx + 128, cx + 142)
            dd.line([(x0, cy - 30), (x0 + 22, cy - 38 + rng.randint(-8, 8))],
                    fill=luz, width=2)
        dd.ellipse([cx + 84, cy - 118, cx + 96, cy - 106], outline=luz, width=2)
    elif id_animal == "arara":
        for i in range(4):                                               # face emplumada
            dd.arc([cx + 60 + i * 10, cy - 96, cx + 116 + i * 8, cy - 30],
                   300, 120, fill=luz, width=3)
        dd.ellipse([cx + 62, cy - 82, cx + 78, cy - 66], outline=luz, width=3)
        dd.ellipse([cx + 67, cy - 77, cx + 74, cy - 70], fill=luz)
        for i in range(4):                                               # penas da asa
            dd.arc([cx - 40 + i * 16, cy - 130 + i * 6, cx + 110, cy + 10],
                   200, 330, fill=luz, width=3)
        dd.line([(cx - 50, cy + 60), (cx - 210, cy + 148)], fill=luz, width=3)
        dd.line([(cx - 30, cy + 74), (cx - 180, cy + 160)], fill=luz, width=3)
    elif id_animal == "lobo":
        for i in range(7):                                               # crina pentheada
            dd.line([(cx - 40 + i * 8, cy - 44), (cx - 46 + i * 8, cy - 70)],
                    fill=luz, width=3)
        dd.polygon([(cx + 56, cy - 100), (cx + 66, cy - 122),
                    (cx + 76, cy - 98)], outline=luz, width=2)           # orelha int.
        dd.ellipse([cx + 92, cy - 58, cx + 106, cy - 44], fill=luz)      # olho
        dd.ellipse([cx + 150, cy - 56, cx + 162, cy - 46], fill=luz)     # nariz
        dd.arc([cx + 120, cy - 40, cx + 160, cy - 20], 20, 120, fill=luz, width=3)
        for i in range(4):                                               # pêlos da cauda
            dd.line([(cx - 130 - i * 12, cy - 30 - i * 4),
                     (cx - 168 - i * 14, cy - 40 - i * 2)], fill=luz, width=3)
    elif id_animal == "golfinho":
        dd.arc([cx + 140, cy - 66, cx + 196, cy - 30], 220, 40, fill=luz, width=3)
        dd.ellipse([cx + 118, cy - 64, cx + 132, cy - 50], fill=luz)     # olho
        dd.arc([cx + 40, cy - 60, cx + 150, cy + 20], 250, 30, fill=luz, width=3)
        dd.arc([cx - 90, cy - 40, cx + 60, cy + 30], 250, 30, fill=luz, width=3)
        dd.line([(cx + 20, cy + 40), (cx + 10, cy + 74)], fill=luz, width=3)
        dd.line([(cx + 34, cy + 42), (cx + 30, cy + 72)], fill=luz, width=3)
        dd.arc([cx - 30, cy - 150, cx + 30, cy - 110], 60, 170, fill=luz, width=3)
    clip = m.filter(ImageFilter.MaxFilter(3))
    layer = Image.composite(layer, Image.new("RGBA", (W, H), (0, 0, 0, 0)), clip)
    img.alpha_composite(layer)
    return m


def animal(img, p):
    cx, cy = 560, 220
    m = _mascara_animal(p["animal"], cx, cy)
    oval = Image.new("L", (W, H), 0)
    do = ImageDraw.Draw(oval)
    do.ellipse([cx - 190, cy - 160, cx + 210, cy + 150], fill=255)
    m = Image.composite(m, Image.new("L", (W, H), 0),
                        oval.point(lambda a: 255 if a else 0))
    mold_oval = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dmo = ImageDraw.Draw(mold_oval)
    dmo.ellipse([cx - 190, cy - 160, cx + 210, cy + 150],
                outline=rnd(p["tinta"], 130), width=4)
    dmo.ellipse([cx - 178, cy - 148, cx + 198, cy + 138],
                outline=rnd(p["tinta"], 60), width=2)
    img.alpha_composite(mold_oval)
    _gravar(img, m, p["tinta"], p["clara"])
    _gravar_detalhes(img, p, m)


# ---------------------------------------------------------------------------
# VERSO da cédula — meandro de segurança, valor por extenso, medalhão
# ---------------------------------------------------------------------------
POR_EXTENSO = {
    2: "DOIS REAIS", 5: "CINCO REAIS", 10: "DEZ REAIS", 20: "VINTE REAIS",
    50: "CINQUENTA REAIS", 100: "CEM REAIS", 200: "DUZENTOS REAIS",
    500: "QUINHENTOS REAIS",
}


def gerar_verso(p):
    """Segunda face: guilloché na largura toda, meandro vertical (faixa de
    segurança), R$ translúcido, valor por extenso e o animal no medalhão
    à direita — como o verso das cédulas de verdade."""
    img = papel_base(p)
    d = ImageDraw.Draw(img)
    # guilloché de ondas na LARGURA TODA (o verso é todo tramado)
    for base in range(44, H - 26, 20):
        pts = [(x, base + 6 * math.sin(x / 21.0 + base / 7.0))
               for x in range(22, W - 22, 4)]
        d.line(pts, fill=rnd(p["tinta"], 32), width=2)
    # meandro (zigue-zague entre dois trilhos verticais) no centro
    mx = W // 2
    ys = list(range(44, H - 32, 22))
    for i in range(len(ys) - 1):
        xA = mx - 30 if i % 2 == 0 else mx + 30
        xB = mx + 30 if i % 2 == 0 else mx - 30
        d.line([(xA, ys[i]), (xB, ys[i + 1])], fill=rnd(p["tinta"], 100),
               width=3)
    d.line([(mx - 34, 36), (mx - 34, H - 36)], fill=rnd(p["tinta"], 90), width=2)
    d.line([(mx + 34, 36), (mx + 34, H - 36)], fill=rnd(p["tinta"], 90), width=2)
    # R$ translúcido grande à esquerda (marca d'água do verso)
    frd = fnt(FONTE_NUM, 150)
    d.text((140, H // 2 - 105), "R$", font=frd, fill=rnd(p["clara"], 80))
    # medalhão do animal à direita
    cx, cy = W - 280, H // 2
    dm = ImageDraw.Draw(img)
    dm.ellipse([cx - 158, cy - 158, cx + 158, cy + 158],
               outline=rnd(p["tinta"], 130), width=4)
    dm.ellipse([cx - 146, cy - 146, cx + 146, cy + 146],
               outline=rnd(p["tinta"], 60), width=2)
    m = _mascara_animal(p["animal"], cx, cy)
    circulo = Image.new("L", (W, H), 0)
    dcir = ImageDraw.Draw(circulo)
    dcir.ellipse([cx - 144, cy - 144, cx + 144, cy + 144], fill=255)
    m = Image.composite(m, Image.new("L", (W, H), 0), circulo)
    _gravar(img, m, p["tinta"], p["clara"])
    _gravar_detalhes(img, p, m)
    # título, valor pequeno no canto e serial
    fr = fnt(FONTE_TXT, 24)
    titulo = "REPÚBLICA DO SNC"
    larg = d.textlength(titulo, font=fr)
    d.text(((W - larg) / 2, 30), titulo, font=fr, fill=rnd(p["numero"], 220))
    fp = fnt(FONTE_NUM, 46)
    d.text((48, 34), str(p["valor"]), font=fp, fill=rnd(p["numero"], 220))
    fser = fnt(FONTE_TXT, 20)
    d.text((W - 190, 34), f"SNC {430000 + p["valor"] * 11}B",
           font=fser, fill=(150, 40, 40, 200))
    # valor por extenso na base
    fex = fnt(FONTE_NUM, 32)
    ext = POR_EXTENSO.get(p["valor"], f"{p['valor']} REAIS")
    largx = d.textlength(ext, font=fex)
    d.text(((W - largx) / 2, H - 66), ext, font=fex,
           fill=rnd(p["numero"], 230))
    moldura(img, p)
    acabamento(img, p)
    return img


def acabamento(img, p):
    """Sheen diagonal sutil + marcas de dobra discretas."""
    brilho = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    db = ImageDraw.Draw(brilho)
    db.polygon([(0, H), (W * 0.25, 0), (W * 0.45, 0), (W * 0.2, H)],
               fill=(255, 255, 255, 16))
    db.polygon([(W * 0.6, H), (W * 0.85, 0), (W * 0.95, 0), (W * 0.7, H)],
               fill=(255, 255, 255, 10))
    img.alpha_composite(brilho.filter(ImageFilter.GaussianBlur(6)))
    dobra = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dd = ImageDraw.Draw(dobra)
    dd.line([(0, H * 0.52), (W, H * 0.50)], fill=(0, 0, 0, 22), width=3)
    dd.line([(0, H * 0.52 + 2), (W, H * 0.50 + 2)], fill=(255, 255, 255, 26),
            width=2)
    img.alpha_composite(dobra.filter(ImageFilter.GaussianBlur(2)))
    # recorte com cantos arredondados
    recorte = Image.new("L", (W, H), 0)
    dr = ImageDraw.Draw(recorte)
    dr.rounded_rectangle([0, 0, W - 1, H - 1], radius=30, fill=255)
    img.putalpha(recorte)


def gerar_nota(p):
    img = papel_base(p)
    guilloche(img, p)
    faixa_plantas(img, p)
    moldura(img, p)
    animal(img, p)
    textos(img, p)
    acabamento(img, p)
    return img


# ---------------------------------------------------------------------------
# moeda de 1
# ---------------------------------------------------------------------------
def gerar_moeda():
    S = MOEDA
    c, y0 = S // 2, S // 2
    r = 236
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # corpo do aço com degradê vertical (mais claro em cima).
    # v1.2.71 — O DISCO OPACO (bug da playtest: "transparente no meio, cinza
    # onde devia ser transparente"): o degradê era desenhado DIRETO em img e
    # depois um canvas 'aco' VAZIO era colado com máscara do disco — a pasta
    # apagava o corpo e a moeda nascia com o miolo semitransparente. Agora o
    # degradê mora no 'aco' (que é colado DE VERDADE) e TODA a pintura
    # seguinte (serrilha, relevo, brilho) é clipada pelo disco: nada escapa
    # pra fora, o fundo fica 100% transparente.
    aco = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    da = ImageDraw.Draw(aco)
    for y in range(y0 - r, y0 + r):
        t = (y - (y0 - r)) / (2 * r)
        cor = tuple(int(MOEDA_1["aco_claro"][i] + (MOEDA_1["aco_escuro"][i]
                   - MOEDA_1["aco_claro"][i]) * t) for i in range(3))
        da.line([(c - r, y), (c + r, y)], fill=cor + (255,))
    disco = Image.new("L", (S, S), 0)
    dd = ImageDraw.Draw(disco)
    dd.ellipse([c - r, y0 - r, c + r, y0 + r], fill=255)
    img.paste(aco, (0, 0), disco)
    d = ImageDraw.Draw(img)
    # serrilha da borda (radial)
    for ang in range(0, 360, 3):
        a = math.radians(ang)
        x1, y1 = c + (r - 6) * math.cos(a), y0 + (r - 6) * math.sin(a)
        x2, y2 = c + r * math.cos(a), y0 + r * math.sin(a)
        cor = (255, 255, 255, 90) if ang % 6 == 0 else (60, 64, 72, 110)
        d.line([(x1, y1), (x2, y2)], fill=cor, width=4)
    d.ellipse([c - r, y0 - r, c + r, y0 + r], outline=(70, 74, 82, 200), width=5)
    # anel interno relevado
    d.ellipse([c - 196, y0 - 196, c + 196, y0 + 196],
              outline=(255, 255, 255, 110), width=4)
    d.ellipse([c - 190, y0 - 190, c + 190, y0 + 190],
              outline=(90, 94, 102, 140), width=3)
    # centro levemente afundado
    centro = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    dc = ImageDraw.Draw(centro)
    dc.ellipse([c - 168, y0 - 168, c + 168, y0 + 168], fill=(140, 144, 152, 46))
    img.alpha_composite(centro.filter(ImageFilter.GaussianBlur(8)))
    # valor em relevo
    fn = fnt(FONTE_NUM, 230)
    txt = "1"
    larg = d.textlength(txt, font=fn)
    pos = (c - larg / 2, y0 - 128)
    d.text((pos[0] + 5, pos[1] + 6), txt, font=fn, fill=(60, 64, 72, 130))
    d.text((pos[0] - 4, pos[1] - 4), txt, font=fn, fill=(255, 255, 255, 150))
    d.text(pos, txt, font=fn, fill=MOEDA_1["tinta"] + (255,))
    fs = fnt(FONTE_TXT, 44)
    for s, dy in (("SNC", -206), ("ADVENTURES", 168)):
        larg = d.textlength(s, font=fs)
        d.text((c - larg / 2, y0 + dy), s, font=fs,
               fill=MOEDA_1["tinta"] + (210,))
    # estrelas do anel
    for ang in range(0, 360, 45):
        a = math.radians(ang)
        x, y = c + 152 * math.cos(a), y0 + 152 * math.sin(a)
        d.ellipse([x - 5, y - 5, x + 5, y + 5], fill=(100, 104, 112, 170))
    # sheen diagonal (dentro do disco: o brilho NUNCA vaza pra fora)
    brilho = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    db = ImageDraw.Draw(brilho)
    db.polygon([(c - r, y0 + r), (c, y0 - r - 20), (c + 90, y0 - r - 20),
                (c - r + 90, y0 + r)], fill=(255, 255, 255, 42))
    img.alpha_composite(brilho.filter(ImageFilter.GaussianBlur(10)))
    # CLIP final pelo disco (mantém a serrilha, corta o brilho vazio):
    # fora do raio da moeda, alpha 0 — é isso que impede o "quadrado cinza"
    img.putalpha(ImageChops.multiply(img.getchannel("A"), disco))
    return img


# ---------------------------------------------------------------------------
# JSONs de modelo/definição (padrão item/generated)
# ---------------------------------------------------------------------------
def para_sprite(img):
    """Letterbox pra SPRITE DE ITEM: o minecraft:item/generated estica a
    textura INTEIRA num quadrado — uma cédula 1024×432 direto viraria um
    trapo quadrado. Colando a nota no centro de um canvas 1024×1024 com
    alpha, o sprite vira quadrado e a nota continua PROPORCIONAL (faixa
    horizontal com transparente em cima/embaixo)."""
    s = Image.new("RGBA", (img.width, img.width), (0, 0, 0, 0))
    s.paste(img, (0, (img.width - img.height) // 2))
    return s


def gerar_jsons(ids):
    for id_item in ids:
        modelo = {"parent": "minecraft:item/generated",
                  "textures": {"layer0": f"intoxicantes:item/{id_item}"}}
        with open(os.path.join(MODELOS, f"{id_item}.json"), "w",
                  encoding="utf-8") as f:
            json.dump(modelo, f, indent=2)
        defin = {"model": {"type": "minecraft:model",
                           "model": f"intoxicantes:item/{id_item}"}}
        with open(os.path.join(DEFS, f"{id_item}.json"), "w",
                  encoding="utf-8") as f:
            json.dump(defin, f, indent=2)


def main():
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(MODELOS, exist_ok=True)
    os.makedirs(DEFS, exist_ok=True)

    geradas = []
    # moeda de 1
    moeda = gerar_moeda()
    alvo = os.path.join(TEX, "moeda_1.png")
    moeda.save(alvo)
    geradas.append(("moeda_1", moeda.size))
    # cédula de 2 (id "real"): a arte ANTIGA era manual — backup único em
    # artes-manuais/, depois passa a ser gerada no padrão da família.
    real_path = os.path.join(TEX, "real.png")
    backup = os.path.join(RAIZ, "artes-manuais", "real.png")
    if os.path.exists(real_path) and not os.path.exists(backup):
        os.makedirs(os.path.dirname(backup), exist_ok=True)
        with open(real_path, "rb") as a, open(backup, "wb") as b:
            b.write(a.read())
        print("  BACKUP real.png (arte manual) -> artes-manuais/")
    for id_item, p in NOTAS.items():
        nota = para_sprite(gerar_nota(p))
        alvo = os.path.join(TEX, f"{id_item}.png")
        nota.save(alvo)
        geradas.append((id_item, nota.size))
        # v1.2.61: o VERSO de cada cédula (textura separada, usada no
        # preview 3D; Minecraft vanilla mostra só a frente) — mesmo
        # letterbox de sprite pra manter o pack uniforme
        verso = para_sprite(gerar_verso(p))
        alvo_v = os.path.join(TEX, f"{id_item}_verso.png")
        verso.save(alvo_v)
        geradas.append((f"{id_item}_verso", verso.size))

    gerar_jsons(["moeda_1"] + list(NOTAS.keys()))

    # espelho no pack do usuário (sprites 2D — regra do pack; o pack
    # sobrescreve o jar de propósito: mesma arte em ambos)
    if os.path.isdir(os.path.dirname(PACK)):
        os.makedirs(PACK, exist_ok=True)
        for id_item, _ in geradas:
            src = os.path.join(TEX, f"{id_item}.png")
            dst = os.path.join(PACK, f"{id_item}.png")
            if os.path.abspath(src) != os.path.abspath(dst):
                with open(src, "rb") as a, open(dst, "wb") as b:
                    b.write(a.read())

    print("=== DINHEIRO R$ gerado (fonte: tools/gen_dinheiro.py) ===")
    for id_item, (w_, h_) in geradas:
        print(f"  {id_item}.png  {w_}x{h_}")


if __name__ == "__main__":
    main()
