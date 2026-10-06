#!/usr/bin/env python3
"""v1.2.58 — Gera as texturas novas em ALTA RESOLUÇÃO (1254×1254), o mesmo
padrão do pack HD que o mod já embute (asfalto, lsd, lúpulo, hidrante...).
Tudo pintado direto em alta resolução com sombreamento (nada de upscale).

v1.2.64 — os sprites 2D das 8 bebidas 3D (cerveja, vinho, cachaça, hidromel,
rum, suco_detox, agua_de_coco, cha_lupulo) foram APOSENTADOS: a arte oficial
delas é o atlas do gen_garrafas.py. A arte abaixo permanece como referência
de pintura, mas salvar() se recusa a recriar o PNG órfão em textures/item/."""
try:
    from tools.gen_catalogo import ITEM_IDS as CATALOGO_3D
except ModuleNotFoundError:
    from gen_catalogo import ITEM_IDS as CATALOGO_3D
from PIL import Image, ImageDraw, ImageFilter
import math
import os

RAIZ = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                    "assets", "intoxicantes", "textures")
T = 1254  # resolução padrão do pack HD do mod

def novo():
    return Image.new("RGBA", (T, T), (0, 0, 0, 0))

def elipse(d, cx, cy, rx, ry, fill, outline=None, w=6):
    d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill,
              outline=outline, width=w if outline else 0)

def poligono(d, pts, fill, outline=None, w=6):
    d.polygon(pts, fill=fill, outline=outline, width=w if outline else 0)

BEBIDAS_3D = {"cerveja", "vinho", "cachaca", "hidromel", "rum", "suco_detox",
              "agua_de_coco", "cha_lupulo",
              # v1.2.65: coco e pão de cevada também viraram 3D
              "coco", "pao_cevada"}


def salvar(img, pasta, nome):
    if pasta == "item" and (nome in BEBIDAS_3D or nome in CATALOGO_3D):
        print(f"  SKIP (bebida 3D — sprite 2D removido na v1.2.64): item/{nome}.png")
        return
    if pasta == "block" and nome == "coco":
        print("  SKIP (coco pendurado compartilha o atlas nativo de gen_garrafas.py)")
        return
    img.save(os.path.join(RAIZ, pasta, nome + ".png"))
    print(f"  {pasta}/{nome}.png ({T}x{T})")

# luz vindo do canto superior-esquerdo: brilho no topo-esq, sombra no baixo-dir
def brilho_sombra(d, cx, cy, rx, ry, tom, k=0.22):
    tom_brilho = tuple(min(255, int(c + (255 - c) * k)) for c in tom[:3]) + (255,)
    tom_sombra = tuple(int(c * (1 - k)) for c in tom[:3]) + (255,)
    d.ellipse([cx - rx * 0.72, cy - ry * 0.72, cx + rx * 0.25, cy + ry * 0.1],
              fill=tom_brilho)
    d.ellipse([cx + rx * 0.1, cy + ry * 0.2, cx + rx * 0.95, cy + ry * 0.98],
              fill=tom_sombra)

# gradiente vertical simples (faixas interpoladas c0 -> c1, SEM frestas:
# cada passo pinta um retângulo cheio, linha 1px deixaria buracos de 8px)
def faixa_gradiente(d, x0, y0, x1, y1, c0, c1, passo=8):
    for y in range(int(y0), int(y1), passo):
        t = (y - y0) / max(1.0, (y1 - y0))
        cor = tuple(int(c0[i] + (c1[i] - c0[i]) * t) for i in range(3)) + (c0[3],)
        d.rectangle([x0, y, x1, min(y + passo, y1)], fill=cor)

# CAMADA SEMI-TRANSPARENTE: o ImageDraw de fills com alpha<255 SUBSTITUI o
# pixel (inclusive o alpha) — fura a textura. Tudo que é translúcido tem que
# nascer numa camada própria e ir pro img por alpha_composite.
def camada(img, pintar):
    c = Image.new("RGBA", img.size, (0, 0, 0, 0))
    pintar(ImageDraw.Draw(c))
    img.alpha_composite(c)
    return img

# ============================================================ SUCO DETOX
# v1.2.62: garrafa de vidro de verdade — silhueta (corpo + ombros + gargalo),
# líquido visível com polpa, rótulo, tampa dourada serrilhada e brilho de vidro.
# v1.2.64: APOSENTADO como sprite (bebida 3D); salvar() pula a escrita no disco.
img = novo(); d = ImageDraw.Draw(img)
VIDRO = (44, 112, 52, 255)
SUCO = (62, 156, 74, 255)
SUCO_CLARO = (126, 200, 118, 255)
POLPA = (34, 96, 44, 255)
DOURADO = (176, 128, 48, 255)
# corpo + gargalo (mesma tinta de vidro; a cor do líquido entra por dentro)
d.rounded_rectangle([320, 300, 934, 1090], 110, fill=VIDRO)
d.rounded_rectangle([516, 96, 738, 340], 46, fill=VIDRO)
# líquido dentro do corpo (inset) com faixa clara no topo
_d = ImageDraw.Draw(img)
_d.rounded_rectangle([360, 344, 894, 1050], 80, fill=SUCO)
_d.rounded_rectangle([360, 344, 894, 480], 80, fill=SUCO_CLARO)
# polpa em suspensão
for (px, py, pr) in [(470, 620, 16), (600, 560, 12), (760, 640, 18),
                     (520, 940, 14), (700, 900, 12), (820, 540, 10)]:
    _d.ellipse([px - pr, py - pr, px + pr, py + pr], fill=POLPA)
# bolhas subindo (camada: alpha mesclado, não furado)
def _bolhas(dd):
    for (bx, by, br) in [(450, 520, 20), (560, 470, 14), (800, 500, 16),
                         (640, 960, 18), (480, 800, 12)]:
        dd.ellipse([bx - br, by - br, bx + br, by + br],
                   fill=(198, 238, 202, 220))
camada(img, _bolhas)
# rótulo creme embrulhando a garrafa, com folha + traços (camada p/ contorno alpha)
def _rotulo(dd):
    dd.rounded_rectangle([320, 690, 934, 880], 30, fill=(244, 238, 214, 255),
                         outline=(58, 40, 20, 180), width=8)
    dd.polygon([(560, 730), (640, 770), (560, 830), (480, 770)],
               fill=(52, 128, 60, 255), outline=(30, 80, 38, 255))
    dd.line([560, 770, 560, 850], fill=(30, 80, 38, 255), width=10)
    dd.rounded_rectangle([700, 750, 870, 768], 9, fill=(52, 128, 60, 255))
    dd.rounded_rectangle([700, 790, 870, 808], 9, fill=(52, 128, 60, 160))
camada(img, _rotulo)
# tampa dourada serrilhada
_d.rounded_rectangle([488, 40, 766, 152], 36, fill=DOURADO,
                     outline=(74, 48, 22, 255), width=10)
for rx in range(520, 740, 30):
    _d.line([rx, 58, rx, 134], fill=(140, 100, 38, 255), width=8)
# base de vidro mais grossa
_d.rounded_rectangle([360, 1000, 894, 1050], 30, fill=(36, 92, 44, 255))
# brilhos de vidro (camada translúcida sobre o vidro)
def _brilhos_vidro(dd):
    dd.line([400, 360, 400, 980], fill=(240, 252, 240, 110), width=30)
    dd.line([860, 380, 860, 700], fill=(240, 252, 240, 70), width=18)
    dd.line([560, 130, 560, 310], fill=(240, 252, 240, 90), width=16)
camada(img, _brilhos_vidro)
salvar(img, "item", "suco_detox")

# ============================================================ ÁGUA DE COCO
# v1.2.64: APOSENTADO como sprite (bebida 3D); salvar() pula a escrita no disco.
img = novo(); d = ImageDraw.Draw(img)
CASCA = (58, 122, 48, 255)
CASCA_ESCURA = (36, 86, 32, 255)
AGUA = (240, 246, 228, 255)
AGUA_CLARA = (252, 253, 248, 255)
# coco verde deitado (corte pra cima) — v1.2.62: volume esférico com luz,
# contraluz no quadrante superior-esquerdo, canudo e casca facetada
_d = ImageDraw.Draw(img)
# folhinhas de coqueiro atrás (com nervura)
for (x1, y1, x2, y2, x3, y3) in [(180, 260, 420, 200, 360, 340),
                                 (1074, 260, 834, 200, 894, 340)]:
    poligono(d, [(x1, y1), (x2, y2), (x3, y3)], (74, 148, 62, 255),
             (44, 104, 40, 255), 10)
    d.line([(x1 + x2) / 2, (y1 + y2) / 2, x3, y3],
           fill=(44, 104, 40, 200), width=8)
elipse(d, 627, 770, 400, 330, CASCA, CASCA_ESCURA, 18)
brilho_sombra(d, 627, 790, 380, 305, CASCA, k=0.30)
# contraluz no topo-esquerdo (luz da cena)
d.arc([247, 460, 1007, 1080], 160, 250, fill=(150, 210, 130, 255), width=26)
# facetas verticais sutis da casca (camada translúcida)
def _facetas(dd):
    for fx, larg in [(460, 30), (627, 26), (790, 30)]:
        dd.arc([fx - larg * 6, 500, fx + larg * 6, 1050], 70, 110,
               fill=(36, 86, 32, 90), width=larg)
camada(img, _facetas)
# corte superior: aro escuro, polpa branca, água dentro
elipse(d, 627, 430, 330, 150, (44, 100, 40, 255), CASCA_ESCURA, 14)
elipse(d, 627, 430, 288, 122, (250, 248, 238, 255))   # polpa
elipse(d, 627, 434, 252, 100, AGUA)
elipse(d, 560, 404, 104, 36, AGUA_CLARA)
# água transbordando pelo lado
poligono(d, [(590, 520), (664, 520), (700, 700), (554, 700)], AGUA)
# canudo vermelho inclinado saindo do corte
poligono(d, [(690, 200), (736, 178), (836, 352), (790, 376)],
         (206, 60, 52, 255), (120, 30, 26, 255), 8)
for sy in (240, 290, 340):
    d.line([(690 + sy - 200) * 0.92 + 60, sy, (690 + sy - 200) * 0.92 + 96, sy + 14],
           fill=(244, 232, 226, 255), width=12)
# olhos do coco na base (com brilho)
for (ex, ey) in [(500, 950), (627, 985), (754, 950)]:
    elipse(d, ex, ey, 34, 28, (26, 62, 24, 255))
    d.ellipse([ex - 16, ey - 14, ex + 2, ey + 2], fill=(86, 150, 78, 255))
salvar(img, "item", "agua_de_coco")

# ============================================================ COCO (fruto)
img = novo(); d = ImageDraw.Draw(img)
COCO = (116, 82, 46, 255)
COCO_CLARO = (168, 130, 84, 255)
COCO_ESCURO = (74, 50, 26, 255)
# v1.2.62: fibra DESENHADA SOBRE a esfera (antes ficava só nos cantos,
# fora do coco), sombreamento esférico e contraluz no topo-esquerdo
elipse(d, 627, 640, 400, 400, COCO, COCO_ESCURO, 18)
# fios de fibra mascarados pelo disco do coco
fibra = Image.new("RGBA", (T, T), (0, 0, 0, 0))
_df = ImageDraw.Draw(fibra)
for i in range(-T, T * 2, 88):
    _df.line([i, -40, i + T + 80, T + 40], fill=(210, 170, 110, 100), width=16)
for i in range(-T + 44, T * 2, 88):
    _df.line([i, -40, i + T + 80, T + 40], fill=(70, 46, 24, 110), width=14)
_masc = Image.new("L", (T, T), 0)
ImageDraw.Draw(_masc).ellipse([227, 240, 1027, 1040], fill=255)
img.paste(fibra, (0, 0), _masc)
_d = ImageDraw.Draw(img)
brilho_sombra(_d, 627, 640, 390, 390, COCO, k=0.26)
# contraluz no topo-esquerdo
_d.arc([247, 260, 1007, 1020], 160, 250, fill=(196, 158, 104, 255), width=30)
# os 3 olhos (triângulo, com brilho)
for (ex, ey) in [(540, 700), (714, 700), (627, 830)]:
    elipse(_d, ex, ey, 42, 40, (34, 22, 10, 255))
    _d.ellipse([ex - 14, ey - 14, ex + 4, ey + 2], fill=(96, 68, 38, 255))
# sombra de contato na base
_d.ellipse([400, 1010, 854, 1080], fill=(52, 34, 16, 255))
salvar(img, "item", "coco")

# ============================================================ CHÁ DE LÚPULO
img = novo(); d = ImageDraw.Draw(img)
CHA = (198, 138, 62, 255)
CHA_CLARO = (232, 180, 104, 255)
VIDRO = (226, 240, 244, 235)
# caneca reta de vidro
d.rounded_rectangle([320, 340, 934, 1080], 70, fill=VIDRO, outline=(60, 46, 30, 255), width=14)
# chá dentro (com espaço do topo)
d.rounded_rectangle([356, 430, 898, 1046], 50, fill=CHA)
d.rounded_rectangle([356, 430, 898, 560], 50, fill=CHA_CLARO)
# espuma/borda
d.rounded_rectangle([320, 300, 934, 380], 40, fill=(244, 240, 228, 255),
                    outline=(60, 46, 30, 255), width=12)
# alça
d.arc([900, 480, 1150, 880], 300, 60, fill=(60, 46, 30, 255), width=36)
d.arc([908, 500, 1130, 860], 300, 60, fill=VIDRO, width=18)
# flor de lúpulo caindo no chá (cone verde-claro com pétalas)
elipse(d, 560, 640, 96, 116, (150, 190, 96, 255), (94, 132, 56, 255), 10)
for i, ang in enumerate(range(-60, 61, 30)):
    a = math.radians(ang)
    px = 560 + 60 * math.sin(a)
    py = 640 + 80 * math.cos(a) - 40
    elipse(d, px, py, 34, 44, (170, 210, 112, 255), (94, 132, 56, 255), 8)
# vapor subindo
for (vx, vy, vr) in [(560, 250, 60), (700, 180, 76), (640, 110, 52)]:
    d.ellipse([vx - vr, vy - vr * 1.6, vx + vr, vy + vr * 1.6],
              fill=(250, 250, 250, 70))
# brilho do vidro
d.line([390, 420, 390, 1000], fill=(250, 254, 255, 140), width=36)
salvar(img, "item", "cha_lupulo")

# ============================================================ PÃO DE CEVADA
img = novo(); d = ImageDraw.Draw(img)
PAO = (188, 132, 66, 255)
PAO_CLARO = (222, 172, 100, 255)
PAO_ESCURO = (128, 86, 40, 255)
# pão redondo achatado
elipse(d, 627, 760, 470, 320, PAO, PAO_ESCURO, 18)
elipse(d, 627, 640, 440, 260, PAO_CLARO)
elipse(d, 560, 560, 220, 120, (240, 202, 134, 255))
# cortes diagonais da crosta
for x0 in (430, 620, 810):
    d.line([x0, 560, x0 + 90, 780], fill=PAO_ESCURO, width=24)
# grãos de cevada salpicados
for (gx, gy) in [(420, 640), (560, 700), (700, 620), (820, 700), (520, 860),
                 (760, 880), (640, 940)]:
    poligono(d, [(gx - 36, gy), (gx, gy - 22), (gx + 36, gy), (gx, gy + 22)],
             (150, 106, 48, 255), (100, 68, 30, 255), 8)
# sombra de contato
d.ellipse([300, 1010, 954, 1090], fill=(96, 64, 28, 255))
salvar(img, "item", "pao_cevada")

# ============================================================ BLOCOS DO COQUEIRO
# tronco lateral — v1.2.62: gradiente base + fibras em pares (sombra/luz)
# e VOLUME CILÍNDRICO: faixa de luz à esquerda, sombra à direita
img = novo(); d = ImageDraw.Draw(img)
faixa_gradiente(d, 0, 0, T, T, (172, 134, 80, 255), (128, 94, 52, 255))
for y in range(0, T, 40):    # fibra escura
    pts = [(y + 16 * math.sin(yy / 150.0 + y), yy)
           for yy in range(-30, T + 40, 60)]
    d.line(pts, fill=(112, 82, 46, 255), width=14)
for y in range(14, T, 40):   # fibra clara (contraleve da vizinha)
    pts = [(y + 16 * math.sin(yy / 150.0 + y) + 12, yy)
           for yy in range(-30, T + 40, 60)]
    d.line(pts, fill=(190, 150, 98, 255), width=8)
# volume cilíndrico (camada: alpha mesclado, não furado)
def _volume_tronco(dd):
    dd.rectangle([0, 0, 90, T], fill=(255, 244, 214, 36))
    dd.rectangle([T - 110, 0, T, T], fill=(30, 20, 10, 54))
camada(img, _volume_tronco)
# anéis horizontais das marcas dos galhos (com sombra embaixo)
for ay in (300, 640, 980):
    d.rectangle([0, ay - 26, T, ay + 26], fill=(96, 70, 40, 255))
    d.line([0, ay - 26, T, ay - 26], fill=(198, 160, 108, 255), width=10)
    d.line([0, ay + 26, T, ay + 26], fill=(70, 50, 28, 255), width=8)
salvar(img, "block", "coqueiro_tronco")

# tronco topo: anéis concêntricos — v1.2.62: com sombreamento assimétrico
# (crescente claro no topo-esquerda, escuro no baixo-direita)
img = novo(); d = ImageDraw.Draw(img)
d.rectangle([0, 0, T, T], fill=(104, 76, 42, 255))
for raio, cor in [(600, (150, 112, 64, 255)), (460, (178, 138, 86, 255)),
                  (320, (150, 112, 64, 255)), (180, (178, 138, 86, 255)),
                  (80, (122, 90, 50, 255))]:
    elipse(d, T // 2, T // 2, raio, raio, cor)
# fibras radiais
for a in range(0, 360, 15):
    rad = math.radians(a)
    d.line([T // 2 + 90 * math.cos(rad), T // 2 + 90 * math.sin(rad),
            T // 2 + 600 * math.cos(rad), T // 2 + 600 * math.sin(rad)],
           fill=(122, 90, 50, 255), width=6)
# sombreamento assimétrico por cima dos anéis (camada)
def _sombra_topo(dd):
    dd.ellipse([T // 2 - 730, T // 2 - 730, T // 2 + 390, T // 2 + 390],
               fill=(210, 174, 120, 36))
    dd.ellipse([T // 2 - 380, T // 2 - 380, T // 2 + 740, T // 2 + 740],
               fill=(20, 14, 8, 46))
camada(img, _sombra_topo)
salvar(img, "block", "coqueiro_tronco_topo")

# folhas — v1.2.62: gradiente base, folíolos com onda orgânica, nervura
# com clarete e mais furos (profundidade em vez de flat)
img = novo(); d = ImageDraw.Draw(img)
faixa_gradiente(d, 0, 0, T, T, (70, 140, 60, 255), (38, 92, 40, 255))
for i in range(-T * 2, T * 2, 116):
    desloc = int(10 * math.sin(i / 90.0))
    d.line([i, 0, i + T, T], fill=(104, 178, 86, 255), width=30)
    d.line([i + 16 + desloc, 0, i + 16 + desloc + T, T],
           fill=(30, 82, 34, 255), width=26)
# nervura central (sombra + clarete) em camada (alpha mesclado)
def _nervura(dd):
    dd.line([-40, -40, T + 40, T + 40], fill=(24, 64, 26, 220), width=26)
    dd.line([-40, -28, T + 40, T + 52], fill=(150, 210, 120, 90), width=8)
camada(img, _nervura)
# furos de transparência espalhados (estilo folha vanilla)
furos = [(180, 240), (760, 160), (420, 700), (1020, 560), (240, 980),
         (880, 1020), (560, 420), (100, 620)]
for (fx, fy) in furos:
    elipse(d, fx, fy, 46, 34, (0, 0, 0, 0))
salvar(img, "block", "coqueiro_folhas")

# coco bloco: casca marrom fibrosa com os 3 olhos
img = novo(); d = ImageDraw.Draw(img)
d.rectangle([0, 0, T, T], fill=COCO)
# fibra em linhas onduladas
for y in range(0, T, 80):
    pts = [(x, y + 22 * math.sin(x / 120.0 + y)) for x in range(-20, T + 40, 40)]
    d.line(pts, fill=(94, 66, 36, 255), width=20)
for y in range(40, T, 80):
    pts = [(x, y + 22 * math.sin(x / 120.0 + y)) for x in range(-20, T + 40, 40)]
    d.line(pts, fill=(150, 112, 66, 255), width=14)
d.rectangle([0, 0, T, T], outline=(58, 40, 20, 255), width=24)
# os 3 olhos
for (ex, ey) in [(470, 560), (784, 560), (627, 800)]:
    elipse(d, ex, ey, 64, 60, (30, 20, 10, 255), (20, 14, 8, 255), 10)
    d.ellipse([ex - 20, ey - 20, ex + 8, ey + 4], fill=(96, 68, 38, 255))
salvar(img, "block", "coco")

# ============================================================ ÍCONES DOS EFEITOS
def icone_fundo(cor_base, cor_clara):
    i = novo(); dd = ImageDraw.Draw(i)
    elipse(dd, T // 2, T // 2, 610, 610, cor_base)
    elipse(dd, T // 2 - 180, T // 2 - 220, 330, 260, cor_clara)
    return i

def icone_salva(img, nome):
    salvar(img, "mob_effect", nome)

# TRANQUILO: folha relaxada
img = icone_fundo((72, 118, 62, 255), (88, 138, 76, 255)); d = ImageDraw.Draw(img)
poligono(d, [(627, 250), (980, 500), (760, 900), (500, 900), (280, 500)],
         (46, 84, 40, 255), (28, 56, 26, 255), 14)
poligono(d, [(627, 330), (860, 520), (700, 800), (560, 800), (400, 520)],
         (110, 170, 96, 255))
d.line([627, 300, 627, 880], fill=(28, 56, 26, 255), width=18)
d.line([627, 880, 627, 1010], fill=(46, 84, 40, 255), width=26)
icone_salva(img, "tranquilo")

# MORNO: sol quente
img = icone_fundo((150, 98, 48, 255), (170, 118, 62, 255)); d = ImageDraw.Draw(img)
elipse(d, 627, 627, 240, 240, (232, 168, 74, 255), (180, 120, 48, 255), 12)
elipse(d, 627, 627, 150, 150, (248, 204, 122, 255))
for a in range(0, 360, 30):
    rad = math.radians(a)
    x1 = 627 + 300 * math.cos(rad); y1 = 627 + 300 * math.sin(rad)
    x2 = 627 + 420 * math.cos(rad); y2 = 627 + 420 * math.sin(rad)
    d.line([x1, y1, x2, y2], fill=(240, 184, 92, 255), width=40)
icone_salva(img, "morno")

# SONHO: lua crescente
img = icone_fundo((98, 76, 132, 255), (114, 92, 150, 255)); d = ImageDraw.Draw(img)
elipse(d, 570, 600, 280, 280, (222, 214, 244, 255), (150, 140, 190, 255), 12)
elipse(d, 700, 540, 250, 250, (114, 92, 150, 255))
for (sx, sy) in [(300, 300), (940, 950), (280, 940)]:
    poligono(d, [(sx, sy - 50), (sx + 14, sy - 14), (sx + 50, sy),
                 (sx + 14, sy + 14), (sx, sy + 50), (sx - 14, sy + 14),
                 (sx - 50, sy), (sx - 14, sy - 14)], (230, 224, 250, 255))
icone_salva(img, "sonho")

# OVERDRIVE: raio branco
img = icone_fundo((196, 196, 200, 255), (216, 216, 220, 255)); d = ImageDraw.Draw(img)
poligono(d, [(700, 180), (460, 660), (620, 660), (520, 1080),
             (860, 560), (680, 560), (820, 180)],
         (255, 255, 255, 255), (150, 150, 158, 255), 12)
icone_salva(img, "overdrive")

# VIAGEM: olho psicodélico arco-íris
img = icone_fundo((132, 48, 168, 255), (152, 68, 188, 255)); d = ImageDraw.Draw(img)
for raio, cor in [(520, (240, 80, 80, 255)), (420, (240, 180, 60, 255)),
                  (320, (120, 220, 100, 255)), (220, (80, 160, 240, 255))]:
    elipse(d, 627, 627, raio, raio * 0.86, cor)
elipse(d, 627, 627, 130, 130, (24, 12, 28, 255))
elipse(d, 580, 580, 40, 40, (255, 255, 255, 255))
icone_salva(img, "viagem")

# ABSTINENCIA: caveira
img = icone_fundo((70, 70, 86, 255), (86, 86, 104, 255)); d = ImageDraw.Draw(img)
elipse(d, 627, 560, 320, 300, (206, 206, 214, 255), (120, 120, 134, 255), 12)
d.rounded_rectangle([500, 780, 754, 960], 60, fill=(206, 206, 214, 255),
                    outline=(120, 120, 134, 255), width=12)
elipse(d, 510, 560, 90, 100, (48, 48, 60, 255))
elipse(d, 744, 560, 90, 100, (48, 48, 60, 255))
poligono(d, [(627, 640), (664, 740), (590, 740)], (48, 48, 60, 255))
for tx in (540, 627, 714):
    d.line([tx, 830, tx, 940], fill=(120, 120, 134, 255), width=14)
icone_salva(img, "abstinencia")

print("Texturas da 1.2.58 geradas em HD (1254x1254).")
