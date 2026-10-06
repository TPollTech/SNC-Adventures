# -*- coding: utf-8 -*-
"""Prévia visual da PORTA-GRADE (v1.2.53): lê os JSONs REAIS gerados pelo
gen_porta_grade.py e renderiza as 2 portas completas (aberta × fechada),
a textura HD e o item. Saída: ../preview/previa-porta.png
Uso: python tools/preview_porta.py  (a partir da raiz do mod)
"""
import json
import os

from PIL import Image, ImageDraw

ASSETS = os.path.join("src", "main", "resources", "assets", "intoxicantes")
SAIDA = os.path.join("..", "preview")


def modelo(nome):
    with open(os.path.join(ASSETS, "models", "block", nome + ".json"),
              encoding="utf-8") as f:
        return json.load(f)


def textura(nome):
    return Image.open(os.path.join(ASSETS, "textures", "block", nome + ".png")).convert("RGBA")


def cor_uv(uv, tex):
    """Cor média do retângulo UV (unidades 0..16) na textura 128."""
    x0, y0, x1, y1 = uv
    px0, py0, px1, py1 = int(x0 * 8), int(y0 * 8), max(int(x1 * 8), int(x0 * 8) + 1), max(int(y1 * 8), int(y0 * 8) + 1)
    recorte = tex.crop((px0, py0, px1, py1))
    px = [p for p in recorte.getdata() if p[3] > 40]
    if not px:
        return None
    r = sum(p[0] for p in px) // len(px)
    g = sum(p[1] for p in px) // len(px)
    b = sum(p[2] for p in px) // len(px)
    a = sum(p[3] for p in px) // len(px)
    return (r, g, b, a)


def faces_do(modelo, tex):
    """Extrai polígonos (canto, cor) das faces com UV real."""
    texturas_do_modelo = {k: v.split("/")[-1] for k, v in (modelo.get("textures") or {}).items()}
    saida = []
    for el in modelo["elements"]:
        x0, y0, z0 = el["from"]
        x1, y1, z1 = el["to"]
        faces = el["faces"]
        caixas = {
            "north": ([(x0, y1, z0), (x1, y1, z0), (x1, y0, z0), (x0, y0, z0)], [x0, 16 - y1, x1, 16 - y0]),
            "south": ([(x1, y1, z1), (x0, y1, z1), (x0, y0, z1), (x1, y0, z1)], [x0, 16 - y1, x1, 16 - y0]),
            "west":  ([(x0, y1, z1), (x0, y1, z0), (x0, y0, z0), (x0, y0, z1)], [z0, 16 - y1, z1, 16 - y0]),
            "east":  ([(x1, y1, z0), (x1, y1, z1), (x1, y0, z1), (x1, y0, z0)], [z0, 16 - y1, z1, 16 - y0]),
            "up":    ([(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)], [x0, z0, x1, z1]),
            "down":  ([(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], [x0, z0, x1, z1]),
        }
        for nome_face, dados in caixas.items():
            f = faces.get(nome_face)
            if not f:
                continue
            tnome = texturas_do_modelo.get(f["texture"].lstrip("#"), f["texture"].lstrip("#"))
            timg = textura(tnome)
            c = cor_uv(dados[1], timg)
            if c is None:
                continue
            saida.append((dados[0], c))
    return saida


def render(faces, w, h, inclinacao=0.46):
    """Projeção isométrica simples com painter's algorithm."""
    def xy(p):
        return (p[0] - p[2] * inclinacao, -p[1] + p[0] * 0.22 + p[2] * 0.22)
    pts = [xy(p) for pol, _ in faces for p in pol]
    minx = min(p[0] for p in pts); maxx = max(p[0] for p in pts)
    miny = min(p[1] for p in pts); maxy = max(p[1] for p in pts)
    esc = min((w - 40) / (maxx - minx), (h - 40) / (maxy - miny))
    def m(p):
        return (20 + (p[0] - minx) * esc, h - 20 - (p[1] - miny) * esc)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    ordem = sorted(range(len(faces)),
                   key=lambda i: sum(p[2] for p in faces[i][0]) + faces[i][0][0][1] * 0.01)
    for i in ordem:
        pol, c = faces[i]
        d.polygon([m(xy(p)) for p in pol], fill=c)
    return img


def porta_completa(baixo, topo):
    m_baixo = modelo(baixo)
    m_topo = modelo(topo)
    tex = textura("porta_grade")
    faces = faces_do(m_baixo, tex)
    for pol, c in faces_do(m_topo, tex):
        faces.append(([(p[0], p[1] + 16, p[2]) for p in pol], c))
    return faces


def main():
    aberta = porta_completa("porta_grade", "porta_grade_topo")
    fechada = porta_completa("porta_grade_fechada", "porta_grade_topo_fechado")

    grade = Image.new("RGBA", (1500, 760), (24, 26, 32, 255))
    d = ImageDraw.Draw(grade)
    d.text((30, 16), "PORTA-GRADE DO ESQUINAU — 4 variantes (modelos reais do jogo)",
           fill=(230, 230, 220, 255))
    a = render(aberta, 700, 640)
    f = render(fechada, 700, 640)
    grade.paste(a, (40, 60), a)
    grade.paste(f, (760, 60), f)
    d.text((40, 40), "ABERTA (07h~00h) — balcao + vao de atendimento", fill=(160, 220, 160, 255))
    d.text((760, 40), "FECHADA (00h~07h) — painel + grade de ferro", fill=(220, 170, 160, 255))

    # a textura HD em pe
    t = textura("porta_grade").resize((256, 256), Image.NEAREST)
    grade.paste(t, (40, 480), t)
    t2 = textura("porta_grade_grade").resize((256, 256), Image.NEAREST)
    grade.paste(t2, (320, 480), t2)
    d.text((40, 742), "texturas 128x128: madeira/ferro + grade (alfa)", fill=(150, 150, 150, 255))

    saida = os.path.join(SAIDA, "previa-porta.png")
    grade.save(saida)
    print("porta:", saida)


if __name__ == "__main__":
    main()
