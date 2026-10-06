"""Comparação ANTES × DEPOIS do revólver .38 (regra 14 do padrão de armas 3D).

Renderiza dois pares (modelo JSON + textura PNG):
  ANTES : backups/20260923-armas-padrao/revolver.json + revolver.png
  DEPOIS: src/main/resources/assets/intoxicantes/models/item/revolver.json + revolver.png

Vistas por arma: isométrica, lateral, ícone de inventário (display.gui) e
simulação de primeira pessoa (display.firstperson_righthand).
Salva build/preview/revolver_antes_depois.html com tudo embutido.

Uso: python tools/preview_revolver.py  (a partir da raiz do projeto do mod)
"""
import base64
import io
import json
import math
import os

from PIL import Image, ImageDraw

ASSETS = os.path.join("src", "main", "resources", "assets", "intoxicantes")
PREVIEW = os.path.join("build", "preview")
ANTES_JSON = os.path.join("backups", "20260923-armas-padrao", "revolver.json")
ANTES_TEX = os.path.join("backups", "20260923-armas-padrao", "revolver.png")
ARTE_MANUAL = os.path.join("backups", "texturas-manuais", "revolver_manual.png")

QUADS = {
    "up": (2, 3, 7, 6), "down": (0, 1, 5, 4),
    "north": (0, 4, 6, 2), "south": (1, 5, 7, 3),
    "east": (4, 5, 7, 6), "west": (0, 1, 3, 2),
}
SOMBRA = {"up": 1.0, "down": 0.55, "north": 0.82, "south": 0.62, "east": 0.72, "west": 0.66}


def carregar_faces(json_path, tex_path):
    with open(json_path, encoding="utf-8") as f:
        modelo = json.load(f)
    tex = Image.open(tex_path).convert("RGBA")

    def uv_media(uv_box):
        x0, y0, x1, y1 = uv_box
        sx, sy = tex.width / 16.0, tex.height / 16.0
        reg = tex.crop((int(x0 * sx), int(y0 * sy), max(int(x1 * sx), int(x0 * sx) + 1),
                        max(int(y1 * sy), int(y0 * sy) + 1))).resize((1, 1), Image.BILINEAR)
        r, g, b, a = reg.getpixel((0, 0))
        if a < 20:
            return (150, 120, 80)  # região transparente: tom de madeira pra não sumir
        return (r, g, b)

    def rot_ponto(p, rot):
        ox, oy, oz = rot["origin"]
        ang = math.radians(rot["angle"])
        c, s = math.cos(ang), math.sin(ang)
        eixo = rot["axis"]
        x, y, z = p[0] - ox, p[1] - oy, p[2] - oz
        if eixo == "x":
            y, z = y * c - z * s, y * s + z * c
        elif eixo == "y":
            x, z = x * c + z * s, -x * s + z * c
        else:
            x, y = x * c - y * s, x * s + y * c
        return (x + ox, y + oy, z + oz)

    faces = []
    for el in modelo["elements"]:
        x0, y0, z0 = el["from"]
        x1, y1, z1 = el["to"]
        pts = [(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
        if "rotation" in el:
            pts = [rot_ponto(p, el["rotation"]) for p in pts]
        for nome, dados in el["faces"].items():
            quad = [pts[i] for i in QUADS[nome]]
            cor = uv_media(dados["uv"])
            fator = SOMBRA[nome]
            cor_f = tuple(min(255, int(c * fator)) for c in cor[:3]) + (255,)
            cx = sum(p[0] for p in quad) / 4
            cy = sum(p[1] for p in quad) / 4
            cz = sum(p[2] for p in quad) / 4
            faces.append((cor_f, quad, (cx, cy, cz)))
    return modelo, faces


def rot_xyz(p, rx, ry, rz):
    x, y, z = p
    c, s = math.cos(math.radians(rx)), math.sin(math.radians(rx))
    y, z = y * c - z * s, y * s + z * c
    c, s = math.cos(math.radians(ry)), math.sin(math.radians(ry))
    x, z = x * c + z * s, -x * s + z * c
    c, s = math.cos(math.radians(rz)), math.sin(math.radians(rz))
    x, y = x * c - y * s, x * s + y * c
    return (x, y, z)


def renderizar(faces, funcao_xy, chave_prof, largura, altura, contorno=(16, 14, 18, 140)):
    pols = []
    for cor, quad, centro in faces:
        prof = chave_prof(centro)
        pts = [funcao_xy(p) for p in quad]
        pols.append((prof, pts, cor))
    pols.sort(key=lambda p: p[0])
    todos = [pt for _, pts, _ in pols for pt in pts]
    xs = [p[0] for p in todos]
    ys = [p[1] for p in todos]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    larg_c, alt_c = max(x1 - x0, 0.01), max(y1 - y0, 0.01)
    margem = 18
    esc = min((largura - 2 * margem) / larg_c, (altura - 2 * margem) / alt_c)
    ox = (largura - larg_c * esc) / 2 - x0 * esc
    oy = (altura - alt_c * esc) / 2 - y0 * esc
    img = Image.new("RGBA", (largura, altura), (56, 54, 64, 255))
    d = ImageDraw.Draw(img)
    for _, pts, cor in pols:
        d.polygon([(ox + p[0] * esc, oy + p[1] * esc) for p in pts], fill=cor, outline=contorno)
    return img


def vistas(modelo, faces):
    saida = {}

    def xy_iso(p):
        return (p[0] - p[2], -p[1] * 0.72 + (p[0] + p[2]) * 0.25)

    saida["iso"] = renderizar(faces, xy_iso, lambda c: c[0] + c[1] * 0.8 - c[2], 430, 300)

    def xy_lat(p):
        return (p[0], -p[1])

    saida["lateral"] = renderizar(faces, xy_lat, lambda c: c[2], 430, 300)

    # GUI: rotaciona pelo display.gui e sombreia pela normal (gui_light: side)
    gui = modelo["display"]["gui"]
    rx, ry, rz = gui["rotation"]
    esc_gui = gui.get("scale", [1, 1, 1])[0]
    todos = [p for _, quad, _ in faces for p in quad]
    centro = tuple(sum(p[i] for p in todos) / len(todos) for i in range(3))
    luz = (0.25, 0.85, 0.45)
    ln = math.sqrt(sum(c * c for c in luz))
    luz = tuple(c / ln for c in luz)
    pols = []
    for cor, quad, _ in faces:
        pts3 = [rot_xyz((p[0] - centro[0], p[1] - centro[1], p[2] - centro[2]), rx, ry, rz)
                for p in quad]
        (ax, ay, az), (bx, by, bz), (cx, cy, cz) = pts3[0], pts3[1], pts3[2]
        u = (bx - ax, by - ay, bz - az)
        v = (cx - ax, cy - ay, cz - az)
        n = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
        nn = math.sqrt(sum(c * c for c in n)) or 1.0
        fator = 0.55 + 0.45 * max(0.0, (n[0] * luz[0] + n[1] * luz[1] + n[2] * luz[2]) / nn)
        cor_f = tuple(min(255, int(c * fator)) for c in cor[:3]) + (255,)
        prof = sum(p[2] for p in pts3) / 4
        pols.append((prof, [(p[0] * esc_gui, -p[1] * esc_gui) for p in pts3], cor_f))
    pols.sort(key=lambda p: p[0])
    todos2 = [pt for _, pts, _ in pols for pt in pts]
    xs = [p[0] for p in todos2]
    ys = [p[1] for p in todos2]
    larg_c, alt_c = max(max(xs) - min(xs), 0.01), max(max(ys) - min(ys), 0.01)
    esc2 = min((188 - 16) / larg_c, (188 - 16) / alt_c)
    ox = (188 - larg_c * esc2) / 2 - min(xs) * esc2
    oy = (188 - alt_c * esc2) / 2 - min(ys) * esc2
    img = Image.new("RGBA", (200, 200), (70, 68, 80, 255))
    d = ImageDraw.Draw(img)
    for _, pts, cor in pols:
        d.polygon([(ox + p[0] * esc2, oy + p[1] * esc2) for p in pts], fill=cor,
                  outline=(16, 14, 18, 160))
    saida["gui"] = img

    # primeira pessoa: rotação + translação + escala do display, vista por trás da arma
    fp = modelo["display"]["firstperson_righthand"]
    rx, ry, rz = fp["rotation"]
    tr = fp.get("translation", [0, 0, 0])
    esc_fp = fp.get("scale", [1, 1, 1])[0]
    centro = tuple(sum(p[i] for p in todos) / len(todos) for i in range(3))

    def proj_fp(p):
        q = rot_xyz((p[0] - centro[0], p[1] - centro[1], p[2] - centro[2]), rx, ry, rz)
        return (q[0] * esc_fp, -q[1] * esc_fp)

    def prof_fp(p):
        q = rot_xyz((p[0] - centro[0], p[1] - centro[1], p[2] - centro[2]), rx, ry, rz)
        return q[2] + tr[2]

    saida["fp"] = renderizar(faces, proj_fp, prof_fp, 430, 300)
    return saida


def b64(img):
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return base64.b64encode(buf.getvalue()).decode()


def bloco(nome, v):
    return f"""
    <div class="arma">
      <h2>{nome}</h2>
      <div class="grelha">
        <figure><img src="data:image/png;base64,{b64(v['iso'])}"><figcaption>isométrica</figcaption></figure>
        <figure><img src="data:image/png;base64,{b64(v['lateral'])}"><figcaption>lateral (perfil)</figcaption></figure>
        <figure><img src="data:image/png;base64,{b64(v['fp'])}"><figcaption>primeira pessoa (simulada)</figcaption></figure>
        <figure><img src="data:image/png;base64,{b64(v['gui'])}" class="pixel"><figcaption>ícone do inventário</figcaption></figure>
      </div>
    </div>"""


# ------------------------------------------------------------------ renderiza os dois
modelo_a, faces_a = carregar_faces(ANTES_JSON, ANTES_TEX)
vistas_a = vistas(modelo_a, faces_a)
modelo_d, faces_d = carregar_faces(
    os.path.join(ASSETS, "models", "item", "revolver.json"),
    os.path.join(ASSETS, "textures", "item", "revolver.png"))
vistas_d = vistas(modelo_d, faces_d)

arte_manual = ""
if os.path.exists(ARTE_MANUAL):
    arte = Image.open(ARTE_MANUAL).convert("RGBA").resize((128, 128), Image.NEAREST)
    arte_manual = f"""
    <div class="nota">
      <h3>Sua arte manual (256×256)</h3>
      <img class="pixel" src="data:image/png;base64,{b64(arte)}" width="128" height="128">
      <p>É uma ilustração plana (perfil lateral), ótima como ícone, mas não dá pra envolver
      cubos 3D com ela. Foi preservada em <code>backups/texturas-manuais/revolver_manual.png</code> —
      se quiser, dá pra restaurá-la como ícone de inventário e manter o atlas só pro 3D.</p>
    </div>"""

html = f"""<!DOCTYPE html>
<html lang="pt-br">
<head>
<meta charset="utf-8">
<title>Revólver .38 — ANTES × DEPOIS</title>
<style>
  body{{margin:0;background:#14161c;color:#e6edf3;font-family:"Segoe UI",system-ui,sans-serif;padding:22px}}
  h1{{font-size:20px;margin:0 0 4px}} h1 small{{color:#8b949e;font-weight:400}}
  .sub{{color:#8b949e;font-size:13px;margin-bottom:18px}}
  .arma{{background:#1c2029;border:1px solid #2c3442;border-radius:12px;padding:14px 16px;margin-bottom:16px}}
  .arma h2{{margin:0 0 10px;font-size:15px}}
  .antes h2{{color:#f0883e}} .depois h2{{color:#3fb950}}
  .grelha{{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:10px}}
  figure{{margin:0;background:#11141a;border-radius:8px;padding:8px}}
  figcaption{{color:#8b949e;font-size:11px;text-align:center;margin-top:4px}}
  img{{max-width:100%;image-rendering:auto;border-radius:4px}}
  img.pixel{{image-rendering:pixelated}}
  .nota{{background:#1c2029;border:1px solid #2c3442;border-radius:12px;padding:14px 16px;margin-bottom:16px;font-size:13px;color:#c9d1d9}}
  .nota h3{{margin:0 0 8px;font-size:14px}}
  ul{{margin:8px 0 0 18px;padding:0}} li{{margin:3px 0}}
</style>
</head>
<body>
<h1>Revólver .38 — recriado pelo padrão de armas 3D <small>(antes × depois, regra 14)</small></h1>
<p class="sub">ANTES = {len(modelo_a['elements'])} elements, textura 32×32, roll de −50° em primeira pessoa ·
DEPOIS = {len(modelo_d['elements'])} elements, atlas 128×128, primeira pessoa alinhada</p>
{bloco('ANTES — modelo antigo', vistas_a)}
{bloco('DEPOIS — recriado (proposta)', vistas_d)}
<div class="nota">
  <h3>O que mudou (mapa das regras)</h3>
  <ul>
    <li><b>Proporções (r. 2–4):</b> comprimento total ~17 unidades; cabo com rake de −22,5°
        (empunhadura natural, não gravata); cano de 4" sob top strap de aço.</li>
    <li><b>Tambor de verdade (r. 6):</b> latão com 2 aros, 3 câmaras visíveis na face traseira,
        4 flutes de recarga e pino do tambor.</li>
    <li><b>Primeira pessoa (r. 10):</b> roll de −50° → −6°: mira pra frente, alinhada com a tela;
        mão esquerda espelhada.</li>
    <li><b>GUI (r. 12):</b> modelo grande; só o transform reduz (0.62).</li>
    <li><b>Textura (r. 8):</b> atlas 128×128 com aço azulado, aço escuro/claro, latão, nogueira
        3 tons, borracha e alvo escuro — materiais distintos por peça.</li>
    <li><b>Regra 16 corrigida:</b> o gerador velho remapeava from/to/origin mas deixava o eixo
        das rotações errado; agora o eixo converte junto (z→x).</li>
  </ul>
</div>
{arte_manual}
</body>
</html>"""

os.makedirs(PREVIEW, exist_ok=True)
caminho = os.path.join(PREVIEW, "revolver_antes_depois.html")
with open(caminho, "w", encoding="utf-8") as f:
    f.write(html)
print(f"Prévia ANTES×DEPOIS salva em {caminho}")
