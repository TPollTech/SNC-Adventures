#!/usr/bin/env python3
"""Fonte canônica dos seis saquinhos de sementes 3D do SNC Adventures.

Não executar antes de aprovar preview/sementes.html. O gerador escreve somente
models/item/{id}.json, items/{id}.json e textures/item/sementes/{id}.png.
Os IDs, comportamento de plantar, receitas, preços e sprites manuais antigos
ficam preservados. O atlas vive em diretório próprio para não colidir com o pack.

Uso após aprovação: python tools/gen_sementes.py --list
                    python tools/gen_sementes.py
                    python tools/gen_sementes.py --only semente_uva
                    python tools/gen_sementes.py --sync-preview
16 unidades = um bloco; +Y para cima; X/Z=8 no centro e base Y=0.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/intoxicantes"

# A prévia contém esta mesma descrição declarativa; --sync-preview a sincroniza
# depois de uma alteração aprovada sem criar fonte ou modelo paralelo.
CATALOGO_JSON = r'''[
  {"id":"semente_maconha","name":"Maconha","symbol":"folha","width":6.5,"depth":4.6,"height":8.8,"band":"788753","accent":"98ac69","ink":"495633","description":"Folha de sete pontas bordada, faixa oliva e saco largo de tecido cru."},
  {"id":"semente_lupulo","name":"Lúpulo","symbol":"cone","width":6.0,"depth":4.9,"height":9.0,"band":"758c53","accent":"b2c079","ink":"4d6037","description":"Cone de lúpulo com escamas sobrepostas, faixa musgo e corpo mais profundo."},
  {"id":"semente_uva","name":"Uva","symbol":"cacho","width":6.7,"depth":4.7,"height":8.7,"band":"8b6b91","accent":"ac87b5","ink":"63506b","description":"Cacho com pequenas uvas e folha recortada, faixa ameixa e base bojuda."},
  {"id":"semente_cafe","name":"Café","symbol":"cafe","width":5.9,"depth":4.4,"height":9.3,"band":"8b6050","accent":"c47959","ink":"674837","description":"Ramo com cerejas de café e folhas opostas, faixa terracota e perfil estreito."},
  {"id":"semente_papoula","name":"Papoula","symbol":"flor","width":6.2,"depth":4.3,"height":8.9,"band":"af6860","accent":"d59870","ink":"7c4d48","description":"Flor de papoula com centro escuro, faixa coral e costura clara aparente."},
  {"id":"semente_cevada","name":"Cevada","symbol":"espiga","width":6.8,"depth":4.8,"height":9.6,"band":"b29a5e","accent":"d2b877","ink":"76623e","description":"Espiga com grãos e aristas, faixa palha e o saquinho mais alto da coleção."}
]'''
CATALOG = json.loads(CATALOGO_JSON)

PROFILE = [
    [0.00, 0.05, .74, .74], [.05, .13, .90, .91],
    [.13, .35, 1.00, 1.00], [.35, .57, .99, 1.00],
    [.57, .69, .86, .89], [.69, .78, .63, .66],
    [.78, .89, .38, .40], [.89, 1.00, .49, .47],
]
TILES = {
    "cloth": (0, 0, 64, 64), "fold": (64, 0, 32, 32),
    "band": (96, 0, 32, 32), "cord": (64, 32, 16, 32),
    "thread": (80, 32, 16, 32), "seal": (96, 32, 32, 32),
    "label": (0, 64, 64, 64), "back": (64, 64, 64, 64),
}
FACES = ("north", "south", "east", "west", "up", "down")
DISPLAY = {
    "gui": {"rotation": [25, 225, 0], "translation": [0, 3, 0], "scale": [.92] * 3},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2.8, 0], "scale": [.60] * 3},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [.95] * 3},
    # O jogo espelha X da translação e Y/Z da rotação na mão esquerda.
    # A raiz do modelo fica em Y=0: subir sete unidades mantém o saco inteiro
    # acima do HUD; yaw165 apresenta o bordado da face north para a câmera.
    "firstperson_righthand": {"rotation": [0, 165, -8], "translation": [-1, 7, -1.25], "scale": [.70] * 3},
    "firstperson_lefthand": {"rotation": [0, 165, -8], "translation": [-1, 7, -1.25], "scale": [.70] * 3},
    # ItemInHandLayer aplica X=-90 antes do modelo. A compensação X=90 deixa
    # o saquinho vertical, com a etiqueta voltada à frente do personagem.
    "thirdperson_righthand": {"rotation": [90, 0, -8], "translation": [0, 2.5, .5], "scale": [.75] * 3},
    "thirdperson_lefthand": {"rotation": [90, 0, -8], "translation": [0, 2.5, .5], "scale": [.75] * 3},
}

# Símbolos botânicos bordados; cada espécie se reconhece pelo desenho.
SYMBOLS = {
    "folha": [".......g.......", ".......g.......", "...g...g...g...",
              "....g..g..g....", ".g...g.g.g...g.", "..gg.ggggg.gg..",
              "...ggggggggg...", "....ggggggg....", ".....ggggg.....",
              "...ggggggggg...", "..gg...b...gg..", ".......b.......", ".......b......."],
    "cone": [".......b.......", "......ggg......", ".....ggggg.....",
             "....ggGggGg....", "...ggGggGggg...", "...gGggGggGg...",
             "...ggGggGggg...", "...gGggGggGg...", "....ggGggGg....",
             ".....ggGgg.....", "......ggg......", ".......g......."],
    "cacho": ["......bb.gg....", "......b.gggg...", "....bbb..gg....",
              "...FfF.FfF.....", "...FFF.FFF.....", "..FfF.FfF.FfF..",
              "..FFF.FFF.FFF..", "...FfF.FfF.....", "...FFF.FFF.....",
              "....FfF.FfF....", "....FFF.FFF....", "......FfF......", "......FFF......"],
    "cafe": ["......b........", "..ggg.b.ggg....", ".ggGggbggGgg...",
             "..ggg.b.ggg....", ".....FfF.......", ".....FFF.......",
             "...gg.b.gg.....", "..ggGgbgGgg....", "...gg.b.gg.....",
             ".....FfF.FfF...", ".....FFF.FFF...", "......b........", "......b........"],
    "flor": ["....FfF.FfF....", "...FFFFFFFFF...", "...FFFFFFFFF...",
             "..FfFFFFFfFFF..", "..FFFFbbbFFFF..", "..FFFFbWbFFFF..",
             "...FFFbbbFFF...", "....FFFFFFF....", ".....FFFFF.....",
             ".......b.......", "....gg.b.......", "...ggGgb.......", ".......b......."],
    "espiga": [".......b.......", "...b...b...b...", "....b..b..b....",
               ".....FfbFf.....", "....FFfbFFf....", ".....FfbFf.....",
               "...b.FfbFf.b...", "....FFfbFFf....", ".....FfbFf.....",
               "....FFfbFFf....", ".....FfbFf.....", "......bbb......", ".......b......."],
}

# Fonte bordada 3×5; inclusive o selo SNC usa bitmap próprio e igual na prévia.
FONT = {
    "A": ["010", "101", "111", "101", "101"], "B": ["110", "101", "110", "101", "110"],
    "C": ["011", "100", "100", "100", "011"], "D": ["110", "101", "101", "101", "110"],
    "E": ["111", "100", "110", "100", "111"], "F": ["111", "100", "110", "100", "100"],
    "G": ["011", "100", "101", "101", "011"], "H": ["101", "101", "111", "101", "101"],
    "I": ["111", "010", "010", "010", "111"], "J": ["001", "001", "001", "101", "010"],
    "K": ["101", "101", "110", "101", "101"], "L": ["100", "100", "100", "100", "111"],
    "M": ["101", "111", "111", "101", "101"], "N": ["101", "111", "111", "111", "101"],
    "O": ["010", "101", "101", "101", "010"], "P": ["110", "101", "110", "100", "100"],
    "Q": ["010", "101", "101", "111", "011"], "R": ["110", "101", "110", "101", "101"],
    "S": ["011", "100", "010", "001", "110"], "T": ["111", "010", "010", "010", "010"],
    "U": ["101", "101", "101", "101", "111"], "V": ["101", "101", "101", "101", "010"],
    "W": ["101", "101", "111", "111", "101"], "X": ["101", "101", "010", "101", "101"],
    "Y": ["101", "101", "010", "010", "010"], "Z": ["111", "001", "010", "100", "111"],
    " ": ["000"] * 5,
}


def uv(material: str) -> list[float]:
    x, y, w, h = TILES[material]
    return [(x + 1) / 8, (y + 1) / 8, (x + w - 1) / 8, (y + h - 1) / 8]


def geometry(seed: dict) -> list[dict]:
    parts = []
    width, depth, height = seed["width"], seed["depth"], seed["height"]

    def box(name, x0, y0, z0, x1, y1, z1, material, faces=None, rotation=None):
        if min(x1 - x0, y1 - y0, z1 - z0) <= 0:
            raise ValueError(f"Cuboide inválido: {seed['id']}/{name}")
        part = {"name": name, "from": [round(x0, 5), round(y0, 5), round(z0, 5)],
                "to": [round(x1, 5), round(y1, 5), round(z1, 5)], "material": material}
        if faces:
            part["face_materials"] = faces
        if rotation:
            part["rotation"] = rotation
        parts.append(part)

    def solid(name, y0, y1, w, d, material):
        bevel = min(w, d) * .13
        box(name + "_miolo", 8 - w / 2, y0, 8 - d / 2 + bevel,
            8 + w / 2, y1, 8 + d / 2 - bevel, material)
        for sign in (-1, 1):
            z0 = 8 - d / 2 if sign < 0 else 8 + d / 2 - bevel
            box(name + "_borda_" + str(sign), 8 - w / 2 + bevel, y0, z0,
                8 + w / 2 - bevel, y1, z0 + bevel, material)

    for n, (y0, y1, w, d) in enumerate(PROFILE):
        solid("tecido_%d" % n, y0 * height, y1 * height, w * width, d * depth,
              "fold" if n >= 5 else "cloth")

    # Faixa tecida em relevo, frente/verso e laterais, sem trocar só a cor do saco.
    for sign in (-1, 1):
        z0 = 8 - depth / 2 - .035 if sign < 0 else 8 + depth / 2 + .005
        box("faixa_" + str(sign), 8 - width * .37, height * .135, z0,
            8 + width * .37, height * .21, z0 + .03, "band")

    # Duas etiquetas de lona; o verso tem conteúdo próprio, sem textura espelhada.
    box("rotulo_frente", 8 - width * .32, height * .225, 8 - depth / 2 - .075,
        8 + width * .32, height * .565, 8 - depth / 2 - .04, "cloth", {"north": "label"})
    box("rotulo_verso", 8 - width * .29, height * .225, 8 + depth / 2 + .04,
        8 + width * .29, height * .565, 8 + depth / 2 + .075, "cloth", {"south": "back"})

    # Pregas geométricas no ombro, uma costura de cada lado do corpo.
    for side in (-1, 1):
        for n in range(9):
            y = height * (.16 + n * .042)
            x = 8 + side * (width / 2 - .025)
            box("ponto_lateral_%d_%d" % (side, n), x - .033, y, 7.85,
                x + .033, y + .095, 8.15, "thread")
        for n in range(3):
            x = 8 + side * (.58 + n * .43)
            z = 8 - depth * (.27 - n * .015)
            box("prega_%d_%d" % (side, n), x - .065, height * .61, z - .035,
                x + .065, height * (.685 - n * .016), z + .075, "fold")

    neck_width, neck_depth = width * .38, depth * .40
    cord_y = height * .823
    for sign in (-1, 1):
        z = 8 + sign * (neck_depth / 2 + .02)
        box("cordao_horizontal_" + str(sign), 8 - neck_width / 2 - .055, cord_y, z - .055,
            8 + neck_width / 2 + .055, cord_y + .14, z + .055, "cord")
        x = 8 + sign * (neck_width / 2 + .02)
        box("cordao_lateral_" + str(sign), x - .055, cord_y, 8 - neck_depth / 2,
            x + .055, cord_y + .14, 8 + neck_depth / 2, "cord")
    front_z = 8 - neck_depth / 2 - .17
    box("no_do_cordao", 7.75, cord_y - .10, front_z - .11,
        8.25, cord_y + .30, front_z + .18, "cord")
    for sign in (-1, 1):
        cx = 8 + sign * .65
        box("laco_topo_" + str(sign), cx - .48, cord_y + .18, front_z - .08,
            cx + .48, cord_y + .29, front_z + .04, "cord")
        box("laco_base_" + str(sign), cx - .48, cord_y - .26, front_z - .08,
            cx + .48, cord_y - .15, front_z + .04, "cord")
        box("laco_borda_" + str(sign), cx + sign * .4 - .055, cord_y - .19, front_z - .08,
            cx + sign * .4 + .055, cord_y + .22, front_z + .04, "cord")
        box("ponta_do_laco_" + str(sign), 8 + sign * .20 - .055, cord_y - .92, front_z - .05,
            8 + sign * .20 + .055, cord_y - .06, front_z + .06, "cord",
            rotation={"origin": [8, cord_y, front_z], "axis": "z", "angle": sign * 22.5})
    return parts


def item_model(seed: dict) -> dict:
    elements = []
    for part in geometry(seed):
        faces = part.get("face_materials", {})
        element = {"name": part["name"], "from": part["from"], "to": part["to"],
                   "faces": {face: {"uv": uv(faces.get(face, part["material"])), "texture": "#atlas"}
                             for face in FACES}}
        if "rotation" in part:
            element["rotation"] = part["rotation"]
        elements.append(element)
    texture = "intoxicantes:item/sementes/" + seed["id"]
    return {"parent": "minecraft:block/block", "gui_light": "side", "ambientocclusion": False,
            "textures": {"atlas": texture, "particle": texture}, "display": DISPLAY, "elements": elements}


def atlas(seed: dict):
    from PIL import Image, ImageDraw

    image = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    colors = {"cloth": "bfa978", "fold": "ac9669", "band": seed["band"],
              "cord": "9b8050", "thread": "e2cf9d", "seal": seed["accent"],
              "label": "e8dabc", "back": "ded0b0"}
    for index, (material, (x0, y0, w, h)) in enumerate(TILES.items()):
        rgb = tuple(int(colors[material][i:i + 2], 16) for i in (0, 2, 4))
        for y in range(h):
            for x in range(w):
                grain = ((x * 193 + y * 389 + x * y * 17 + index * 61) % 19) - 9
                weave = (4 if (x % 3 == 0) else -2) + (3 if y % 4 == 0 else 0)
                shade = 9 * (1 - y / max(1, h - 1)) - 7 * x / max(1, w - 1) + grain * .45 + weave
                image.putpixel((x0 + x, y0 + y), tuple(max(0, min(255, round(c + shade))) for c in rgb) + (255,))
    draw = ImageDraw.Draw(image)

    def text(word, cx, y, scale, fill):
        word = word.upper().replace("Ú", "U").replace("É", "E")
        x0 = int(cx - (len(word) * 4 - 1) * scale / 2)
        for n, ch in enumerate(word):
            for yy, row in enumerate(FONT.get(ch, FONT[" "])):
                for xx, px in enumerate(row):
                    if px == "1":
                        x, yp = x0 + (n * 4 + xx) * scale, y + yy * scale
                        draw.rectangle((x, yp, x + scale - 1, yp + scale - 1), fill=fill)

    for tile in ("label", "back"):
        x0, y0, _, _ = TILES[tile]
        draw.rectangle((x0 + 2, y0 + 2, x0 + 61, y0 + 61), outline="#bba478", width=1)
        for n in range(4, 60, 5):
            draw.line((x0 + n, y0 + 4, x0 + n + 2, y0 + 4), fill="#f4e8ce")
            draw.line((x0 + n, y0 + 59, x0 + n + 2, y0 + 59), fill="#f4e8ce")
        text("SNC", x0 + 32, y0 + 7, 3, "#55472f")
    symbol = SYMBOLS[seed["symbol"]]
    palette = {"g": "#" + seed["accent"], "G": "#" + seed["ink"],
               "F": "#" + seed["band"], "f": "#" + seed["accent"], "b": "#77613f", "W": "#efdaa2"}
    symbol_width = max(map(len, symbol))
    ox, oy = int(32 - symbol_width), 64 + 25
    for yy, row in enumerate(symbol):
        for xx, ch in enumerate(row):
            if ch in palette:
                x, y = ox + xx * 2, oy + yy * 2
                draw.rectangle((x, y, x + 1, y + 1), fill=palette[ch])
    text(seed["name"], 32, 64 + 55, 1, "#" + seed["ink"])
    text("ADVENTURES", 96, 64 + 27, 1, "#" + seed["ink"])
    text("SEMENTES", 96, 64 + 38, 1, "#6f6146")
    for y in (64 + 49, 64 + 53):
        draw.line((78, y, 113, y), fill="#b5a17b", width=1)
    return image


def outputs(seeds):
    for seed in seeds:
        yield ASSETS / "models/item" / (seed["id"] + ".json")
        yield ASSETS / "items" / (seed["id"] + ".json")
        yield ASSETS / "textures/item/sementes" / (seed["id"] + ".png")


def sync_preview():
    path = ROOT / "preview/sementes.html"
    source = path.read_text(encoding="utf-8")
    content = json.dumps({"seeds": CATALOG, "profile": PROFILE, "tiles": TILES,
                          "symbols": SYMBOLS, "font": FONT, "display": DISPLAY}, ensure_ascii=False, indent=2)
    updated, count = re.subn(r'(?<=<!-- SEMENTES_CATALOGO_BEGIN -->)[\s\S]*?(?=<!-- SEMENTES_CATALOGO_END -->)',
                           lambda _: '\n<script type="application/json" id="catalog">\n' + content + '\n</script>\n', source)
    if count != 1:
        raise ValueError("A prévia precisa ter exatamente um bloco SEMENTES_CATALOGO")
    path.write_text(updated, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--only", choices=[seed["id"] for seed in CATALOG])
    parser.add_argument("--list", action="store_true", help="Listar caminhos sem gerar")
    parser.add_argument("--sync-preview", action="store_true", help="Sincronizar dados da prévia após aprovação")
    args = parser.parse_args()
    if args.sync_preview:
        sync_preview()
        print("OK: catálogo da prévia sincronizado; nenhum recurso de produção gerado")
        return
    seeds = [seed for seed in CATALOG if not args.only or seed["id"] == args.only]
    if args.list:
        for path in outputs(seeds):
            print(path.relative_to(ROOT).as_posix())
        return
    for seed in seeds:
        model, definition, texture = list(outputs([seed]))
        for path in (model, definition, texture):
            path.parent.mkdir(parents=True, exist_ok=True)
        model.write_text(json.dumps(item_model(seed), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        definition.write_text(json.dumps({"model": {"type": "minecraft:model", "model": "intoxicantes:item/" + seed["id"]}}, indent=2) + "\n", encoding="utf-8")
        atlas(seed).save(texture)
        print(f"OK: {seed['id']} · {len(geometry(seed))} cuboides · atlas128x128")


if __name__ == "__main__":
    main()
