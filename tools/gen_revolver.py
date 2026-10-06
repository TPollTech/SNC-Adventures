"""Revólver .38 (três-oitão) — RECRIADO seguindo o padrão de armas 3D do AGENTS.md.

Evolução do gerador antigo: anatomia real de revólver de serviço (cabo com rake,
backstrap, tambor de latão com câmaras e flutes, top strap, martelo, ejector rod,
mira frontal e alça), textura 128x128 com materiais distintos (aço azulado, latão,
nogueira, borracha) e UV por material — mesmo pipeline da escopeta aprovada.

O modelo nasce DESENHADO com o cano no +X (lado) e é remapeado pra +Y (convenção
de item 3D) pelo remap (x,y,z) -> (z,x,y). REGRA 16: o remap também converte o
EIXO das rotações de elemento (cíclico: x->y->z->x preserva o sentido).

Uso: python tools/gen_revolver.py  (a partir da raiz do projeto do mod)
"""
import json
import os
import random
import shutil

from PIL import Image, ImageDraw

ASSETS = os.path.join("src", "main", "resources", "assets", "intoxicantes")

# ------------------------------------------------------------------ textura (atlas 128x128)
TEX_SIZE = 128
TILE = 16

BASES = {
    "blued_steel": ((104, 116, 126), (22, 28, 34), (52, 62, 72)),
    "steel_dark":  ((70, 76, 82),    (13, 16, 19), (34, 39, 44)),
    "steel_light": ((174, 184, 192), (82, 92, 102), (122, 134, 144)),
    "bore":        ((28, 30, 32),    (3, 4, 5),    (11, 13, 15)),
    "brass":       ((222, 194, 118), (104, 74, 24), (165, 132, 62)),
    "brass_dark":  ((150, 122, 52),  (66, 46, 14), (104, 80, 30)),
    "walnut":      ((155, 96, 49),   (52, 28, 13), (101, 57, 28)),
    "walnut_light":((197, 133, 72), (89, 51, 25), (145, 88, 44)),
    "walnut_dark": ((101, 57, 29),   (29, 16, 8),  (62, 34, 17)),
    "black":       ((58, 60, 61),    (10, 11, 12), (27, 29, 31)),
    "rubber":      ((56, 54, 50),    (13, 12, 11), (29, 28, 26)),
    "gold":        ((236, 200, 96),  (130, 96, 26), (188, 152, 62)),
}
POS = {}
for i, name in enumerate(BASES):
    POS[name] = ((i % 8) * TILE, (i // 8) * TILE)

tex_path = os.path.join(ASSETS, "textures", "item", "revolver.png")

# v1.2.42: o modelo 3D precisa de ATLAS de materiais. Se existir arte manual do
# usuário (PNG grande), preserva em backups/texturas-manuais/ antes de gerar.
if os.path.exists(tex_path) and os.path.getsize(tex_path) > 20000:
    destino = os.path.join("backups", "texturas-manuais")
    os.makedirs(destino, exist_ok=True)
    salvaguarda = os.path.join(destino, "revolver_manual.png")
    if not os.path.exists(salvaguarda):
        shutil.copy2(tex_path, salvaguarda)
        print(f"  arte manual preservada em {salvaguarda}")
    print("  gerando atlas novo por cima (arte manual guardada em backup)")
else:
    print("  nenhuma arte manual detectada; gerando atlas")

rng = random.Random(380)
img = Image.new("RGBA", (TEX_SIZE, TEX_SIZE), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
for name, (px, py) in POS.items():
    light, dark, mid = BASES[name]
    for yy in range(TILE):
        t = (yy / (TILE - 1)) ** 0.82
        base = tuple(int(light[c] * (1 - t) + dark[c] * t) for c in range(3))
        for xx in range(TILE):
            noise = rng.randint(-5, 5)
            if name.startswith("walnut"):
                wave = (xx * 3 + yy * 2) % 11
                noise += 10 if wave in (0, 1) else (-4 if wave in (5, 6) else 0)
            elif name in ("blued_steel", "steel_dark", "steel_light"):
                noise += 4 if ((xx * 7 + yy * 3) % 19 == 0) else 0
                if (xx + yy * 2) % 29 == 0:
                    noise -= 7
            elif name in ("brass", "brass_dark", "gold"):
                noise += 5 if (xx + yy) % 9 == 0 else 0
            col = tuple(max(0, min(255, v + noise)) for v in base)
            d.point((px + xx, py + yy), fill=(*col, 255))
    d.line((px, py, px + TILE - 1, py), fill=(*light, 255))
    d.line((px, py + TILE - 1, px + TILE - 1, py + TILE - 1), fill=(*dark, 255))

# rubber do cabo: micro-estrias horizontais
px, py = POS["rubber"]
for yy in (4, 8, 12):
    d.line((px, py + yy, px + TILE - 1, py + yy), fill=(22, 21, 20, 255))

os.makedirs(os.path.dirname(tex_path), exist_ok=True)
img.save(tex_path)


def uv(name):
    px, py = POS[name]
    scale = TEX_SIZE / 16.0
    return [px / scale, py / scale, (px + TILE) / scale, (py + TILE) / scale]


# ------------------------------------------------------------------ modelo 3D
AXIS_REMAP = {"x": "y", "y": "z", "z": "x"}


def elemento(nome, de, ate, lados, rot=None):
    """Remap (x,y,z) -> (z,x,y): desenhado com cano no +X, final com cano no +Y.
    REGRA 16: o eixo de rotação também é remapeado (cíclico preserva o sentido)."""
    def remap(p):
        x, y, z = p
        return [z, x, y]

    faces = {lado: {"uv": uv(mat), "texture": "#revolver"} for lado, mat in lados.items()}
    el = {"name": nome, "from": remap(de), "to": remap(ate), "faces": faces}
    if rot:
        el["rotation"] = {
            "origin": remap(rot["origin"]),
            "axis": AXIS_REMAP.get(rot["axis"], rot["axis"]),
            "angle": rot["angle"],
            "rescale": True,
        }
    return el


def mats(main, top=None, bottom=None, side=None, ends=None):
    top = top or main
    bottom = bottom or main
    side = side or main
    ends = ends or side
    return {"up": top, "down": bottom, "north": side, "south": side, "east": ends, "west": ends}


E = []

# ------------------------------ cabo: nogueira com rake real de revólver (-22,5°)
ROT_CABO = {"origin": [5.4, 6.6, 8.0], "axis": "z", "angle": -22.5}
E.append(elemento("cabo_superior", [3.84, 4.19, 6.9], [6.04, 6.79, 9.1],
                  mats("walnut", "walnut_light", "walnut_dark", "walnut", "walnut_dark"), ROT_CABO))
E.append(elemento("cabo_inferior", [2.92, 1.97, 7.0], [5.12, 4.57, 9.0],
                  mats("walnut", "walnut_light", "walnut_dark", "walnut", "walnut_dark"), ROT_CABO))
E.append(elemento("cabo_pomal", [2.26, 0.72, 7.1], [4.26, 2.12, 8.9],
                  mats("rubber", "rubber", "black", "rubber", "black"), ROT_CABO))
# backstrap de aço ligando o cabo à armação
E.append(elemento("backstrap", [4.5, 5.9, 7.4], [5.5, 9.2, 8.6],
                  mats("steel_dark")))

# ------------------------------ armação: aço azulado, top strap por cima do tambor
E.append(elemento("armacao_baixa", [5.4, 6.3, 6.8], [10.4, 8.6, 9.2],
                  mats("blued_steel", "steel_light", "steel_dark", "blued_steel", "steel_dark")))
E.append(elemento("top_strap", [4.9, 10.2, 7.3], [10.5, 11.4, 8.7],
                  mats("blued_steel", "steel_light", "steel_dark", "blued_steel", "steel_dark")))
E.append(elemento("armacao_frontal", [9.9, 8.5, 7.3], [10.6, 10.3, 8.7],
                  mats("blued_steel", "steel_light", "steel_dark", "blued_steel", "steel_dark")))
# culatra fixa (standing breech): a face traseira do tambor encosta aqui
E.append(elemento("culatra", [4.85, 8.4, 7.0], [5.6, 10.3, 9.0],
                  mats("steel_dark")))

# ------------------------------ tambor: latão com câmaras visíveis na traseira e flutes
E.append(elemento("tambor", [5.7, 8.05, 6.9], [9.7, 10.25, 9.1],
                  mats("brass", "brass", "brass_dark", "brass", "brass_dark")))
E.append(elemento("tambor_aro_tras", [5.6, 8.0, 6.85], [6.05, 10.3, 9.15],
                  mats("brass_dark")))
E.append(elemento("tambor_aro_frente", [9.35, 8.0, 6.85], [9.8, 10.3, 9.15],
                  mats("brass_dark")))
# 3 câmaras na face traseira (a leitura de "revólver" vista por trás/cima)
E.append(elemento("camara_cima", [5.42, 9.25, 7.7], [5.62, 9.9, 8.3], mats("bore")))
E.append(elemento("camara_esq", [5.42, 8.3, 7.2], [5.62, 8.95, 7.8], mats("bore")))
E.append(elemento("camara_dir", [5.42, 8.3, 8.2], [5.62, 8.95, 8.8], mats("bore")))
# flutes de recarga nas 2 faces laterais (2 por lado)
for nome, y0, z0, z1 in [
    ("flute_s_cima", 8.9, 9.08, 9.16), ("flute_s_baixo", 8.3, 9.08, 9.16),
    ("flute_n_cima", 8.9, 6.84, 6.92), ("flute_n_baixo", 8.3, 6.84, 6.92),
]:
    E.append(elemento(nome, [6.6, y0, z0], [8.8, y0 + 0.5, z1], mats("steel_dark")))

# ------------------------------ cano: 4 polegadas, abaixo do top strap
E.append(elemento("cano", [10.5, 8.9, 7.35], [18.2, 10.1, 8.65],
                  mats("blued_steel", "steel_light", "steel_dark", "blued_steel", "bore")))
E.append(elemento("cano_rib", [10.5, 10.1, 7.55], [18.1, 10.4, 8.45],
                  mats("steel_light")))
E.append(elemento("ejector_rod", [10.6, 8.45, 7.8], [15.8, 8.9, 8.2],
                  mats("steel_light")))
E.append(elemento("boca_colar", [18.2, 8.8, 7.25], [18.7, 10.2, 8.75],
                  mats("steel_dark", "blued_steel", "steel_dark", "steel_dark", "bore")))
E.append(elemento("boca", [18.7, 9.0, 7.45], [19.05, 10.0, 8.55], mats("bore")))
# mira frontal (lâmina) e alça traseira em entalhe
E.append(elemento("mira_frontal", [17.7, 10.4, 7.8], [18.1, 11.0, 8.2],
                  mats("steel_light")))
E.append(elemento("mira_traseira", [5.0, 11.4, 7.7], [5.5, 11.75, 8.3],
                  mats("steel_dark")))

# ------------------------------ martelo armado (22,5 é o ângulo legal do vanilla)
ROT_MARTELO = {"origin": [10.0, 11.4, 8.0], "axis": "z", "angle": 22.5}
E.append(elemento("martelo", [9.5, 11.3, 7.6], [10.4, 12.4, 8.4],
                  mats("steel_light", "steel_light", "steel_dark", "steel_dark", "steel_dark"),
                  ROT_MARTELO))

# ------------------------------ gatilho, guarda-mato e desconector do tambor
E.append(elemento("guarda_baixo", [5.8, 5.3, 7.5], [8.3, 5.7, 8.5], mats("black")))
E.append(elemento("guarda_tras", [5.8, 5.3, 7.5], [6.2, 6.6, 8.5], mats("black")))
E.append(elemento("guarda_frente", [7.95, 5.3, 7.5], [8.35, 6.6, 8.5], mats("black")))
ROT_GATILHO = {"origin": [7.1, 6.9, 8.0], "axis": "z", "angle": -22.5}
E.append(elemento("gatilho", [6.9, 5.7, 7.75], [7.3, 6.9, 8.25],
                  mats("steel_light", "steel_light", "steel_dark", "steel_light", "steel_dark"),
                  ROT_GATILHO))
E.append(elemento("pino_tambor", [10.0, 9.3, 6.6], [10.5, 9.7, 6.85],
                  mats("steel_light")))

modelo = {
    "gui_light": "side",
    "textures": {
        "revolver": "intoxicantes:item/revolver",
        "particle": "intoxicantes:item/revolver",
    },
    "elements": E,
    "display": {
        # REGRA 10: em primeira pessoa apontada pra frente, alinhada com a mira —
        # roll mínimo (-6°) em vez do tombamento de -50° do modelo antigo.
        "firstperson_righthand": {
            "rotation": [-90, -2, -6], "translation": [1.0, 1.75, 0.45],
            "scale": [0.75, 0.75, 0.75],
        },
        "firstperson_lefthand": {
            "rotation": [-90, 2, 6], "translation": [1.0, 1.75, 0.45],
            "scale": [0.75, 0.75, 0.75],
        },
        # REGRA 11: terceira pessoa proporcional, sem encolher demais.
        "thirdperson_righthand": {
            "rotation": [-90, 0, -50], "translation": [1.5, 0.0, -2.4],
            "scale": [0.85, 0.85, 0.85],
        },
        "thirdperson_lefthand": {
            "rotation": [-90, 0, 40], "translation": [1.5, 0.0, -2.4],
            "scale": [0.85, 0.85, 0.85],
        },
        # REGRA 12: o modelo fica grande; só o transform gui/fixed/ground reduz.
        "gui": {"rotation": [10, 45, 30], "translation": [0, 0, 0],
                "scale": [0.62, 0.62, 0.62]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 1.5, 0],
                  "scale": [0.42, 0.42, 0.42]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 1, 0],
                   "scale": [0.34, 0.34, 0.34]},
    },
}

caminho = os.path.join(ASSETS, "models", "item", "revolver.json")
os.makedirs(os.path.dirname(caminho), exist_ok=True)
with open(caminho, "w", encoding="utf-8") as f:
    json.dump(modelo, f, indent=2, ensure_ascii=False)
    f.write("\n")

# item definition (padrão novo de items/*.json) — nome e caminho preservados
caminho = os.path.join(ASSETS, "items", "revolver.json")
os.makedirs(os.path.dirname(caminho), exist_ok=True)
with open(caminho, "w", encoding="utf-8") as f:
    json.dump({"model": {"type": "minecraft:model", "model": "intoxicantes:item/revolver"}},
              f, indent=2)
    f.write("\n")


# ------------------------------------------------------------------ cartucho .38 (sprite 16x16)
# Casulo de LATÃO curto e grosso (diferente do cartucho 12 vermelho de papel)
PAPER      = (205, 178, 100, 255)   # latão
PAPER_LT   = (240, 214, 140, 255)   # latão claro
PAPER_DK   = (150, 120, 52, 255)    # latão escuro
CRIMP      = (170, 138, 62, 255)    # boca do casulo
CRIMP_DK   = (110, 86, 34, 255)
BULLET     = (108, 114, 122, 255)   # chumbo exposto no topo
BULLET_LT  = (168, 174, 182, 255)
BULLET_DK  = (70, 73, 79, 255)
OUTLINE    = (30, 22, 12, 255)

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

x0, x1 = 4, 11
# contorno
d.rectangle([x0 - 1, 1, x1 + 1, 14], fill=OUTLINE)
# ponta de chumbo exposta (o .38 tem bala de cobre/chumbo a vista no topo)
d.rectangle([x0 + 1, 1, x1 - 1, 3], fill=BULLET)
d.line([(x0 + 2, 1), (x0 + 2, 3)], fill=BULLET_LT)
d.line([(x1 - 2, 1), (x1 - 2, 3)], fill=BULLET_DK)
d.line([(x0 + 1, 4), (x1 - 1, 4)], fill=CRIMP_DK)
# boca crimpada do casulo de latão
d.rectangle([x0, 4, x1, 5], fill=CRIMP)
for cx in range(x0, x1 + 1, 2):
    d.line([(cx, 4), (cx, 5)], fill=CRIMP_DK)
# corpo de latão
d.rectangle([x0, 6, x1, 10], fill=PAPER)
d.line([(x0 + 1, 6), (x0 + 1, 10)], fill=PAPER_LT)   # brilho
d.line([(x1 - 1, 6), (x1 - 1, 10)], fill=PAPER_DK)   # sombra
# sulco de extração
d.line([(x0, 10), (x1, 10)], fill=PAPER_DK)
# base com aro (1px mais largo)
d.rectangle([x0, 11, x1, 13], fill=PAPER_DK)
d.rectangle([x0 - 1, 12, x1 + 1, 14], fill=CRIMP_DK)
d.line([(x0 - 1, 12), (x1 + 1, 12)], fill=PAPER)
d.line([(x0 - 1, 14), (x1 + 1, 14)], fill=PAPER_DK)

caminho = os.path.join(ASSETS, "textures", "item", "cartucho_38.png")
# v1.2.35: arte MANUAL do usuário — nunca sobrescrever
if os.path.exists(caminho) and os.path.getsize(caminho) > 20000:
    print("  SKIP (manual do usuário): cartucho_38.png")
else:
    os.makedirs(os.path.dirname(caminho), exist_ok=True)
    img.save(caminho)

# modelo item/generated (mesmo padrão dos outros itens do mod)
modelo = {
    "parent": "minecraft:item/generated",
    "textures": {"layer0": "intoxicantes:item/cartucho_38"},
}
caminho = os.path.join(ASSETS, "models", "item", "cartucho_38.json")
os.makedirs(os.path.dirname(caminho), exist_ok=True)
with open(caminho, "w", encoding="utf-8") as f:
    json.dump(modelo, f, indent=2)
    f.write("\n")

# item definition
caminho = os.path.join(ASSETS, "items", "cartucho_38.json")
os.makedirs(os.path.dirname(caminho), exist_ok=True)
with open(caminho, "w", encoding="utf-8") as f:
    json.dump({"model": {"type": "minecraft:model", "model": "intoxicantes:item/cartucho_38"}},
              f, indent=2)
    f.write("\n")

print(f"Revólver .38 RECRIADO: {len(E)} elements | atlas {TEX_SIZE}x{TEX_SIZE} "
      f"| cabo -22,5° | tambor de latão com 3 câmaras visíveis")
