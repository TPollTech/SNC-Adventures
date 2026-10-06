"""Gera a instalação elétrica: quadros, cinco bitolas, cargas e instrumentos.

Saídas (sob src/main/resources/):
  assets/intoxicantes/textures/block/cabo_cobre.png            (cobre puro)
  assets/intoxicantes/blockstates/cabo_cobre_{1_5,2_5,4,6,10}mm.json (multipart!)
  assets/intoxicantes/models/block/cabo_cobre_nucleo.json
  assets/intoxicantes/models/block/cabo_cobre_braco.json
  assets/intoxicantes/items/cabo_cobre_2_5mm.json (+ model/item)
  ... tomada / soquete_teto / interruptor_simples / quadro_eletrico / multímetro
  data/intoxicantes/recipe/... (receitas e modelos de bloco/item)

Uso: python tools/gen_eletrica.py  (a partir da raiz do mod)
"""
import json
import os
import copy

from PIL import Image, ImageDraw
try:
    from tools.gen_lampada_led import data_url, display, material, texto
except ModuleNotFoundError:
    from gen_lampada_led import data_url, display, material, texto

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__))) + os.sep
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "intoxicantes")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "intoxicantes")
MIRROR = os.path.normpath(os.path.join(
    ROOT, "..", "resourcepacks", "minhas-texturas",
    "assets", "intoxicantes", "textures", "block")) + os.sep

T = 128


def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)


def png(nome, pixels):
    img = pixels if isinstance(pixels, Image.Image) else Image.new("RGBA", (T, T))
    if not isinstance(pixels, Image.Image):
        img.putdata(pixels)
    destino = os.path.join(ASSETS, "textures", "block", nome)
    os.makedirs(os.path.dirname(destino), exist_ok=True)
    img.save(destino)
    os.makedirs(MIRROR, exist_ok=True)
    img.save(os.path.join(MIRROR, nome))
    print("  " + nome)


# ------------------------------------------------------------------ texturas
def tex_cobre():
    """Cobre nu128, com torção das fibras e reflexos quentes pixelados."""
    img = material((193, 99, 56), "metal", 591)
    for y in range(T):
        for x in range(T):
            if (x + y * 2) % 19 < 2:
                img.putpixel((x, y), (136, 65, 36, 255))
            elif (x + y * 2) % 19 < 4:
                img.putpixel((x, y), (245, 156, 98, 255))
    return img


def tex_cinza_claro():
    """Atlas: cerâmica/plástico0..8; contato dourado8..12; rebaixo12..16."""
    img = material((225, 225, 211), "diffuser", 701)
    g = ImageDraw.Draw(img)
    for y in range(64, 96):
        for x in range(128):
            shade = (x * 11 + y * 3) % 7 + (12 if y % 5 == 0 else 0)
            img.putpixel((x, y), (170 + shade, 128 + shade, 64 + shade, 255))
    g.rectangle((0, 96, 127, 127), fill="#293943")
    for y in range(99, 126, 4):
        g.line((2, y, 125, y), fill="#43565d")
    return img


def tex_quadro():
    """A chapa do quadro: cinza metálico com a faixa de aviso amarela."""
    img = material((119, 135, 136), "metal", 987, "SNC")
    g = ImageDraw.Draw(img)
    g.rectangle((0, 64, 127, 95), fill="#e9bd4b", outline="#413d31", width=2)
    for x in range(-8, 128, 16):
        g.polygon(((x, 65), (x + 5, 65), (x + 35, 95), (x + 30, 95)), fill="#494735")
    # Placa de advertência nativa, sem uma foto ou logo comercial.
    g.rectangle((39, 65, 88, 94), fill="#f5d96a")
    g.polygon(((63, 67), (44, 91), (82, 91)), fill="#403a26")
    g.polygon(((63, 71), (54, 83), (62, 81), (59, 89), (72, 77), (64, 79)), fill="#f4d563")
    return img


def tex_multimetro():
    """O multímetro do eletricista (item 128): corpo amarelo de borracha,
    visor LCD com "888", seletor e pontas vermelho/preto enroladas."""
    img = material((224, 176, 40), "diffuser", 424)
    g = ImageDraw.Draw(img)
    # corpo interno cinza (a carcaça plástica)
    g.rounded_rectangle((14, 8, 114, 120), radius=14, fill="#3a3f45",
                        outline="#15171a", width=3)
    # borracha amarela de proteção (moldura)
    g.rounded_rectangle((20, 14, 108, 114), radius=11,
                        outline="#d8a51f", width=6)
    # visor LCD
    g.rectangle((32, 24, 96, 52), fill="#9fb894", outline="#15171a", width=2)
    # "888" segmentado (o teste do display)
    for i, dx in enumerate((38, 54, 70)):
        g.rectangle((dx, 30, dx + 10, 34), fill="#2c352c")      # topo
        g.rectangle((dx, 36, dx + 4, 44), fill="#2c352c")        # sup. esq.
        g.rectangle((dx + 6, 36, dx + 10, 44), fill="#2c352c")   # sup. dir.
        g.rectangle((dx, 44, dx + 10, 48), fill="#2c352c")        # meio
        g.rectangle((dx, 30, dx + 4, 38) if i == 2 else (dx + 6, 30, dx + 10, 38),
                    fill="#2c352c")
    # símbolo V~ no visor
    g.line((84, 40, 88, 30), fill="#2c352c", width=2)
    g.line((88, 30, 92, 40), fill="#2c352c", width=2)
    # seletor rotativo
    g.ellipse((46, 62, 82, 98), fill="#23262a", outline="#15171a", width=2)
    g.line((64, 80, 78, 66), fill="#e8c319", width=4)
    # bornes
    g.ellipse((30, 104, 42, 116), fill="#b3362e", outline="#15171a", width=2)
    g.ellipse((86, 104, 98, 116), fill="#1a1a1a", outline="#15171a", width=2)
    # pontas de prova enroladas (as duas fitas)
    for (x0, y0), cor in (((6, 40), "#b3362e"), ((112, 52), "#1a1a1a")):
        for t in range(0, 100, 4):
            x = x0 + (t * 7 % 9)
            y = y0 + t // 3
            g.point((x, y), fill=cor)
    return img


def png_item(nome, pixels):
    """Textura de ITEM 128 (espelha no pack como as de bloco)."""
    img = pixels if isinstance(pixels, Image.Image) else Image.new("RGBA", (T, T))
    if not isinstance(pixels, Image.Image):
        img.putdata(pixels)
    destino = os.path.join(ASSETS, "textures", "item", nome)
    os.makedirs(os.path.dirname(destino), exist_ok=True)
    img.save(destino)
    espelho = os.path.normpath(os.path.join(
        ROOT, "..", "resourcepacks", "minhas-texturas",
        "assets", "intoxicantes", "textures", "item")) + os.sep
    os.makedirs(espelho, exist_ok=True)
    img.save(os.path.join(espelho, nome))
    print("  item/" + nome)


# ------------------------------------------------------------------ helpers
def caixa(f, t, tex, faces=("north", "south", "east", "west", "up", "down"),
         uv=None):
    if uv is None:
        uv = (0, 0, 16, 8) if tex in ("#plastico", "#metal", "#quadro") else (0, 0, 16, 16)
    return {"from": list(f), "to": list(t),
            "faces": {fa: {"uv": list(uv), "texture": tex} for fa in faces}}


DIRECOES_BRACO = {
    # propriedade → from/to do braço (do núcleo até a face)
    "north": ((7.0, 7.0, 0.0), (9.0, 9.0, 7.0)),
    "south": ((7.0, 7.0, 9.0), (9.0, 9.0, 16.0)),
    "east": ((9.0, 7.0, 7.0), (16.0, 9.0, 9.0)),
    "west": ((0.0, 7.0, 7.0), (7.0, 9.0, 9.0)),
    "up": ((7.0, 9.0, 7.0), (9.0, 16.0, 9.0)),
    "down": ((7.0, 0.0, 7.0), (9.0, 7.0, 9.0)),
}


def _modelo(elements, textures, nome):
    return {"parent": "minecraft:block/block", "texture_size": [128, 128],
            "credit": "SNC — " + nome, "textures": textures,
            "elements": elements, "display": display(.8, (20, 145, 0))}


def modelo_cabo_item():
    """O fio coletado é uma bobina voxel; a rede continua com braços multipart."""
    el = []
    for z in (6.5, 7.5, 8.5):
        for f, t in [((5, 3, z), (11, 4, z + .7)), ((5, 12, z), (11, 13, z + .7)),
                     ((3, 5, z), (4, 11, z + .7)), ((12, 5, z), (13, 11, z + .7)),
                     ((4, 4, z), (5, 5, z + .7)), ((11, 4, z), (12, 5, z + .7)),
                     ((4, 11, z), (5, 12, z + .7)), ((11, 11, z), (12, 12, z + .7))]:
            el.append(caixa(f, t, "#cabo"))
    el.extend([caixa((11.7, 3.7, 8.5), (14, 4.5, 9.2), "#cabo"),
               caixa((13.2, 2.2, 8.5), (14, 3.7, 9.2), "#cabo")])
    return _modelo(el, {"cabo": "intoxicantes:block/cabo_cobre",
                        "particle": "intoxicantes:block/cabo_cobre"}, "bobina de cobre torcido")


def modelo_soquete():
    el = [caixa((5.5, 15, 5), (10.5, 16, 11), "#plastico"),
          caixa((5, 15, 5.5), (5.5, 16, 10.5), "#plastico"),
          caixa((10.5, 15, 5.5), (11, 16, 10.5), "#plastico"),
          caixa((5.5, 14.4, 5.5), (10.5, 15, 10.5), "#plastico"),
          caixa((6, 13.5, 6), (10, 14.4, 10), "#plastico"),
          caixa((6.25, 12.2, 6.25), (7, 13.5, 9.75), "#plastico"),
          caixa((9, 12.2, 6.25), (9.75, 13.5, 9.75), "#plastico"),
          caixa((7, 12.2, 6.25), (9, 13.5, 7), "#plastico"),
          caixa((7, 12.2, 9), (9, 13.5, 9.75), "#plastico"),
          caixa((7, 13.42, 7), (9, 13.5, 9), "#plastico", uv=(0, 12, 16, 16)),
          caixa((7.65, 13.32, 7.65), (8.35, 13.42, 8.35), "#plastico", uv=(0, 8, 16, 12))]
    for x in (5.65, 9.8):
        el.append(caixa((x, 14.25, 7.7), (x + .55, 14.4, 8.3), "#plastico", uv=(0, 8, 16, 12)))
    return _modelo(el, {"plastico": "intoxicantes:block/componente_cinza",
                        "particle": "intoxicantes:block/componente_cinza"}, "soquete cerâmico com contato recuado")


def modelo_interruptor(ligado=False):
    el = [caixa((3.5, 5, 14.2), (12.5, 13, 16), "#plastico"),
          caixa((3, 5.5, 14.2), (3.5, 12.5, 16), "#plastico"),
          caixa((12.5, 5.5, 14.2), (13, 12.5, 16), "#plastico"),
          caixa((5, 7, 14), (11, 11, 14.2), "#plastico", uv=(0, 12, 16, 16))]
    # Inclinação por degraus: estado powered continua usando modelo_on.
    el.append(caixa((5.4, 7.4, 13.1 if ligado else 12.7),
                    (10.6, 9, 14), "#plastico"))
    el.append(caixa((5.4, 9, 12.7 if ligado else 13.1),
                    (10.6, 10.6, 14), "#plastico"))
    el.append(caixa((7.6, 9.3, 12.67 if ligado else 13.07),
                    (8.4, 10.0, 12.7 if ligado else 13.1), "#plastico", uv=(0, 12, 16, 16)))
    for y in (5.8, 11.8):
        el.append(caixa((7.6, y, 14.08), (8.4, y + .4, 14.2), "#plastico", uv=(0, 8, 16, 12)))
    return _modelo(el, {"plastico": "intoxicantes:block/componente_cinza",
                        "particle": "intoxicantes:block/componente_cinza"}, "interruptor com tecla basculante e parafusos")


def modelo_quadro():
    el = [caixa((2, 1, 14.5), (14, 15, 16), "#quadro"),
          caixa((2, 1, 12.8), (3, 15, 14.5), "#quadro"),
          caixa((13, 1, 12.8), (14, 15, 14.5), "#quadro"),
          caixa((3, 1, 12.8), (13, 2, 14.5), "#quadro"),
          caixa((3, 14, 12.8), (13, 15, 14.5), "#quadro"),
          caixa((3, 2, 14.35), (13, 14, 14.5), "#metal", uv=(0, 12, 16, 16)),
          caixa((3.5, 13.2, 12.75), (12.5, 14, 12.8), "#quadro", faces=("north",), uv=(0, 8, 16, 12)),
          caixa((5.5, 1.15, 12.75), (10.5, 1.85, 12.8), "#quadro", faces=("north",), uv=(0, 12, 16, 16))]
    for y in (3, 8):
        el.append(caixa((3.5, y + 1.5, 13.8), (12.5, y + 2.2, 14.3), "#metal", uv=(0, 8, 16, 12)))
        for x in (4, 9):
            el.append(caixa((x, y, 12.8), (x + 3, y + 4, 13.8), "#metal"))
            el.append(caixa((x + .45, y + 1.2, 12.5), (x + 2.55, y + 2.5, 12.8), "#metal", uv=(0, 12, 16, 16)))
            el.append(caixa((x + .7, y + 1.65, 12.25), (x + 2.3, y + 2.15, 12.5), "#metal", uv=(0, 8, 16, 12)))
            for screw_y in (y + .3, y + 3.3):
                el.append(caixa((x + 1.2, screw_y, 12.7), (x + 1.8, screw_y + .4, 12.8), "#metal", uv=(0, 8, 16, 12)))
    return _modelo(el, {"quadro": "intoxicantes:block/quadro_eletrico",
                        "metal": "intoxicantes:block/componente_cinza",
                        "particle": "intoxicantes:block/quadro_eletrico"}, "quadro de chapa, barramentos e quatro disjuntores")


def modelo_tomada():
    """A tomada de parede: placa com os dois furos e o pino terra."""
    el = [caixa((0, 3, 14.4), (16, 13, 16), "#plastico"),
          caixa((0, 4, 14.4), (0.5, 12, 16), "#plastico"),
          caixa((15.5, 4, 14.4), (16, 12, 16), "#plastico"),
          # a face com os furos (recuo fino pra sombrear)
          caixa((5, 5, 14.2), (11, 11, 14.4), "#plastico", uv=(0, 8, 16, 12)),
          # os dois furos (colunas escuras)
          caixa((6.4, 6.6, 14.15), (7.2, 9.4, 14.2), "#plastico", uv=(0, 12, 16, 16)),
          caixa((8.8, 6.6, 14.15), (9.6, 9.4, 14.2), "#plastico", uv=(0, 12, 16, 16)),
          # o pino terra embaixo
          caixa((7.5, 4.6, 14.15), (8.5, 5.6, 14.2), "#plastico", uv=(0, 12, 16, 16))]
    return _modelo(el, {"plastico": "intoxicantes:block/componente_cinza",
                        "particle": "intoxicantes:block/componente_cinza"},
                   "tomada de parede com furos e pino terra")


def modelo_multimetro_item():
    """O item é flat (textura 128 pintada). O estúdio de prévia usa a PLACA
    voxel (o estúdio só desenha elementos); o jogo usa item/generated."""
    placa = {
        "credit": "v1.2.71 — multímetro (placa de prévia)",
        "parent": "minecraft:block/block",
        "texture_size": [128, 128],
        "textures": {"mm": "intoxicantes:item/multimetro",
                     "particle": "intoxicantes:item/multimetro"},
        "elements": [
            {"from": [0, 0, 7.5], "to": [16, 16, 8.5],
             "faces": {"north": {"uv": [0, 16, 16, 0], "texture": "#mm"},
                       "south": {"uv": [16, 16, 0, 0], "texture": "#mm"}}}],
        "display": display(.9, (30, 140, 0))}
    return placa


def modelo_conector():
    """O CONECTOR (v1.2.75): base parafusedada contra o bloco, colar, corpo do
    borne e a ponta de cobre por onde o cabo suspenso sai. Modelado para o
    FACING = norte: o SUPORTE fica ao sul (z=16) e o cabo sai pelo norte."""
    el = [caixa((3, 3, 14), (13, 13, 16), "#plastico"),
          caixa((4, 4, 13), (12, 12, 14), "#plastico", uv=(0, 12, 16, 16)),
          caixa((4.5, 4.5, 11), (11.5, 11.5, 13.5), "#plastico"),
          caixa((5.5, 5.5, 5), (10.5, 10.5, 11), "#plastico", uv=(0, 8, 16, 12)),
          caixa((6, 6, 4.4), (10, 10, 5), "#cabo"),
          caixa((6.5, 6.5, 1), (9.5, 9.5, 4.4), "#cabo")]
    for (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11)):
        el.append(caixa((x, y, 15.6), (x + 1, y + 1, 16), "#metal", uv=(0, 8, 16, 12)))
    return _modelo(el, {"plastico": "intoxicantes:block/componente_cinza",
                        "metal": "intoxicantes:block/componente_cinza",
                        "cabo": "intoxicantes:block/cabo_cobre",
                        "particle": "intoxicantes:block/componente_cinza"},
                   "conector de cabo suspenso")


def modelo_bobina_item():
    """A BOBINA de cabo (v1.2.75): carretel deitado no eixo X — duas abas
    circulares, eixo, o rolo de cobre enrolado e as pontas do fio soltas."""
    el = []
    for x in (2, 13):    # as duas abas (cruz = disco de blocos)
        el.append(caixa((x, 4, 4), (x + 1, 12, 12), "#plastico"))
        el.append(caixa((x, 3, 5), (x + 1, 13, 11), "#plastico"))
        el.append(caixa((x, 5, 3), (x + 1, 11, 13), "#plastico"))
    el.append(caixa((3, 7, 7), (13, 9, 9), "#plastico"))          # eixo
    el.append(caixa((5, 12, 5), (11, 13, 11), "#plastico"))       # pega do carretel
    for x in (4, 6.5, 9, 11.5):                                   # o rolo de cobre
        el.append(caixa((x, 10, 5), (x + 1.5, 12, 11), "#cabo"))
        el.append(caixa((x, 4, 5), (x + 1.5, 6, 11), "#cabo"))
        el.append(caixa((x, 5, 10), (x + 1.5, 11, 12), "#cabo"))
        el.append(caixa((x, 5, 4), (x + 1.5, 11, 6), "#cabo"))
    el.append(caixa((6, 9, 11), (10, 10, 14), "#cabo"))           # pontas do fio
    el.append(caixa((6, 6, 2), (10, 7, 5), "#cabo"))
    return _modelo(el, {"plastico": "intoxicantes:block/componente_cinza",
                        "cabo": "intoxicantes:block/cabo_cobre",
                        "particle": "intoxicantes:block/cabo_cobre"},
                   "bobina de cobre com carretel")


def preview_entries():
    textures = {"intoxicantes:block/cabo_cobre": data_url(tex_cobre()),
                "intoxicantes:block/componente_cinza": data_url(tex_cinza_claro()),
                "intoxicantes:block/quadro_eletrico": data_url(tex_quadro())}
    specs = [("cabo_cobre_2_5mm", "Bitolas de cabo (1,5 a 10 mm²)", modelo_cabo_item(), "A mesma bobina voxel nas 5 bitolas (v1.2.71); a fibra de cobre torcido128 é a textura da rede."),
             ("soquete_teto", "Soquete de teto", modelo_soquete(), "Cerâmica facetada, contato metálico recuado e fixações."),
             ("interruptor_simples", "Interruptor simples", modelo_interruptor(), "Espelho claro com cantos em degraus, tecla basculante e parafusos."),
             ("quadro_eletrico", "Quadro elétrico", modelo_quadro(), "Chapa escovada, trilhos, quatro disjuntores e advertênciaSNC."),
             ("tomada", "Tomada de parede (v1.2.71)", modelo_tomada(), "Placa com os dois furos e o pino terra; LED sutil quando energizada."),
             ("multimetro", "Multímetro (v1.2.71)", modelo_multimetro_item(), "Corpo amarelo de borracha, LCD com 888, seletor e pontas vermelho/preto."),
             ("conector_eletrico", "Conector do cabo suspenso (v1.2.75)", modelo_conector(), "Base parafusedada contra o bloco, colar, corpo do borne e ponta de cobre."),
             ("bobina_cobre_2_5mm", "Bobina de cabo (v1.2.75)", modelo_bobina_item(), "Carretel deitado com rolo de cobre e pontas do fio soltas; uma por bitola.")]
    return [{"id": id_, "name": name, "category": "Elétrica", "model": model,
             "variants": [],
             "textures": copy.deepcopy(textures), "description": desc}
            for id_, name, model, desc in specs]


BITOLAS = ("cabo_cobre_1_5mm", "cabo_cobre_2_5mm", "cabo_cobre_4mm",
           "cabo_cobre_6mm", "cabo_cobre_10mm")

# v1.2.75: as bobinas do cabo suspenso (mesma escala das bitolas).
BOBINAS = ("bobina_cobre_1_5mm", "bobina_cobre_2_5mm", "bobina_cobre_4mm",
           "bobina_cobre_6mm", "bobina_cobre_10mm")


def main():
    print("gen_eletrica: texturas")
    png("cabo_cobre.png", tex_cobre())
    png("componente_cinza.png", tex_cinza_claro())
    png("quadro_eletrico.png", tex_quadro())
    png_item("multimetro.png", tex_multimetro())

    print("gen_eletrica: cabo multipart")
    # o núcleo do cabo
    nucleo = {
        "credit": "v1.2.70 — cabo de cobre (núcleo)",
        "parent": "minecraft:block/block",
        "textures": {"cabo": "intoxicantes:block/cabo_cobre",
                     "particle": "intoxicantes:block/cabo_cobre"},
        "elements": [caixa((7.0, 7.0, 7.0), (9.0, 9.0, 9.0), "#cabo")],
    }
    wjson(os.path.join(ASSETS, "models", "block", "cabo_cobre_nucleo.json"), nucleo)
    # o braço (1 modelo, rotacionado pelo multipart? multipart suporta 1 por estado)
    bracos = {}
    for prop, (f, t) in DIRECOES_BRACO.items():
        bracos[prop] = {
            "credit": f"v1.2.70 — braço {prop}",
            "parent": "minecraft:block/block",
            "textures": {"cabo": "intoxicantes:block/cabo_cobre",
                         "particle": "intoxicantes:block/cabo_cobre"},
            "elements": [caixa(f, t, "#cabo")],
        }
        wjson(os.path.join(ASSETS, "models", "block",
                           f"cabo_cobre_braco_{prop}.json"), bracos[prop])

    # o blockstate MULTIPART (v1.2.71: um por bitola — mesmos modelos)
    for nome in BITOLAS:
        multipart = []
        # Sem condição significa todas as variantes; when:{} é inválido no26.3.
        multipart.append({"apply": {
            "model": "intoxicantes:block/cabo_cobre_nucleo"}})
        for prop in DIRECOES_BRACO:
            multipart.append({"when": {prop: "true"}, "apply": {
                "model": f"intoxicantes:block/cabo_cobre_braco_{prop}"}})
        wjson(os.path.join(ASSETS, "blockstates", f"{nome}.json"),
              {"multipart": multipart})

    # item/definition/loot das 5 bitolas (mesma bobina; item model próprio)
    for nome in BITOLAS:
        wjson(os.path.join(ASSETS, "models", "item", f"{nome}.json"),
              modelo_cabo_item())
        wjson(os.path.join(ASSETS, "items", f"{nome}.json"),
              {"model": {"type": "minecraft:model",
                         "model": f"intoxicantes:item/{nome}"}})
        wjson(os.path.join(DATA, "loot_table", "blocks", f"{nome}.json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "bonus_rolls": 0,
                       "entries": [{"type": "minecraft:item",
                                    "name": f"intoxicantes:{nome}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}],
                       "random_sequence": f"intoxicantes:blocks/{nome}"}],
        })

    # ------------------------------------------------ soquete de teto
    soquete = modelo_soquete()
    wjson(os.path.join(ASSETS, "blockstates", "soquete_teto.json"), {
        "variants": {
            "lit=false": {"model": "intoxicantes:block/soquete_teto"},
            "lit=true": {"model": "intoxicantes:block/soquete_teto"},
        }})
    wjson(os.path.join(ASSETS, "models", "block", "soquete_teto.json"), soquete)
    wjson(os.path.join(ASSETS, "models", "item", "soquete_teto.json"),
          {"parent": "intoxicantes:block/soquete_teto"})
    wjson(os.path.join(ASSETS, "items", "soquete_teto.json"),
          {"model": {"type": "minecraft:model",
                     "model": "intoxicantes:block/soquete_teto"}})
    wjson(os.path.join(DATA, "loot_table", "blocks", "soquete_teto.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item",
                                "name": "intoxicantes:soquete_teto"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "random_sequence": "intoxicantes:blocks/soquete_teto"}],
    })

    # ------------------------------------------------ interruptor simples
    interruptor = modelo_interruptor(False)
    wjson(os.path.join(ASSETS, "blockstates", "interruptor_simples.json"), {
        "variants": {
            "facing=north,powered=false": {"model": "intoxicantes:block/interruptor_simples"},
            "facing=north,powered=true": {"model": "intoxicantes:block/interruptor_simples_on"},
            "facing=south,powered=false": {"model": "intoxicantes:block/interruptor_simples", "y": 180},
            "facing=south,powered=true": {"model": "intoxicantes:block/interruptor_simples_on", "y": 180},
            "facing=east,powered=false": {"model": "intoxicantes:block/interruptor_simples", "y": 90},
            "facing=east,powered=true": {"model": "intoxicantes:block/interruptor_simples_on", "y": 90},
            "facing=west,powered=false": {"model": "intoxicantes:block/interruptor_simples", "y": 270},
            "facing=west,powered=true": {"model": "intoxicantes:block/interruptor_simples_on", "y": 270},
        }})
    # o modelo ON: a tecla afundada (mesma caixa, tecla recuada)
    interruptor_on = modelo_interruptor(True)
    wjson(os.path.join(ASSETS, "models", "block", "interruptor_simples.json"),
          interruptor)
    wjson(os.path.join(ASSETS, "models", "block", "interruptor_simples_on.json"),
          interruptor_on)
    wjson(os.path.join(ASSETS, "models", "item", "interruptor_simples.json"),
          {"parent": "intoxicantes:block/interruptor_simples"})
    wjson(os.path.join(ASSETS, "items", "interruptor_simples.json"),
          {"model": {"type": "minecraft:model",
                     "model": "intoxicantes:block/interruptor_simples"}})
    wjson(os.path.join(DATA, "loot_table", "blocks", "interruptor_simples.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item",
                                "name": "intoxicantes:interruptor_simples"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "random_sequence": "intoxicantes:blocks/interruptor_simples"}],
    })

    # ------------------------------------------------ quadro elétrico
    quadro = modelo_quadro()
    wjson(os.path.join(ASSETS, "blockstates", "quadro_eletrico.json"), {
        "variants": {
            "facing=north,open=false": {"model": "intoxicantes:block/quadro_eletrico"},
            "facing=north,open=true": {"model": "intoxicantes:block/quadro_eletrico"},
            "facing=south,open=false": {"model": "intoxicantes:block/quadro_eletrico", "y": 180},
            "facing=south,open=true": {"model": "intoxicantes:block/quadro_eletrico", "y": 180},
            "facing=east,open=false": {"model": "intoxicantes:block/quadro_eletrico", "y": 90},
            "facing=east,open=true": {"model": "intoxicantes:block/quadro_eletrico", "y": 90},
            "facing=west,open=false": {"model": "intoxicantes:block/quadro_eletrico", "y": 270},
            "facing=west,open=true": {"model": "intoxicantes:block/quadro_eletrico", "y": 270},
        }})
    wjson(os.path.join(ASSETS, "models", "block", "quadro_eletrico.json"), quadro)
    wjson(os.path.join(ASSETS, "models", "item", "quadro_eletrico.json"),
          {"parent": "intoxicantes:block/quadro_eletrico"})
    wjson(os.path.join(ASSETS, "items", "quadro_eletrico.json"),
          {"model": {"type": "minecraft:model",
                     "model": "intoxicantes:block/quadro_eletrico"}})
    wjson(os.path.join(DATA, "loot_table", "blocks", "quadro_eletrico.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item",
                                "name": "intoxicantes:quadro_eletrico"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "random_sequence": "intoxicantes:blocks/quadro_eletrico"}],
    })

    # ------------------------------------------------ tomada de parede (v1.2.71)
    tomada = modelo_tomada()
    wjson(os.path.join(ASSETS, "blockstates", "tomada.json"), {
        "variants": {
            "facing=north,lit=false": {"model": "intoxicantes:block/tomada"},
            "facing=north,lit=true": {"model": "intoxicantes:block/tomada_energizada"},
            "facing=south,lit=false": {"model": "intoxicantes:block/tomada", "y": 180},
            "facing=south,lit=true": {"model": "intoxicantes:block/tomada_energizada", "y": 180},
            "facing=east,lit=false": {"model": "intoxicantes:block/tomada", "y": 270},
            "facing=east,lit=true": {"model": "intoxicantes:block/tomada_energizada", "y": 270},
            "facing=west,lit=false": {"model": "intoxicantes:block/tomada", "y": 90},
            "facing=west,lit=true": {"model": "intoxicantes:block/tomada_energizada", "y": 90},
        }})
    wjson(os.path.join(ASSETS, "models", "block", "tomada.json"), tomada)
    # a ENERGIZADA: mesma geometria com a "lâmpada" da textura no tom do LED
    tomada_on = copy.deepcopy(tomada)
    tomada_on["credit"] = "v1.2.71 — tomada energizada (LED do pino terra)"
    for e in tomada_on["elements"]:
        if e["to"][2] <= 14.22:   # os furos e o pino: face externa acende
            for fa in e["faces"].values():
                fa["uv"] = [0, 12, 16, 16]
    wjson(os.path.join(ASSETS, "models", "block", "tomada_energizada.json"), tomada_on)
    wjson(os.path.join(ASSETS, "models", "item", "tomada.json"),
          {"parent": "intoxicantes:block/tomada"})
    wjson(os.path.join(ASSETS, "items", "tomada.json"),
          {"model": {"type": "minecraft:model",
                     "model": "intoxicantes:block/tomada"}})
    wjson(os.path.join(DATA, "loot_table", "blocks", "tomada.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item",
                                "name": "intoxicantes:tomada"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "random_sequence": "intoxicantes:blocks/tomada"}],
    })

    # ------------------------------------------------ multímetro (item flat)
    wjson(os.path.join(ASSETS, "models", "item", "multimetro.json"),
          modelo_multimetro_item())
    wjson(os.path.join(ASSETS, "items", "multimetro.json"),
          {"model": {"type": "minecraft:model",
                     "model": "intoxicantes:item/multimetro"}})

    # ---------------------------------------- conector + bobinas (v1.2.75)
    print("gen_eletrica: conector e bobinas de cabo suspenso")
    wjson(os.path.join(ASSETS, "blockstates", "conector_eletrico.json"), {
        "variants": {
            "facing=north": {"model": "intoxicantes:block/conector_eletrico"},
            "facing=east": {"model": "intoxicantes:block/conector_eletrico", "y": 90},
            "facing=south": {"model": "intoxicantes:block/conector_eletrico", "y": 180},
            "facing=west": {"model": "intoxicantes:block/conector_eletrico", "y": 270},
            "facing=up": {"model": "intoxicantes:block/conector_eletrico", "x": 270},
            "facing=down": {"model": "intoxicantes:block/conector_eletrico", "x": 90},
        }})
    wjson(os.path.join(ASSETS, "models", "block", "conector_eletrico.json"),
          modelo_conector())
    wjson(os.path.join(ASSETS, "models", "item", "conector_eletrico.json"),
          {"parent": "intoxicantes:block/conector_eletrico"})
    wjson(os.path.join(ASSETS, "items", "conector_eletrico.json"),
          {"model": {"type": "minecraft:model",
                     "model": "intoxicantes:block/conector_eletrico"}})
    wjson(os.path.join(DATA, "loot_table", "blocks", "conector_eletrico.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item",
                                "name": "intoxicantes:conector_eletrico"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "random_sequence": "intoxicantes:blocks/conector_eletrico"}],
    })
    for nome in BOBINAS:
        wjson(os.path.join(ASSETS, "models", "item", f"{nome}.json"),
              modelo_bobina_item())
        wjson(os.path.join(ASSETS, "items", f"{nome}.json"),
              {"model": {"type": "minecraft:model",
                         "model": f"intoxicantes:item/{nome}"}})

    # ------------------------------------------------ receitas (cobre real)
    print("gen_eletrica: receitas")
    wjson(os.path.join(DATA, "recipe", "cabo_cobre_1_5mm.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "group": "intoxicantes",
        "pattern": ["C"],
        "key": {"C": "minecraft:copper_ingot"},
        "result": {"id": "intoxicantes:cabo_cobre_1_5mm", "count": 4},
    })
    wjson(os.path.join(DATA, "recipe", "cabo_cobre_2_5mm.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "group": "intoxicantes",
        "pattern": ["CC", "CC"],
        "key": {"C": "minecraft:copper_ingot"},
        "result": {"id": "intoxicantes:cabo_cobre_2_5mm", "count": 8},
    })
    # a escala das bitolas: 4 cabos finos viram 1 grosso (o fio encordoado)
    wjson(os.path.join(DATA, "recipe", "cabo_cobre_4mm.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "group": "intoxicantes",
        "pattern": ["CC", "CC"],
        "key": {"C": "intoxicantes:cabo_cobre_2_5mm"},
        "result": {"id": "intoxicantes:cabo_cobre_4mm", "count": 4},
    })
    wjson(os.path.join(DATA, "recipe", "cabo_cobre_6mm.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "group": "intoxicantes",
        "pattern": ["CC", "CC"],
        "key": {"C": "intoxicantes:cabo_cobre_4mm"},
        "result": {"id": "intoxicantes:cabo_cobre_6mm", "count": 3},
    })
    wjson(os.path.join(DATA, "recipe", "cabo_cobre_10mm.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "group": "intoxicantes",
        "pattern": ["CC", "CC"],
        "key": {"C": "intoxicantes:cabo_cobre_6mm"},
        "result": {"id": "intoxicantes:cabo_cobre_10mm", "count": 2},
    })
    wjson(os.path.join(DATA, "recipe", "tomada.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "group": "intoxicantes",
        "pattern": ["C", "N"],
        "key": {"C": "minecraft:copper_ingot", "N": "minecraft:iron_nugget"},
        "result": {"id": "intoxicantes:tomada", "count": 2},
    })
    wjson(os.path.join(DATA, "recipe", "multimetro.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "group": "intoxicantes",
        "pattern": ["G", "R", "I"],
        "key": {"G": "minecraft:glass", "R": "minecraft:redstone",
                "I": "minecraft:iron_ingot"},
        "result": {"id": "intoxicantes:multimetro", "count": 1},
    })
    wjson(os.path.join(DATA, "recipe", "soquete_teto.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "group": "intoxicantes",
        "pattern": ["C", "S"],
        "key": {"C": "minecraft:copper_ingot", "S": "minecraft:smooth_stone_slab"},
        "result": {"id": "intoxicantes:soquete_teto", "count": 2},
    })
    wjson(os.path.join(DATA, "recipe", "interruptor_simples.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "group": "intoxicantes",
        "pattern": ["C", "B"],
        "key": {"C": "minecraft:copper_ingot", "B": "minecraft:stone_button"},
        "result": {"id": "intoxicantes:interruptor_simples", "count": 2},
    })
    wjson(os.path.join(DATA, "recipe", "quadro_eletrico.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "group": "intoxicantes",
        "pattern": ["III", "ICW", "III"],
        "key": {"I": "minecraft:iron_ingot", "C": "minecraft:copper_ingot",
                "W": "intoxicantes:cabo_cobre_2_5mm"},
        "result": {"id": "intoxicantes:quadro_eletrico", "count": 1},
    })

    wjson(os.path.join(DATA, "recipe", "conector_eletrico.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "group": "intoxicantes",
        "pattern": [" C ", "CIC", " C "],
        "key": {"C": "minecraft:copper_ingot", "I": "minecraft:iron_nugget"},
        "result": {"id": "intoxicantes:conector_eletrico", "count": 4},
    })
    # a bobina fina nasce do cobre; as grossas reembobinam o cabo da bitola menor
    receitas_bobina = {
        "bobina_cobre_1_5mm": {"C": "minecraft:copper_ingot",
                               "I": "minecraft:iron_nugget"},
        "bobina_cobre_2_5mm": {"C": "minecraft:copper_ingot",
                               "I": "intoxicantes:cabo_cobre_1_5mm"},
        "bobina_cobre_4mm": {"C": "minecraft:copper_ingot",
                             "I": "intoxicantes:cabo_cobre_2_5mm"},
        "bobina_cobre_6mm": {"C": "minecraft:copper_ingot",
                             "I": "intoxicantes:cabo_cobre_4mm"},
        "bobina_cobre_10mm": {"C": "minecraft:copper_ingot",
                              "I": "intoxicantes:cabo_cobre_6mm"},
    }
    for nome, chave in receitas_bobina.items():
        wjson(os.path.join(DATA, "recipe", f"{nome}.json"), {
            "type": "minecraft:crafting_shaped",
            "category": "misc",
            "group": "intoxicantes",
            "pattern": ["CI", "IC"],
            "key": chave,
            "result": {"id": f"intoxicantes:{nome}", "count": 2},
        })

    print("gen_eletrica: OK (5 texturas + 5 bitolas multipart + equipamentos "
          "+ multímetro + conector/bobinas + receitas)")


if __name__ == "__main__":
    main()
