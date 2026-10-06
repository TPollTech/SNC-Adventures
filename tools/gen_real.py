"""[APOSENTADO] Gerava a sprite 16x16 do Real — hoje o id "real" é a cédula
de R$ 2 gerada pelo gen_dinheiro.py (1024×432, tartaruga). Mantido só por
histórico: a guarda de tamanho abaixo evita qualquer sobrescrita acidental.

Uso: python tools/gen_real.py  (a partir da raiz do projeto do mod)
"""
from PIL import Image, ImageDraw
import os

IMG = "src/main/resources/assets/intoxicantes/textures/item/real.png"


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # cedula: retangulo verde com borda escura e brilho claro no centro
    VERDE_BORDA = (26, 84, 42, 255)
    VERDE_CLARO = (86, 160, 92, 255)
    VERDE_MEDIO = (56, 128, 66, 255)
    VERDE_ESCURO = (36, 100, 50, 255)
    CREME = (214, 232, 190, 255)

    # corpo da nota
    d.rectangle([1, 3, 14, 12], fill=VERDE_MEDIO)
    # borda
    d.rectangle([1, 3, 14, 3], outline=VERDE_BORDA, fill=None)
    for x in range(1, 15):
        d.point((x, 3), fill=VERDE_BORDA)
        d.point((x, 12), fill=VERDE_BORDA)
    for y in range(3, 13):
        d.point((1, y), fill=VERDE_BORDA)
        d.point((14, y), fill=VERDE_BORDA)
    # faixa clara horizontal no meio (estilo marca d'agua de cedula)
    d.rectangle([2, 7, 13, 8], fill=VERDE_CLARO)
    # detalhe central claro (retrato estilizado)
    d.rectangle([6, 5, 9, 10], fill=CREME)
    d.rectangle([7, 6, 8, 9], fill=VERDE_ESCURO)
    d.point((7, 7), fill=CREME)
    # cantos com valor "R" (pontos sugerindo a letra)
    d.point((2, 4), fill=CREME)
    d.point((3, 4), fill=CREME)
    d.point((2, 5), fill=CREME)
    d.point((3, 5), fill=VERDE_CLARO)
    d.point((12, 4), fill=CREME)
    d.point((13, 4), fill=CREME)
    d.point((13, 5), fill=CREME)
    d.point((12, 5), fill=VERDE_CLARO)
    # linhas guilloche (padrao de cedula) nas laterais
    for x in (4, 5, 10, 11):
        d.point((x, 6), fill=VERDE_CLARO)
        d.point((x + (1 if x < 8 else -1), 9), fill=VERDE_ESCURO)

    # v1.2.60: a arte da cédula vem do gen_dinheiro.py — nunca sobrescrever
    if os.path.exists(IMG) and os.path.getsize(IMG) > 20000:
        print(f"  SKIP ({os.path.basename(IMG)} é gerado pelo gen_dinheiro.py)")
        return
    img.save(IMG)
    print(f"Sprite salva: {IMG}")


if __name__ == "__main__":
    main()
