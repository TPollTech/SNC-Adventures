"""Previa HTML das skins dos NPCs (v1.2.43).

Para cada NPC: NO JOGO ANTES (textura steve do jar 1.2.41, sem sombra) x
NO JOGO AGORA (com o sombreamento suave dos membros) x textura nova 64x64
x ARTE ORIGINAL (layout velho do backup).
Uso: python tools/gen_preview_skins.py  (a partir de intoxicantes-mod/)
"""
import base64
import io
import os
import zipfile

from PIL import Image

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOD = os.path.join(RAIZ, "src", "main", "resources", "assets", "intoxicantes", "textures", "entity")
BK = os.path.join(RAIZ, "backups", "20260923-skins-player")
SAIDA = os.path.join(RAIZ, "build", "preview-skins.html")

SKINS = [
    ("gago", "Gago (dono do Esquinão)", "pack"),
    ("gago_sertao", "Gago — fantasia Sertão", "pack"),
    ("gago_mata", "Gago — fantasia Mata", "pack"),
    ("gago_cerrado", "Gago — fantasia Cerrado", "pack"),
    ("gago_sul", "Gago — fantasia Sul", "pack"),
    ("gago_serra", "Gago — fantasia Serra", "pack"),
    ("gago_brejo", "Gago — fantasia Brejo", "pack"),
    ("traficante", "Traficante", "pack"),
    ("juca", "Juça (o parça)", "mod"),
]


JAR_ANTIGO = os.path.join(RAIZ, "dist", "intoxicantes-1.2.41.jar")


def abrir_jar(nome):
    """Skin steve SEM sombreamento, de dentro do jar da v1.2.41."""
    with zipfile.ZipFile(JAR_ANTIGO) as z:
        return Image.open(io.BytesIO(
            z.read("assets/intoxicantes/textures/entity/" + nome + ".png"))).convert("RGBA")


def b64(img):
    from io import BytesIO
    buf = BytesIO()
    img.save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def frente_steve(img, esc=10):
    """Monta a visao frontal do player a partir do layout steve 64x64."""
    head = img.crop((8, 8, 16, 16))       # rosto
    hat = img.crop((40, 8, 48, 16))       # camada hat (frente)
    body = img.crop((20, 20, 28, 32))     # torso frente
    bracoD = img.crop((44, 20, 48, 32))   # braco direito frente
    bracoE = img.crop((36, 52, 40, 64))   # braco esquerdo frente
    pernaD = img.crop((4, 20, 8, 32))     # perna direita frente
    pernaE = img.crop((20, 52, 24, 64))   # perna esquerda frente

    W, H = 24, 32
    tela = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    tela.alpha_composite(head, (8, 0))
    tela.alpha_composite(hat, (8, 0))
    tela.alpha_composite(body, (8, 8))
    tela.alpha_composite(bracoD, (4, 8))
    tela.alpha_composite(bracoE, (12, 8))
    tela.alpha_composite(pernaD, (8, 20))
    tela.alpha_composite(pernaE, (12, 20))
    fundo = Image.new("RGBA", tela.size, (34, 38, 48, 255))
    fundo.alpha_composite(tela)
    return fundo.resize((W * esc, H * esc), Image.NEAREST)


def cartao(nome, titulo, base_backup):
    conv = Image.open(os.path.join(MOD, nome + ".png")).convert("RGBA")
    sem_sombra = abrir_jar(nome)
    src_path = os.path.join(BK, base_backup, nome + ".png")
    src = Image.open(src_path).convert("RGBA")
    antes = src.resize((src.width * 2, src.height * 2), Image.NEAREST) if src.width <= 128 \
        else src.resize((256, 256), Image.NEAREST)
    return f"""
    <div class="card">
      <h3>{titulo}</h3>
      <div class="row">
        <figure><figcaption>NO JOGO ANTES (v1.2.41, sem sombra)</figcaption>
          <img src="{b64(frente_steve(sem_sombra))}"></figure>
        <figure class="destaque"><figcaption>NO JOGO AGORA (sombra suave nos membros)</figcaption>
          <img src="{b64(frente_steve(conv))}"></figure>
        <figure><figcaption>TEXTURA NOVA (steve 64x64)</figcaption>
          <img src="{b64(conv.resize((320, 320), Image.NEAREST))}"></figure>
        <figure><figcaption>ARTE ORIGINAL (layout velho {src.width}px)</figcaption>
          <img src="{b64(antes)}"></figure>
      </div>
    </div>"""


def main():
    cartoes = "".join(cartao(n, t, b) for (n, t, b) in SKINS)
    html = f"""<!DOCTYPE html>
<html lang="pt-br"><head><meta charset="utf-8">
<title>Skins — corpo de player (v1.2.40)</title>
<style>
  body {{ background:#14161c; color:#e8e6e0; font-family:Segoe UI, sans-serif; margin:24px; }}
  h1 {{ color:#8fd18f; }} p.sub {{ color:#9aa; }}
  .card {{ background:#1d2029; border-radius:12px; padding:16px 20px; margin:18px 0;
          border:1px solid #2c3140; }}
  .row {{ display:flex; gap:28px; flex-wrap:wrap; align-items:flex-start; }}
  figure {{ margin:0; text-align:center; }}
  figcaption {{ font-size:12px; color:#9aa3b2; margin-bottom:6px; }}
  img {{ image-rendering:pixelated; border-radius:6px; background:#11131a; }}
  .destaque figcaption {{ color:#8fd18f; font-weight:600; }}
</style></head><body>
<h1>Skins com corpo de player — v1.2.43 (sombra suave)</h1>
<p class="sub">Novidade desta versão: braços e pernas ganharam sombreamento suave
(luz de cima + gradiente ombro→mão / quadril→pé, aproveitando as 12 linhas do
formato steve). Rosto e tronco ficaram intactos. Também entra aqui o cartucho
da 12 novo e o .38 na mão animada do traficante.</p>
{cartoes}
</body></html>"""
    os.makedirs(os.path.dirname(SAIDA), exist_ok=True)
    with open(SAIDA, "w", encoding="utf-8") as f:
        f.write(html)
    print("Previa:", SAIDA)


if __name__ == "__main__":
    main()
