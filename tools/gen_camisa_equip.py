"""v1.2.72 — CAMADA DE EQUIPAMENTO da Camisa do Matanza (bug do playtest:
"a camisa é o peitoral de couro"). A item definição já apontava pro asset
`intoxicantes:camisa_matanza`, mas o JSON `equipment/camisa_matanza.json` e a
textura `textures/entity/equipment/humanoid/camisa_matanza.png` NUNCA foram
criados — o 26.3 caía pro default de couro (cinza tingível).

Layout da textura 64×32 (idêntico ao vanilla humanoid): o TORSO frontal é o
retângulo 16..40 × 20..32 (a banda 20..31 é a arte útil; a face de trás
espelha essa mesma janela). Mangas: a janela 40..56 × 16..32 cobre o braço
frontal do corpo (espelhada pro outro braço pelo renderer). Pernas ficam
transparentes de propósito (camisa, não calça — sem camada leggings).

Uso: python tools/gen_camisa_equip.py   (a partir da raiz do projeto do mod)
"""
import json
import os

from PIL import Image, ImageDraw

ASSETS = os.path.join("src", "main", "resources", "assets", "intoxicantes")

# Paleta do item 3D (tools/gen_catalogo.py, id camisa_matanza)
PRETO = (41, 40, 42, 255)        # 29282a — tecido base
PRETO_CLARO = (65, 64, 69, 255)  # 414045 — vinco/luz da dobra
PRETO_ESCURO = (25, 25, 29, 255) # 19191d — sombra
BRANCO = (236, 232, 224, 255)    # ossos da caveira
VERMELHO = (158, 90, 80, 255)    # 9e5a50 — olhos da impressão
LINHA = (74, 72, 78, 255)        # costura

TORSO = (16, 20, 40, 32)   # x0,y0,x1,y1 — frente (e espelho de costas)
BRACO = (40, 16, 56, 32)   # janela da manga frontal


def tecido(d, box):
    """Tecido preto + vincos diagonais (mesma família do item 3D)."""
    x0, y0, x1, y1 = box
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=PRETO)
    for i in range(x0 + 3, x1 - 4, 7):
        d.line([(i, y1 - 2), (min(i + 4, x1 - 1), y0 + 1)],
               fill=PRETO_CLARO, width=1)
    d.line([(x0, y1 - 1), (x1 - 1, y1 - 1)], fill=PRETO_ESCURO, width=1)


def costura(d, box):
    x0, y0, x1, y1 = box
    for y in (y0 + 1, y1 - 2):
        for x in range(x0 + 1, x1 - 1, 2):
            d.point((x, y), fill=LINHA)


def caveira(d, cx, y0):
    """Caveira branca 9 px de altura no peito (crânio + mandíbula + dentes)."""
    # bojo do crânio (7 de largura)
    d.rectangle([cx - 3, y0, cx + 3, y0 + 4], fill=BRANCO)
    d.rectangle([cx - 2, y0 - 1, cx + 2, y0 - 1], fill=BRANCO)   # topo arredondado
    # têmporas em sombra
    d.point((cx - 3, y0), fill=BRANCO)
    d.point((cx + 3, y0), fill=BRANCO)
    # mandíbula (5 de largura)
    d.rectangle([cx - 2, y0 + 5, cx + 2, y0 + 7], fill=BRANCO)
    # olhos vermelhos + nariz escuro
    d.point((cx - 2, y0 + 2), fill=VERMELHO)
    d.point((cx + 2, y0 + 2), fill=VERMELHO)
    d.point((cx, y0 + 3), fill=PRETO)
    # dentes
    d.point((cx - 1, y0 + 6), fill=PRETO)
    d.point((cx + 1, y0 + 6), fill=PRETO)


def gerar_textura():
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # ---- TORSO: tecido + costura nas laterais, gola aberta e impressão
    tecido(d, TORSO)
    costura(d, TORSO)
    # gola aberta: linha em V discreta no topo (a camisa é decotada)
    d.line([(24, 20), (28, 22)], fill=LINHA, width=1)
    d.line([(32, 20), (28, 22)], fill=LINHA, width=1)
    # impressão no peito: caveira centralizada (cx=28 = centro do torso)
    caveira(d, 28, 23)

    # ---- MANGA: tecido completo na janela do braço + ombro marcado
    tecido(d, BRACO)
    costura(d, BRACO)
    d.rectangle([41, 16, 48, 18], fill=PRETO_ESCURO)   # cúpula do ombro
    d.line([(44, 19), (44, 30)], fill=LINHA, width=1)  # costura da manga

    alvo = os.path.join(ASSETS, "textures", "entity", "equipment",
                        "humanoid", "camisa_matanza.png")
    os.makedirs(os.path.dirname(alvo), exist_ok=True)
    img.save(alvo)
    print("textura:", alvo)


def gerar_json():
    """Mesma forma do equipment/iron.json (26.3): camadas humanoid SEM
    `dyeable` — a paleta preta já está embutida na textura (a camisa NÃO é
    tingível; era esse o bug). Sem humanoid_leggings (não cobre pernas)."""
    dados = {
        "layers": {
            "humanoid": [{"texture": "intoxicantes:camisa_matanza"}],
            "humanoid_baby": [{"texture": "intoxicantes:camisa_matanza"}],
        }
    }
    alvo = os.path.join(ASSETS, "equipment", "camisa_matanza.json")
    os.makedirs(os.path.dirname(alvo), exist_ok=True)
    with open(alvo, "w", encoding="utf-8") as f:
        json.dump(dados, f, indent=2)
        f.write("\n")
    print("asset :", alvo)


if __name__ == "__main__":
    gerar_textura()
    gerar_json()
