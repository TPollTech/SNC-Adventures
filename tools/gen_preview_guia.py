# -*- coding: utf-8 -*-
"""
PRÉVIA COMPLETA E NAVEGÁVEL DO GUIA DO SNC ADVENTURES.

Gera preview/guia/previa-guia.html: site interativo que replica a GUI planejada
(menu -> categoria -> entrada, com Voltar/Índice/ESC), com TODAS as categorias.
Ícones = texturas REAIS do mod (assets/.../textures, base64); insumos vanilla
das grades (polvora, ferro...) vêm do jar do Minecraft 26.3 no cache do Loom.
Receitas 3x3 são LIDAS dos JSONs de recipe/; tempos, cadeias e efeitos vêm de
ProcessosBebida e do lang do jogo. Nada inventado: o que não existe no mod
não ganha receita nem página.

Uso:  python tools/gen_preview_guia.py
"""

import base64
import io
import json
from pathlib import Path

from PIL import Image, ImageDraw

RAIZ = Path(__file__).resolve().parents[1]
TEXTURAS = RAIZ / "src/main/resources/assets/intoxicantes/textures"
RECIPES = RAIZ / "src/main/resources/data/intoxicantes/recipe"
LANG = json.load(open(RAIZ / "src/main/resources/assets/intoxicantes/lang/pt_br.json", encoding="utf-8"))
SAIDA = RAIZ / "preview/guia/previa-guia.html"
ASSETS = RAIZ / "preview/guia/assets"
ASSETS.mkdir(parents=True, exist_ok=True)

# ------------------------------------------------------------- texturas

_cache = {}


def _quadrada(im):
    lado = max(im.size)
    out = Image.new("RGBA", (lado, lado), (0, 0, 0, 0))
    out.paste(im, ((lado - im.width) // 2, (lado - im.height) // 2))
    return out


def _icone_guia():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    CAPA, CAPA_ESC, CAPA_CLA = (30, 107, 56, 255), (8, 61, 30, 255), (46, 138, 74, 255)
    PAPEL, PAPEL_SOM = (241, 228, 195, 255), (216, 199, 154, 255)
    OURO, OURO_ESC = (200, 151, 30, 255), (138, 102, 18, 255)
    for y in range(3, 14):
        for x in range(12, 14):
            d.point((x, y), fill=PAPEL_SOM if x == 13 else PAPEL)
    for y in range(2, 14):
        for x in range(1, 12):
            d.point((x, y), fill=CAPA)
    for x in range(1, 12):
        d.point((x, 2), fill=CAPA_ESC); d.point((x, 13), fill=CAPA_ESC)
    for y in range(2, 14):
        d.point((1, y), fill=CAPA_ESC); d.point((11, y), fill=CAPA_ESC)
    for x in range(2, 10):
        d.point((x, 3), fill=CAPA_CLA)
    for y in range(2, 14):
        d.point((3, y), fill=CAPA_ESC)
    for y in range(5, 11):
        d.point((6, y), fill=OURO)
    d.point((6, 5), fill=OURO_ESC); d.point((6, 10), fill=OURO_ESC)
    for x in range(8, 11):
        d.point((x, 7), fill=OURO); d.point((x, 8), fill=OURO)
    d.point((8, 6), fill=OURO_ESC); d.point((10, 6), fill=OURO_ESC)
    d.point((8, 9), fill=OURO_ESC); d.point((10, 9), fill=OURO_ESC)
    return im


def _icone_crop_ripened(nome):
    p1 = TEXTURAS / "block" / f"{nome}_stage4_ripe.png"
    p2 = TEXTURAS / "block" / f"{nome}_stage4.png"
    return Image.open(p1 if p1.exists() else p2).convert("RGBA")


# blocos cujo item não tem PNG próprio: usam a textura da base
_FACHADAS = {
    "item/spruce_slab": "block/spruce_planks",
    "item/birch_slab": "block/birch_planks",
    "item/smooth_stone_slab": "block/smooth_stone",
    "item/stone_button": "block/stone",
    "item/stripped_dark_oak_log": "block/stripped_dark_oak_log",
    "item/stripped_spruce_log": "block/stripped_spruce_log",
    "item/stripped_birch_log": "block/stripped_birch_log",
    "item/barrel": "block/barrel_side",
    "item/water_bucket": "item/bucket",
    "item/iron_trapdoor": "block/iron_trapdoor",
    "item/black_wool": "block/black_wool",
    "item/white_stained_glass": "block/white_stained_glass",
    "item/glass": "block/glass",
    "item/copper_ingot": "item/copper_ingot",
    "item/glowstone_dust": "item/glowstone_dust",
    "item/furnace": "block/furnace_front",
    "item/furnace_front": "block/furnace_front",
    "item/furnace_side": "block/furnace_side",
}


def _icone_vanilla(item_id):
    """Extrai a textura vanilla do jar 26.3; tenta item/, block/ e fachadas."""
    import subprocess
    jar = Path.home() / ".gradle/caches/fabric-loom/26.3/minecraft-client.jar"
    destino = ASSETS / f"mc_{item_id.replace('/', '_')}.png"
    candidatos = [item_id, item_id.replace("item/", "block/", 1), _FACHADAS.get(item_id)]
    if not destino.exists():
        for caminho in [c for c in candidatos if c]:
            r = subprocess.run(["unzip", "-p", str(jar), f"assets/minecraft/textures/{caminho}.png"],
                               stdout=subprocess.PIPE)
            if r.returncode == 0 and r.stdout:
                destino.write_bytes(r.stdout)
                break
        else:
            raise FileNotFoundError(f"textura vanilla não achada: {item_id}")
    return Image.open(destino).convert("RGBA")


# blocos do mod cuja textura não tem o mesmo nome do ID
_ALIASES_MOD = {
    "dorna_bebida": "dorna",
    "painel_led": "placa_esquinao_tela",
    "uva_plant": "uva_stage4_ripe",
    "lupulo_plant": "lupulo_stage4_ripe",
    "cafe_plant": "cafe_stage4_ripe",
    "maconha_plant": "maconha_stage4_ripe",
    "papoula_plant": "papoula_stage4_ripe",
    "cevada_plant": "cevada_stage4",
}


def _carregar(nome):
    if nome == "guia":
        return _icone_guia()
    if nome.startswith("mc:"):
        return _icone_vanilla(nome[3:])
    nome = _ALIASES_MOD.get(nome, nome)
    # v1.2.64 — bebidas 3D: o sprite 2D textures/item/{nome}.png foi removido;
    # o ícone do guia é o RÓTULO da garrafa (tile 64px em (0,64) do atlas 128
    # do gen_garrafas.py — a mesma arte que o jogador vê engarrafada).
    atlas = TEXTURAS / "item" / "garrafas" / f"{nome}.png"
    if atlas.exists():
        return Image.open(atlas).convert("RGBA").crop((0, 64, 64, 128))
    p_item = TEXTURAS / "item" / f"{nome}.png"
    p_block = TEXTURAS / "block" / f"{nome}.png"
    if p_item.exists():
        return Image.open(p_item).convert("RGBA")
    if p_block.exists():
        return Image.open(p_block).convert("RGBA")
    if f"{nome}_stage4_ripe.png" in [p.name for p in (TEXTURAS / "block").glob(f"{nome}_stage4*")]:
        return _icone_crop_ripened(nome)
    raise FileNotFoundError(f"textura não encontrada: {nome}")


def _uri(nome, escala):
    chave = (nome, escala)
    if chave in _cache:
        return _cache[chave]
    im = _quadrada(_carregar(nome)).resize((16 * escala, 16 * escala), Image.NEAREST)
    buf = io.BytesIO()
    im.save(buf, "PNG")
    _cache[chave] = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()
    return _cache[chave]


def icone(nome, escala=2, titulo=""):
    alt = titulo or nome
    return (f'<img class="ico" src="{_uri(nome, escala)}" width="{16 * escala}" '
            f'height="{16 * escala}" alt="{alt}" draggable="false">')


# ------------------------------------------------------------- receitas

def _receita_json(nome):
    return json.load(open(RECIPES / f"{nome}.json", encoding="utf-8"))


def _tex_de_item(item_id):
    """Namespace intoxicantes -> textura do mod; minecraft: -> mc:<caminho>."""
    ns, _, caminho = item_id.partition(":")
    return caminho if ns == "intoxicantes" else f"mc:item/{caminho}"


def receita_crafting(nome):
    """Receita 3x3 REAL lida do JSON (pattern + key)."""
    d = _receita_json(nome)
    padrao = [p.ljust(3, " ") for p in d["pattern"]]
    padrao += ["   "] * (3 - len(padrao))
    celulas = []
    for linha in range(3):
        for col in range(3):
            ch = padrao[linha][col]
            if ch == " ":
                celulas.append('<div class="slot"></div>')
            else:
                entrada = d["key"][ch]
                item_id = entrada if isinstance(entrada, str) else entrada["item"]
                celulas.append(f'<div class="slot" title="{item_id}">{icone(_tex_de_item(item_id), 2, item_id)}</div>')
    r = d["result"]
    n = r.get("count", 1)
    return (f'<div class="receita"><div class="grade3x3">{"".join(celulas)}</div>'
            f'<div class="seta-receita">➜</div>'
            f'<div class="slot resultado" title="{r["id"]}">{icone(_tex_de_item(r["id"]), 2, r["id"])}'
            f'<span class="qtd">{n}×</span></div></div>')


def receita_shapeless(nome, nota=None):
    """Receita sem forma: ingredientes -> resultado (como aparece no jogo)."""
    d = _receita_json(nome)

    def item_de(ing):
        return ing if isinstance(ing, str) else ing["item"]

    chips = "".join(f'<span class="chip">{icone(_tex_de_item(item_de(i)), 2, item_de(i))}</span>'
                    for i in d["ingredients"])
    r = d["result"]
    n = r.get("count", 1)
    nota_html = f'<div class="obs" style="margin-top:2px">{nota}</div>' if nota else ""
    return (f'<div class="receita"><div><div class="shapeless">{chips}</div>{nota_html}</div>'
            f'<div class="seta-receita">➜</div>'
            f'<div class="slot resultado" title="{r["id"]}">{icone(_tex_de_item(r["id"]), 2, r["id"])}'
            f'<span class="qtd">{n}×</span></div></div>')


def cadeia(*nos):
    """Cadeia visual: nó=(tipo, ...). item=(tex,qtd,rotulo) maq=(tex,nome,tempo,dica)."""
    html = []
    for i, no in enumerate(nos):
        if i:
            html.append('<div class="seta-baixo">▼</div>')
        if no[0] == "item":
            _, tex, qtd, rotulo = no
            html.append(f'<div class="no"><span class="q">{qtd}×</span>{icone(tex, 2, rotulo)}<span class="l">{rotulo}</span></div>')
        elif no[0] == "maq":
            _, tex, nome_m, tempo, dica = no
            html.append(f'<div class="maq">{icone(tex, 2, nome_m)}<div class="maq-texto"><div class="nome">{nome_m}</div><div class="dica">{dica}</div></div><span class="tempo">{tempo}</span></div>')
    # último item da cadeia ganha destaque
    return "".join(html)


def tabela(linhas):
    return "".join(f'<div class="linha"><span class="e">{e}</span><span class="v">{v}</span>'
                   f'<span class="t">{t}</span></div>' for e, v, t in linhas)


# ============================================================ CONTEÚDO
# Fontes: ProcessosBebida (cadeias/tempos), recipe/*.json (crafts), pt_br.json
# (nomes/lores), loot do IntoxicantesMod (sementes/drops), ModConfig (valores).

CATS = []


def categoria(id_, icone_, nome, sub, intro, entradas):
    CATS.append(dict(id=id_, icone=icone_, nome=nome, sub=sub, intro=intro, entradas=entradas))


# entradas: (id, icone, nome, desc, cadeia|None, receita_html|None, cartoes, rodapé)

BARRIL_RUM_NOTA = "A receita usa bloco de carvão no centro: a madeira escura sai \u201ctostada\u201d pro rum."

categoria("comecando", "guia", "Começando", "por onde começar",
    "O mapa da mina: o que é o SNC Adventures e o que fazer primeiro.", [
    ("sobre", "guia", "O que é o SNC Adventures?",
     "Mercado do Gago, economia em R$, plantações, bebidas e uma esquina muito viva.",
     None, None,
     [("O mundo", [("Esquinão", "mercado gerado perto de vilas", ""),
                   ("Gago", "vende bebidas e ingredientes, 24h", ""),
                   ("Traficante", "aparece caminhando; vende na porta", ""),
                   ("Juça", "o Matanza da esquina; troca Camel", "")],
       "A esquina nunca fecha: planta, destila, gasta — e se vira com a ressaca."),
      ("Regra de ouro", [("Lei da esquina", "beba com moderação — a ressaca cobra caro", ""),
                         ("Ressaca", "o mundo cobra a manhã seguinte", ""),
                         ("Fiado", "o Traficante anota. Sempre anota.", "")], None),
      ("__callout__", "Perdeu o guia? <b>Crafta outro</b>: livro vanilla + R$ na bancada. "
       "Este livro é um item de valor da esquina — não tem comando de graça; quem quer ler, carrega o livro.", None)],
     "14 categorias · 5 bebidas · 6 cultivos · 3 NPCs · 2 armas"),
    ("primeiros-passos", "mc:item/emerald", "Primeiros R$",
     "Do zumbi ao primeiro gole: como juntar e gastar dinheiro.",
     None, receita_shapeless("emerald_to_real_single", "esmeralda → R$ 1 (e o bloco vira 9)"),
     [("Ganhos", [("Zumbi", "15% de drop de R$", ""),
                  ("Piglin", "25% de drop", ""),
                  ("Venda", "sobras pro Gago/Traficante", "")],
       "Esmeralda ↔ real: 1 esmeralda = R$ 1; bloco de esmeralda = R$ 9."),
      ("Comandos", [("/saldo", "consulta o saldo virtual", ""),
                    ("/pagar", "transfere a outro jogador", "")], None)],
     "zumbi 15% · piglin 25% · esmeralda = R$ 1"),
])

categoria("bebidas", "cerveja", "Bebidas", "as 5 da esquina",
    "Cada bebida nasce numa plantação e descansa no barril certo. Cadeias com os números reais do código.", [
    ("garrafas", "vinho", LANG["guia.intoxicantes.bebidas.garrafas.titulo"],
     LANG["guia.intoxicantes.bebidas.garrafas.hero"], None, None,
     [("Colocar", [], LANG["guia.intoxicantes.bebidas.garrafas.colocar"]),
      ("Recolher", [], LANG["guia.intoxicantes.bebidas.garrafas.recolher"]),
      ("Beber", [], LANG["guia.intoxicantes.bebidas.garrafas.consumir"])],
     LANG["guia.intoxicantes.bebidas.garrafas.apoio"]),
    ("vinho", "vinho", "Vinho", "“Regenera devagar. Bebida de quem tem tempo (e paciência).”",
     [("item", "uva", 6, "Uva — do pé maduro"),
      ("maq", "prensa_uvas", "Prensa de Uvas", "2 s", "esmaga e coa"),
      ("item", "mosto_de_uva", 4, "Mosto de Uva"),
      ("maq", "barril_vinho", "Barril de Vinho", "5 min + 5 min", "fermenta e matura"),
      ("item", "mc:item/glass_bottle", 4, "Garrafa de Vidro — engarrafa 1 a 1, mão vazia"),
      ("item", "vinho", 4, "Vinho")],
     receita_crafting("barril_vinho"),
     [("No copo", [("Regeneração I", "cura constante", "30 s"),
                   ("Náusea", "o mundo gira", "15 s")],
       "Pode beber com a fome cheia; o copo devolve a garrafa."),
      ("As uvas", [("Sementes", "trepadeiras 20% · folhas de jungle 5%", ""),
                   ("Colheita", "cada cacho visível entrega 1 uva", "")],
       "6 uvas → 4 mosto: o vinho pede paciência."),
      ("O barril", [("Carregar", "4 mostos de uva", ""),
                    ("Pronto", "4 garrafas por lote (config)", ""),
                    ("Servir", "garrafa de vidro 1 a 1", "")], None)],
     "Uva → Prensa → Mosto → Barril (5+5 min) → 4 garrafas"),
    ("cachaca", "cachaca", "Cachaça", "“Golpe forte, perna mole. Beba antes do tiroteio, não durante.”",
     [("item", "cana_de_acucar", 4, "Cana-do-mod (2 canas vanilla → 2)"),
      ("maq", "moenda_cana", "Moenda de Cana", "2 s", "4 canas → 4 caldo + 1 bagaço"),
      ("item", "caldo_de_cana", 4, "Caldo de Cana"),
      ("maq", "dorna_bebida", "Dorna de Fermentação", "21 s", "4 caldo → 4 mosto"),
      ("item", "mosto_cana_fermentado", 4, "Mosto de Cana Fermentado"),
      ("maq", "alambique", "Alambique de Cobre", "4,5 s", "fogo embaixo, sempre"),
      ("item", "cachaca_jovem", 2, "Cachaça Jovem (Branca)"),
      ("maq", "barril_cachaca", "Barril de Cachaça", "5 min", "só maturação"),
      ("item", "mc:item/glass_bottle", 4, "Garrafa de Vidro"),
      ("item", "cachaca", 4, "Cachaça")],
     receita_crafting("barril_cachaca"),
     [("No copo", [("Força II", "o golpe", "45 s"),
                   ("Náusea", "o mundo gira", "20 s"),
                   ("Lentidão", "perna mole", "15 s")],
       "A mais forte da casa — beba ANTES do tiroteio."),
      ("A cana", [("Bagaço", "1 por moenda: queima na fornalha", ""),
                  ("Melaço", "caldo na fornalha → melaço (pro rum)", "")],
       "A moenda abre as DUAS cadeias: cachaça e rum."),
      ("O barril", [("Carregar", "2 jovens do alambique", ""),
                    ("Maturação", "5 min de descanso", "")], None)],
     "Cana → Moenda → Dorna → Alambique → Barril (5 min) → 4 garrafas"),
    ("cerveja", "cerveja", "Cerveja", "“Clássica da esquina: força pro trampo, ressaca depois.”",
     [("item", "semente_cevada", 4, "Sementes de Cevada (grama da taiga 6%)"),
      ("item", "cevada", 1, "Cevada (4 sementes agrupam em 1)"),
      ("maq", "mc:item/furnace_front", "Fornalha", "10 s", "cevada → Malte"),
      ("item", "malte", 4, "Malte de Cevada"),
      ("maq", "caldeirao_mostura", "Caldeirão de Mostura", "2 s + 1,5 s", "malte → mostura → + 1 lúpulo"),
      ("item", "mosto_cerveja_lupulado", 4, "Mosto Lupulado"),
      ("maq", "barril_cerveja", "Barril de Cerveja", "4 min + 1 min", "fermenta e condiciona"),
      ("item", "cerveja", 4, "Cerveja")],
     receita_crafting("barril_cerveja"),
     [("No copo", [("Força I", "trampo rende", "45 s"),
                   ("Náusea", "a conta chega", "10 s")],
       "A bebida de entrada do mod — e a favorita do Gago."),
      ("A cevada", [("Sementes", "grama da taiga (6%)", ""),
                    ("Agrupar", "4 sementes → 1 cevada", "")],
       "ÁGUA embaixo do caldeirão é obrigatória."),
      ("O barril", [("Carregar", "4 mostos lupulados", ""),
                    ("Fases", "4 min borbulhando + 1 min quieto", "")], None)],
     "Cevada → Malte → Caldeirão + lúpulo → Barril (4+1 min) → 4 garrafas"),
    ("rum", "rum", "Rum", "“Barriga de rum não pega fogo. Pirata sabe o porquê.”",
     [("item", "caldo_de_cana", 1, "Caldo de Cana"),
      ("maq", "mc:item/furnace_front", "Fornalha", "10 s", "caldo → melaço"),
      ("item", "melaco", 1, "Melaço"),
      ("maq", "dorna_bebida", "Dorna de Fermentação", "21 s", "1 melaço → 4 mosto de rum"),
      ("item", "mosto_rum_fermentado", 4, "Mosto de Rum Fermentado"),
      ("maq", "alambique", "Alambique de Cobre", "4,5 s", "4 mosto → 2 jovem"),
      ("item", "rum_jovem", 2, "Rum Jovem"),
      ("maq", "barril_rum", "Barril de Rum", "5 min", "madeira escura tostada"),
      ("item", "rum", 4, "Rum")],
     receita_crafting("barril_rum"),
     [("No copo", [("Resist. ao Fogo", "barriga de pirata", "60 s"),
                   ("Náusea", "o convés gira", "15 s")],
       "Sobreviva ao inferno — ou só à fogueira."),
      ("A madeira escura", [("Toras", "carvalho-escuro descascado", ""),
                            ("Centro", "bloco de carvão: tosta a madeira", "")],
         "O barril de rum é o único “tostado” do mod."),
      ("O barril", [("Carregar", "2 jovens", ""),
                    ("Maturação", "5 min", "")], None)],
     "Caldo → Fornalha → Melaço → Dorna → Alambique → Barril (5 min) → 4 garrafas"),
    ("hidromel", "hidromel", "Hidromel", "“Doce e pesado: absorção extra pra aguentar a pancadaria.”",
     [("item", "mc:item/honey_bottle", 1, "Frasco de Mel"),
      ("item", "mc:item/glass_bottle", 1, "Garrafa de Vidro"),
      ("item", "hidromel", 2, "Hidromel")],
     receita_crafting("hidromel"),
     [("No copo", [("Absorção I", "coração extra", "60 s"),
                   ("Náusea", "doce demais", "10 s")],
       "Única bebida SEM cadeia: crafting direto, sem dorna nem barril."),
      ("O mel", [("Frasco", "devolvido no craft", ""),
                 ("Fonte", "apiário vanilla", "")], None)],
     "Frasco de mel + garrafa → 2 hidroméis"),
])

categoria("cultivos", "uva", "Cultivos", "6 plantações",
    "Seis plantas: semente, estágios, maturação UV e colheita — do jeito que o código faz.", [
    ("uva-c", "semente_uva", "Parreira de uva", "Saquinho SNC; trepadeiras (20%) e folhas de jungle (5%). Conduza pelos apoios de cerca.",
     [("item", "semente_uva", 1, "Saquinho SNC de Uva"),
      ("maq", "uva_plant", "Parreira", "5 estágios + UV", "cercas até 3 blocos; teto até 5×5"),
      ("item", "uva", 1, "Cada cacho visível = 1 uva")],
     None,
     [("Cachos vivos", [("Colher", "mão vazia ou segurando uvas", ""),
                        ("Quantidade", "10 cachos = 10 uvas separadas", ""),
                        ("Rebrota", "cada cacho: mínimo 60 s com luz e apoio", "")],
       "Colha o cacho ou sua cerca exata; só ele some. Folhas e vizinhos ficam."),
      ("Maturação", [("UV", "maturação própria por cacho", ""),
                    ("Mundo", "rebrota salva; poda não adianta", "")], None),
      ("Destino", [("Vinho", "6 uvas → prensa → mosto", ""),
                   ("Venda", "Gago compra quando tem", "")], None)],
     "Um cacho = uma uva · colheita individual"),
    ("lupulo-c", "semente_lupulo", "Pé de Lúpulo", "Bucha-doce (15%); 1 dose na fervura do caldeirão.",
     [("item", "semente_lupulo", 1, "Sementes de Lúpulo"),
      ("item", "lupulo", 1, "Lúpulo Fresco")],
     None,
     [("Ciclo", [("Sementes", "bucha-doce (15%)", ""),
                 ("Uso", "1 na fervura do caldeirão", "")],
       "Sem lúpulo não tem cerveja: a segunda dose é obrigatória."),
      ("Dica", [("Estoque", "o Gago vende quando tem", "")], None)],
     "Bucha-doce 15% · 1 dose por lote de cerveja"),
    ("cafe-c", "semente_cafe", "Pé de Café", "Samambaia grande (10%); café verde vira extrato de cafeína.",
     [("item", "semente_cafe", 1, "Sementes de Café"),
      ("item", "cafe_verde", 1, "Café Verde")],
     None,
     [("Ciclo", [("Sementes", "samambaia grande (10%)", "")],
       "O extrato de cafeína pressa+velocidade 120 s e MATA a ressaca."),
      ("Dica", [("Extrato", "café + açúcar + garrafa", "")], None)],
     "Samambaia grande 10% · base do extrato"),
    ("maconha-c", "semente_maconha", "Pé de Maconha", "Grama/samambaia (10%); seda vira baseado.",
     [("item", "semente_maconha", 1, "Sementes de Maconha"),
      ("item", "maconha_seda", 1, "Seda de Maconha")],
     None,
     [("Ciclo", [("Sementes", "grama/samambaia (10%)", "")],
       "2 sedas + papel → 2 baseados."),
      ("Dica", [("Fumaça", "verde, contínua enquanto puxa", "")], None)],
     "Grama/samambaia 10% · seda → baseado"),
    ("papoula-c", "semente_papoula", "Papoula", "A papoula vanilla dropa semente (8%) E ópio (35%).",
     [("item", "semente_papoula", 1, "Sementes de Papoula"),
      ("item", "opio", 1, "Ópio")],
     None,
     [("Ciclo", [("Semente", "8% na papoula vanilla", ""),
                 ("Ópio", "35% na papoula vanilla", "")],
       "2 papoulas → 1 ópio; ópio é base da heroína."),
      ("Dica", [("Plante", "replante a semente", "")], None)],
     "Semente 8% · ópio 35% · base da heroína"),
    ("cevada-c", "semente_cevada", "Cevada", "Grama da taiga (6%); 4 sementes agrupam em 1 cevada.",
     [("item", "semente_cevada", 4, "4 Sementes de Cevada"),
      ("item", "cevada", 1, "Cevada")],
     receita_shapeless("cevada_agrupa", "4 sementes agrupam em 1 cevada"),
     [("Ciclo", [("Sementes", "taiga/planície fria (6%)", ""),
                 ("Malte", "fornalha: cevada → malte", "")],
       "O coração da cerveja: sem cevada, sem mostura."),
      ("Dica", [("Replante", "quebre madura e replante", "")], None)],
     "Taiga 6% · 4 sementes → 1 cevada → malte"),
])

categoria("maquinas", "moenda_cana", "Máquinas", "moenda, prensa, caldeirão, dorna, alambique, UV",
    "As máquinas da cadeia: como fabricar, carregar e recolher — todas no mesmo padrão.", [
    ("moenda", "moenda_cana", "Moenda de Cana", "4 canas → 4 caldo + 1 bagaço. O bagaço queima na fornalha.",
     [("item", "cana_de_acucar", 4, "Cana-do-mod"),
      ("maq", "moenda_cana", "Moenda de Cana", "2 s", "ingrediente único"),
      ("item", "caldo_de_cana", 4, "Caldo de Cana (+1 bagaço)")],
     receita_crafting("moenda_cana"),
     [("Como funciona", [("Carregar", "4 canas na mão", ""),
                         ("Espera", "2 segundos", ""),
                         ("Recolher", "mão vazia pega o caldo", "")],
       "Serve cachaça E rum: a moenda abre as duas cadeias."),
      ("Bagaço", [("Extra", "1 bagaço por moenda", ""),
                  ("Uso", "combustível de fornalha", "")], None)],
     "4 canas → 4 caldo + 1 bagaço · 2 s"),
    ("prensa", "prensa_uvas", "Prensa de Uvas", "6 uvas → 4 mosto de uva. O primeiro passo do vinho.",
     [("item", "uva", 6, "Uvas"),
      ("maq", "prensa_uvas", "Prensa de Uvas", "2 s", "esmaga e coa"),
      ("item", "mosto_de_uva", 4, "Mosto de Uva")],
     receita_crafting("prensa_uvas"),
     [("Como funciona", [("Carregar", "6 uvas", ""),
                         ("Recolher", "4 mostos, mão vazia", "")],
       "O mosto vai direto pro Barril de Vinho.")],
     "6 uvas → 4 mosto de uva · 2 s"),
    ("caldeirao", "caldeirao_mostura", "Caldeirão de Mostura", "4 malte + 1 lúpulo → 4 mosto lupulado. ÁGUA embaixo!",
     [("item", "malte", 4, "Malte de Cevada"),
      ("maq", "caldeirao_mostura", "Caldeirão de Mostura", "2 s + 1,5 s", "mostura → fervura (2ª dose)"),
      ("item", "mosto_cerveja_lupulado", 4, "Mosto Lupulado")],
     receita_crafting("caldeirao_mostura"),
     [("Duas doses", [("1ª dose", "4 malte → mostura (2 s)", ""),
                      ("2ª dose", "1 lúpulo → fervura (1,5 s)", ""),
                      ("Água", "fonte ou caldeirão cheio embaixo", "")],
       "Como na cervejaria real: malte primeiro, lúpulo depois."),
      ("Sem água", [("Bloqueio", "a 1ª dose não carrega", ""),
                    ("Aviso", "mensagem na interação", "")], None)],
     "4 malte + 1 lúpulo → 4 mosto · água obrigatória"),
    ("dorna", "dorna_bebida", "Dorna de Fermentação", "4 caldo (ou 1 melaço) → mosto fermentado. As borbulhas avisam.",
     [("item", "caldo_de_cana", 4, "Caldo de Cana (ou 1 melaço)"),
      ("maq", "dorna_bebida", "Dorna de Fermentação", "21 s", "borbulhas + partículas"),
      ("item", "mosto_cana_fermentado", 4, "Mosto Fermentado")],
     receita_crafting("dorna_bebida"),
     [("Receitas", [("Caldo de cana", "4 → 4 mosto de cana", "21 s"),
                    ("Melaço", "1 → 4 mosto de rum", "21 s")],
       "A dorna é genérica: futuras frutas caem aqui de graça."),
      ("Sinais", [("Fermentando", "borbulhas + % na mensagem", ""),
                  ("Servido", "recolha com a mão vazia", "")], None)],
     "4 caldo → 4 mosto · 1 melaço → 4 mosto · 21 s"),
    ("alambique", "alambique", "Alambique de Cobre", "4 mosto fermentado → 2 destilado jovem. Fogo embaixo, sempre.",
     [("item", "mosto_cana_fermentado", 4, "Mosto Fermentado"),
      ("maq", "alambique", "Alambique de Cobre", "4,5 s", "FOGO embaixo, senão pausa"),
      ("item", "cachaca_jovem", 2, "Destilado Jovem")],
     receita_crafting("alambique"),
     [("Como funciona", [("Concentra", "4 mosto → 2 jovens", ""),
                         ("Tempo", "4,5 s", "")],
       "A MESMA máquina pra cachaça e rum."),
      ("Sem fogo", [("Pausa", "destilação para e retoma", ""),
                    ("Progresso", "não se perde", "")],
       "A mensagem “SEM FOGO” avisa — coloque a fornalha embaixo.")],
     "4 mosto → 2 jovem · 4,5 s · fogo obrigatório"),
    ("lampada", "lampada_uv", "Lâmpada UV", "Matura cultivos no indoor: raio 2 (5×5×5), chance 35% por tick.",
     [("item", "lampada_uv", 1, "Lâmpada UV"),
      ("maq", "lampada_uv", "Lâmpada UV", "—", "redstone liga/desliga"),
      ("item", "uva", 1, "Cultivo amadurecendo")],
     receita_crafting("lampada_uv"),
     [("Como funciona", [("Raio", "5×5×5 ao redor", ""),
                         ("Chance", "35% por random tick", ""),
                         ("Energia", "redstone; disjuntor pode cortar", "")],
       "Indoor farming de uva/lúpulo/café/maconha/papoula/cevada."),
      ("Investimento", [("Preço", "R$ 36 no Gago (2/dia)", ""),
                        ("Som", "zumbido de proximidade", "")], None)],
     "Raio 2 · 35%/tick · ligada por redstone"),
    ("barris-maq", "barril_vinho", "Barris", "Fermentam e/ou maturam; engarrafamento 1 a 1 com garrafa.",
     [("item", "mosto_de_uva", 4, "Insumo da bebida"),
      ("maq", "barril_vinho", "Barril da bebida", "fase 1 + fase 2", "FERMENTANDO → MATURANDO"),
      ("item", "vinho", 4, "Bebida final")],
     receita_crafting("barril_vinho"),
     [("Fases", [("Fase 1", "FERMENTANDO: bolhas/partículas", ""),
                 ("Fase 2", "MATURANDO: silencioso", "")],
       "Cerveja e vinho usam as 2 fases; cachaça e rum só a maturação."),
      ("Engarrafar", [("Ferramenta", "garrafa de vidro", ""),
                      ("Recolha", "1 garrafa por clique", "")],
       "O rótulo é gravado no craft: o barril nunca troca de bebida.")],
     "Fase 1 + fase 2 → 4 garrafas por lote"),
])

categoria("fermentacao", "dorna_bebida", "Fermentação", "a dorna borbulha",
    "A etapa mágica: mosto cru vira mosto fermentado.", [
    ("dorna-fe", "dorna_bebida", "Como fermenta", "Caldo ou melaço entra; mosto fermentado sai.",
     [("item", "caldo_de_cana", 4, "Insumo cru (caldo/melaço)"),
      ("maq", "dorna_bebida", "Dorna de Fermentação", "21 s", "borbulhas + partículas"),
      ("item", "mosto_cana_fermentado", 4, "Mosto Fermentado")],
     receita_crafting("dorna_bebida"),
     [("Receitas", [("Caldo", "4 → 4 mosto de cana", "21 s"),
                    ("Melaço", "1 → 4 mosto de rum", "21 s")],
       "Futuras bebidas fermentáveis caem aqui sem mexer nas máquinas."),
      ("Dicas", [("Mão vazia", "recolhe o servido", ""),
                 ("Progresso", "salva com o mundo", "")], None)],
     "4 caldo → 4 mosto · 1 melaço → 4 mosto · 21 s"),
])

categoria("destilacao", "alambique", "Destilação", "fogo embaixo, cobre em cima",
    "O alambique concentra: 4 de mosto viram 2 de destilado jovem.", [
    ("alambique-de", "alambique", "Como destila", "Fogo embaixo, mosto dentro, jovem fora.",
     [("item", "mosto_cana_fermentado", 4, "Mosto fermentado"),
      ("maq", "alambique", "Alambique de Cobre", "4,5 s", "fogo obrigatório"),
      ("item", "cachaca_jovem", 2, "Destilado Jovem")],
     receita_crafting("alambique"),
     [("Concentra", [("Entrada", "4 mosto fermentado", ""),
                     ("Saída", "2 destilados jovens", "")],
       "Metade do volume, o dobro da graça."),
      ("Sem fogo", [("Pausa", "retoma de onde parou", "")], None)],
     "4 mosto → 2 jovem · fogo embaixo"),
])

categoria("barris-cat", "barril_vinho", "Barris e envelhecimento", "a madeira completa a obra",
    "Fermentação e maturação no barril certo — e o engarrafamento 1 a 1.", [
    ("barril-fe", "barril_vinho", "Como funciona o barril", "Fase 1 fermenta, fase 2 matura, garrafa recolhe.",
     [("item", "mosto_de_uva", 4, "Insumo (mosto ou jovem)"),
      ("maq", "barril_vinho", "Barril da bebida", "fase 1 + fase 2", "FERMENTANDO → MATURANDO"),
      ("item", "vinho", 4, "Bebida final")],
     receita_crafting("barril_vinho"),
     [("Fases", [("FERMENTANDO", "fase 1: bolhas", ""),
                 ("MATURANDO", "fase 2: silêncio", "")],
       "Cerveja/vinho: 2 fases. Cachaça/rum: só maturação."),
      ("Engarrafamento", [("Ferramenta", "garrafa de vidro", ""),
                          ("Lote", "4 garrafas (config)", "")],
       "Cada barril tem receita própria com o rótulo gravado.")],
     "4 insumos → 4 garrafas"),
])

categoria("armas", "escopeta", "Armas", "a 12 e o três-oitão",
    "As armas da rua: mecanismo de verdade, munição própria, reparo na bigorna.", [
    ("escopeta", "escopeta", "Escopeta 12", "8 balins por tiro, tubo de 5, recarga shell-by-shell.",
     [("item", "cartucho", 1, "Cartucho calibre 12"),
      ("item", "escopeta", 1, "Escopeta 12 (tubo 5)")],
     receita_crafting("escopeta"),
     [("Especificações", [("Munição", "cartucho calibre 12", ""),
                          ("Tubo", "5 cartuchos", ""),
                          ("Balins", "8 por tiro", ""),
                          ("ADS", "agachar: zoom + ½ dispersão", "")],
       "Recarga shell-by-shell segurando o usar; pump automático pós-tiro."),
      ("Manutenção", [("Durabilidade", "128 usos", ""),
                      ("Reparo", "bigorna + 2 cartuchos", "")],
       "Esqueletos dropam cartucho (20%).")],
     "8 balins · tubo 5 · ADS no agachar"),
    ("revolver", "revolver", "Revólver .38", "Tambor de 6, mais preciso e forte por bala.",
     [("item", "cartucho_38", 1, "Cartucho .38"),
      ("item", "revolver", 1, "Revólver .38 (tambor 6)")],
     receita_crafting("revolver"),
     [("Especificações", [("Munição", "cartucho .38", ""),
                          ("Tambor", "6 tiros, gira a cada disparo", ""),
                          ("Alcance", "30 blocos", "")],
       "O “três-oitão”: dano maior por bala que a 12."),
      ("Manutenção", [("Durabilidade", "256 usos", ""),
                      ("Reparo", "bigorna + 2 cartuchos .38", "")],
       "Esqueletos dropam .38 (12%).")],
     "Tambor 6 · dano maior por bala"),
])

categoria("municoes", "cartucho", "Munições", "polvora, prego e latão",
    "Cada arma tem a sua: calibre 12 e .38.", [
    ("cartucho-m", "cartucho", "Cartucho calibre 12", "Polvora + papel + nugget de ferro (o prego). 4 por craft.",
     [("item", "cartucho", 4, "Cartucho calibre 12")],
     receita_shapeless("cartucho", "polvora + papel + nugget de ferro → 4"),
     [("Uso", [("Arma", "Escopeta 12", ""),
               ("Loot", "esqueletos (20%)", ""),
               ("Reparo", "2 cartuchos na bigorna", "")], None)],
     "Escopeta 12 · 4/craft · esqueleto 20%"),
    ("cartucho-38", "cartucho_38", "Cartucho .38", "Polvora + 2 nuggets de ferro. 4 por craft.",
     [("item", "cartucho_38", 4, "Cartucho .38")],
     receita_shapeless("cartucho_38", "polvora + 2 nuggets de ferro → 4"),
     [("Uso", [("Arma", "Revólver .38", ""),
               ("Loot", "esqueletos (12%)", ""),
               ("Reparo", "2 cartuchos .38 na bigorna", "")], None)],
     "Revólver .38 · 4/craft · esqueleto 12%"),
])

categoria("mobs", "ovo_gago", "Mobs", "Gago, Traficante e Juça",
    "Os moradores da esquina: onde aparecem, o que fazem e o que dropam.", [
    ("gago", "ovo_gago", "Gago", "O dono do Esquinão: gagueja, vende bebidas e saca a 12 se mexerem com ele.",
     [("item", "ovo_gago", 1, "Ovo de Gago"),
      ("item", "cerveja", 1, "Bebidas à venda")],
     None,
     [("Onde vive", [("Local", "só no Mercado Esquinão", ""),
                     ("Horário", "24h; cochila fora do expediente", "")],
       "Comércio SÓ de bebida: bebidas, garrafas vazias, insumos de grow, lâmpada UV (R$ 36) — droga é com o camelo."),
      ("Cuidado", [("Tapas", "1º tapa = advertência; repetiu = a 12", ""),
                   ("Vandalismo", "quebrou, pagou — na bala", "")],
       "Loot ao morrer: cachaça, cervejas, R$ e cartuchos.")],
     "Esquinão · 24h · fidelidade · saca a 12"),
    ("traficante", "ovo_traficante", "Traficante", "Spawn periódico; só droga: vende estoque, compra o cru, fiado de R$ 50.",
     [("item", "ovo_traficante", 1, "Ovo de Traficante"),
      ("item", "real", 1, "R$ no inventário")],
     None,
     [("Onde vive", [("Spawn", "periódico perto de jogadores", ""),
                     ("Ponto", "tela própria: Estoque/Fiado/Cru", ""),
                     ("Cotação", "flutuante −30%…+40%", "")],
       "Compra o CRU de droga na porta (90% do preço do dia). Fiado: R$ 50, juros 10%; quitar sobe a confiança."),
      ("Fila", [("Desconto", "5% por freguês na fila", "")], None)],
     "Spawn periódico · fiado R$ 50 · cotação flutuante"),
    ("juca", "ovo_juca", "Juça (Jucelino)", "O barrigão da Banda Matanza: Camel na boca, riff na alma.",
     [("item", "ovo_juca", 1, "Ovo de Juça"),
      ("item", "cigarro_camel", 1, "Cigarro Camel")],
     None,
     [("O que faz", [("Troca", "Camel dele e volta o trocado", ""),
                     ("Chat", "ouve e responde com hÃ© hÃ©", "")],
       "Jogador com Camel na mão? O Juça vem conversar."),
      ("Camisa", [("Matanza", "quem veste não queima", "")], None)],
     "Banda Matanza · troca Camel · camisa antifogo"),
])

categoria("especiais", "baseado", "Itens especiais", "ervas, pós e pílulas",
    "Tudo com contrapartidas de verdade — igual o resto do mod.", [
    ("baseado-e", "baseado", "Baseado", "Regenera, cai devagar e cobra a náusea.",
     [("item", "maconha_seda", 2, "2 Sedas"),
      ("item", "mc:item/paper", 1, "Papel"),
      ("item", "baseado", 2, "2 Baseados")],
     receita_crafting("baseado"),
     [("No trago", [("Regeneração I", "cura constante", "15 s"),
                    ("Queda lenta", "desce suave", "30 s"),
                    ("Náusea", "a conta", "7,5 s")],
       "Fumaça verde contínua enquanto puxa; soltar antes não consome.")],
     "Regen 15 s + queda lenta + náusea"),
    ("cigarro-e", "cigarro_camel", "Cigarro Camel", "O cigarro amarelo do Juça: trago curto de nicotina de roleplay.",
     [("item", "mc:item/paper", 1, "Papel"),
      ("item", "mc:item/golden_carrot", 1, "Cenoura Dourada"),
      ("item", "cigarro_camel", 3, "3 Cigarros Camel")],
     receita_shapeless("cigarro_camel", "papel + cenoura dourada → 3"),
     [("No trago", [("Velocidade", "pressa de papel", "10 s"),
                    ("Náusea", "tontura curta", "5 s")],
       "Troque um com o Juça: ele respeita quem fuma o dele.")],
     "Velocidade 10 s · trago curto"),
    ("opio-e", "opio", "Ópio", "Anestésico de rua: resistência a troco de lentidão e fraqueza.",
     [("item", "opio", 1, "Ópio")],
     receita_crafting("opio"),
     [("No pó", [("Resistência I", "tanque barato", "30 s"),
                 ("Náusea", "a conta", "15 s"),
                 ("Fraqueza", "braços macarrão", "15 s")],
       "Base da heroína: 2 ópio + redstone + slime."),
      ("Fonte", [("Papoula", "35% no drop", "")], None)],
     "Resistência 30 s · náusea + fraqueza"),
    ("cocaina-e", "cocaina", "Cocaína", "Burst brutal de energia + queda brutal depois.",
     [("item", "cocaina", 1, "Cocaína")],
     receita_shapeless("cocaina", "açúcar + quartzo + farinha de osso → 2"),
     [("No pó", [("Velocidade III", "maratona", "60 s"),
                 ("Pressa II", "picareta voa", "60 s"),
                 ("Fadiga", "a queda", "20 s"),
                 ("Fraqueza", "a conta", "15 s")],
       "Antes de minerar, não durante.")],
     "Vel III + Pressa II 60 s · queda depois"),
    ("heroina-e", "heroina", "Heroína", "Anestesia total: resistência e regen — e câmera lenta.",
     [("item", "heroina", 1, "Heroína")],
     receita_shapeless("heroina", "2 ópio + redstone + slime → 1"),
     [("No pó", [("Resistência II", "tanque", "30 s"),
                 ("Regeneração", "cura", "20 s"),
                 ("Lentidão III", "câmera lenta", "30 s"),
                 ("Náusea", "a conta", "15 s")],
       "Você não sente o golpe — nem o chão.")],
     "Resist II + regen · lentidão III"),
    ("lsd-e", "lsd", "LSD", "Viagem total: levitação, visão distorcida e trevas.",
     [("item", "lsd", 1, "LSD")],
     receita_shapeless("lsd", "papel + lapis + redstone → 2"),
     [("Na pílula", [("Levitação", "a viagem", "10 s"),
                     ("Visão noturna", "cores inventadas", "120 s"),
                     ("Escuridão", "as trevas entre as cores", "10 s"),
                     ("Náusea", "a conta", "20 s")],
       "A pílula mais criativa — e a mais traiçoeira.")],
     "Levitação 10 s · visão 120 s · escuridão"),
    ("po-estelar-e", "po_estelar", "Pó Estelar", "Velocidade e pressa longas, fome no final.",
     [("item", "po_estelar", 1, "Pó Estelar")],
     receita_shapeless("po_estelar", "ingredientes do craft → 1"),
     [("No pó", [("Velocidade II", "vento nos pés", "90 s"),
                 ("Pressa", "minerar voa", "90 s"),
                 ("Fome", "a conta", "10 s")],
       "O pó “bom” da lista: sem náusea, mas cobra fome.")],
     "Vel II + pressa 90 s · fome no final"),
])

categoria("blocos", "poste_luz", "Blocos", "a rua do Esquinão",
    "Os blocos que dão cara de rua — poste, asfalto, letreiro e a lâmpada UV já vista em Máquinas.", [
    ("letreiro", "placa_esquinao", "Placa do Esquinão", "Letreiro de LED de 9 blocos com ABERTO/FECHADO gigante.",
     [("item", "placa_esquinao", 1, "Placa do Esquinão")],
     None,
     [("Como funciona", [("Tamanho", "9 blocos: torres + vão de LED", ""),
                         ("Ciclo", "ABERTO/FECHADO com cor e jingle", ""),
                         ("Autocura", "ticker 1×/30 s completa e adota", "")],
       "Fonte de LED 5×7 própria em Java — nada de fonte vanilla."),
      ("Dica", [("Blips", "som de leitura por linha", ""),
                ("VIP", "tier máximo tem fala própria", "")], None)],
     "9 blocos · jingle de virada · autocurável"),
    ("poste", "poste_luz", "Poste de Luz", "Acende sozinho 19h–5h; nos cantos do lote, fora de vaga; autocura peças.",
     [("item", "poste_luz", 1, "Poste de Luz")],
     None,
     [("Como funciona", [("Acende", "19h–5h, automático", ""),
                         ("Autocura", "repara peças erradas no lugar", ""),
                         ("Recolhe", "peça desgarrada volta pro eixo", "")],
       "3 blocos: coluna + braços + luminária de sódio. Nasce com o mercado.")],
     "Acende 19h–5h · autocura"),
    ("lampada-led", "lampada_led", "Lâmpada LED", "Tubo de LED convencional suspenso no teto do mercado — luz branca sempre acesa.",
     [("item", "lampada_led", 1, "Lâmpada LED")],
     None,
     [("Como funciona", [("Luz", "nível 15, teto claro", ""),
                         ("Layout", "3 fileiras sobre os corredores", "")],
       "v1.2.68: sempre acesa por agora — o liga/desliga e a energia entram depois.")],
     "Luz 15 · sempre acesa"),
    ("prateleira-mercado", "prateleira_mercado", "Prateleira de Mercado", "A gôndola que VENDE do Esquinão: 9 slots de item de verdade, preço na etiqueta e entrega na quarta-feira.",
     [("item", "prateleira_mercado", 1, "Prateleira de Mercado")],
     None,
     [("Vender", [("Clique vazio", "compra o produto do slot (R$ da etiqueta)", ""),
                  ("Shift+vazio", "leva o carrinho inteiro", "")],
       "Item sem preço de tabela (o que o dono pôs) sai de graça — gôndola não é alfândega."),
      ("Repor", [("Clique com item", "insere no slot mirado (qualquer item)", ""),
                 ("Shift+item", "modo gerente: recolhe a pilha", "")],
       "Garrafa, semente, o que der — a prateleira aceita QUALQUER coisa."),
      ("Entrega", [("Quarta-feira", "o caminhão repõe o estoque de partida", ""),
                   ("Relógio", "o dia da semana aparece no canto (qua 14:32)", "")],
       "Estilo My Summer Car: o mercadinho reabastece num dia fixo da semana.")],
     "Vende qualquer item · entrega na quarta"),
    ("lampada-potencia", "lampada_led_50w", "Lâmpadas LED por Potência", "Dez potências como na vida real (5W a 200W): bulbo residencial, refletor e high-bay industrial — quanto maior o watt, maior a luz.",
     [("item", f"lampada_led_{w}w", 1, f"LED {w}W") for w in (5, 9, 12, 15, 20, 30, 50, 100, 150, 200)],
     None,
     [("Famílias", [("5–20W", "bulbo residencial (luz 7–10)", ""),
                    ("30–50W", "refletor do quintal (luz 11–13)", ""),
                    ("100–200W", "high-bay industrial (luz 14–15)", "")],
       "A luz ESCALA COM O WATT — a 200W ilumina como o sol do teto do galpão."),
      ("Receita em série", [("5W", "vidro + tocha", ""),
                            ("Próximo watt", "lâmpada anterior + vidro + glowstone/ferro/diamante", "")],
       "Você não paga high-bay com vidrinho: o degrau da família custa o ingrediente dela."),
      ("Liga/desliga", [("Clique", "alterna acesa/apagada", ""),
                        ("Troca", "segure outra potência e clique pra trocar no lugar", "")],
       "Lâmpada morta é eterna — manutenção é craftar outra (como na vida real).")],
     "5W a 200W · clique liga/desliga"),
    ("asfalto-b", "asfalto", "Asfalto", "O chão xadrez do pátio; o zelador reforma o que envelheceu.",
     [("item", "asfalto", 1, "Asfalto")],
     None,
     [("Como funciona", [("Visual", "xadrez de rua", ""),
                         ("Zelador", "reforma pátios velhos", "")],
       "O pátio nasce com fundação e vagas demarcadas — sem obstáculo dentro (v1.2.68).")],
     "Chão do Esquinão · xadrez"),
    ("faixa-b", "faixa_pedestre", "Faixa de Pedestre", "Listras brancas na calçada; o zelador recolhe as velhas.",
     [("item", "faixa_pedestre", 1, "Faixa de Pedestre")],
     None,
     [("Como funciona", [("Visual", "listras brancas", "")],
       "Um hidrante junto, na calçada — nunca na vaga.")],
     "Listras na calçada"),
    ("hidrante-b", "hidrante", "Hidrante", "15 pixels de segurança na calçada, nunca na vaga.",
     [("item", "hidrante", 1, "Hidrante")],
     None,
     [("Como funciona", [("Altura", "15px", ""),
                         ("Vagas", "nunca na vaga", "")],
       "O zelador recolhe hidrantes velhos das vagas em mundos antigos.")],
     "15px · um na calçada"),
    ("painel-b", "painel_led", "Painel de LED + Central", "A TV de tela plana craftável e o controle remoto.",
     [("item", "painel_led", 1, "Painel de LED"),
      ("item", "central_comando", 1, "Central de Comando")],
     receita_crafting("painel_led"),
     [("Como funciona", [("Painel", "texto por código, cor/brilho do NBT", ""),
                         ("Central", "controle portátil p/ painéis perto", "")],
       "Receita da Central: vidro + ferro + redstone + botão (Central de Comando)."),
      ("Dica", [("Teclas", "usa os atalhos configurados pelo jogador", "")], None)],
     "Painel craftável + Central remota"),
    ("camisa-b", "camisa_matanza", "Camisa do Matanza", "Peito preto de couro: quem veste NÃO queima.",
     [("item", "camisa_matanza", 1, "Camisa do Matanza")],
     receita_crafting("camisa_matanza"),
     [("Como funciona", [("Peito", "resistência ao fogo vestida", ""),
                         ("Reparo", "couro (tag vanilla)", "")],
       "Poderes matanzísticos demoníacos, cortesia do Juça.")],
     "Fire resistance permanente · lã preta"),
])

categoria("economia", "real", "Economia R$", "saldo, fiado e cotação",
    "R$ do mod: saldo virtual, notas físicas, fiado e a cotação da rua.", [
    ("real-e", "real", "Real (R$)", "A moeda: notas no inventário + saldo virtual no Gago.",
     [("item", "mc:item/emerald", 1, "Esmeralda"),
      ("item", "real", 9, "R$ (bloco de esmeralda vira 9)")],
     receita_shapeless("emerald_to_real", "bloco de esmeralda → 9 reais"),
     [("Duas formas", [("Física", "notas no inventário (Traficante)", ""),
                       ("Virtual", "saldo no banco do Gago (/saldo)", "")],
       "As duas direções têm receita: real → esmeralda também."),
      ("Comandos", [("/saldo", "consulta", ""), ("/pagar", "transfere", "")], None)],
     "1 esmeralda = R$ 1 · bloco = R$ 9"),
    ("fiado-e", "real", "Fiado do Traficante", "R$ 50 na hora, juros 10%, confiança sobe quitando.",
     [("item", "real", 50, "R$ 50 na mão")],
     None,
     [("Regras", [("Valor", "R$ 50 direto", ""),
                  ("Juros", "10% no nível 0", ""),
                  ("Confiança", "quitar 2× = 30%; 4× = palavra 0%", "")],
       "A dívida persiste no save — morrer não apaga."),
      ("Fila", [("Desconto", "5% por freguês na fila", "")], None)],
     "R$ 50 · juros 10% · quitar sobe a confiança"),
    ("fidelidade-e", "cerveja", "Fidelidade do Esquinão", "Compras viram nível; tier máximo = Dono da Esquina.",
     [("item", "cerveja", 1, "Compras acumulam nível")],
     None,
     [("Tiers", [("Progressão", "compras acumulam", ""),
                 ("Benefícios", "descontos crescentes", ""),
                 ("VIP", "fala própria ao ler o letreiro", "")],
       "Reposição às 07h do jogo, adiada durante atendimento."),
      ("Anti-explore", [("Relogar", "NÃO renova estoque", ""),
                        ("Relógio", "voltar o tempo não renova", "")], None)],
     "Compras → nível → descontos"),
])

categoria("efeitos", "extrato_cafeina", "Efeitos", "consulta rápida",
    "Quem faz o quê, e por quanto tempo — valores reais do código e do config.", [
    ("efeitos-tabela", "extrato_cafeina", "Tabela de efeitos", "Consulta rápida de todos os consumíveis.",
     [("item", "extrato_cafeina", 1, "Extrato de Cafeína")],
     receita_shapeless("extrato_cafeina", "café verde + açúcar + garrafa → extrato"),
     [("Bebidas", [("Cerveja", "Força I 45 s + náusea 10 s", ""),
                   ("Vinho", "Regen I 30 s + náusea 15 s", ""),
                   ("Cachaça", "Força II 45 s + náusea 20 s + lento 15 s", ""),
                   ("Rum", "Resist. fogo 60 s + náusea 15 s", ""),
                   ("Hidromel", "Absorção 60 s + náusea 10 s", "")], None),
      ("Fumáveis", [("Baseado", "Regen 15 s + queda lenta 30 s + náusea", ""),
                    ("Camel", "Velocidade 10 s + náusea 5 s", "")], None),
      ("Pós e pílulas", [("Ópio", "Resist 30 s + náusea/fraqueza 15 s", ""),
                         ("Cocaína", "Vel III + pressa II 60 s + queda", ""),
                         ("Heroína", "Resist II + regen + lento III", ""),
                         ("LSD", "Levitação 10 s + visão 120 s + escuridão", ""),
                         ("Pó Estelar", "Vel II + pressa 90 s + fome", ""),
                         ("Café (extrato)", "pressa + vel 120 s; MATA a ressaca", "")],
       "Teto de embriaguez no nível 7: o corpo devolve o gole (refluxo).")],
     "5 bebidas · 2 fumáveis · pós/pílulas · café cura a ressaca"),
])

# ============================================================ HTML

CSS = """
*{box-sizing:border-box;margin:0;padding:0}
body{background:#17171b radial-gradient(1200px 600px at 50% -100px,#232329,#17171b);
  color:#cfc9b8;font-family:Consolas,'Courier New',monospace;padding:26px;display:flex;
  flex-direction:column;align-items:center;gap:26px}
.cab{width:100%;text-align:center;margin-bottom:-12px}
.cab h1{color:#F2D06B;font-size:20px;letter-spacing:2px;text-shadow:2px 2px #000}
.cab p{font-size:12.5px;color:#9a937f;margin-top:6px}
.cab p b{color:#1E6B38}
.cab p code{color:#d8c79a}
/* ---- IDENTIDADE PRÓPRIA DO GUIA: LIVRO DE COURO + PAPEL ENVELHECIDO + TINTA.
   Nada de fachada verde do Esquinão: aqui é capa de couro, lombada costurada,
   sumário com pontilhado, carimbo de tinta e fichas pautadas. ---- */
.screen{width:648px;height:504px;background:
  linear-gradient(105deg,#4a3320 0%,#3a2717 55%,#2c1d10 100%);
  border:3px solid #191007;border-radius:6px;padding:12px 12px 12px 26px;position:relative;
  box-shadow:0 18px 50px rgba(0,0,0,.65), inset 0 0 0 2px #241709, inset 0 0 26px rgba(0,0,0,.5)}
.screen::before{content:'';position:absolute;left:7px;top:16px;bottom:16px;width:8px;
  border-left:3px dashed rgba(216,199,154,.35)}
.papel{position:absolute;inset:12px 12px 12px 28px;background:
  radial-gradient(240px 150px at 85% 12%,rgba(160,120,60,.12),transparent 60%),
  radial-gradient(300px 170px at 8% 90%,rgba(120,90,50,.16),transparent 60%),
  radial-gradient(200px 120px at 72% 72%,rgba(90,60,30,.10),transparent 60%),
  linear-gradient(#f5e9c8,#e9d8ab);
  box-shadow:inset 8px 0 16px rgba(70,45,20,.28), inset 0 0 40px rgba(120,90,45,.28);
  color:#3a2a17;overflow:hidden;display:flex;flex-direction:column;border-radius:0 4px 4px 0}
.papel::before{content:'';position:absolute;left:0;top:0;bottom:0;width:30px;
  background:linear-gradient(90deg,rgba(70,45,20,.20),transparent);pointer-events:none;z-index:2}
.topo{color:#3a2a17;padding:10px 14px 6px 34px;display:flex;align-items:center;gap:10px}
.topo .t{display:inline-block;border:3px double rgba(70,42,20,.75);color:#4a2c14;
  font-size:15px;letter-spacing:2px;font-weight:bold;padding:4px 12px;
  transform:rotate(-1.3deg);background:rgba(70,42,20,.06);box-shadow:1px 2px 0 rgba(70,42,20,.15)}
.topo .st{font-size:11px;color:#7a6248;margin-left:auto;text-align:right;line-height:1.25;font-style:italic}
.corpo{flex:1;padding:6px 16px 10px 36px;display:flex;flex-direction:column;min-height:0;overflow-y:auto}
.rodape{border-top:2px dashed rgba(70,42,20,.4);padding:6px 14px 6px 34px;display:flex;gap:10px;
  align-items:center;font-size:11.5px;color:#7a6248;flex-wrap:wrap}
.btn{background:transparent;border:2px solid #4a2c14;padding:3px 10px;color:#3a2a17;
  font-size:11.5px;cursor:pointer;white-space:nowrap;font-weight:bold;letter-spacing:.5px}
.btn:hover{background:#4a2c14;color:#f5e9c8}
.sum-item{display:flex;align-items:baseline;gap:9px;padding:4px 6px;cursor:pointer}
.sum-item:hover{background:rgba(70,42,20,.08)}
.sum-n{font-weight:bold;font-size:13.5px;color:#3a2a17;white-space:nowrap}
.sum-d{font-size:11px;color:#8a7455;font-style:italic;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:170px}
.pontos{flex:1;border-bottom:2px dotted rgba(70,42,20,.45);min-width:18px;transform:translateY(-3px)}
.sum-pag{font-weight:bold;color:#4a2c14;font-size:11.5px;white-space:nowrap}
.entrada{display:flex;gap:12px;align-items:center;padding:6px 6px;cursor:pointer;
  border-bottom:1px dashed rgba(70,42,20,.28)}
.entrada>div:nth-of-type(1){flex:1;min-width:0}
.entrada:hover{background:rgba(160,120,60,.15)}
.entrada .n{font-weight:bold;font-size:14px;color:#3a2a17}
.entrada .d{font-size:11.5px;color:#8a7455;font-style:italic}
.tag{margin-top:3px;align-self:flex-start;border:2px solid rgba(122,52,32,.75);color:#8a3a24;
  background:rgba(122,52,32,.06);font-size:10px;padding:2px 8px;display:inline-block;
  font-weight:bold;letter-spacing:.5px;text-transform:uppercase;transform:rotate(-.6deg)}
.intro-cat{font-size:12.5px;font-style:italic;color:#6b543a;margin:2px 0 6px 4px}
.heroe{display:flex;gap:12px;align-items:center;border-bottom:3px double rgba(70,42,20,.5);padding-bottom:8px}
.heroe .n{font-size:19px;font-weight:bold;color:#4a2c14}
.heroe .d{font-size:12px;font-style:italic;color:#8a7455}
.duas{display:flex;gap:14px;flex:1;min-height:0;margin-top:8px}
.esq{flex:1.15;display:flex;flex-direction:column;min-width:0}
.dir{flex:1;display:flex;flex-direction:column;gap:8px}
.secao{font-size:11px;letter-spacing:1.5px;color:#7a6248;border-bottom:1px solid rgba(70,42,20,.4);
  padding-bottom:2px;margin-bottom:6px}
.cadeia{display:flex;flex-direction:column}
.no{display:flex;align-items:center;gap:9px;padding:2px 6px}
.no .q{font-weight:bold;font-size:14px;color:#8a3a24;min-width:34px;text-align:right}
.no .l{font-size:12.5px;color:#3a2a17}
.maq{display:flex;align-items:center;gap:5px 9px;flex-wrap:wrap;background:rgba(90,107,47,.10);
  border:2px dashed rgba(90,107,47,.55);padding:3px 8px;margin:1px 14px}
.maq .nome{font-size:12px;font-weight:bold;color:#4a5a24;white-space:nowrap}
.maq .tempo{margin-left:auto;background:transparent;color:#6b4f10;font-size:10.5px;
  font-weight:bold;padding:1px 7px;border:2px solid #8a6612;white-space:nowrap}
.maq .dica{font-size:10.5px;color:#8a7455;white-space:nowrap}
.maq .maq-texto{flex:1;min-width:120px}
.seta-baixo{color:#8a7455;text-align:center;font-size:11px;line-height:1;padding:1px 0}
.cartao{border:1px solid rgba(70,42,20,.4);border-top:3px double #4a2c14;
  background:repeating-linear-gradient(transparent 0 19px, rgba(70,42,20,.09) 19px 20px), rgba(255,251,238,.55);
  padding:7px 10px}
.cartao .n{font-size:12.5px;font-weight:bold;color:#4a2c14;margin-bottom:4px}
.cartao .n::before{content:'§ ';color:#8a6612}
.linha{font-size:12px;padding:1.5px 0}
.linha .e{color:#8a3a24;font-weight:bold;white-space:nowrap}
.linha .e::after{content:'—';color:rgba(70,42,20,.35);margin:0 5px;font-weight:normal}
.linha .v{color:#3a2a17}
.linha .t{margin-left:auto;color:#8a7455;white-space:nowrap;padding-left:8px}
.obs{font-size:10.5px;color:#8a7455;font-style:italic;margin-top:5px;line-height:1.35}
.receita{display:flex;align-items:center;gap:12px;margin:8px 0}
.grade3x3{display:grid;grid-template-columns:repeat(3,36px);grid-template-rows:repeat(3,36px);
  gap:3px;background:#8A7B5A;border:2px solid #2B2417;padding:3px}
.slot{width:36px;height:36px;background:#D8C79A;border:1px solid #8A7B5A;display:flex;
  align-items:center;justify-content:center;position:relative;overflow:hidden}
.slot img{width:32px;height:32px}
.slot:hover{background:#F2D06B}
.slot .qtd{position:absolute;right:1px;bottom:-1px;font-weight:bold;font-size:12px;
  color:#2B2417;text-shadow:1px 1px #E7D6A8}
.slot.resultado{background:#C8971E;border-color:#2B2417}
.slot.resultado:hover{background:#F2D06B}
.seta-receita{font-size:18px;color:#8A7B5A}
.shapeless{display:flex;flex-wrap:wrap;gap:4px;max-width:230px;padding:6px;
  background:#D8C79A;border:2px solid #8A7B5A}
.chip{display:flex}
/* ---- BARRA DE ROLAGEM na estética do livro (couro + puxador dourado) ---- */
*{scrollbar-width:thin;scrollbar-color:#4a3320 rgba(58,42,23,.35)}
::-webkit-scrollbar{width:12px}
::-webkit-scrollbar-track{background:rgba(58,42,23,.35);border-radius:8px;
  box-shadow:inset 0 0 4px rgba(0,0,0,.4)}
::-webkit-scrollbar-thumb{background:linear-gradient(90deg,#5a3f28,#3a2717);border-radius:8px;
  border:2px solid rgba(216,199,154,.4);box-shadow:inset 0 0 0 1px rgba(242,208,107,.25)}
::-webkit-scrollbar-thumb:hover{background:linear-gradient(90deg,#6b4c30,#4a3320)}
::-webkit-scrollbar-corner{background:rgba(58,42,23,.35)}
.corpo::-webkit-scrollbar{width:10px}
.corpo::-webkit-scrollbar-track{background:rgba(70,45,20,.18);border-radius:6px}
.corpo::-webkit-scrollbar-thumb{background:linear-gradient(90deg,#8a6612,#5c440c);border-radius:6px;
  border:2px solid rgba(245,233,200,.45)}
.corpo::-webkit-scrollbar-thumb:hover{background:linear-gradient(90deg,#a87b18,#6b4f10)}
/* ---- CAPA de couro com costura e rebite ---- */
.capa{position:absolute;inset:0;background:
  linear-gradient(115deg,#2c5c34 0%,#1E6B38 38%,#144324 72%,#0d3018 100%);
  display:flex;flex-direction:column;align-items:center;justify-content:center;gap:14px}
.capa::before{content:'';position:absolute;inset:13px;border:2px dashed rgba(216,199,154,.55);
  outline:3px double rgba(242,208,107,.35);outline-offset:5px;
  box-shadow:inset 0 0 90px rgba(0,0,0,.55)}
.capa::after{content:'';position:absolute;width:10px;height:10px;background:#C8971E;
  border:1px solid #6b4f10;box-shadow:0 0 0 2px rgba(0,0,0,.35);top:22px;left:22px}
.capa-titulo{font-size:36px;letter-spacing:7px;color:#F2D06B;font-weight:bold;
  text-shadow:0 2px 0 #0a2e16, 0 0 18px rgba(242,208,107,.3)}
.capa-sub{font-size:13px;color:#d9e6c6;letter-spacing:3px;font-style:italic}
.capa-hint{margin-top:16px;font-size:12.5px;color:#F2D06B;border:2px solid #C8971E;
  background:rgba(10,46,22,.55);padding:5px 16px;cursor:pointer;animation:pulsa 1.6s infinite;
  letter-spacing:1px}
.capa-hint:hover{background:rgba(200,151,30,.3)}
@keyframes pulsa{0%,100%{opacity:.7}50%{opacity:1}}
.capa-livro{filter:drop-shadow(0 8px 12px rgba(0,0,0,.55))}
.capa-rodape{position:absolute;bottom:24px;font-size:10.5px;color:#d9e6c6;opacity:.85}
/* ---- ABERTURA da entrada (capitular) e callout ---- */
.abertura{border:1px solid rgba(70,42,20,.4);background:rgba(255,251,238,.6);padding:10px 12px;
 font-size:12.5px;line-height:1.55;margin-bottom:8px}
.abertura p::first-letter{font-size:27px;font-weight:bold;color:#8a3a24;float:left;
 line-height:1;padding-right:6px;margin-top:2px}
.callout{border:1.5px solid rgba(122,84,32,.5);border-radius:12px 4px 12px 4px;
 background:rgba(122,84,32,.10);padding:8px 10px;font-size:12px;line-height:1.45}
.callout b{color:#6b4f10;letter-spacing:.5px}
.badge-num{min-width:22px;height:22px;border-radius:50%;background:#4a2c14;color:#f5e9c8;
 font-size:10.5px;font-weight:bold;display:flex;align-items:center;justify-content:center;flex-shrink:0}
"""

JS = """
var hist = [];
var atual = 'menu';
function ir(dest) {
  if (dest === 'back') { dest = hist.pop() || 'menu'; } else { hist.push(atual); }
  document.querySelectorAll('.screen').forEach(function (el) { el.style.display = 'none'; });
  var alvo = document.getElementById('screen-' + dest.replace(/:/g, '-'));
  if (alvo) { alvo.style.display = 'flex'; atual = dest; window.scrollTo(0, 0); }
}
function voltar() { ir('back'); }
document.addEventListener('keydown', function (ev) {
  if (ev.key === 'Escape' || ev.key === 'Backspace') { ev.preventDefault(); voltar(); }
});
"""


def tela_capa():
    return f"""
<div class="screen" id="screen-capa" style="display:flex"><div class="papel">
  <div class="capa">
    <div class="capa-livro">{icone("guia", 6, "Guia do SNC Adventures")}</div>
    <div class="capa-titulo">SNC ADVENTURES</div>
    <div class="capa-sub">— GUIA OFICIAL —</div>
    <div class="capa-hint" onclick="ir('menu')">ABRIR O GUIA ▸</div>
    <div class="capa-rodape">edição do jogador</div>
  </div>
</div></div>
"""


def tela_menu():
    itens = "".join(
        f'<div class="sum-item" onclick="ir(\'cat:{c["id"]}\')">{icone(c["icone"], 2, c["nome"])}'
        f'<span class="sum-n">{c["nome"]}</span>'
        f'<span class="sum-d">{c["sub"]}</span>'
        f'<span class="pontos"></span>'
        f'<span class="sum-pag">cap. {i:02d}</span></div>'
        for i, c in enumerate(CATS, 1)
    )
    return f"""
<div class="screen" id="screen-menu" style="display:none"><div class="papel">
  <div class="topo"><span class="t">SUMÁRIO</span>
    <span class="st">Guia do SNC Adventures<br>edição da esquina · {len(CATS)} capítulos</span>
  </div>
  <div class="corpo"><div>{itens}</div></div>
  <div class="rodape">
    <span class="btn" onclick="ir('capa')">◀ Capa</span>
    <span class="btn">Esc — voltar</span>
    <span>Perdeu o livro? Crafta outro: <b>livro + R$</b>.</span>
  </div>
</div></div>
"""


def tela_categoria(c):
    linhas = []
    for i, e in enumerate(c["entradas"], 1):
        badge = f'<span class="badge-num">{i:02d}</span>' if c["id"] == "comecando" else ""
        linhas.append(
            f'<div class="entrada" onclick="ir(\'ent:{c["id"]}:{e[0]}\')">{badge}{icone(e[1], 2, e[2])}'
            f'<div><div class="n">{e[2]}</div><div class="d">{e[3]}</div>'
            f'<div class="tag">{e[7]}</div></div></div>'
        )
    linhas = "".join(linhas)
    return f"""
<div class="screen" id="screen-cat-{c["id"]}"><div class="papel">
  <div class="topo">{icone(c["icone"], 2, c["nome"])}
    <div><div class="t">{c["nome"].upper()}</div></div>
    <div class="st">{len(c["entradas"])} entradas<br>capítulo do guia</div>
  </div>
  <div class="corpo"><div class="intro-cat">{c["intro"]}</div><div class="cadeia">{linhas}</div></div>
  <div class="rodape">
    <span class="btn" onclick="ir('menu')">◀ Índice</span>
    <span>Clique numa entrada pra abrir.</span>
  </div>
</div></div>
"""


# abertura capitular por entrada (chave "categoria:entrada")
ABERTURAS = {
    "comecando:sobre": "Bem-vindo à esquina, parceiro. Este livro é o manual da rua: como plantar, "
        "moer, fermentar, destilar, envelhecer, engarrafar, gastar — e como NÃO levar um fora no "
        "fiado. Nada aqui é real: é Minecraft com sotaque de bar. Comece pelos Primeiros R$, ache "
        "o Esquinão na beira da vila e siga o cheiro de cerveja.",
}


def tela_entrada(c, e):
    _, ic, nome, desc, nos_cadeia, receita, cartoes, rodape_txt = e
    chave_ab = f'{c["id"]}:{e[0]}'
    abertura_html = (f'<div class="abertura"><p>{ABERTURAS[chave_ab]}</p></div>'
                     if chave_ab in ABERTURAS else "")
    cadeia_html = cadeia(*nos_cadeia) if nos_cadeia else ""
    col_cadeia = ""
    if cadeia_html or receita:
        col_cadeia = f"""
      <div class="esq">
        <div class="secao">A CADEIA</div>
        <div class="cadeia">{cadeia_html}</div>
        {receita or ""}
      </div>"""
    partes = []
    for titulo, linhas, obs in cartoes:
        if titulo == "__callout__":
            partes.append(f'<div class="callout"><b>DICA DA ESQUINA —</b> {linhas}</div>')
            continue
        partes.append(f'<div class="cartao"><div class="n">{titulo}</div>{tabela(linhas)}'
                      + (f'<div class="obs">{obs}</div>' if obs else "") + "</div>")
    col_dir = "".join(partes)
    return f"""
<div class="screen" id="screen-ent-{c["id"]}-{e[0]}"><div class="papel">
  <div class="topo">{icone(ic, 2, nome)}
    <div><div class="t">{c["nome"].upper()} — {nome.upper()}</div></div>
    <div class="st">capítulo {c["nome"]}<br>Guia do SNC Adventures</div>
  </div>
  <div class="corpo">
    <div class="heroe">{icone(ic, 4, nome)}
      <div><div class="n">{nome}</div><div class="d">{desc}</div></div>
    </div>
    {abertura_html}
    <div class="duas">
      {col_cadeia}
      <div class="dir">{col_dir}</div>
    </div>
  </div>
  <div class="rodape">
    <span class="btn" onclick="ir('cat:{c["id"]}')">◀ {c["nome"]}</span>
    <span class="btn" onclick="ir('menu')">Índice</span>
    <span>{rodape_txt}</span>
  </div>
</div></div>
"""


corpos = [tela_capa(), tela_menu()]
for c in CATS:
    corpos.append(tela_categoria(c))
    for e in c["entradas"]:
        corpos.append(tela_entrada(c, e))

html = f"""<!DOCTYPE html>
<html lang="pt-BR"><head><meta charset="utf-8">
<title>Prévia completa — Guia do SNC Adventures</title>
<style>{CSS}</style></head>
<body>
  <div class="cab">
    <h1>GUIA DO SNC ADVENTURES — CAPA + ABERTURA REFINADA</h1>
    <p>Clique nas categorias e entradas · <b>Esc/Backspace</b> volta · texturas <b>reais</b> do mod ·
    receitas lidas dos JSONs de <code>recipe/</code> · cadeias e tempos de <code>ProcessosBebida</code></p>
  </div>
  {''.join(corpos)}
  <div class="legenda">O ícone do livro é conceito desenhado (não existe no jogo ainda); insumos vanilla vêm do jar do Minecraft 26.3.</div>
<script>{JS}</script>
</body></html>
"""

SAIDA.write_text(html, encoding="utf-8")
print(f"OK -> {SAIDA.relative_to(RAIZ)}  ({SAIDA.stat().st_size // 1024} KB, "
      f"{len(CATS)} categorias, {sum(len(c['entradas']) for c in CATS)} entradas, {len(_cache)} ícones)")
