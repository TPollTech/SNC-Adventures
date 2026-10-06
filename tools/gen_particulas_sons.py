"""Texturas 16x16 RGBA das particulas proprias do mod.

Mesmo pipeline das demais texturas (geradas por codigo):
- dinheiro.png      cedula de R$ (a cara do item real), verde-nota
- folha_maconha.png folha serrilhada de sete pontas, verde-cannabis
- fumaca_baseado.png fumaca branca translucida (cor de erva vem do setColor)
"""
from PIL import Image, ImageDraw
import os

OUT = os.path.join("src", "main", "resources", "assets", "intoxicantes", "textures", "particle")
os.makedirs(OUT, exist_ok=True)


def canvas():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


# ------------------------------------------------------------- dinheiro
img = canvas()
d = ImageDraw.Draw(img)
# cedula horizontal com faixa clara no centro (a cara do real.png)
d.rectangle([1, 4, 14, 11], fill=(34, 110, 62, 255))          # papel verde-nota
d.rectangle([2, 5, 13, 10], outline=(22, 74, 43, 255))        # moldura
d.rectangle([6, 6, 9, 9], fill=(196, 214, 178, 255))          # selo central
d.ellipse([6, 6, 9, 9], outline=(24, 82, 48, 255))            # circulo do selo
d.point([3, 6], fill=(196, 214, 178, 255))                    # brilho do canto
d.point([12, 9], fill=(196, 214, 178, 255))
img.save(os.path.join(OUT, "dinheiro.png"))

# ------------------------------------------------------- folha de maconha
img = canvas()
d = ImageDraw.Draw(img)
VERDE = (58, 128, 52, 255)
VERDE_CLARO = (86, 158, 72, 255)
# sete pontas radiais partindo do centro (8,10)
folha = [(8, 1), (3, 4), (1, 8), (3, 12), (8, 14), (13, 12), (15, 8), (13, 4)]
for tx, ty in folha:
    for passo in range(4):
        x = 8 + (tx - 8) * passo // 4
        y = 10 + (ty - 10) * passo // 4
        d.point((x, y), fill=VERDE)
        if passo > 1:
            d.point((x - 1 if tx < 8 else x + 1, y), fill=VERDE_CLARO)
d.line([8, 5, 8, 14], fill=VERDE)            # haste central
d.ellipse([7, 9, 9, 11], fill=VERDE)         # miolo
img.save(os.path.join(OUT, "folha_maconha.png"))

# ------------------------------------------------------------- fumaca
img = canvas()
d = ImageDraw.Draw(img)
BRANCA = (235, 235, 230, 190)
for cx, cy, r in [(8, 8, 6), (5, 6, 3), (11, 7, 3), (6, 11, 3), (10, 11, 3)]:
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=BRANCA)
# borda com alpha menor (nuvem macia)
for x in range(16):
    for y in range(16):
        r, g, b, a = img.getpixel((x, y))
        if a > 0 and not (3 <= x <= 12 and 4 <= y <= 12):
            img.putpixel((x, y), (r, g, b, 110))
img.save(os.path.join(OUT, "fumaca_baseado.png"))

print("Particulas geradas em", OUT)
