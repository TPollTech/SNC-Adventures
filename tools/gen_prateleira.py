"""Gera a PRATELEIRA DE MERCADO (v1.2.75) — a gôndola ILHA do Esquinão:
estrutura de metal cinza, três prateleiras brancas e etiquetas amarelas nos
DOIS corredores. A ilha tem MEIO BLOCO de profundidade por lado: os
expositores encostam na face -Z (frente, etiqueta olhando pra rua) e na face
+Z (fundo, o segundo corredor compra de lá). A ESTANTERIA é o modelo 3D; os
ITENS (garrafas, sementes, o que o freguês repuser) são desenhados pelo
BlockEntityRenderer (PrateleiraMercadoRenderer), NOVE por lado — dezoito no
total (o par de trás vira estoque vivo da fileira dupla).

Saídas (todas sob src/main/resources/):
  assets/intoxicantes/textures/block/prateleira_mercado.png        (metal escuro)
  assets/intoxicantes/textures/block/prateleira_mercado_prateleira.png (tábua branca)
  assets/intoxicantes/textures/block/prateleira_mercado_etiqueta.png   (etiqueta amarela)
  assets/intoxicantes/blockstates/prateleira_mercado.json          (facing N/E/S/W)
  assets/intoxicantes/models/block/prateleira_mercado.json         (estrutura 3D)
  assets/intoxicantes/models/item/prateleira_mercado.json
  assets/intoxicantes/items/prateleira_mercado.json                (definition v1.2.65)
  data/intoxicantes/loot_table/blocks/prateleira_mercado.json      (dropa a si; os ITENS
                                                    caem pelo Java: soltarConteudo)

Espelha as 3 texturas no pack (../resourcepacks/minhas-texturas) — regra do
AGENTS.md: texturas 2D do mod vivem espelhadas no pack; atlas de item 3D nunca.

Convenção do modelo (igual a fornalha do vanilla): a FRENTE (etiqueta,
produtos olhando pra rua) é o lado -Z local. O blockstate gira com "y":
north=0 (padrão), east=90, south=180, west=270.

Uso: python tools/gen_prateleira.py  (a partir da raiz do projeto do mod)
"""
import json
import os
import base64
import copy
import random
from io import BytesIO

from PIL import Image, ImageDraw

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
    """Grava a pintura 128 no mod e no pack, somente na execução explícita."""
    img = pixels if isinstance(pixels, Image.Image) else Image.new("RGBA", (T, T))
    if not isinstance(pixels, Image.Image):
        img.putdata(pixels)
    destino = os.path.join(ASSETS, "textures", "block", nome)
    os.makedirs(os.path.dirname(destino), exist_ok=True)
    img.save(destino)
    os.makedirs(MIRROR, exist_ok=True)
    img.save(os.path.join(MIRROR, nome))
    print("  " + nome)


# ------------------------------------------------------------------ metal
def tex_metal():
    """Aço pintado escuro da gôndola: cinza grafite com colunas verticais de
    brilho — o perfurado do comércio de bairro."""
    img = Image.new("RGBA", (T, T))
    rng = random.Random(129)
    for y in range(T):
        for x in range(T):
            luz = int(12 * (1 - abs(x - 40) / 90)) + rng.randrange(-3, 4)
            img.putpixel((x, y), (61 + luz, 66 + luz, 70 + luz, 255))
    g = ImageDraw.Draw(img)
    for x in (0, 31, 63, 95, 127):
        g.line((x, 0, x, 127), fill="#333b3d", width=2)
        if x < 127:
            g.line((x + 2, 0, x + 2, 127), fill="#8b9493")
    for y in range(10, 126, 16):
        for x in (12, 44, 76, 108):
            g.rectangle((x, y, x + 4, y + 7), fill="#252d2f")
            g.line((x, y + 8, x + 4, y + 8), fill="#a6adab")
    return img


# ------------------------------------------------------------------ prateleira
def tex_prateleira():
    """A tábua branca da gôndola: melamina clara com sombra na borda frontal
    (o lábio que segura o produto) e linha de embaixo mais escura."""
    img = Image.new("RGBA", (T, T))
    rng = random.Random(253)
    for y in range(T):
        for x in range(T):
            delta = rng.randrange(-2, 3) - int(y / 16)
            img.putpixel((x, y), (243 + delta, 243 + delta, 231 + delta, 255))
    g = ImageDraw.Draw(img)
    g.rectangle((0, 0, 127, 2), fill="#fffef3")
    g.rectangle((0, 119, 127, 127), fill="#b8bcad")
    g.line((0, 118, 127, 118), fill="#e2e3d6", width=2)
    for y in (31, 63, 95):
        g.line((0, y, 127, y), fill="#d9ddd0")
        g.line((0, y + 1, 127, y + 1), fill="#f6f5e9")
    return img


# ------------------------------------------------------------------ etiqueta
def tex_etiqueta():
    """A etiqueta de preço amarela presa na frente da prateleira — a cor do
    mercadinho de esquina (o 'OFERTA' de fita amarela)."""
    img = Image.new("RGBA", (T, T), "#ccb765")
    g = ImageDraw.Draw(img)
    # Região de 128×24, igual à proporção 12×2.25 da etiqueta no modelo.
    for y in range(24):
        for x in range(128):
            n = ((x * 17 + y * 7) % 5) - 2
            img.putpixel((x, y), (min(255, 247 + n), 223 + n, 134 + n, 255))
    g.rectangle((0, 0, 127, 23), outline="#746a34", width=2)
    g.rectangle((3, 3, 43, 20), fill="#355345")
    _texto(g, "SNC", 6, 7, "#f8edc3", 1)
    # Preço real vem do estoque: o material não imprime um valor fictício.
    g.line((53, 17, 95, 17), fill="#9b8b57", width=1)
    for x in range(102, 123, 3):
        g.line((x, 5, x, 18), fill="#665c3e", width=1 if x % 2 else 2)
    # Outras regiões: aço do porta-etiqueta e parafuso usinado.
    g.rectangle((0, 32, 127, 63), fill="#899080")
    for y in range(33, 63, 4):
        g.line((0, y, 127, y), fill="#b8bcad")
    g.rectangle((0, 64, 127, 127), fill="#a1a698")
    g.line((0, 65, 127, 65), fill="#e5e6d4", width=2)
    return img


def _texto(g, texto, x, y, cor, escala=1):
    glyphs = {"S": ["11111","10000","10000","11111","00001","00001","11111"],
              "N": ["10001","11001","11001","10101","10011","10011","10001"],
              "C": ["11111","10000","10000","10000","10000","10000","11111"],
              "4": ["10010","10010","10010","11111","00010","00010","00010"],
              "9": ["11111","10001","10001","11111","00001","00001","11111"],
              "0": ["11111","10001","10001","10001","10001","10001","11111"],
              ",": ["00000","00000","00000","00000","00000","00100","01000"]}
    for ch in texto:
        for py, linha in enumerate(glyphs[ch]):
            for px, bit in enumerate(linha):
                if bit == "1":
                    g.rectangle((x + px * escala, y + py * escala,
                                 x + (px + 1) * escala - 1, y + (py + 1) * escala - 1), fill=cor)
        x += 6 * escala


# ------------------------------------------------------------------ modelos
def elemento(from_, to, tex, faces=("north", "south", "east", "west", "up", "down"),
             uv=(0, 0, 16, 16)):
    """Caixa do modelo: todas as faces mapeadas na textura dada."""
    return {"from": list(from_), "to": list(to),
            "faces": {f: {"uv": list(uv), "texture": tex} for f in faces}}


COLUNAS = (3.5, 8.0, 12.5)
NIVEIS = (2.0, 7.0, 12.0)
# A ILHA: dorso central em z 7.5..8.5 separa frente (-Z, z 0..7.5) e fundo
# (+Z, z 8.5..16) — meio bloco de vão por corredor (PrateleiraMercadoBlock).
DORSO = (7.5, 8.5)


def modelo():
    # Mesmas peças físicas de PrateleiraMercadoBlock; frente local -Z.
    elements = [
        elemento((0, 0, 0), (1, 16, 16), "#metal"),
        elemento((15, 0, 0), (16, 16, 16), "#metal"),
        elemento((1, 1, DORSO[0]), (15, 16, DORSO[1]), "#metal"),   # dorso central
    ]
    for superficie in NIVEIS:
        # tampa CONTÍNUA: os dois corredores apoiam na mesma prateleira
        elements.append(elemento((1, superficie - .5, 0), (15, superficie, 15.5), "#prateleira"))
        for x in COLUNAS:
            # etiquetas do MESMO slot: frente (-Z) e fundo (+Z)
            elements.append(elemento((x - 1.95, superficie - .9, 0),
                                     (x + 1.95, superficie - .05, .22), "#etiqueta", uv=(0, 4, 16, 8)))
            elements.append(elemento((x - 1.85, superficie - .8, 0),
                                     (x + 1.85, superficie - .12, .02), "#etiqueta", faces=("north",), uv=(0, 0, 16, 3)))
            elements.append(elemento((x - 1.95, superficie - .9, 15.78),
                                     (x + 1.95, superficie - .05, 16), "#etiqueta", uv=(0, 4, 16, 8)))
            elements.append(elemento((x - 1.85, superficie - .8, 15.98),
                                     (x + 1.85, superficie - .12, 16), "#etiqueta", faces=("south",), uv=(0, 0, 16, 3)))
    for x in (.25, 15.25):
        for y in (2, 7, 12):
            # parafuso nas QUATRO arestas (os dois corredores veem o móvel)
            elements.append(elemento((x, y, .01), (x + .5, y + .5, .05), "#etiqueta", uv=(1, 8, 3, 10)))
            elements.append(elemento((x, y, 15.95), (x + .5, y + .5, 15.99), "#etiqueta", uv=(1, 8, 3, 10)))
    return {
        "credit": "SNC — gôndola ilha: meio bloco por lado, dezoito expositores e etiquetas nos dois corredores",
        "parent": "minecraft:block/block",
        "textures": {
            "metal": "intoxicantes:block/prateleira_mercado",
            "prateleira": "intoxicantes:block/prateleira_mercado_prateleira",
            "etiqueta": "intoxicantes:block/prateleira_mercado_etiqueta",
            "particle": "intoxicantes:block/prateleira_mercado",
        },
        "texture_size": [128, 128], "elements": elements,
        "display": {
            "gui": {"rotation": [15, 145, 0], "translation": [0, 0, 0], "scale": [.8] * 3},
            "firstperson_righthand": {"rotation": [0, 165, -8], "translation": [0, 2, -1], "scale": [.55] * 3},
            "firstperson_lefthand": {"rotation": [0, 165, -8], "translation": [0, 2, -1], "scale": [.55] * 3},
            "thirdperson_righthand": {"rotation": [90, 0, 0], "translation": [0, 2, 1], "scale": [.5] * 3},
            "thirdperson_lefthand": {"rotation": [90, 0, 0], "translation": [0, 2, 1], "scale": [.5] * 3},
            "ground": {"translation": [0, 0, 0], "scale": [.45] * 3},
            "fixed": {"rotation": [0, 180, 0], "scale": [.8] * 3},
        },
    }


def shapes_js():
    """Peças físicas da COLISÃO (o laranja do estúdio), iguais ao Java."""
    return [[0, 0, 0, 1, 16, 16], [15, 0, 0, 16, 16, 16],
            [1, 1, DORSO[0], 15, 16, DORSO[1]],
            [1, 1.5, 0, 15, 2, DORSO[0]], [1, 6.5, 0, 15, 7, DORSO[0]],
            [1, 11.5, 0, 15, 12, DORSO[0]],
            [1, 1.5, DORSO[1], 15, 2, 16], [1, 6.5, DORSO[1], 15, 7, 16],
            [1, 11.5, DORSO[1], 15, 12, 16]]


def gira180(caixa):
    """Vira o produto pro corredor de trás: 180° no eixo Y (centro 8, 8).
    Faces laterais trocam de nome; up/down espelham a textura nos 2 eixos."""
    c = copy.deepcopy(caixa)
    f, t = c["from"], c["to"]
    c["from"] = [16 - t[0], f[1], 16 - t[2]]
    c["to"] = [16 - f[0], t[1], 16 - f[2]]
    nomes = {"north": "south", "south": "north", "east": "west", "west": "east"}
    faces = {}
    for nome, face in c.get("faces", {}).items():
        if nome in ("up", "down") and "uv" in face:
            uv = face["uv"]
            face = {**face, "uv": [uv[2], uv[3], uv[0], uv[1]]}
        faces[nomes.get(nome, nome)] = face
    c["faces"] = faces
    return c


MODELO_BLOCO = modelo()


def preview_entries():
    """Estúdio abastecido usa as mesmas medidas/fit do renderer, só em memória."""
    import gen_garrafas
    import gen_farm_resources
    textures = {}
    def imagem(ref, img):
        stream = BytesIO(); img.save(stream, format="PNG")
        textures[ref] = "data:image/png;base64," + base64.b64encode(stream.getvalue()).decode("ascii")
    for name, img in (("prateleira_mercado", tex_metal()),
                      ("prateleira_mercado_prateleira", tex_prateleira()),
                      ("prateleira_mercado_etiqueta", tex_etiqueta())):
        imagem("intoxicantes:block/" + name, img)
    catalog = gen_garrafas.load_catalog()
    produtos = []
    for dados in catalog["bottles"]:
        nome = dados["id"]
        produtos.append(gen_garrafas.model_de_parts(gen_garrafas.geometry(dados, catalog),
                                                   gen_garrafas.altura_de(dados), nome))
        imagem("intoxicantes:item/garrafas/" + nome,
               gen_garrafas.atlas(dados, catalog["atlasSize"], catalog["symbols"]))
    partes, altura = gen_garrafas.pao_cevada({"id": "pao_cevada"})
    pao = gen_garrafas.model_de_parts(partes, altura, "pao_cevada")
    partes, altura = gen_garrafas.coco_fruto(gen_garrafas.coco_dados())
    coco = gen_garrafas.model_de_parts(partes, altura, "coco")
    comidas = [pao, coco, gen_farm_resources.modelo_cacho()]
    for ref, arquivo in (("intoxicantes:item/garrafas/pao_cevada", "textures/item/garrafas/pao_cevada.png"),
                         ("intoxicantes:block/coco_atlas", "textures/block/coco_atlas.png")):
        with Image.open(os.path.join(ASSETS, arquivo)) as img: imagem(ref, img)
    with Image.open(os.path.join(ASSETS, "textures", "block", "parreira_atlas.png")) as img:
        imagem("intoxicantes:block/parreira_atlas", img)
    def montagem(expostos, fundo=(), mesa=False):
        movel = {"textures": {}, "elements": []} if mesa else modelo()
        lados = [(expostos, 3.8, False)]
        if fundo:
            lados.append((list(fundo), 16 - 3.8, True))
        for lados_expostos, z_centro, verso in lados:
            for slot, produto in enumerate(lados_expostos):
                el = [gira180(c) for c in produto["elements"]] if verso else produto["elements"]
                lo = [min(e["from"][a] for e in el) for a in range(3)]
                hi = [max(e["to"][a] for e in el) for a in range(3)]
                escala = 1 if mesa else min(1, 3.8 / max(hi[0] - lo[0], 1e-9),
                             3.8 / max(hi[1] - lo[1], 1e-9), 7 / max(hi[2] - lo[2], 1e-9))
                destino = ((slot % 3) * 14 - 6, .01, (slot // 3) * 14) if mesa else (
                    COLUNAS[slot % 3], NIVEIS[slot // 3] + .01, z_centro)
                centro = ((lo[0] + hi[0]) / 2, lo[1], (lo[2] + hi[2]) / 2)
                prefixo = ("verso" if verso else "slot") + str(slot)
                for key, ref in produto["textures"].items():
                    movel["textures"][f"{prefixo}_{key}"] = ref
                for caixa in el:
                    copia = copy.deepcopy(caixa)
                    for limite in ("from", "to"):
                        copia[limite] = [round(destino[a] + (copia[limite][a] - centro[a]) * escala, 6) for a in range(3)]
                    for face in copia["faces"].values():
                        if face["texture"].startswith("#"):
                            face["texture"] = f"#{prefixo}_" + face["texture"][1:]
                    movel["elements"].append(copia)
        return movel
    abastecida = montagem(comidas + produtos[:6], (produtos[6:] + produtos + comidas)[:9])
    padaria = montagem(comidas * 3, comidas[::-1])
    empilhadas = copy.deepcopy(padaria)
    bebidas = montagem(produtos + [pao], produtos[::-1][:9])
    for key, ref in bebidas["textures"].items(): empilhadas["textures"]["cima_" + key] = ref
    superiores = copy.deepcopy(bebidas["elements"])
    for caixa in superiores:
        for limite in ("from", "to"): caixa[limite][1] += 16
        for face in caixa["faces"].values():
            if face["texture"].startswith("#"): face["texture"] = "#cima_" + face["texture"][1:]
    empilhadas["elements"].extend(superiores)
    return [{"id": "prateleira_mercado", "name": "Prateleira do Esquinão", "category": "Ambiente",
             "model": abastecida, "textures": textures,
             "variants": [{"name": "Ilha vazia · meio bloco por lado", "model": modelo()},
                          {"name": "Abastecida · frente e fundo", "model": abastecida},
                          {"name": "Duas empilhadas · comidas e oito bebidas", "model": empilhadas},
                          {"name": "Padaria e frutas · dois corredores", "model": padaria},
                          {"name": "Colocados na mesa · escala real", "model": montagem(comidas + produtos[:3], mesa=True)}],
             "description": "A ilha de MEIO BLOCO expõe NOVE produtos na frente e NOVE atrás — o corredor dos dois lados compra. Preço, dose e reposição por slot; a fileira dupla alterna as seções entre frente e fundo. Pão de cevada, coco e uva podem ser colocados na mesa e recolhidos. Prévia fora do jogo; validação no Minecraft após aprovação.",
             "shapes": shapes_js()}]


BLOCKSTATE = {
    "variants": {
        "facing=north": {"model": "intoxicantes:block/prateleira_mercado"},
        "facing=east": {"model": "intoxicantes:block/prateleira_mercado", "y": 90},
        "facing=south": {"model": "intoxicantes:block/prateleira_mercado", "y": 180},
        "facing=west": {"model": "intoxicantes:block/prateleira_mercado", "y": 270},
    }
}


def main():
    print("gen_prateleira: texturas")
    png("prateleira_mercado.png", tex_metal())
    png("prateleira_mercado_prateleira.png", tex_prateleira())
    png("prateleira_mercado_etiqueta.png", tex_etiqueta())

    print("gen_prateleira: jsons")
    wjson(os.path.join(ASSETS, "blockstates", "prateleira_mercado.json"), BLOCKSTATE)
    wjson(os.path.join(ASSETS, "models", "block", "prateleira_mercado.json"), MODELO_BLOCO)
    wjson(os.path.join(ASSETS, "models", "item", "prateleira_mercado.json"),
          {"parent": "intoxicantes:block/prateleira_mercado"})
    # v1.2.65: TODO item novo nasce com a definition apontando pro modelo
    wjson(os.path.join(ASSETS, "items", "prateleira_mercado.json"),
          {"model": {"type": "minecraft:model",
                     "model": "intoxicantes:block/prateleira_mercado"}})
    wjson(os.path.join(DATA, "loot_table", "blocks", "prateleira_mercado.json"), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item",
                         "name": "intoxicantes:prateleira_mercado"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
            "random_sequence": "intoxicantes:blocks/prateleira_mercado",
        }],
    })
    print("gen_prateleira: OK (3 texturas espelhadas no pack + 5 jsons)")


if __name__ == "__main__":
    main()
