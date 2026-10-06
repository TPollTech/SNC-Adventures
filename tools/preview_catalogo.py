#!/usr/bin/env python3
"""Prévia da auditoria: modelos/atlas das fontes canônicas, sem gravar no jogo.

Executar da raiz: python tools/preview_catalogo.py
Escreve apenas preview/identidade-dados.json. Não chama main() dos geradores.
"""
from pathlib import Path
import os
import json
import io
import base64
from PIL import Image
import gen_farm_resources
import gen_garrafas
import gen_catalogo
import gen_porta_grade
import gen_prateleira
import gen_lampada_led
import gen_lampada_led_potencia
import gen_eletrica
import gen_mobilia_mercado

ROOT = Path(__file__).resolve().parent.parent


def main():
    os.chdir(ROOT)
    entries=[]
    for module in (gen_farm_resources, gen_garrafas, gen_catalogo, gen_porta_grade,
                   gen_prateleira, gen_lampada_led, gen_lampada_led_potencia, gen_eletrica,
                   gen_mobilia_mercado):
        entries.extend(module.preview_entries())
    for entry in entries:
        # Foto/sprite ANTES: apenas quando ainda é a arte ativa do item.
        path = ROOT / 'src/main/resources/assets/intoxicantes/models/item' / (entry['id']+'.json')
        if path.exists():
            model=json.loads(path.read_text(encoding='utf-8'))
            if model.get('parent') == 'minecraft:item/generated':
                ref=model.get('textures',{}).get('layer0','')
                if ref.startswith('intoxicantes:'):
                    texture=ROOT/'src/main/resources/assets/intoxicantes/textures'/(ref.split(':',1)[1]+'.png')
                    if texture.exists():
                        with Image.open(texture) as img:
                            img.thumbnail((160,160),Image.Resampling.LANCZOS)
                            buf=io.BytesIO();img.save(buf,format='PNG')
                            entry['before']='data:image/png;base64,'+base64.b64encode(buf.getvalue()).decode('ascii')
        entry.setdefault('variants',[])
    dest=ROOT/'preview/identidade-dados.json'
    dest.write_text(json.dumps({'entries':entries},ensure_ascii=False,separators=(',',':')),encoding='utf-8')
    print(f'Prévia: {len(entries)} recursos; somente {dest.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
