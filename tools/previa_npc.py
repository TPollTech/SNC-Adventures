# -*- coding: utf-8 -*-
"""Gera a preview v1.2.45 (NPC overhaul): ANTES (jar atual) x DEPOIS (workspace)."""
import base64
import os

from PIL import Image

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SAIDA = os.path.normpath(os.path.join(RAIZ, "..", "preview", "previa-npc-1245.html"))
MOD = os.path.join(RAIZ, "src", "main", "resources", "assets", "intoxicantes", "textures", "entity")

JAR = os.path.join(os.path.dirname(RAIZ), "mods", "intoxicantes-1.2.44.jar")
TMP = os.path.join(RAIZ, "build", "jar_atual")
os.makedirs(TMP, exist_ok=True)

import zipfile
with zipfile.ZipFile(JAR) as z:
    for nome in ("gago", "traficante", "juca"):
        with z.open("assets/intoxicantes/textures/entity/%s.png" % nome) as src:
            data = src.read()
        with open(os.path.join(TMP, nome + "_jar.png"), "wb") as dst:
            dst.write(data)


def b64(img):
    img = img.resize((img.width * 4, img.height * 4), Image.NEAREST)
    return base64.b64encode(img.convert("RGBA").tobytes()).decode()


def png_b64(path):
    with open(path, "rb") as f:
        return base64.b64encode(f.read()).decode()


def card_img(src_path, titulo, nota):
    return f'''
    <div class="card">
      <h3>{titulo}</h3>
      <img src="data:image/png;base64,{png_b64(src_path)}">
      <p>{nota}</p>
    </div>'''


gago_novo = os.path.join(MOD, "gago.png")
traf_novo = os.path.join(MOD, "traficante.png")
juca_novo = os.path.join(MOD, "juca.png")

html = f"""<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<title>Prevvia — Overhaul dos NPCs (v1.2.45)</title>
<style>
  body{{margin:0;background:#0d1117;color:#e6edf3;font-family:"Segoe UI",system-ui,sans-serif;padding:24px}}
  h1{{font-size:20px;margin:0 0 4px}} h1 small{{color:#8b949e;font-weight:400}}
  h2{{font-size:16px;margin:28px 0 10px;color:#58a6ff;border-bottom:1px solid #21262d;padding-bottom:6px}}
  .sub{{color:#8b949e;font-size:13px;margin-bottom:18px}}
  .grade{{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:14px}}
  .card{{background:#161b22;border:1px solid #30363d;border-radius:10px;padding:14px}}
  .card img{{width:100%;image-rendering:pixelated;background:#0d1117;border-radius:6px;margin:8px 0}}
  .card h3{{font-size:14px;margin:0 0 6px}}
  .card p{{font-size:12px;color:#8b949e;margin:4px 0 0}}
  .ok{{color:#3fb950;font-weight:600}}
  .tag{{display:inline-block;background:#238636;color:#fff;border-radius:12px;padding:1px 8px;font-size:11px;margin-right:6px}}
  .tag.azul{{background:#1f6feb}}
  ul{{margin:6px 0 0 18px;padding:0;font-size:13px;color:#c9d1d9}}
  li{{margin:3px 0}}
</style>
</head>
<body>
<h1>Overhaul dos NPCs <small>v1.2.45 — skins, menus, 24h e o Gago fantasma</small></h1>
<div class="sub">Prvia — <b>sem build</b>, como combina. Tudo aguardando seu ok.</div>

<h2>1. Skins: buracos transparentes — ANTES x DEPOIS (mesma textura, zoom 4x)</h2>
<div class="grade">
  {card_img(os.path.join(TMP, "gago_jar.png"), "Gago — ANTES (no JAR)", "Furo na frente do tronco (coluna de 24px transparente) — o corpo vazado do print.")}
  {card_img(gago_novo, "Gago — DEPOIS", "Autocura de pixel: vizinho mais prximo preenche o buraco com o mesmo tecido. Auditoria de alpha no gerador: 0 buraco.")}
  {card_img(os.path.join(TMP, "traficante_jar.png"), "Traficante — ANTES (no JAR)", "Membros com 306px furos: converso antiga lia o layout errado.")}
  {card_img(traf_novo, "Traficante — DEPOIS", "Membros completos, corrente no peito e tocado - o corpo de player de verdade.")}
  {card_img(juca_novo, "Juça — DEPOIS", "Mesma autocura aplicada (24px no tronco). Camisa do Matanza + Camel intactos.")}
</div>

<h2>2. Cardpio do Gago — letra limpa + aba Exclusivos</h2>
<div class="grade">
  <div class="card">
    <h3>O que muda</h3>
    <ul>
      <li><span class="tag">LETRA</span> Todo texto da tela <b>sem sombra</b> (a sombra de 1px era o "negrito" que incomodava). Letra limpa, sem peso falso.</li>
      <li><span class="tag">ABA NOVA</span> <b>★ Exclusivos</b> saiu da lista de compra — abas agora so Compra / Venda / Exclusivos. A lista de compra fica limpa de "no pode comprar isso ainda".</li>
      <li><span class="tag">24H</span> O cardpio agora diz "atende 24h" — e  verdade (item 3).</li>
    </ul>
  </div>
</div>

<h2>3. Mercado 24h — horrio de atendimento EXTERMINADO</h2>
<div class="grade">
  <div class="card">
    <h3>O que sai do jogo</h3>
    <ul>
      <li><span class="tag azu">VIA</span> A virada 00:00/07:00 no muda mais nada: placa no alterna ABERTO/FECHADO (fica no nome, como voc pediu), no h "porta fechada" nem Gago cochilando no expediente.</li>
      <li>O Gago de save antigo que nasceu com a flag "fechado" atende normal — o cdigo converte pra 24h na primeira checagem.</li>
      <li>O relgio da action bar continua (da hora), s sem o status de aberto/fechado.</li>
    </ul>
  </div>
</div>

<h2>4. O PONTO do Traficante — overhaul completo do comrcio dele</h2>
<div class="grade">
  <div class="card">
    <h3>Tela prpria dele (nunca mais a UI de aldeio)</h3>
    <ul>
      <li><b>Aba Estoque:</b> 7 produtos com preo do dia, estoque dirio que repõe s 07:00. Fila = desconto: cada fregus simultneo d 5% de desconto (mx 10%).</li>
      <li><b>Aba Fiado:</b> "Me empresta a?" — R$ 50 na hora. Nvel 0: SEM LANÇAMENTO (10% de juros). Quitou 2x: na confiana (30%). Quitou 4x: na palavra (0%). A dvida aparece no rodap e persiste.</li>
      <li><b>Aba Vender colheita:</b> ele compra lote na hora (90% do preo do dia).</li>
      <li><b>Fidelidade da rua:</b> a cada 5 compras no dia, +5% de desconto permanente (mx 15%).</li>
      <li><b>Lanamento do dia:</b> 40% de chance de um produto sair metade do preo, 1 por fregus.</li>
    </ul>
  </div>
</div>

<h2>5. O Gago fantasma — nunca mais</h2>
<div class="grade">
  <div class="card">
    <h3>3 causas raiz, 3 consertos</h3>
    <ul>
      <li><b>Teleporte de planto:</b> a cada ciclo ele era teleportado pro posto (e nascia bloco no caminho = preso e invisvel). Agora o gerenciador conserta <b>onde ele est</b> — desobstrui o corpo e trava o posto ali mesmo.</li>
      <li><b>Gagos excedentes:</b> ovo duplicado/ corrida de spawn = 2 Gagos, 1 fica preso e invisvel mas fala no chat. O gerenciador agora d de baixa nos excedentes.</li>
      <li><b>Entalado de save:</b> a checagem "preso em bloco" agora teleporte 0 — s desobstrui (mesma garantia da 1.2.30).</li>
    </ul>
  </div>
</div>

<div class="card" style="margin-top:24px">
  <h3>Resumo tcnico</h3>
  <ul>
    <li>Compilao main + gametest verde. Teste do porto atualizado pra 24h (agora espera ATENDIMENTO, no recusa).</li>
    <li>9 skins regeradas com auditoria de alpha: 0 px transparente onde o modelo amostra.</li>
    <li>Novos payloads: abrir_ponto / destranco / fiado / diazinho (todos server-authoritative).</li>
    <li>Dvida do fiado persistida em <b>intoxicantes_fiado.json</b> no mundo.</li>
    <li>Verso proposta: <b>1.2.45</b> (a 1.2.44 j existe no jar do brao esquerdo).</li>
  </ul>
</div>
</body>
</html>
"""
os.makedirs(os.path.dirname(SAIDA), exist_ok=True)
with open(SAIDA, "w", encoding="utf-8") as f:
    f.write(html)
print("previa gerada:", SAIDA)
