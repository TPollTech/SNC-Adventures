"""Gera texturas pixel-art dos itens de plantacao e da lampada UV.

v2: sementes = pacote de sementes (sache); plantacoes = plantas construidas
por codigo (caule + folhas + cachos, identidade por cultura); lampada UV =
luminaria industrial com tubos brilhantes (item) e painel de luz (bloco).

Complementa o gen_textures.py (que mantem as texturas dos itens/bebidas).
Uso: python tools/gen_farm_textures.py  (a partir da raiz do projeto do mod)
"""
import os
import struct
import zlib
try:
    from tools.gen_catalogo import ITEM_IDS as CATALOGO_3D
except ModuleNotFoundError:
    from gen_catalogo import ITEM_IDS as CATALOGO_3D

ASSETS = os.path.join("src", "main", "resources", "assets", "intoxicantes")

# v1.2.35 — TEXTURAS MANUAIS (artes do usuário): o gerador NÃO sobrescreve.
# Esses arquivos em textures/item foram desenhados à mão em alta resolução
# (256×256) e substituem os procedurais. Quem quiser regenerar um deles:
# mover o PNG manual pra backups/, rodar este script e recolocar.
MANUAIS = {
    "baseado", "cachaca", "cafe_verde", "cana_de_acucar", "cartucho",
    "cartucho_38", "cerveja", "cocaina", "cogumelo_xamanico",
    "cristal_de_euforia", "extrato_cafeina", "faixa_pedestre", "heroina",
    "hidrante", "hidromel", "lampada_uv", "lsd", "lupulo", "maconha_seda",
    "nevoa_do_deserto", "opio", "ovo_gago", "ovo_traficante", "po_estelar",
    "raiz_de_sombra", "real", "revolver",
}


def write_png(path, rows):
    nome = os.path.basename(path)
    dirnome = os.path.basename(os.path.dirname(path))
    if dirnome == "item" and nome.removesuffix(".png") in CATALOGO_3D | {"uva", "lampada_uv"}:
        print(f"  SKIP (modelo3D canônico): {nome}")
        return
    # Pinturas manuais em alta resolução aprovadas com o poste completo.
    # A fonte é o próprio PNG de produção, sincronizado com minhas-texturas;
    # os antigos mapas16 não podem recriar uma arte diferente por cima dele.
    if dirnome == "block" and nome in {
            "poste_luz.png", "poste_ped.png", "poste_luz_on.png", "poste_luz_off.png"}:
        print(f"  SKIP (pintura canônica do poste): {nome}")
        return
    if dirnome == "item" and (nome[:-4] if nome.endswith(".png") else nome) in MANUAIS:
        print(f"  SKIP (manual do usuário): {nome}")
        return
    h = len(rows)
    w = len(rows[0])
    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in rows)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def conserta_buracos_internos(caminho):
    """v1.2.71 (reescrito) — FECHA OS VÃOS INTERNOS de uma arte manual (bug do
    playtest: o café tinha transparência no meio do item). A regra de 1px com
    4 vizinhos opacos nunca pegava nada (os pinpricks do GIMP têm anti-alias:
    a vizinhança imediata é semitransparente). A regra nova é GEOMÉTRICA:
    componente conectado de pixels transparentes (alpha<128) que NÃO alcança
    a borda do canvas = buraco DENTRO do desenho, de qualquer tamanho. O
    contorno real da silhueta sempre encosta na borda e continua intacto.
    Anti-alias residual (semi-transparência cercada) também é solidificado.
    O preenchimento é inpainting em cascas (média dos vizinhos já sólidos,
    de fora pra dentro) — o remendo herda o degradê da vizinhança.
    """
    from PIL import Image
    from collections import deque
    img = Image.open(caminho).convert("RGBA")
    px = img.load()
    w, h = img.size
    pendentes = set()
    n_vaos = 0
    visto = [[False] * w for _ in range(h)]
    for y0 in range(h):
        for x0 in range(w):
            if visto[y0][x0] or px[x0, y0][3] >= 128:
                continue
            fila = deque([(x0, y0)])
            visto[y0][x0] = True
            comp = []
            toca_borda = False
            while fila:
                x, y = fila.popleft()
                comp.append((x, y))
                if x == 0 or y == 0 or x == w - 1 or y == h - 1:
                    toca_borda = True
                for nx, ny in ((x-1, y), (x+1, y), (x, y-1), (x, y+1)):
                    if 0 <= nx < w and 0 <= ny < h \
                            and not visto[ny][nx] and px[nx, ny][3] < 128:
                        visto[ny][nx] = True
                        fila.append((nx, ny))
            if not toca_borda:
                n_vaos += 1
                pendentes.update(comp)
    # anti-alias cercado: semitransparente cujo anel 3×3 é todo >= semialfa
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            a = px[x, y][3]
            if 128 <= a <= 230:
                anel = [px[x+dx, y+dy][3] for dx in (-1, 0, 1)
                        for dy in (-1, 0, 1) if (dx, dy) != (0, 0)]
                if all(aa >= 128 for aa in anel):
                    pendentes.add((x, y))
    if not pendentes:
        return 0

    def solidos_viz(x, y):
        viz = [px[x+dx, y+dy] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
               if (x+dx, y+dy) not in pendentes]
        return [v for v in viz if v[3] >= 200]

    # inpainting em cascas: pinta quem tem vizinho sólido, até acabar
    pintado = 0
    while pendentes:
        feitos = []
        for (x, y) in list(pendentes):
            solidos = solidos_viz(x, y)
            if solidos:
                r = sum(v[0] for v in solidos) // len(solidos)
                g = sum(v[1] for v in solidos) // len(solidos)
                b = sum(v[2] for v in solidos) // len(solidos)
                px[x, y] = (r, g, b, 255)
                feitos.append((x, y))
        if not feitos:
            break  # ilha cercada só de semitransparência: deixa como está
        for p in feitos:
            pendentes.discard(p)
        pintado += len(feitos)
    return pintado if pintado else 0

def rgba(s, alpha=255):
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), alpha)

def clareia(hexs, t):
    """Mistura a cor com branco (t=0..1) — highlight de fruto/folha."""
    r, g, b, a = rgba(hexs)
    return (r + int((255 - r) * t), g + int((255 - g) * t), b + int((255 - b) * t), a)

def escurece(hexs, t):
    """Mistura a cor com preto (t=0..1) — sombra de volume."""
    r, g, b, a = rgba(hexs)
    return (int(r * (1 - t)), int(g * (1 - t)), int(b * (1 - t)), a)

def render(map_lines, palette):
    rows = []
    for line in map_lines:
        row = []
        for ch in line:
            if ch == ".":
                row.append((0, 0, 0, 0))
            else:
                v = palette[ch]
                row.append(v if isinstance(v, tuple) else rgba(v))
        rows.append(row)
    return rows

def outline(rows, color_hex, alpha=255):
    col = rgba(color_hex, alpha)
    h, w = len(rows), len(rows[0])
    out = [list(r) for r in rows]
    for y in range(h):
        for x in range(w):
            if rows[y][x][3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                ny, nx = y + dy, x + dx
                if 0 <= ny < h and 0 <= nx < w and rows[ny][nx][3] >= 200:
                    out[y][x] = col
                    break
    return [tuple(p) for p in out]

# ---------------------------------------------------------------- culturas
# nome -> (caule escuro, caule claro, fruto, fruto maduro,
#          (semente escura, semente clara))
CROPS = {
    "maconha": ("3E2812", "4C8C3A", "6FBF4A", "B4EFA0", ("2E6B22", "8FD96A")),
    "lupulo": ("3E5A28", "5A8C3A", "8CC63F", "D4F07A", ("3E5A28", "9BD95A")),
    "uva": ("3E4A28", "5A7A3A", "8B5CF6", "D8B4FE", ("3E4A28", "B08CF0")),
    "cafe": ("4A3826", "6B5A3A", "C0392B", "E74C3C", ("4A3826", "D07050")),
    "papoula": ("2E4A20", "4A7A30", "D42A2A", "FF6B6B", ("2E4A20", "E86A6A")),
}

# ---------------------------------------------------------------- semente: pacote
def pacote_semente(semente_escura, semente_clara, acento):
    """Pacote de sementes (sache de papel kraft): costura no topo, broto
    estampado e etiqueta com a cor da cultura. MUITO mais legivel que
    graozinhos soltos no inventario."""
    return (
        [
            "................",
            "..oooooooooooo..",
            "..o.k.k.k.k..o..",  # costura do topo
            "..okkkkkkkkkko..",
            "..okkWWkkkkkko..",  # brilho do papel
            "..okkWggkkkkko..",  # broto estampado (folhinhas)
            "..okgggggkkkko..",  # broto estampado (corpo)
            "..okkWggkkkkko..",
            "..okkkkkkkkkko..",
            "..oaaaaaaaaaao..",  # etiqueta (faixa da cultura)
            "..oaF.sF.sF.ao..",  # graozinhos na etiqueta
            "..oaaaaaaaaaao..",
            "..okkkkkkkkkko..",
            "..okkkkkkkkkko..",
            "..oooooooooooo..",
            "................",
        ],
        {
            "o": "5A4326",                                  # borda do papel
            "k": "B8965C",                                  # kraft
            "W": "D4B784",                                  # kraft claro (brilho)
            "g": "4C8C3A",                                  # broto verde
            "a": clareia(acento, 0.25),                     # faixa da cultura
            "F": rgba(semente_escura),                      # grao escuro
            "s": rgba(semente_clara),                       # grao claro
        },
    )

# ---------------------------------------------------------------- planta (blocos)
def _grid():
    return [["." for _ in range(16)] for _ in range(16)]

def _caule(g, topo, base=14, x=7):
    """Caule reto: claro em cima, escuro na base + torrao de terra."""
    for y in range(topo, base + 1):
        g[y][x] = "g"
    g[base][x] = "d"
    g[15][x - 1] = "d"
    g[15][x] = "d"
    g[15][x + 1] = "d"

def _folha(g, y, lado, n, ch="g", cai=True):
    """Folha em DIAGONAL saindo do caule: sobe/desce 1px a cada 2 de distancia,
    lendo como folha caida (nao como barra solida). Ponta com 1px extra."""
    for i in range(1, n + 1):
        x = 7 + lado * i
        yy = y + ((i + 1) // 2 if cai else -((i + 1) // 2))
        if 0 <= x < 16 and 0 <= yy < 16:
            g[yy][x] = ch
    ponta_x, ponta_y = 7 + lado * (n + 1), y + ((n + 2) // 2 if cai else -((n + 2) // 2))
    if 0 <= ponta_x < 16 and 0 <= ponta_y < 16:
        g[ponta_y][ponta_x] = "d"  # ponta na sombra

def _cacho(g, pontos, ch="f"):
    for x, y in pontos:
        if 0 <= x < 16 and 0 <= y < 16:
            g[y][x] = ch

def _spec_maconha():
    """Maconha: BUDA (cola) redondinho no topo + folhas diagonais caidas."""
    g = _grid()
    _caule(g, topo=4)
    # buda: cluster 5x4 com topo arredondado (nao e' cruz, e' flor)
    _cacho(g, [(7, 0), (6, 1), (7, 1), (8, 1),
               (5, 2), (6, 2), (7, 2), (8, 2), (9, 2),
               (5, 3), (6, 3), (7, 3), (8, 3), (9, 3),
               (6, 4), (7, 4), (8, 4)])
    _folha(g, 6, +1, 4); _folha(g, 6, -1, 4)
    _folha(g, 9, +1, 3); _folha(g, 9, -1, 3)
    _folha(g, 11, +1, 2); _folha(g, 11, -1, 2)
    return g

def _spec_lupulo():
    """Lupulo: cones pendurados (ovais 2x3) descendo de conectores."""
    g = _grid()
    _caule(g, topo=3)
    # cone esquerdo: conecta em (5,3), oval em x4-5, y4-6
    g[3][5] = "d"
    _cacho(g, [(4, 4), (5, 4), (4, 5), (5, 5), (4, 6), (5, 6)])
    # cone direito: conecta em (9,4), oval em x9-10, y5-7
    g[4][9] = "d"
    _cacho(g, [(9, 5), (10, 5), (9, 6), (10, 6), (9, 7), (10, 7)])
    _folha(g, 6, +1, 3); _folha(g, 6, -1, 3)
    _folha(g, 9, +1, 2); _folha(g, 9, -1, 2)
    return g

def _spec_uva():
    """Uva: copa de folhas caidas em cima + cacho PENDURADO em losango."""
    g = _grid()
    _caule(g, topo=5)
    g[4][7] = "d"  # haste da copa
    _folha(g, 3, +1, 4); _folha(g, 3, -1, 4)
    _folha(g, 5, +1, 3); _folha(g, 5, -1, 3)
    # cacho em losango (5-4-3): largura maxima no meio
    _cacho(g, [(7, 7), (6, 8), (7, 8), (8, 8),
               (5, 9), (6, 9), (7, 9), (8, 9), (9, 9),
               (6, 10), (7, 10), (8, 10), (7, 11)])
    _folha(g, 10, +1, 2); _folha(g, 10, -1, 2)
    return g

def _spec_cafe():
    """Cafe: arbusto frondoso com BAGAS 2x2 em pares colados no caule."""
    g = _grid()
    _caule(g, topo=4)
    # copa: folhas caindo no topo
    _folha(g, 4, +1, 3); _folha(g, 4, -1, 3)
    _folha(g, 6, +1, 4); _folha(g, 6, -1, 4)
    # pares de bagas 2x2 (cafe cereja vem em pares no galho)
    _cacho(g, [(5, 7), (6, 7), (5, 8), (6, 8), (8, 7), (9, 7), (8, 8), (9, 8)])
    _folha(g, 9, +1, 3); _folha(g, 9, -1, 3)
    _cacho(g, [(5, 11), (6, 11), (5, 12), (6, 12), (8, 11), (9, 11), (8, 12), (9, 12)])
    _folha(g, 12, +1, 2); _folha(g, 12, -1, 2)
    return g

def _spec_papoula():
    """Papoula: haste fina + FLOR redonda grande no topo (capsula 5x4)."""
    g = _grid()
    _caule(g, topo=5)
    # flor: capsula redondinha 5x4
    _cacho(g, [(6, 1), (7, 1), (8, 1),
               (5, 2), (6, 2), (7, 2), (8, 2), (9, 2),
               (5, 3), (6, 3), (7, 3), (8, 3), (9, 3),
               (6, 4), (7, 4), (8, 4)])
    _folha(g, 7, +1, 3); _folha(g, 7, -1, 3)
    _folha(g, 10, +1, 2); _folha(g, 10, -1, 2)
    return g

SPEC_CULTURA = {
    "maconha": _spec_maconha,
    "lupulo": _spec_lupulo,
    "uva": _spec_uva,
    "cafe": _spec_cafe,
    "papoula": _spec_papoula,
}

def _broto(sd, sc):
    """Estagio 1 compartilhado: mudinha com dois cotiledones."""
    g = _grid()
    g[13][7] = "g"
    g[14][7] = "d"
    g[15][6] = "d"; g[15][7] = "d"; g[15][8] = "d"
    g[12][5] = "g"; g[12][9] = "g"   # cotiledones
    g[11][6] = "g"; g[11][8] = "g"   # abrindo
    return g

def _corta(g, y_min):
    """Crescimento = revelar a planta de baixo pra cima (estilos vanilla)."""
    out = _grid()
    for y in range(y_min, 16):
        out[y] = list(g[y])
    return out

def _para_ascii(g):
    return ["".join(linha) for linha in g]

def mistura(c1, c2, t):
    """Mistura duas cores RGBA (t=0..1 pra c2)."""
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(4))

def planta_estagio(crop, estagio, sd, sc, fr, rf, brilho=None):
    """Grid de caracteres + paleta pra um estagio da cultura.
    brilho=None -> automatico (xadrez claro a partir do estagio 3)."""
    if estagio == 1:
        g = _broto(sd, sc)
    elif estagio == 2:
        # v1.2.16: corta mais fundo (era 8) — o gap visual 2->3 era minúsculo
        # ("será que ela só tem 2 estágios?")
        g = _corta(SPEC_CULTURA[crop](), 9)
        # fruto novo e' VERDE (ainda nao e' fruto)
        for y in range(16):
            for x in range(16):
                if g[y][x] == "f":
                    g[y][x] = "g"
    elif estagio == 3:
        # v1.2.16: revela mais planta (era 5) — e o fruto continua VERDE: a
        # cor do fruto é sinal de MATURAÇÃO, não de crescimento
        g = _corta(SPEC_CULTURA[crop](), 4)
        for y in range(16):
            for x in range(16):
                if g[y][x] == "f":
                    g[y][x] = "g"
    else:
        g = SPEC_CULTURA[crop]()
    if brilho is None:
        brilho = estagio >= 4
    if brilho:
        for y in range(16):
            for x in range(16):
                if g[y][x] == "f" and (x + y) % 2 == 0:
                    g[y][x] = "F"
    paleta = {
        "g": rgba(sc),
        "d": rgba(sd),
        "f": rgba(fr),
        "F": clareia(fr, 0.4),   # fruto com brilho (alternado)
    }
    return _para_ascii(g), paleta

def stage4_ripe(crop, sd, sc, fr, rf):
    """Planta MADURA: frutos na cor 'pronta' + HALO DOURADO em volta do cacho
    inteiro + brilho interno. O dourado e a assinatura visual (mesma familia
    do selo do cartao fidelidade e do letreiro) — da longe o jogador ja sabe
    que aquilo vale 3x. O halo e generico (qualquer pixel '.' vizinho de
    fruto vira ouro), entao funciona pras 5 culturas sem coordenada fixa."""
    ascii_grid, pal = planta_estagio(crop, 4, sd, sc, fr, rf, brilho=False)
    pal = dict(pal)
    pal["f"] = rgba(rf)
    pal["F"] = clareia(rf, 0.45)
    pal["H"] = (255, 236, 150, 255)   # dourado claro (brilho principal)
    pal["h"] = (214, 178, 92, 255)    # dourado medio (halo discreto — nao vira pétala)
    pal["W"] = (255, 255, 255, 235)   # faiscas brancas
    lines = [list(line) for line in ascii_grid]
    frutos = [(y, x) for y in range(16) for x in range(16) if lines[y][x] == "f"]
    halo = set()
    for y, x in frutos:
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):  # 4-dir: contorno colado, sem "asas"
            yy, xx = y + dy, x + dx
            if 0 <= yy < 16 and 0 <= xx < 16 and lines[yy][xx] == ".":
                halo.add((yy, xx))
    for y, x in halo:
        lines[y][x] = "h"
    # brilho: cruz de dourado claro no centro do cacho (nao xadrez, nao borra)
    ys = [y for y, _ in frutos]
    xs = [x for _, x in frutos]
    cy, cx = (min(ys) + max(ys)) // 2, (min(xs) + max(xs)) // 2
    for y, x in frutos:
        if abs(y - cy) + abs(x - cx) <= 1:
            lines[y][x] = "H"
    # SEM faiscas brancas: aglomeram e viram blob
    return ["".join(line) for line in lines], pal

# ---------------------------------------------------------------- lampada UV (item)
LAMPADA_ITEM = (
    [
        "................",
        ".......kk.......",
        ".......kk.......",
        "....KKKKKKKK....",
        "...KxxxxxxxxK...",
        "...KUvvvvvvUK...",  # tubo 1
        "...KUvWvvWvUK...",  # highlight do vidro
        "...KxxxxxxxxK...",
        "...KUvvvvvvUK...",  # tubo 2
        "...KUvWvvWvUK...",
        "...KxxxxxxxxK...",
        "....KKKKKKKK....",
        "................",
        "................",
        "................",
        "................",
    ],
    {
        "k": "6E6E78",      # corrente/suporte
        "K": "2B2B31",      # carcaça escura
        "x": "45454E",      # carcaça clara
        "U": "4A2482",      # borda do tubo (UV escura)
        "v": "8B5CF6",      # tubo aceso
        "W": "DCC8FF",      # highlight do vidro
    },
)

# ---------------------------------------------------------------- lampada UV (bloco)
def lampada_bloco():
    """Painel de luz UV (textura cheia 16x16 pro cube_all): moldura metalica
    + DOIS tubos verticais brilhando + grade de ventilacao no centro. Nada de
    quadrado chapado — parece equipamento."""
    g = _grid()
    for i in range(16):
        g[0][i] = "K"; g[15][i] = "K"; g[i][0] = "K"; g[i][15] = "K"
    for y in range(1, 15):
        for x in range(1, 15):
            g[y][x] = "x"
    # tubos verticais (colunas x=3..4 e x=11..12), full height, com highlight
    for y in range(2, 14):
        g[y][3] = "U"; g[y][4] = "v"
        g[y][11] = "v"; g[y][12] = "U"
        if y % 4 == 1:
            g[y][4] = "W"      # brilho do vidro (esquerdo)
        if y % 4 == 3:
            g[y][11] = "W"     # brilho do vidro (direito)
    # cantos dos tubos (soquete)
    for x in (3, 4, 11, 12):
        g[1][x] = "k"; g[14][x] = "k"
    # grade de ventilacao central
    for y in (4, 7, 10):
        for x in (6, 7, 8, 9):
            g[y][x] = "K"
            g[y + 1][x] = "d"
    ascii_grid = _para_ascii(g)
    paleta = {
        "K": "1E1E24",   # moldura escura
        "x": "3A3A44",   # carcaça
        "k": "6E6E78",   # soquete metal
        "d": "2A2A33",   # sombra da grade
        "U": "4A2482",   # tubo escuro
        "v": "8B5CF6",   # tubo aceso
        "W": "DCC8FF",   # highlight
    }
    return ascii_grid, paleta

# ---------------------------------------------------------------- produtos (itens)
PRODUTOS = {
    "lupulo": (
        [
            "................",
            ".....ccc........",
            "....cCCCcc......",
            "...cCCsCCcc.....",
            "...cCsCCsCc.....",
            "....cCCsCcc.....",
            ".....cccc.......",
            "......cc........",
            "......Ss........",
            "......Ss........",
            "......ss........",
            "................",
            "................",
            "................",
            "................",
            "................",
        ],
        {"c": "7FA83A", "C": "A8CC5A", "s": "D4F07A", "S": "5A8C3A"},
    ),
    "uva": (
        [
            "................",
            ".......ss.......",
            "......s.........",
            "....vVv.vVv.....",
            "...vVVVvVvVv....",
            "...vVVvVVvVv....",
            "....vVvVVvV.....",
            "...vVvVVvVv.....",
            "....vVvVvV......",
            ".....vvvv.......",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
        ],
        {"s": "5A7A3A", "v": "7B2FBE", "V": "9B4FD8"},
    ),
    "cafe_verde": (
        [
            "................",
            "................",
            "................",
            "......ccc.......",
            ".....cCCCc......",
            "....cCCsCCc.....",
            "....cCsCsCc.....",
            "....cCCsCCc.....",
            "....cCCCCCC.....",
            ".....cCCCc......",
            "......ccc.......",
            "................",
            "................",
            "................",
            "................",
            "................",
        ],
        {"c": "4A6B2A", "C": "7FA845", "s": "A8CC5A"},
    ),
    "cana_de_acucar": (
        [
            "................",
            "......gg........",
            ".....gGGg.......",
            "......gg........",
            ".....gGGg.......",
            "......gg........",
            ".....gGGg.......",
            "......gg........",
            ".....gGGg.......",
            "......gg........",
            ".....gGGg.......",
            "......gg........",
            ".....gGGg.......",
            "......gg........",
            "................",
            "................",
        ],
        {"g": "6B8C3A", "G": "A8CC7A"},
    ),
}

# ---------------------------------------------------------------- letreiro esquinão (v1.2.18)
# A PLACA custom: painel preto, moldura verde, texto "LED" em linhas verdes.
# O TEXTO DE VERDADE é o block entity renderer (PlacaEsquinaoRenderer) — estas
# texturas vestem a CAIXA do letreiro (frente/verso/colunas) e o ícone do item.

def placa_front():
    """Frente do painel (v1.2.25): fundo preto profundo + MOLDURA VERDE VIVA
    (o contorno que o tester vê de longe — a antiga era quase preta e a placa
    virava um monólito) + matriz de LED apagada (juntas a cada 2px, cara de
    painel de posto). SEM barras falsas de texto: o texto real brilha por
    cima via renderer (BER), centralizado — barras fixas conflitavam."""
    g = []
    for y in range(16):
        linha = []
        for x in range(16):
            if y == 0 or y == 15 or x == 0 or x == 15:
                linha.append("K")          # moldura verde VIVA
            elif y == 1 or y == 14 or x == 1 or x == 14:
                linha.append("k")          # bisel interno (volume)
            elif x % 2 == 0 and y % 2 == 0:
                linha.append("d")          # junta da matriz de LED (apagada)
            elif (x * 5 + y * 3) % 17 == 0:
                linha.append("m")          # pixel morto (sujeira de rua)
            else:
                linha.append("P")          # preto do painel
        g.append("".join(linha))
    return g, {"K": "27D96A", "k": "0F5A30", "P": "0B0F0C",
               "d": "060907", "m": "10150F"}


def placa_tela():
    """TELA do display de fachada (v1.2.31): a faixa larga MONTADA NA FACHADA
    é UM screen contínuo (estilo Satisfactory) — SEM moldura por bloco (a
    moldura verde da placa avulsa viraria 15 quadradinhos na fachada). Fundo
    preto profundo + matriz de LED apagada + pixels mortos espalhados."""
    g = []
    for y in range(16):
        linha = []
        for x in range(16):
            if x % 2 == 0 and y % 2 == 0:
                linha.append("d")          # junta da matriz de LED (apagada)
            elif (x * 5 + y * 3) % 17 == 0:
                linha.append("m")          # pixel morto (sujeira de rua)
            else:
                linha.append("P")          # preto do painel
        g.append("".join(linha))
    return g, {"P": "0B0F0C", "d": "060907", "m": "10150F"}


def placa_back():
    """Verso da caixa (v1.2.25): chapa metálica mais CLARA (a antiga era
    quase preta — de longe a placa inteira parecia um bloco de carvão) com
    respiros horizontais e rebites. 100% OPACA (sem camada cutout no 26.3)."""
    g = []
    for y in range(16):
        linha = []
        for x in range(16):
            if y == 0 or y == 15 or x == 0 or x == 15:
                linha.append("K")
            elif y in (4, 5, 10, 11) and 3 <= x <= 12:
                linha.append("v")          # respiro
            elif (y, x) in ((2, 3), (2, 12), (13, 3), (13, 12)):
                linha.append("r")          # rebite
            elif (x * 7 + y * 13) % 13 == 0:
                linha.append("l")          # brilho da chapa
            else:
                linha.append("M")
        g.append("".join(linha))
    return g, {"K": "1A1E24", "M": "333A44", "v": "14171C",
               "r": "4A525E", "l": "3E4650"}


def placa_coluna():
    """Coluna de sustentação (v1.2.25): ferro com anel verde — 100% OPACA
    (a antiga tinha margens transparentes; bloco sólido SEM camada cutout no
    26.3 as renderiza PRETO — as torres viravam caixas escuras)."""
    g = []
    for y in range(16):
        linha = []
        for x in range(16):
            if 6 <= x <= 9:
                if y <= 1:
                    linha.append("K")      # cap verde
                elif x == 6 or x == 9:
                    linha.append("F")      # lateral sombreada
                elif y in (4, 11):
                    linha.append("K")      # anéis
                else:
                    linha.append("C")      # ferro claro
            else:
                linha.append("f")          # fundo de chapa metálica (opaco)
        g.append("".join(linha))
    return g, {"K": "0E5A2E", "F": "2A2E34", "C": "4A5058", "f": "24282E"}


def placa_rodape():
    """Pedestal de concreto das torres do letreiro (v1.2.23): gris claro,
    juntas de forma (a "tábua" do concreto), manchas e um rebite de ferro
    no meio — o pedestal de sinalização de rua de verdade."""
    rows = []
    for y in range(16):
        linha = []
        for x in range(16):
            if y == 0 or y == 15 or x == 0 or x == 15:
                linha.append("e")        # canto quebrado (escuro)
            elif y in (3, 12):
                linha.append("j")        # junta de forma horizontal
            elif x in (5, 10) and 3 < y < 12:
                linha.append("j")        # junta vertical
            elif y in (7, 8) and 7 <= x <= 8:
                linha.append("r")        # rebite de ferro
            elif (x * 7 + y * 13) % 11 == 0:
                linha.append("m")        # mancha
            else:
                linha.append("c")        # concreto
        rows.append(linha)
    return [[{"c": (168, 170, 166, 255), "e": (96, 98, 94, 255),
              "j": (128, 130, 126, 255), "m": (150, 152, 148, 255),
              "r": (58, 62, 68, 255)}[k] for k in linha] for linha in rows]


PLACA_ITEM = (
    # 16 de largura em TODAS as linhas (uma linha de 17px gerava PNG inválido
    # — o jogador via o xadrez rosa/preto na mão)
    [
        "................",
        "..KKKKKKKKKKKK..",
        "..KPPPPPPPPPPK..",
        "..KPP.TTTT.PPK..",
        "..KPPPPPPPPPPK..",
        "..KP.TTTTTT.PK..",
        "..KPPPPPPPPPPK..",
        "..KPP.TTTTT.PK..",
        "..KPPPPPPPPPPK..",
        "..KPP.TTTT.PPK..",
        "..KPPPPPPPPPPK..",
        "..KKKKKKKKKKKK..",
        "....FF....FF....",
        "....FF....FF....",
        "....FF....FF....",
        "................",
    ],
    {"K": "0E5A2E", "P": "070A08", "T": "39FF6E", "F": "3A4048"},
)


# ------------------------------------------------- poste de luz + asfalto (v1.2.19)
def poste_luz_bloco():
    """AÇO GALVANIZADO do poste (v1.2.27): tubo CINZA MÉDIO-CLARO (legível
    contra céu e folhagem — a v1.2.24 usava chapa quase preta e o poste
    virava "caixa preta" no pátio), brilho vertical no centro, costura de
    emenda a cada 5px e salpico de galvanização."""
    g = _grid()
    for y in range(16):
        for x in range(16):
            if x <= 1 or x >= 14:
                g[y][x] = "m"          # sombra da borda do tubo
            elif y % 5 == 0:
                g[y][x] = "K"          # costura do tubo (emendas)
            elif x in (5, 6):
                g[y][x] = "M"          # brilho central do tubo
            elif (x * 7 + y * 13) % 17 == 0:
                g[y][x] = "s"          # salpico do galvanizado
            else:
                g[y][x] = "c"          # aço base
    ascii_grid = _para_ascii(g)
    paleta = {
        "M": "C9CDC9",   # brilho central
        "m": "5A605C",   # sombra da borda
        "K": "4A504C",   # costura do tubo
        "c": "8E948E",   # aço galvanizado (médio-claro)
        "s": "7A807A",   # salpico
        "a": (0, 0, 0, 0),
    }
    return ascii_grid, paleta


def poste_luz_on():
    """LENTE ACESA (v1.2.27): o bulbo de sódio visto de dentro da carcaça —
    hotspot central quente, anel âmbar e aro de vidro. A lente é a face DE
    BAIXO da luminária (a "lâmpada do poste" que o jogador vê de baixo)."""
    g = _grid()
    for y in range(16):
        for x in range(16):
            if x <= 1 or x >= 14:
                g[y][x] = "F"          # aro do vidro (flange)
            elif 6 <= y <= 9:
                g[y][x] = "H"          # hotspot do filamento
            elif 4 <= y <= 11:
                g[y][x] = "B"          # bulbo quente
            elif y in (3, 12):
                g[y][x] = "V"          # borda do vidro
            else:
                g[y][x] = "D"          # vidro frio (polar)
    ascii_grid = _para_ascii(g)
    paleta = {
        "F": "6E726E",   # aro do vidro
        "V": "8A7A48",   # borda do vidro (reflexo)
        "B": "FFC963",   # bulbo de sódio
        "H": "FFF3C4",   # hotspot
        "D": "D8A040",   # vidro frio (polar)
        "a": (0, 0, 0, 0),
    }
    return ascii_grid, paleta


def poste_luz_off():
    """LENTE APAGADA (v1.2.27): mesma lente com o filamento morto — vidro
    frio e cinza, sem brilho (antes era a textura da COLUNA escurecida, o
    "bulbo" apagado nem parecia lâmpada)."""
    g = _grid()
    for y in range(16):
        for x in range(16):
            if x <= 1 or x >= 14:
                g[y][x] = "F"
            elif 6 <= y <= 9:
                g[y][x] = "H"
            elif 4 <= y <= 11:
                g[y][x] = "B"
            elif y in (3, 12):
                g[y][x] = "V"
            else:
                g[y][x] = "D"
    ascii_grid = _para_ascii(g)
    paleta = {
        "F": "565A56",
        "V": "5C5442",
        "B": "4E4A44",
        "H": "6E6A60",
        "D": "46423C",
        "a": (0, 0, 0, 0),
    }
    return ascii_grid, paleta


def poste_ped():
    """PEDESTAL do poste (v1.2.24): concreto cinza-escuro com juntas de forma
    e mancha — primo do rodapé da placa, mas mais robusto (é poste de rua)."""
    g = _grid()
    for y in range(16):
        for x in range(16):
            if y == 0 or y == 15 or x == 0 or x == 15:
                g[y][x] = "e"
            elif y in (5, 11):
                g[y][x] = "j"
            elif x in (5, 10):
                g[y][x] = "j"
            elif (x * 7 + y * 13) % 11 == 0:
                g[y][x] = "m"
            else:
                g[y][x] = "c"
    ascii_grid = _para_ascii(g)
    paleta = {
        "c": (120, 122, 118, 255),
        "e": (64, 66, 62, 255),
        "j": (88, 90, 86, 255),
        "m": (104, 106, 102, 255),
    }
    return ascii_grid, paleta


def asfalto():
    """Asfalto do estacionamento (cube do bloco intoxicantes:asfalto): cinza
    muito escuro com grão heterogêneo e alguma mancha — o "tapete" denso da
    esquina, meio quebrado (não é piso de shopping)."""
    g = _grid()
    for y in range(16):
        for x in range(16):
            n = (x * 7 + y * 13 + (x * y) % 5) % 10
            g[y][x] = "a" if n < 5 else ("b" if n < 8 else "c")
    # manchas de óleo/borracha (par de retângulos aleatórios mas fixos)
    for x in range(4, 8):
        g[9][x] = "c"
        g[10][x] = "c"
    for y in range(3, 6):
        g[y][11] = "b"
        g[y][12] = "c"
    ascii_grid = _para_ascii(g)
    paleta = {
        "a": "26262B",   # asfalto base
        "b": "303036",   # grão claro
        "c": "1B1B1F",   # grão escuro/mancha
    }
    return ascii_grid, paleta


def main():
    tex_dir = os.path.join(ASSETS, "textures", "item")
    block_tex_dir = os.path.join(ASSETS, "textures", "block")
    count = 0

    # Sementes 3D: atlas e modelos exclusivos de gen_sementes.py.

    # Luminária UV usa exclusivamente o atlas 128 de gen_farm_resources.py.

    # v1.2.18: LETREIRO DO ESQUINÃO — ícone do item + as 3 texturas da caixa
    rows = outline(render(*PLACA_ITEM), "060906")
    write_png(os.path.join(tex_dir, "placa_esquinao.png"), rows)
    mp, pal = placa_front()
    write_png(os.path.join(block_tex_dir, "placa_esquinao_front.png"), render(mp, pal))
    # v1.2.31: a TELA contínua do display de fachada (sem moldura por bloco)
    mp, pal = placa_tela()
    write_png(os.path.join(block_tex_dir, "placa_esquinao_tela.png"), render(mp, pal))
    mp, pal = placa_back()
    write_png(os.path.join(block_tex_dir, "placa_esquinao_back.png"), render(mp, pal))
    mp, pal = placa_coluna()
    write_png(os.path.join(block_tex_dir, "placa_esquinao_coluna.png"), render(mp, pal))
    # v1.2.23: pedestal de concreto das TORRES da placa (rodapé/capitel)
    write_png(os.path.join(block_tex_dir, "placa_esquinao_rodape.png"), placa_rodape())
    # v1.2.23: ATLAS LED — 32×32 branco puro; a COR vem do vertex color do
    # renderer (verde/vermelho/dim). Branco liso = zero bleed de mipmap.
    misc_dir = os.path.join(ASSETS, "textures", "misc")
    os.makedirs(misc_dir, exist_ok=True)
    write_png(os.path.join(misc_dir, "led_atlas.png"),
              [[(255, 255, 255, 255)] * 32 for _ in range(32)])
    count += 7

    # Luminária UV: atlas único de gen_farm_resources.py, sem recriar painéis antigos.

    # v1.2.27: POSTE DE LUZ — aço galvanizado (base/corpo/topo) + lente
    # ACESA + lente APAGADA (própria, não mais a coluna escurecida) +
    # pedestal de concreto + ícone do item
    mp, pal = poste_luz_bloco()
    rows_poste = render(mp, pal)
    write_png(os.path.join(block_tex_dir, "poste_luz.png"), rows_poste)
    mp, pal = poste_luz_on()
    write_png(os.path.join(block_tex_dir, "poste_luz_on.png"), render(mp, pal))
    mp, pal = poste_luz_off()
    write_png(os.path.join(block_tex_dir, "poste_luz_off.png"), render(mp, pal))
    mp, pal = poste_ped()
    write_png(os.path.join(block_tex_dir, "poste_ped.png"), render(mp, pal))
    write_png(os.path.join(tex_dir, "poste_luz.png"), outline(rows_poste, "3A403C"))
    count += 5

    # v1.2.19: ASFALTO do estacionamento (cube do bloco)
    mp, pal = asfalto()
    write_png(os.path.join(block_tex_dir, "asfalto.png"), render(mp, pal))
    count += 1

    # v1.2.24: FAIXA DE PEDESTRE — a zebra branca da travessia. Listras de
    # tinta gasta (as pontas da listra ganham falha de rolo: pixel de tinta
    # mais ralo), fundo transparente (o asfalto do modelo aparece embaixo).
    fx = _grid()
    for y in range(16):
        for x in range(16):
            faixa = (x // 4) % 2 == 0          # listra a cada 4px
            borda = x % 4 == 3                  # borda direita da listra
            sujo = (x * 3 + y * 7) % 11 == 0    # falha de rolo espalhada
            fx[y][x] = "w" if (faixa and not (borda and sujo)) else "."
    ascii_grid = _para_ascii(fx)
    paleta_fx = {"w": "F2F2EC"}   # tinta branca de faixa (levemente suja)
    write_png(os.path.join(block_tex_dir, "faixa_pedestre.png"),
              render(ascii_grid, paleta_fx))
    count += 1

    # v1.2.24: HIDRANTE — corpo, tampa e base (o modelo usa 3 texturas).
    # Corpo: vermelho de ferro fundido com barras de sombra e brilho de tinta.
    hx = _grid()
    for y in range(16):
        for x in range(16):
            n = (x * 5 + y * 11) % 9
            hx[y][x] = "r" if n < 6 else ("d" if n < 8 else "h")
    # brilho da tinta (lateral esquerda) e ferrugem na base
    for y in range(2, 14):
        hx[y][4] = "h"
    for x in range(16):
        hx[15][x] = "d"
    ascii_h = _para_ascii(hx)
    paleta_h = {
        "r": "C42B1C",   # vermelho do corpo
        "d": "8E1D12",   # sombra/ferrugem
        "h": "E8543F",   # brilho da tinta
    }
    write_png(os.path.join(block_tex_dir, "hidrante.png"),
              render(ascii_h, paleta_h))
    # Tampa: mesma família, mais escura (parafuso hexagonal no centro)
    tx = _grid()
    for y in range(16):
        for x in range(16):
            n = (x * 7 + y * 3) % 7
            tx[y][x] = "r" if n < 5 else "d"
    for x in range(6, 10):
        tx[7][x] = "h"
        tx[8][x] = "h"
    write_png(os.path.join(block_tex_dir, "hidrante_topo.png"),
              render(_para_ascii(tx), paleta_h))
    # Base: flange escura com parafusos nos cantos
    bx = _grid()
    for y in range(16):
        for x in range(16):
            bx[y][x] = "d" if (x + y) % 5 == 0 else "r"
    write_png(os.path.join(block_tex_dir, "hidrante_base.png"),
              render(_para_ascii(bx), paleta_h))
    # Ícones de item (com contorno)
    rows_h = render(ascii_h, paleta_h)
    write_png(os.path.join(tex_dir, "hidrante.png"), outline(rows_h, "2A0A06"))
    rows_f = render(_para_ascii(fx), paleta_fx)
    write_png(os.path.join(tex_dir, "faixa_pedestre.png"), outline(rows_f, "3A3A38"))
    count += 3

    # produtos agricolas (itens, com contorno)
    for nome, (mp, pal) in PRODUTOS.items():
        rows = outline(render(mp, pal), "1A2010")
        write_png(os.path.join(tex_dir, nome + ".png"), rows)
        count += 1

    # estagios das culturas (blocos cross, SEM contorno)
    # v1.2.16 — a leitura visual em 6 fases (o report do Jade: "a 75% é
    # idêntica à madura"):
    #   1 broto | 2 meia verde | 3 alta verde | 4 COR SURGINDO (esmaecida)
    #   | dormant cor CHEIA sem halo (madura, esperando UV) | ripe DOURADA
    for crop, (sd, sc, fr, rf, _) in CROPS.items():
        for st in (1, 2, 3, 4):
            mp, pal = planta_estagio(crop, st, sd, sc, fr, rf, brilho=False)
            if st == 4:
                # "encamando" (age3): a cor do fruto COMEÇA a sair — misturada
                # com o verde do caule, sem brilho de "pronto"
                pal = dict(pal)
                pal["f"] = mistura(rgba(fr), rgba(sd), 0.45)
            write_png(os.path.join(block_tex_dir, "%s_stage%d.png" % (crop, st)),
                      render(mp, pal))
            count += 1
        # madura ESPERANDO UV (age4, uv 0..2): cor CHEIA, sem halo — "quase,
        # mas o brilho dourado ainda não veio"
        mp, pal = planta_estagio(crop, 4, sd, sc, fr, rf, brilho=False)
        write_png(os.path.join(block_tex_dir, crop + "_stage4_dormant.png"),
                  render(mp, pal))
        count += 1
        mp, pal = stage4_ripe(crop, sd, sc, fr, rf)
        write_png(os.path.join(block_tex_dir, crop + "_stage4_ripe.png"), render(mp, pal))
        count += 1

    print("OK: %d texturas de farm geradas" % count)

    # v1.2.71 — CONCERTO DAS ARTES MANUAIS: itens desenhados à mão (o gerador
    # não sobrescreve) com buraco interno transparente (o "café com furo" do
    # playtest). Fecha os pinpricks e reporta o que consertou.
    # v1.2.72 (escopo do conserto): vazio por padrão — o detector é agressivo
    # de propósito e confunde RECORTE DE DESIGN com defeito (o hidrante e a
    # faixa têm vãos internos intencionais). Pra consertar um manual:
    # adicione o id aqui e rode este script.
    CONCERTOS = set()
    for manual in sorted(CONCERTOS):
        if manual in CATALOGO_3D | {"uva", "lampada_uv"}:
            continue
        caminho = os.path.join(tex_dir, manual + ".png")
        if os.path.exists(caminho):
            n = conserta_buracos_internos(caminho)
            if n:
                print("  CONSERTADO (buracos internos fechados): %s — %d px" % (manual, n))

if __name__ == "__main__":
    main()
