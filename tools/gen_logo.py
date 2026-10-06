"""LOGO DO MOD — letreiro de LED verde em fundo preto, estilo da placa
do Esquinao.

A fonte e a MESMA do letreiro in-game: LedFont.java (5x7, bitmap duro,
sem anti-aliasing na fonte — o anti-aliasing aqui e so nos pontos de LED).
SNC em LED verde grande, ADVENTURES embaixo, "* MOD *" em ambar de lampada
de sodio (os postes do estacionamento). Fundo preto com scanlines, vignette
e moldura metalica com rebites.

Regenera tambem o icon.png (64x64) declarado no fabric.mod.json — este
script passa a ser o gerador oficial do icone (o gen_textures.py antigo
nao deve mais ser rodado sozinho, senao sobrescreve o icone).

Uso: python tools/gen_logo.py   (a partir da raiz do projeto do mod)
"""
import math
import os
import random
import struct
import zlib

# ---------------------------------------------------------------- png utils
def write_png(path, rows):
    h = len(rows)
    w = len(rows[0])

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    # canvas deste gerador e RGB (float) -> RGBA (int) na hora de empacotar
    raw = b"".join(
        b"\x00" + b"".join(
            bytes((clamp(int(px[0])), clamp(int(px[1])), clamp(int(px[2])), 255))
            for px in row
        )
        for row in rows
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))

def clamp(v, lo=0, hi=255):
    return lo if v < lo else hi if v > hi else v

# ------------------------------------------------- fonte LED 5x7 (LedFont.java)
# Cada glifo = 7 linhas de 5 bits (bit 4 = coluna esquerda). Transcricao
# EXATA dos glifos usados no letreiro do jogo.
FONT = {
    "S": [0b01111, 0b10000, 0b10000, 0b01110, 0b00001, 0b00001, 0b11110],
    "N": [0b10001, 0b10001, 0b11001, 0b10101, 0b10011, 0b10001, 0b10001],
    "C": [0b01110, 0b10001, 0b10000, 0b10000, 0b10000, 0b10001, 0b01110],
    "A": [0b01110, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001],
    "D": [0b11110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b11110],
    "V": [0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01010, 0b00100],
    "E": [0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b11111],
    "T": [0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100],
    "U": [0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110],
    "R": [0b11110, 0b10001, 0b10001, 0b11110, 0b10100, 0b10010, 0b10001],
    "M": [0b10001, 0b11011, 0b10101, 0b10101, 0b10001, 0b10001, 0b10001],
    "O": [0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110],
    "*": [0b00000, 0b10101, 0b01110, 0b11111, 0b01110, 0b10101, 0b00000],
    " ": [0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b00000],
}

def linha_dots(texto):
    """Texto -> (celulas acesas [(col,lin)], largura em dots do grid)."""
    celulas = []
    x = 0
    for ch in texto.upper():
        g = FONT.get(ch, FONT[" "])
        for lin in range(7):
            bits = g[lin]
            for b in range(5):
                if bits & (1 << (4 - b)):
                    celulas.append((x + b, lin))
        x += 6  # 5 do glifo + 1 de espaco
    return celulas, x - 1

# ---------------------------------------------------------------- LED dot
def acende_dot(canvas, cx, cy, pitch, cor_nucleo, cor_centro, cor_halo, brilho, nucleo_ratio=0.30):
    """Um LED: halo suave + nucleo redondo com highlight no centro.
    Mescla por MAX (nunca estoura onde dois halos se cruzam)."""
    hr = pitch * 0.9
    cr = max(1.1, pitch * nucleo_ratio + 0.5)
    # halo (fora do nucleo)
    r_h = int(hr) + 1
    for dy in range(-r_h, r_h + 1):
        y = cy + dy
        if not (0 <= y < len(canvas)):
            continue
        row = canvas[y]
        for dx in range(-r_h, r_h + 1):
            x = cx + dx
            if not (0 <= x < len(row)):
                continue
            d2 = dx * dx + dy * dy
            if d2 >= hr * hr:
                continue
            d = math.sqrt(d2)
            if d < cr:
                continue  # o nucleo cuida daqui
            f = 1.0 - (d / hr) ** 2
            f *= f
            intens = f * 0.30
            ox, oy, oz = row[x]
            row[x] = (
                max(ox, clamp(cor_halo[0] * intens)),
                max(oy, clamp(cor_halo[1] * intens)),
                max(oz, clamp(cor_halo[2] * intens)),
            )
    # nucleo
    r_c = int(cr) + 2
    for dy in range(-r_c, r_c + 1):
        y = cy + dy
        if not (0 <= y < len(canvas)):
            continue
        row = canvas[y]
        for dx in range(-r_c, r_c + 1):
            x = cx + dx
            if not (0 <= x < len(row)):
                continue
            d = math.sqrt(dx * dx + dy * dy)
            if d > cr + 0.5:
                continue
            # anti-aliasing da borda + highlight branco no centro
            t = min(1.0, d / cr)
            base = tuple(cor_centro[i] + (cor_nucleo[i] - cor_centro[i]) * t for i in range(3))
            b = 1.0 if d <= cr else (cr + 0.5 - d)  # borda suave
            b *= brilho
            ox, oy, oz = row[x]
            row[x] = (
                max(ox, clamp(base[0] * b)),
                max(oy, clamp(base[1] * b)),
                max(oz, clamp(base[2] * b)),
            )

def desenha_linha(canvas, texto, pitch, oy, paleta, nucleo_ratio=0.30):
    """Uma linha de texto LED centralizada. paleta = (nucleo, centro, halo)."""
    celulas, larg = linha_dots(texto)
    w = len(canvas[0])
    ox = (w - larg * pitch) // 2
    rng = random.Random(hash(texto) & 0xFFFF)
    for (c, l) in celulas:
        cx = ox + c * pitch + pitch // 2
        cy = oy + l * pitch + pitch // 2
        brilho = rng.uniform(0.82, 1.0)  # LED vivo: cada ponto com fome propria
        acende_dot(canvas, cx, cy, pitch, *paleta, brilho, nucleo_ratio)
    return larg * pitch, 7 * pitch

# ---------------------------------------------------------------- fundo
def fundo(size):
    rng = random.Random(42)
    cx = cy = size / 2.0
    diag = math.sqrt(2) * size / 2.0
    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            v = 7 + rng.randint(-2, 3)  # ruido do painel
            # vignette radial
            d = math.sqrt((x - cx) ** 2 + (y - cy) ** 2) / diag
            m = 1.0 - 0.20 * d * d
            # scanlines sutis (so no fundo; os LEDs ficam por cima, limpos)
            if y % 2 == 0:
                m *= 0.92
            row.append((clamp(v * m), clamp((v + 3) * m), clamp((v + 1) * m)))
        rows.append(row)
    return rows

# ---------------------------------------------------------------- moldura
def moldura(canvas, esp=10):
    size = len(canvas)
    for y in range(esp):
        t = y / esp
        cor = tuple(int(c * (1 - t) + c * 0.45 * t) for c in (58, 58, 64))
        for x in range(size):
            canvas[y][x] = cor
            canvas[size - 1 - y][x] = tuple(int(c * 0.45) for c in cor)
    for y in range(size):
        for x in range(esp):
            t = x / esp
            cor = tuple(int(c * (1 - t) + c * 0.45 * t) for c in (58, 58, 64))
            canvas[y][x] = cor
            canvas[y][size - 1 - x] = tuple(int(c * 0.45) for c in cor)
    # bisel interno
    b = esp
    for i in range(size - 2 * b):
        for (x, y) in ((b + i, b), (b + i, size - 1 - b), (b, b + i), (size - 1 - b, b + i)):
            canvas[y][x] = (16, 16, 18)
    # rebites nos cantos
    for (rx, ry) in ((b + 7, b + 7), (size - b - 8, b + 7), (b + 7, size - b - 8), (size - b - 8, size - b - 8)):
        for dy in range(-4, 5):
            for dx in range(-4, 5):
                d = math.sqrt(dx * dx + dy * dy)
                if d > 4.2:
                    continue
                sombra = 1 if (dx - dy) > 1 else 0
                base = 108 - sombra * 40 - int(d * 8)
                canvas[ry + dy][rx + dx] = (clamp(base), clamp(base), clamp(base + 6))

# ---------------------------------------------------------------- logo 512
def gera_logo(size=512):
    canvas = fundo(size)

    # paletas: nucleo / centro (highlight) / halo
    verde = ((86, 244, 66), (188, 255, 158), (46, 210, 26))
    verde2 = ((67, 190, 51), (150, 232, 124), (38, 165, 20))
    ambar = ((255, 184, 72), (255, 228, 158), (206, 128, 26))

    inner = size - 2 * 10
    # empilhamento: SNC (pitch 26) + ADVENTURES (7) + * MOD * (5)
    p1, p2, p3 = 26, 7, 5
    _, w1 = linha_dots("SNC")
    _, w2 = linha_dots("ADVENTURES")
    _, w3 = linha_dots("* MOD *")
    gap12, gap23 = 28, 22
    total = 7 * p1 + gap12 + 7 * p2 + gap23 + 7 * p3
    oy = 10 + (inner - total) // 2
    oy2 = oy + 7 * p1 + gap12
    oy3 = oy2 + 7 * p2 + gap23

    desenha_linha(canvas, "SNC", p1, oy, verde)
    desenha_linha(canvas, "ADVENTURES", p2, oy2, verde2)
    desenha_linha(canvas, "* MOD *", p3, oy3, ambar)

    moldura(canvas, 10)
    return canvas

# ---------------------------------------------------------------- icone 64
def gera_icone(size=64):
    canvas = fundo(size)
    verde = ((96, 250, 70), (200, 255, 168), (46, 210, 26))
    ambar = ((255, 190, 80), (255, 232, 170), (206, 128, 26))

    esp = 3
    inner = size - 2 * esp
    p = 3
    _, w1 = linha_dots("SNC")
    oy = esp + (inner - 7 * p - 8) // 2
    desenha_linha(canvas, "SNC", p, oy, verde, nucleo_ratio=0.42)  # leds quase colados: legivel pequeno
    # strip ambar de LED embaixo (assinatura do letreiro)
    rng = random.Random(7)
    for i in range(12):
        cx = esp + 6 + i * 4
        cy = oy + 7 * p + 5
        acende_dot(canvas, cx, cy, 4, *ambar, rng.uniform(0.7, 1.0), nucleo_ratio=0.5)

    moldura(canvas, esp)
    return canvas

# ---------------------------------------------------------------- main
def main():
    logo = gera_logo(512)
    write_png(os.path.join("branding", "logo_512.png"), logo)
    icone = gera_icone(64)
    write_png(os.path.join("src", "main", "resources", "assets", "intoxicantes", "icon.png"), icone)

    # preview ASCII do texto (conferencia do grid)
    for texto in ("SNC", "ADVENTURES", "* MOD *"):
        celulas, larg = linha_dots(texto)
        grid = [["."] * larg for _ in range(7)]
        for (c, l) in celulas:
            grid[l][c] = "#"
        print("\n".join("".join(r) for r in grid))
        print()
    print("OK: branding/logo_512.png (512x512) + assets/intoxicantes/icon.png (64x64)")

if __name__ == "__main__":
    main()
