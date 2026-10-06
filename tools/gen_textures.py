"""Gera texturas pixel-art (16x16) caprichadas: sombreamento em rampa,
contorno escuro automatico, brilhos e transparencias. Tambem gera modelos
de item e o icone do mod.

v2 (1.2.8): as 16 bebidas/consumiveis refeitas com pixel maps novos —
volume por rampa vertical, brilho de vidro, rotulos e silhuetas proprias
por produto (caneca, taça, garrafas, baggie, cone, seringa, selo...).

Uso: python tools/gen_textures.py  (executar a partir da raiz do projeto do mod)
"""
import json
import os
import struct
import zlib
from pathlib import Path

try:
    from tools.gen_catalogo import ITEM_IDS as CATALOGO_3D
except ModuleNotFoundError:
    from gen_catalogo import ITEM_IDS as CATALOGO_3D

ASSETS = os.path.join("src", "main", "resources", "assets", "intoxicantes")

# Modelos das bebidas prontas pertencem exclusivamente ao gen_garrafas.py.
# Os sprites históricos continuam úteis como ilustração do guia, mas nunca
# podem transformar as garrafas nativas 3D novamente em minecraft:item/generated.
BEBIDAS_3D = {
    drink["id"] for drink in json.loads(
        Path(__file__).with_name("garrafas.json").read_text(encoding="utf-8")
    )["bottles"]
}

# v1.2.35 — artes MANUAIS do usuário: nunca sobrescrever (ver gen_farm_textures.py)
MANUAIS = {
    "baseado", "cachaca", "cartucho", "cerveja", "cocaina",
    "cogumelo_xamanico", "cristal_de_euforia", "extrato_cafeina", "heroina",
    "hidromel", "lsd", "maconha_seda", "nevoa_do_deserto", "po_estelar",
    "raiz_de_sombra",
}

# v1.2.64 — os sprites 2D das bebidas 3D foram APOSENTADOS e REMOVIDOS de
# textures/item/: a única arte oficial é o atlas do gen_garrafas.py. Regravar
# aqui recriaria um órfão. O sprite da cerveja sobrevive só EM MEMÓRIA (base
# do icon.png), consumido pelo TEXTURES acima sem tocar o disco.


def write_png(path, rows):
    nome = os.path.basename(path)
    dirnome = os.path.basename(os.path.dirname(path))
    if dirnome == "item" and (nome[:-4] if nome.endswith(".png") else nome) in MANUAIS:
        print(f"  SKIP (manual do usuário): {nome}")
        return
    if dirnome == "item" and (nome[:-4] if nome.endswith(".png") else nome) in BEBIDAS_3D:
        print(f"  SKIP (bebida 3D — sprite 2D removido na v1.2.64): {nome}")
        return
    if dirnome == "item" and nome.removesuffix(".png") in CATALOGO_3D:
        print(f"  SKIP (catálogo 3D canônico): {nome}")
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

def rgba(s, alpha=255):
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), alpha)

def render(map_lines, palette):
    rows = []
    for line in map_lines:
        assert len(line) == 16, "linha com %d colunas: %r" % (len(line), line)
        row = []
        for ch in line:
            if ch == ".":
                row.append((0, 0, 0, 0))
                continue
            v = palette[ch]
            row.append(v if isinstance(v, tuple) else rgba(v))
        assert len(row) == 16
        rows.append(row)
    assert len(rows) == 16
    return rows

def outline(rows, color_hex, alpha=255, min_solid=200):
    """Adiciona contorno escuro nos vizinhos transparentes dos pixels solidos."""
    col = rgba(color_hex, alpha)
    h, w = len(rows), len(rows[0])
    out = [list(r) for r in rows]
    for y in range(h):
        for x in range(w):
            if rows[y][x][3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                ny, nx = y + dy, x + dx
                if 0 <= ny < h and 0 <= nx < w and rows[ny][nx][3] >= min_solid:
                    out[y][x] = col
                    break
    return [tuple(p) for p in out]

def scale(rows, factor):
    out = []
    for row in rows:
        linha = []
        for px in row:
            linha.extend([px] * factor)
        out.extend([linha] * factor)
    return out

def sombra_vertical(rows, x0, x1, y0, y1, forca=0.45):
    """Escurece o corpo de cima pra baixo (volume de liquido/garrafa)."""
    alto = max(1, y1 - y0)
    for y in range(y0, y1 + 1):
        t = forca * (y - y0) / alto
        for x in range(x0, x1 + 1):
            r, g, b, a = rows[y][x]
            if a == 0:
                continue
            rows[y][x] = (int(r * (1 - t)), int(g * (1 - t)), int(b * (1 - t)), a)
    return rows

def brilho(rows, x, y0, y1, alpha=170):
    """Coluna de brilho de vidro (passa por cima do liquido)."""
    for y in range(y0, y1 + 1):
        r, g, b, a = rows[y][x]
        if a == 0:
            continue
        rows[y][x] = (min(255, r + (255 - r) * alpha // 255),
                      min(255, g + (255 - g) * alpha // 255),
                      min(255, b + (255 - b) * alpha // 255), a)
    return rows

TEXTURES = {
    # ============================================================ O JUÇA (v1.2.39)
    # Cigarro Camel amarelo: maço branco/amarelo com o cigarro de fora
    "cigarro_camel": (
        [
            "................",
            "..OOOOOOOOOOO...",
            "..O#########O...",
            "..O#aaaaaaa#O...",
            "..O#aAAAAAa#O...",
            "..O#aA0A0Aa#O...",
            "..O#aAAAAAa#O...",
            "..O#aaaaaaa#O...",
            "..O#bbbbbbb#O...",
            "..O#bBBBBBb#O...",
            "..O#bBccccb#O...",
            "..O#bcccccb#O...",
            "..O#bbbbbbb#O...",
            "..O#########O...",
            "..OOOOOOOOOOO...",
            "................",
        ],
        {
            "O": "C7A24A",   # borda do maço (amarelo do Camel)
            "#": "F5F1E4",   # papel do maço (creme)
            "a": "E8B23A",   # faixa amarela-Camel (topo)
            "A": "D69E2E",   # amarelo sombra
            "0": "8A6420",   # ornamento da faixa
            "b": "E8B23A",   # faixa amarela (baixo)
            "B": "D69E2E",
            "c": "C9862A",   # emblema central
        },
        "3A3128",
    ),
    # Camisa do Matanza: preta com caveira branca estampada no peito
    "camisa_matanza": (
        [
            "................",
            "................",
            "..OO......OO....",
            "..OOO....OOO....",
            "..O#OOOOOO#O....",
            "..O########O....",
            "..O##w####w#O...",  # mangas
            "..O##ww##ww#O...",
            "..O###wwww##O...",  # ombros
            "..O####ww###O...",
            "..O###WwwW##O...",  # colarinho
            "..O##ww##ww#O...",
            "..O###w##w##O...",  # caveira no peito
            "..O##########O..",
            "..OOOOOOOOOOO...",
            "................",
        ],
        {
            "O": "2E2E38",   # borda do tecido
            "#": "1C1C22",   # tecido preto (o preto do rock)
            "w": "E8E4DC",   # estampa branca (caveira)
            "W": "B8B4AC",   # sombra da estampa
        },
        "0A0A0E",
    ),
    # ============================================================ CONTROLE REMOTO (v1.2.38)
    # A Central de Comando em item: controle preto com telinha verde
    # (a cor do LED) e teclado de botões com o power vermelho
    "central_comando": (
        [
            "................",
            "....OOOOOOO.....",
            "...O#ss###RO....",
            "...O#SLLS#BO....",
            "...O#SSSS#BO....",
            "...O#######O....",
            "...O#B#B#B#O....",
            "...O#B#B#B#O....",
            "...O#######O....",
            "...O#R#B#B#O....",
            "...O#B#B#B#O....",
            "...O#######O....",
            "...O##ss###O....",
            "....OOOOOOO.....",
            "................",
            "................",
        ],
        {
            "O": "2A3138",  # contorno
            "#": "3A4650",  # corpo do controle
            "s": "5A6A78",  # brilho do corpo
            "S": "0A1410",  # telinha apagada
            "L": "39FF6E",  # LED verde da telinha (a cor da Central)
            "B": "8A9AA8",  # botões
            "R": "FF5A48",  # botão power vermelho
        },
        "14181C"),

    # ============================================================ CERVEJA
    # Caneca robusta: espuma transbordando em 3 tons, cerveja em rampa,
    # bolhas subindo, brilho de vidro na parede esquerda, alca com furo
    "cerveja": (
        [
            "................",
            "................",
            ".....OOOOOO.....",
            "....OOfOFOOfO...",
            "....########....",
            "....GsAAAAAa....",
            "....GAAAAAAahh..",
            "....GAaAAAAa.h..",
            "....GAwBAAAa.h..",
            "....GAwAABAahh..",
            "....GawAAwAa....",
            "....awwwwAwa....",
            "....########....",
            "....#dddddd#....",
            "................",
            "................",
        ],
        {"O": "FDF8EA", "o": "E2D6B8", "F": "FFFDF4", "f": "E7DCBE", "#": "8A6B4A",
         "G": rgba("FFF6D8", 150), "A": "E8940F", "a": "B85E08", "w": "8A4A08",
         "s": rgba("FFFFFF", 190), "B": "FFF6D8", "h": "C9A16B", "d": "5A4632"},
        "2E1F10",
    ),
    # ============================================================ VINHO
    # Taça: boia larga, vinho em rampa rubi, reflexo no copo, pé firme
    "vinho": (
        [
            "................",
            "................",
            "....EGGGGGGE....",
            "....EVRVRRVE....",
            "....ESVRrRVE....",
            "....ESVRRrVE....",
            ".....EVRRrE.....",
            ".....EVrrE......",
            "......EE........",
            "......EE........",
            "......EE........",
            "......EE........",
            ".....EsEE.......",
            "....EEEEEEE.....",
            "................",
            "................",
        ],
        {"E": "B9C7CE", "G": rgba("D7E4EA", 160), "S": rgba("FFFFFF", 210),
         "V": rgba("FFFFFF", 90), "R": "A61E3E", "r": "6E0F28",
         "s": rgba("FFFFFF", 200)},
        "1A1016",
    ),
    # ============================================================ CACHACA
    # Garrafa alta de vidro claro: pescoco, rolha, liquido palha em rampa,
    # rotulo amarelo com faixa verde (a identidade da branquinha)
    "cachaca": (
        [
            "................",
            ".......cc.......",
            ".......gg.......",
            "......gggg......",
            ".....sLLLLg.....",
            ".....sLLLLg.....",
            ".....LLLLLg.....",
            ".....LLLLLg.....",
            "....sYyyyyYg....",
            "....sYGGGGYg....",
            "....sYyyyyYg....",
            ".....LLLLLg.....",
            ".....LLLLLg.....",
            ".....LLLLLg.....",
            "................",
            "................",
        ],
        {"c": "8A4A22", "g": rgba("E8F0EC", 190), "L": "F2E6B0",
         "l": "E2CE8A", "s": rgba("FFFFFF", 200), "Y": "F2C230",
         "y": "E0B01E", "G": "1E7A34"},
        "3A3018",
    ),
    # ============================================================ HIDROMEL
    # Frasco bojudo com rolha e lacre de cera: mel ambar em rampa,
    # brilho no ombro, selo de favo
    "hidromel": (
        [
            "................",
            "................",
            "......cccc......",
            "......CCCC......",
            ".....BMMMM......",
            "....BMMmmMM.....",
            "....MMmhmMM.....",
            "...BMMhhhMM.....",
            "...BMhFhhMMM....",
            "...BMhhhMMmM....",
            "....MhhmmmM.....",
            "....MMmmmmM.....",
            ".....MMMMM......",
            "......MMM.......",
            "................",
            "................",
        ],
        {"c": "C9A16B", "C": "8A6B4A", "B": rgba("FFE9B8", 200),
         "M": "D89A3C", "m": "B5761F", "h": "F0BE55", "F": "FFF0B8"},
        "4A3010",
    ),
    # ============================================================ RUM
    # Garrafa quadrada de vidro escuro: silhueta firme, cobre refletindo,
    # rotulo preto com faixa ouro (a cara do pirata)
    "rum": (
        [
            "................",
            ".......KK.......",
            ".......kF.......",
            ".......KK.......",
            "......kDDk......",
            ".....sDDDDk.....",
            ".....DDcDDk.....",
            ".....DDDDDD.....",
            "....sPPPPPPk....",
            "....sPOOOOPk....",
            "....sPPPPPPk....",
            "....kDDDDDDk....",
            "....kDDDDDDk....",
            "....kDDDDDDk....",
            "................",
            "................",
        ],
        {"K": "1C1410", "k": "33261C", "F": "C9A16B", "D": "4A2C1A",
         "c": "B5651F", "s": rgba("D8A058", 130), "P": "14100C",
         "O": "E8B84A"},
        "0A0806",
    ),
    # ============================================================ MACONHA_SEDA
    # Baggie transparente com zip: buchozinhos verdes dentro, folhinha
    # estampada, brilho diagonal do plastico
    "maconha_seda": (
        [
            "................",
            "................",
            "................",
            "....zzzzzzzz....",
            "....gpppppppg...",
            "....gGGgGGggg...",
            "....gGGGGgGGg...",
            "....pgGGgGGpg...",
            "....gGGGGGGgg...",
            "....gGgGGgGGg...",
            "....pggGGggpg...",
            "....gpppppppg...",
            "....ggggggggg...",
            "................",
            "................",
            "................",
        ],
        {"z": rgba("D8E4E0", 210), "p": rgba("F0F8F4", 120),
         "g": rgba("C8D8D0", 160), "G": "3A8A34", "h": "56A848"},
        "16240F",
    ),
    # ============================================================ BASEADO
    # Cone na diagonal: papel com dobras, bucha castanho, brasa acesa
    # e fumaça subindo da ponta
    "baseado": (
        [
            "................",
            ".............w..",
            "............c...",
            "..........ccF...",
            ".........cFF....",
            "........cFf.....",
            ".......cFf......",
            "......cFf.......",
            ".....bFf........",
            "....bFf.........",
            "...bbf..........",
            "..bbb...........",
            "..bb............",
            "................",
            "................",
            "................",
        ],
        {"F": "F5EFD8", "f": "D8CCA8", "c": "E86A10", "C": "B53010",
         "b": "C9A16B", "B": "8A6B4A", "w": rgba("C8C8C8", 150)},
        "4A3418",
    ),
    # ============================================================ COCAINA
    # Espelho negro com as linhas alvas e o pilar de po (o classico da mesa)
    "cocaina": (
        [
            "................",
            "................",
            "................",
            "................",
            "....KKKKKKKK....",
            "....KsKKKKKK....",
            "....KKKKsKKK....",
            "..WWWWWWWWWW....",
            "..WwWWsWWWW.....",
            "..WWWWWWWWs.....",
            "...WWWWWWW......",
            "....KKsKKKK.....",
            "....KKKKsKK.....",
            "................",
            "................",
            "................",
        ],
        {"K": "14161A", "s": rgba("9AA8B8", 120),
         "W": "F5F5F0", "w": "D8D8D0"},
        "000204",
    ),
    # ============================================================ HEROINA
    # Seringa na diagonal: êmbolo, cilindro com liquido palido,
    # graduações e agulha fina
    "heroina": (
        [
            "................",
            "....PP..........",
            "....PP.r........",
            "....PPrr........",
            ".....rTT........",
            "......TLt.......",
            ".......TLl......",
            ".......TLl......",
            "........Tl......",
            ".........Tl.....",
            "..........s.....",
            "...........s....",
            "............s...",
            ".............S..",
            "................",
            "................",
        ],
        {"P": "5A6470", "r": "C8D0D8", "T": "D8E0E8", "L": "B8C8BC",
         "l": "9FB8A8", "s": "B8C4CC", "S": "E8F0F4", "t": "86A094"},
        "2A3238",
    ),
    # ============================================================ LSD
    # Selo de papel cartão com mandala psicodelica em tres tons e picotes
    "lsd": (
        [
            "................",
            "................",
            "....pppppppp....",
            "....pTTTTTTp....",
            "....pTMMMMMTp...",
            "....pTMYYYMTp...",
            "....pTMYyYMTp...",
            "....pTMYyYMTp...",
            "....pTMYYYMTp...",
            "....pTMMMMMTp...",
            "....pTTTTTTp....",
            "....pppppppp....",
            "................",
            "................",
            "................",
            "................",
        ],
        {"p": "E8E4D8", "T": "1E9E8A", "M": "C8388A",
         "Y": "F2C230", "y": "FFF0B8"},
        "403A28",
    ),
    # ============================================================ PO_ESTELAR
    # Poeira dourada cintilando: fagulhas de quatro pontas e po solto
    "po_estelar": (
        [
            "................",
            "................",
            "......G.........",
            "......g.........",
            ".....gGg........",
            "......g.....Y...",
            "............y...",
            "..Y.........Y...",
            "...y....G....y..",
            "..Y....gGg...Y..",
            ".......ggg......",
            "..........G.....",
            "....g.....g.....",
            "....G...........",
            "................",
            "................",
        ],
        {"G": "FFE9A8", "g": "F2C230", "Y": "FFF8E0", "y": "D8A828"},
        "8A6B1E",
    ),
    # ============================================================ COGUMELO_XAMANICO
    # Cogumelo classico: chapeu vermelho com pintas brancas, lamelas e caule
    "cogumelo_xamanico": (
        [
            "................",
            "................",
            "....RRRRRRR.....",
            "...RWRRRRRRR....",
            "..RRRRWRRRRR....",
            "..RRRRRRRWRR....",
            "...RRRRRRRRR....",
            "....ggggggg.....",
            ".....sSSSs......",
            ".....sSSSs......",
            ".....sSsSs......",
            ".....sSSSs......",
            "....ssSSSss.....",
            "................",
            "................",
            "................",
        ],
        {"R": "A82818", "W": "F5F0E0", "g": "5A3A28",
         "S": "EFE2C8", "s": "C9B494"},
        "3A1408",
    ),
    # ============================================================ NEVOA_DO_DESERTO
    # Vidrinho com a nevoa lilas enrolando e areia no fundo
    "nevoa_do_deserto": (
        [
            "................",
            "................",
            "......cccc......",
            "......CCCC......",
            ".....sNNNNs.....",
            ".....NNnNNNs....",
            ".....NnNNNNs....",
            ".....NNNnNNs....",
            ".....NnNNNNs....",
            ".....NNnNNNs....",
            ".....SSSSSSs....",
            ".....SsSSSSs....",
            ".....sssssss....",
            "................",
            "................",
            "................",
        ],
        {"c": "C9A16B", "C": "8A6B4A", "N": "B890D8", "n": "9268B8",
         "s": rgba("FFFFFF", 150), "S": "E8C878"},
        "3A2448",
    ),
    # ============================================================ RAIZ_DE_SOMBRA
    # Raiz retorcida: corpo escuro em S, nós claros e radículas
    "raiz_de_sombra": (
        [
            "................",
            "..........rr....",
            ".........rrr....",
            "........rrN.....",
            ".......rrr......",
            "......rrr.......",
            "......Nrr.......",
            ".....rrr........",
            ".....rrrN.......",
            "....rrr.........",
            "....Nrr.........",
            "...rrr..........",
            "..rrr...........",
            "..rr............",
            "................",
            "................",
        ],
        {"r": "2A1830", "R": "3E2448", "N": "6A4878"},
        "0E0612",
    ),
    # ============================================================ CRISTAL_DE_EUFORIA
    # Drusa: cristal central facetado rosa + dois satelites, cintilação
    "cristal_de_euforia": (
        [
            "................",
            "......W.........",
            ".....WlW........",
            ".....lMLw...s...",
            ".....lMLw..sLs..",
            "....WlMLLw..Ll..",
            "....lMLLLLw.Ll..",
            "....lMLLLLw.Llw.",
            "...WlMLLLLwLLlw.",
            "...lMLLLLLLLLLw.",
            "...lMLLLLLLLLLw.",
            "..WlLLLLLLLLLw..",
            "..bLLLLLLLLLb...",
            "..bbbLLLLLbbb...",
            "................",
            "................",
        ],
        {"W": "F8D8EC", "l": "F0A8D8", "M": "E878BC", "L": "C858A0",
         "w": "A83880", "b": "8A2868", "s": rgba("FFFFFF", 200)},
        "5A1040",
    ),
    # ============================================================ EXTRATO_CAFEINA
    # Ampola de farmacia: tambor prateado, liquido cafe, rotulo com faixa
    "extrato_cafeina": (
        [
            "................",
            "................",
            "....KKKKKKKK....",
            "....kssssssk....",
            ".....sLLLLs.....",
            ".....sLLLLs.....",
            ".....sLllLs.....",
            "....sWWWWWWs....",
            "....sWrrrrWs....",
            "....sWWWWWWs....",
            ".....sLLLLs.....",
            ".....sLLLLs.....",
            ".....sLLLLs.....",
            ".....ssssss.....",
            "................",
            "................",
        ],
        {"K": "8A929C", "k": "5A6470", "s": rgba("FFFFFF", 170),
         "L": "6A4228", "l": "4A2C18", "W": "F5F2E8", "r": "C83838"},
        "2A1A10",
    ),
}

ITEMS = [
    "central_comando",
    "cerveja", "vinho", "cachaca", "hidromel", "rum",
    "maconha_seda", "baseado", "cocaina", "heroina", "lsd",
    "po_estelar", "cogumelo_xamanico", "nevoa_do_deserto",
    "raiz_de_sombra", "cristal_de_euforia", "extrato_cafeina",
    "cigarro_camel", "camisa_matanza",
]

# Pos-tratamento por item: rampa de volume + brilho de vidro onde ha corpo
POS = {
    "cerveja": lambda r: brilho(sombra_vertical(r, 4, 11, 5, 11, 0.28), 4, 5, 11, 150),
    "vinho": lambda r: brilho(sombra_vertical(r, 4, 11, 3, 7, 0.30), 4, 3, 7, 170),
    "cachaca": lambda r: brilho(sombra_vertical(r, 4, 10, 4, 13, 0.22), 5, 4, 13, 170),
    "hidromel": lambda r: sombra_vertical(r, 3, 11, 4, 12, 0.30),
    "rum": lambda r: brilho(sombra_vertical(r, 4, 11, 5, 13, 0.25), 5, 5, 13, 120),
    "maconha_seda": lambda r: sombra_vertical(r, 5, 10, 5, 10, 0.15),
    "heroina": lambda r: sombra_vertical(r, 6, 9, 4, 9, 0.20),
    "nevoa_do_deserto": lambda r: brilho(sombra_vertical(r, 5, 10, 4, 12, 0.22), 5, 4, 10, 160),
    "extrato_cafeina": lambda r: brilho(sombra_vertical(r, 4, 11, 4, 13, 0.25), 5, 4, 13, 150),
}

def main():
    tex_dir = os.path.join(ASSETS, "textures", "item")
    items_dir = os.path.join(ASSETS, "items")

    geradas = {}
    for name, (map_lines, palette, outline_hex) in TEXTURES.items():
        rows = render(map_lines, palette)
        if name in POS:
            rows = POS[name](rows)
        rows = outline(rows, outline_hex)
        write_png(os.path.join(tex_dir, name + ".png"), rows)
        geradas[name] = rows

    # icone do mod: caneca de cerveja ampliada 4x (64x64)
    icon_rows = scale(geradas["cerveja"], 4)
    write_png(os.path.join(ASSETS, "icon.png"), icon_rows)

    # definicoes de modelo de item (formato novo, 1.21.4+/26.x)
    for name in ITEMS:
        if name in BEBIDAS_3D or name in CATALOGO_3D:
            continue
        model = {
            "model": {
                "type": "minecraft:model",
                "model": "intoxicantes:item/" + name,
            }
        }
        os.makedirs(items_dir, exist_ok=True)
        with open(os.path.join(items_dir, name + ".json"), "w", encoding="utf-8") as f:
            json.dump(model, f, indent=2)

    # modelos classics apontando para a textura
    models_dir = os.path.join(ASSETS, "models", "item")
    for name in ITEMS:
        if name in BEBIDAS_3D or name in CATALOGO_3D:
            continue
        model = {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "intoxicantes:item/" + name},
        }
        os.makedirs(models_dir, exist_ok=True)
        with open(os.path.join(models_dir, name + ".json"), "w", encoding="utf-8") as f:
            json.dump(model, f, indent=2)

    print("OK: %d texturas (com contorno e sombreamento), icon.png, %d modelos" % (len(geradas), len(ITEMS)))

if __name__ == "__main__":
    main()
