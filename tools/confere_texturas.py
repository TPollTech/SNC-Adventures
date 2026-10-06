#!/usr/bin/env python3
"""v1.2.57 — Confere as dimensões das texturas: resourcepack HD vs assets do mod."""
import struct
import os

RAIZ = os.path.join(os.path.dirname(__file__), "..", "..")
PACK = os.path.join(RAIZ, "resourcepacks", "minhas-texturas",
                    "assets", "intoxicantes", "textures")
MOD = os.path.join(os.path.dirname(__file__), "..", "src", "main",
                   "resources", "assets", "intoxicantes", "textures")

NOMES = [
    "block/asfalto.png",
    "block/uva_stage1.png", "block/uva_stage2.png", "block/uva_stage3.png",
    "block/uva_stage4.png", "block/uva_stage4_dormant.png",
    "block/uva_stage4_ripe.png", "block/prensa_uvas.png",
    "item/mosto_de_uva.png", "item/semente_uva.png",
]


def dims(caminho):
    with open(caminho, "rb") as f:
        d = f.read(24)
    w, h = struct.unpack(">II", d[16:24])
    return w, h


for nome in NOMES:
    pack = os.path.join(PACK, *nome.split("/"))
    mod = os.path.join(MOD, *nome.split("/"))
    d_pack = f"{dims(pack)[0]}x{dims(pack)[1]}" if os.path.exists(pack) else "AUSENTE"
    d_mod = f"{dims(mod)[0]}x{dims(mod)[1]}" if os.path.exists(mod) else "AUSENTE"
    print(f"{nome}: pack={d_pack} mod={d_mod}")
