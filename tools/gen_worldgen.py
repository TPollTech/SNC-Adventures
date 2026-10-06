"""Gera os JSONs de worldgen das plantacoes selvagens (feature + placed_feature).

Cada cultura nasce em manchinhas raras no bioma certo, como o berry_bush do vanilla.
Uso: python tools/gen_worldgen.py  (a partir da raiz do projeto do mod)
"""
import json
import os

DATA = os.path.join("src", "main", "resources", "data", "intoxicantes")


def escrever(caminho, obj):
    os.makedirs(os.path.dirname(caminho), exist_ok=True)
    with open(caminho, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def feature_bloco(bloco, idade, uv=True):
    """Feature simple_block que coloca o bloco da cultura ja crescida.
    uv=False para crops vanilla-style (cevada): sem a propriedade uv_age."""
    props = {"age": str(idade)}
    if uv:
        props["uv_age"] = "0"
    return {
        "type": "minecraft:simple_block",
        "to_place": {
            "id": f"intoxicantes:{bloco}",
            "properties": props,
        },
    }


def placed(nome, chance):
    """Placed feature: mancha rara em terreno alto, so sobre grama/terra."""
    return {
        "feature": f"intoxicantes:{nome}",
        "placement": [
            {"type": "minecraft:rarity_filter", "chance": chance},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
            {"type": "minecraft:biome"},
            {"type": "minecraft:count", "count": 24},
            {
                "type": "minecraft:offset",
                "x": {"type": "minecraft:trapezoid", "max": 6, "min": -6, "plateau": 0},
                "y": {"type": "minecraft:trapezoid", "max": 2, "min": -2, "plateau": 0},
                "z": {"type": "minecraft:trapezoid", "max": 6, "min": -6, "plateau": 0},
            },
            {
                "type": "minecraft:block_predicate_filter",
                "predicate": {
                    "type": "minecraft:all_of",
                    "predicates": [
                        {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"},
                        {
                            "type": "minecraft:matching_blocks",
                            "blocks": "#minecraft:dirt",
                            "offset": [0, -1, 0],
                        },
                    ],
                },
            },
        ],
    }


# nome -> (bloco da cultura, idade maxima, raridade 1/em N chunks)
# v1.2.50: a cevada é crop vanilla (uv=False, sem uv_age no feature)
CULTURAS = {
    "maconha_selvagem": ("maconha_plant", 4, 10, True),
    "lupulo_selvagem": ("lupulo_plant", 4, 14, True),
    "uva_selvagem": ("uva_plant", 4, 10, True),
    "cafe_selvagem": ("cafe_plant", 4, 12, True),
    "papoula_selvagem": ("papoula_plant", 4, 14, True),
    # a cevada selvagem nasce age=6 (quase madura — recompensa sem free loot)
    "cevada_selvagem": ("cevada_plant", 6, 10, False),
}

for nome, (bloco, idade, chance, uv) in CULTURAS.items():
    escrever(
        os.path.join(DATA, "worldgen", "feature", f"{nome}.json"),
        feature_bloco(bloco, idade, uv),
    )
    escrever(
        os.path.join(DATA, "worldgen", "placed_feature", f"{nome}.json"),
        placed(nome, chance),
    )

print(f"Worldgen OK: {len(CULTURAS)} manchas selvagens (feature + placed_feature).")
