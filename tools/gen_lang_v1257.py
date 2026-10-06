#!/usr/bin/env python3
"""v1.2.57 — Prontuário vira "O Quão Fudido Tu Está" + chaves da tela nova."""
import json
import os

RAIZ = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                    "assets", "intoxicantes", "lang")

PT = {
    # renome (o pedido do chefe kkkk)
    "key.intoxicantes.prontuario": "O Quão Fudido Tu Está (saúde)",
    "gui.intoxicantes.prontuario.titulo": "O QUÃO FUDIDO TU ESTÁ",
    "gui.intoxicantes.prontuario.subtitulo": "Clínica do Esquinão · consultório de confiança do Gago",
    # consequências reais por órgão (os ganchos existem no código)
    "gui.intoxicantes.prontuario.efeito.figado.limpo": "Fígado aguenta a bebedeira.",
    "gui.intoxicantes.prontuario.efeito.figado.ruim": "A ressaca vem com o dobro do sofrimento.",
    "gui.intoxicantes.prontuario.efeito.pulmao.limpo": "Fôlego de maratonista.",
    "gui.intoxicantes.prontuario.efeito.pulmao.ruim": "Tosse seca depois de todo baseado.",
    "gui.intoxicantes.prontuario.efeito.estomago.limpo": "Engole qualquer coisa.",
    "gui.intoxicantes.prontuario.efeito.estomago.ruim": "A náusea da queda dura bem mais.",
    # histórico em grade
    "gui.intoxicantes.prontuario.historico": "Histórico do fregues (desde a primeira dose):",
    "gui.intoxicantes.prontuario.h.alcool": "Bebidas",
    "gui.intoxicantes.prontuario.h.erva": "Ervas fumadas",
    "gui.intoxicantes.prontuario.h.po": "Pós cheirados",
    "gui.intoxicantes.prontuario.h.pilula": "Pílulas",
    "gui.intoxicantes.prontuario.diaslimpos": "Dias limpos: %s",
    # vício
    "gui.intoxicantes.prontuario.vicio.label": "Vício em %s",
    "gui.intoxicantes.prontuario.vicio.nivel": "%s/100",
    "gui.intoxicantes.prontuario.vicio.dependente": "DEPENDENTE",
    "gui.intoxicantes.prontuario.vicio.controlado": "sob controle",
    "gui.intoxicantes.prontuario.vicio.desconhecida": "algo",
    "gui.intoxicantes.prontuario.abstinencia": "ABSTINÊNCIA estágio %s — o corpo tá cobrando",
}

EN = {
    "key.intoxicantes.prontuario": "How Screwed You Are (health)",
    "gui.intoxicantes.prontuario.titulo": "HOW SCREWED YOU ARE",
    "gui.intoxicantes.prontuario.subtitulo": "Corner Clinic · the Gago's trusted practice",
    "gui.intoxicantes.prontuario.efeito.figado.limpo": "Liver can take the party.",
    "gui.intoxicantes.prontuario.efeito.figado.ruim": "Hangovers now hit twice as hard.",
    "gui.intoxicantes.prontuario.efeito.pulmao.limpo": "Lungs of a marathoner.",
    "gui.intoxicantes.prontuario.efeito.pulmao.ruim": "Dry cough after every joint.",
    "gui.intoxicantes.prontuario.efeito.estomago.limpo": "Swallows anything.",
    "gui.intoxicantes.prontuario.efeito.estomago.ruim": "The comedown nausea lasts way longer.",
    "gui.intoxicantes.prontuario.historico": "Patient history (since the first dose):",
    "gui.intoxicantes.prontuario.h.alcool": "Drinks",
    "gui.intoxicantes.prontuario.h.erva": "Joints smoked",
    "gui.intoxicantes.prontuario.h.po": "Lines snorted",
    "gui.intoxicantes.prontuario.h.pilula": "Pills swallowed",
    "gui.intoxicantes.prontuario.diaslimpos": "Clean days: %s",
    "gui.intoxicantes.prontuario.vicio.label": "Addicted to %s",
    "gui.intoxicantes.prontuario.vicio.nivel": "%s/100",
    "gui.intoxicantes.prontuario.vicio.dependente": "DEPENDENT",
    "gui.intoxicantes.prontuario.vicio.controlado": "under control",
    "gui.intoxicantes.prontuario.vicio.desconhecida": "something",
    "gui.intoxicantes.prontuario.abstinencia": "WITHDRAWAL stage %s — your body is collecting",
}

for arquivo, adicoes in [("pt_br.json", PT), ("en_us.json", EN)]:
    caminho = os.path.join(RAIZ, arquivo)
    with open(caminho, encoding="utf-8") as f:
        dados = json.load(f)
    dados.update(adicoes)
    # remover chave de histórico antiga (vira linha do título da grade)
    dados.pop("gui.intoxicantes.prontuario.historico.linhas", None)
    with open(caminho, "w", encoding="utf-8", newline="\n") as f:
        json.dump(dados, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"{arquivo}: {len(adicoes)} chaves aplicadas")
