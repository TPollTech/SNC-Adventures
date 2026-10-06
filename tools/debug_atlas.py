"""Atlas de debug para validar a conversao de skins (v1.2.40).

Desenha cada skin convertida em escala 8x com:
  - magenta onde o pixel e transparente (alpha 0)
  - cinza claro onde e opaco
  - retangulos verdes marcando as regioes do layout steve
  - retangulo vermelho se a regiao deveria ter conteudo e esta VAZIA
Assim da pra validar os crops pixel a pixel sem depender da arte.
Uso: python tools/debug_atlas.py  (a partir de intoxicantes-mod/)
"""
import os

from PIL import Image, ImageDraw

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOD = os.path.join(RAIZ, "src", "main", "resources", "assets", "intoxicantes", "textures", "entity")
SAIDA = os.path.join(RAIZ, "build", "debug-atlas")

# regioes do layout steve 64x64: (nome, x, y, w, h)
REGIOES = [
    ("cabeca.topo", 8, 0, 8, 8), ("cabeca.fundo", 16, 0, 8, 8),
    ("cabeca.frente", 8, 8, 8, 8), ("cabeca.ladoD", 0, 8, 8, 8),
    ("cabeca.ladoE", 16, 8, 8, 8), ("cabeca.costas", 24, 8, 8, 8),
    ("hat.frente", 40, 8, 8, 8),
    ("corpo.frente", 20, 20, 8, 12), ("corpo.costas", 32, 20, 8, 12),
    ("corpo.ladoD", 16, 20, 4, 12), ("corpo.ladoE", 28, 20, 4, 12),
    ("corpo.topo", 20, 16, 8, 4), ("corpo.baixo", 28, 16, 8, 4),
    ("bracoD.frente", 44, 20, 4, 12), ("bracoD.topo", 44, 16, 4, 4),
    ("bracoD.fundo", 48, 16, 4, 4),
    ("pernaD.frente", 4, 20, 4, 12), ("pernaD.topo", 4, 16, 4, 4),
    ("pernaE.frente", 20, 52, 4, 12),
    ("bracoE.frente", 36, 52, 4, 12),
]

VAZIAS_OK = {"corpo.baixo"}  # face de baixo pode ficar vazia no traficante? nao — marcada


def atlas(nome_arquivo):
    caminho = os.path.join(MOD, nome_arquivo)
    if not os.path.exists(caminho):
        print(f"  SEM ARQUIVO: {nome_arquivo}")
        return
    src = Image.open(caminho).convert("RGBA")
    esc = 8
    img = Image.new("RGBA", (64 * esc, 64 * esc), (18, 18, 24, 255))
    d = ImageDraw.Draw(img)
    px = src.load()
    for y in range(64):
        for x in range(64):
            r, g, b, a = px[x, y]
            cor = (170, 170, 180, 255) if a > 0 else (200, 0, 200, 255)
            d.rectangle([x * esc, y * esc, x * esc + esc - 1, y * esc + esc - 1], fill=cor)
    problemas = []
    for (nome, x, y, w, h) in REGIOES:
        opacos = sum(1 for yy in range(y, y + h) for xx in range(x, x + w) if px[xx, yy][3] > 0)
        d.rectangle([x * esc, y * esc, (x + w) * esc - 1, (y + h) * esc - 1],
                    outline=(60, 220, 90, 255), width=2)
        if opacos == 0:
            d.rectangle([x * esc, y * esc, (x + w) * esc - 1, (y + h) * esc - 1],
                        outline=(240, 50, 50, 255), width=2)
            problemas.append(nome)
    img.save(os.path.join(SAIDA, nome_arquivo.replace(".png", "-debug.png")))
    status = "OK" if not problemas else f"VAZIAS: {', '.join(problemas)}"
    print(f"  {nome_arquivo}: {status}")


def main():
    os.makedirs(SAIDA, exist_ok=True)
    nomes = ["gago", "gago_sertao", "gago_mata", "gago_cerrado",
             "gago_sul", "gago_serra", "gago_brejo", "juca", "traficante"]
    for n in nomes:
        atlas(n + ".png")
    print(f"Atlases em: {SAIDA}")


if __name__ == "__main__":
    main()
