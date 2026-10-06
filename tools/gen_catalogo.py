#!/usr/bin/env python3
"""Arte voxel canônica do catálogo SNC: ingredientes, consumíveis e especiais.

As funções model(), atlas(), particle() e preview_entries() são puras: importá-las para o
estúdio não toca os recursos do jogo. A CLI grava SOMENTE depois de aprovação
visual explícita, com --approved. Não muda registros, receitas ou efeitos.

128x128 por atlas e por superfície dedicada de partícula, sem fotos, fontes
externas ou overrides do resource pack. Quatro arquivos por item: modelo,
definition, atlas e partícula sem letras/rótulos; nenhum tile é ampliado.
16 unidades = 1 bloco; +Y sobe; base Y=0 e centro X/Z=8. Faces north são frente.
Uso após aprovação: python tools/gen_catalogo.py --approved
                    python tools/gen_catalogo.py --only ingredientes --approved
                    python tools/gen_catalogo.py --only guia_snc,central_comando --list
"""
from __future__ import annotations

import argparse
import base64
import copy
import io
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/intoxicantes"
FACES = ("north", "south", "east", "west", "up", "down")
TILES = {"primary": (0, 0, 32), "secondary": (32, 0, 32),
         "accent": (64, 0, 32), "dark": (96, 0, 32),
         "light": (0, 32, 32), "metal": (32, 32, 32),
         "paper": (64, 32, 32), "glow": (96, 32, 32),
         "label": (0, 64, 64), "back": (64, 64, 64)}

# Cada item tem forma própria; os ovos compartilham casco e emblemas próprios.
CATALOG = [
    {"id": "guia_snc", "name": "Guia do SNC Adventures", "category": "Especiais", "kind": "book", "colors": ["435b40", "314832", "ad784f", "273224", "c1ad88", "b69758", "dccbad", "c88360"], "description": "Livro de couro verde: lombada arredondada em degraus, páginas envelhecidas, costura e quatro rebites."},
    {"id": "central_comando", "name": "Central de Comando", "category": "Especiais", "kind": "remote", "colors": ["353e45", "4a5860", "79a394", "17212a", "94abb6", "788994", "aebcbf", "b0dda1"], "description": "Controle portátil eletrônico: visor LED SNC, antena curta, teclado em relevo e tampa da bateria."},
    {"id": "camisa_matanza", "name": "Camisa do Matanza", "category": "Especiais", "kind": "shirt", "colors": ["29282a", "414045", "9e5a50", "19191d", "b2a599", "817265", "d4c4ac", "aa7954"], "description": "Camiseta preta com mangas, gola aberta, costuras e impressão Matanza no peito; modelo do item de roupa."},
    {"id": "ovo_gago", "name": "Ovo do Gago", "category": "Ovos dos NPCs", "kind": "egg", "colors": ["d6cba5", "b9aa7a", "587645", "725d3d", "ece3c8", "ba9a58", "dbcba8", "88a264"], "description": "Casco voxel marfim, manchas oliva, emblema de avental e faixa de balcão; mesma família dos três ovos."},
    {"id": "ovo_traficante", "name": "Ovo do Traficante", "category": "Ovos dos NPCs", "kind": "egg", "colors": ["727a77", "566660", "948557", "333e3a", "afbab0", "b39c6a", "cbc9b3", "a29169"], "description": "Casco voxel de tom asfalto, manchas cinza, emblema de capuz e costuras da rua."},
    {"id": "ovo_juca", "name": "Ovo do Juça", "category": "Ovos dos NPCs", "kind": "egg", "colors": ["bca47a", "9e805e", "644c42", "332d2b", "decba5", "ad925b", "d9c5a1", "b1855d"], "description": "Casco voxel ocre, manchas carvão e emblema de camiseta com gola; volume e acabamento da mesma família."},
    {"id": "cafe_verde", "name": "Café Verde", "category": "Ingredientes", "kind": "coffee", "colors": ["8a9a64", "697b50", "b0b880", "4e613d", "c2c89a", "947d52", "d7c5a1", "a5b178"], "description": "Três grãos verdes em volume, contorno oval em degraus e sulco real no meio de cada grão."},
    {"id": "lupulo", "name": "Lúpulo Fresco", "category": "Ingredientes", "kind": "hop", "colors": ["8f9f59", "647e44", "b3bd78", "435a32", "c8ce95", "8a7447", "d7c9a1", "b2c575"], "description": "Dois cones de lúpulo com fileiras de escamas sobrepostas, talo e folha recortada."},
    {"id": "cana_de_acucar", "name": "Cana-de-açúcar", "category": "Ingredientes", "kind": "cane", "colors": ["8b9c54", "658248", "b3b674", "48633d", "d8ce9b", "8a7448", "decea6", "b5c078"], "description": "Três canas de tamanhos diferentes, gomos em relevo, ponta cortada clara e folha lateral."},
    {"id": "cevada", "name": "Cevada", "category": "Ingredientes", "kind": "barley", "colors": ["bfa261", "927941", "dec185", "725b32", "eed89d", "a9874e", "ded0a9", "dab878"], "description": "Três espigas maduras: grãos pareados, aristas geométricas finas e hastes de comprimentos distintos."},
    {"id": "malte", "name": "Malte de Cevada", "category": "Ingredientes", "kind": "malt", "colors": ["a16c3f", "80502f", "c9985c", "593d29", "deb47b", "9b7847", "c6a777", "be9259"], "description": "Grãos tostados com sulcos e pequenos brotos sobre uma concha de papel pardo; ingrediente processado, sem espigas."},
    {"id": "bagaco_de_cana", "name": "Bagaço de Cana", "category": "Ingredientes", "kind": "bagasse", "colors": ["b0a078", "8c835e", "c6b890", "706647", "dfcda0", "8c7c51", "cabe98", "c0ae80"], "description": "Fibras secas desfiadas num feixe irregular, com talos rachados e núcleo comprimido."},
    {"id": "maconha_seda", "name": "Maconha (Seda)", "category": "Consumíveis", "kind": "herb", "colors": ["6b874d", "4c6b3d", "91a060", "364d2a", "b4be84", "8a7850", "cebf95", "95ad64"], "description": "Folha de sete pontas voxel, serrilhas em degraus, nervura e duas folhas menores presas ao mesmo talo."},
    {"id": "baseado", "name": "Baseado", "category": "Consumíveis", "kind": "joint", "colors": ["ddceb0", "bea989", "93a164", "706249", "f0e5cc", "9e8057", "e7d7b8", "bc8251"], "description": "Seda enrolada em cone, dobra longitudinal, ponta torcida, piteira e miolo vegetal aparente."},
    {"id": "cigarro_camel", "name": "Cigarro Camel", "category": "Consumíveis", "kind": "cigarette", "colors": ["e8debf", "d1be94", "c49b58", "705138", "f5eed5", "a27c43", "e2cda5", "bf9960"], "description": "Cigarro fino com filtro ocre pontilhado, dois anéis de papel, ponta de tabaco e costura lateral."},
    {"id": "cocaina", "name": "Pó Branco", "category": "Consumíveis", "kind": "white_powder", "colors": ["dfddd1", "bcbdb5", "a2b1ba", "606b72", "f2f0e4", "849197", "d2d7d2", "c5d6de"], "description": "Papel dobrado em quatro abas com pó claro em pequenos montes voxel; acabamento artesanal legível."},
    {"id": "opio", "name": "Ópio", "category": "Consumíveis", "kind": "resin", "colors": ["775439", "503b2b", "a0784c", "32281f", "ba9862", "897447", "c0aa7b", "9b7144"], "description": "Resina escura irregular, cortes, fissuras e laço de fibra clara; perfil sólido distinto dos pós."},
    {"id": "heroina", "name": "Heroína", "category": "Consumíveis", "kind": "syringe", "colors": ["bac8c8", "8ea7aa", "87a1a1", "42585d", "e4eeee", "8f9a9d", "ceddd7", "a7c5c4"], "description": "Seringa estilizada da arte existente: corpo graduado, êmbolo, abas e ponteira fina com capa clara."},
    {"id": "lsd", "name": "Selva de LSD", "category": "Consumíveis", "kind": "blotter", "colors": ["decdb0", "c1ab89", "bf8b58", "67435b", "f1dfbd", "967553", "e0cbac", "b49d77"], "description": "Cartela de papel perfurada com nove selos geométricos próprios e canto levemente dobrado."},
    {"id": "po_estelar", "name": "Pó Estelar", "category": "Consumíveis", "kind": "stardust", "colors": ["7479a9", "52577c", "b1a8cb", "31384f", "d6ccdf", "8d87a5", "bfb8c9", "c0c9d9"], "description": "Monte de pó violeta numa folha escura, fragmentos claros e um selo estrela em relevo."},
    {"id": "cogumelo_xamanico", "name": "Cogumelo Xamânico", "category": "Consumíveis", "kind": "mushroom", "colors": ["a57c9d", "745a7f", "769686", "4a3e50", "d8b4c4", "958674", "c5b9a4", "bdd0b4"], "description": "Dois cogumelos de tamanhos diferentes, chapéus em patamares, pintas, lâminas e haste creme."},
    {"id": "nevoa_do_deserto", "name": "Névoa do Deserto", "category": "Consumíveis", "kind": "desert_bottle", "colors": ["748f96", "516a74", "ba9b64", "314652", "b7d0d0", "8b8168", "dbc79f", "aec8ca"], "description": "Frasco baixo de vidro azulado fosco, pó areia dentro, rolha amarrada e etiqueta com dunas."},
    {"id": "raiz_de_sombra", "name": "Raiz de Sombra", "category": "Consumíveis", "kind": "root", "colors": ["695347", "493b35", "928071", "302929", "ae9d82", "837051", "c5b89b", "86715d"], "description": "Raiz retorcida com ramificações assimétricas, casca em estrias e cortes claros nas extremidades."},
    {"id": "cristal_de_euforia", "name": "Cristal de Euforia", "category": "Consumíveis", "kind": "crystal", "colors": ["9282ad", "655d85", "b8a4cb", "454560", "dbcced", "8f8d9e", "c7c1d4", "cfc4e6"], "description": "Drusa violeta com três prismas de alturas distintas, faces em degrau e pontas facetadas."},
    {"id": "extrato_cafeina", "name": "Extrato de Cafeína", "category": "Consumíveis", "kind": "coffee_bottle", "colors": ["a98757", "70543b", "565b43", "433427", "d7bd86", "83764d", "dbc7a2", "c29d61"], "description": "Frasco âmbar estreito com extrato escuro, tampa estriada, gargalo curto e etiqueta botânica de café."},
]
BY_ID = {entry["id"]: entry for entry in CATALOG}
ITEM_IDS = frozenset(BY_ID)
GROUPS = {"ingredientes": [e["id"] for e in CATALOG if e["category"] == "Ingredientes"],
          "consumiveis": [e["id"] for e in CATALOG if e["category"] == "Consumíveis"],
          "especiais": [e["id"] for e in CATALOG if e["category"] == "Especiais"],
          "ovos": [e["id"] for e in CATALOG if e["kind"] == "egg"]}

DISPLAY = {
    "gui": {"rotation": [25, 225, 0], "translation": [0, 3, 0], "scale": [.88] * 3},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2.8, 0], "scale": [.55] * 3},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [.90] * 3},
    "firstperson_righthand": {"rotation": [0, 165, -8], "translation": [-1, 7, -1.25], "scale": [.66] * 3},
    "firstperson_lefthand": {"rotation": [0, 165, -8], "translation": [-1, 7, -1.25], "scale": [.66] * 3},
    "thirdperson_righthand": {"rotation": [90, 0, -8], "translation": [0, 2.5, .5], "scale": [.70] * 3},
    "thirdperson_lefthand": {"rotation": [90, 0, -8], "translation": [0, 2.5, .5], "scale": [.70] * 3},
}

# Letras próprias 5x7 para o visor LED, rótulos e capa. Não depende de fonte SO.
FONT = {
    "A": ["01110","10001","10001","11111","10001","10001","10001"],
    "B": ["11110","10001","10001","11110","10001","10001","11110"],
    "C": ["01111","10000","10000","10000","10000","10000","01111"],
    "D": ["11110","10001","10001","10001","10001","10001","11110"],
    "E": ["11111","10000","10000","11110","10000","10000","11111"],
    "F": ["11111","10000","10000","11110","10000","10000","10000"],
    "G": ["01111","10000","10000","10111","10001","10001","01111"],
    "H": ["10001","10001","10001","11111","10001","10001","10001"],
    "I": ["11111","00100","00100","00100","00100","00100","11111"],
    "J": ["00111","00010","00010","00010","10010","10010","01100"],
    "K": ["10001","10010","10100","11000","10100","10010","10001"],
    "L": ["10000","10000","10000","10000","10000","10000","11111"],
    "M": ["10001","11011","10101","10101","10001","10001","10001"],
    "N": ["10001","11001","10101","10011","10001","10001","10001"],
    "O": ["01110","10001","10001","10001","10001","10001","01110"],
    "P": ["11110","10001","10001","11110","10000","10000","10000"],
    "Q": ["01110","10001","10001","10001","10101","10010","01101"],
    "R": ["11110","10001","10001","11110","10100","10010","10001"],
    "S": ["01111","10000","10000","01110","00001","00001","11110"],
    "T": ["11111","00100","00100","00100","00100","00100","00100"],
    "U": ["10001","10001","10001","10001","10001","10001","01110"],
    "V": ["10001","10001","10001","10001","10001","01010","00100"],
    "W": ["10001","10001","10001","10101","10101","10101","01010"],
    "X": ["10001","10001","01010","00100","01010","10001","10001"],
    "Y": ["10001","10001","01010","00100","00100","00100","00100"],
    "Z": ["11111","00001","00010","00100","01000","10000","11111"],
    "0": ["01110","10001","10011","10101","11001","10001","01110"],
    "1": ["00100","01100","00100","00100","00100","00100","01110"],
    "2": ["01110","10001","00001","00010","00100","01000","11111"],
    "3": ["11110","00001","00001","01110","00001","00001","11110"],
    "4": ["00010","00110","01010","10010","11111","00010","00010"],
    "5": ["11111","10000","10000","11110","00001","00001","11110"],
    "6": ["01110","10000","10000","11110","10001","10001","01110"],
    "7": ["11111","00001","00010","00100","01000","01000","01000"],
    "8": ["01110","10001","10001","01110","10001","10001","01110"],
    "9": ["01110","10001","10001","01111","00001","00001","01110"],
    " ": ["00000"] * 7,
}


def uv(material):
    x, y, size = TILES[material]
    return [(x + .5) / 8, (y + .5) / 8, (x + size - .5) / 8, (y + size - .5) / 8]


class Geometry:
    def __init__(self):
        self.parts = []

    def box(self, name, xyz0, xyz1, mat="primary", faces=None, angle=0, axis="z", origin=None):
        if any(b <= a for a, b in zip(xyz0, xyz1)):
            raise ValueError(f"Cuboide sem volume: {name}")
        part = {"name": name, "from": [round(v, 5) for v in xyz0],
                "to": [round(v, 5) for v in xyz1],
                "faces": {face: {"texture": "#atlas", "uv": uv((faces or {}).get(face, mat))} for face in FACES}}
        if angle:
            part["rotation"] = {"origin": list(origin or [(a + b) / 2 for a, b in zip(xyz0, xyz1)]),
                                "axis": axis, "angle": angle}
        self.parts.append(part)

    def centered(self, name, cx, y0, cz, width, height, depth, mat="primary", **kwargs):
        self.box(name, (cx - width / 2, y0, cz - depth / 2),
                 (cx + width / 2, y0 + height, cz + depth / 2), mat, **kwargs)

    def bevel(self, name, cx, y0, cz, width, height, depth, mat="primary", bevel=.3, **kwargs):
        # Três cuboides fazem a seção octogonal sem faces coincidentes externas.
        # Tampas e etiquetas podem ser mais finas que o chanfro habitual.
        # Limitar pelas duas dimensões preserva volume em todas as três peças.
        bevel = min(bevel, width * .25, depth * .25)
        self.centered(name + "_miolo", cx, y0, cz, width, height, depth - bevel * 2, mat, **kwargs)
        for side in (-1, 1):
            self.centered(name + f"_quina_{side}", cx, y0, cz + side * (depth / 2 - bevel / 2),
                          width - bevel * 2, height, bevel, mat, **kwargs)

    def label(self, name, cx, y0, z0, width, height, mat="label"):
        self.centered(name, cx, y0, z0, width, height, .04, "paper", faces={"north": mat})


def geometry(entry):
    g = Geometry()
    b, c, bevel = g.box, g.centered, g.bevel
    kind, item_id = entry["kind"], entry["id"]
    if kind == "book":
        b("miolo_paginas", (4.9, .5, 7.15), (11.25, 10.7, 8.75), "paper")
        for z in (6.9, 8.8):
            b("capa_couro_" + str(z), (4.25, .22, z), (11.7, 11.05, z + .26))
        bevel("lombada", 4.35, .22, 8, 1.1, 10.83, 2.15, "secondary", bevel=.18)
        for y in (1.0, 2.0, 9.0, 10.0):
            c("nervura_lombada_" + str(y), 4.3, y, 8, 1.24, .23, 2.22, "accent")
        for y in (.8, 10.35):
            for x in (4.9, 11.05):
                c("rebite_" + str((x, y)), x, y, 6.85, .25, .26, .15, "metal")
        for n in range(17):
            y = .8 + n * .56
            for x in (4.67, 11.35):
                c("costura_" + str((x, n)), x, y, 6.86, .07, .18, .055, "light")
            c("linha_pagina_" + str(n), 11.27, y, 8, .035, .025, 1.40, "light")
        g.label("capa_impressa", 8.05, 3.3, 6.85, 5.3, 4.9)
        b("marcador_ferrugem", (8.85, .0, 7.75), (9.42, .53, 8.06), "glow")
        b("tira_fecho", (11.02, 4.4, 6.78), (11.92, 5.25, 9.12), "accent")
        c("botao_fecho", 11.45, 4.64, 6.69, .35, .34, .15, "metal")
    elif kind == "remote":
        bevel("carcaca", 8, .2, 8, 4.5, 10.5, 2.2, bevel=.35)
        bevel("tampa_frontal", 8, .5, 6.87, 4.15, 9.75, .20, "secondary", bevel=.2)
        c("antena", 9.40, 10.7, 8.3, .35, 1.25, .35, "dark")
        c("antena_ponta", 9.40, 11.82, 8.3, .48, .18, .48, "metal")
        c("visor_moldura", 8, 7.1, 6.66, 3.34, 2.50, .20, "dark")
        g.label("visor_led", 8, 7.25, 6.535, 2.99, 2.15)
        c("botao_liga", 6.80, 5.8, 6.59, .78, .75, .38, "accent")
        c("botao_sinal", 9.17, 5.8, 6.59, .60, .75, .35, "glow")
        for x in (7.18, 8, 8.82):
            for n in range(3):
                c("tecla_" + str((x, n)), x, 2.0 + n * 1.02, 6.60, .55, .65, .33, "dark")
        for n in range(4):
            c("grade_" + str(n), 8, .9 + n * .15, 6.67, 1.8, .055, .10, "dark")
        b("tampa_bateria", (6.40, 1.1, 9.14), (9.60, 5.8, 9.20), "dark")
        c("trava_bateria", 8, 5.45, 9.26, .60, .22, .15, "metal")
        for x in (6.47, 9.53):
            for y in (.9, 9.75):
                c("parafuso_" + str((x, y)), x, y, 9.12, .16, .16, .11, "metal")
    elif kind == "shirt":
        b("peito", (5.3, .8, 7.25), (10.7, 9.0, 8.75))
        b("ombro_esquerdo", (3.5, 6.8, 7.4), (5.3, 9.0, 8.6), "secondary")
        b("ombro_direito", (10.7, 6.8, 7.4), (12.5, 9.0, 8.6), "secondary")
        for side in (-1, 1):
            cx = 8 + side * 4.7
            c("manga_" + str(side), cx, 5.55, 8, 1.30, 2.3, 1.2, angle=side * -22.5, origin=[8 + side * 3.0, 8, 8])
            c("punho_" + str(side), cx, 5.45, 8, 1.41, .27, 1.28, "light", angle=side * -22.5, origin=[8 + side * 3.0, 8, 8])
        b("gola_esquerda", (6.7, 8.8, 7.14), (7.15, 9.40, 8.85), "secondary")
        b("gola_direita", (8.85, 8.8, 7.14), (9.30, 9.40, 8.85), "secondary")
        b("gola_frente", (7.15, 8.75, 7.12), (8.85, 9.02, 7.48), "secondary")
        b("gola_tras", (7.15, 8.95, 8.52), (8.85, 9.40, 8.85), "secondary")
        c("barra", 8, .76, 8, 5.46, .3, 1.62, "secondary")
        g.label("estampa_matanza", 8, 3.2, 7.20, 4.6, 4.5)
        for side in (-1, 1):
            for n in range(11):
                c("costura_" + str((side, n)), 8 + side * 2.55, 1.3 + .6 * n, 7.17, .05, .12, .06, "light")
    elif kind == "egg":
        for n, (y, w, d, h) in enumerate([(0, 3.2, 3.2, .7), (.7, 4.7, 4.5, 1.1),
                                         (1.8, 5.6, 5.3, 2.0), (3.8, 5.4, 5.1, 1.8),
                                         (5.6, 4.5, 4.3, 1.5), (7.1, 3.2, 3.1, 1.1), (8.2, 1.8, 1.7, .7)]):
            bevel("casco_" + str(n), 8, y, 8, w, h, d, bevel=min(.55, w / 5))
        for n, (x, y, z, w, h) in enumerate([(6.55, 2.4, 5.33, .9, .7), (9.30, 4.35, 5.44, .7, 1.0),
                                            (7.55, 6.15, 5.82, .8, .65), (5.24, 3.3, 7.1, .07, .65),
                                            (10.72, 3.0, 8.6, .07, 1.0), (8.75, 2.1, 10.65, 1.1, .6)]):
            c("mancha_" + str(n), x, y, z, w, h, .07 if w > .1 else .70, "secondary")
        if item_id == "ovo_gago":
            c("avental", 8, 2.7, 5.23, 1.45, 1.3, .13, "accent")
            c("alca_avental", 8, 4.05, 5.42, .62, .82, .10, "accent")
            c("bolso_avental", 8, 3.1, 5.11, .81, .45, .11, "light")
        elif item_id == "ovo_traficante":
            c("capuz_base", 8, 3.05, 5.24, 1.67, 1.58, .10, "dark")
            c("capuz_topo", 8, 4.55, 5.43, 1.06, .52, .10, "dark")
            c("rosto_capuz", 8, 3.33, 5.11, .72, .98, .11, "accent")
        else:
            c("camiseta", 8, 2.8, 5.24, 1.50, 1.63, .11, "dark")
            for side in (-1, 1):
                c("manga_" + str(side), 8 + side * .93, 3.72, 5.28, .6, .5, .10, "dark")
            c("gola_camiseta", 8, 4.15, 5.12, .42, .22, .08, "light")
        for y in (1.55, 6.75):
            c("selo_snc_" + str(y), 8, y, 5.98, .78, .18, .1, "metal")
    elif kind == "coffee":
        for n, (x, y, z, a) in enumerate([(6.5, .15, 7.2, -22.5), (9.4, .25, 8.1, 22.5), (7.45, 3.35, 8.25, 0)]):
            origin = [x, y + 1.45, z]
            for k, (yy, ww, hh) in enumerate([(0, 1.12, .48), (.48, 1.8, .65), (1.13, 2.05, .75), (1.88, 1.65, .65), (2.53, .94, .45)]):
                bevel("grao_" + str((n, k)), x, y + yy, z, ww, hh, 1.20, "primary" if k % 2 else "accent", bevel=.2, angle=a, origin=origin)
            c("sulco_" + str(n), x, y + .42, z - .62, .14, 2.06, .055, "dark", angle=a, origin=origin)
            for side in (-1, 1):
                c("labio_sulco_" + str((n, side)), x + side * .13, y + .48, z - .63, .08, 1.93, .06, "secondary", angle=a, origin=origin)
    elif kind == "hop":
        for n, (cx, cy, cz) in enumerate([(6.5, .0, 7.5), (9.0, 1.0, 8.6)]):
            c("talo_cone_" + str(n), cx, cy + 4.3, cz, .25, 1.4, .28, "dark")
            for layer in range(5):
                y = cy + layer * .87
                w = [1.7, 2.55, 2.9, 2.45, 1.5][layer]
                bevel("cone_miolo_" + str((n, layer)), cx, y, cz, w, .95, w * .90, "secondary", bevel=.26)
                for face in range(4):
                    theta = math.pi / 2 * face
                    sx, sz = cx + math.sin(theta) * w * .41, cz + math.cos(theta) * w * .41
                    c("escama_" + str((n, layer, face)), sx, y + .16, sz, .70, .87, .55, "accent" if layer % 2 else "primary", angle=22.5 if face % 2 else -22.5, axis="y")
            b("folha_" + str(n), (cx + .2, cy + 4.7, cz - .06), (cx + 1.7, cy + 5.4, cz + .11), "primary", angle=-22.5)
    elif kind == "cane":
        for n, (x, z, h) in enumerate([(6.3, 7.9, 9.2), (8, 8.65, 10.75), (9.45, 7.45, 8.4)]):
            bevel("cana_" + str(n), x, 0, z, 1.2, h, 1.15, bevel=.20)
            for seg in range(4):
                yy = .5 + seg * (h - 1) / 4
                bevel("no_" + str((n, seg)), x, yy, z, 1.30, .27, 1.25, "secondary", bevel=.18)
                c("fibra_" + str((n, seg)), x - .37, yy + .31, z - .586, .10, (h - 1) / 4 - .38, .025, "accent")
            c("corte_" + str(n), x, h - .04, z, .75, .10, .73, "light")
        for n in range(4):
            c("folha_lateral_" + str(n), 10.4 + n * .31, 5.9 + n * .4, 7.7, .38, .8, .18, "secondary", angle=-22.5)
    elif kind == "barley":
        for n, (x, z, h, a) in enumerate([(6.2, 7.6, 8.7, -22.5), (8, 8.2, 10.3, 0), (9.6, 7.4, 9.3, 22.5)]):
            c("haste_" + str(n), x, 0, z, .18, h, .19, "secondary")
            for layer in range(6):
                y = h - 4.5 + layer * .57
                for side in (-1, 1):
                    xx = x + side * (.25 + (5 - layer) * .025)
                    c("grao_" + str((n, layer, side)), xx, y, z, .42, .66, .54, "accent" if layer % 2 else "primary", angle=side * 22.5)
                    c("arista_" + str((n, layer, side)), xx + side * .16, y + .44, z, .06, .86, .07, "light", angle=side * 22.5)
            c("ponta_" + str(n), x, h - .9, z, .08, 1.8, .08, "light")
    elif kind == "malt":
        b("concha_fundo", (4.8, .1, 5.4), (11.2, .35, 10.6), "paper")
        for side in (-1, 1):
            c("aba_concha_" + str(side), 8 + side * 3.15, .28, 8, .23, 1.7, 5.0, "paper", angle=side * -22.5, origin=[8 + side * 3.05, .35, 8])
        b("dobra_concha_tras", (4.90, .25, 10.50), (11.10, 1.15, 10.7), "paper")
        for n in range(15):
            x, z = 5.5 + (n % 5) * 1.16, 6.15 + (n // 5) * 1.5
            y = .42 + (n % 3) * .23
            bevel("grao_tostado_" + str(n), x, y, z, .85, .85, 1.12, "accent" if n % 3 == 0 else "primary", bevel=.16, angle=(n % 3 - 1) * 22.5, axis="y")
            c("sulco_" + str(n), x - .04, y + .80, z, .07, .035, .65, "dark")
            if n % 4 == 0:
                c("broto_" + str(n), x + .40, y + .1, z, .27, .50, .08, "light", angle=22.5)
    elif kind == "bagasse":
        bevel("miolo_comprimido", 8, .0, 8, 4.45, 1.32, 3.9, "secondary", bevel=.4)
        for n in range(30):
            x, z = 5.3 + (n % 6) * .92, 5.8 + (n // 6) * .95
            y = .2 + ((n * 7) % 5) * .27
            length = 2.0 + (n % 5) * .36
            c("fibra_" + str(n), x, y, z, .17 + (n % 2) * .06, .20 + (n % 3) * .06, length, "light" if n % 3 == 0 else "primary", angle=(n % 5 - 2) * 22.5, axis="y")
            if n % 5 == 0:
                c("fenda_talo_" + str(n), x + .22, y, z, .10, .20, length * .85, "dark", angle=22.5, axis="y")
    elif kind == "herb":
        c("talo", 8, .1, 8, .20, 7.0, .18, "secondary")
        # Sete dedos saem de um centro só; a serrilha é geometria de ambos lados.
        for n, (a, length) in enumerate([(-67.5, 2.85), (-45, 4.1), (-22.5, 5.0), (0, 5.7), (22.5, 5.0), (45, 4.1), (67.5, 2.85)]):
            # JSON nativo limita uma rotação a 45°. Folhas extremas são degraus.
            if abs(a) <= 45:
                for k in range(5):
                    w = [.45, .90, 1.05, .72, .28][k]
                    c("foliolo_" + str((n, k)), 8, 3.2 + k * length / 5, 8, w, length / 5 + .04, .23, "primary" if k % 2 else "accent", angle=a, origin=[8, 3.2, 8])
                c("nervura_" + str(n), 8, 3.2, 7.84, .09, length * .86, .055, "secondary", angle=a, origin=[8, 3.2, 8])
            else:
                sign = 1 if a > 0 else -1
                for k in range(5):
                    c("foliolo_extremo_" + str((n, k)), 8 - sign * (.5 + .5 * k), 3.10 + .26 * k, 8, .78 if k < 3 else .48, .50, .23, "primary")
        for side in (-1, 1):
            c("folha_basal_" + str(side), 8 + side * .60, 1.75, 8.14, .75, 1.50, .21, "secondary", angle=side * 45, origin=[8, 1.8, 8])
    elif kind in ("joint", "cigarette"):
        if kind == "joint":
            for n, (y, width, h) in enumerate([(0, .77, 1.40), (1.4, .87, 1.85), (3.25, 1.12, 2.2), (5.45, 1.31, 2.4), (7.85, 1.16, 1.80)]):
                bevel("seda_enrolada_" + str(n), 8, y, 8, width, h, width * .83, "paper", bevel=.15)
                c("dobra_seda_" + str(n), 8 - width * .27, y + .02, 8 - width * .42, .10, h - .04, .05, "secondary")
            bevel("piteira", 8, 0, 8, .82, .70, .68, "accent", bevel=.12)
            c("vegetal_ponta", 8, 9.56, 8, .65, .13, .53, "accent")
            c("ponta_torcida", 8.15, 9.68, 8, .45, .83, .37, "paper", angle=-22.5)
        else:
            bevel("papel", 8, 2.4, 8, .95, 7.55, .90, "paper", bevel=.17)
            bevel("filtro", 8, 0, 8, 1.0, 2.4, .95, "accent", bevel=.17)
            for y in (2.35, 2.55):
                bevel("anel_filtro_" + str(y), 8, y, 8, 1.01, .09, .96, "secondary", bevel=.15)
            c("tabaco", 8, 9.94, 8, .59, .08, .56, "dark")
            c("emenda", 7.74, 2.78, 7.54, .07, 6.97, .028, "secondary")
    elif kind in ("white_powder", "stardust"):
        mat = "paper" if kind == "white_powder" else "dark"
        b("papel_fundo", (4.9, .0, 5.3), (11.1, .17, 10.7), mat)
        for n, (x, z, axis, a, w, d) in enumerate([(4.9, 8, "z", -22.5, .20, 5.0), (11.1, 8, "z", 22.5, .20, 5.0), (8, 5.3, "x", 22.5, 6.0, .20), (8, 10.7, "x", -22.5, 6.0, .20)]):
            c("aba_papel_" + str(n), x, .12, z, w, 1.2, d, mat, angle=a, axis=axis, origin=[x, .15, z])
        for n in range(16):
            x, z = 6 + n % 4 * 1.25, 6.2 + n // 4 * 1.10
            y = .2 + ((n * 3) % 4) * .20
            bevel("po_" + str(n), x, y, z, .90, .7 + (n % 3) * .16, .90, "light" if n % 4 == 0 else "primary", bevel=.18)
        if kind == "stardust":
            # Estrela estilizada em lâminas grossas, sem partículas falsas na arte.
            c("estrela_centro", 8, 1.76, 7.4, .80, .20, .80, "glow")
            c("estrela_braço_x", 8, 1.79, 7.4, 1.9, .17, .35, "glow")
            c("estrela_braço_z", 8, 1.79, 7.4, .35, .17, 1.9, "glow")
    elif kind == "resin":
        for n, (x, y, z, w, h, d) in enumerate([(7.6, 0, 8, 3.4, 1.0, 3.8), (8.2, 1.0, 8, 4.1, 1.2, 3.5), (7.7, 2.2, 8.2, 3.1, .8, 3.0), (8.45, 3.0, 8.25, 1.9, .6, 2.0)]):
            bevel("resina_" + str(n), x, y, z, w, h, d, "primary" if n % 2 else "secondary", bevel=.35)
        for n in range(5):
            c("fissura_" + str(n), 6.75 + .58 * n, 1.2 + (n % 3) * .34, 6.235, .09, .7, .05, "dark", angle=22.5)
        c("cordel_frente", 8.2, 1.23, 6.205, 3.65, .12, .1, "light")
        c("cordel_tras", 8.2, 1.23, 9.82, 3.65, .12, .1, "light")
        for side in (-1, 1):
            c("cordel_lado_" + str(side), 8.2 + side * 1.82, 1.23, 8.0, .09, .12, 3.65, "light")
        c("no", 8.6, 1.12, 6.05, .4, .3, .22, "accent")
    elif kind == "syringe":
        bevel("cilindro", 8, 3.3, 8, 1.70, 5.5, 1.60, "primary", bevel=.27)
        c("liquido", 8, 3.55, 7.17, .84, 3.35, .06, "accent")
        for n in range(7):
            c("graduacao_" + str(n), 8.30, 3.75 + n * .62, 7.155, .33 if n % 2 else .53, .065, .05, "dark")
        c("aba_dedos", 8, 8.72, 8, 3.35, .22, 1.12, "secondary")
        c("haste_embolo", 8, 8.93, 8, .58, 2.3, .61, "metal")
        bevel("botao_embolo", 8, 11.17, 8, 2.1, .29, 1.7, "light", bevel=.22)
        bevel("encaixe", 8, 2.55, 8, .92, .76, .87, "metal", bevel=.13)
        c("ponteira", 8, .45, 8, .105, 2.15, .105, "metal")
        c("capa_ponta", 8, .15, 8, .32, 1.9, .31, "light")
        c("reflexo", 7.39, 3.6, 7.56, .09, 4.90, .09, "light")
    elif kind == "blotter":
        c("papel_cartela", 8, .1, 8, 6.8, .20, 6.8, "paper")
        for yy in range(3):
            for xx in range(3):
                n = yy * 3 + xx
                x, z = 5.79 + xx * 2.2, 5.79 + yy * 2.2
                c("selo_" + str(n), x, .305, z, 2.0, .09, 2.0, ["accent", "primary", "dark"][n % 3])
                for k in range(3):
                    c("grafismo_" + str((n, k)), x + (k - 1) * .28, .402, z + (k - 1) * .25, .35, .055, .30, "glow" if n % 2 else "light")
        for n in range(18):
            for split in (6.89, 9.11):
                c("perfuracao_x_" + str((split, n)), 4.75 + n * .38, .405, split, .08, .03, .08, "secondary")
                c("perfuracao_z_" + str((split, n)), split, .405, 4.75 + n * .38, .08, .03, .08, "secondary")
        c("canto_dobrado", 10.8, .24, 10.72, 1.05, .12, .98, "paper", angle=22.5, axis="x")
    elif kind == "mushroom":
        for n, (cx, cz, h, w) in enumerate([(6.65, 8.3, 6.8, 4.3), (9.62, 7.75, 4.1, 3.0)]):
            bevel("haste_" + str(n), cx, .0, cz, .86, h - 1.25, .94, "paper", bevel=.14)
            bevel("base_" + str(n), cx, .0, cz, 1.3, .65, 1.35, "secondary", bevel=.2)
            for level in range(3):
                scale = [1, .82, .49][level]
                bevel("chapeu_" + str((n, level)), cx, h - 1.4 + level * .52, cz, w * scale, .55, w * scale * .88, "primary" if level % 2 else "secondary", bevel=.3 * scale)
            for k in range(5):
                theta = k * math.pi * 2 / 5
                x, z = cx + math.cos(theta) * w * .29, cz + math.sin(theta) * w * .24
                c("pinta_" + str((n, k)), x, h - .72, z, .37, .06, .29, "light")
                c("lamela_" + str((n, k)), cx + (k - 2) * w * .12, h - 1.48, cz, .055, .10, w * .61, "accent")
            bevel("anel_" + str(n), cx, h - 2.7, cz, 1.35, .22, 1.40, "light", bevel=.18)
    elif kind in ("desert_bottle", "coffee_bottle"):
        desert = kind == "desert_bottle"
        w, d, h = (4.3, 3.9, 5.4) if desert else (3.6, 3.1, 6.9)
        bevel("fundo_vidro", 8, 0, 8, w * .92, .36, d * .92, "secondary", bevel=.37)
        bevel("corpo_vidro_fosco", 8, .36, 8, w, h - .86, d, "primary", bevel=.43)
        bevel("ombro", 8, h - .5, 8, w * .78, .65, d * .78, "secondary", bevel=.3)
        bevel("gargalo", 8, h + .15, 8, 1.62 if desert else 1.40, 1.17, 1.53 if desert else 1.28, "primary", bevel=.22)
        bevel("boca", 8, h + 1.22, 8, 1.78 if desert else 1.59, .22, 1.68 if desert else 1.45, "light", bevel=.2)
        g.label("rotulo", 8, 1.15, 8 - d / 2 - .045, w * .77, h * .50)
        for side in (-1, 1):
            c("reflexo_" + str(side), 8 + side * (w / 2 - .42), .64, 8 - d / 2 + .02, .12, h * .63, .05, "light")
        if desert:
            bevel("rolha", 8, h + 1.37, 8, 1.48, .82, 1.40, "accent", bevel=.17)
            for z in (7.07, 8.93):
                c("cordao_rolha_" + str(z), 8, h + .98, z, 2.0, .12, .1, "paper")
            c("no_rolha", 8.64, h + .87, 6.94, .32, .35, .21, "paper")
            c("ponta_cordao", 8.88, h + .20, 6.94, .12, .82, .14, "paper", angle=22.5)
        else:
            bevel("tampa", 8, h + 1.35, 8, 1.76, .73, 1.65, "dark", bevel=.2)
            for n in range(3):
                bevel("estria_tampa_" + str(n), 8, h + 1.42 + n * .20, 8, 1.83, .055, 1.72, "metal", bevel=.18)
            c("selo_tampa", 8.6, h + .58, 7.13, .43, 1.17, .10, "accent")
        # Janela lateral mostra o ingrediente; sem transparência que oculte o miolo.
        c("janela_conteudo", 8 + w / 2 - .09, .78, 8, .16, h * .5, d * .45, "accent" if desert else "dark")
    elif kind == "root":
        for n, (x, y, z, w, h) in enumerate([(8.2, 0, 8, 1.8, 1.5), (7.7, 1.3, 8, 2.4, 1.7), (8.2, 2.8, 8.1, 2.2, 1.8), (7.65, 4.4, 8, 1.6, 1.9), (8.2, 6.1, 8, 1.25, 1.8), (7.7, 7.7, 8, .80, 1.4)]):
            bevel("raiz_" + str(n), x, y, z, w, h, w * .70, "primary" if n % 2 else "secondary", bevel=.18)
            c("estria_" + str(n), x + w * .24, y + .13, z - w * .36, .11, h * .74, .06, "dark")
        for n, (x, y, z, a, length) in enumerate([(6.6, 1.8, 7.8, -45, 2.3), (9.4, 2.8, 8.4, 45, 2.5), (7.0, 5.2, 8.3, -45, 1.9), (9.1, 6.5, 7.9, 45, 1.65)]):
            bevel("ramo_" + str(n), x, y, z, .55, length, .53, "secondary", bevel=.08, angle=a)
            c("corte_" + str(n), x, y + length - .06, z, .32, .12, .30, "light", angle=a)
        c("corte_principal", 7.7, 9.07, 8, .48, .08, .40, "light")
    elif kind == "crystal":
        bevel("matriz", 8, 0, 8, 5.9, 1.2, 4.5, "dark", bevel=.55)
        for n, (x, z, y, w, d, a) in enumerate([(7.75, 8.1, 8.2, 2.4, 2.05, 0), (5.6, 7.55, 5.4, 1.60, 1.52, -22.5), (10.15, 8.5, 6.0, 1.8, 1.61, 22.5)]):
            bevel("prisma_" + str(n), x, .6, z, w, y - .6, d, "primary" if n == 0 else "secondary", bevel=.31, angle=a, axis="y")
            for k in range(4):
                factor = 1 - (k + 1) * .20
                bevel("ponta_" + str((n, k)), x, y + k * .30, z, w * factor, .31, d * factor, "light" if k > 1 else "accent", bevel=min(.18, w * factor / 4), angle=a, axis="y")
            c("aresta_brilho_" + str(n), x - w * .22, .95, z - d / 2 - .01, .10, y * .65, .035, "glow", angle=a, axis="y")
    else:
        raise ValueError("Forma não implementada: " + kind)
    return g.parts


def model(entry):
    display = copy.deepcopy(DISPLAY)
    if entry["kind"] in ("syringe", "remote", "cane", "barley", "book"):
        for key in ("firstperson_righthand", "firstperson_lefthand"):
            display[key]["scale"] = [.55] * 3
    if entry["kind"] in ("white_powder", "stardust", "blotter", "malt", "bagasse"):
        # Produção tem perfil baixo: inclinação permite ler a superfície na mão.
        for key in ("firstperson_righthand", "firstperson_lefthand"):
            display[key]["rotation"] = [28, 165, -8]
            display[key]["translation"] = [-1, 5.3, -1.25]
        display["gui"]["rotation"] = [55, 225, 0]
        display["gui"]["translation"] = [0, 5.5, 0]
    texture = "intoxicantes:item/catalogo/" + entry["id"]
    return {"parent": "minecraft:block/block", "gui_light": "side", "ambientocclusion": False,
            "textures": {"atlas": texture, "particle": texture + "_particula"}, "display": display, "elements": geometry(entry)}


def bitmap_text(draw, text, cx, y, fill, scale=1):
    word = text.upper()
    for source, target in (("Á", "A"), ("Ã", "A"), ("É", "E"), ("Í", "I"), ("Ó", "O"), ("Ú", "U"), ("Ç", "C")):
        word = word.replace(source, target)
    x0 = round(cx - (len(word) * 6 - 1) * scale / 2)
    for n, char in enumerate(word):
        for row, pixels in enumerate(FONT.get(char, FONT[" "])):
            for col, bit in enumerate(pixels):
                if bit == "1":
                    x, yy = x0 + (n * 6 + col) * scale, y + row * scale
                    draw.rectangle((x, yy, x + scale - 1, yy + scale - 1), fill=fill)


def atlas(entry):
    from PIL import Image, ImageDraw
    image = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    palette = dict(zip(list(TILES)[:8], entry["colors"]))
    palette.update(label=entry["colors"][6], back=entry["colors"][1])
    kind = entry["kind"]
    # Acabamento nasce na resolução final: ruído controlado, trama, estria e luz.
    for index, (material, (x0, y0, size)) in enumerate(TILES.items()):
        rgb = tuple(int(palette[material][i:i + 2], 16) for i in (0, 2, 4))
        for y in range(size):
            for x in range(size):
                grain = ((x * 137 + y * 283 + x * y * 7 + index * 47) % 17) - 8
                shade = 13 * (1 - y / max(1, size - 1)) - 8 * x / max(1, size - 1) + grain * .52
                if kind in ("book", "shirt"):
                    shade += (2.4 if x % 3 == 0 else -.8) + (1.7 if y % 4 == 0 else -1)
                elif kind in ("remote", "syringe", "coffee_bottle", "desert_bottle", "crystal"):
                    shade += 13 * math.exp(-((x / max(1, size - 1) - .20) / .08) ** 2)
                elif kind in ("cane", "barley", "bagasse", "root"):
                    shade += 4 * math.sin(x * .68) + (3 if (x + y // 9) % 11 == 0 else 0)
                elif kind in ("hop", "herb", "coffee"):
                    shade += 3 * math.sin(x * .54 + y * .22)
                image.putpixel((x0 + x, y0 + y), tuple(max(0, min(255, round(v + shade))) for v in rgb) + (255,))
    draw = ImageDraw.Draw(image)
    if kind == "book":
        draw.rectangle((2, 66, 61, 125), fill="#40563c", outline="#a98952", width=2)
        for n in range(7, 60, 6):
            draw.line((n, 68, n + 2, 68), fill="#c7b381")
            draw.line((n, 123, n + 2, 123), fill="#c7b381")
        bitmap_text(draw, "SNC", 32, 77, "#d1b776", 2)
        bitmap_text(draw, "ADVENTURES", 32, 98, "#d1b776")
        bitmap_text(draw, "GUIA", 32, 112, "#cba977")
        draw.rectangle((70, 73, 121, 118), outline="#8e815c")
    elif kind == "remote":
        draw.rectangle((0, 64, 63, 127), fill="#15242b")
        for yy in range(68, 126, 3):
            for xx in range(3, 62, 3):
                draw.point((xx, yy), fill="#294237")
        bitmap_text(draw, "SNC", 32, 74, "#b1d49b", 2)
        bitmap_text(draw, "PAINEL", 32, 96, "#9eb9a1")
        draw.rectangle((12, 111, 51, 117), outline="#a2c094")
        for xx in range(15, 43, 5):
            draw.rectangle((xx, 113, xx + 2, 115), fill="#a2c094")
    elif kind == "shirt":
        draw.rectangle((0, 64, 63, 127), fill="#29282a")
        bitmap_text(draw, "MATANZA", 32, 72, "#dacabd")
        # Caveira estilizada SNC do merch: olhos vazados, maxilar e dentes.
        draw.polygon([(20, 88), (42, 88), (48, 94), (46, 108), (39, 113), (38, 121), (25, 121), (23, 113), (17, 107), (15, 95)], fill="#c3b3a3")
        draw.rectangle((20, 97, 27, 103), fill="#29282a")
        draw.rectangle((35, 97, 42, 103), fill="#29282a")
        draw.rectangle((29, 105, 33, 110), fill="#29282a")
        for xx in (27, 32, 37):
            draw.line((xx, 116, xx, 120), fill="#29282a")
    elif kind in ("coffee_bottle", "desert_bottle"):
        draw.rectangle((2, 66, 61, 125), fill="#dbc7a2", outline="#7b6442", width=2)
        bitmap_text(draw, "SNC", 32, 71, "#57452e", 2)
        if kind == "coffee_bottle":
            bitmap_text(draw, "EXTRATO", 32, 91, "#665139")
            bitmap_text(draw, "CAFEINA", 32, 105, "#665139")
            for xx in (21, 39):
                draw.rounded_rectangle((xx - 4, 115, xx + 4, 123), radius=3, fill="#6d7847")
                draw.line((xx, 116, xx, 122), fill="#3d4b32")
        else:
            bitmap_text(draw, "NEVOA", 32, 91, "#4e6870")
            bitmap_text(draw, "DESERTO", 32, 104, "#806c43")
            draw.polygon([(7, 123), (18, 113), (34, 122), (49, 114), (57, 123)], fill="#b29862")
    if kind == "cigarette":
        x0, y0, size = TILES["accent"]
        for yy in range(3, size - 2, 4):
            for xx in range(3, size - 2, 5):
                draw.rectangle((x0 + xx, y0 + yy, x0 + xx + 1, y0 + yy + 1), fill="#987446")
    if kind == "book":
        x0, y0, size = TILES["paper"]
        for yy in range(3, size - 1, 3):
            draw.line((x0 + 2, y0 + yy, x0 + size - 3, y0 + yy), fill="#b3a07c")
    return image


def particle(entry):
    """Superfície principal nativa128: migalhas sem letras, rótulo ou transparência.

    Pintura direta pixel a pixel na resolução final, sem recortar/ampliar atlas.
    O material acompanha o objeto: couro, tecido, vegetal, metal ou mineral.
    """
    from PIL import Image
    image = Image.new("RGBA", (128, 128))
    rgb = tuple(int(entry["colors"][0][i:i + 2], 16) for i in (0, 2, 4))
    kind = entry["kind"]
    for y in range(128):
        for x in range(128):
            grain = ((x * 137 + y * 283 + x * y * 7) % 17) - 8
            shade = 13 * (1 - y / 127) - 8 * x / 127 + grain * .52
            if kind in ("book", "shirt"):
                shade += (2.4 if x % 3 == 0 else -.8) + (1.7 if y % 4 == 0 else -1)
            elif kind in ("remote", "syringe", "coffee_bottle", "desert_bottle", "crystal"):
                shade += 13 * math.exp(-((x / 127 - .20) / .08) ** 2)
            elif kind in ("cane", "barley", "bagasse", "root"):
                shade += 4 * math.sin(x * .68) + (3 if (x + y // 9) % 11 == 0 else 0)
            elif kind in ("hop", "herb", "coffee"):
                shade += 3 * math.sin(x * .54 + y * .22)
            image.putpixel((x, y), tuple(max(0, min(255, round(value + shade))) for value in rgb) + (255,))
    return image


def preview_entries():
    result = []
    for entry in CATALOG:
        output = io.BytesIO()
        atlas(entry).save(output, format="PNG")
        particle_output = io.BytesIO()
        particle(entry).save(particle_output, format="PNG")
        texture = "intoxicantes:item/catalogo/" + entry["id"]
        result.append({"id": entry["id"], "name": entry["name"], "category": entry["category"],
                       "description": entry["description"], "model": model(entry),
                       "textures": {texture: "data:image/png;base64," + base64.b64encode(output.getvalue()).decode("ascii"),
                                    texture + "_particula": "data:image/png;base64," + base64.b64encode(particle_output.getvalue()).decode("ascii")}})
    return result


def selected(only=None):
    if not only:
        return CATALOG
    ids = []
    for token in only.split(","):
        token = token.strip()
        if token in GROUPS:
            ids.extend(GROUPS[token])
        elif token in BY_ID:
            ids.append(token)
        else:
            raise ValueError("ID ou grupo desconhecido: " + token)
    return [BY_ID[key] for key in dict.fromkeys(ids)]


def outputs(entry):
    item_id = entry["id"]
    return (ASSETS / "models/item" / (item_id + ".json"),
            ASSETS / "items" / (item_id + ".json"),
            ASSETS / "textures/item/catalogo" / (item_id + ".png"),
            ASSETS / "textures/item/catalogo" / (item_id + "_particula.png"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--only", help="IDs separados por vírgula, ou ingredientes/consumiveis/especiais/ovos")
    parser.add_argument("--list", action="store_true", help="Somente listar os arquivos, sem gravação")
    parser.add_argument("--approved", action="store_true", help="A prévia recebeu aprovação explícita")
    args = parser.parse_args()
    try:
        entries = selected(args.only)
    except ValueError as error:
        parser.error(str(error))
    if args.list:
        for entry in entries:
            for path in outputs(entry):
                print(path.relative_to(ROOT).as_posix())
        return
    if not args.approved:
        parser.error("A geração de produção exige aprovação da prévia e --approved.")
    for entry in entries:
        model_path, definition_path, texture_path, particle_path = outputs(entry)
        for path in (model_path, definition_path, texture_path, particle_path):
            path.parent.mkdir(parents=True, exist_ok=True)
        model_path.write_text(json.dumps(model(entry), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        definition_path.write_text(json.dumps({"model": {"type": "minecraft:model", "model": "intoxicantes:item/" + entry["id"]}}, indent=2) + "\n", encoding="utf-8")
        atlas(entry).save(texture_path)
        particle(entry).save(particle_path)
        print(f"OK: {entry['id']} · {len(geometry(entry))} cuboides · atlas128x128 + partícula128x128 · 4 arquivos")


if __name__ == "__main__":
    main()
