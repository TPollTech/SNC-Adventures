#!/usr/bin/env python3
"""v1.2.54 — Ícones dos efeitos (mobs_effects) + texturas de item da saúde.

Gera PNGs pixel art puros (sem Pillow: só zlib/struct), no padrão 8x8 dos
ícones de efeito vanilla, em:
  assets/intoxicantes/textures/effect/*.png   (efeitos: tranquilo..viagem, abstinencia)
  assets/intoxicantes/textures/item/*.png     (suco_detox, agua_de_coco)
"""
import struct
import zlib
import os

RAIZ = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                    "assets", "intoxicantes", "textures")


def escrever_png(caminho, largura, altura, pixels):
    """pixels: lista de linhas; cada linha: lista de (r,g,b,a)."""
    def chunk(tipo, dados):
        c = tipo + dados
        return struct.pack(">I", len(dados)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    bruto = b""
    for linha in pixels:
        bruto += b"\x00" + b"".join(struct.pack("4B", *px) for px in linha)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", largura, altura, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bruto))
           + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(caminho), exist_ok=True)
    with open(caminho, "wb") as f:
        f.write(png)


def cor(hexa):
    return ((hexa >> 16) & 255, (hexa >> 8) & 255, hexa & 255, 255)


TRANSPARENTE = (0, 0, 0, 0)


def tela(grama_cor, desenho):
    """Matriz 8x8 a partir de um desenho: strings de 8 chars ('.' = transparente)."""
    grade = []
    for y, linha in enumerate(desenho):
        px = []
        for x, ch in enumerate(linha):
            if ch == ".":
                px.append(TRANSPARENTE)
            elif ch == "1":
                px.append(grama_cor)
            elif ch == "2":
                px.append(tuple(min(255, c + 40) for c in grama_cor[:3]) + (255,))
            elif ch == "3":
                px.append(tuple(max(0, c - 40) for c in grama_cor[:3]) + (255,))
            else:
                px.append(TRANSPARENTE)
        grade.append(px)
    return grade


EFEITOS = {
    # folha da erva (T1 tranquilo)
    "tranquilo": (0x5D8F4A, [
        "....11..",
        "...1221.",
        "..122211",
        ".122113.",
        "..1221..",
        "...121..",
        "....3...",
        "...333..",
    ]),
    # colher de pó (T2 morno)
    "morno": (0xB5783C, [
        "...111..",
        "..12221.",
        "..12221.",
        "...111..",
        "....1...",
        "....1...",
        "....1...",
        "..3333..",
    ]),
    # olho sonolento (T2.5 sonho)
    "sonho": (0x7C5C9E, [
        "........",
        ".111111.",
        "1......1",
        "1..22..1",
        ".1....1.",
        "..1111..",
        "........",
        "........",
    ]),
    # raio (T3 overdrive)
    "overdrive": (0xE8E8E8, [
        "....11..",
        "...11...",
        "..11....",
        ".111111.",
        "...11...",
        "..11....",
        ".11.....",
        "........",
    ]),
    # cogumelo (T4 viagem)
    "viagem": (0xC040E0, [
        "..1111..",
        ".122221.",
        "12111211",
        "11111111",
        "..1111..",
        "..2332..",
        "..2332..",
        "...33...",
    ]),
    # punho tremendo (abstinência)
    "abstinencia": (0x4A4A5A, [
        ".11...11",
        "1111.111",
        "11111111",
        ".111111.",
        "..1111..",
        "...11...",
        "...11...",
        "..3333..",
    ]),
}

ITENS = {
    # garrafa verde com folhinha (suco detox)
    "suco_detox": (0x3E9A50, [
        "...11...",
        "...11...",
        "..1111..",
        ".111111.",
        "11211121",
        "11111111",
        ".122221.",
        "..1111..",
    ]),
    # coco com palhinha (água de coco)
    "agua_de_coco": (0x6B4A2B, [
        "......2.",
        ".....2..",
        "....11..",
        "..111111",
        ".1131111",
        ".1111111",
        ".111111.",
        "..1111..",
    ]),
}


try:
    from tools.gen_catalogo import ITEM_IDS as CATALOGO_3D
except ModuleNotFoundError:
    from gen_catalogo import ITEM_IDS as CATALOGO_3D

def main():
    # v1.2.64 — bebidas 3D (gen_garrafas.py): sprites 2D de item removidos do mod;
    # regravá-los aqui recriaria órfãos que nenhum modelo usa.
    BEBIDAS_3D = {"cerveja", "vinho", "cachaca", "hidromel", "rum", "suco_detox",
                  "agua_de_coco", "cha_lupulo"}
    for nome, (hexa, desenho) in EFEITOS.items():
        escrever_png(os.path.join(RAIZ, "effect", f"{nome}.png"), 8, 8, tela(cor(hexa), desenho))
        print(f"effect/{nome}.png OK")
    for nome, (hexa, desenho) in ITENS.items():
        if nome in BEBIDAS_3D or nome in CATALOGO_3D:
            print(f"item/{nome}.png SKIP (bebida 3D — sprite 2D removido na v1.2.64)")
            continue
        escrever_png(os.path.join(RAIZ, "item", f"{nome}.png"), 8, 8, tela(cor(hexa), desenho))
        print(f"item/{nome}.png OK")


if __name__ == "__main__":
    main()
