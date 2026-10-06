"""Gera o cartucho calibre 12: sprite 16x16 + modelo de item.

Casulo de papel vermelho com base de latao, estilo sprite vanilla.
Uso: python tools/gen_cartucho.py  (a partir da raiz do projeto do mod)
"""
import json
import os

from PIL import Image, ImageDraw

ASSETS = os.path.join("src", "main", "resources", "assets", "intoxicantes")

# paleta
PAPER      = (168, 44, 38, 255)
PAPER_LT   = (206, 78, 62, 255)
PAPER_DK   = (116, 24, 22, 255)
CRIMP      = (142, 34, 30, 255)
CRIMP_DK   = (96, 18, 18, 255)
BRASS      = (205, 178, 100, 255)
BRASS_LT   = (240, 214, 140, 255)
BRASS_DK   = (150, 120, 52, 255)
OUTLINE    = (40, 16, 14, 255)

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# corpo do casulo: colunas 4..11 (8 px de largura), linhas 1..13
x0, x1 = 4, 11
# contorno
d.rectangle([x0 - 1, 1, x1 + 1, 14], fill=OUTLINE)
# bocha/crimp no topo (dobras do papel)
d.rectangle([x0, 1, x1, 3], fill=CRIMP)
for cx in range(x0, x1 + 1, 2):
    d.line([(cx, 1), (cx, 2)], fill=CRIMP_DK)
d.line([(x0, 3), (x1, 3)], fill=PAPER_DK)
# papel vermelho
d.rectangle([x0, 4, x1, 10], fill=PAPER)
d.line([(x0 + 1, 4), (x0 + 1, 10)], fill=PAPER_LT)   # brilho
d.line([(x1 - 1, 4), (x1 - 1, 10)], fill=PAPER_DK)   # sombra
# faixa de reforco
d.line([(x0, 8), (x1, 8)], fill=PAPER_DK)
# base de latao
d.rectangle([x0, 11, x1, 13], fill=BRASS)
d.line([(x0 + 1, 11), (x1 - 1, 11)], fill=BRASS_LT)
d.line([(x0, 13), (x1, 13)], fill=BRASS_DK)
# aro da base (1px mais largo)
d.rectangle([x0 - 1, 13, x1 + 1, 14], fill=BRASS_DK)
d.line([(x0 - 1, 13), (x1 + 1, 13)], fill=BRASS)

caminho = os.path.join(ASSETS, "textures", "item", "cartucho.png")
os.makedirs(os.path.dirname(caminho), exist_ok=True)
img.save(caminho)

# modelo item/generated (mesmo padrao dos outros itens do mod)
modelo = {
    "parent": "minecraft:item/generated",
    "textures": {"layer0": "intoxicantes:item/cartucho"},
}
caminho = os.path.join(ASSETS, "models", "item", "cartucho.json")
os.makedirs(os.path.dirname(caminho), exist_ok=True)
with open(caminho, "w", encoding="utf-8") as f:
    json.dump(modelo, f, indent=2)
    f.write("\n")

# item definition
caminho = os.path.join(ASSETS, "items", "cartucho.json")
os.makedirs(os.path.dirname(caminho), exist_ok=True)
with open(caminho, "w", encoding="utf-8") as f:
    json.dump({"model": {"type": "minecraft:model", "model": "intoxicantes:item/cartucho"}},
              f, indent=2)
    f.write("\n")

print("Cartucho OK: sprite 16x16 + modelo item/generated.")
