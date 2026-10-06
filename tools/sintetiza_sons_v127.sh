#!/usr/bin/env bash
# Sons proprios v1.2.7: caixa registradora (venda) e balim (acerto da 12).
# Mesmo pipeline da 1.2.4 (ffmpeg -> .ogg mono em assets/intoxicantes/sounds/).
set -e
cd "$(dirname "$0")/.."
OUT=src/main/resources/assets/intoxicantes/sounds
mkdir -p "$OUT"

# caixa_registradora: "ca-ching" — duas campaninhas metalicas rapidas (fundindo a compra)
ffmpeg -y -loglevel error \
  -f lavfi -i "sine=frequency=1318:duration=0.30" \
  -f lavfi -i "sine=frequency=1975:duration=0.22" \
  -filter_complex "[0]adelay=0|0,apad=whole_dur=0.30,volume=0.7,afade=t=out:st=0.03:d=0.26[a];[1]adelay=110|110,apad=whole_dur=0.30,volume=0.8,afade=t=out:st=0.14:d=0.15[b];[a][b]amix=inputs=2:normalize=0,volume=0.9" \
  "$OUT/caixa_registradora.ogg"

# balim_acerto: chumbo doendo — estalo seco + corpo grave curto
ffmpeg -y -loglevel error \
  -f lavfi -i "sine=frequency=196:duration=0.12" \
  -f lavfi -i "anoisesrc=d=0.03:c=pink:a=0.35" \
  -filter_complex "[0]volume=0.9,afade=t=out:st=0.01:d=0.11[corpo];[1]volume=0.5,afade=t=out:st=0.002:d=0.028[click];[corpo][click]amix=inputs=2:normalize=0" \
  "$OUT/balim_acerto.ogg"

echo "Sons v1.2.7 sintetizados em $OUT"
