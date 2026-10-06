"""Sons do Jucelino "Juça" (v1.2.39): o riff do Matanza e a voz dele.

O riff: cowpunk/southern rock ORIGINAL (composto aqui, sem sampledar nada
de ninguem) — power chord em Mi com gallop de 8 notas, duas camadas de
guitarra levemente desafinadas (a sujeira do Matanza). Toca quando o Juça
se aproxima, tipo tema de personagem.

Sintetizado em Python puro (onda dente-de-serra + quadrada com envelope),
exportado em WAV e convertido pra OGG pelo ffmpeg — mesmo pipeline dos
outros sons do mod.
"""
import os
import subprocess
import wave
import struct
import math

OUT = os.path.join("src", "main", "resources", "assets", "intoxicantes", "sounds")
TMP = "build"
os.makedirs(OUT, exist_ok=True)
os.makedirs(TMP, exist_ok=True)

TAXA = 44100


def save_wav(nome, samples):
    caminho = os.path.join(TMP, nome + ".wav")
    with wave.open(caminho, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(TAXA)
        quadros = b"".join(
            struct.pack("<h", max(-32767, min(32767, int(s * 32767))))
            for s in samples)
        w.writeframes(quadros)
    ogg = os.path.join(OUT, nome + ".ogg")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", caminho,
                    "-c:a", "libvorbis", "-q:a", "4", ogg], check=True)
    print(f"  {nome}.ogg ({len(samples) / TAXA:.1f}s)")


def dente_de_serra(fase):
    return 2.0 * (fase - math.floor(fase + 0.5))


def quadrada(fase):
    return 1.0 if (fase % 1.0) < 0.5 else -1.0


def nota(freq, duracao, chug=False, volume=1.0):
    """Uma nota de guitarra: power chord (root+fifth+oitava) sujo.

    chug=True = palm mute (decay curtão, a mão na ponte).
    """
    n = int(TAXA * duracao)
    quinta = freq * 1.5
    oitava = freq * 2.0
    soma = 0.0
    saida = []
    for i in range(n):
        t = i / TAXA
        # duas camadas desafinadas 0.4% (choro de valve amp)
        g1 = (dente_de_serra(freq * t) + 0.5 * quadrada(freq * 1.004 * t)
              + 0.6 * dente_de_serra(quinta * t) + 0.3 * quadrada(oitava * t))
        g2 = (dente_de_serra(freq * 0.996 * t) + 0.5 * quadrada(freq * t)
              + 0.6 * dente_de_serra(quinta * 0.996 * t) + 0.3 * quadrada(oitava * 1.004 * t))
        soma = 0.6 * g1 + 0.4 * g2
        # envelope: ataque 4ms, decai pro chug (0.10s) ou aberto (0.30s)
        ataque = min(1.0, t / 0.004)
        decai = math.exp(-t / (0.10 if chug else 0.30))
        saida.append(soma * ataque * decai * volume)
    return saida


def concatenar(partes):
    saida = []
    for p in partes:
        saida.extend(p)
    return saida


def silencio(segundos):
    return [0.0] * int(TAXA * segundos)


E2, G2, A2, B2, D3 = 82.41, 98.00, 110.00, 123.47, 146.83

# O GALLOP DO JUÇA (140 BPM, colcheias ~214ms): chug-chug-ABERTO, o riff
# de cowpunk que a orta do barril aponta. Duas voltas: a segunda sobe o D.
COMPRIMENTO = 0.214
volta1 = [
    nota(E2, COMPRIMENTO, chug=True), nota(E2, COMPRIMENTO, chug=True),
    nota(G2, COMPRIMENTO * 1.6),      nota(E2, COMPRIMENTO, chug=True),
    nota(A2, COMPRIMENTO * 1.6),      nota(G2, COMPRIMENTO),
    nota(E2, COMPRIMENTO, chug=True), nota(D3, COMPRIMENTO * 1.6),
    nota(E2, COMPRIMENTO, chug=True), nota(E2, COMPRIMENTO, chug=True),
    nota(B2, COMPRIMENTO * 1.6),      nota(A2, COMPRIMENTO),
    nota(G2, COMPRIMENTO),            nota(E2, COMPRIMENTO, chug=True),
    nota(D3, COMPRIMENTO),            nota(E2, COMPRIMENTO * 1.8),
]
volta2 = [
    nota(E2, COMPRIMENTO, chug=True), nota(E2, COMPRIMENTO, chug=True),
    nota(G2, COMPRIMENTO * 1.6),      nota(E2, COMPRIMENTO, chug=True),
    nota(A2, COMPRIMENTO * 1.6),      nota(G2, COMPRIMENTO),
    nota(E2, COMPRIMENTO, chug=True), nota(D3 + 12.0, COMPRIMENTO * 1.6),  # sobe meio tom
    nota(E2, COMPRIMENTO, chug=True), nota(E2, COMPRIMENTO, chug=True),
    nota(B2, COMPRIMENTO * 1.6),      nota(A2, COMPRIMENTO),
    nota(G2, COMPRIMENTO),            nota(E2, COMPRIMENTO, chug=True),
    nota(D3, COMPRIMENTO),            nota(E2, COMPRIMENTO * 2.4, volume=1.15),  # final aberto
]
riff = concatenar(volta1 + volta2)

# normaliza + fade-out de 0.5s (a valve desliga)
pico = max(abs(s) for s in riff) or 1.0
fade = int(TAXA * 0.5)
for i in range(len(riff)):
    ganho = 0.85 / pico
    if i > len(riff) - fade:
        ganho *= (len(riff) - i) / fade
    riff[i] *= ganho
save_wav("juca_riff", riff)

# A VOZ: "hé hé" grave do Juça (duas cabidas de serra caindo em pitch)
voz = concatenar([
    nota(96.0, 0.14, chug=True, volume=0.9),
    silencio(0.10),
    nota(84.0, 0.18, chug=True, volume=0.9),
])
save_wav("juca_voz", voz)

print("Sons do Juça gerados em", OUT)
