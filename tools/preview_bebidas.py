# -*- coding: utf-8 -*-
"""Renderiza os modelos 3D dos BARRIS de bebida (e texturas das maquinas)
pra pre-visualizacao. Gera preview/previa-barril-modelos.png (grade com
iso/lateral/topo de cada barril) e previa-gui.png (icones de inventario).

Reusa o motor do preview_escopeta.py, parametrizado por modelo.
Uso: python tools/preview_bebidas.py  (a partir da raiz do mod)
"""
import base64
import io
import json
import math
import os

from PIL import Image, ImageDraw

ASSETS = os.path.join("src", "main", "resources", "assets", "intoxicantes")
SAIDA = os.path.join("..", "preview")

BARRIS = [
    ("barril_cachaca", (1.10, 0.75, 0.25)),   # fundo quente (amburana)
    ("barril_cerveja", (0.95, 0.85, 0.60)),
    ("barril_rum", (0.55, 0.55, 0.65)),
    ("barril_vinho", (0.85, 0.65, 0.85)),
]

# Máquinas de matéria-prima: mesmo motor, uma textura-atlas por modelo
MAQUINAS = ["dorna_bebida", "alambique", "moenda_cana", "prensa_uvas", "caldeirao_mostura"]


MAQUINAS_2BLOCOS = {"dorna_bebida", "alambique", "moenda_cana", "prensa_uvas",
                    "caldeirao_mostura"}


def carrega_modelo(nome):
    """v1.2.53: máquinas de tamanho real = os DOIS modelos (_baixo + _alto)
    fundidos (o alto deslocado +16 de volta pro espaço completo)."""
    if nome in MAQUINAS_2BLOCOS:
        with open(os.path.join(ASSETS, "models", "block", nome + "_baixo.json"),
                  encoding="utf-8") as f:
            baixo = json.load(f)
        with open(os.path.join(ASSETS, "models", "block", nome + "_alto.json"),
                  encoding="utf-8") as f:
            alto = json.load(f)
        fundido = dict(baixo)
        els = list(baixo["elements"])
        for e in alto["elements"]:
            e2 = dict(e)
            e2["from"] = [e["from"][0], e["from"][1] + 16, e["from"][2]]
            e2["to"] = [e["to"][0], e["to"][1] + 16, e["to"][2]]
            els.append(e2)
        fundido["elements"] = els
        return fundido
    with open(os.path.join(ASSETS, "models", "block", nome + ".json"), encoding="utf-8") as f:
        return json.load(f)


def carrega_textura(nome):
    # textura primaria do modelo: primeira layer citada
    m = carrega_modelo(nome)
    tex_nome = next(iter(m["textures"].values())).split("/")[-1]
    with open(os.path.join(ASSETS, "textures", "block", tex_nome + ".png"), "rb") as f:
        return Image.open(io.BytesIO(f.read())).convert("RGBA")


def uv_media(tex, uv_box):
    x0, y0, x1, y1 = uv_box
    sx, sy = tex.width / 16.0, tex.height / 16.0
    regiao = tex.crop((int(x0 * sx), int(y0 * sy), int(x1 * sx), int(y1 * sy))).resize((1, 1), Image.BILINEAR)
    r, g, b, a = regiao.getpixel((0, 0))
    return (r, g, b, a)


def rot_ponto(p, rot):
    if not rot:
        return p
    ox, oy, oz = rot["origin"]
    ang = math.radians(rot["angle"])
    c, s = math.cos(ang), math.sin(ang)
    eixo = rot["axis"]
    x, y, z = p[0] - ox, p[1] - oy, p[2] - oz
    if eixo == "x":
        y, z = y * c - z * s, y * s + z * c
    elif eixo == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + ox, y + oy, z + oz)


def cantos(el):
    x0, y0, z0 = el["from"]
    x1, y1, z1 = el["to"]
    pts = [(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
    if el.get("rotation"):
        pts = [rot_ponto(p, el["rotation"]) for p in pts]
    return pts


QUADS = {
    "up": (2, 3, 7, 6), "down": (0, 1, 5, 4), "north": (0, 4, 6, 2),
    "south": (1, 5, 7, 3), "east": (4, 5, 7, 6), "west": (0, 1, 3, 2),
}
SOMBRA = {"up": 1.0, "down": 0.55, "north": 0.82, "south": 0.62, "east": 0.72, "west": 0.66}


def faces_com_cor(modelo, tex):
    def tex_de(dados):
        ref = dados.get("texture", "")
        if ref.startswith("#"):
            ref = modelo["textures"].get(ref[1:], ref[1:])
        return ref.split("/")[-1] if "/" in ref else ref

    saida = []
    for el in modelo.get("elements", []):
        pts = cantos(el)
        for nome_face, dados in el["faces"].items():
            quad = [pts[i] for i in QUADS[nome_face]]
            tex_nome = tex_de(dados)
            cor = uv_media(tex, dados["uv"]) if tex_nome == tex_nome and tex is not None else (140, 140, 140, 255)
            fator = SOMBRA[nome_face]
            cor_face = tuple(min(255, int(c * fator)) for c in cor[:3]) + (255,)
            cx = sum(p[0] for p in quad) / 4
            cy = sum(p[1] for p in quad) / 4
            cz = sum(p[2] for p in quad) / 4
            saida.append((nome_face, cor_face, quad, (cx, cy, cz)))
    return saida


def projetar(faces, funcao_xy, chave_prof):
    pol = []
    for nome, cor, quad, centro in faces:
        pol.append((chave_prof(centro), [funcao_xy(p) for p in quad], cor))
    pol.sort(key=lambda p: p[0])
    return pol


def renderizar(largura, altura, poligonos, fundo=(56, 54, 64, 255)):
    todos = [pt for _, pts, _ in poligonos for pt in pts]
    if not todos:
        return Image.new("RGBA", (largura, altura), fundo)
    xs = [p[0] for p in todos]
    ys = [p[1] for p in todos]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    lc, ac = max(x1 - x0, 0.01), max(y1 - y0, 0.01)
    margem = 16
    esc = min((largura - 2 * margem) / lc, (altura - 2 * margem) / ac)
    ox = (largura - lc * esc) / 2 - x0 * esc
    oy = (altura - ac * esc) / 2 - y0 * esc
    img = Image.new("RGBA", (largura, altura), fundo)
    d = ImageDraw.Draw(img)
    for _, pts, cor in poligonos:
        d.polygon([(ox + p[0] * esc, oy + p[1] * esc) for p in pts], fill=cor,
                  outline=(16, 14, 18, 140))
    return img


def vista_iso(faces):
    def xy(p):
        return (p[0] - p[2], -p[1] * 0.72 + (p[0] + p[2]) * 0.25)
    return renderizar(430, 300, projetar(faces, xy, lambda c: c[0] + c[1] * 0.8 - c[2]))


def vista_lateral(faces):
    def xy(p):
        return (p[0], -p[1])
    return renderizar(300, 200, projetar(faces, xy, lambda c: c[2]))


def vista_topo(faces):
    def xy(p):
        return (p[0], p[2])
    return renderizar(300, 200, projetar(faces, xy, lambda c: c[1]))


def vista_gui(faces):
    def xy(p):
        return (p[0] - p[2] * 0.5, -p[1] + p[0] * 0.18 + p[2] * 0.18)
    return renderizar(220, 220, projetar(faces, xy, lambda c: c[0] + c[1] * 0.6 - c[2] * 0.5))


def monta_grade():
    celula_l, celula_a = 430, 300 + 200 + 220 + 40
    grade = Image.new("RGBA", (celula_l * 2 + 60, celula_a * 2 + 100), (24, 26, 32, 255))
    d = ImageDraw.Draw(grade)
    posicoes = [(30, 40), (30 + celula_l + 20, 40), (30, 40 + celula_a + 30), (30 + celula_l + 20, 40 + celula_a + 30)]
    for (nome, _tint), (gx, gy) in zip(BARRIS, posicoes):
        modelo = carrega_modelo(nome)
        tex = carrega_textura(nome)
        faces = faces_com_cor(modelo, tex)
        iso = vista_iso(faces)
        lat = vista_lateral(faces)
        top = vista_topo(faces)
        gui = vista_gui(faces)
        # composicao da celula: iso em cima; lateral+topo; gui no canto
        celula = Image.new("RGBA", (celula_l, celula_a), (38, 40, 48, 255))
        celula.paste(iso, (0, 0), iso)
        x2 = (celula_l - 300 * 2 - 12) // 2
        celula.paste(lat, (x2, 300 + 8), lat)
        celula.paste(top, (x2 + 300 + 12, 300 + 8), top)
        celula.paste(gui, (celula_l - 220, celula_a - 220), gui)
        grade.paste(celula, (gx, gy), celula)
        d.text((gx + 12, gy - 26), nome, fill=(230, 230, 220, 255))
    os.makedirs(SAIDA, exist_ok=True)
    grade.save(os.path.join(SAIDA, "previa-barril-modelos.png"))
    print("grade:", os.path.join(SAIDA, "previa-barril-modelos.png"))


def monta_gui():
    # icones de inventario lado a lado (como aparecem no criativo)
    gui_total = Image.new("RGBA", (240 * 4 + 60, 280), (24, 26, 32, 255))
    for i, (nome, _tint) in enumerate(BARRIS):
        modelo = carrega_modelo(nome)
        tex = carrega_textura(nome)
        faces = faces_com_cor(modelo, tex)
        gui = vista_gui(faces)
        gui_total.paste(gui, (30 + i * 240, 30), gui)
    gui_total.save(os.path.join(SAIDA, "previa-barril-gui.png"))
    print("gui:", os.path.join(SAIDA, "previa-barril-gui.png"))


def monta_maquinas():
    """Grade com as 5 máquinas de matéria-prima (iso + lateral + topo + gui)."""
    celula_l, celula_a = 430, 300 + 200 + 40
    colunas = 3
    linhas = (len(MAQUINAS) + colunas - 1) // colunas
    grade = Image.new("RGBA", (celula_l * colunas + 60, celula_a * linhas + 100),
                      (24, 26, 32, 255))
    d = ImageDraw.Draw(grade)
    for i, nome in enumerate(MAQUINAS):
        gx = 30 + (i % colunas) * (celula_l + 20)
        gy = 40 + (i // colunas) * (celula_a + 30)
        modelo = carrega_modelo(nome)
        tex = carrega_textura(nome)
        faces = faces_com_cor(modelo, tex)
        celula = Image.new("RGBA", (celula_l, celula_a), (38, 40, 48, 255))
        celula.paste(vista_iso(faces), (0, 0), vista_iso(faces))
        lat = vista_lateral(faces)
        top = vista_topo(faces)
        x2 = (celula_l - 300 * 2 - 12) // 2
        celula.paste(lat, (x2, 300 + 8), lat)
        celula.paste(top, (x2 + 300 + 12, 300 + 8), top)
        gui = vista_gui(faces)
        celula.paste(gui, (celula_l - 200, celula_a - 200), gui)
        grade.paste(celula, (gx, gy), celula)
        d.text((gx + 12, gy - 26), nome, fill=(230, 230, 220, 255))
    grade.save(os.path.join(SAIDA, "previa-maquinas.png"))
    print("maquinas:", os.path.join(SAIDA, "previa-maquinas.png"))


def monta_maquinas_zoom():
    """Isométrica GRANDE de cada máquina (2 por linha) — revisão de proporção."""
    cel_l, cel_a = 640, 460
    colunas = 2
    linhas = (len(MAQUINAS) + colunas - 1) // colunas
    grade = Image.new("RGBA", (cel_l * colunas + 60, cel_a * linhas + 80), (24, 26, 32, 255))
    d = ImageDraw.Draw(grade)
    for i, nome in enumerate(MAQUINAS):
        faces = faces_com_cor(carrega_modelo(nome), carrega_textura(nome))
        big = renderizar(620, 440, projetar(faces, lambda q: (q[0] - q[2], -q[1] * 0.72 + (q[0] + q[2]) * 0.25),
                                            lambda c: c[0] + c[1] * 0.8 - c[2]))
        gx = 30 + (i % colunas) * (cel_l + 20)
        gy = 40 + (i // colunas) * (cel_a + 30)
        grade.paste(big, (gx, gy), big)
        d.text((gx + 12, gy - 24), nome, fill=(230, 230, 220, 255))
    grade.save(os.path.join(SAIDA, "previa-maquinas-zoom.png"))
    print("zoom:", os.path.join(SAIDA, "previa-maquinas-zoom.png"))


if __name__ == "__main__":
    monta_grade()
    monta_gui()
    monta_maquinas()
    monta_maquinas_zoom()
