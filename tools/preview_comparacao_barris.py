# -*- coding: utf-8 -*-
"""Comparacao ANTES x DEPOIS dos barris (regra 14 do padrao de armas 3D).
Renderiza os 4 modelos antigos (build/preview/antes) e os 4 novos com o
MESMO motor de preview, lado a lado: previa-barris-antes-depois.png"""
import io
import json
import os

from PIL import Image, ImageDraw

import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from preview_bebidas import (ASSETS, faces_com_cor, vista_iso, vista_lateral,
                             vista_topo, vista_gui, renderizar, projetar, cantos)

ANTES = os.path.join("build", "preview", "antes")
BARRIS = ["cachaca", "cerveja", "rum", "vinho"]


def render_de_json(caminho_modelo, tamanho=320):
    modelo = json.load(open(caminho_modelo, encoding="utf-8"))
    tex_nome = next(iter(modelo["textures"].values())).split("/")[-1]
    tex_path = os.path.join(ASSETS, "textures", "block", tex_nome + ".png")
    tex = Image.open(tex_path).convert("RGBA")
    faces = faces_com_cor(modelo, tex)

    def xy(p):
        return (p[0] - p[2], -p[1] * 0.72 + (p[0] + p[2]) * 0.25)

    return renderizar(tamanho, int(tamanho * 0.7), projetar(faces, xy, lambda c: c[0] + c[1] * 0.8 - c[2]),
                      fundo=(38, 40, 48, 255))


CELULA = 360
linha_a, linha_d = 40, 40 + CELULA + 40
grade = Image.new("RGBA", (CELULA * 4 + 50, CELULA * 2 + 130), (24, 26, 32, 255))
d = ImageDraw.Draw(grade)
d.text((20, 10), "ANTES (v1 — faixas grossas, aros de textura, sem curva real)", fill=(240, 120, 110, 255))
d.text((20, linha_d - 26), "DEPOIS (v2 — barriga graduada, 5 aros geométricos, tábuas contínuas)", fill=(120, 230, 140, 255))

for i, b in enumerate(BARRIS):
    x = 20 + i * CELULA
    antes = render_de_json(os.path.join(ANTES, "barril_%s.json" % b))
    depois = render_de_json(os.path.join(ASSETS, "models", "block", "barril_%s.json" % b))
    grade.paste(antes, (x, linha_a), antes)
    grade.paste(depois, (x, linha_d + 16), depois)
    d.text((x + 4, linha_d + CELULA + 16), b, fill=(220, 220, 210, 255))

grade.save(os.path.join("..", "preview", "previa-barris-antes-depois.png"))
print("comparacao: ../preview/previa-barris-antes-depois.png")
