"""Gera os JSONs de recursos das plantacoes: blockstates, modelos,
definicoes de item, loot tables de colheita e receitas novas.

Uso: python tools/gen_farm_resources.py  (a partir da raiz do projeto do mod)
     python tools/gen_farm_resources.py --only parreira --list
     python tools/gen_farm_resources.py --only parreira
"""
import json
import os
import math
import copy
import base64
import io

RES = os.path.join("src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "intoxicantes")
DATA = os.path.join(RES, "data", "intoxicantes")

CROPS = ["maconha", "lupulo", "uva", "cafe", "papoula"]

def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)

def item_def(model_id):
    return {"model": {"type": "minecraft:model", "model": model_id}}

def gen_crop_blockstate(crop):
    variants = {}
    for age in range(5):
        for uv in range(4):
            suffix = ("stage%d" % (age + 1)) if age < 4 else ("ripe" if uv >= 3 else "dormant")
            variants["age=%d,uv_age=%d" % (age, uv)] = {
                "model": "intoxicantes:block/%s_plant_%s" % (crop, suffix)}
    wjson(os.path.join(ASSETS, "blockstates", crop + "_plant.json"), {"variants": variants})

# ---------------------------------------------------------------- blockstates
def gen_blockstates():
    for crop in CROPS:
        gen_crop_blockstate(crop)

    # luminária de dois tubos com estado LIT (v1.2.17 — disjuntor de redstone)
    wjson(os.path.join(ASSETS, "blockstates", "lampada_uv.json"), {
        "variants": {
            "lit=true": {"model": "intoxicantes:block/lampada_uv"},
            "lit=false": {"model": "intoxicantes:block/lampada_uv_off"},
        }
    })

    # POSTE DE LUZ (v1.2.24): PARTE (base/corpo/topo — poste de 3 blocos) × LIT
    # (a luminária do topo apaga; base e corpo não mudam de cara)
    wjson(os.path.join(ASSETS, "blockstates", "poste_luz.json"), {
        "variants": {
            "parte=base,lit=true": {"model": "intoxicantes:block/poste_luz_base"},
            "parte=base,lit=false": {"model": "intoxicantes:block/poste_luz_base"},
            "parte=corpo,lit=true": {"model": "intoxicantes:block/poste_luz_corpo"},
            "parte=corpo,lit=false": {"model": "intoxicantes:block/poste_luz_corpo"},
            "parte=topo,lit=true": {"model": "intoxicantes:block/poste_luz_topo_lit"},
            "parte=topo,lit=false": {"model": "intoxicantes:block/poste_luz_topo_off"},
        }
    })

    # v1.2.23: ASFALTO — o BLOCKSTATE nunca foi gerado (só o modelo existia):
    # o xadrez rosa do estacionamento era isto. Uma variante, modelo cube_all.
    wjson(os.path.join(ASSETS, "blockstates", "asfalto.json"), {
        "variants": {"": {"model": "intoxicantes:block/asfalto"}}
    })

    # v1.2.60: PROJETOR DE HOLOGRAMA — LIGADO segue redstone (sem sinal, dorme)
    wjson(os.path.join(ASSETS, "blockstates", "holograma.json"), {
        "variants": {
            "ligado=true": {"model": "intoxicantes:block/holograma"},
            "ligado=false": {"model": "intoxicantes:block/holograma_off"},
        }
    })

    # LETREIRO DO ESQUINÃO (v1.2.23; CONVENÇÃO CORRIGIDA na v1.2.28): o modelo
    # tem a face FRONT na NORTH local (convenção da fornalha), então o facing
    # gira FORNALHA (north=0, east=90, south=180, west=270) — o mapeamento
    # wall-sign antigo punha a textura da matriz de LED no lado OPOSTO do
    # texto (a "caixa metálica com texto flutuando" do playtest).
    # × PARTE (torres/painel) × NIVEL — 72 estados cobertos.
    placas = {}
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    # v1.2.31: o DISPLAY DE FACHADA é o painel/extensão — a faixa larga
    # MONTADA NA FACHADA (bloco cheio, 1 modelo só; o texto atravessa via
    # BER no painel central). As anatomias VELHAS (torres de 1.2.24–1.2.30)
    # continuam cobertas enquanto os saves antigos migram pro zelador.
    modelos_por_parte_nivel = {
        ("painel", "rodape"): "placa_esquinao",
        ("painel", "coluna"): "placa_esquinao",
        ("painel", "topo"): "placa_esquinao",
        ("extensao", "rodape"): "placa_esquinao",
        ("extensao", "coluna"): "placa_esquinao",
        ("extensao", "topo"): "placa_esquinao",
        ("esquerda", "rodape"): "placa_esquinao_rodape",
        ("esquerda", "coluna"): "placa_esquinao_coluna",
        ("esquerda", "topo"): "placa_esquinao_topo",
        ("direita", "rodape"): "placa_esquinao_rodape",
        ("direita", "coluna"): "placa_esquinao_coluna",
        ("direita", "topo"): "placa_esquinao_topo",
    }
    for face, ang in rot.items():
        for (parte, nivel), modelo in modelos_por_parte_nivel.items():
            for lit in ("true", "false"):
                placas["facing=%s,parte=%s,nivel=%s,lit=%s" % (
                    face, parte, nivel, lit)] = {
                    "model": "intoxicantes:block/" + modelo,
                    "y": ang, "uvlock": False}
    wjson(os.path.join(ASSETS, "blockstates", "placa_esquinao.json"),
          {"variants": placas})

    # PAINEL DE LED CRAFTÁVEL (v1.2.36): a TV de tela plana — PAINEL FINO de
    # 3px colado na face da parede. Mesma tabela FORNALHA do letreiro
    # (facing=north → y=0 → a tela/face north do elemento encara o leitor).
    painel_led = {}
    for face, ang in rot.items():
        for telas in ("1", "2", "3"):
            for lit in ("true", "false"):
                painel_led["facing=%s,telas=%s,lit=%s" % (face, telas, lit)] = {
                    "model": "intoxicantes:block/painel_led",
                    "y": ang, "uvlock": False}
    wjson(os.path.join(ASSETS, "blockstates", "painel_led.json"),
          {"variants": painel_led})

# ---------------------------------------------------------------- modelos de bloco
def gen_crop_models(crop):
    for st in range(1, 5):
        texture = "%s_parreira_raiz_stage%d" % (crop, st) if crop == "uva" else "%s_stage%d" % (crop, st)
        wjson(os.path.join(ASSETS, "models", "block", "%s_plant_stage%d.json" % (crop, st)),
              {"parent": "minecraft:block/cross", "textures": {"cross": "intoxicantes:block/" + texture}})
    for suffix in ("ripe", "dormant"):
        # A raiz é uma muda sem cachos duplicados: o BER desenha os frutos no alto.
        texture = "uva_parreira_raiz_stage4" if crop == "uva" else "%s_stage4_%s" % (crop, suffix)
        wjson(os.path.join(ASSETS, "models", "block", "%s_plant_%s.json" % (crop, suffix)),
              {"parent": "minecraft:block/cross", "textures": {"cross": "intoxicantes:block/" + texture}})

def gen_block_models():
    for crop in CROPS:
        gen_crop_models(crop)
    for nome, lit in (("lampada_uv", True), ("lampada_uv_off", False)):
        wjson(os.path.join(ASSETS, "models", "block", nome + ".json"), modelo_lampada_uv(lit))

    for nome, modelo in modelos_poste().items():
        wjson(os.path.join(ASSETS, "models", "block", nome + ".json"), modelo)

    # ASFALTO do estacionamento (v1.2.19)
    wjson(os.path.join(ASSETS, "models", "block", "asfalto.json"),
          {"parent": "minecraft:block/cube_all",
           "textures": {"all": "intoxicantes:block/asfalto"}})

    # ---- PROJETOR DE HOLOGRAMA (v1.2.60): pedestal 7px — base de aço liso,
    # anel de projeção no topo (a lente emissiva) e núcleo que brilha quando
    # LIGADO. O holograma em si é desenhado por código (HologramaRenderer).
    wjson(os.path.join(ASSETS, "models", "block", "holograma.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "base": "intoxicantes:block/holograma_base",
            "anel": "intoxicantes:block/holograma_anel",
            "core": "intoxicantes:block/holograma_core",
            "particle": "intoxicantes:block/holograma_base"},
        "elements": [
            # base larga (0..5) — o corpo de aço
            {"from": [1, 0, 1], "to": [15, 5, 15], "faces": {
                "north": {"texture": "#base"}, "south": {"texture": "#base"},
                "west": {"texture": "#base"}, "east": {"texture": "#base"},
                "up": {"texture": "#base"}, "down": {"texture": "#base", "cullface": "down"}}},
            # ombro (5..6) — chanfro pro anel
            {"from": [2, 5, 2], "to": [14, 6, 14], "faces": {
                "north": {"texture": "#base"}, "south": {"texture": "#base"},
                "west": {"texture": "#base"}, "east": {"texture": "#base"},
                "up": {"texture": "#base"}, "down": {"texture": "#base"}}},
            # ANEL DE PROJEÇÃO (6..7) — a lente emissiva verde
            {"from": [3, 6, 3], "to": [13, 7, 13], "faces": {
                "north": {"texture": "#anel"}, "south": {"texture": "#anel"},
                "west": {"texture": "#anel"}, "east": {"texture": "#anel"},
                "up": {"texture": "#anel"}, "down": {"texture": "#anel"}}},
            # núcleo (7..8) — o emissor que aponta pra cima
            {"from": [5, 7, 5], "to": [11, 8, 11], "faces": {
                "north": {"texture": "#core"}, "south": {"texture": "#core"},
                "west": {"texture": "#core"}, "east": {"texture": "#core"},
                "up": {"texture": "#core"}, "down": {"texture": "#core"}}}]})
    # APAGADO (ligado=false): mesmo corpo, anel morto
    wjson(os.path.join(ASSETS, "models", "block", "holograma_off.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "base": "intoxicantes:block/holograma_base",
            "anel": "intoxicantes:block/holograma_anel_off",
            "core": "intoxicantes:block/holograma_core",
            "particle": "intoxicantes:block/holograma_base"},
        "elements": [
            {"from": [1, 0, 1], "to": [15, 5, 15], "faces": {
                "north": {"texture": "#base"}, "south": {"texture": "#base"},
                "west": {"texture": "#base"}, "east": {"texture": "#base"},
                "up": {"texture": "#base"}, "down": {"texture": "#base", "cullface": "down"}}},
            {"from": [2, 5, 2], "to": [14, 6, 14], "faces": {
                "north": {"texture": "#base"}, "south": {"texture": "#base"},
                "west": {"texture": "#base"}, "east": {"texture": "#base"},
                "up": {"texture": "#base"}, "down": {"texture": "#base"}}},
            {"from": [3, 6, 3], "to": [13, 7, 13], "faces": {
                "north": {"texture": "#anel"}, "south": {"texture": "#anel"},
                "west": {"texture": "#anel"}, "east": {"texture": "#anel"},
                "up": {"texture": "#anel"}, "down": {"texture": "#anel"}}},
            {"from": [5, 7, 5], "to": [11, 8, 11], "faces": {
                "north": {"texture": "#core"}, "south": {"texture": "#core"},
                "west": {"texture": "#core"}, "east": {"texture": "#core"},
                "up": {"texture": "#core"}, "down": {"texture": "#core"}}}]})

    # ---- LETREIRO DO ESQUINÃO (v1.2.31): DISPLAY DE FACHADA — caixa custom
    # (o TEXTO é o BER)
    # PAINEL/EXTENSÃO: bloco CHEIO (0..16 nos 3 eixos) — a faixa É a parede
    # da fachada, montada acima da porta (sem torres, sem flutuar na frente
    # da entrada). CONVENÇÃO DA FORNALHA: facing=north → blockstate y=0 → a
    # face LOCAL north (−Z) encara o leitor; "front" na NORTH e "back" na
    # SOUTH. O renderer desenha o LED nos dois lados (−Z e +Z locais).
    wjson(os.path.join(ASSETS, "models", "block", "placa_esquinao.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            # v1.2.31: frente = TELA contínua (sem moldura por bloco — a faixa
            # da fachada é UM display só); a moldura verde fica na placa avulsa
            "front": "intoxicantes:block/placa_esquinao_tela",
            "back": "intoxicantes:block/placa_esquinao_back",
            "metal": "intoxicantes:block/placa_esquinao_back",
            "particle": "intoxicantes:block/placa_esquinao_back"},
        "elements": [{
            "from": [0, 0, 0], "to": [16, 16, 16],
            "faces": {
                "north": {"texture": "#front"},
                "south": {"texture": "#back"},
                "up": {"texture": "#metal"},
                "down": {"texture": "#metal"},
                "west": {"texture": "#metal"},
                "east": {"texture": "#metal"}}}]})
    # PAINEL DE LED (v1.2.36): a TV FINA — 3px de espessura (z13..16), tela
    # na face NORTH local (convenção da fornalha do letreiro) e moldura
    # metálica no resto. Reusa as texturas do letreiro (tela contínua sem
    # moldura por bloco). O texto de LED é desenhado por código
    # (PainelLedRenderer) no plano da tela.
    wjson(os.path.join(ASSETS, "models", "block", "painel_led.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "tela": "intoxicantes:block/placa_esquinao_tela",
            "moldura": "intoxicantes:block/placa_esquinao_back",
            "particle": "intoxicantes:block/placa_esquinao_back"},
        "elements": [{
            "from": [0, 0, 13], "to": [16, 16, 16],
            "faces": {
                "north": {"texture": "#tela"},
                "south": {"texture": "#moldura"},
                "up": {"texture": "#moldura"},
                "down": {"texture": "#moldura"},
                "west": {"texture": "#moldura"},
                "east": {"texture": "#moldura"}}}]})
    # ---- ANATOMIA VELHA (1.2.24–1.2.30, mantida só pros saves migrarem):
    # TORRE — RODAPÉ: pedestal de concreto com a base da coluna em cima
    wjson(os.path.join(ASSETS, "models", "block", "placa_esquinao_rodape.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "ped": "intoxicantes:block/placa_esquinao_rodape",
            "post": "intoxicantes:block/placa_esquinao_coluna",
            "particle": "intoxicantes:block/placa_esquinao_rodape"},
        "elements": [
            {"from": [4, 0, 4], "to": [12, 10, 12], "faces": {
                "north": {"texture": "#ped"}, "south": {"texture": "#ped"},
                "west": {"texture": "#ped"}, "east": {"texture": "#ped"},
                "up": {"texture": "#ped"}, "down": {"texture": "#ped"}}},
            {"from": [6, 10, 6], "to": [10, 16, 10], "faces": {
                "north": {"texture": "#post"}, "south": {"texture": "#post"},
                "west": {"texture": "#post"}, "east": {"texture": "#post"},
                "up": {"texture": "#post"}, "down": {"texture": "#post"}}}]})
    # TORRE — COLUNA: metal fino no centro (a antiga, agora com cinta)
    wjson(os.path.join(ASSETS, "models", "block", "placa_esquinao_coluna.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "post": "intoxicantes:block/placa_esquinao_coluna",
            "particle": "intoxicantes:block/placa_esquinao_coluna"},
        "elements": [{
            "from": [6, 0, 6], "to": [10, 16, 10],
            "faces": {
                "north": {"texture": "#post"},
                "south": {"texture": "#post"},
                "up": {"texture": "#post"},
                "down": {"texture": "#post"},
                "west": {"texture": "#post"},
                "east": {"texture": "#post"}}}]})
    # TORRE — TOPO: capitel (coroa larga) segurando o painel
    wjson(os.path.join(ASSETS, "models", "block", "placa_esquinao_topo.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "cap": "intoxicantes:block/placa_esquinao_rodape",
            "post": "intoxicantes:block/placa_esquinao_coluna",
            "particle": "intoxicantes:block/placa_esquinao_rodape"},
        "elements": [
            {"from": [6, 0, 6], "to": [10, 12, 10], "faces": {
                "north": {"texture": "#post"}, "south": {"texture": "#post"},
                "west": {"texture": "#post"}, "east": {"texture": "#post"},
                "up": {"texture": "#post"}, "down": {"texture": "#post"}}},
            {"from": [4, 12, 4], "to": [12, 16, 12], "faces": {
                "north": {"texture": "#cap"}, "south": {"texture": "#cap"},
                "west": {"texture": "#cap"}, "east": {"texture": "#cap"},
                "up": {"texture": "#cap"}, "down": {"texture": "#cap"}}}]})

# ---------------------------------------------------------------- modelos/definicoes de item
def gen_item_models():
    # Os seis saquinhos 3D pertencem exclusivamente a gen_sementes.py.
    # Regenerar culturas não pode sobrescrevê-los com modelos de sprite.

    # Produtos botânicos pertencem a gen_catalogo.py; uva compartilha o cacho.
    gen_cacho_models()
    wjson(os.path.join(ASSETS, "models", "item", "lampada_uv.json"),
          {"parent": "intoxicantes:block/lampada_uv"})
    wjson(os.path.join(ASSETS, "items", "lampada_uv.json"),
          item_def("intoxicantes:item/lampada_uv"))

    # As três partes do poste inteiro em miniatura, sem reduzir o item à base.
    wjson(os.path.join(ASSETS, "models", "item", "poste_luz.json"),
          modelo_poste_item())
    wjson(os.path.join(ASSETS, "items", "poste_luz.json"),
          item_def("intoxicantes:item/poste_luz"))
    # v1.2.19: asfalto (item; é obtido via criativo/comando)
    wjson(os.path.join(ASSETS, "models", "item", "asfalto.json"),
          {"parent": "intoxicantes:block/asfalto"})
    wjson(os.path.join(ASSETS, "items", "asfalto.json"),
          item_def("intoxicantes:block/asfalto"))

    # v1.2.23: a placa no inventário ganhou corpo 3D (o painel suspenso do
    # modelo do bloco) — o item model herda a geometria em vez do sprite chato
    wjson(os.path.join(ASSETS, "models", "item", "placa_esquinao.json"),
          {"parent": "intoxicantes:block/placa_esquinao"})
    wjson(os.path.join(ASSETS, "items", "placa_esquinao.json"),
          item_def("intoxicantes:block/placa_esquinao"))

    # v1.2.36: PAINEL DE LED (item) — a TV de tela plana no inventário (o
    # modelo fino de bloco, igual a placa)
    wjson(os.path.join(ASSETS, "models", "item", "painel_led.json"),
          {"parent": "intoxicantes:block/painel_led"})
    wjson(os.path.join(ASSETS, "items", "painel_led.json"),
          item_def("intoxicantes:block/painel_led"))

    # v1.2.60: PROJETOR DE HOLOGRAMA (item) — o pedestal 3D no inventário
    wjson(os.path.join(ASSETS, "models", "item", "holograma.json"),
          {"parent": "intoxicantes:block/holograma"})
    wjson(os.path.join(ASSETS, "items", "holograma.json"),
          item_def("intoxicantes:block/holograma"))
    # v1.2.60: PENDRIVE DE PLANTA (item) — sprite HD próprio
    wjson(os.path.join(ASSETS, "models", "item", "pendrive_planta.json"),
          {"parent": "minecraft:item/generated",
           "textures": {"layer0": "intoxicantes:item/pendrive_planta"}})
    wjson(os.path.join(ASSETS, "items", "pendrive_planta.json"),
          item_def("intoxicantes:item/pendrive_planta"))

    # blocos das plantas: item do bloco usa sprite da planta madura
    for crop in CROPS:
        wjson(os.path.join(ASSETS, "models", "item", crop + "_plant.json"),
              {"parent": "minecraft:item/generated",
               "textures": {"layer0": "intoxicantes:block/" + crop + "_stage4_ripe"}})
        wjson(os.path.join(ASSETS, "items", crop + "_plant.json"),
              item_def("intoxicantes:item/" + crop + "_plant"))

# ---------------------------------------------------------------- loot tables de colheita
def gen_loot_tables(crops=CROPS, only_crops=False):
    for crop in crops:
        produto = {"maconha": "maconha_seda", "lupulo": "lupulo", "uva": "uva",
                   "cafe": "cafe_verde", "papoula": "opio"}[crop]
        # esperar a maturacao VALE 3x o produto (vs 1x na so-madura) e DEVOLVE a
        # semente garantida — o loop da plantacao nunca morre por esperar demais.
        # v1.2.16: a conta é 1 (pool madura) + EXTRA (pool ripe) = TOTAL; pra 3x
        # o extra é 2 — o set_count 3 antigo dava 4x (a regeneração expôs a
        # deriva entre gerador e tables afinadas à mão; agora gerador = disk)
        bonus = {"maconha": 2, "lupulo": 2, "uva": 2, "cafe": 2, "papoula": 2}[crop]
        mature = {"type": "minecraft:match_block", "blocks": "intoxicantes:" + crop + "_plant", "state": {"age": "4"}}
        ripe = {"type": "minecraft:match_block", "blocks": "intoxicantes:" + crop + "_plant", "state": {"age": "4", "uv_age": "3"}}
        # 26.3 uses singular condition/modifier. Old plural fields are silently ignored.
        # Always return the planted seed; only grown plants yield a sellable product.
        table = {
            "type": "minecraft:block",
            "modifier": {"type": "minecraft:explosion_decay"},
            "pools": [
                {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "intoxicantes:semente_" + crop}]},
                {"rolls": 1, "condition": mature, "entries": [{"type": "minecraft:item", "name": "intoxicantes:" + produto}]},
                {"rolls": 1, "condition": ripe, "entries": [{
                    "type": "minecraft:item", "name": "intoxicantes:" + produto,
                    "modifier": {"type": "minecraft:set_count", "count": bonus}
                }]},
                {"rolls": 1, "condition": ripe, "entries": [{
                    "type": "minecraft:item", "name": "intoxicantes:semente_" + crop
                }]}
            ],
            "random_sequence": "intoxicantes:blocks/" + crop + "_plant"
        }
        wjson(os.path.join(DATA, "loot_table", "blocks", crop + "_plant.json"), table)

    if only_crops:
        return

    # lampada: dropa a si mesma
    wjson(os.path.join(DATA, "loot_table", "blocks", "lampada_uv.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "intoxicantes:lampada_uv"}]}],
        "random_sequence": "intoxicantes:blocks/lampada_uv"
    })

    # v1.2.60: holograma dropa a si mesmo
    wjson(os.path.join(DATA, "loot_table", "blocks", "holograma.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "intoxicantes:holograma"}]}],
        "random_sequence": "intoxicantes:blocks/holograma"
    })

    # letreiro: qualquer parte dropa o item (o playerWillDestroy derruba o
    # resto do multi-bloco SEM drop — a placa é um objeto só)
    wjson(os.path.join(DATA, "loot_table", "blocks", "placa_esquinao.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "intoxicantes:placa_esquinao"}]}],
        "random_sequence": "intoxicantes:blocks/placa_esquinao"
    })

    # painel de LED (v1.2.36): dropa 1 item POR TELA da linha (TELAS 1..3)
    wjson(os.path.join(DATA, "loot_table", "blocks", "painel_led.json"), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{
                "type": "minecraft:item", "name": "intoxicantes:painel_led",
                "functions": [{"function": "minecraft:set_count",
                    "count": {"type": "minecraft:state_provider",
                        "state": {"property": "telas"}}}]
            }]
        }],
        "random_sequence": "intoxicantes:blocks/painel_led"
    })

    # poste de luz: dropa a si mesmo (v1.2.19)
    wjson(os.path.join(DATA, "loot_table", "blocks", "poste_luz.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "intoxicantes:poste_luz"}]}],
        "random_sequence": "intoxicantes:blocks/poste_luz"
    })
    # asfalto: dropa a si mesmo (v1.2.19)
    wjson(os.path.join(DATA, "loot_table", "blocks", "asfalto.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "intoxicantes:asfalto"}]}],
        "random_sequence": "intoxicantes:blocks/asfalto"
    })

# ---------------------------------------------------------------- receitas
def gen_recipes():
    def shapeless(ingredients, result, count=1):
        return {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "ingredients": ingredients,
            "result": {"id": result, "count": count}
        }

    # bebidas agora usam os produtos das plantacoes
    wjson(os.path.join(DATA, "recipe", "cerveja.json"), shapeless(
        ["intoxicantes:lupulo", "intoxicantes:lupulo", "minecraft:glass_bottle", "minecraft:wheat"],
        "intoxicantes:cerveja", 2))
    wjson(os.path.join(DATA, "recipe", "vinho.json"), shapeless(
        ["intoxicantes:uva", "intoxicantes:uva", "intoxicantes:uva", "minecraft:glass_bottle"],
        "intoxicantes:vinho", 1))
    wjson(os.path.join(DATA, "recipe", "cachaca.json"), shapeless(
        ["intoxicantes:cana_de_acucar", "intoxicantes:cana_de_acucar", "minecraft:glass_bottle"],
        "intoxicantes:cachaca", 1))
    wjson(os.path.join(DATA, "recipe", "rum.json"), shapeless(
        ["intoxicantes:cana_de_acucar", "intoxicantes:cana_de_acucar",
         "intoxicantes:cana_de_acucar", "minecraft:glass_bottle"],
        "intoxicantes:rum", 1))

    # heroina agora parte do opio colhido da papoula
    wjson(os.path.join(DATA, "recipe", "heroina.json"), shapeless(
        ["intoxicantes:opio", "intoxicantes:opio", "minecraft:redstone", "minecraft:slime_ball"],
        "intoxicantes:heroina", 1))

    # v1.2.39: CIGARRO CAMEL — papel + folha dourada (tabaco do Juça)
    wjson(os.path.join(DATA, "recipe", "cigarro_camel.json"), shapeless(
        ["minecraft:paper", "minecraft:golden_carrot"],
        "intoxicantes:cigarro_camel", 3))
    # v1.2.39: CAMISA DO MATANZA — lã preta + couro (a farda do rock)
    wjson(os.path.join(DATA, "recipe", "camisa_matanza.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["W W", "WWW", "WWW"],
        "key": {
            "W": "minecraft:black_wool"
        },
        "result": {"id": "intoxicantes:camisa_matanza", "count": 1}
    })

    # v1.2.36: PAINEL DE LED CRAFTÁVEL — vidro + redstone + iron (a TV da
    # fachada, programável pela Central de Comando)
    wjson(os.path.join(DATA, "recipe", "painel_led.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["III", "GRG", "GGG"],
        "key": {
            "I": "minecraft:iron_ingot",
            "G": "minecraft:glass",
            "R": "minecraft:redstone_block"
        },
        "result": {"id": "intoxicantes:painel_led", "count": 1}
    })

    # v1.2.38: o CONTROLE REMOTO — a Central de Comando em item (aponta pro
    # painel/letreiro e edita): vidro de tela, ferro no corpo, redstone de
    # dentro e botão de pedra (o "OK")
    wjson(os.path.join(DATA, "recipe", "central_comando.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["WIW", "GRG", " S "],
        "key": {
            "W": "minecraft:white_stained_glass",
            "I": "minecraft:iron_ingot",
            "G": "minecraft:glass",
            "R": "minecraft:redstone_block",
            "S": "minecraft:stone_button"
        },
        "result": {"id": "intoxicantes:central_comando", "count": 1}
    })

    # lampada UV
    wjson(os.path.join(DATA, "recipe", "lampada_uv.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["IWI", "GGG", "GDG"],
        "key": {
            "I": "minecraft:iron_ingot",
            "W": "minecraft:white_stained_glass",
            "G": "minecraft:glass",
            "D": "minecraft:glowstone_dust"
        },
        "result": {"id": "intoxicantes:lampada_uv", "count": 1}
    })

    # v1.2.60: PROJETOR DE HOLOGRAMA — ferro na base, lente de vidro, olho de
    # ender (projeta), redstone (liga/desliga) e dia-branco no pino
    wjson(os.path.join(DATA, "recipe", "holograma.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": [" IE ", "IRRI", "QDQQ", "QQQQ"],
        "key": {
            "I": "minecraft:iron_ingot",
            "E": "minecraft:ender_eye",
            "R": "minecraft:redstone_block",
            "Q": "minecraft:smooth_stone",
            "D": "minecraft:diamond"
        },
        "result": {"id": "intoxicantes:holograma", "count": 1}
    })

    # v1.2.60: PENDRIVE DE PLANTA — ferro (carcaça), ouro (contato), vidro
    # (janela do LED) e lápis-lazúli (a "memória")
    wjson(os.path.join(DATA, "recipe", "pendrive_planta.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["GIG", "GLG", " I "],
        "key": {
            "G": "minecraft:glass",
            "I": "minecraft:iron_ingot",
            "L": "minecraft:lapis_lazuli"
        },
        "result": {"id": "intoxicantes:pendrive_planta", "count": 1}
    })

# ---------------------------------------------------------------- texturas HD (v1.2.60)
def gen_texturas_holograma():
    """Texturas 128x128 do PROJETOR DE HOLOGRAMA e do PENDRIVE, pintadas
    pixel a pixel (regra do AGENTS.md: textura nova nasce em alta resolução)."""
    import math
    import random as _random
    from PIL import Image, ImageDraw

    TEX = os.path.join(ASSETS, "textures")
    rnd = _random.Random(60)

    def grao(g, x0, y0, x1, y1, tons, n, r):
        for _ in range(n):
            x = r.randint(x0, x1 - 1)
            y = r.randint(y0, y1 - 1)
            g.point((x, y), fill=r.choice(tons))

    # ---- BASE: aço escuro escovado com rebites nos cantos e placa de identificação
    base = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    g = base.load()
    d = ImageDraw.Draw(base)
    for y in range(128):
        for x in range(128):
            t = 52 + ((x * 7 + y * 13) % 5) * 4 + (y % 2) * 3
            g[x, y] = (t, t + 2, t + 6, 255)
    # placas de aço (linhas de junta)
    for yj in (42, 85):
        d.line([(0, yj), (127, yj)], fill=(34, 36, 42, 255), width=3)
    for xj in (63,):
        d.line([(xj, 0), (xj, 127)], fill=(34, 36, 42, 255), width=3)
    grao(d, 0, 0, 128, 128, [(30, 32, 38, 255), (78, 82, 92, 255), (95, 100, 112, 255)], 2400, rnd)
    # rebites nos cantos de cada placa
    for rx, ry in [(10, 10), (118, 10), (10, 118), (118, 118), (10, 52), (118, 52), (52, 95), (74, 95)]:
        d.ellipse([rx - 4, ry - 4, rx + 4, ry + 4], fill=(120, 126, 138, 255))
        d.ellipse([rx - 2, ry - 2, rx, ry], fill=(160, 168, 182, 255))
    base.save(os.path.join(TEX, "block", "holograma_base.png"))

    # ---- ANEL DE PROJEÇÃO: lente verde-LED com anéis concêntricos (acesa e morta)
    for sufixo, centro, brilho in [("", (57, 255, 110), 1.0), ("_off", (40, 70, 52), 0.35)]:
        anel = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
        g = anel.load()
        d = ImageDraw.Draw(anel)
        cx = cy = 64
        for y in range(128):
            for x in range(128):
                r2 = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                if r2 > 62:
                    g[x, y] = (24, 26, 30, 255)  # moldura de aço
                    continue
                anel_t = (int(38 + 26 * ((x * 3 + y * 5) % 4)), int(40 + 18 * ((x + y) % 3)), 46, 255)
                # anéis concêntricos verdes (de fora pra dentro, o "furo" da lente)
                if 44 <= r2 < 62:
                    tom = tuple(min(255, int(c * (0.8 + 0.5 * ((x + y) % 2)))) for c in centro)
                    g[x, y] = tom
                elif 20 <= r2 < 44:
                    g[x, y] = anel_t
                else:
                    # miolo: o furo da lente escuro com brilho central
                    furo = max(0.0, 1.0 - r2 / 20.0)
                    g[x, y] = (int(6 + centro[0] * 0.25 * furo * brilho),
                               int(10 + centro[1] * 0.35 * furo * brilho),
                               int(12 + centro[2] * 0.25 * furo * brilho), 255)
        if not sufixo:
            # faíscas de LED (os pontos acesos do anel externo)
            for _ in range(90):
                ang = rnd.uniform(0, 6.283)
                rr = rnd.uniform(46, 60)
                px, py = int(cx + rr * math.cos(ang)), int(cy + rr * math.sin(ang))
                d.point((px, py), fill=(200, 255, 220, 255))
        anel.save(os.path.join(TEX, "block", "holograma_anel" + sufixo + ".png"))

    # ---- NÚCLEO: o emissor (verde quente no centro, aço frio na borda)
    core = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    g = core.load()
    for y in range(128):
        for x in range(128):
            r2 = ((x - 64) ** 2 + (y - 64) ** 2) ** 0.5
            if r2 > 60:
                t = 40 + (x * y) % 9
                g[x, y] = (t, t + 2, t + 5, 255)
            else:
                f = max(0.0, 1.0 - r2 / 60.0)
                g[x, y] = (int(30 + 30 * f), int(120 + 130 * f), int(60 + 70 * f), 255)
    core.save(os.path.join(TEX, "block", "holograma_core.png"))

    # ---- PENDRIVE (item): carcaça de ferro, janela de vidro com LED verde e contato dourado
    pend = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(pend)
    # corpo
    d.rounded_rectangle([36, 14, 92, 116], radius=10, fill=(58, 62, 70, 255), outline=(24, 26, 30, 255), width=3)
    # janela de vidro com o LED de planta (a cara do holograma)
    d.rounded_rectangle([46, 30, 82, 74], radius=6, fill=(12, 20, 16, 255), outline=(90, 96, 108, 255), width=2)
    d.rounded_rectangle([52, 38, 76, 66], radius=4, fill=(20, 46, 30, 255))
    # cubinho wireframe verde na janela (o "conteúdo" da planta)
    d.rectangle([56, 44, 72, 60], outline=(57, 255, 110, 255), width=2)
    d.line([(56, 44), (64, 38), (72, 44)], fill=(120, 255, 160, 255), width=2)
    d.line([(64, 38), (64, 52)], fill=(120, 255, 160, 255), width=1)
    d.line([(56, 52), (72, 52)], fill=(57, 255, 110, 255), width=1)
    # contatos dourados
    for i in range(3):
        y0 = 84 + i * 9
        d.rounded_rectangle([50, y0, 78, y0 + 6], radius=2, fill=(222, 178, 74, 255), outline=(140, 108, 34, 255), width=1)
    # grão de ruído no corpo
    grao(d, 38, 16, 90, 114, [(44, 48, 56, 255), (74, 80, 90, 255)], 900, rnd)
    pend.save(os.path.join(TEX, "item", "pendrive_planta.png"))

    print("OK: texturas HD do holograma + pendrive (128x128) geradas")

def display_objeto(altura=10, escala=1):
    """Poses centradas no modelo; Minecraft espelha a mão esquerda."""
    return {
        "gui": {"rotation": [20, -32, 0], "translation": [0, (8-altura/2)*escala, 0], "scale": [escala]*3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 4, 0], "scale": [.5]*3},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 8-altura/2, 0], "scale": [.85]*3},
        **{mao: {"rotation": [0, 165, -8], "translation": [-1, 7, -1.25], "scale": [.7]*3}
           for mao in ("firstperson_righthand", "firstperson_lefthand")},
        **{mao: {"rotation": [90, 0, -8], "translation": [0, 2.5, .5], "scale": [.75]*3}
           for mao in ("thirdperson_righthand", "thirdperson_lefthand")},
    }


def cuboide(nome, inicio, fim, uv, textura="#atlas"):
    return {"name": nome, "from": [round(v, 5) for v in inicio],
            "to": [round(v, 5) for v in fim],
            "faces": {face: {"texture": textura, "uv": uv}
                      for face in ("north", "south", "east", "west", "up", "down")}}


def modelo_cacho(maduro=False):
    """Geometria única: item UVA e BER nativo compartilham estes mesmos cuboides.

    Centro do primeiro anel em (8,8,8): ao renderizar NONE, o Minecraft subtrai
    oito unidades. Medidas e posição dos frutos preservam o cacho da parreira.
    """
    uv = [65/8, 65/8, 127/8, 127/8] if maduro else [1/8, 65/8, 63/8, 127/8]
    elementos = [cuboide("pedunculo", [7.8, 8.48, 7.8], [8.2, 11.04, 8.2],
                         [1/8, 1/8, 63/8, 63/8])]
    for camada in range(4):
        quantidade = 4 if camada == 0 else 1 if camada == 3 else 3
        for n in range(quantidade):
            a = n*math.tau/quantidade + camada*.7
            raio = 0 if camada == 3 else .078*16
            centro = [8+math.cos(a)*raio, 8-camada*.094*16, 8+math.sin(a)*raio]
            s = (.135-camada*.011)*16
            for p, fatores in enumerate(((.76, 1, .76), (1, .61, .8), (.8, .65, 1))):
                tam = [s*f for f in fatores]
                elementos.append(cuboide(f"uva_{camada}_{n}_{p}",
                    [c-t/2 for c,t in zip(centro,tam)], [c+t/2 for c,t in zip(centro,tam)], uv))
    return {"credit": "SNC Adventures | tools/gen_farm_resources.py | cacho compartilhado",
            "ambientocclusion": False, "gui_light": "front", "texture_size": [128,128],
            "textures": {"atlas": "intoxicantes:block/parreira_atlas", "particle": "intoxicantes:block/parreira_atlas"},
            "display": {**display_objeto(),
                "gui": {"rotation": [20,-32,0], "translation": [0,1.15,0], "scale": [1.4]*3}},
            "elements": elementos}


def gen_cacho_models():
    for nome, maduro in (("parreira_cacho", False), ("parreira_cacho_maduro", True)):
        wjson(os.path.join(ASSETS, "models", "block", nome+".json"), modelo_cacho(maduro))
    wjson(os.path.join(ASSETS, "models", "item", "uva.json"), {"parent": "intoxicantes:block/parreira_cacho"})
    wjson(os.path.join(ASSETS, "items", "uva.json"), {"model": {
        "type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0,
        "fallback": {"type": "minecraft:model", "model": "intoxicantes:item/uva"},
        "entries": [{"threshold": 3, "model": {"type": "minecraft:model",
                    "model": "intoxicantes:block/parreira_cacho_maduro"}}]}})


def atlas_lampada_uv():
    """Metal anodizado, tubo violeta, difusor apagado e identificação UV128."""
    from PIL import Image, ImageDraw
    img = Image.new("RGBA", (128,128))
    paleta = ((87,91,110), (41,38,52), (152,98,213), (75,62,92))
    for y in range(128):
        for x in range(128):
            cel = (y//64)*2+x//64
            xx,yy = x%64,y%64
            shade = 17*(1-yy/63)-11*xx/63 + (((x*193+y*389+x*y*17)%19)-9)*.55
            if cel == 0: shade += 5 if xx%5 == 0 else -1
            if cel >= 2: shade += 24*max(0,1-abs(xx-25)/32)
            img.putpixel((x,y),tuple(max(0,min(255,round(c+shade))) for c in paleta[cel])+(255,))
    d = ImageDraw.Draw(img)
    d.rectangle((67,4,123,29),fill=(29,26,39,255),outline=(148,119,180,255),width=2)
    # Letra UV pixelada, legível no frontispício da luminária.
    for i, letra in enumerate((("10001","10001","10001","10001","01110"),
                              ("10001","10001","01010","01010","00100"))):
        for y, linha in enumerate(letra):
            for x, valor in enumerate(linha):
                if valor == "1": d.rectangle((74+i*23+x*3,8+y*3,76+i*23+x*3,10+y*3),fill=(211,192,240,255))
    return img


def modelo_lampada_uv(lit=True):
    uv = {"metal": [.125,.125,7.875,7.875], "escuro": [8.125,4.25,15.875,7.875],
          "placa": [8.375,.5,15.5,3.75], "tubo": ([.125,8.125,7.875,15.875] if lit else [8.125,8.125,15.875,15.875])}
    els=[]
    def box(n,f,t,m): els.append(cuboide(n,f,t,uv[m]))
    box("carcaca",[1,9,3],[15,10.6,13],"metal")
    for z in (3,12.3): box("moldura",[1,7.8,z],[15,9,z+.7],"escuro")
    for x in (1,14.3): box("tampa",[x,7.8,3],[x+.7,10.6,13],"metal")
    for z in (5,10):
        box("tubo_miolo",[3,6.8,z],[13,8.4,z+1],"tubo")
        box("tubo_lateral",[3,7.1,z-.3],[13,8.1,z+1.3],"tubo")
        for x in (2.1,12.9): box("soquete",[x,6.65,z-.4],[x+1,8.6,z+1.4],"escuro")
    for x in (3,12): box("suporte",[x,10.6,7],[x+1,16,9],"metal")
    for x in (2.7,12.1): box("parafuso",[x,10.61,4],[x+.45,10.9,4.45],"escuro")
    etiqueta=cuboide("placa_uv",[5,8.05,2.96],[11,9.9,3],uv["escuro"])
    etiqueta["faces"]["north"]["uv"]=uv["placa"]
    els.append(etiqueta)
    return {"credit": "SNC Adventures | tools/gen_farm_resources.py", "texture_size": [128,128],
            "textures": {"atlas":"intoxicantes:block/lampada_uv_atlas", "particle":"intoxicantes:block/lampada_uv_atlas"},
            "display": display_objeto(16,.85), "elements":els}


def modelos_poste():
    """Mesmas partes canônicas para o mundo e a miniatura completa do item."""
    face = lambda m: {s:{"texture":"#"+m} for s in ("north","south","west","east","up","down")}
    def model(parts, light="on"):
        return {"textures":{"ped":"intoxicantes:block/poste_ped","cap":"intoxicantes:block/poste_ped",
                "post":"intoxicantes:block/poste_luz","light":"intoxicantes:block/poste_luz_"+light,
                "particle":"intoxicantes:block/poste_luz"},
                "elements":[{"name":n,"from":f,"to":t,"faces":face(m)} for n,f,t,m in parts]}
    base=[("pedestal",[5,0,5],[11,6,11],"ped"),("arranque",[6,6,6],[10,16,10],"post")]
    corpo=[("coluna",[6,0,6],[10,16,10],"post")]
    topo=[("coluna",[6,0,6],[10,10,10],"post"),("capitel",[5,10,5],[11,11,11],"cap"),
          ("lente",[5,11,5],[11,13,11],"light"),("tampa",[4,13,4],[12,14,12],"post")]
    return {"poste_luz_base":model(base), "poste_luz_corpo":model(corpo),
            "poste_luz_topo_lit":model(topo), "poste_luz_topo_off":model(topo,"off")}


def modelo_poste_item():
    partes = modelos_poste()
    modelo = copy.deepcopy(partes["poste_luz_topo_lit"])
    elementos=[]
    for n,nome in enumerate(("poste_luz_base","poste_luz_corpo","poste_luz_topo_lit")):
        for e in copy.deepcopy(partes[nome]["elements"]):
            # Fixa UV antes da miniaturização: escala não muda o material.
            x0,y0,z0=e["from"]; x1,y1,z1=e["to"]
            recortes={"down":[x0,16-z1,x1,16-z0], "up":[x0,z0,x1,z1],
                "north":[16-x1,16-y1,16-x0,16-y0], "south":[x0,16-y1,x1,16-y0],
                "west":[z0,16-y1,z1,16-y0], "east":[16-z1,16-y1,16-z0,16-y0]}
            for face, dados in e["faces"].items(): dados["uv"]=recortes[face]
            for limite in ("from","to"):
                e[limite] = [8+(e[limite][0]-8)/3, (e[limite][1]+n*16)/3, 8+(e[limite][2]-8)/3]
            elementos.append(e)
    modelo.update(credit="SNC Adventures | poste completo de três partes", elements=elementos,
                  display=display_objeto(46/3,.95))
    return modelo


def identidade_outputs():
    modelos = ("models/block/parreira_cacho.json", "models/block/parreira_cacho_maduro.json",
        "models/item/uva.json", "items/uva.json", "models/block/lampada_uv.json", "models/block/lampada_uv_off.json",
        "models/item/lampada_uv.json", "items/lampada_uv.json", "models/item/poste_luz.json", "items/poste_luz.json",
        "textures/block/lampada_uv_atlas.png")
    return [os.path.join(ASSETS,path.replace("/",os.sep)) for path in modelos]


def gen_identidade():
    gen_cacho_models()
    for nome,lit in (("lampada_uv",True),("lampada_uv_off",False)):
        wjson(os.path.join(ASSETS,"models","block",nome+".json"),modelo_lampada_uv(lit))
    wjson(os.path.join(ASSETS,"models","item","lampada_uv.json"),{"parent":"intoxicantes:block/lampada_uv"})
    wjson(os.path.join(ASSETS,"items","lampada_uv.json"),item_def("intoxicantes:item/lampada_uv"))
    wjson(os.path.join(ASSETS,"models","item","poste_luz.json"),modelo_poste_item())
    wjson(os.path.join(ASSETS,"items","poste_luz.json"),item_def("intoxicantes:item/poste_luz"))
    os.makedirs(os.path.join(ASSETS,"textures","block"), exist_ok=True)
    atlas_lampada_uv().save(os.path.join(ASSETS,"textures","block","lampada_uv_atlas.png"))


def preview_entries():
    """Somente dados em memória, nunca grava assets de produção."""
    from PIL import Image
    def data(img):
        buf=io.BytesIO(); img.save(buf,format="PNG")
        return "data:image/png;base64,"+base64.b64encode(buf.getvalue()).decode("ascii")
    atlas=data(Image.open(os.path.join(ASSETS,"textures","block","parreira_atlas.png")))
    uv=data(atlas_lampada_uv())
    poste=modelo_poste_item()
    texturas={ref:data(Image.open(os.path.join(ASSETS,"textures",ref.split(":")[1]+".png")))
              for ref in set(poste["textures"].values())}
    return [
        {"id":"uva","name":"Uva · cacho compartilhado","category":"Item e mundo", "model":modelo_cacho(),
         "textures":{"intoxicantes:block/parreira_atlas":atlas},"variants":[{"name":"Maturação UV","model":modelo_cacho(True)}],
         "description":"Onze bagas voxel no cacho, com a mesma geometria e o mesmo atlas no inventário e na parreira. A colheita continua individual por cacho."},
        {"id":"lampada_uv","name":"Luminária UV","category":"Item e mundo","model":modelo_lampada_uv(),
         "textures":{"intoxicantes:block/lampada_uv_atlas":uv},"variants":[{"name":"Apagada","model":modelo_lampada_uv(False)}],
         "description":"Luminária de dois tubos, soquetes, carcaça metálica, suporte e identificação UV. Item e bloco são a mesma peça; redstone e maturação preservados."},
        {"id":"poste_luz","name":"Poste de luz completo","category":"Item e mundo","model":poste,"textures":texturas,
         "description":"Pedestal, coluna e luminária no mesmo item, com as três partes da geometria do poste colocado reduzidas proporcionalmente."}]


def parreira_outputs():
    """Manifesto fechado do modo --only: nunca toca outras culturas ou o pack."""
    return [
        os.path.join(ASSETS, "blockstates", "uva_plant.json"),
        *[os.path.join(ASSETS, "models", "block", "uva_plant_" + suffix + ".json")
          for suffix in ("stage1", "stage2", "stage3", "stage4", "dormant", "ripe")],
        os.path.join(DATA, "loot_table", "blocks", "uva_plant.json"),
        *[os.path.join(ASSETS, "textures", "block", "uva_parreira_raiz_stage%d.png" % n)
          for n in range(1, 5)],
        os.path.join(ASSETS, "textures", "block", "parreira_folha.png"),
        os.path.join(ASSETS, "textures", "block", "parreira_atlas.png"),
        os.path.join(ASSETS, "models", "block", "parreira_cacho.json"),
        os.path.join(ASSETS, "models", "block", "parreira_cacho_maduro.json"),
        os.path.join(ASSETS, "models", "item", "uva.json"),
        os.path.join(ASSETS, "items", "uva.json"),
    ]

def gen_parreira_texturas():
    """Arte nova 128×128, sem substituir as uva_stage* manuais de 1254×1254.

    A folha tem silhueta lobada, nervuras, borda escura e variação por pixel.
    O atlas possui casca e três acabamentos de uva; o BER usa luz do mundo.
    As raízes são mudas discretas, ancoradas na última linha, sem fruto 2D.
    """
    import math
    from PIL import Image, ImageDraw

    texture_dir = os.path.join(ASSETS, "textures", "block")
    os.makedirs(texture_dir, exist_ok=True)

    def noise(x, y, seed=0):
        return ((x * 193 + y * 389 + x * y * 17 + seed * 61) % 29) - 14

    folha = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    mask = Image.new("L", (128, 128), 0)
    md = ImageDraw.Draw(mask)
    contorno = [(64, 3), (75, 22), (91, 13), (90, 36), (119, 32),
                (109, 55), (125, 63), (106, 80), (115, 94), (91, 95),
                (93, 111), (76, 106), (64, 125), (53, 107), (35, 114),
                (35, 94), (14, 97), (23, 79), (3, 62), (20, 55),
                (11, 33), (37, 37), (34, 14), (52, 23)]
    md.polygon(contorno, fill=255)
    mp, pixels = mask.load(), folha.load()
    for y in range(128):
        for x in range(128):
            if not mp[x, y]:
                continue
            borda = any(not (0 <= x + dx < 128 and 0 <= y + dy < 128)
                        or not mp[x + dx, y + dy]
                        for dx, dy in ((-2, 0), (2, 0), (0, -2), (0, 2)))
            luz = 10 * math.sin(x * .085 + y * .032) - y * .075 + noise(x, y) * .55
            if borda:
                luz -= 22
            pixels[x, y] = tuple(max(0, min(255, int(c + luz))) for c in (119, 143, 77)) + (255,)
    fd = ImageDraw.Draw(folha)
    fd.line([(64, 124), (65, 21), (64, 8)], fill=(177, 192, 117, 255), width=2)
    for y, spread in ((39, 16), (58, 31), (78, 34), (98, 25)):
        for sign in (-1, 1):
            fd.line([(64, y + 14), (64 + sign * spread, y - 2)], fill=(152, 175, 93, 255), width=1)
    folha.putalpha(mask)
    folha.save(os.path.join(texture_dir, "parreira_folha.png"))

    atlas = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    ap = atlas.load()
    for y in range(128):
        for x in range(128):
            xx, yy = x % 64, y % 64
            if x < 64 and y < 64:
                grain = math.sin(xx * .39 + math.sin(yy * .05)) * 11
                shade = grain + noise(x, y, 1) * .45 - (13 if xx % 17 == 0 else 0)
                cor = (113, 95, 60)
            else:
                # Shading esférico discreto e reflexo no alto à esquerda.
                cx, cy = (xx - 30) / 32, (yy - 32) / 32
                shade = 18 * max(0, 1 - (cx * cx + cy * cy)) - yy * .19 + noise(x, y, 2) * .3
                if (xx - 19) ** 2 + (yy - 17) ** 2 < 37:
                    shade += 25
                cor = (123, 147, 76) if y < 64 else ((112, 73, 125) if x < 64 else (137, 85, 146))
            ap[x, y] = tuple(max(0, min(255, int(c + shade))) for c in cor) + (255,)
    atlas.save(os.path.join(texture_dir, "parreira_atlas.png"))

    for stage in range(1, 5):
        root = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
        draw = ImageDraw.Draw(root)
        height = (29, 40, 52, 64)[stage - 1]
        top = 127 - height
        points = [(63, 127), (65, 114), (62, 104), (66, top)]
        draw.line(points, fill=(63, 61, 35, 255), width=5)
        draw.line([(64, 127), (66, 114), (63, 104), (67, top)], fill=(132, 141, 77, 255), width=2)
        leaf_size = 20 + stage * 4
        for n in range(stage + 1):
            sign = -1 if n % 2 == 0 else 1
            cy = 118 - int((n + 1) * height / (stage + 2))
            cx = 64 + sign * (leaf_size // 3)
            draw.line([(64, cy + 4), (cx, cy)], fill=(99, 119, 61, 255), width=2)
            small = folha.resize((leaf_size, leaf_size), Image.Resampling.NEAREST)
            small = small.rotate(sign * (30 + n * 8), resample=Image.Resampling.NEAREST, expand=True)
            root.alpha_composite(small, (cx - small.width // 2, cy - small.height // 2))
        # Raízes finas tocam y127. A imagem não tem margem transparente embaixo.
        draw.line([(64, 119), (59, 127)], fill=(83, 66, 42, 255), width=2)
        draw.line([(64, 119), (69, 127)], fill=(112, 88, 51, 255), width=2)
        root.save(os.path.join(texture_dir, "uva_parreira_raiz_stage%d.png" % stage))
    print("OK: folhas, atlas e quatro mudas da parreira em 128x128")

def gen_parreira():
    gen_crop_blockstate("uva")
    gen_crop_models("uva")
    gen_loot_tables(("uva",), only_crops=True)
    gen_parreira_texturas()
    gen_cacho_models()
    print("OK: somente os 18 recursos da parreira; receitas, IDs e pack preservados")

def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--only", choices=("parreira", "identidade"), help="Gerar somente os recursos desta funcionalidade")
    parser.add_argument("--list", action="store_true", help="Listar saídas do modo --only, sem gravar recursos")
    args = parser.parse_args()
    if args.list:
        if not args.only:
            parser.error("--list precisa de --only parreira ou identidade")
        for path in (identidade_outputs() if args.only == "identidade" else parreira_outputs()):
            print(path.replace(os.sep, "/"))
        return
    if args.only == "identidade":
        gen_identidade()
        return
    if args.only == "parreira":
        gen_parreira()
        return
    gen_blockstates()
    gen_block_models()
    gen_item_models()
    gen_loot_tables()
    gen_recipes()
    gen_texturas_holograma()
    gen_identidade()
    gen_parreira_texturas()
    print("OK: blockstates, modelos, items, loot tables e receitas de farm gerados")

if __name__ == "__main__":
    main()
