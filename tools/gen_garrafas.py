#!/usr/bin/env python3
"""Fonte canônica dos modelos e atlas das bebidas prontas do SNC Adventures.

Executar APENAS depois da aprovação da prévia preview/garrafas.html.
O catálogo garrafas.json descreve perfis, rótulos e materiais compartilhados com
a prévia. Este gerador escreve somente os oito modelos, suas definições de item
e atlas em textures/item/garrafas/ (sem colidir com sprites do pack antigo).
X/Z=8 no centro, base Y=0; 16 unidades equivalem a um bloco. A decoração usa
ItemDisplayContext.NONE. Não há modelo de bloco ou item decorativo duplicado.
"""
from __future__ import annotations

import json
import copy
import base64
import io
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
CATALOG_PATH = Path(__file__).with_name("garrafas.json")
ASSETS = ROOT / "src/main/resources/assets/intoxicantes"
MATERIALS = ["glass", "edge", "liquid", "cap", "label", "ink", "gold", "seal",
             "highlight", "foam", "cork", "back_label"]
TILES = {
    "glass": (0, 0, 32), "edge": (32, 0, 32), "liquid": (64, 0, 32), "cap": (96, 0, 32),
    "ink": (0, 32, 16), "gold": (16, 32, 16), "seal": (32, 32, 16),
    "highlight": (48, 32, 16), "foam": (64, 32, 16), "cork": (80, 32, 16),
    "label": (0, 64, 64), "back_label": (64, 64, 64)
}
FACES = ("north", "south", "east", "west", "up", "down")


def load_catalog():
    return json.loads(CATALOG_PATH.read_text(encoding="utf-8"))


def uv(material):
    # Espaço UV NATIVO do Minecraft: 0..16 por textura (NÃO é fração 0..1 de arquivo).
    # Fração real do pixel p = coords/128; MC multiplica por 16 ao ler => p*16/8 = p/8.
    # Logo /8 É o fator correto (1 unidade de atlas = 16px). Borda evita bleeding;
    # rótulos têm 64px de detalhe dentro do atlas 128.
    x, y, size = TILES[material]
    return [(x+1)/8, (y+1)/8, (x+size-1)/8, (y+size-1)/8]


def geometry(drink, catalog):
    """Cuboides sem rotações livres: compatíveis com o modelo JSON nativo."""
    parts = []
    wall = catalog["wall"]

    def box(name, x0, y0, z0, x1, y1, z1, mat):
        if min(x1-x0, y1-y0, z1-z0) <= 0:
            raise ValueError(f"Cuboide inválido: {drink['id']}/{name}")
        parts.append({"name": name, "from": [round(x0, 5), round(y0, 5), round(z0, 5)],
                      "to": [round(x1, 5), round(y1, 5), round(z1, 5)],
                      "material": mat})

    def solid(name, y0, y1, w, d, mat):
        b = min(w, d) * catalog["bevel"]
        box(name+"_miolo", 8-w/2, y0, 8-d/2+b, 8+w/2, y1, 8+d/2-b, mat)
        for side in (-1, 1):
            z = 8-d/2 if side < 0 else 8+d/2-b
            box(name+f"_borda_{side}", 8-w/2+b, y0, z, 8+w/2-b, y1, z+b, mat)

    def ring(name, y0, y1, w, d, mat):
        b = min(w, d) * catalog["bevel"]
        for side in (-1, 1):
            z = 8-d/2 if side < 0 else 8+d/2-wall
            x = 8-w/2 if side < 0 else 8+w/2-wall
            box(name+f"_frente_{side}", 8-w/2+b, y0, z, 8+w/2-b, y1, z+wall, mat)
            box(name+f"_lado_{side}", x, y0, 8-d/2+b, x+wall, y1, 8+d/2-b, mat)
            for other in (-1, 1):
                # Chanfros em degrau: paredes fechadas, sem cubos transparentes sobrepostos.
                cx = 8-w/2+b-wall if side < 0 else 8+w/2-b
                cz = 8-d/2 if other < 0 else 8+d/2-b
                box(name+f"_quina_v_{side}_{other}", cx, y0, cz, cx+wall, y1, cz+b, mat)
                cx = 8-w/2 if side < 0 else 8+w/2-b
                cz = 8-d/2+b-wall if other < 0 else 8+d/2-b
                box(name+f"_quina_h_{side}_{other}", cx, y0, cz, cx+b, y1, cz+wall, mat)

    sections = drink["sections"]
    solid("fundo_espesso", 0, sections[0][1], sections[0][2], sections[0][3], "edge")
    for n, (y0, y1, w, d) in enumerate(sections[1:], 1):
        ring(f"vidro_{n}", y0, y1, w, d, "glass")
        if n < len(sections)-1:
            # Patamar dos ombros é vidro fino, não um cubo opaco que oculta o líquido.
            solid(f"ombro_{n}", y1-0.035, y1, w-wall*2, d-wall*2, "glass")
        liquid_top = min(drink["liquidLevel"], y1-0.04)
        if liquid_top > y0+0.045:
            solid(f"liquido_{n}", y0+0.04, liquid_top, w-wall*2.8, d-wall*2.8, "liquid")
    base = max(sections, key=lambda s: s[2])
    ring("aro_base", 0.42, 0.62, base[2]+0.03, base[3]+0.03, "edge")
    neck = sections[-1]
    ring("boca", neck[1]-0.45, neck[1]-0.18, neck[2]+0.16, neck[3]+0.16, "edge")
    ring("colar", drink["sealY"], drink["sealY"]+0.22, neck[2]+0.17, neck[3]+0.17, "seal")
    cap = drink["cap"]
    solid("fechamento", cap["y"], cap["y"]+cap["height"], cap["width"], cap["depth"],
          "cork" if cap["kind"] == "cork" else "cap")
    if cap["kind"] in ("screw", "crown"):
        for n in range(3):
            y = cap["y"]+0.12+n*0.2
            ring(f"friso_tampa_{n}", y, y+0.06, cap["width"]+0.055, cap["depth"]+0.055, "gold")
    if cap["kind"] in ("foil", "cork"):
        box("lacre_vertical", 7.79, drink["sealY"]+0.02, 8-cap["depth"]/2-0.03,
            8.21, cap["y"]+cap["height"]+0.015, 8-cap["depth"]/2+0.04, "seal")
    ly, lh, lw = drink["labelY"], drink["labelHeight"], drink["labelWidth"]
    body = next(s for s in sections if s[0] <= ly < s[1])
    z = 8-body[3]/2
    box("rotulo_frente", 8-lw/2, ly, z-0.065, 8+lw/2, ly+lh, z-0.015, "label")
    box("rotulo_verso", 8-lw*0.36, ly+0.28, 16-z+0.015,
        8+lw*0.36, ly+lh-0.28, 16-z+0.06, "back_label")
    # Reflexo lateral interrompido pelo ombro, sempre na superfície do vidro.
    box("reflexo_corpo", 8-body[2]*0.5+0.09, body[0]+0.4, 8-body[3]*0.22,
        8-body[2]*0.5+0.145, body[1]-0.28, 8-body[3]*0.22+0.19, "highlight")
    if drink.get("foam"):
        sec = next(s for s in sections if s[0] <= drink["liquidLevel"] <= s[1])
        solid("espuma", drink["liquidLevel"]-0.06, drink["liquidLevel"]+0.10,
              sec[2]-wall*3, sec[3]-wall*3, "foam")
    return parts


def altura_de(drink):
    return max(drink["sections"][-1][1], drink["cap"]["y"]+drink["cap"]["height"])


def model_de_parts(parts, altura, atlas_id):
    # O recipiente vazio preserva o fechamento e sua partícula opaca. O atlas
    # de faces continua próprio; não referenciar um PNG _vazia_particula ausente.
    particle_id = atlas_id.removesuffix("_vazia")
    # O fruto também é bloco. No26.3 blocos só aceitam materiais do atlas de
    # blocos; o item pode usar esse mesmo atlas, mantendo uma pintura única.
    material = "block/coco_atlas" if atlas_id == "coco" else "item/garrafas/" + atlas_id
    particle = "block/coco_particula" if atlas_id == "coco" else "item/garrafas/" + particle_id + "_particula"
    elements = []
    for part in parts:
        element = {k: part[k] for k in ("name", "from", "to")}
        element["faces"] = {side: {"uv": uv(part["material"]), "texture": "#atlas"}
                            for side in FACES}
        element["shade"] = part["material"] not in ("glass", "highlight")
        elements.append(element)
    return {
        "credit": "SNC Adventures | tools/gen_garrafas.py + tools/garrafas.json",
        "ambientocclusion": False, "gui_light": "front",
        "textures": {"atlas": "intoxicantes:"+material,
                     # v1.2.71 — PARTÍCULA PRÓPRIA (bug do playtest: "partículas
                     # estranhas ao comer o pão de cevada — vem de um item com
                     # 'cevada' escrito"): a partícula era o PRÓPRIO ATLAS, e o
                     # Minecraft amostra UM PIXEL ALEATÓRIO dele a cada migalha —
                     # podia cair no tile do rótulo (com "PAO CEVADA" escrito) ou
                     # no vidro transparente. Agora cada item usa o tile 'cap'
                     # dedicado (garrafas/<id>_particula.png), opaco e neutro.
                     "particle": "intoxicantes:"+particle},
        "display": {
            "gui": {"rotation": [15, -30, 0], "translation": [0, (8-altura/2)*1.05, 0], "scale": [1.05]*3},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 4, 0], "scale": [0.5]*3},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 8-altura/2, 0], "scale": [0.9]*3},
            "firstperson_righthand": {"rotation": [0, -20, -8], "translation": [0, 2.8, 0], "scale": [0.72]*3},
            "firstperson_lefthand": {"rotation": [0, 20, 8], "translation": [0, 2.8, 0], "scale": [0.72]*3},
            "thirdperson_righthand": {"rotation": [0, 0, -8], "translation": [0, 2.7, 0], "scale": [0.68]*3},
            "thirdperson_lefthand": {"rotation": [0, 0, 8], "translation": [0, 2.7, 0], "scale": [0.68]*3}
        }, "elements": elements
    }


def intermediario(drink, catalog):
    """Frasco padrão das ETAPAS (caldos, mostos, melaço, jovens): vidro grosso
    de laboratório de bodega, tampa de rosca, rótulo da etapa e líquido cheio.
    v1.2.65: derivado pela MESMA geometry() das garrafas — prévia e produção
    saem do mesmo caminho, sem divergência. IDs preservados."""
    w = 5.2
    frasco = dict(drink,
                  sections=[[0, 0.8, w, w], [0.8, 9.2, w, w]],
                  liquidLevel=8.6, foam=False,
                  cap={"kind": "screw", "y": 9.02, "height": 1.1,
                       "width": 3.3, "depth": 3.3},
                  sealY=8.4, labelY=3.1, labelHeight=3.2, labelWidth=4.4)
    return geometry(frasco, catalog), altura_de(frasco)


def atlas(drink, size, symbols, base=None):
    if size != 128:
        raise ValueError("As UVs deste atlas exigem resolução 128×128")
    if base is not None:
        # v1.2.65: garrafa VAZIA — mesmo atlas da cheia com o tile do líquido
        # (e da espuma) PINTADO com a tampa: o render não mostra conteúdo, sem
        # gerar arte nova por bebida.
        image = base.copy()
        draw = ImageDraw.Draw(image)
        x0, y0, tile_size = TILES["cap"]
        tampa = image.getpixel((x0+8, y0+8))
        for material in ("liquid", "foam"):
            x0, y0, tile_size = TILES[material]
            draw.rectangle((x0, y0, x0+tile_size-1, y0+tile_size-1), fill=tampa)
        return image
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    palette = dict(drink["palette"], highlight="ecf9ec", foam="f0e3b7", cork=drink["palette"]["cap"],
                   back_label=drink["palette"]["label"])
    alpha = {"glass": 82, "highlight": 148}
    for index, material in enumerate(MATERIALS):
        x0, y0, tile_size = TILES[material]
        rgb = tuple(int(palette[material][n:n+2], 16) for n in (0, 2, 4))
        for y in range(tile_size):
            for x in range(tile_size):
                grain = ((x*13+y*7+index*11) % 9)-4
                if material == "cork": grain += 9 if (x*3+y*5) % 17 < 3 else -2
                shade = 12*(1-y/(tile_size-1)) - 10*(x/(tile_size-1)) + grain
                c = tuple(max(0, min(255, round(v+shade))) for v in rgb)
                image.putpixel((x0+x, y0+y), (*c, alpha.get(material, 255)))
    draw = ImageDraw.Draw(image)
    font = ImageFont.load_default(size=9)
    for material in ("label", "back_label"):
        x0, y0, tile_size = TILES[material]
        ink = "#"+palette["ink"]
        draw.rectangle((x0+4, y0+4, x0+59, y0+59), outline=ink, width=1)
        draw.rectangle((x0+6, y0+6, x0+57, y0+57), outline="#"+palette["gold"], width=1)
        if material == "label":
            for row, word in enumerate(drink["label"]):
                # Rótulo somente genérico. Tamanho adapta ao texto, sem marcas.
                f = font if row == 0 else ImageFont.load_default(size=7)
                bounds = draw.textbbox((0, 0), word, font=f)
                draw.text((x0+32-(bounds[2]-bounds[0])/2, y0+15+row*12), word, fill=ink, font=f)
            for y, line in enumerate(symbols[drink["symbol"]]):
                for x, pixel in enumerate(line):
                    if pixel == "1":
                        draw.rectangle((x0+23+x*2, y0+38+y*2, x0+24+x*2, y0+39+y*2), fill=ink)
        else:
            for y in (16, 22, 28, 39, 45):
                draw.line((x0+14, y0+y, x0+48-(y % 3), y0+y), fill=ink)
    return image


def pao_cevada(drink):
    """v1.2.65: PÃO DE CEVADA em 3D — pão rústico escuro com fatias na cima,
    miolo claro nas pontas e poeira de farinha. A comida honesta da colheita."""
    parts = []

    def box(name, x0, y0, z0, x1, y1, z1, mat):
        if min(x1-x0, y1-y0, z1-z0) <= 0:
            raise ValueError(f"Cuboide inválido: {drink['id']}/{name}")
        parts.append({"name": name, "from": [round(x0, 5), round(y0, 5), round(z0, 5)],
                      "to": [round(x1, 5), round(y1, 5), round(z1, 5)], "material": mat})

    box("base", 3.5, 0, 4.5, 12.5, 1.2, 11.5, "cap")
    box("corpo", 3.0, 1.2, 4.2, 13.0, 6.2, 11.8, "cap")
    box("topo", 4.2, 6.2, 5.0, 11.8, 7.9, 11.0, "cap")
    # miolo claro aparecendo nas pontas do pão (a fatia de dentro)
    box("miolo_esq", 2.9, 2.0, 4.6, 3.05, 5.8, 11.4, "liquid")
    box("miolo_dir", 12.95, 2.0, 4.6, 13.1, 5.8, 11.4, "liquid")
    # fatias marcadas na crosta
    box("fatia_1", 5.2, 7.75, 5.0, 6.0, 8.05, 11.0, "ink")
    box("fatia_2", 8.6, 7.75, 5.0, 9.4, 8.05, 11.0, "ink")
    box("farinha", 4.4, 7.9, 5.2, 8.4, 8.0, 10.8, "highlight")
    return parts, 8.05


def coco_fruto(drink):
    """v1.2.65: O COCO em 3D — esfera voxelizada com fibra, três olhos e brilho.
    O fruto in natura do coqueiro, no padrão do mod."""
    parts = []

    def box(name, x0, y0, z0, x1, y1, z1, mat):
        if min(x1-x0, y1-y0, z1-z0) <= 0:
            raise ValueError(f"Cuboide inválido: {drink['id']}/{name}")
        parts.append({"name": name, "from": [round(x0, 5), round(y0, 5), round(z0, 5)],
                      "to": [round(x1, 5), round(y1, 5), round(z1, 5)], "material": mat})

    box("fundo", 5.4, 0, 5.4, 10.6, 1.4, 10.6, "cap")
    box("corpo", 3.8, 1.4, 3.8, 12.2, 7.2, 12.2, "cap")
    box("topo", 4.8, 7.2, 4.8, 11.2, 8.8, 11.2, "cap")
    # fibra da casca descendo em faixas
    box("fibra_frente", 3.8, 1.4, 7.8, 3.98, 7.2, 8.3, "edge")
    box("fibra_lado", 7.2, 1.4, 3.8, 7.7, 7.2, 3.98, "edge")
    box("fibra_fundo", 11.0, 1.4, 6.4, 11.18, 7.2, 6.9, "edge")
    # os três olhos do coco (triângulo em cima)
    box("olho_1", 7.1, 8.8, 6.5, 7.7, 9.0, 7.1, "ink")
    box("olho_2", 8.6, 8.8, 8.2, 9.2, 9.0, 8.8, "ink")
    box("olho_3", 5.9, 8.8, 8.3, 6.5, 9.0, 8.9, "ink")
    # brilho de luz no quadrante superior-esquerdo
    box("brilho_corpo", 3.9, 2.2, 3.9, 4.05, 6.4, 4.05, "highlight")
    box("brilho_topo", 4.9, 8.85, 4.9, 5.05, 8.95, 5.05, "highlight")
    return parts, 9.0


def coco_dados():
    return {"id": "coco", "name": "Coco", "label": ["COCO", "DA PRAÇA"], "symbol": "coconut",
            "palette": {"glass": "ffffff", "edge": "6e4a28", "liquid": "c8a071", "cap": "7a5230",
                        "label": "e8dcc4", "ink": "2e1d10", "gold": "b6914b", "seal": "705027"}}


def coco_modelo_bloco():
    """Mesmo fruto coletado, elevado sete unidades para encostar no apoio acima."""
    dados=coco_dados()
    partes, altura=coco_fruto(dados)
    modelo=model_de_parts(partes, altura, "coco")
    for e in modelo["elements"]:
        for limite in ("from", "to"): e[limite][1] = round(e[limite][1]+7, 5)
    return modelo


def preview_entries():
    catalog=load_catalog()
    dados=coco_dados()
    partes, altura=coco_fruto(dados)
    arte=atlas(dados,128,catalog["symbols"])
    def data(img):
        buf=io.BytesIO(); img.save(buf,format="PNG")
        return "data:image/png;base64,"+base64.b64encode(buf.getvalue()).decode("ascii")
    x0,y0,ts=TILES["cap"]
    return [{"id":"coco", "name":"Coco · fruto único", "category":"Item e mundo",
        "model": model_de_parts(partes, altura, "coco"),
        "variants":[{"name":"Pendurado no coqueiro", "model":coco_modelo_bloco()}],
        "textures":{"intoxicantes:block/coco_atlas":data(arte),
                    "intoxicantes:block/coco_particula":data(arte.crop((x0,y0,x0+ts,y0+ts)))},
        "description":"O coco pendurado usa os mesmos onze cuboides, fibras, três olhos e atlas128 do fruto colhido. O modelo encosta no apoio do coqueiro."}]


def gravar(caminho_relativo, dados):
    path = ASSETS / caminho_relativo
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(dados, indent=2, ensure_ascii=False)+"\n", encoding="utf-8")


def definition(item_id):
    gravar(f"items/{item_id}.json", {"model": {"type": "minecraft:model",
                                               "model": f"intoxicantes:item/{item_id}"}})


def main():
    import argparse
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--only", choices=("coco", "recipientes"))
    parser.add_argument("--list", action="store_true")
    args=parser.parse_args()
    if args.list:
        if args.only == "recipientes":
            for drink in load_catalog()["bottles"]:
                print(ASSETS / f"models/item/{drink['id']}_vazia.json")
            return
        if args.only != "coco": parser.error("--list precisa de --only coco ou recipientes")
        for path in ("models/item/coco.json", "items/coco.json", "models/block/coco_bloco.json", "textures/block/coco_atlas.png", "textures/block/coco_particula.png"):
            print(ASSETS / path)
        return
    catalog = load_catalog()
    if args.only == "recipientes":
        for drink in catalog["bottles"]:
            vazio_id = drink["id"] + "_vazia"
            gravar(f"models/item/{vazio_id}.json", model_de_parts(
                geometry(dict(drink, id=vazio_id), catalog), altura_de(drink), vazio_id))
        print("OK: oito modelos de recipientes; faces e atlas preservados")
        return
    simbolos = catalog["symbols"]
    # v1.2.65: itens 3D fora das bebidas, mesmo padrão de atlas/modelo
    extras = [
        ("pao_cevada", pao_cevada, {"id": "pao_cevada", "name": "Pão de Cevada",
                                     "label": ["PAO", "CEVADA"], "symbol": "grain",
                                     "palette": {"glass": "d9c9a8", "edge": "6b4a26",
                                                 "liquid": "c9a469", "cap": "8a5f33",
                                                 "label": "e3d4ae", "ink": "3c2a14",
                                                 "gold": "b6914b", "seal": "705027"}}),
        ("coco", coco_fruto, coco_dados()),
    ]
    for nome, funcao, drink in extras:
        if args.only and nome != args.only: continue
        parts, altura = funcao(drink)
        gravar(f"models/item/{nome}.json", model_de_parts(parts, altura, nome))
        definition(nome)
        atlas_extra = atlas(drink, catalog["atlasSize"], simbolos)
        material_path = f"textures/block/coco_atlas.png" if nome == "coco" else f"textures/item/garrafas/{nome}.png"
        atlas_extra.save(ASSETS / material_path)
        # v1.2.71: partícula dedicada (fim da migalha com letra de rótulo)
        x0, y0, ts = TILES["cap"]
        particle_path = "textures/block/coco_particula.png" if nome == "coco" else f"textures/item/garrafas/{nome}_particula.png"
        atlas_extra.crop((x0, y0, x0+ts, y0+ts)).save(ASSETS / particle_path)
        print(f"{nome}: 3D no padrão do mod ({len(parts)} cuboides), atlas 128×128")
    gravar("models/block/coco_bloco.json", coco_modelo_bloco())
    if args.only: return
    for drink in catalog["bottles"]:
        name = drink["id"]
        cheia = atlas(drink, catalog["atlasSize"], simbolos)
        pasta = ASSETS / "textures/item/garrafas"
        pasta.mkdir(parents=True, exist_ok=True)
        cheia.save(pasta / f"{name}.png")
        # v1.2.71: a textura de PARTÍCULA dedicada (tile 'cap' recortado do
        # atlas — o material "neutro" do item)
        x0, y0, ts = TILES["cap"]
        cheia.crop((x0, y0, x0+ts, y0+ts)).save(pasta / f"{name}_particula.png")
        parts = geometry(drink, catalog)
        gravar(f"models/item/{name}.json", model_de_parts(parts, altura_de(drink), name))
        definition(name)
        # v1.2.65: a versão VAZIA — mesma identidade/tampa/rótulo, sem líquido
        # nem espuma. É o recipiente devolvido quando a bebida acaba.
        vazio_id = name + "_vazia"
        vazia = dict(drink, id=vazio_id)
        gravar(f"models/item/{vazio_id}.json",
               model_de_parts(geometry(vazia, catalog), altura_de(drink), vazio_id))
        definition(vazio_id)
        atlas(drink, catalog["atlasSize"], simbolos, base=cheia).save(
            pasta / f"{vazio_id}.png")
        print(f"{name}: {len(parts)} cuboides + vazia, atlas 128×128")
    for drink in catalog.get("intermediarios", []):
        parts, altura = intermediario(drink, catalog)
        gravar(f"models/item/{drink['id']}.json",
               model_de_parts(parts, altura, drink["id"]))
        definition(drink["id"])
        atlas_intermediario = atlas(drink, catalog["atlasSize"], simbolos)
        atlas_intermediario.save(
            ASSETS / f"textures/item/garrafas/{drink['id']}.png")
        x0, y0, ts = TILES["cap"]
        atlas_intermediario.crop((x0, y0, x0+ts, y0+ts)).save(
            ASSETS / f"textures/item/garrafas/{drink['id']}_particula.png")
        print(f"{drink['id']}: frasco de etapa ({len(parts)} cuboides), atlas 128×128")


if __name__ == "__main__":
    main()
