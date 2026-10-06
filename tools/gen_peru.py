"""Fonte canônica do Peru e da sua moto. --preview não grava recursos do jogo.

Uso antes da aprovação: python tools/gen_peru.py --preview
Depois da aprovação: python tools/gen_peru.py --export
"""
from pathlib import Path
from PIL import Image, ImageDraw
import argparse
import base64
import io
import json
import math
import random

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/intoxicantes"
PALETA = {
    "pintura": (67, 105, 107), "creme": (211, 199, 163),
    "borracha": (38, 39, 40), "banco": (73, 52, 42),
    "aco": (104, 111, 112), "cromo": (181, 190, 189),
    "motor": (79, 81, 77), "ferrugem": (128, 75, 46),
    "farol": (228, 218, 156), "vermelho": (150, 45, 36),
    "ambar": (201, 125, 37), "painel": (25, 37, 37),
}


def textura(material):
    cor = PALETA[material]
    im = Image.new("RGBA", (128, 128))
    rnd = random.Random(material)
    for y in range(128):
        for x in range(128):
            brilho = 7 * math.cos(x * math.pi / 128) + 4 * math.sin(y / 18)
            ruido = rnd.uniform(-3, 3)
            if material in ("aco", "cromo", "motor"):
                brilho += 7 * math.sin(y * 1.8) + 10 * math.exp(-((x - 35) / 9) ** 2)
            if material in ("borracha", "banco"):
                brilho += (x % 7 == 0) * -3 + (y % 9 == 0) * 2
            if material in ("pintura", "creme") and rnd.random() < .005:
                brilho -= 28  # desgaste discreto, sem apagar a forma
            im.putpixel((x, y), tuple(max(0, min(255, int(v + brilho + ruido))) for v in cor) + (255,))
    return im


def pele():
    """Steve 64×64 normalizado, amostrado em 128×128 com detalhes de um pixel."""
    im = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    rnd = random.Random(75020)
    def area(x, y, w, h, cor):
        for yy in range(y * 2, (y + h) * 2):
            for xx in range(x * 2, (x + w) * 2):
                shade = rnd.randrange(-5, 6) + int(4 * math.cos((xx - x * 2) / max(1, w * 2) * math.pi))
                im.putpixel((xx, yy), tuple(max(0, min(255, v + shade)) for v in cor) + (255,))
    def rect(x, y, w, h, cor):
        d.rectangle((int(x * 2), int(y * 2), int((x + w) * 2 - 1), int((y + h) * 2 - 1)), fill=cor)
    skin, camisa, calca, cabelo = (186, 143, 109), (143, 134, 107), (91, 75, 59), (132, 129, 118)
    area(0, 0, 32, 16, skin)
    area(8, 0, 8, 8, cabelo)
    for x in (0, 16, 24):
        rect(x, 8, 8, 3, cabelo)
    rect(8, 8, 8, 1, cabelo)
    rect(8, 9, 1, 2, cabelo); rect(15, 9, 1, 2, cabelo)
    # Cabelo ralo, sobrancelhas cinzas, olhos pequenos, bigode e rugas.
    rect(10, 9, 4, .5, (161, 119, 91))
    for x in (9, 13):
        rect(x, 11, 2, .5, (101, 96, 83)); rect(x + .5, 11.5, 1, 1, (38, 39, 34))
        rect(x, 12.5, 2, .5, (157, 110, 83))
    rect(11.5, 12, 1, 1.5, (166, 116, 84))
    rect(10, 13.5, 4, 1, (127, 118, 102)); rect(11, 14.5, 3, .5, (111, 77, 58))
    rect(8.5, 14.5, .5, 1, (156, 110, 82)); rect(15, 14, .5, 1, (154, 106, 80))
    area(16, 16, 24, 16, camisa)
    # Gola aberta, costura, bolso e botões próprios da camisa usada.
    rect(23, 20, 2, 2.5, skin); rect(22, 20, 1, 2, (183, 173, 142))
    rect(25, 20, 1, 2, (112, 105, 83)); rect(24, 23, .5, 8.5, (115, 105, 80))
    for y in (24, 27, 30): rect(24, y, .5, .5, (195, 185, 151))
    rect(20.5, 23, 2.5, 3, (119, 111, 85)); rect(21, 23.5, 1.5, 2, (148, 138, 110))
    rect(20, 31, 8, 1, (64, 48, 36)); rect(23.5, 31, 1, .5, (162, 142, 94))
    for x, y in ((40, 16), (32, 48)):
        area(x, y, 16, 16, skin)
        rect(x, y + 4, 16, 6, camisa)
        rect(x, y + 9, 16, 1, (178, 166, 130))
        for xx in (x + 4.5, x + 8.5): rect(xx, y + 13, .5, 2, (151, 107, 77))
    for x, y in ((0, 16), (16, 48)):
        area(x, y, 16, 16, calca)
        rect(x, y + 13, 16, 3, skin)
        rect(x, y + 15, 16, 1, (47, 44, 34))
        rect(x + 5, y + 13.5, 2, .5, (50, 46, 35))
        rect(x + 4, y + 5, .5, 7, (70, 59, 47))
        rect(x + 7, y + 9, .5, 3, (115, 98, 76))
    return im


def modelo_moto():
    groups = [
        {"name": "chassis", "origin": [0, 0, 0], "rotation": [0, 0, 0]},
        {"name": "direcao", "origin": [0, 13, -10], "rotation": [0, 0, 0], "parent": "chassis"},
        {"name": "roda_frente", "origin": [0, 5, -12], "rotation": [0, 0, 0], "parent": "direcao"},
        {"name": "roda_tras", "origin": [0, 5, 12], "rotation": [0, 0, 0], "parent": "chassis"},
        {"name": "cavalete", "origin": [0, 6, 6], "rotation": [0, 0, 0], "parent": "chassis"},
    ]
    cubes = []
    nomes = {}
    def box(name, center, size, mat, group="chassis", rotation=None):
        # Cada peça repetida precisa de identidade própria para o renderer;
        # o nome não altera geometria, materiais ou articulações.
        nomes[name] = nomes.get(name, 0) + 1
        nome_unico = f"{name}_{nomes[name]}"
        c = {"name": nome_unico, "from": [round(center[i] - size[i] / 2, 4) for i in range(3)],
             "to": [round(center[i] + size[i] / 2, 4) for i in range(3)], "material": mat, "group": group}
        if rotation:
            c["rotation"], c["origin"] = rotation, center
        cubes.append(c)
    def bar(name, a, b, width, mat, group="chassis", depth=None):
        delta = [b[i] - a[i] for i in range(3)]
        length = math.sqrt(sum(v * v for v in delta))
        # Tubo voxel no plano Y/Z; X constante para todas as travessas inclinadas.
        box(name, [(a[i] + b[i]) / 2 for i in range(3)],
            [width, length, depth or width], mat, group,
            [round(math.degrees(math.atan2(delta[2], delta[1])), 3), 0, 0])
    for wheel, z in (("roda_frente", -12), ("roda_tras", 12)):
        for i in range(28):
            a = i * math.tau / 28
            for raio, thick, wide, mat in ((4.4, 1.2, 2.0, "borracha"), (3.6, .5, 1.55, "cromo")):
                box(f"{wheel}_{mat}_{i}", [0, 5 + math.cos(a) * raio, z + math.sin(a) * raio],
                    [wide, thick, 2 * raio * math.sin(math.pi / 28) + .14], mat, wheel,
                    [round(math.degrees(a), 3), 0, 0])
            box(f"{wheel}_sulco_{i}", [0, 5 + math.cos(a) * 4.94, z + math.sin(a) * 4.94],
                [1.4, .12, .23], "motor", wheel, [round(math.degrees(a), 3), 0, 0])
        for i in range(10):
            a = i * math.tau / 10
            bar(f"{wheel}_raio_{i}", [0, 5, z], [0, 5 + math.cos(a) * 3.55, z + math.sin(a) * 3.55],
                .20, "aco", wheel)
        box(f"{wheel}_cubo", [0, 5, z], [2.55, 1.9, 1.9], "motor", wheel)
        for side in (-1, 1):
            box(f"{wheel}_eixo_{side}", [side * 1.6, 5, z], [.55, .8, .8], "cromo", wheel)
    # Chassi de tubos, berço do motor e triangulação da traseira.
    for side in (-1, 1):
        x = side * 1.35
        bar("quadro_superior", [x, 13, -8], [x, 10, 10], .7, "pintura")
        bar("quadro_berco_frente", [x, 13, -8], [x, 4.3, -3], .65, "pintura")
        bar("quadro_berco_base", [x, 4.3, -3], [x, 5.7, 8], .65, "pintura")
        bar("quadro_escora", [x, 5.7, 8], [x, 10, 10], .6, "pintura")
        bar("balanca", [side * 1.6, 5.8, 4], [side * 1.6, 5, 12], .65, "aco")
        bar("garfo_cromo", [side * 1.7, 5, -12], [side * 1.7, 14.4, -8.8], .65, "cromo", "direcao")
        bar("garfo_bainha", [side * 1.7, 5, -12], [side * 1.7, 9, -10.6], .95, "pintura", "direcao")
        bar("amortecedor", [side * 2.1, 5.5, 11], [side * 2.1, 10.3, 8], .65, "cromo")
        for i in range(7):
            t = i / 7
            box("mola_traseira", [side * 2.1, 7 + t * 2.4, 10.1 - t * 1.5],
                [1.05, .23, 1.05], "aco", rotation=[32, 0, 0])
    # Motor, aletas individuais, tampas e parafusos.
    box("carter", [0, 6.3, .2], [4.1, 3.1, 4.8], "motor")
    box("cilindro", [0, 9, -1.6], [2.8, 3.2, 2.8], "motor", rotation=[-13, 0, 0])
    for i in range(7): box("aleta", [0, 7.8 + i * .42, -1.6], [3.2, .18, 3.1], "aco", rotation=[-13, 0, 0])
    for side in (-1, 1):
        box("tampa_motor", [side * 2.15, 6.5, 1.1], [.45, 2.3, 2.8], "aco")
        for y in (5.7, 7.3):
            for z in (.2, 2): box("parafuso_motor", [side * 2.42, y, z], [.16, .24, .24], "cromo")
        box("pedaleira", [side * 3.1, 5.7, 2.5], [2, .55, 1], "borracha")
        box("haste_pedaleira", [side * 2.1, 5.7, 2.5], [1.6, .35, .35], "aco")
    bar("escape_curva", [2.1, 8, -3], [2.65, 3.5, -1], .6, "ferrugem")
    bar("escape_tubo", [2.65, 3.5, -1], [2.65, 4.2, 10], .7, "cromo")
    box("silenciador", [2.7, 4.5, 10.7], [1.2, 1.3, 6.2], "cromo", rotation=[4, 0, 0])
    box("boca_escape", [2.7, 4.72, 13.85], [.75, .75, .15], "painel")
    box("corrente_capa", [-2.2, 5.4, 8.2], [.48, 1.0, 9.0], "pintura")
    for z in range(5, 12): box("elo_corrente", [-2.49, 5.9, z], [.14, .17, .55], "aco")
    box("caixa_filtro", [0, 9.1, 4], [3.7, 3.3, 3.5], "painel")
    for side in (-1, 1):
        box("tampa_lateral", [side * 2.05, 9.1, 4.3], [.3, 2.4, 3.7], "creme", rotation=[5, 0, 0])
        box("emblema_generico", [side * 2.23, 9.5, 4.3], [.08, .25, 1.2], "pintura")
    # Tanque com ombros, tampas e faixa em volume; selim separado da rabeta.
    box("tanque_base", [0, 12, -3.7], [4.5, 2.7, 8], "pintura", rotation=[7, 0, 0])
    box("tanque_ombro", [0, 13.3, -4.0], [3.55, 1.4, 6.7], "pintura", rotation=[7, 0, 0])
    for side in (-1, 1): box("faixa_tanque", [side * 2.31, 12.4, -3.6], [.13, .6, 5.3], "creme", rotation=[7, 0, 0])
    box("tampa_combustivel", [0, 14.15, -4.8], [1.2, .32, 1.2], "cromo")
    box("selim_base", [0, 11.1, 6.3], [4.5, .4, 9.7], "aco")
    box("selim_espuma", [0, 11.75, 6.3], [4.85, 1, 9.7], "banco")
    box("selim_frente", [0, 11.5, 1.4], [3.7, .8, 1.2], "banco")
    for z in (3.2, 6, 8.8): box("costura_banco", [0, 12.28, z], [4.7, .05, .10], "creme")
    for name, z, group in (("paralama_frente", -12, "direcao"), ("paralama_tras", 12, "chassis")):
        for i in range(11):
            a = (i - 5) * .14
            box(name, [0, 5 + math.cos(a) * 5.6, z + math.sin(a) * 5.6],
                [2.5, .28, .95], "creme", group, [round(math.degrees(a), 3), 0, 0])
    box("rabeta", [0, 10.5, 13], [3.3, 1, 3.3], "pintura")
    box("lanterna_suporte", [0, 10.7, 15], [2.35, 1.6, .6], "painel")
    box("lanterna_lente", [0, 10.7, 15.35], [2.0, 1.2, .2], "vermelho")
    box("placa_suporte", [0, 8.9, 15.1], [2.4, 1.5, .2], "aco", rotation=[-12, 0, 0])
    box("placa", [0, 8.9, 15.25], [2.1, 1.15, .12], "creme", rotation=[-12, 0, 0])
    for z in (11.4, 13.4): box("bagageiro_travessa", [0, 13, z], [5.0, .25, .3], "cromo")
    for side in (-1, 1):
        box("bagageiro_lateral", [side * 2.4, 13, 12.4], [.28, .28, 3.4], "cromo")
        bar("bagageiro_suporte", [side * 2.1, 10.4, 10.4], [side * 2.4, 13, 13], .3, "aco")
    # Guidão, manetes e retrovisores com haste/pivô próprios.
    box("mesa_direcao", [0, 14, -8.9], [4.6, .6, 1.3], "aco", "direcao")
    box("guidom_centro", [0, 16.8, -8.4], [7.5, .45, .5], "cromo", "direcao")
    for side in (-1, 1):
        box("punho", [side * 4.5, 17, -7.9], [2, .65, .65], "borracha", "direcao")
        box("manete", [side * 4.5, 17, -9.1], [1.7, .18, .25], "cromo", "direcao")
        bar("haste_espelho", [side * 3.2, 17, -8.4], [side * 3.2, 20.2, -9.4], .25, "cromo", "direcao")
        box("espelho_moldura", [side * 3.2, 20.35, -9.45], [2.1, 1.15, .35], "painel", "direcao")
        box("espelho_vidro", [side * 3.2, 20.35, -9.23], [1.8, .85, .09], "cromo", "direcao")
        for z, y, group in ((-10.8, 14.1, "direcao"), (14, 10.7, "chassis")):
            box("pisca_haste", [side * 2.5, y, z], [1.4, .25, .3], "aco", group)
            box("pisca_lente", [side * 3.2, y, z], [.65, .7, .65], "ambar", group)
    box("farol_caixa", [0, 14.6, -10.5], [3.25, 2.8, 2.3], "pintura", "direcao")
    box("farol_aro", [0, 14.6, -11.75], [3.5, 3, .3], "cromo", "direcao")
    box("farol_lente", [0, 14.6, -11.95], [2.9, 2.4, .12], "farol", "direcao")
    for x in (-.7, 0, .7): box("farol_friso", [x, 14.6, -12.03], [.055, 2.35, .03], "creme", "direcao")
    box("painel_caixa", [0, 16.1, -8.5], [2.6, 1.2, 1.6], "painel", "direcao", [20, 0, 0])
    box("velocimetro", [0, 16.72, -8.25], [1.9, .1, 1.1], "creme", "direcao", [20, 0, 0])
    box("ponteiro", [0, 16.81, -8.2], [.08, .07, .65], "vermelho", "direcao", [20, 0, 0])
    for side in (-1, 1):
        bar("cavalete_perna", [side * 1.3, 6, 6], [side * 1.8, .25, 7.3], .35, "aco", "cavalete")
        box("cavalete_pe", [side * 1.8, .18, 7.3], [.9, .35, .9], "aco", "cavalete")
    return {"units_per_block": 16, "groups": groups, "cubes": cubes}


def png_data(im):
    out = io.BytesIO(); im.save(out, format="PNG")
    return "data:image/png;base64," + base64.b64encode(out.getvalue()).decode()


def preview():
    rig = modelo_moto()
    cigarro = json.loads((ASSETS / "models/item/cigarro_camel.json").read_text(encoding="utf-8"))
    atlas = ASSETS / "textures/item/catalogo/cigarro_camel.png"
    dados = {"moto": rig, "materiais": {k: png_data(textura(k)) for k in PALETA},
             "pele": png_data(pele()), "cigarro": cigarro,
             "cigarroAtlas": png_data(Image.open(atlas).convert("RGBA"))}
    template = (ROOT / "tools/peru_preview.html").read_text(encoding="utf-8")
    destino = ROOT / "preview/peru.html"
    destino.write_text(template.replace("__PERU_DATA__", json.dumps(dados, ensure_ascii=False)), encoding="utf-8")
    print(f"Prévia: {destino} — {len(rig['cubes'])} volumes; recursos do jogo preservados.")


def export():
    rig = modelo_moto()
    path = ASSETS / "models/entity/moto_peru.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(rig, ensure_ascii=False, indent=2), encoding="utf-8")
    for material in PALETA:
        dest = ASSETS / f"textures/entity/moto_peru/{material}.png"
        dest.parent.mkdir(parents=True, exist_ok=True); textura(material).save(dest)
    dest = ASSETS / "textures/entity/peru.png"
    dest.parent.mkdir(parents=True, exist_ok=True); pele().save(dest)
    # Regra de skins: espelho no pack pessoal, sem sobrescrever atlas da moto.
    pack = ROOT.parent / "resourcepacks/minhas-texturas/assets/intoxicantes/textures/entity/peru.png"
    pack.parent.mkdir(parents=True, exist_ok=True); pele().save(pack)
    print(f"Peru e moto exportados ({len(rig['cubes'])} volumes).")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--preview", action="store_true")
    mode.add_argument("--export", action="store_true")
    args = parser.parse_args()
    preview() if args.preview else export()
