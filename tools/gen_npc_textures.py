"""Gera as texturas dos NPCs: skins recoloridas (layout vanilla intacto) e ovos.

Traficante: skin do wandering trader recolorida pra hoodie escuro + oculos.
Gago (v1.2.22): SKIN PROPRIA 128x128 desenhada pixel a pixel pro GagoModel
  codado do 0 — pele, camisa, colete, avental "R$", chapéu de palha e
  chinelos, com 7 fantasias tematicas por bioma. Nada de villager remendado.
Uso: python tools/gen_npc_textures.py  (a partir da raiz do projeto do mod)
"""
import colorsys
import io
import os
import struct
import zipfile

from PIL import Image, ImageDraw
try:
    from tools.gen_catalogo import ITEM_IDS as CATALOGO_3D
except ModuleNotFoundError:
    from gen_catalogo import ITEM_IDS as CATALOGO_3D

ASSETS = os.path.join("src", "main", "resources", "assets", "intoxicantes")
JAR = os.path.expanduser(
    "~/.gradle/caches/fabric-loom/26.3/minecraft-merged.jar")


def vanilla_png(caminho_no_jar):
    with zipfile.ZipFile(JAR) as z:
        return Image.open(io.BytesIO(z.read(caminho_no_jar))).convert("RGBA")


def salvar(img, rel):
    caminho = os.path.join(ASSETS, rel)
    if "textures/item/" in rel.replace("\\", "/") and os.path.splitext(os.path.basename(rel))[0] in CATALOGO_3D:
        print(f"  SKIP (ovo do catálogo 3D canônico): {os.path.basename(rel)}")
        return
    # v1.2.35: artes MANUAIS do usuário nunca são sobrescritas
    if "textures/item/" in rel.replace("\\", "/") and os.path.exists(caminho) \
            and os.path.getsize(caminho) > 20000:
        print(f"  SKIP (manual do usuário): {os.path.basename(rel)}")
        return
    os.makedirs(os.path.dirname(caminho), exist_ok=True)
    img.save(caminho)


def recolor_por_matiz(img, matiz_min, matiz_max, novo_rgb, manter_luz=True):
    """Substitui pixels cujo matiz esta na faixa, preservando a luminancia."""
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            hh, ll, ss = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            if matiz_min <= hh <= matiz_max and ss > 0.12:
                if manter_luz:
                    fator = max(0.35, min(1.6, ll / 0.42))
                    nr, ng, nb = (min(255, int(c * fator)) for c in novo_rgb)
                else:
                    nr, ng, nb = novo_rgb
                px[x, y] = (nr, ng, nb, a)
    return img


def oculos(img):
    """Oculos escuro na face do villager (face frontal fica em 8..15 x 8..15)."""
    d = ImageDraw.Draw(img)
    d.rectangle([8, 10, 15, 11], fill=(20, 20, 25, 255))       # lentes
    d.point([(7, 10), (16, 10)], fill=(20, 20, 25, 255))       # hastes
    d.point([(11, 10), (12, 10)], fill=(90, 90, 110, 255))     # brilho
    return img


def corrente(img):
    """Corrente de ouco no peito do traficante (regiao do corpo frontal)."""
    d = ImageDraw.Draw(img)
    d.rectangle([20, 21, 27, 21], fill=(212, 175, 55, 255))
    d.point([(21, 22), (26, 22)], fill=(160, 125, 30, 255))
    return img


def egg(cor_base, cor_mancha):
    """Ovo de spawn 16x16 no estilo vanilla (base clara + manchas)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # corpo do ovo: elipse com sombreamento
    d.ellipse([3, 1, 12, 15], fill=cor_base, outline=(60, 55, 50, 255))
    d.ellipse([4, 2, 8, 7], fill=tuple(min(255, c + 28) for c in cor_base))
    d.ellipse([5, 10, 11, 14], fill=tuple(max(0, c - 22) for c in cor_base))
    # manchas
    for mx, my in [(6, 5), (10, 4), (8, 8), (5, 11), (11, 11), (9, 13)]:
        d.point([(mx, my), (mx + 1, my)], fill=cor_mancha)
    return img


# ------------------------------------------------------------------ Traficante
skin = vanilla_png("assets/minecraft/textures/entity/wandering_trader/wandering_trader.png")
# robe azul/verde-azulado do andarilho -> hoodie cinza-esverdeado escuro
skin = recolor_por_matiz(skin, 0.35, 0.60, (52, 66, 56))
skin = oculos(skin)
skin = corrente(skin)
salvar(skin, "textures/entity/traficante.png")

# ================================================================== GAGO 128x128
# Skin desenhada do 0 pro GagoModel (v1.2.22, REFORMADA na v1.2.31). O layout
# de UV abaixo é O MESMO dos texOffs do modelo Java — mexeu num, mexe no outro:
#   cabeca (0,0) 8x10x8     nariz (32,0) 2x2x2 (pequeno, SEM ponta pendurada)
#   corpo  (0,20) 10x12x6   bracos (32,20) 4x8x4  perna (0,40) 4x12x4
#   avental (32,40) 10x4x7  (aba/coroa do chapéu de palha REMOVIDAS — v1.2.31:
#   "na vida real ele é um cara simples, meio velho, de óculos — sem chapelão")
#   chinelo_d (0,90) 3x1x6  chinelo_e (20,90) 3x1x6
PELE   = ((238, 200, 160), (224, 182, 140), (196, 152, 110))   # claro/meio/escuro
CAMISA = ((238, 234, 224), (220, 214, 202), (192, 184, 170))
COLETE = ((118, 64, 38), (96, 50, 28), (68, 34, 18))
CALCA  = ((56, 62, 80), (46, 50, 66), (34, 38, 50))
AVENTAL = ((38, 168, 82), (28, 136, 64), (18, 100, 46))        # verde-dinheiro
PALHA  = ((232, 192, 116), (210, 168, 92), (180, 138, 70))      # faixa do cerrado
GRISALHO = (196, 192, 184)                                     # cabelo de velhinho
ARMACAO = (58, 44, 34)                                          # óculos de tartaruga
VERMELHO = (182, 46, 54)
OURO   = (222, 178, 74)
BRANCO = (242, 240, 232)
COURO  = (104, 66, 34)


def caixa(d, u, v, w, h, dd, top, meio, escuro):
    """Pinta as 6 faces de um box (layout padrao de skin do MC)."""
    d.rectangle([u + dd, v, u + dd + w - 1, v + dd - 1], fill=top)            # topo
    d.rectangle([u + dd + w, v, u + dd + 2 * w - 1, v + dd - 1], fill=escuro)  # fundo
    y0, y1 = v + dd, v + dd + h - 1
    d.rectangle([u, y0, u + dd - 1, y1], fill=meio)                    # lado dir
    d.rectangle([u + dd, y0, u + dd + w - 1, y1], fill=meio)           # FRENTE
    d.rectangle([u + dd + w, y0, u + dd + w + dd - 1, y1], fill=meio)  # lado esq
    d.rectangle([u + 2 * dd + w, y0, u + 2 * dd + 2 * w - 1, y1], fill=escuro)  # costas


def pintar_face(d):
    """O rosto do Gago (v1.2.31): velhinho simples de óculos — sobrancelha
    calma, armação fina de tartaruga, olho manso por trás do vidro e o
    sorriso de quem sabe o preço de tudo. SEM chapéu: cabelo grisalho."""
    # base da face (frente da cabeca: 8..15 x 8..17)
    d.rectangle([8, 8, 15, 17], fill=PELE[1])
    d.rectangle([8, 8, 15, 8], fill=PELE[0])            # testa clara
    d.rectangle([8, 16, 15, 17], fill=PELE[2])          # queixo sombreado
    d.rectangle([8, 8, 8, 17], fill=PELE[2])            # lateral esq sombra
    # CABELO GRISALHO (enterdado, recuada nas têmporas — sem chapéu em cima):
    # a primeira linha da testa vira cabelo (topo da cabeça pinta GRISALHO)
    d.rectangle([8, 9, 15, 9], fill=GRISALHO)
    d.point([(8, 10), (15, 10)], fill=GRISALHO)          # têmporas ralas
    # sobrancelhas calmas de tio de comércio (horizontais, grisalho-escuro)
    d.rectangle([9, 11, 10, 11], fill=(134, 126, 116))
    d.rectangle([13, 11, 14, 11], fill=(134, 126, 116))
    # ÓCULOS de armação fina: lentes 2x1 e a ponte central
    d.rectangle([9, 12, 10, 13], fill=ARMACAO)           # armação esq (anel)
    d.rectangle([13, 12, 14, 13], fill=ARMACAO)           # armação dir
    d.point([(9, 13), (10, 13), (13, 13), (14, 13)], fill=BRANCO)  # vidro
    d.point([(10, 12), (13, 12)], fill=(240, 244, 248))  # brilho do vidro
    d.point([(11, 12), (12, 12)], fill=ARMACAO)          # ponte
    d.point([(8, 12), (15, 12)], fill=ARMACAO)           # hastes nas orelhas
    # olho manso por trás do vidro (meia pupila preguiçosa)
    d.point([(10, 13), (13, 13)], fill=(30, 26, 24))
    # nariz PEQUENO (sombra sutil — o cubo 2x2 fica na frente disso)
    d.rectangle([11, 13, 12, 14], fill=PELE[2])
    # bigode ralo de velhinho (duas linhas finas sobre o sorriso)
    d.point([(9, 15), (10, 15), (13, 15), (14, 15)], fill=GRISALHO)
    # sorriso de quem sabe o preço de tudo
    d.point([(10, 16), (11, 17), (12, 17), (13, 16)], fill=(120, 66, 44))


def pintar_gago_base():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # ===== CABEÇA: pele + rosto; TOPO GRISALHO (sem chapéu, v1.2.31)
    caixa(d, 0, 0, 8, 10, 8, GRISALHO, PELE[1], PELE[2])
    pintar_face(d)
    d.rectangle([0, 12, 7, 12], fill=PELE[0])           # orelha dir (brinco!)
    d.point([(3, 12)], fill=OURO)
    d.rectangle([16, 12, 23, 12], fill=PELE[0])         # orelha esq (brinco!)
    d.point([(19, 12)], fill=OURO)
    # nariz PEQUENO (2x2x2, texOffs 32,0) — pele com narinas
    caixa(d, 32, 0, 2, 2, 2, PELE[0], PELE[1], PELE[2])
    d.rectangle([34, 0, 35, 1], fill=PELE[1])           # frente do cubo
    d.point([(34, 1), (35, 1)], fill=(150, 100, 70))    # narinas

    # ===== CORPO: camisa branca + colete de couro aberto + avental nocol
    caixa(d, 0, 20, 10, 12, 6, COLETE[0], COLETE[1], COLETE[2])       # base colete
    frente = [6, 26, 15, 37]
    d.rectangle(frente, fill=CAMISA[1])                  # camisa na frente
    d.rectangle([6, 26, 8, 37], fill=COLETE[1])          # lapela esq
    d.rectangle([13, 26, 15, 37], fill=COLETE[1])        # lapela dir
    d.rectangle([6, 26, 8, 26], fill=COLETE[0])
    d.rectangle([13, 26, 15, 26], fill=COLETE[0])
    d.rectangle([9, 26, 12, 26], fill=CAMISA[0])         # colarinho
    # avental pescador verde-dinheiro (bib no torso + box nos quadris)
    d.rectangle([9, 30, 12, 37], fill=AVENTAL[1])
    d.rectangle([9, 30, 12, 30], fill=AVENTAL[0])
    caixa(d, 32, 40, 10, 4, 7, AVENTAL[0], AVENTAL[1], AVENTAL[2])
    # R$ no bolso do peito do avental (3x3 cada, branco)
    d.rectangle([40, 47, 48, 50], fill=AVENTAL[1])       # refresca a frente
    d.point([(41, 47), (41, 48), (42, 48), (41, 49), (42, 49), (43, 47),
             (43, 48), (43, 49), (43, 50)], fill=BRANCO)  # R
    d.point([(45, 47), (46, 48), (46, 47), (47, 49), (46, 49), (46, 50),
             (45, 49), (47, 47)], fill=BRANCO)            # $
    d.point([(46, 46)], fill=BRANCO)                     # haste do $

    # ===== BRAÇOS: manga branca arregaçada + mão
    caixa(d, 32, 20, 4, 8, 4, CAMISA[0], CAMISA[1], CAMISA[2])
    d.rectangle([36, 24, 39, 31], fill=CAMISA[1])        # frente
    d.rectangle([36, 28, 39, 28], fill=CAMISA[2])        # barra arregaçada
    d.rectangle([36, 29, 39, 31], fill=PELE[1])          # mão

    # ===== PERNAS: calça de brim com barra dobrada + meia
    caixa(d, 0, 40, 4, 12, 4, CALCA[0], CALCA[1], CALCA[2])
    d.rectangle([4, 44, 7, 55], fill=CALCA[1])
    d.rectangle([4, 50, 7, 51], fill=CALCA[0])           # barra dobrada
    d.rectangle([4, 52, 7, 55], fill=BRANCO)             # meia

    # ===== CHINELOS: sola de borracha azul + tira em V
    for u0 in (0, 20):
        caixa(d, u0, 90, 3, 1, 6, (44, 88, 124), (36, 74, 108), (26, 56, 84))
        tx, ty = u0 + 6, 90                              # face de cima (3x6)
        d.rectangle([tx, ty, tx + 2, ty + 5], fill=(58, 110, 150))
        d.point([(tx + 1, ty + 1), (tx, ty + 2), (tx + 2, ty + 2)], fill=BRANCO)
        d.point([(tx + 1, ty + 3)], fill=BRANCO)
    return img


# 7 FANTASIAS TEMATICAS (ordem = indice do DATA_ROUPA em GagoEntity, 0..6).
# O bioma do mercado escolhe a variante; a identidade (pele+avental+palha)
# fica, muda o detalhe da roupa. O renderer troca a textura pelo nome.
def det_nenhuma(d):
    pass


def det_sertao(d):
    """Sertao: cartucheira de couro no peito (balas de ouro)."""
    d.rectangle([6, 28, 15, 28], fill=COURO)
    for x in range(6, 16, 2):
        d.point([(x, 28)], fill=OURO)


def det_mata(d):
    """Mata: flor vermelha no peito da camisa."""
    d.point([(9, 28), (10, 28), (8, 29), (11, 29)], fill=(214, 64, 74))
    d.point([(9, 29), (10, 29)], fill=(255, 214, 112))


def det_cerrado(d):
    """Cerrado: faixa trançada de palha no avental."""
    d.rectangle([40, 49, 47, 49], fill=PALHA[1])
    for x in range(40, 48, 2):
        d.point([(x, 49)], fill=PALHA[0])


def det_sul(d):
    """Sede Sul: cachecol de la branco + botões de ouro na camisa."""
    d.rectangle([6, 26, 15, 27], fill=(226, 232, 240))
    d.rectangle([6, 27, 15, 27], fill=(196, 204, 216))
    d.point([(11, 30), (11, 32)], fill=OURO)


def det_serra(d):
    """Serra: faixa de la escura no avental (com pontinhos vermelhos)."""
    d.rectangle([40, 47, 47, 47], fill=(30, 46, 34))
    for x in (41, 44, 47):
        d.point([(x, 47)], fill=VERMELHO)


def det_brejo(d):
    """Brejo: salpicos de lama no avental e na perna."""
    for x, y in [(41, 48), (44, 50), (46, 49), (42, 50)]:
        d.point([(x, y)], fill=(64, 52, 30))
    d.point([(5, 52), (6, 53)], fill=(64, 52, 30))


VARIANTE_GAGO = [
    ("gago",         "plains",  det_nenhuma),   # camisa classica
    ("gago_sertao",  "desert",  det_sertao),
    ("gago_mata",    "jungle",  det_mata),
    ("gago_cerrado", "savanna", det_cerrado),
    ("gago_sul",     "snowy",   det_sul),       # sede matriz kkkk
    ("gago_serra",   "taiga",   det_serra),
    ("gago_brejo",   "swamp",   det_brejo),
]

for arquivo, nome, det in VARIANTE_GAGO:
    img = pintar_gago_base()
    det(ImageDraw.Draw(img))
    salvar(img, f"textures/entity/{arquivo}.png")
    print(f"  gago[{nome}] -> {arquivo}.png (128x128)")

# ------------------------------------------------------------------ Ovos de spawn
salvar(egg((226, 230, 224, 255), (52, 140, 84, 255)), "textures/item/ovo_traficante.png")
salvar(egg((226, 230, 224, 255), (176, 40, 48, 255)), "textures/item/ovo_gago.png")
salvar(egg((30, 30, 36, 255), (232, 178, 58, 255)), "textures/item/ovo_juca.png")


# ================================================================== O JUÇA (v1.2.39)
# Jucelino, o Juça: o parça do Gago. Mesmo modelo (barrigão do GagoModel),
# mas a alma é outra: cabelo comprido preto (lado do rock), camisa preta do
# Matanza com caveira no peito, calça jeans e o Camel pendurado na boca.
CABELO_JUCA = ((32, 28, 32), (22, 19, 24), (14, 12, 16))
CAMISA_JUCA = ((44, 44, 54), (30, 30, 38), (20, 20, 26))
JEANS_JUCA  = ((64, 74, 96), (52, 60, 80), (38, 44, 60))


def pintar_juca():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # ===== CABEÇA: cabelo comprido preto nas laterais e no topo
    caixa(d, 0, 0, 8, 10, 8, CABELO_JUCA[1], PELE[1], PELE[2])
    pintar_face_juca(d)
    # laterais da cabeça = cabelo descendo (a juba)
    d.rectangle([0, 8, 7, 15], fill=CABELO_JUCA[1])       # lado direito (face -x)
    d.rectangle([16, 8, 23, 15], fill=CABELO_JUCA[1])     # lado esquerdo
    d.rectangle([24, 8, 31, 15], fill=CABELO_JUCA[2])     # trás: nuca cheia
    d.rectangle([0, 12, 7, 12], fill=CABELO_JUCA[0])      # orelha dir espreita
    d.rectangle([16, 12, 23, 12], fill=CABELO_JUCA[0])    # orelha esq
    # nariz 2x2 (texOffs 32,0 — igual do Gago)
    caixa(d, 32, 0, 2, 2, 2, PELE[0], PELE[1], PELE[2])
    d.rectangle([34, 0, 35, 1], fill=PELE[1])

    # ===== CORPO: camisa preta do Matanza (mangas nas laterais)
    caixa(d, 0, 20, 10, 12, 6, CAMISA_JUCA[0], CAMISA_JUCA[1], CAMISA_JUCA[2])
    frente = [6, 26, 15, 37]
    d.rectangle(frente, fill=CAMISA_JUCA[1])
    # A CAVEIRA DO MATANZA no peito (estampa branca 4x5)
    d.rectangle([9, 29, 12, 29], fill=(232, 228, 220))     # topo do crânio
    d.rectangle([9, 30, 12, 31], fill=(232, 228, 220))
    d.point([(9, 31), (12, 31)], fill=(20, 20, 26))        # olhos
    d.rectangle([10, 32, 11, 33], fill=(232, 228, 220))    # maxilar
    d.point([(10, 33), (11, 33)], fill=(20, 20, 26))       # dentes
    # gola V aberta (a camisa de show nunca é fechada)
    d.point([(9, 26), (12, 26)], fill=PELE[1])
    d.point([(10, 26), (11, 26)], fill=PELE[1])
    d.point([(10, 27), (11, 27)], fill=PELE[1])

    # ===== BRAÇOS: manga preta curta + tatuagem de braço (a do bar kkkk)
    caixa(d, 32, 20, 4, 8, 4, CAMISA_JUCA[0], CAMISA_JUCA[1], CAMISA_JUCA[2])
    d.rectangle([36, 24, 39, 27], fill=PELE[1])            # braço de fora
    d.point([(37, 25), (38, 26)], fill=(40, 60, 90))       # tat signa
    d.rectangle([36, 28, 39, 31], fill=PELE[1])            # mão

    # ===== PERNAS: jeans do rockeiro
    caixa(d, 0, 40, 4, 12, 4, JEANS_JUCA[0], JEANS_JUCA[1], JEANS_JUCA[2])
    d.rectangle([4, 44, 7, 55], fill=JEANS_JUCA[1])
    d.point([(5, 50), (6, 52)], fill=(80, 92, 116))        # costura do jeans

    # ===== BOTAS de couro (ele não usa chinelo como o Gago kkkk)
    for u0 in (0, 20):
        caixa(d, u0, 90, 3, 1, 6, COURO, (86, 54, 28), (60, 38, 18))
        tx, ty = u0 + 6, 90
        d.rectangle([tx, ty, tx + 2, ty + 5], fill=(96, 62, 32))
        d.point([(tx + 1, ty + 2)], fill=(60, 38, 18))     # cadarço
    return img


def pintar_face_juca(d):
    """A cara do Juça: olhar de quem dormiu no bar, barba rala e o CAMEL
    pendurado no canto da boca (a marca registrada — brasa acesa)."""
    d.rectangle([8, 8, 15, 17], fill=PELE[1])
    # franja preta caindo na testa (a juba desce até a sobrancelha)
    d.rectangle([8, 8, 15, 10], fill=CABELO_JUCA[1])
    d.point([(9, 11), (12, 11), (15, 11)], fill=CABELO_JUCA[1])  # franja desigual
    # olhos semi-cerrados (de quem acordou agora)
    d.rectangle([9, 12, 10, 12], fill=(24, 20, 18))        # olho esq (traço)
    d.rectangle([13, 12, 14, 12], fill=(24, 20, 18))       # olho dir
    # barba rala de 3 dias (pontos escuros no queixo e canto)
    for x, y in [(8, 16), (10, 16), (12, 16), (14, 16), (9, 17), (13, 17), (15, 15), (8, 15)]:
        d.point([(x, y)], fill=(74, 62, 50))
    # O CAMEL AMARELO: cigarro pendurado no canto direito da boca
    d.rectangle([14, 14, 15, 15], fill=(232, 178, 58))     # filtro amarelo
    d.point([(15, 15)], fill=(240, 238, 230))              # papel na ponta
    d.point([(16, 15)], fill=(255, 140, 40))               # a BRASA
    d.point([(16, 14)], fill=(120, 120, 124))              # fumaça subindo
    return img


img = pintar_juca()
salvar(img, "textures/entity/juca.png")
print("  juca -> juca.png (128x128, camisa do Matanza + Camel na boca)")

print("NPCs OK: skin nova do Gago (128x128) + traficante + ovos de spawn.")
