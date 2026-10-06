"""Renderiza o modelo 3D da escopeta pra pre-visualizacao.

Le o modelo JSON, aplica as rotacoes de elemento, e desenha 4 vistas separadas:
isometrica, lateral (sul), topo (norte pra cima) e simulacao do icone do GUI.
Salva escopeta.html (com tudo embutido) e um PNG por vista em build/preview/.
Uso: python tools/preview_escopeta.py  (a partir da raiz do projeto do mod)
"""
import base64
import io
import json
import math
import os

from PIL import Image, ImageDraw

ASSETS = os.path.join("src", "main", "resources", "assets", "intoxicantes")
PREVIEW = os.path.join("build", "preview")

with open(os.path.join(ASSETS, "models", "item", "escopeta.json"), encoding="utf-8") as f:
    modelo = json.load(f)

with open(os.path.join(ASSETS, "textures", "item", "escopeta.png"), "rb") as f:
    tex_b64 = base64.b64encode(f.read()).decode()

tex = Image.open(io.BytesIO(base64.b64decode(tex_b64))).convert("RGBA")


def uv_media(uv_box):
    # UV de model e NORMALIZADO 0-16: converte pra pixels da textura
    x0, y0, x1, y1 = uv_box
    sx = tex.width / 16.0
    sy = tex.height / 16.0
    regiao = tex.crop((int(x0 * sx), int(y0 * sy), int(x1 * sx), int(y1 * sy))).resize((1, 1), Image.BILINEAR)
    r, g, b, a = regiao.getpixel((0, 0))
    return (r, g, b, a)


# ------------------------------------------------------------------ geometria
def rot_ponto(p, rot):
    if not rot:
        return p
    ox, oy, oz = rot["origin"]
    ang = math.radians(rot["angle"])
    c, s = math.cos(ang), math.sin(ang)
    eixo = rot["axis"]
    x, y, z = p[0] - ox, p[1] - oy, p[2] - oz
    if eixo == "x":
        y, z = y * c - z * s, y * s + z * c
    elif eixo == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:  # z
        x, y = x * c - y * s, x * s + y * c
    return (x + ox, y + oy, z + oz)


def cantos(el):
    """8 cantos do elemento com rotacao de elemento aplicada."""
    x0, y0, z0 = el["from"]
    x1, y1, z1 = el["to"]
    pts = [(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
    rot = el.get("rotation")
    if rot:
        pts = [rot_ponto(p, rot) for p in pts]
    return pts  # indice = xi*4 + yi*2 + zi


QUADS = {
    "up":    (2, 3, 7, 6),
    "down":  (0, 1, 5, 4),
    "north": (0, 4, 6, 2),
    "south": (1, 5, 7, 3),
    "east":  (4, 5, 7, 6),
    "west":  (0, 1, 3, 2),
}
SOMBRA = {"up": 1.0, "down": 0.55, "north": 0.82, "south": 0.62, "east": 0.72, "west": 0.66}


def faces_com_cor():
    saida = []
    for el in modelo["elements"]:
        pts = cantos(el)
        for nome_face, dados in el["faces"].items():
            quad = [pts[i] for i in QUADS[nome_face]]
            cor = uv_media(dados["uv"])
            fator = SOMBRA[nome_face]
            cor_face = tuple(min(255, int(c * fator)) for c in cor[:3]) + (255,)
            cx = sum(p[0] for p in quad) / 4
            cy = sum(p[1] for p in quad) / 4
            cz = sum(p[2] for p in quad) / 4
            saida.append((nome_face, cor_face, quad, (cx, cy, cz)))
    return saida


FACES = faces_com_cor()


# ------------------------------------------------------------------ motor de vista
def projetar_faces(funcao_xy, chave_prof):
    """Projeta cada face e devolve (profundidade, poligono2d, cor) ordenado
    do mais distante pro mais proximo (painter's algorithm)."""
    poligonos = []
    for nome, cor, quad, centro in FACES:
        prof = chave_prof(centro)
        pts = [funcao_xy(p) for p in quad]
        poligonos.append((prof, pts, cor))
    poligonos.sort(key=lambda p: p[0])
    return poligonos


def renderizar_vista(largura, altura, poligonos, fundo=(56, 54, 64, 255), contorno=None):
    """Desenha os poligonos num canvas novo, ajustando pra caber com margem."""
    todos = [pt for _, pts, _ in poligonos for pt in pts]
    if not todos:
        return Image.new("RGBA", (largura, altura), fundo)
    xs = [p[0] for p in todos]
    ys = [p[1] for p in todos]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    larg_conteudo, alt_conteudo = max(x1 - x0, 0.01), max(y1 - y0, 0.01)
    margem = 24
    esc = min((largura - 2 * margem) / larg_conteudo, (altura - 2 * margem) / alt_conteudo)
    ox = (largura - larg_conteudo * esc) / 2 - x0 * esc
    oy = (altura - alt_conteudo * esc) / 2 - y0 * esc
    img = Image.new("RGBA", (largura, altura), fundo)
    d = ImageDraw.Draw(img)
    for _, pts, cor in poligonos:
        d.polygon([(ox + p[0] * esc, oy + p[1] * esc) for p in pts], fill=cor,
                  outline=contorno)
    return img


def vista_iso():
    """Isometrica: +x pra direita-baixo, +y pra cima, +z pra esquerda-baixo."""
    def xy(p):
        return (p[0] - p[2], -p[1] * 0.72 + (p[0] + p[2]) * 0.25)
    pol = projetar_faces(xy, lambda c: c[0] * 1.0 + c[1] * 0.8 - c[2] * 1.0)
    return renderizar_vista(560, 360, pol, contorno=(16, 14, 18, 140))


def vista_lateral():
    """Vista de lado (olhando a face sul): x pra direita, y pra cima."""
    def xy(p):
        return (p[0], -p[1])
    pol = projetar_faces(xy, lambda c: c[2])
    return renderizar_vista(360, 200, pol, contorno=(16, 14, 18, 140))


def vista_topo():
    """Vista de cima: x pra direita, z pra baixo (norte pra cima)."""
    def xy(p):
        return (p[0], p[2])
    pol = projetar_faces(xy, lambda c: c[1])
    return renderizar_vista(360, 200, pol, contorno=(16, 14, 18, 140))


def rot_xyz(p, rx, ry, rz):
    """Aplica Rx depois Ry depois Rz (aproximacao da ordem do display do MC)."""
    x, y, z = p
    c, s = math.cos(math.radians(rx)), math.sin(math.radians(rx))
    y, z = y * c - z * s, y * s + z * c
    c, s = math.cos(math.radians(ry)), math.sin(math.radians(ry))
    x, z = x * c + z * s, -x * s + z * c
    c, s = math.cos(math.radians(rz)), math.sin(math.radians(rz))
    x, y = x * c - y * s, x * s + y * c
    return (x, y, z)


def vista_gui(largura=220, altura=220):
    """Simula o icone do inventario com a rotacao do display 'gui'."""
    gui = modelo["display"]["gui"]
    rx, ry, rz = gui["rotation"]
    escala_gui = gui.get("scale", [1, 1, 1])[0]

    todos = [p for _, _, quad, _ in FACES for p in quad]
    centro = (sum(p[0] for p in todos) / len(todos),
              sum(p[1] for p in todos) / len(todos),
              sum(p[2] for p in todos) / len(todos))

    luz = (0.25, 0.85, 0.45)
    ln = math.sqrt(sum(c * c for c in luz))
    luz = tuple(c / ln for c in luz)

    poligonos = []
    for nome, cor, quad, _ in FACES:
        pts3 = [rot_xyz((p[0] - centro[0], p[1] - centro[1], p[2] - centro[2]),
                        rx, ry, rz) for p in quad]
        (ax, ay, az), (bx, by, bz), (cxx, cyy, czz) = pts3[0], pts3[1], pts3[2]
        u = (bx - ax, by - ay, bz - az)
        v = (cxx - ax, cyy - ay, czz - az)
        n = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
        nn = math.sqrt(sum(c * c for c in n)) or 1.0
        n = tuple(c / nn for c in n)
        profundidade = sum(p[2] for p in pts3) / 4
        fator = 0.55 + 0.45 * max(0.0, n[0] * luz[0] + n[1] * luz[1] + n[2] * luz[2])
        cor_f = tuple(min(255, int(c * fator)) for c in cor[:3]) + (255,)
        poligonos.append((profundidade, pts3, cor_f))
    poligonos.sort(key=lambda p: p[0])

    saida = []
    for prof, pts3, cor_f in poligonos:
        saida.append((prof, [(p[0] * escala_gui, -p[1] * escala_gui) for p in pts3], cor_f))
    return renderizar_vista(largura, altura, saida, contorno=(16, 14, 18, 160))


# ------------------------------------------------------------------ composicao
os.makedirs(PREVIEW, exist_ok=True)

img_iso = vista_iso()
img_lat = vista_lateral()
img_top = vista_topo()
img_gui = vista_gui()

comp = Image.new("RGBA", (940, 640), (34, 32, 38, 255))
d = ImageDraw.Draw(comp)
comp.paste(img_iso, (10, 24))
comp.paste(img_lat, (570, 30))
comp.paste(img_top, (570, 260))

# moldura do icone 16x16 simulado
d.rectangle([14, 390, 256, 626], outline=(70, 68, 80, 255), width=2)
comp.paste(img_gui, (24, 398))

d.text((16, 8), "ESCOPETA DO GAGO - modelo 3D", fill=(240, 235, 220))
d.text((570, 12), "lateral (sul)", fill=(150, 146, 138))
d.text((570, 242), "topo (norte pra cima)", fill=(150, 146, 138))
d.text((20, 372), "icone do inventario (simulado)", fill=(150, 146, 138))
d.text((300, 630), f"{len(modelo['elements'])} elementos | coronha 22,5 graus | aros de latao",
       fill=(150, 146, 138))

comp.save(os.path.join(PREVIEW, "escopeta.png"))
img_iso.save(os.path.join(PREVIEW, "escopeta_iso.png"))
img_lat.save(os.path.join(PREVIEW, "escopeta_lateral.png"))
img_top.save(os.path.join(PREVIEW, "escopeta_topo.png"))
img_gui.save(os.path.join(PREVIEW, "escopeta_gui.png"))

buf = io.BytesIO()
comp.save(buf, format="PNG")
img_b64 = base64.b64encode(buf.getvalue()).decode()

html = f"""<!DOCTYPE html>
<html lang="pt-br">
<head>
<meta charset="utf-8">
<style>
body {{ background:#1e1c24; color:#e8e4da; font-family: monospace; margin:0; padding:20px; }}
h1 {{ font-size:18px; }}
img {{ max-width:100%; image-rendering: pixelated; border:1px solid #3a3844; }}
</style>
</head>
<body>
<h1>Escopeta do Gago — prévia do modelo 3D</h1>
<p>O Gago carrega essa beleza na mão o tempo todo (e usa quando você chama ele de gago kkkk).
Modelo JSON com {len(modelo['elements'])} elementos: coronha em dois estágios com inclinação de 22,5°
e soleira de couro, culatra com porta de ejeção, martelo, cano com aros de latão,
tubo de munição, bomba estriada, gatilho, guarda-mato, massa de mira e alvo de mira.</p>
<img src="data:image/png;base64,{img_b64}" alt="escopeta">
</body>
</html>
"""

caminho = os.path.join(PREVIEW, "escopeta.html")
with open(caminho, "w", encoding="utf-8") as f:
    f.write(html)
print("Previa salva em build/preview/escopeta.html (+ 5 PNGs)")
