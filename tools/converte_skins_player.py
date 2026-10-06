"""v1.2.44 — Converte as skins dos NPCs pro CORPO DE PLAYER (steve 64x64).

Antes: Gago/Juça usavam o GagoModel proprio (128x128, barrigao com avental
3D) e o traficante usava o VillagerModel (64x64). Agora os tres usam o
HumanoidModel padrao do player — este script remapeia a ARTE pintada pro
layout steve, sem distorcer nada (crop 1:1, nearest).

Fontes:
  gago*.png / juca.png    — layout GagoModel 128x128 (texOffs do GagoModel)
  traficante.png          — layout villager/wandering_trader 64x64
Destino (mesmos nomes de arquivo, em mod E pack):
  layout steve 64x64 (cabeca 8x8x8, corpo 8x12x4, bracos 4x12x4, pernas)

Mapeamento (fonte -> destino), todos em crops 1:1:
  cabeca:   8x10x8 -> 8x8x8 (linhas 2..9 da fonte = rosto inteiro; as 2
            primeiras linhas eram testa/cabelo chapado, o cabelo fica no
            topo da cabeca + camada hat)
  corpo:    frente 10x12 -> 8x12 (corta 1 coluna de lapela de cada lado;
            camisa/avental/lapelos preservados), lados 6->4 (corta 1 de
            cada lado), fundo 10->8, topo 10x6->8x4 (corta borda)
            "quadril" do avental (box 3D) -> face de BAIXO do corpo
  bracos:   4x8 -> 4x12 (manga esticada com a barra arretecida repetida,
            mao nos rows 8..10, punho escuro no 11)
  pernas:   4x12 -> 4x12 copia 1:1 (calca preta + meia vao inteiros)
  nariz:    frente do cubo 2x2 pintada no centro do rosto + sombra lateral
  R$:       repintado de branco no peito do avental (o box 3D morreu)
v1.2.43: sombreamento suave nos membros (sombrear_membro) — luz direcional
  por face + gradiente ombro->mao/quadril->pe, aproveitando as 12 linhas
  do formato steve (o barrigao 4x8 fonte nao tinha essa altura).
v1.2.44 — OS BURACOS: o diagnostic pixel a pixel mostrou que as FONTES de
  backup ja tem regioes vazias onde o modelo amostra (torso do gago com as
  colunas 26..27 transparentes, bracos/pernas do traficante semiacabados).
  O conserto e AUTOCURAVEL: depois de montar a skin, tapar_buracos() preenche
  qualquer pixel transparente nas regioes amostradas com a cor do vizinho
  opaco mais proximo (inpaint por aneis de busca) e a auditoria de alpha
  FAILA O BUILD se ainda sobrar buraco — regenerar skins nunca mais sai
  com corpo furado no jogo.
Uso: python tools/converte_skins_player.py  (a partir de intoxicantes-mod/)
"""
import os

from PIL import Image

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # intoxicantes-mod/
MOD = os.path.join(RAIZ, "src", "main", "resources", "assets", "intoxicantes", "textures", "entity")
PACK = os.path.join(RAIZ, "..", "resourcepacks", "minhas-texturas", "assets", "intoxicantes", "textures", "entity")
PACK = os.path.normpath(PACK)
BACKUP = os.path.join(RAIZ, "backups", "20260923-skins-player")  # fontes IMUTAVEIS


def abrir_fonte(nome):
    """Abre a skin original (pre-conversao) do backup: pack primeiro, depois mod."""
    for base in (os.path.join(BACKUP, "pack"), os.path.join(BACKUP, "mod")):
        caminho = os.path.join(base, nome + ".png")
        if os.path.exists(caminho):
            src = Image.open(caminho).convert("RGBA")
            return src
    raise FileNotFoundError(nome + ".png")


def para_grade(src, grade):
    """Re-rasteriza a arte pro grid logico (ex.: 1254x1254 -> 64x64, nearest).
    O art do traficante no pack e um upscale blocky do layout villager; o
    nearest preserva os blocos pintados sem misturar cores de blocos vizinhos."""
    if src.width == grade:
        return src
    return src.resize((grade, grade), Image.NEAREST)

# ---------------------------------------------------------------- helpers

def recorte(img, x, y, w, h):
    return img.crop((x, y, x + w, y + h))


def colar(dst, regiao, x, y):
    """Cola direto (sem mascara) preservando pixel a pixel o alfa da regiao."""
    dst.paste(regiao, (x, y))


def escurecer(img, x, y, fator=0.82):
    p = img.getpixel((x, y))
    img.putpixel((x, y), (int(p[0] * fator), int(p[1] * fator), int(p[2] * fator), p[3]))


def sombrear_membro(img, u, v):
    """v1.2.43: sombreamento suave no membro steve 4x12 (as linhas extras do
    formato) — luz vinda de cima, estilo vanilla:
      frente integral, lado direito 0.95, lado esquerdo 0.90, costas 0.93,
      face de BAIXO 0.82 e gradiente vertical ombro(1.0) -> mao/pe(0.88).
    So escurece onde tem pixel (alfa preservado pixel a pixel)."""
    d = img.load()
    for i, fator_face in enumerate((0.95, 1.0, 0.90, 0.93)):  # D, frente, E, costas
        x0 = u + i * 4
        for linha in range(12):
            fator = fator_face * (1.0 - 0.01 * linha)  # gradiente ombro->ponta
            for col in range(4):
                x, y = x0 + col, v + 4 + linha
                p = d[x, y]
                if p[3] == 0:
                    continue
                d[x, y] = (int(p[0] * fator), int(p[1] * fator), int(p[2] * fator), p[3])
    for col in range(4):  # face de baixo: sombra de chao
        for linha in range(4):
            x, y = u + 8 + col, v + linha
            p = d[x, y]
            if p[3] == 0:
                continue
            d[x, y] = (int(p[0] * 0.82), int(p[1] * 0.82), int(p[2] * 0.82), p[3])


# ---------------------------------------------------------------- cabeca
# O box de cabeca e IDENTICO nos dois layouts fonte: (0,0) 8x10x8.
# Destino steve: topo(8,0) fundo(16,0) ladoD(0,8) FRENTE(8,8) ladoE(16,8)
#                costas(24,8) — tudo 8x8. Camada hat: (40,0)/(32,8)...

def converte_cabeca(src, dst, com_nariz=False):
    # topo e fundo sao 8x8 nos DOIS layouts — copia direta
    colar(dst, recorte(src, 8, 0, 8, 8), 8, 0)     # topo
    colar(dst, recorte(src, 16, 0, 8, 8), 16, 0)   # fundo
    # faces laterais: 8x10 -> 8x8 (linhas 2..9 da fonte: rosto inteiro,
    # sorriso incluido; cai fora testa/cabelo chapado das linhas 0..1)
    for (sx, dx) in [(0, 0), (8, 8), (16, 16), (24, 24)]:  # ladoD, FRENTE, ladoE, costas
        colar(dst, recorte(src, sx, 10, 8, 8), dx, 8)
    # camada hat: mesma coisa deslocada pro slot de overlay
    colar(dst, recorte(dst, 0, 0, 32, 16), 32, 0)
    if com_nariz:
        # nariz: frente do cubo 2x2 (34,1) no centro do rosto (11..12, 12..13)
        colar(dst, recorte(src, 34, 1, 2, 2), 11, 12)
        for yy in (12, 13):
            escurecer(dst, 10, yy)
            escurecer(dst, 13, yy)
    return dst


# ---------------------------------------------------------------- corpo

def converte_corpo(src, dst, u_body, w_body, u_avental=0, com_R=False):
    """Corpo. u_body/w_body: x e largura do box do torso no layout fonte
    (gago: (0,10) 10-wide; villager: (16,8) 8-wide). u_avental: x da FRENTE
    do box do avental (so no gago; vai pra face de baixo do torso)."""
    dd = 6  # profundidade 6 nos dois layouts fonte
    v = 20  # y do box do torso
    trim = w_body - 8  # colunas a cortar (1 de cada lado no gago, 0 no WT)

    # v1.2.44 — ORDEM DAS SOBREPOSIÇÕES: lado direito primeiro, frente DEPOIS.
    # Na arte antiga a frente colada antes perdia pixels pros lados/na tira
    # do quadril (as 2 colunas furadas do x26..27 que apareciam no jogo).
    colar(dst, recorte(src, u_body + trim, v + dd, 4, 12), 16, 20)   # lado D
    colar(dst, recorte(src, u_body + dd + w_body + trim, v + dd, 4, 12), 28, 20)  # lado E
    colar(dst, recorte(src, u_body + 2 * dd + w_body + trim, v + dd, 8, 12), 32, 20)  # costas
    frente = recorte(src, u_body + dd + trim, v + dd, w_body - 2 * trim, 12)
    colar(dst, frente, 20, 20)                          # FRENTE (20,20) 8x12 — por ultimo, vence
    colar(dst, recorte(src, u_body + dd + trim, v, 8, 4), 20, 16)     # topo
    # face de baixo: o "quadril" — no gago recebe a frente do avental (box 3D).
    # v1.2.44: em (28,16) o x28..x29 sobrepõe as colunas 0..1 da FRENTE do
    # torso (v+dd+8 = y28) — colar ANTES da frente pra frente ganhar.
    tira = None
    if u_avental:
        candidata = recorte(src, u_avental + 1, 47, 8, 4)
        if min(candidata.getchannel("A").getextrema()) > 0:
            tira = candidata
    if tira is None:
        tira = recorte(src, u_body + dd + trim, v + dd + 8, 8, 4)  # rows de quadril do torso
    colar(dst, tira, 28, 16)
    # v1.2.44: RECOLA a frente por cima (o x28..29 da tira pode ter mordido as colunas 0..1)
    colar(dst, frente, 20, 20)

    if com_R:
        # R$ branco no peito do avental (avental = colunas 2..5, linhas 4..11)
        d = dst.load()
        R = (242, 240, 232, 255)
        for (x, y) in [(2, 5), (3, 5), (4, 5),          # R topo
                       (2, 6), (4, 6),                  # R meio
                       (2, 7), (3, 7), (4, 7),          # R base
                       (2, 8), (4, 8),                  # R pernas
                       (5, 5), (5, 6), (5, 7), (5, 8),  # $ haste
                       (4, 6), (4, 7)]:                 # $ barra do S
            d[x, y] = R
    return dst


# ---------------------------------------------------------------- bracos
# Fonte 4x8x4 -> destino 4x12x4. Manga nas linhas 0..4 (com a barra
# arretecida da linha 4 repetida nas 5..7), mao 8..10, punho 11.

def converte_braco(src, dst, u_src, v_src, u_dst, v_dst, cor_mao):
    faces = [("lado", 0), ("frente", 4), ("lado2", 8), ("costas", 12)]
    for nome, fx in faces:
        sx = u_src + fx
        dx = u_dst + fx
        fonte = recorte(src, sx, v_src + 4, 4, 8)
        for linha in range(12):
            if linha <= 4:
                sy = linha
            elif linha <= 7:
                sy = 4          # barra arretecida repetida
            elif linha <= 10:
                sy = linha - 3  # mao (linhas 5..7 da fonte)
            else:
                sy = None
            if sy is None:
                # punho escuro: cor da mao * 0.8
                dst.paste(Image.new("RGBA", (4, 1), tuple(int(c * 0.8) for c in cor_mao[:3]) + (255,)), (dx, v_dst + 4 + linha))
            else:
                colar(dst, recorte(fonte, 0, sy, 4, 1), dx, v_dst + 4 + linha)


def converte_bracos(src, dst, u_src, cor_mao):
    # braco direito: (40,16); esquerdo: (32,48) — ambos 4x12x4
    for (u_dst, v_dst) in [(40, 16), (32, 48)]:
        converte_braco(src, dst, u_src, 20, u_dst, v_dst, cor_mao)
    # v1.2.44 — face de CIMA do membro vem da FONTE (gago/juca: box do braco
    # (32,20): cima=(36,20); villager: (44,20)).
    for (u_dst, v_dst) in [(44, 16), (36, 48)]:
        colar(dst, recorte(src, u_src + 4, 20, 4, 4), u_dst, v_dst)   # cima
    for (u_dst, v_dst) in [(48, 16), (40, 48)]:
        colar(dst, recorte(src, u_src + 8, 20, 4, 4), u_dst, v_dst)   # baixo
    # v1.2.43: sombreamento suave (luz direcional + gradiente ombro->mao)
    for (u_dst, v_dst) in [(40, 16), (32, 48)]:
        sombrear_membro(dst, u_dst, v_dst)


# ---------------------------------------------------------------- pernas
# Fonte e destino: 4x12x4 — copia 1:1 das 4 faces laterais.

def converte_pernas(src, dst, u_src, v_src):
    for (u_dst, v_dst) in [(0, 16), (16, 48)]:  # perna D e perna E
        for fx in (0, 4, 8, 12):
            colar(dst, recorte(src, u_src + fx, v_src + 4, 4, 12), u_dst + fx, v_dst + 4)
        colar(dst, recorte(src, u_src + 4, v_src, 4, 4), u_dst + 4, v_dst)      # cima
        colar(dst, recorte(src, u_src + 8, v_src, 4, 4), u_dst + 8, v_dst)      # baixo
    # v1.2.43: sombreamento suave (luz direcional + gradiente quadril->pe)
    for (u_dst, v_dst) in [(0, 16), (16, 48)]:
        sombrear_membro(dst, u_dst, v_dst)


# ---------------------------------------------------------------- skins

def gago_para_player(src, com_R=True):
    """Layout GagoModel 128x128 -> steve 64x64."""
    dst = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    converte_cabeca(src, dst, com_nariz=True)
    converte_corpo(src, dst, u_body=0, w_body=10, u_avental=39, com_R=com_R)
    cor_mao = src.getpixel((38, 30))  # mao do braco fonte (frente, linha 6)
    converte_bracos(src, dst, u_src=32, cor_mao=cor_mao)
    converte_pernas(src, dst, u_src=0, v_src=40)
    return dst


def villager_para_player(src):
    """Layout villager/wandering_trader 64x64 -> steve 64x64."""
    dst = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    converte_cabeca(src, dst, com_nariz=False)
    converte_corpo(src, dst, u_body=16, w_body=8)
    cor_mao = src.getpixel((12, 13))  # tom de pele do rosto
    converte_bracos(src, dst, u_src=40, cor_mao=cor_mao)
    converte_pernas(src, dst, u_src=0, v_src=20)
    # corrente de ouco: repintada no peito (no villager ficava no topo do
    # torso, invisivel — agora aparecem 4 elos + pendente)
    d = dst.load()
    ouro = (212, 175, 55, 255)
    ouro_esc = (160, 125, 30, 255)
    for x in (22, 23, 24, 25):
        d[x, 21] = ouro
    d[23, 22] = ouro_esc
    d[24, 22] = ouro_esc
    return dst


# ---------------------------------------------------------------- buracos v1.2.44
# Face de cada box que o HumanoidModel AMOSTRA; qualquer pixel transparente
# nelas vira "buraco" no corpo. Autocura: inpaint pelo vizinho opaco mais
# proximo (aneis de busca 1..4 px) e auditoria no fim.

REGIONES_STEVE = {
    "cabeca":       [(8, 0, 8, 8), (16, 0, 8, 8), (0, 8, 8, 8), (8, 8, 8, 8), (16, 8, 8, 8), (24, 8, 8, 8)],
    "corpo":        [(20, 16, 8, 4), (28, 16, 8, 4), (16, 20, 4, 12), (20, 20, 8, 12), (28, 20, 4, 12), (32, 20, 8, 12)],
    "braco D":      [(44, 16, 4, 4), (48, 16, 4, 4), (40, 20, 16, 12)],
    "braco E":      [(36, 48, 4, 4), (40, 48, 4, 4), (32, 52, 16, 12)],
    "perna D":      [(4, 16, 4, 4), (8, 16, 4, 4), (0, 20, 16, 12)],
    "perna E":      [(20, 48, 4, 4), (24, 48, 4, 4), (16, 52, 16, 12)],
}


def tapar_buracos(img, nome):
    """v1.2.44 — AUTOCURA: preenche pixels transparentes das regioes amostradas
    com a cor do vizinho opaco mais proximo (aneis 1..4 px). Buracos de fonte
    (torso do gago, membros do traficante) somem com a cor do tecido vizinho —
    invisivel no jogo. Devolve quantos pixels tapou (pro log)."""
    px = img.load()
    tapados = 0
    for parte, faces in REGIONES_STEVE.items():
        for (x0, y0, w, h) in faces:
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    if px[x, y][3] > 0:
                        continue
                    achou = None
                    for raio in range(1, 5):
                        for dy in range(-raio, raio + 1):
                            for dx in range(-raio, raio + 1):
                                if max(abs(dx), abs(dy)) != raio:
                                    continue
                                nx, ny = x + dx, y + dy
                                if 0 <= nx < img.width and 0 <= ny < img.height \
                                        and px[nx, ny][3] > 0:
                                    achou = px[nx, ny]
                                    break
                            if achou:
                                break
                        if achou:
                            break
                    if achou:
                        px[x, y] = (achou[0], achou[1], achou[2], 255)
                        tapados += 1
    if tapados:
        print(f"    {nome}: {tapados}px furados tapados com o vizinho (autocura)")
    return tapados


def auditar(img, nome):
    """Garantia final: ZERO pixel transparente onde o modelo amostra."""
    px = img.load()
    buracos = []
    for parte, faces in REGIONES_STEVE.items():
        for (x0, y0, w, h) in faces:
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    if px[x, y][3] == 0:
                        buracos.append((parte, x, y))
    if buracos:
        raise SystemExit(f"BURACOS em {nome}: {len(buracos)} px transparentes "
                         f"amostrados pelo modelo (ex.: {buracos[:6]})")


# ---------------------------------------------------------------- main

GAGOS = ["gago", "gago_sertao", "gago_mata", "gago_cerrado",
         "gago_sul", "gago_serra", "gago_brejo"]


def main():
    os.makedirs(MOD, exist_ok=True)
    os.makedirs(PACK, exist_ok=True)

    for nome in GAGOS:
        src = para_grade(abrir_fonte(nome), 128)
        dst = gago_para_player(src, com_R=True)
        tapar_buracos(dst, nome)
        auditar(dst, nome)
        dst.save(os.path.join(MOD, nome + ".png"))
        dst.save(os.path.join(PACK, nome + ".png"))
        print(f"  {nome}.png  -> 64x64 ok (auditoria de buracos passou)")

    # traficante: a arte do usuario no pack era 1254x1254 (upscale blocky do
    # layout villager) — para_grade() traz ela pro grid 64 antes dos crops
    src = para_grade(abrir_fonte("traficante"), 64)
    dst = villager_para_player(src)
    tapar_buracos(dst, "traficante")
    auditar(dst, "traficante")
    dst.save(os.path.join(MOD, "traficante.png"))
    dst.save(os.path.join(PACK, "traficante.png"))
    print("  traficante.png  villager -> steve 64x64 ok (auditoria passou)")

    # juca: layout do gago mas SEM avental e SEM R$ (ele nao e o mercador)
    src = para_grade(abrir_fonte("juca"), 128)
    dst = gago_para_player(src, com_R=False)
    tapar_buracos(dst, "juca")
    auditar(dst, "juca")
    dst.save(os.path.join(MOD, "juca.png"))
    dst.save(os.path.join(PACK, "juca.png"))
    print("  juca.png  -> 64x64 ok (auditoria passou)")

    print("Pronto: 9 skins convertidas pro layout steve, zero buracos.")


if __name__ == "__main__":
    main()
