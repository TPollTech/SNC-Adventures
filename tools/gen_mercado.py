"""Gera os JSONs de worldgen do Mercado Esquinão + o template NBT da estrutura.

O NBT segue o formato REAL do 26.3 (decifrado do igloo/top.nbt do jar vanilla):
  size     = TAG_List de 3 TAG_Int
  entities = TAG_List de TAG_Compound {blockPos: [i,i,i], pos: [d,d,d], nbt: {...}}
  blocks   = TAG_List de TAG_Compound {pos: [i,i,i], state: TAG_Int, nbt?}
  palette  = TAG_List de TAG_Compound {id: str, properties?: {str:str}, nbt?}
  DataVersion = TAG_Int

Layout (v1.2.61 — MERCADO ESQUINÃO V2, 27x8x27; substitui o v1 15x8x19 no
MESMO arquivo mercado_gago.nbt — mundos antigos continuam funcionando porque
as âncoras do Java são relativas ao CENTRO GEOMÉTRICO, que o v2 respeita):
lote 27x27 INTEIRO de piso sólido (anti-mato: calçada perimetral + meio-fio
em TODO o contorno — nada de mato/árvore/água nascendo dentro), PRÉDIO 21 de
fachada (x3..23) com vitrines de vidro e PORTA-GRADE no vão (x13), PÁTIO de
asfalto com vagas demarcadas + vaga PCD azul + TRAVESSIA só na calçada
(v1.2.68: o pátio inteiro é VAGA — faixa de pedestre no meio do estacionamento
não existe na vida real), 2 postes de luz NOS CANTOS do meio-fio (fora de
vaga — o v2 plantava 4 postes DENTRO das vagas demarcadas) e o hidrante na
calçada ao lado da travessia. Interior FUNCIONAL: geladeiras de bebida no fundo (vidro),
DUAS ILHAS de PRATELEIRA DE MERCADO no salão (v1.2.76 — a gôndola que VENDE:
8 slots por lado, entrega na QUARTA; as "gôndolas" de quartzo/slab do v1.2.61–75
SAÍRAM: eram furniture mudo que parecia estoque e não vendia nada),
balcão com CAIXA e o Gago de plantão atrás (v1.2.76: ele serve NO BALCÃO,
24h — o plantão do guichê na porta foi extinto). MOBÍLIA DE SERVIÇO NAS LATERAIS
(v1.2.78: quando as prateleiras de imitação saíram, o salão ficou vazio nas
paredes. Engradado de estoque, bebedouro e máquina de lavar são BLOCOS DO MOD
(modelados em 3D por tools/gen_mobilia_mercado.py — nada de improviso com
bloco vanilla), encostados em x3/x23; os corredores x4..x6, x19..x22 e o do
balcão x13/x14 ficam LIVRES — travado pelo validate_worldgen). TETO ILUMINADO:
3 fileiras de LÂMPADA LED
(tubo convencional suspenso, sempre aceso — v1.2.68).

ÂNCORAS (o Java as calcula a partir do CENTRO do bounding box = célula (13,13),
tanto na descoberta espiral (bb.minX()+span/2, bb.minY()) quanto no
/gagomarket rebuild (origem = âncora - tam/2 — verificadas no bytecode 26.3:
bb.minY = origem.y e o centro X/Z cai sempre na célula 13):
  PORTA-GRADE        = centro+(0,0,4)  -> (13, y1..y2, 17)  [posPortaReal]
  PORTA (âncora)     = centro+(0,0,3)  -> (13, y1, 16)       [PORTA_LOCAL — ar!;
                     v1.2.76: o Gago NÃO desce mais pra lá (serve 24h no
                     balcão); o offset segue vivo como âncora da porta e do
                     ponto do Juça na calçada]
  BALCÃO/Gago        = centro+(-3,0,-2) -> (10, y1, 11)     [BALCAO_LOCAL — ar!]
  faixa LED          = 15 blocos na frente da fachada (x6..20, y3, z18, sob o beiral)
  painel de OFERTAS  = (8..10, y2, 18), NBT só no cabeça x8  [alvo da reforma 1.2.40]
  MINI display       = (18, y1, 18) "ABERTO · 24H"           [alvo da reforma 1.2.51]
As reforms do zelador (reformarPatio/reformarEntrada, que giram o offset
(lx-7, lz-5) — o MESMO centro da célula 13) viram NO-OP por construção:
  - faixa (12..14, y1, 19): y0 = calçada W (não asfalto) -> reforma não pinta
    mais NADA (v1.2.68: só RECOLHE faixa/hidrante/poste caídos no asfalto);
  - hidrante (11, y1, 19): JÁ NASCE com o hidrante na calçada;
  - hidrantes velhos (8, y1, 22)/(18, y1, 22) e o hidrante-em-vaga do
    1.2.61–67 (10, y1, 20): o zelador RECOLHE (o chão é asfalto);
  - postes velhos em vaga (3/23, z20/z23): recolhidos pelo zelador;
  - display velho (12, y2, 18): ar (sem painel_led) -> reforma não dispara;
  - porta spruce (13, y1, 17): é porta_grade -> troca não dispara;
  - mini display (18, y1, 18): ocupado pelo próprio template -> não planta.
Duas garantias de "espaço seguro" (mantidas do v1):
  1. TODO o volume do template é colocado, inclusive o AR — mata árvore,
     cana e folhagem invasora, e desalaga o box (água de lago/raso some);
  2. terrain_adaptation BEARD_BOX no JSON de estrutura: o terreno sob a
     fundação inteira é aterrado em caixa.
REGRA DO ZELADOR: a varredura destrói QUALQUER bloco de #minecraft:logs no
raio ±10 — por isso o v2 NÃO usa NENHUM tronco (molduras de concreto). O char
"L" (spruce_log) saiu da paleta e o override de "L" das regiões ficou órfão
de propósito.

O LETREIRO da fachada continua a placa CUSTOM (intoxicantes:placa_esquinao):
faixa preta contínua de 15 blocos (7X + J + 7X) na linha y3 montada sob o
beiral, com NBT SÓ no painel central. O quadro de CARDÁPIO interno continua
placa vanilla (vitrine "de papel" da bodega, o texto real fica na tela do menu).

Uso: python tools/gen_mercado.py  (a partir da raiz do projeto do mod)
"""
import gzip
import json
import os
import struct
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__))) + os.sep

DATA_VERSION = 5023  # 26.3

# ============================================================ PALETA (chars -> estado)
PALETTE = {
    "W": {"id": "minecraft:white_concrete"},                     # fachada/telhado/calçada
    "G": {"id": "minecraft:green_concrete"},                     # paredes verdes
    "Q": {"id": "minecraft:smooth_quartz"},                      # piso interno/corpo das gôndolas
    # linhas das vagas + meio-fio do lote
    "y": {"id": "minecraft:gray_concrete"},
    # vaga PCD (piso azul demarcado)
    "a": {"id": "minecraft:blue_concrete"},
    # v1.2.19: ASFALTO do estacionamento (bloco do mod — tapete denso tom de
    # asfalto; vagas demarcadas pelas linhas "y" em cima)
    "A": {"id": "intoxicantes:asfalto"},
    # v1.2.68: POSTES de luz do mod — POSTE COMPLETO de 3 blocos: BASE (y1,
    # sobre a calçada z19) + CORPO (y2) + TOPO (y3). DOIS postes, nos CANTOS
    # do lote (x0/x26, z19) — postes DENTRO de vaga demarcada são vagas mortas
    # (o v2 plantava 4, todos sobre faixa de vaga). Nos cantos iluminam a
    # fachada e a saída sem matar vaga nenhuma.
    "N": {"id": "intoxicantes:poste_luz", "properties": {
        "lit": "true", "parte": "base"}},
    "C": {"id": "intoxicantes:poste_luz", "properties": {
        "lit": "true", "parte": "corpo"}},
    "O": {"id": "intoxicantes:poste_luz", "properties": {
        "lit": "true", "parte": "topo"}},
    # v1.2.24: FAIXA DE PEDESTRE (tinta da travessia, 1px de altura) — vai
    # SOBRE a CALÇADA (y1), alinhada com a porta. NUNCA sobre o asfalto:
    # o pátio inteiro é vaga (v1.2.68 — travessia no meio do estacionamento
    # não existe na rua real).
    "f": {"id": "intoxicantes:faixa_pedestre"},
    # HIDRANTE (v1.2.29): UM, na CALÇADA ao lado da travessia (x11,z19) —
    # hidrante em VAGA é o oposto da vida real (perto de hidrante se PROÍBE
    # estacionar). Exatamente onde o zelador plantaria o dele: o template
    # ocupa o lugar e a reforma vira no-op.
    "H": {"id": "intoxicantes:hidrante"},
    # v1.2.51: a PORTA-GRADE do guichê (porta dupla do mod, codada do zero):
    # de madrugada o guichê FECHA (colisão no vão embaixo) e o Gago atende
    # POR TRÁS da grade de ferro; de dia abre (passagem livre). A virada é do
    # Zelador do mercado (MarketSystem) — a entidade-dono "porta_grade" é
    # colocada junto (o Java aceita se já existir por código).
    "g": {"id": "intoxicantes:porta_grade", "properties": {
        "facing": "south", "half": "lower", "fechada": "false"}},
    # metade de cima da porta-grade: CHAR PROPRIO ("d"). Na 1.2.51 ela era
    # "G" — e o Python deixa a última definição vencer, então o "G" da
    # porta SOBRESCREVEU o "G" das paredes: o mercado inteiro nasceu como
    # porta de guichê (o print do Skyu). "d" não colide com parede nenhuma.
    "d": {"id": "intoxicantes:porta_grade", "properties": {
        "facing": "south", "half": "upper", "fechada": "false"}},
    # v1.2.61: SEM TRONCOS — o zelador destrói qualquer #minecraft:logs dentro
    # do raio ±10 do centro (árvore invasora), então colunas/mobiliário de
    # tronco sumiriam. v1.2.76: o SLAB é só o BALCÃO (nenhuma "prateleira"
    # de imitação sobrou no salão — as gôndolas que vendem são o bloco do mod).
    "s": {"id": "minecraft:smooth_stone_slab", "properties": {"type": "bottom"}},  # base do balcão
    "t": {"id": "minecraft:smooth_stone_slab", "properties": {"type": "top"}},     # tampo do balcão
    # v1.2.75: O CAIXA DO MERCADINHO — registradora 3D com visor do carrinho,
    # no CENTRO do balcão (x10, z12), de frente pro corredor (facing=south).
    # O posto de TRÁS (10,1,11) é EXATAMENTE o BALCAO_LOCAL do MarketSystem:
    # o Gago âncora do gerenciador nasce no posto que o CaixaMercadoBlock
    # espera — dois sistemas, o mesmo lugar (o caixa já sai ATENDENDO).
    "c": {"id": "intoxicantes:caixa_mercado", "properties": {
        "facing": "south", "half": "lower"}},
    "u": {"id": "intoxicantes:caixa_mercado", "properties": {
        "facing": "south", "half": "upper"}},
    # v1.2.68: LÂMPADA LED de teto — tubo convencional suspenso (difusor
    # branco + ponteiras de alumínio + hastes), luz nível 15, SEMPRE acesa
    # por enquanto. Três fileiras cruzam o salão por cima dos corredores.
    "L": {"id": "intoxicantes:lampada_led"},
    "r": {"id": "minecraft:barrel", "properties": {"facing": "up"}},  # estoque
    # v1.2.78 — MOBÍLIA DE SERVIÇO DAS LATERAIS, agora em BLOCOS DO MOD.
    # Regra do usuário: o enfeite do mercado não é improviso com bloco vanilla
    # (barril, concreto e vidro viravam blockout — você não aceita). Engradado
    # de estoque, bebedouro e máquina de lavar nascem em
    # tools/gen_mobilia_mercado.py: geometria 3D e materiais 128.
    # TUDO ENCOSTADO NA PAREDE (x3/x23): os corredores (x4..x6 e x19..x22) e o
    # corredor do balcão (x13/x14) continuam livres — freguês circulando e Gago
    # alcançando a gôndola são o que o salão existe pra fazer.
    "E": {"id": "intoxicantes:engradado_mercado"},                # caixote de estoque
    # I/i são o MESMO bebedouro virado para cada parede: a peça tem FRENTE
    # (torneiras e copo apontam para dentro do salão), então o par maiúscula/
    # minúscula carrega a orientação. F = garrafão; G é exclusivo das paredes.
    "I": {"id": "intoxicantes:bebedouro_mercado", "properties": {"facing": "east"}},
    "i": {"id": "intoxicantes:bebedouro_mercado", "properties": {"facing": "west"}},
    "F": {"id": "intoxicantes:bebedouro_garrafao"},
    "D": {"id": "intoxicantes:lavadora_mercado", "properties": {"facing": "west"}},
    "k": {"id": "minecraft:lantern", "properties": {"hanging": "true"}},
    "B": {"id": "minecraft:black_stained_glass_pane", "properties": {
        "east": "true", "west": "true", "north": "false", "south": "false"}},
    # LETREIRO DO ESQUINÃO (v1.2.31; CONSERTADO na v1.2.51): DISPLAY DE
    # FACHADA estilo Satisfactory — a faixa atravessa a fachada (15 blocos da
    # linha y3, z18), MONTADA SOB O BEIRAL, 1 bloco à frente da fachada z17
    # (o mesmo padrão do v1: fachada z9, faixa z10). O PAINEL (J, x13) é o
    # ÚNICO com block entity (o texto renderiza 1×); os outros 14 são
    # EXTENSÃO (X) — a faixa preta contínua.
    "J": {"id": "intoxicantes:placa_esquinao", "properties": {
        "facing": "south", "parte": "painel", "nivel": "coluna",
        "lit": "true"}},
    "X": {"id": "intoxicantes:placa_esquinao", "properties": {
        "facing": "south", "parte": "extensao", "nivel": "coluna",
        "lit": "true"}},
    # quadro de PRECOS: placa de parede interna sobre a parede do fundo (menu
    # de bodega, atras do caixa) — facing=south, suporte = parede z0
    "P": {"id": "minecraft:oak_wall_sign", "properties": {"facing": "south"}},
    # painel de luz embutido no teto (luz de noite, sem precisar de suporte
    # pendurado — o teto E' o proprio bloco)
    "K": {"id": "minecraft:shroomlight"},
    # moldura da porta (cinza escuro): coluna solida ao lado dos vidros pra as
    # panes de vidro conectarem entre si SEM "vazar" pro vão da porta
    "m": {"id": "minecraft:gray_concrete"},
    # v1.2.36: PAINEL DE LED CRAFTÁVEL — o "display de ofertas" da fachada
    # (V): TV de tela plana de 3px montada à ESQUERDA da porta (z18, y2),
    # mostrando as ofertas (texto editável pela Central de Comando). NBT só
    # no CABEÇA (x8); os outros 2 são extensão (sem NBT = sem texto duplicado).
    "V": {"id": "intoxicantes:painel_led", "properties": {
        "facing": "south", "telas": "3", "lit": "true"}},
    # v1.2.51: O MINI DISPLAY "ABERTO · 24H" — a plaquinha de LED ao lado
    # DIREITO da porta (espelho do painel de ofertas). Nasce em (18, y1, 18) —
    # o lugar EXATO que a reforma do zelador planta ((+5,+5) do centro, y1):
    # o template ocupa o lugar antes e a reforma vira no-op (sem duplicado).
    # PainelLedBlock com NBT de fábrica no CABEÇA (M); o "·" renderiza como
    # "." na fonte LED (sem glifo próprio — o visual fica idêntico).
    "M": {"id": "intoxicantes:painel_led", "properties": {
        "facing": "south", "telas": "1", "lit": "true"}, "interativo": True},
    # v1.2.69: A PRATELEIRA DE MERCADO — a gôndola que VENDE (BaseEntityBlock):
    # 9 slots de item de verdade, reabastecida QUARTA-FEIRA (a semana do
    # CalendarioEsquinao). Nasce de frente pro corredor (facing=south, a
    # etiqueta olha pra porta) e com o estoque de PARTIDA do Gago no NBT de
    # bloco (padrão do mini display: NBT vai NA ENTRADA DE BLOCO).
    "z": {"id": "intoxicantes:prateleira_mercado", "properties": {
        "facing": "south"}, "interativo": True},
    ".": {"id": "minecraft:air"},
}

# ============================================================ REGIÕES (v1.2.25)
# O item 3 do TODO, fase 2: o PRÉDIO agora nasce "do lugar". Mesmo layout,
# mesmas fileiras, mesma anatomia — muda a PELE (paleta) por clima, no padrão
# das vilas vanilla: UM structure_set com 3 structures, cada uma presa à
# própria tag de bioma (o chunk sorteado só ergue a que o bioma aceitar).
# Override de char = str (só o id; propriedades herdadas, ex. slabs) ou dict
# (substitui o estado inteiro). O override de "L" (tronco) ficou órfão de
# propósito: o v2 não usa mais tronco (zelador destrói #logs).
REGIOES = {
    # esquina clássica: concreto branco/verde do SUL DISTRIBUIDORA original
    "classico": {},
    # SERTÃO: adobe de terracota laranja, piso e balcão de arenito lapidado —
    # a bodega de encruzilhada do interior
    "sertao": {
        "W": "minecraft:smooth_sandstone",
        "G": "minecraft:orange_terracotta",
        "Q": "minecraft:cut_sandstone",
        "y": "minecraft:light_gray_concrete",
        "s": "minecraft:smooth_sandstone_slab",
        "t": "minecraft:smooth_sandstone_slab",
        "m": "minecraft:gray_terracotta",
        # v1.2.78: a mobília virou bloco do mod (inox, madeira, laminado) —
        # não troca mais por arenito: é a mesma máquina em qualquer esquina.
    },
    # SERRA: pedra fria, paredes de pinho escuro, piso de smooth stone —
    # o mercadinho de serra com chão de freezer antigo
    "serra": {
        "W": "minecraft:stone_bricks",
        "G": "minecraft:spruce_planks",
        "Q": "minecraft:smooth_stone",
        "m": "minecraft:polished_deepslate",
        # v1.2.78: idem sertão — a mobília de serviço é a mesma nas três regiões.
    },
}

def paleta_da_regiao(regiao):
    """Paleta da região: a base com os overrides aplicados (ordem preservada —
    os índices de estado do NBT saem da ordem do dict)."""
    paleta = {}
    for ch, estado in PALETTE.items():
        override = REGIOES[regiao].get(ch)
        if override is None:
            paleta[ch] = estado
        elif isinstance(override, str):
            trocado = dict(estado)
            trocado["id"] = override
            paleta[ch] = trocado
        else:
            fundido = dict(estado)
            fundido.update(override)
            paleta[ch] = fundido
    return paleta


# v1.2.79 — respiro longitudinal: três colunas por ilha, com vão de 1 bloco
# em X, e fileiras em z6/z9, deixando corredores para a frente e o verso.
# Corredores laterais: x4/x5 e x21/x22; central: x11..x15.
# v1.2.78 — malha de iluminação do teto: passo de 3 blocos nos dois eixos.
# 7 colunas x 5 fileiras = 35 tubos. X evita as paredes (x3/x23) e o corredor
# central do balcão (x13/x14 cai entre colunas, não em cima delas).
LAMPADAS_X = (4, 7, 10, 13, 16, 19, 22)
LAMPADAS_Z = (2, 5, 8, 11, 14)

COLUNAS_ILHA = (6, 8, 10, 16, 18, 20)
# Cada bloco vende frente E fundo. Dois blocos de corredor separam fileiras
# (z7/z8), abrindo os slots traseiros. Mantém 24 BEs.
FILEIRAS_ILHA = (6, 9)


def secao_prateleira(x, y, z):
    """Cada BE ocupa duas seções consecutivas; índice real, não x antigo."""
    return COLUNAS_ILHA.index(x) * 8 + FILEIRAS_ILHA.index(z) * 4 + (y - 1) * 2


def estado_do_template(paleta, x, y, z):
    estado = paleta[ROWS[y][z][x]]
    if estado['id'] == 'intoxicantes:prateleira_mercado':
        return {**estado, 'properties': {**estado.get('properties', {}),
                'facing': 'north' if z == FILEIRAS_ILHA[0] else 'south'}}
    return estado


# ============================================================ TEMPLATE ROWS[y][z][x]
# z=17 é a FRENTE do prédio (fachada + porta x13); z=0 é o fundo. x=0..26.
# O CENTRO do lote é a célula (13,13) — TODA a anatomia do Java (descoberta,
# rebuild, reforms do zelador) se ancora nela. z18..z26 é a FRENTE: alpendre,
# calçada com faixa de pedestre, asfalto com vagas/PCD e meio-fio.
# y=5..7 é AR EXPLICITO sobre tudo (espaço seguro).
SIZE_X, SIZE_Y, SIZE_Z = 27, 8, 27


def _asfalto(pcd=False):
    """Fileira de piso do pátio (y0): asfalto com linhas de vaga 'y' nas
    colunas x1,5,9,15,19,23 (vagas de 3) e, se pcd, piso azul x16..18.
    A travessia (x12..14) fica SÓ na calçada (v1.2.68: o asfalto é puro)."""
    cel = ["A"] * 27
    cel[0] = cel[26] = "y"                      # borda do lote (meio-fio lateral)
    for x in (1, 5, 9, 15, 19, 23):             # linhas das vagas
        cel[x] = "y"
    if pcd:                                     # vaga PCD (x16..18)
        for x in (16, 17, 18):
            cel[x] = "a"
    return "".join(cel)


_W27 = "W" * 27
_G27 = "G" * 27
_DOT27 = "." * 27
_ASF = _asfalto(False)
_ASF_PCD = _asfalto(True)
_Q23 = "WW" + "Q" * 23 + "WW"                   # piso interno (x2..x24 sob o prédio)


def _interna():
    """Linha interna do prédio (z1..z16, y1/y2): colunas G em x2/x24, ar dentro."""
    l = ["."] * 27
    l[2] = l[24] = "G"
    return l


def _j(l, x0, x1, ch):
    for x in range(x0, x1 + 1):
        l[x] = ch
    return l


# v1.2.78 — MOBÍLIA LATERAL: os modelos customizados encostam nas paredes
# internas (x3 junto de x2; x23 junto de x24).
# O salão de x3..x23 tem 4 corredores: x4..x6 (oeste), x11..x14 (central, com o
# balcão cortando x8..12 no z12) e x19..x22 (leste) — a mobília NUNCA entra
# neles, só na linha da parede.
MOBILIA = [
    (3, 4, [(1, "E"), (2, "E")]),                  # oeste: pilha de 2 engradados
    (3, 10, [(1, "I"), (2, "F")]),                 # oeste: bebedouro + garrafão
    (23, 4, [(1, "E"), (2, "E"), (3, "E")]),       # leste: torre de 3 engradados
    (23, 10, [(1, "i"), (2, "F")]),                # leste: bebedouro + garrafão
    (23, 13, [(1, "D")]),                          # leste: máquina de lavar
]


def _mobilia(y):
    """Caractere de mobília por (z, x) na altura y — as paredes primeiro."""
    por_z = {}
    for parede, z, pilha in MOBILIA:
        for altura, ch in pilha:
            if altura == y:
                por_z.setdefault(z, {})[parede] = ch
    return por_z


def _monta_rows():
    R = []

    # ---------- y = 0 — piso: quartz interno, calçada, asfalto e meio-fio ----------
    y0 = [
        _W27,               # z0 (fundo — rodapé da parede)
        _Q23, _Q23, _Q23, _Q23, _Q23, _Q23, _Q23, _Q23,   # z1..z8 (piso interno)
        _Q23, _Q23, _Q23, _Q23, _Q23, _Q23, _Q23, _Q23,   # z9..z16 (z11 Gago, z16 posto)
        _W27,               # z17: soleira da fachada (porta x13 em cima)
        _W27,               # z18: alpendre (faixa LED/display montados à frente)
        _W27,               # z19: calçada (a reforma da faixa NÃO pinta: não é asfalto)
        _ASF,               # z20: pátio — vagas + postes + hidrante
        _ASF_PCD,           # z21: vaga PCD azul (x16..18)
        _ASF_PCD,           # z22 (células 8/18,22 SEM hidrante — o zelador recolhe)
        _ASF_PCD,           # z23
        _ASF,               # z24
        _ASF,               # z25
        "y" * 27,           # z26: meio-fio de saída
    ]
    R.append(y0)

    # ---------- y = 1 — paredes, geladeiras, gôndolas (base), balcão, pátio ----------
    y1 = []
    y1.append(_G27.replace("G", "G"))  # z0: parede do fundo (largura toda do prédio)
    for z in range(1, 17):
        l = _interna()
        if z == 1:
            _j(l, 9, 17, "B")                   # geladeiras de bebida (vidro escuro)
        elif z == 2:
            l[4] = "r"; l[21] = "r"; l[22] = "r"  # barris de estoque
        if z in FILEIRAS_ILHA:                          # v1.2.76: AS ILHAS DE GÔNDOLA
            # O salão é mobiliado SÓ com a gôndola que VENDE: as "prateleiras"
            # de quartzo/slab (laterais e as pontas do centro) saíram — eram
            # furniture mudo que parecia estoque e não vendia NADA (playtest).
            # Duas ilhas com 3 colunas, vãos em X e fileiras z6/z9.
            # Corredor de 2 blocos em z7/z8 dá acesso aos fundos; faces
            # externas compráveis pelos corredores z5 e z10.
            for cx in COLUNAS_ILHA:              # 3 colunas por ilha, com VAO
                l[cx] = "z"
        if z == 12:
            _j(l, 8, 12, "s")                   # BALCÃO (x8..12) — Gago atrás (z11)
            l[10] = "c"                         # v1.2.75: o CAIXA no centro (visado pro posto)
        for mx, mch in _mobilia(1).get(z, {}).items():   # v1.2.78: mobília das laterais
            l[mx] = mch
        y1.append("".join(l))
    # z17: FACHADA — colunas + vitrines + PORTA-GRADE (x13)
    fachada1 = ["."] * 27
    fachada1[2] = fachada1[24] = "G"
    _j(fachada1, 3, 12, "B"); _j(fachada1, 14, 23, "B")
    fachada1[12] = fachada1[14] = "m"   # v1.2.67: moldura no y1 também (v1: "mBBBBgBBBBm")
    fachada1[13] = "g"
    y1.append("".join(fachada1))
    # z18: alpendre — MINI DISPLAY "ABERTO · 24H" (x18; lugar exato da reforma)
    z18_1 = ["."] * 27
    z18_1[18] = "M"
    y1.append("".join(z18_1))
    # z19: travessia SÓ na calçada (x12..14, alinhada com a porta) + HIDRANTE
    # na calçada, ao lado da travessia (x11) + BASE dos DOIS postes nos CANTOS
    # do lote (x0/x26 — fora de toda vaga; vaga com poste no meio não estaciona).
    z19_1 = ["."] * 27
    _j(z19_1, 12, 14, "f")
    z19_1[11] = "H"                          # hidrante (o do zelador vira no-op)
    z19_1[0] = z19_1[26] = "N"               # postes — base
    y1.append("".join(z19_1))
    # z20..z25: pátio LIMPO (v1.2.68) — asfalto puro com vagas demarcadas.
    # Faixa de pedestre no MEIO do estacionamento não existe na rua real: a
    # travessia fica na calçada, postes no canto do lote, hidrante na calçada.
    # Zero obstáculos dentro das vagas.
    for z in range(20, 26):
        y1.append(_DOT27)
    y1.append(_DOT27)       # z26
    R.append(y1)

    # ---------- y = 2 — corpo das gôndolas, tampo do balcão, porta superior ----------
    y2 = []
    y2.append(_G27)         # z0
    for z in range(1, 17):
        l = _interna()
        if z == 1:
            _j(l, 9, 17, "B")                   # geladeiras (nível de cima)
        if z in FILEIRAS_ILHA:                          # v1.2.76: o CORPO das ilhas
            for cx in COLUNAS_ILHA:              # idem em y2 (etiqueta/3 tábuas)
                l[cx] = "z"
        if z == 12:
            _j(l, 8, 12, "t")                   # tampo do balcão (slab top)
            l[10] = "u"                         # v1.2.75: a registradora no centro (tampo 2px)
        for mx, mch in _mobilia(2).get(z, {}).items():   # v1.2.78: mobília (topo das peças)
            l[mx] = mch
        y2.append("".join(l))
    # z17: FACHADA — moldura m + porta superior
    fachada2 = ["."] * 27
    fachada2[2] = fachada2[24] = "G"
    _j(fachada2, 3, 12, "B"); _j(fachada2, 14, 23, "B")
    fachada2[12] = fachada2[14] = "m"
    fachada2[13] = "d"
    y2.append("".join(fachada2))
    # z18: PAINEL DE OFERTAS (x8..10, cabeça x8 — o alvo (12,2,18) fica AR)
    z18_2 = ["."] * 27
    _j(z18_2, 8, 10, "V")
    y2.append("".join(z18_2))
    # z19..z26
    z19_2 = ["."] * 27
    z19_2[0] = z19_2[26] = "C"              # postes — corpo
    y2.append("".join(z19_2))
    for z in range(20, 26):
        y2.append(_DOT27)   # pátio (sem postes desde a v1.2.68)
    y2.append(_DOT27)       # z26
    R.append(y2)

    # ---------- y = 3 — fileiras de LÂMPADA LED, lanterna, viga + FAIXA LED ----------
    # v1.2.67: as linhas internas GANHAM AS PAREDES de volta (x2/x24 = G). Sem
    # isso sobrava um vão corrido entre o topo da parede verde (y2) e o teto
    # (y4) — os "buracos pelas paredes" do report.
    y3 = []
    y3.append(".." + "W" * 23 + "..")  # z0: viga do fundo (suporte do quadro P)
    for z in range(1, 17):
        l = _interna()
        if z == 1:
            l[12] = "P"                          # QUADRO DE PREÇOS (presa na parede z0)
        # v1.2.78: as lâmpadas do teto eram TRÊS fileiras CORRIDAS de x5 a x21
        # (17 coladas em cada uma, 51 no total) — uma parede de tubo, não uma
        # iluminação. Agora é uma malha com folga de 3 blocos nos dois eixos:
        # a peça existe para iluminar, e de perto a fileira cheia escondia a
        # gôndola e o balcão em vez de destacar.
        if z in LAMPADAS_Z:
            for lx in LAMPADAS_X:
                l[lx] = "L"
        if z == 12:
            l[10] = "k"                          # lanterna sobre o balcão (teto acima)
        for mx, mch in _mobilia(3).get(z, {}).items():   # v1.2.78: 3o engradado da pilha
            l[mx] = mch
        y3.append("".join(l))
    y3.append(".." + "W" * 23 + "..")  # z17: viga da fachada (topo, sob o telhado)
    # z18: FAIXA LED — 7X + J (x13) + 7X = 15 blocos, sob o beiral
    z18_3 = ["."] * 27
    _j(z18_3, 6, 12, "X"); _j(z18_3, 14, 20, "X")
    z18_3[13] = "J"
    y3.append("".join(z18_3))
    z19_3 = ["."] * 27
    z19_3[0] = z19_3[26] = "O"              # postes — topo (luminária)
    y3.append("".join(z19_3))
    for z in range(20, 26):
        y3.append(_DOT27)   # pátio (sem postes desde a v1.2.68)
    y3.append(_DOT27)       # z26
    R.append(y3)

    # ---------- y = 4 — telhado branco plano (z0..z18, beiral sobre a faixa LED) ----------
    y4 = []
    for z in range(0, 19):
        l = ["."] * 27
        _j(l, 2, 24, "W")
        if z == 10:
            _j(l, 11, 15, "K")                   # shroomlights no teto (luz de noite)
        y4.append("".join(l))
    for z in range(19, 27):
        y4.append(_DOT27)
    R.append(y4)

    # ---------- y = 5..7 — AR EXPLICITO sobre tudo (prédio + lote): mata árvore
    # invasora, desalaga o box e garante a "bola de cristal" limpa do esquinão
    # (o AR é COLOCADO, não pulado — ver build_nbt)
    for _ in range(3):
        R.append([_DOT27 for _ in range(SIZE_Z)])

    # saneamento: TODA linha tem exatamente SIZE_X chars (o erro de contagem
    # manual vira SystemExit aqui, não um template torto no jogo)
    for y, camada in enumerate(R):
        for z, linha in enumerate(camada):
            assert len(linha) == SIZE_X, f"linha y={y} z={z} tem {len(linha)} chars"
    return R


ROWS = _monta_rows()


# O Gago, ATRAS do balcão (célula BALCAO_LOCAL: centro+(-3,0,-2) = (10,1,11)),
# de frente pra porta (+z/sul) — a anatomia do v1 (atras do balcão) mantida.
GAGO_BLOCK_POS = [10, 1, 11]
GAGO_POS = [10.5, 1.0, 11.5]  # piso do balcão (a placa nova não interfere no interior)


# ============================================================ NBT writer (big-endian, gzip)
def _payload_string(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def _payload_int(v):
    return struct.pack(">i", v)


def _int_payloads(values):
    """Payloads de TAG_Int pra montar uma TAG_List de ints (pos, size, blockPos...)."""
    return [_payload_int(v) for v in values]


def _int_list(name, values):
    return _tag_list(name, 3, _int_payloads(values))


def build_nbt(paleta):
    # Fileiras separadas em z6/z9: os quatro lados de venda têm corredor.
    paleta = dict(paleta)
    paleta["prateleira_norte"] = {**paleta["z"],
        "properties": {**paleta["z"].get("properties", {}), "facing": "north"}}
    estados = list(paleta.values())  # ordem determinística do dict
    estado_para_indice = {ch: i for i, ch in enumerate(paleta)}

    # ---- palette (lista de compounds; elementos de TAG_List são SÓ payload)
    palette_payload = []
    for estado in estados:
        body = _tag_string("id", estado["id"])
        if "properties" in estado:
            props = b""
            for k, v in estado["properties"].items():
                props += _tag_string(k, v)
            body += _tag_compound("properties", props)
        # NAO escrever nbt aqui: o vanilla nunca guarda nbt na paleta (0/1511
        # templates) e o StructureTemplate IGNORA — o texto da placa ia a perder.
        palette_payload.append(_compound_payload(body))

    # ---- blocks (lista de compounds {pos, state, nbt?}).
    # v1.2.18: o AR TAMBEM É COLOCADO — o template é uma "bola de cristal":
    # derruba árvore/cana invasora, seca água rasa do box e garante que um
    # prédio da vila não nasça colado dentro da loja (report: "nasceu engolida
    # por uma vila"). nbt de tile entity vai NA ENTRADA DE BLOCO — padrão do
    # vanilla (5597 ocorrências, ex. baús de shipwreck).
    blocks_payload = []
    for y in range(SIZE_Y):
        for z in range(SIZE_Z):
            linha = ROWS[y][z]
            assert len(linha) == SIZE_X, f"linha y={y} z={z} tem {len(linha)} chars"
            for x in range(SIZE_X):
                ch = linha[x]
                if ch not in PALETTE:
                    raise SystemExit(f"caractere '{ch}' (y={y} z={z} x={x}) nao esta na PALETTE")
                body = _int_list("pos", (x, y, z))
                chave_estado = "prateleira_norte" if ch == "z" and z == FILEIRAS_ILHA[0] else ch
                body += _tag_int("state", estado_para_indice[chave_estado])
                if ch == "J":
                    # o LETREIRO CUSTOM: nome em 1 linha esticada + vínculo
                    # com o mercado (painel central da faixa, x13)
                    body += _tag_compound("nbt", _placa_nbt())
                elif ch == "V" and x == 8:
                    # NBT SÓ no painel-CABEÇA (x8, a ponta oeste da linha z18).
                    # O template 1.2.36–39 gravava NBT nos 5 blocos → 5 block
                    # entities com texto próprio = 5 renderers sobrepostos.
                    # Extensão não tem texto: quem manda na linha é o cabeça.
                    body += _tag_compound("nbt", _painel_nbt())
                elif ch == "M" and x == 18:
                    # o mini display "ABERTO · 24H" (único, x18 — o lugar
                    # exato que a reforma do zelador planta: no-op garantido)
                    body += _tag_compound("nbt", _mini_display_nbt())
                elif ch == "g":
                    # a porta-grade nasce com o dono (a entidade porta_grade
                    # é colocada junto do bloco pela estrutura)
                    body += _tag_compound("nbt", _porta_grade_nbt())
                elif ch == "z":
                    # Ambos os lados têm passagem: 18 slots em CADA bloco,
                    # frente/fundo com duas seções consecutivas do catálogo.
                    secao = secao_prateleira(x, y, z)
                    body += _tag_compound("nbt", _prateleira_nbt(secao, comFundo=True))
                elif ch == "P":
                    # o QUADRO DE PREÇOS interno (a "parede" é a fonte visível;
                    # a tela custom detalha compra/venda/estoque em tempo real)
                    body += _tag_compound("nbt", _sign_nbt(SINAL_PRECOS))
                blocks_payload.append(_compound_payload(body))

    # ---- entities
    entities_payload = [_gago_entity()]

    root_body = (
        _int_list("size", (SIZE_X, SIZE_Y, SIZE_Z))
        + _tag_list("entities", 10, entities_payload)
        + _tag_list("blocks", 10, blocks_payload)
        + _tag_list("palette", 10, palette_payload)
        + _tag_int("DataVersion", DATA_VERSION)
    )
    return gzip.compress(_tag_compound("", root_body))


def _tag_string(name, value):
    return b"\x08" + _payload_string(name) + _payload_string(value)


def _tag_byte(name, value):
    return b"\x01" + _payload_string(name) + struct.pack(">b", value)


def _tag_short(name, value):
    return b"\x02" + _payload_string(name) + struct.pack(">h", value)


def _tag_int(name, value):
    return b"\x03" + _payload_string(name) + struct.pack(">i", value)


def _tag_float(name, value):
    return b"\x05" + _payload_string(name) + struct.pack(">f", value)


def _tag_int_array(name, values):
    out = b"\x0B" + _payload_string(name) + struct.pack(">i", len(values))
    for v in values:
        out += struct.pack(">i", v)
    return out


def _tag_list(name, tag_type, payloads):
    out = b"\x09" + _payload_string(name) + struct.pack(">B", tag_type)
    out += struct.pack(">i", len(payloads))
    return out + b"".join(payloads)


def _tag_compound(name, body):
    return b"\x0A" + _payload_string(name) + body + b"\x00"


def _compound_payload(body):
    """Elemento de TAG_List de compounds: SO o payload, COM o terminador 0x00
    (mas sem byte de tipo e sem nome — igual ao igloo/top.nbt)."""
    return body + b"\x00"


def _double_list(name, values):
    return _tag_list(name, 6, [struct.pack(">d", v) for v in values])


def _float_list(name, values):
    return _tag_list(name, 5, [struct.pack(">f", v) for v in values])


# ---- textos das placas (quadro de preços interno) + nbt da placa custom
# quadro de preços: os valores REAIS mudam por dia (cotação da rua) e por nível
# de fidelidade — a tela custom do cardápio é a fonte verdadeira; esta placa é
# a vitrine "de papel" da loja, com os preços base visíveis de dentro.
SINAL_PRECOS = {
    "front_text": {"mensagens": ["- CARDÁPIO -", "Cerveja .. R$15", "Colheita 8x: R$6-8", "Fidelidade: -15%"],
                   "cor": "black", "glow": 0},
    "back_text": {"mensagens": ["", "", "", ""], "cor": "black", "glow": 0},
    "is_waxed": 1,
}


def _sign_nbt(sign=None):
    if sign is None:
        sign = SINAL_PRECOS

    # formato REAL do vanilla (igloo/bottom.nbt): messages e filtered_messages
    # sao TAG_List de 4 TAG_Compound; cada linha = {text: str, color?: str}.
    # O texto NAO e' string JSON — 'color' e' campo IRMAO de 'text'
    # (igloo: \x08\x00\x04text\x00\x05<----\x08\x00\x05color\x00\x05black).
    # A mensagem crua ({"text":...} como valor de text) renderizaria o JSON literal!
    def linha(txt, cor):
        return _compound_payload(_tag_string("text", txt)
                                 + _tag_string("color", cor))

    def texto(lado):
        mensagens = lado["mensagens"]
        cor = lado.get("cor", "black")
        return _tag_list("messages", 10, [linha(m, cor) for m in mensagens]) \
            + _tag_list("filtered_messages", 10, [linha(m, cor) for m in mensagens]) \
            + _tag_byte("has_glowing_text", lado.get("glow", 0))

    body = _tag_compound("front_text", texto(sign["front_text"]))
    body += _tag_compound("back_text", texto(sign["back_text"]))
    body += _tag_byte("is_waxed", sign["is_waxed"])
    return body


def _placa_nbt():
    """NBT da PlacaEsquinaoBlockEntity gravado pelo template (SÓ no painel
    central): o nome da loja em 1 LINHA que o renderer estica pela largura da
    fachada + vínculo com o mercado + a anatomia atual (versao/largura — o
    zelador não re-migra). O 'id' é incluído como os templates do vanilla
    fazem para block entities."""
    body = _tag_string("id", "intoxicantes:placa_esquinao")
    # v1.2.31: o MESMO LINHAS_PADRAO do código + anatomia do display de fachada.
    body += _tag_string("linha0", "MERCADO ESQUINÃO")
    body += _tag_int("versao", 3)
    # v1.2.61: largura = a FAIXA da fachada (LARGURA_FACHADA do Java = 15:
    # 7X + J + 7X), NÃO o lote (27). Gravar 27 faria o zelador semear faixa
    # além do prédio na primeira autocura.
    body += _tag_int("largura", 15)
    body += _tag_byte("mercado", 1)
    body += _tag_byte("nova", 1)
    return body


def _painel_nbt():
    """NBT do PainelLedBlockEntity (v1.2.36) — o display de OFERTAS montado na
    fachada, à esquerda da porta. 2 linhas, tela esticada."""
    body = _tag_string("id", "intoxicantes:painel_led")
    body += _tag_string("linha0", "OFERTAS DO DIA")
    body += _tag_string("linha1", "PROMOCOES!")
    body += _tag_int("cor", 0x39FF6E)
    body += _tag_int("brilho", 15)
    body += _tag_int("modo", 0)
    return body


def _mini_display_nbt():
    """NBT do MINI DISPLAY (v1.2.51) — o ABERTO · 24H fixo ao lado direito
    da porta (o mercado é 24h: o letreiro NUNCA mostra "FECHADO"; o ciclo
    de status saiu do letreiro na v1.2.40 e agora mora aqui). O "·" não tem
    glifo na fonte LED e cai no fallback do ponto (visual idêntico)."""
    body = _tag_string("id", "intoxicantes:painel_led")
    body += _tag_string("linha0", "ABERTO")
    body += _tag_string("linha1", "· 24H ·")
    body += _tag_int("cor", 0x39FF6E)
    body += _tag_int("brilho", 15)
    body += _tag_int("modo", 0)
    return body


def _porta_grade_nbt():
    """NBT da PORTA-GRADE (v1.2.51): entidade-dono do bloco colocada pela
    estrutura (a porta é o único bloco interativo do template — o beacon do
    vanilla não suporta id custom). O Java aceita a entidade se ela já
    existir por código (compatibilidade total, mesmo padrão do Gago)."""
    body = _tag_string("id", "intoxicantes:porta_grade")
    body += _tag_byte("KeepPacked", 0)
    return body


# Estoque de partida lido do TradeCatalog: oito bebidas e comidas/ingredientes,
# distribuídos em seções de nove slots, com os mesmos preços e doses do Java.
# Formato dos itens = o codec REAL do 26.3 (ContainerHelper
# saveAllItems → ItemStackWithSlot: {Slot, id, count} — decifrado no jar).
def catalogo_prateleira():
    """Lê ofertas literais da fonte Java; não mantém outra tabela de preços."""
    path = os.path.join(ROOT, "src", "main", "java", "com", "intoxicantes", "TradeCatalog.java")
    with open(path, encoding="utf-8") as f:
        fonte = f.read()
    base = fonte.split("static List<Entry> gago(int tier) {", 1)[1].split("menu.addAll(alimentos());", 1)[0]
    comida = fonte.split("private static List<Entry> alimentos() {", 1)[1].split("private static List<Entry> produtosPrateleira()", 1)[0]
    padrao = r'new Entry\("([^\"]+)",\s*(IntoxicantesMod|Items)\.\w+,\s*(\d+),\s*(\d+),\s*(\d+),\s*0,\s*false\)'
    def ofertas(texto):
        return [(id_, ("intoxicantes:" if ns == "IntoxicantesMod" else "minecraft:") + id_,
                 int(dose), int(preco), int(stock))
                for id_, ns, dose, preco, stock in re.findall(padrao, texto)]
    alimentos = ofertas(comida)
    bebidas = [e for e in ofertas(base) if not e[0].endswith("_vazia")
               and not e[0].startswith("semente_") and e[0] not in ("lampada_uv", "glass_bottle")]
    if len(bebidas) != 8 or not alimentos:
        raise ValueError("Catálogo canônico do Gago não pôde ser lido; não gerar estoque divergente")
    return bebidas + alimentos, alimentos


def agenda_prateleira(secao):
    produtos, alimentos = catalogo_prateleira()
    total = (len(produtos) + 8) // 9
    inicio = (secao % total) * 9
    agenda = produtos[inicio:inicio + 9]
    agenda += alimentos[:9 - len(agenda)]
    return secao % total, agenda


def _maximo_pilha(item, id_):
    return 1 if item in {"minecraft:mushroom_stew", "minecraft:beetroot_soup",
                         "minecraft:rabbit_stew", "minecraft:cake", "minecraft:milk_bucket"} else (
           16 if item.endswith("_egg") or item == "minecraft:egg" or item == "minecraft:honey_bottle"
           or (item.startswith("intoxicantes:") and id_ in {
               "cerveja", "vinho", "hidromel", "cachaca", "rum", "suco_detox", "agua_de_coco", "cha_lupulo"}) else 64)


def _prateleira_nbt(secao=0, comFundo=False):
    """NBT da PRATELEIRA DE MERCADO (v1.2.69): a gôndola nasce ABASTECIDA —
    o estoque de partida do Gago já nas estantes (igual baú de shipwreck:
    NBT de tile entity na entrada de bloco). A hierarquia é a da BE:
    prateleira → itens → Items (o ContainerHelper lê 'Items' DENTRO do
    child 'itens'; codec do 26.3: Slot + id + count). 'precos' é a
    etiqueta de cada slot.
    v1.2.75: comFundo=True completa os slots 9..17 (o lado de TRÁS da
    ilha, a seção ALTERNADA — o corredor oposto compra outra mercadoria)."""
    secao, agenda = agenda_prateleira(secao)
    agendas = [(0, agenda)]
    if comFundo:
        _secao_fundo, agenda_fundo = agenda_prateleira(secao + 1)
        agendas.append((9, agenda_fundo))
    itens = []
    precos_payload = b""
    doses_payload = b""
    reposicao_payload = b""
    for deslocamento, entradas in agendas:
        for slot, (id_, item, dose, preco, stock) in enumerate(entradas):
            count = min(_maximo_pilha(item, id_), stock * dose)
            entry = _tag_int("Slot", deslocamento + slot) + _tag_string("id", item) + _tag_int("count", count)
            itens.append(_compound_payload(entry))
            precos_payload += _tag_int(str(deslocamento + slot), preco)
            doses_payload += _tag_int(str(deslocamento + slot), dose)
            reposicao_payload += _tag_string(str(deslocamento + slot), id_)
    body = _tag_string("id", "intoxicantes:prateleira_mercado")
    child = _tag_compound("itens", _tag_list("Items", 10, itens))
    child += _tag_int("secao", secao)
    child += _tag_compound("precos", precos_payload)
    child += _tag_compound("doses", doses_payload)
    child += _tag_compound("reposicao", reposicao_payload)
    body += _tag_compound("prateleira", child)
    return body


def _gago_entity():
    nbt = (
        _tag_string("id", "intoxicantes:gago")
        + _double_list("Pos", GAGO_POS)
        + _double_list("Motion", [0.0, 0.0, 0.0])
        + _float_list("Rotation", [0.0, 0.0])
        + _tag_int_array("UUID", [305419896, 1871213665, -1463642571, 121865398])
        + _tag_short("Air", 300)
        + _tag_short("Fire", 0)
        + _tag_byte("Invulnerable", 0)  # NUNCA vulneravel=1: com 1 o escudo de
        # veneno/dano nao remove e o Gago ficaria imortal (a 12 "nao dava dano")
        + _tag_byte("OnGround", 1)
        + _tag_byte("PersistenceRequired", 1)
        + _tag_int("PortalCooldown", 0)
        + _tag_float("fall_distance", 0.0)
    )
    body = _int_list("blockPos", GAGO_BLOCK_POS)
    body += _double_list("pos", GAGO_POS)
    body += _tag_compound("nbt", nbt)
    return _compound_payload(body)


# ============================================================ JSONS de worldgen
def write_json(path, obj):
    full = ROOT + path.replace("/", os.sep)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"  {path}")


def main():
    # ============ v1.2.25: MERCADOS REGIONAIS (item 3 do TODO, fase 2) ============
    # Padrão das vilas vanilla: UM structure_set espalha; CADA estrutura tem a
    # própria tag de bioma (disjuntas — o bioma decide a pele) e o próprio
    # template pool. A espiral do mod localiza por chave e grava a região no
    # .dat — o /gagomarket rebuild e o Zelador já sabem qual pele erguer.
    for regiao in ("classico", "sertao", "serra"):
        paleta = paleta_da_regiao(regiao)
        sufixo = "" if regiao == "classico" else f"_{regiao}"

        # ---- template pool: unico piece, rotação aleatória do jigsaw
        # processors INLINE (como os pools vanilla): a referencia "minecraft:none" NAO existe
        # no 26.3 e quebra o registry inteiro ("Unbound values in processor_list")
        write_json(
            f"src/main/resources/data/intoxicantes/worldgen/template_pool/mercado_gago{sufixo}/start.json", {
            "fallback": "minecraft:empty",
            "elements": [{
                "weight": 1,
                "element": {
                    "projection": "rigid",
                    "element_type": "minecraft:legacy_single_pool_element",
                    "location": f"intoxicantes:mercado_gago{sufixo}",
                    "processors": {"processors": []},
                },
            }],
        })

        # ---- tags de bioma DISJUNTAS: o bioma decide a pele. Clássico é o
        # "clima neutro" (plains/forest/selva/brejo); sertão é o quente;
        # serra é o frio. Nenhum bioma em duas tags — um bioma, um mercado.
        tags_bioma = {
            "classico": [
                "minecraft:plains", "minecraft:sunflower_plains", "minecraft:forest",
                "minecraft:birch_forest", "minecraft:old_growth_birch_forest",
                "minecraft:dark_forest", "minecraft:flower_forest", "minecraft:meadow",
                "minecraft:cherry_grove", "minecraft:jungle", "minecraft:sparse_jungle",
                "minecraft:bamboo_jungle", "minecraft:swamp", "minecraft:mangrove_swamp",
                "minecraft:mushroom_fields", "minecraft:pale_garden", "minecraft:river",
                "minecraft:beach", "minecraft:savanna", "minecraft:savanna_plateau",
                "minecraft:windswept_savanna",
            ],
            "sertao": [
                "minecraft:desert", "minecraft:badlands", "minecraft:wooded_badlands",
                "minecraft:eroded_badlands", "minecraft:stony_shore",
            ],
            "serra": [
                "minecraft:taiga", "minecraft:snowy_taiga", "minecraft:snowy_plains",
                "minecraft:snowy_beach", "minecraft:ice_spikes", "minecraft:grove",
                "minecraft:snowy_slopes", "minecraft:jagged_peaks", "minecraft:frozen_peaks",
                "minecraft:stony_peaks", "minecraft:windswept_hills",
                "minecraft:windswept_forest", "minecraft:windswept_gravelly_hills",
            ],
        }
        write_json(
            f"src/main/resources/data/intoxicantes/tags/worldgen/biome/has_structure/mercado_esquinao{sufixo}.json", {
            "replace": False,
            "values": tags_bioma[regiao],
        })

        # ---- structure: jigsaw em superficie, ATERRAMENTO EM CAIXA (beard_box,
        #      v1.2.18): o terreno sob TODO o footprint (prédio + lote) é preenchido
        #      sólido — a loja não nasce mais no precipício, sobre buraco ou com a
        #      fundação exposta na encosta. O ar explícito do template cuida do
        #      ACIMA (água/árvore/vila colada).
        write_json(
            f"src/main/resources/data/intoxicantes/worldgen/structure/mercado_gago{sufixo}.json", {
            "type": "minecraft:jigsaw",
            "biomes": f"#intoxicantes:has_structure/mercado_esquinao{sufixo}",
            "spawn_overrides": {},
            "step": "surface_structures",
            "terrain_adaptation": "beard_box",
            "start_pool": f"intoxicantes:mercado_gago{sufixo}/start",
            "size": 1,
            "start_height": {"absolute": 0},
            "project_start_to_heightmap": "WORLD_SURFACE_WG",
            "max_distance_from_center": 80,
            "use_expansion_hack": True,
        })

        # ---- NBT da estrutura (paleta da região, mesmo layout)
        nbt_path = (ROOT
                    + f"src/main/resources/data/intoxicantes/structure/mercado_gago{sufixo}.nbt")
        os.makedirs(os.path.dirname(nbt_path), exist_ok=True)
        with open(nbt_path, "wb") as f:
            f.write(build_nbt(paleta))
        print(f"  src/main/resources/data/intoxicantes/structure/mercado_gago{sufixo}.nbt")

        # ---- sanity: le de volta e confere
        with open(nbt_path, "rb") as f:
            blob = f.read()
        assert blob[:2] == b"\x1f\x8b", "NBT nao esta gzip!"
        print(f"OK [{regiao}] — template {SIZE_X}x{SIZE_Y}x{SIZE_Z} "
              f"({sum(len(linha) for row_rows in ROWS for linha in row_rows)} blocos, "
              f"+ Gago, {len(paleta)} estados na paleta)")

    # ---- structure set: raro, estilo igloo (spacing 40 / separation 22).
    # As 3 estruturas dividem a MESMA distribuição: o random_spread sorteia a
    # célula, e a PRIMEIRA estrutura cuja tag de bioma aceita o terreno ergue.
    write_json("src/main/resources/data/intoxicantes/worldgen/structure_set/mercado_gago.json", {
        "structures": [
            {"structure": "intoxicantes:mercado_gago", "weight": 1},
            {"structure": "intoxicantes:mercado_gago_sertao", "weight": 1},
            {"structure": "intoxicantes:mercado_gago_serra", "weight": 1},
        ],
        "placement": {
            "type": "minecraft:random_spread",
            "spacing": 40,
            "separation": 22,
            "salt": 918273645,
        },
    })

    # ---- receita do opio (2 papoulas -> 1 opio): o loot da papoula ja cobre, mas
    #      quem cultiva em fazenda merece renda fixa
    write_json("src/main/resources/data/intoxicantes/recipe/opio.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "group": "intoxicantes",
        "pattern": ["P", "P"],
        "key": {"P": "minecraft:poppy"},
        "result": {"id": "intoxicantes:opio", "count": 1},
    })


if __name__ == "__main__":
    main()
