# -*- coding: utf-8 -*-
"""Monta preview/previa-bebidas.html — a previa do sistema de bebidas v1.2.50
(barris 3D, cadeias das 4 bebidas, texturas novas, status dos testes)."""
import base64
import os

from PIL import Image

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(RAIZ)

TEX = "src/main/resources/assets/intoxicantes/textures"
# máquinas em alta resolução (atlas 128x128, células de material de 64px)
MAQUINAS_128 = ["dorna", "alambique", "moenda_cana", "prensa_uvas", "caldeirao_mostura"]
ITENS = ["cevada", "semente_cevada", "malte", "caldo_de_cana", "bagaco_de_cana",
         "melaco", "mosto_cana_fermentado", "mosto_rum_fermentado",
         "mosto_de_uva", "mosto_cerveja_lupulado", "cachaca_jovem", "rum_jovem"]
CROPS = ["cevada_stage%d" % i for i in range(8)]
ITENS_HD = ["cevada", "semente_cevada", "malte", "caldo_de_cana", "bagaco_de_cana",
            "melaco", "mosto_cana_fermentado", "mosto_rum_fermentado",
            "mosto_de_uva", "mosto_cerveja_lupulado", "cachaca_jovem", "rum_jovem"]
BACKUP_HD = os.path.join("backups", "20260924-texturas-hd")


def b64(caminho):
    with open(caminho, "rb") as f:
        return base64.b64encode(f.read()).decode()


def grade_png(nomes, pasta, tam=64):
    """Grade de texturas em tamanho legível — aceita 16×16 ou 128×128."""
    imgs = []
    for n in nomes:
        img = Image.open(os.path.join(TEX, pasta, n + ".png")).convert("RGBA")
        img = img.resize((tam, tam), Image.NEAREST)
        imgs.append((n, img))
    cols = min(len(imgs), 6)
    rows = (len(imgs) + cols - 1) // cols
    W, H = cols * (tam + 8), rows * (tam + 22)
    out = Image.new("RGBA", (W, H), (38, 40, 48, 255))
    from PIL import ImageDraw
    d = ImageDraw.Draw(out)
    for i, (n, img) in enumerate(imgs):
        x, y = (i % cols) * (tam + 8), (i // cols) * (tam + 22)
        out.paste(img, (x + 4, y + 4), img)
        d.text((x + 4, y + tam + 5), n[:22], fill=(200, 200, 190, 255))
    return out


def compara_maquinas():
    """Faixa ANTES (atlas 64px, células de 16px) x DEPOIS (128px, células de 64px)
    das texturas das 5 máquinas — mesmas dimensões na tela, densidade 16x."""
    from PIL import ImageDraw
    alvo = 180
    rotulos_w = 130
    W = rotulos_w + (alvo + 10) * 2 + 30
    H = len(MAQUINAS_128) * (alvo + 28) + 10
    out = Image.new("RGBA", (W, H), (38, 40, 48, 255))
    d = ImageDraw.Draw(out)
    for i, n in enumerate(MAQUINAS_128):
        velha = Image.open(os.path.join("backups", "20260924-maquinas-128", n + ".png")).convert("RGBA")
        velha = velha.resize((alvo, alvo), Image.NEAREST)
        nova = Image.open(os.path.join(TEX, "block", n + ".png")).convert("RGBA")
        nova = nova.resize((alvo, alvo), Image.NEAREST)
        y = 10 + i * (alvo + 28)
        d.text((8, y + alvo // 2 - 6), n[:15], fill=(220, 220, 205, 255))
        out.paste(velha, (rotulos_w, y), velha)
        out.paste(nova, (rotulos_w + alvo + 20, y), nova)
    return out


def compara_hd(pasta, nomes, alvo):
    """Faixa ANTES (16×16 do backup) × DEPOIS (128×128 novo) com rótulos."""
    from PIL import ImageDraw
    rotulos_w = 150
    W = rotulos_w + (alvo + 10) * 2 + 30
    H = len(nomes) * (alvo + 26) + 10
    out = Image.new("RGBA", (W, H), (38, 40, 48, 255))
    d = ImageDraw.Draw(out)
    for i, n in enumerate(nomes):
        velha = Image.open(os.path.join(BACKUP_HD, pasta, n + ".png")).convert("RGBA")
        velha = velha.resize((alvo, alvo), Image.NEAREST)
        nova = Image.open(os.path.join(TEX, pasta, n + ".png")).convert("RGBA")
        nova = nova.resize((alvo, alvo), Image.LANCZOS)
        y = 8 + i * (alvo + 26)
        d.text((8, y + alvo // 2 - 6), n[:18], fill=(220, 220, 205, 255))
        out.paste(velha, (rotulos_w, y), velha)
        out.paste(nova, (rotulos_w + alvo + 20, y), nova)
    return out


os.makedirs(os.path.join("..", "preview"), exist_ok=True)
compara_maquinas().save(os.path.join("..", "preview", "previa-maquinas-128.png"))
compara_hd("item", ITENS_HD, 96).save(os.path.join("..", "preview", "previa-itens-antes-depois.png"))
compara_hd("block", CROPS, 96).save(os.path.join("..", "preview", "previa-cevada-antes-depois.png"))
grade_png(ITENS, "item", tam=72).save(os.path.join("..", "preview", "previa-itens-bebida.png"))
grade_png(CROPS, "block", tam=88).save(os.path.join("..", "preview", "previa-cevada-crop.png"))

barril_modelos = b64("../preview/previa-barril-modelos.png")
barril_gui = b64("../preview/previa-barril-gui.png")
antes_depois = b64("../preview/previa-barris-antes-depois.png")
maquinas_3d = b64("../preview/previa-maquinas.png")
maquinas_tex = b64("../preview/previa-maquinas-128.png")
zoom_maq = b64("../preview/previa-maquinas-zoom.png")
itens = b64("../preview/previa-itens-bebida.png")
itens_ad = b64("../preview/previa-itens-antes-depois.png")
crop = b64("../preview/previa-cevada-crop.png")
crop_ad = b64("../preview/previa-cevada-antes-depois.png")

html = """<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<title>Prévia v1.2.50 — Sistema de Bebidas (fermentação, destilação, envelhecimento)</title>
<style>
  body{margin:0;background:#0d1117;color:#e6edf3;font-family:"Segoe UI",system-ui,sans-serif;padding:24px}
  h1{font-size:20px;margin:0 0 4px} h1 small{color:#8b949e;font-weight:400}
  .sub{color:#8b949e;font-size:13px;margin-bottom:18px}
  h2{font-size:16px;margin:28px 0 10px;color:#f0c674}
  img.painel{max-width:100%;border-radius:10px;border:1px solid #30363d;display:block;margin:8px 0}
  .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(330px,1fr));gap:12px}
  .card{background:#161b22;border:1px solid #30363d;border-radius:10px;padding:12px 14px}
  .card h3{margin:0 0 8px;font-size:14px;color:#79c0ff}
  .fluxo{font-size:13px;line-height:1.9;color:#c9d1d9}
  .fluxo b{color:#7ee787}
  .tempo{color:#ffa657;font-size:12px}
  .ok{color:#3fb950;font-weight:600}
  .nota{color:#8b949e;font-size:12px;margin-top:6px}
</style>
</head>
<body>
<h1>🍺 SISTEMA DE BEBIDAS — v1.2.50 <small>(fermentação · destilação · envelhecimento · engarrafamento)</small></h1>
<div class="sub">Auditoria confirmou as 4 bebidas existentes (cachaça, cerveja, rum + hidromel) e vinho — IDs preservados. As receitas simples de crafting foram REMOVIDAS; agora cada bebida tem sua cadeia de produção de verdade.</div>

<h2>🔥 ANTES × DEPOIS — o retrabalho dos barris</h2>
<img class="painel" src="data:image/png;base64,__ANTES_DEPOIS__">
<div class="nota">v1: 5 faixas grossas, aros de 1.1px de textura espremida (cara de bolo). v2: perfil bojudo de 7 bandas graduadas (chime → barriga → chime), 5 aros geométricos que acompanham a curva (o central grosso, como no carvalho real), tábuas verticais contínuas com frestas, cabeças com anéis concêntricos, rolha em degraus, torneira na cerveja e carbonização no rum.</div>

<h2>Os 4 barris — modelos 3D próprios (isométrica · lateral · topo · GUI)</h2>
<img class="painel" src="data:image/png;base64,__BARRIL_MODELOS__">
<div class="nota">Cachaça: madeira quente (amburana) com aro dourado · Cerveja: madeira clara com torneira · Rum: madeira escura tostada, aros pretos · Vinho: carvalho com anéis bordô. Cada barril é um BLOCO diferente (não recolor do vanilla), craft próprio e identidade gravada no BlockEntity.</div>

<h2>Ícones no inventário</h2>
<img class="painel" src="data:image/png;base64,__BARRIL_GUI__" style="max-width:1100px">

<h2>As 4 cadeias (como o jogador produz)</h2>
<div class="grid">
  <div class="card"><h3>🥃 Cachaça (destilada)</h3>
    <div class="fluxo">Cana → <b>Moenda</b> (4 canas→4 caldo+1 bagaço) → <b>Dorna</b> fermenta (7min) → <b>Alambique de Cobre</b> com fogo embaixo (1min30, pausa sem fogo) → cachaça jovem → <b>Barril de Cachaça</b> (10min) → 4 garrafas de <b>cachaça</b></div>
    <div class="tempo">Tempo real de jogo: ~19 min pelo ciclo completo (config ajustável)</div></div>
  <div class="card"><h3>🍺 Cerveja (fermentada + condicionada)</h3>
    <div class="fluxo">Cevada (crop novo, 8 estágios) → fornalha = <b>malte</b> → <b>Caldeirão de Mostura</b> com ÁGUA embaixo: 4 malte → mostura → + 1 lúpulo → fervura → mosto lupulado → <b>Barril de Cerveja</b> (fermenta 8min + condiciona 2min) → 4 garrafas</div>
    <div class="tempo">O lúpulo tem uso de verdade agora: sem ele o caldeirão não completa</div></div>
  <div class="card"><h3>🏴‍☠️ Rum (destilado + envelhecido)</h3>
    <div class="fluxo">Caldo de cana → fornalha = <b>melaço</b> → <b>Dorna</b> (1 melaço→4 mosto) → <b>Alambique</b> (o MESMO da cachaça) → rum jovem → <b>Barril de Rum</b> madeira tostada (10min) → 4 garrafas</div>
    <div class="tempo">Caminho mais longo da destilaria — como manda a tradição</div></div>
  <div class="card"><h3>🍷 Vinho (fermentado + maturado)</h3>
    <div class="fluxo">Uvas → <b>Prensa de Uvas</b> (6 uvas→4 mosto) → <b>Barril de Vinho</b> (fermenta 5min + matura 5min, dois estágios no mesmo bloco) → 4 garrafas</div>
    <div class="tempo">Adega completa: prensa + barril</div></div>
</div>

<h2>Máquinas de matéria-prima — modelos 3D com texturas em alta resolução</h2>
<img class="painel" src="data:image/png;base64,__MAQUINAS_3D__" style="max-width:1400px">
<div class="nota">Dorna, alambique, moenda, prensa e caldeirão: atlas <b>128×128</b> com células de material de 64px (antes: 64×64 com células de 16px — 16× menos densidade). Tábuas com fresta, canto iluminado, grão e nós · chapas com vincos, rebites e escovado · líquidos com bolhas de fermentação · cobre com pátina de envelhecimento.</div>
<img class="painel" src="data:image/png;base64,__MAQUINAS_TEX__" style="max-width:620px">
<h3 style="color:#79c0ff;font-size:14px;margin:18px 0 6px">Zoom de revisão — isométrica grande de cada máquina</h3>
<img class="painel" src="data:image/png;base64,__ZOOM_MAQ__" style="max-width:1400px">

<h2>Cevada — o crop novo em alta resolução (8 estágios 1:1, 128×128)</h2>
<img class="painel" src="data:image/png;base64,__CROP_AD__" style="max-width:520px">
<div class="nota">ANTES (esquerda): 16×16 com hastes de 1px. DEPOIS (direita): 128×128 pintado — colmos com nós e lâminas em arco, sombra de contato, espigas douradas com arestas (as "barbas") e amarelecimento na maturação. O dourado segue sendo a assinatura do "pronto"; no estágio 7 o bloco dropa até 4 grãos + semente extra.</div>
<img class="painel" src="data:image/png;base64,__CROP__" style="max-width:900px">
<div class="nota">Sementes também caem de grama alta (6%). Worldgen selvagem gerado e validado.</div>

<h2>Itens intermediários em alta resolução (cada etapa vira um item com lore)</h2>
<img class="painel" src="data:image/png;base64,__ITENS_AD__" style="max-width:520px">
<div class="nota">ANTES (esquerda): ASCII-art 16×16. DEPOIS (direita): 128×128 pintado — garrafas de vidro com gradiente de líquido, bolhas de fermentação, menisco, brilho e tampa de lata · pote bojudo de melaço com rolha de cortiça · feixes de cereal amarrados (cevada dourada, malte torrado) · bagaço com fibra e cacos de folha · pacote de sementes kraft com etiqueta.</div>
<img class="painel" src="data:image/png;base64,__ITENS__" style="max-width:900px">

<h2>Status</h2>
<div class="grid">
  <div class="card"><h3>✅ Validação</h3><div class="fluxo">
    Compilação main + gametest <span class="ok">verde</span><br>
    <b>48/48 game tests</b> — 43 antigos + <b>5 novos</b> (cadeia completa da cachaça, cerveja, rum, vinho + persistência do lote da dorna)<br>
    Assets: 0 texturas/modelos ausentes (validador próprio)<br>
    Worldgen JSONs válidos (validateWorldgen)</div></div>
  <div class="card"><h3>🛡️ Regras da spec atendidas</h3><div class="fluxo">
    Itens finais com <b>mesmo ID/namespace</b> (cachaca, cerveja, rum, vinho)<br>
    Receitas simples antigas <b>removidas</b> (impossível pular o sistema)<br>
    Estado persiste (save/restart/chunk unload) · 100% server-side<br>
    Performance: 1 checagem/segundo por bloco (regra 25)<br>
    Progresso visível ao interagir + partículas de fermentação<br>
    Arquitetura extensível: uísque/vodka = +1 registro no ProcessosBebida</div></div>
</div>

<div class="nota">Hidromel segue com receita simples (não tem cadeia na spec). A aprovação desta prévia libera o fluxo: bump 1.2.50 → changelog → build → 48 testes → JAR em mods/.</div>
</body>
</html>
"""

html = html.replace("__BARRIL_MODELOS__", barril_modelos)
html = html.replace("__ANTES_DEPOIS__", antes_depois)
html = html.replace("__BARRIL_GUI__", barril_gui)
html = html.replace("__MAQUINAS_3D__", maquinas_3d)
html = html.replace("__MAQUINAS_TEX__", maquinas_tex)
html = html.replace("__ZOOM_MAQ__", zoom_maq)
html = html.replace("__CROP__", crop)
html = html.replace("__CROP_AD__", crop_ad)
html = html.replace("__ITENS__", itens)
html = html.replace("__ITENS_AD__", itens_ad)

caminho = os.path.join("..", "preview", "previa-bebidas.html")
with open(caminho, "w", encoding="utf-8") as f:
    f.write(html)
print("prévia:", caminho)
