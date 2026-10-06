#!/usr/bin/env python3
"""v1.2.58 — Chaves de lang novas: itens do coqueiro, chá, pão de cevada,
lore do coco (explica DE ONDE vem) e os 6 nomes dos efeitos (pt_br + en_us)."""
import json
import io
import os

raiz = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                    "assets", "intoxicantes", "lang")

pt = {
    # ---- os itens novos
    "item.intoxicantes.coco": "Coco",
    "item.intoxicantes.coco.lore": "§7Do pé direto pra mão. §fQuebre e beba a água§7 — mata fome E sede. Nas praias, o coqueiro é a fonte.",
    "item.intoxicantes.cha_lupulo": "Chá de Lúpulo",
    "item.intoxicantes.cha_lupulo.lore": "§eA calma da flor: corta qualquer viagem na hora e regenera devagar. O antídoto caseiro do fregues.",
    "item.intoxicantes.pao_cevada": "Pão de Cevada",
    "item.intoxicantes.pao_cevada.lore": "§6A comida honesta da colheita: denso, escuro, feito do cereal da cerveja. Fome de descer redondo.",
    # ---- os blocos do coqueiro
    "block.intoxicantes.coqueiro_tronco": "Tronco de Coqueiro",
    "block.intoxicantes.coqueiro_folhas": "Folhas de Coqueiro",
    "block.intoxicantes.coco_bloco": "Coco no Pé",
    # ---- os 6 efeitos (o quadriculado roxo tinha nome, ninguém via kkkk)
    "effect.intoxicantes.tranquilo": "Tranquilo",
    "effect.intoxicantes.morno": "Morno",
    "effect.intoxicantes.sonho": "Sonho",
    "effect.intoxicantes.overdrive": "Overdrive",
    "effect.intoxicantes.viagem": "Viagem",
    "effect.intoxicantes.abstinencia": "Abstinência",
}

en = {
    "item.intoxicantes.coco": "Coconut",
    "item.intoxicantes.coco.lore": "§7Straight from the tree. §fCrack it and drink§7 — quenches hunger AND thirst. Beach palms are the source.",
    "item.intoxicantes.cha_lupulo": "Hops Tea",
    "item.intoxicantes.cha_lupulo.lore": "§eThe flower's calm: cuts any trip instantly and slowly regenerates. The regular's homemade antidote.",
    "item.intoxicantes.pao_cevada": "Barley Bread",
    "item.intoxicantes.pao_cevada.lore": "§6The harvest's honest food: dense, dark, made from beer's cereal. Hunger settles right.",
    "block.intoxicantes.coqueiro_tronco": "Palm Trunk",
    "block.intoxicantes.coqueiro_folhas": "Palm Leaves",
    "block.intoxicantes.coco_bloco": "Coconut on the Tree",
    "effect.intoxicantes.tranquilo": "Tranquil",
    "effect.intoxicantes.morno": "Warm",
    "effect.intoxicantes.sonho": "Dream",
    "effect.intoxicantes.overdrive": "Overdrive",
    "effect.intoxicantes.viagem": "Trip",
    "effect.intoxicantes.abstinencia": "Withdrawal",
}

for nome, chaves in (("pt_br", pt), ("en_us", en)):
    caminho = os.path.join(raiz, nome + ".json")
    with io.open(caminho, "r", encoding="utf-8") as f:
        dados = json.load(f)
    for k, v in chaves.items():
        dados[k] = v
    with io.open(caminho, "w", encoding="utf-8", newline="\n") as f:
        json.dump(dados, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"{nome}.json: +{len(chaves)} chaves")
