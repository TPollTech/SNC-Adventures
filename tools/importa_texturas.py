#!/usr/bin/env python3
"""v1.2.57 — Importa do resourcepack HD (`resourcepacks/minhas-texturas`) toda
textura que esteja em resolução MAIOR que a embutida no mod (ou que falte no
mod). O pack fica redundante: o jar carrega a arte final."""
import struct
import os
import shutil

RAIZ = os.path.join(os.path.dirname(__file__), "..", "..")
PACK = os.path.join(RAIZ, "resourcepacks", "minhas-texturas",
                    "assets", "intoxicantes", "textures")
MOD = os.path.join(os.path.dirname(__file__), "..", "src", "main",
                   "resources", "assets", "intoxicantes", "textures")


def dims(caminho):
    with open(caminho, "rb") as f:
        d = f.read(24)
    w, h = struct.unpack(">II", d[16:24])
    return w * h  # área: critério de "mais detalhe"


importadas, mantidas, puladas = [], [], []
for dirpath, _, arquivos in os.walk(PACK):
    for arq in sorted(arquivos):
        if not arq.endswith(".png"):
            continue
        origem = os.path.join(dirpath, arq)
        rel = os.path.relpath(origem, PACK)
        destino = os.path.join(MOD, rel)
        os.makedirs(os.path.dirname(destino), exist_ok=True)
        if not os.path.exists(destino):
            shutil.copy2(origem, destino)
            importadas.append(f"{rel} (nova)")
            continue
        if dims(origem) > dims(destino):
            shutil.copy2(origem, destino)
            importadas.append(rel)
        else:
            mantidas.append(rel)

print(f"=== IMPORTADAS do pack ({len(importadas)}) ===")
for n in importadas:
    print("  " + n)
print(f"=== já maiores no mod ({len(mantidas)}) ===")
for n in mantidas:
    print("  " + n)
if puladas:
    print("PULADAS:", puladas)
