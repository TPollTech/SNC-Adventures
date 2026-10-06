"""Gera uma pagina HTML de pre-visualizacao das texturas (bebidas antes/depois + farm novo).

Uso: python tools/gen_preview.py  (a partir da raiz do projeto do mod)
"""
import base64
import os

TEX = os.path.join("src", "main", "resources", "assets", "intoxicantes", "textures", "item")
BTEX = os.path.join("src", "main", "resources", "assets", "intoxicantes", "textures", "block")
OLD = os.path.join("build", "texture_backup")

NAMES = [
    ("cerveja", "Cerveja"), ("vinho", "Vinho"), ("cachaca", "Cachaça"),
    ("hidromel", "Hidromel"), ("rum", "Rum"), ("maconha_seda", "Maconha (Seda)"),
    ("baseado", "Baseado"), ("cocaina", "Pó Branco"), ("heroina", "Heroína"),
    ("lsd", "Selva de LSD"), ("po_estelar", "Pó Estelar"),
    ("cogumelo_xamanico", "Cogumelo Xamânico"), ("nevoa_do_deserto", "Névoa do Deserto"),
    ("raiz_de_sombra", "Raiz de Sombra"), ("cristal_de_euforia", "Cristal de Euforia"),
    ("extrato_cafeina", "Extrato de Cafeína"),
]

FARM_NOVOS = [
    ("semente_maconha", "Sementes de Maconha"), ("semente_lupulo", "Sementes de Lúpulo"),
    ("semente_uva", "Sementes de Uva"), ("semente_cafe", "Sementes de Café"),
    ("semente_papoula", "Sementes de Papoula"),
    ("lupulo", "Lúpulo Fresco"), ("uva", "Uva"), ("cafe_verde", "Café Verde"),
    ("cana_de_acucar", "Cana-de-açúcar"), ("lampada_uv", "Lâmpada UV"),
]

FARM_BLOCOS = [
    ("maconha_stage1", "Maconha — broto"), ("maconha_stage2", "Maconha — média"),
    ("maconha_stage3", "Maconha — alta"), ("maconha_stage4", "Maconha — madura"),
    ("maconha_stage4_ripe", "Maconha — UV máx 🌟"),
    ("lupulo_stage4", "Lúpulo — maduro"), ("lupulo_stage4_ripe", "Lúpulo — UV máx 🌟"),
    ("uva_stage4", "Uva — madura"), ("uva_stage4_ripe", "Uva — UV máx 🌟"),
    ("cafe_stage4", "Café — maduro"), ("cafe_stage4_ripe", "Café — UV máx 🌟"),
    ("papoula_stage4", "Papoula — madura"), ("papoula_stage4_ripe", "Papoula — UV máx 🌟"),
    ("lampada_uv", "Lâmpada UV — bloco (6 faces)"),
]


def b64(path):
    with open(path, "rb") as f:
        return "data:image/png;base64," + base64.b64encode(f.read()).decode()


def main():
    # ===== antes/depois das bebidas e itens
    cards = []
    for n, titulo in NAMES:
        old = b64(os.path.join(OLD, n + ".png"))
        new = b64(os.path.join(TEX, n + ".png"))
        cards.append(
            '<div class="card"><h3>' + titulo + "</h3>"
            '<div class="row">'
            '<figure><img src="' + old + '"><figcaption>antes</figcaption></figure>'
            '<div class="arrow">&#8594;</div>'
            '<figure><img class="new" src="' + new + '"><figcaption>depois</figcaption></figure>'
            "</div></div>"
        )

    # ===== itens novos de farm
    farm_cards = []
    for n, titulo in FARM_NOVOS:
        img = b64(os.path.join(TEX, n + ".png"))
        farm_cards.append(
            '<div class="card"><h3>' + titulo + "</h3>"
            '<div class="row single"><figure><img class="new" src="' + img + '"></figure></div></div>'
        )

    # ===== estagios das plantas (blocos)
    stage_cards = []
    for n, titulo in FARM_BLOCOS:
        img = b64(os.path.join(BTEX, n + ".png"))
        stage_cards.append(
            '<div class="card"><h3>' + titulo + "</h3>"
            '<div class="row single"><figure><img class="new big" src="' + img + '"></figure></div></div>'
        )

    html = """<!doctype html>
<html lang="pt-br"><head><meta charset="utf-8">
<title>Mod Intoxicantes — texturas</title>
<style>
  body { background:#15171e; color:#e8e6e3; font-family:system-ui,sans-serif; margin:14px; }
  h1 { font-size:1.15rem; margin:0 0 4px; }
  h2 { font-size:1rem; margin:20px 0 10px; color:#7fd88f; border-bottom:1px solid #2e3240; padding-bottom:6px; }
  p { color:#9a97a0; margin:0 0 12px; font-size:0.85rem; }
  .grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(250px,1fr)); gap:10px; }
  .card { background:#1e212b; border:1px solid #2e3240; border-radius:10px; padding:10px; }
  .card h3 { margin:0 0 8px; font-size:0.85rem; color:#c9c4b0; }
  .row { display:flex; align-items:center; justify-content:center; gap:8px; }
  figure { margin:0; text-align:center; }
  img { width:92px; height:92px; image-rendering:pixelated;
        background:
          linear-gradient(45deg,#262a35 25%,transparent 25%,transparent 75%,#262a35 75%),
          linear-gradient(45deg,#262a35 25%,#1a1d25 25%,#1a1d25 75%,#262a35 75%);
        background-size:16px 16px; background-position:0 0,8px 8px;
        border-radius:8px; }
  img.big { width:120px; height:120px; }
  img.new { outline:2px solid #4ade80; outline-offset:2px; }
  figcaption { margin-top:4px; font-size:0.7rem; color:#8a8790; }
  .arrow { font-size:1.1rem; color:#4ade80; }
  .single img { width:120px; height:120px; }
</style></head><body>
<h1>&#127867; Mod Intoxicantes &mdash; texturas</h1>
<p>Bebidas: antes &#8594; depois. Planta&ccedil;&otilde;es: tudo novo (moldura verde).</p>

<h2>&#127866; Bebidas &amp; itens &mdash; antes/depois</h2>
<div class="grid">__CARDS__</div>

<h2>&#127793; Planta&ccedil;&otilde;es &mdash; itens novos</h2>
<div class="grid">__FARM__</div>

<h2>&#127807; Est&aacute;gios das plantas (blocos)</h2>
<p>age 0&#8594;3 crescem; no age 4 amadurecem sob sol forte ou L&acirc;mpada UV at&eacute; o ponto (estrela).</p>
<div class="grid">__STAGES__</div>
</body></html>"""

    html = html.replace("__CARDS__", "".join(cards))
    html = html.replace("__FARM__", "".join(farm_cards))
    html = html.replace("__STAGES__", "".join(stage_cards))

    out_dir = os.path.join("build", "preview")
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, "textures.html")
    with open(out, "w", encoding="utf-8") as f:
        f.write(html)
    print("preview OK:", os.path.abspath(out))


if __name__ == "__main__":
    main()
