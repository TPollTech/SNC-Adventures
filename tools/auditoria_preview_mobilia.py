"""Checks estáticos e espaciais da prévia do salão; não gera assets do jogo."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

import gen_mercado as mercado
import gen_mobilia_mercado as mobilia


def validar_modelos():
    for peca in mobilia.PECAS:
        model = peca["modelo"]()
        assert len(model["elements"]) >= 40, (peca["id"], len(model["elements"]))
        if peca["id"] == "lavadora_mercado":
            assert len(model["elements"]) >= 117, (peca["id"], len(model["elements"]))
        assert model["texture_size"] == [128, 128]
        for el in model["elements"]:
            assert all(a < b for a, b in zip(el["from"], el["to"])), (peca["id"], el)
            for face in el["faces"].values():
                assert all(0 <= uv <= 16 for uv in face["uv"]), (peca["id"], face)
    # Porta da lavadora: raio completo do vidro sem chapa frontal opaca por trás.
    lav = mobilia.modelo_lavadora()["elements"]
    chapa = [e for e in lav if e["name"].startswith("frente_chapa")
             or e["name"].startswith("chapa_lateral_porta")]
    assert chapa and all(not (e["from"][2] <= .52 and e["to"][2] >= .42
                              and e["from"][0] < 11.43 and e["to"][0] > 4.57
                              and e["from"][1] < 10.63 and e["to"][1] > 3.77)
                         for e in chapa), "a porta de vidro está escondida por chapa"
    # Etiqueta do engradado fica voltada para os quatro lados; nenhuma encosta no fundo.
    engradado = mobilia.modelo_engradado()["elements"]
    assert {e["name"] for e in engradado if e["name"].startswith("etiqueta_")} == {
        "etiqueta_north", "etiqueta_south", "etiqueta_east", "etiqueta_west"}


def validar_layout():
    assert len(mercado.COLUNAS_ILHA) * len(mercado.FILEIRAS_ILHA) * 2 == 24
    assert mercado.FILEIRAS_ILHA == (6, 9)
    assert mercado.COLUNAS_ILHA == (6, 8, 10, 16, 18, 20)
    assert [len(l) for layer in mercado.ROWS for l in layer] == [27] * (8 * 27)
    for x in mercado.COLUNAS_ILHA:
        for y in (1, 2):
            for z in mercado.FILEIRAS_ILHA:
                assert mercado.ROWS[y][z][x] == "z"
                for cz in (z - 1, z + 1):
                    assert mercado.ROWS[0][cz][x] != "."
                    assert mercado.ROWS[y][cz][x] == "."
                sec = mercado.secao_prateleira(x, y, z)
                assert 0 <= sec < 58
    # Nenhuma face do bebedouro/lavadora aponta para a parede.
    for x, z, ch, facing in ((3, 10, "I", "east"), (23, 10, "i", "west"),
                             (23, 13, "D", "west")):
        assert mercado.ROWS[1][z][x] == ch
        assert mercado.PALETTE[ch]["properties"]["facing"] == facing


def main():
    validar_modelos()
    validar_layout()
    print("Auditoria prévia OK: geometrias, UVs, porta aberta, estoque e corredores.")


if __name__ == "__main__":
    main()
