#!/usr/bin/env python3
"""v1.2.54 — Adiciona as chaves de lang da saúde (pt_br + en_us)."""
import json
import io
import os

raiz = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                    "assets", "intoxicantes", "lang")

S = "\u00a7"  # o § do Minecraft

pt_adicoes = {
    # ---- teclas
    "key.intoxicantes.prontuario": "Abrir o Prontuário do Fregues (saúde)",
    # ---- itens novos
    "item.intoxicantes.suco_detox": "Suco Detox",
    "item.intoxicantes.suco_detox.lore": S + "aA redenção do fregues: regenera os órgãos devagar e mata a sede.",
    "item.intoxicantes.agua_de_coco": "Água de Coco",
    "item.intoxicantes.agua_de_coco.lore": S + "bO isotônico do sertão: a melhor água que a esquina oferece.",
    # ---- efeitos (nomes)
    "effect.intoxicantes.tranquilo": "Tranquilo",
    "effect.intoxicantes.morno": "Morno",
    "effect.intoxicantes.sonho": "Sonho",
    "effect.intoxicantes.overdrive": "Overdrive",
    "effect.intoxicantes.viagem": "Viagem",
    "effect.intoxicantes.abstinencia": "Síndrome de Abstinência",
    # ---- efeitos (descrições)
    "effect.intoxicantes.tranquilo.desc": "A morgue do baseado: corpo solto, fome da boa.",
    "effect.intoxicantes.morno.desc": "O casaco quente do ópio: nada dói, o teto pesa.",
    "effect.intoxicantes.sonho.desc": "O nod da heroína: lá e aqui ao mesmo tempo.",
    "effect.intoxicantes.overdrive.desc": "O coração de metralhadora: o mundo em fast-forward.",
    "effect.intoxicantes.viagem.desc": "A viagem do LSD: o mundo derrete e o Gago atravessa o céu.",
    # ---- viagens (avisos no chat por substância)
    "effect.intoxicantes.viagem.baseado": S + "aA morgue chegou... o mundo ficou mais macio.",
    "effect.intoxicantes.viagem.baseado.forte": S + "aPuxou demais: a morgue tá PESADA e a fome veio junto.",
    "effect.intoxicantes.viagem.opio": S + "6O casaco quente cobre você: nada dói agora.",
    "effect.intoxicantes.viagem.opio.forte": S + "6O corpo virou chumbo quente: até o chão parece confortável.",
    "effect.intoxicantes.viagem.heroina": S + "5O nod chega: lá e aqui ao mesmo tempo.",
    "effect.intoxicantes.viagem.heroina.forte": S + "5Você afundou no colchão do mundo. Não lute.",
    "effect.intoxicantes.viagem.cocaina": S + "fO coração dispara: o mundo em fast-forward.",
    "effect.intoxicantes.viagem.cocaina.forte": S + "fTUDO MUITO RÁPIDO: o coração é uma metralhadora e o mundo acompanha.",
    "effect.intoxicantes.viagem.lsd": S + "dA viagem começou: não olhe muito pro céu.",
    "effect.intoxicantes.viagem.lsd.forte": S + "dA viagem PESOU: as paredes derretem e o mercadão atravessa o céu.",
    # ---- quedas (aftershock)
    "effect.intoxicantes.queda.morno": S + "7O casaco esfriou: o corpo pesa e tudo cansa.",
    "effect.intoxicantes.queda.sonho": S + "8Você voltou do sonho: com fome, lento e com saudade.",
    "effect.intoxicantes.queda.overdrive": S + "7A batida passou: o corpo cobra a festa.",
    "effect.intoxicantes.queda.viagem": S + "8As paredes pararam de derreter... mas a escuridão dá bom dia.",
    # ---- sede
    "effect.intoxicantes.sede.zerou": S + "cVocê tá DESIDRATADO: fraqueza, lentidão e náusea. Beba alguma coisa (água, coco, suco)!",
    # ---- vício / abstinência
    "effect.intoxicantes.abstinencia.inicio": S + "cO corpo tá cobrando %s... (síndrome de abstinência)",
    "effect.intoxicantes.abstinencia.colapso": S + "4O COLAPSO do vício: seu corpo está desistindo. A dose alivia... mas o preço é alto.",
    "effect.intoxicantes.vicio.dialimpo": S + "aDia limpo: o vício derrete (%s de nível restante). Continue assim.",
    "effect.intoxicantes.vicio.curado": S + "aVÍCIO CURADO: o corpo te perdoou. Não jogue essa vitória fora.",
    "effect.intoxicantes.detox.bebeu": S + "aO detox desce fazendo bem: os órgãos agradecem.",
    # ---- HUD
    "hud.intoxicantes.saude.abstinencia": "ABSTINÊNCIA ★%s",
    # ---- prontuário
    "gui.intoxicantes.prontuario.titulo": "PRONTUÁRIO DO FREGUES",
    "gui.intoxicantes.prontuario.subtitulo": "Clínica da Esquina - Dr. Gago atende",
    "gui.intoxicantes.prontuario.orgao.FIGADO": "Fígado",
    "gui.intoxicantes.prontuario.orgao.PULMAO": "Pulmão",
    "gui.intoxicantes.prontuario.orgao.ESTOMAGO": "Estômago",
    "gui.intoxicantes.prontuario.estagio.0.figado": "Fígado de Aço",
    "gui.intoxicantes.prontuario.estagio.1.figado": "Fígado Zangado",
    "gui.intoxicantes.prontuario.estagio.2.figado": "Fígado Sofrendo",
    "gui.intoxicantes.prontuario.estagio.3.figado": "Fígado Pedindo Misericórdia",
    "gui.intoxicantes.prontuario.estagio.4.figado": "Cirrose em estágio de Jucelino",
    "gui.intoxicantes.prontuario.estagio.0.pulmao": "Pulmão de Vento",
    "gui.intoxicantes.prontuario.estagio.1.pulmao": "Pulmão Roncando",
    "gui.intoxicantes.prontuario.estagio.2.pulmao": "Pulmão de Carvão",
    "gui.intoxicantes.prontuario.estagio.3.pulmao": "Pulmão Vencido",
    "gui.intoxicantes.prontuario.estagio.4.pulmao": "Pulmão em Modo Puro Absurdo",
    "gui.intoxicantes.prontuario.estagio.0.estomago": "Barriga de Aço",
    "gui.intoxicantes.prontuario.estagio.1.estomago": "Estômago Resmungando",
    "gui.intoxicantes.prontuario.estagio.2.estomago": "Estômago de Guerra",
    "gui.intoxicantes.prontuario.estagio.3.estomago": "Estômago em Greve",
    "gui.intoxicantes.prontuario.estagio.4.estomago": "Estômago do Ipê Decadente",
    "gui.intoxicantes.prontuario.historico": "Histórico do fregues:",
    "gui.intoxicantes.prontuario.historico.linhas": "%s bebidas, %s ervas fumadas, %s pó cheirado, %s pílulas engolidas",
    "gui.intoxicantes.prontuario.veredito.0": S + "a" + '"Saudável pra karalho" - Dr. Gago',
    "gui.intoxicantes.prontuario.veredito.1": S + "2" + '"Dá pra consertar com suco" - Dr. Gago',
    "gui.intoxicantes.prontuario.veredito.2": S + "6" + '"O fígado já pediu demissão" - Dr. Gago',
    "gui.intoxicantes.prontuario.veredito.3": S + "c" + '"Reza e detox, meu irmão" - Dr. Gago',
    "gui.intoxicantes.prontuario.veredito.4": S + "4" + '"O Gago reza por você" - Dr. Gago',
    "gui.intoxicantes.prontuario.medico": "H para fechar",
}

en_adicoes = {
    "key.intoxicantes.prontuario": "Open Patient Record (health)",
    "item.intoxicantes.suco_detox": "Detox Juice",
    "item.intoxicantes.suco_detox.lore": S + "aThe regular's redemption: slowly heals your organs and quenches thirst.",
    "item.intoxicantes.agua_de_coco": "Coconut Water",
    "item.intoxicantes.agua_de_coco.lore": S + "bThe backcountry isotonic: the best water on the corner.",
    "effect.intoxicantes.tranquilo": "Mellow",
    "effect.intoxicantes.morno": "Warm Blanket",
    "effect.intoxicantes.sonho": "Dreaming",
    "effect.intoxicantes.overdrive": "Overdrive",
    "effect.intoxicantes.viagem": "Trip",
    "effect.intoxicantes.abstinencia": "Withdrawal Syndrome",
    "effect.intoxicantes.tranquilo.desc": "The joint's mellow: loose body, big appetite.",
    "effect.intoxicantes.morno.desc": "Opium's warm blanket: nothing hurts, the ceiling weighs.",
    "effect.intoxicantes.sonho.desc": "Heroin's nod: there and here at the same time.",
    "effect.intoxicantes.overdrive.desc": "Machine-gun heart: the world on fast-forward.",
    "effect.intoxicantes.viagem.desc": "LSD's trip: the world melts and the Gago crosses the sky.",
    "effect.intoxicantes.viagem.baseado": S + "aThe mellow sets in... the world feels softer.",
    "effect.intoxicantes.viagem.baseado.forte": S + "aDeep toke: HEAVY mellow, and the munchies came along.",
    "effect.intoxicantes.viagem.opio": S + "6The warm blanket covers you: nothing hurts now.",
    "effect.intoxicantes.viagem.opio.forte": S + "6Your body is warm lead: even the floor feels comfy.",
    "effect.intoxicantes.viagem.heroina": S + "5The nod arrives: there and here at the same time.",
    "effect.intoxicantes.viagem.heroina.forte": S + "5You sank into the world's mattress. Don't fight it.",
    "effect.intoxicantes.viagem.cocaina": S + "fThe heart races: the world on fast-forward.",
    "effect.intoxicantes.viagem.cocaina.forte": S + "fEVERYTHING TOO FAST: your heart is a machine gun and the world keeps up.",
    "effect.intoxicantes.viagem.lsd": S + "dThe trip begins: don't stare at the sky too long.",
    "effect.intoxicantes.viagem.lsd.forte": S + "dThe trip got HEAVY: walls melt and the big market crosses the sky.",
    "effect.intoxicantes.queda.morno": S + "7The blanket cooled off: body heavy, everything tires you.",
    "effect.intoxicantes.queda.sonho": S + "8Back from the dream: hungry, slow and homesick.",
    "effect.intoxicantes.queda.overdrive": S + "7The beat wore off: your body bills you for the party.",
    "effect.intoxicantes.queda.viagem": S + "8The walls stopped melting... but the darkness says good morning.",
    "effect.intoxicantes.sede.zerou": S + "cYou are DEHYDRATED: weakness, slowness and nausea. Drink something!",
    "effect.intoxicantes.abstinencia.inicio": S + "cYour body is demanding %s... (withdrawal)",
    "effect.intoxicantes.abstinencia.colapso": S + "4The withdrawal CRASH: your body is giving up. A dose would help... at a price.",
    "effect.intoxicantes.vicio.dialimpo": S + "aClean day: the addiction melts (%s levels left). Keep going.",
    "effect.intoxicantes.vicio.curado": S + "aADDICTION CURED: your body forgave you. Don't waste it.",
    "effect.intoxicantes.detox.bebeu": S + "aThe detox goes down easy: your organs thank you.",
    "hud.intoxicantes.saude.abstinencia": "WITHDRAWAL " + chr(9733) + "%s",
    "gui.intoxicantes.prontuario.titulo": "PATIENT RECORD",
    "gui.intoxicantes.prontuario.subtitulo": "Corner Clinic - Dr. Gago on duty",
    "gui.intoxicantes.prontuario.orgao.FIGADO": "Liver",
    "gui.intoxicantes.prontuario.orgao.PULMAO": "Lungs",
    "gui.intoxicantes.prontuario.orgao.ESTOMAGO": "Stomach",
    "gui.intoxicantes.prontuario.estagio.0.figado": "Steel Liver",
    "gui.intoxicantes.prontuario.estagio.1.figado": "Grumpy Liver",
    "gui.intoxicantes.prontuario.estagio.2.figado": "Suffering Liver",
    "gui.intoxicantes.prontuario.estagio.3.figado": "Liver Begging for Mercy",
    "gui.intoxicantes.prontuario.estagio.4.figado": "Full-Blown Cirrhosis",
    "gui.intoxicantes.prontuario.estagio.0.pulmao": "Windy Lungs",
    "gui.intoxicantes.prontuario.estagio.1.pulmao": "Rattling Lungs",
    "gui.intoxicantes.prontuario.estagio.2.pulmao": "Coal Lungs",
    "gui.intoxicantes.prontuario.estagio.3.pulmao": "Beaten Lungs",
    "gui.intoxicantes.prontuario.estagio.4.pulmao": "Lungs in Pure Nonsense Mode",
    "gui.intoxicantes.prontuario.estagio.0.estomago": "Steel Stomach",
    "gui.intoxicantes.prontuario.estagio.1.estomago": "Grumbling Stomach",
    "gui.intoxicantes.prontuario.estagio.2.estomago": "Wartime Stomach",
    "gui.intoxicantes.prontuario.estagio.3.estomago": "Stomach on Strike",
    "gui.intoxicantes.prontuario.estagio.4.estomago": "Decadent Ipe Stomach",
    "gui.intoxicantes.prontuario.historico": "Regular's history:",
    "gui.intoxicantes.prontuario.historico.linhas": "%s drinks, %s joints smoked, %s powders snorted, %s pills swallowed",
    "gui.intoxicantes.prontuario.veredito.0": S + "a" + '"Healthy as hell" - Dr. Gago',
    "gui.intoxicantes.prontuario.veredito.1": S + "2" + '"Juice can fix that" - Dr. Gago',
    "gui.intoxicantes.prontuario.veredito.2": S + "6" + '"The liver already quit" - Dr. Gago',
    "gui.intoxicantes.prontuario.veredito.3": S + "c" + '"Pray and detox, brother" - Dr. Gago',
    "gui.intoxicantes.prontuario.veredito.4": S + "4" + '"The Gago prays for you" - Dr. Gago',
    "gui.intoxicantes.prontuario.medico": "Press H to close",
}


def main():
    for nome, adicoes in (("pt_br.json", pt_adicoes), ("en_us.json", en_adicoes)):
        caminho = os.path.join(raiz, nome)
        with io.open(caminho, encoding="utf-8") as f:
            dados = json.load(f)
        antes = len(dados)
        for k, v in adicoes.items():
            dados[k] = v
        with io.open(caminho, "w", encoding="utf-8", newline="\n") as f:
            json.dump(dados, f, ensure_ascii=False, indent=2)
            f.write("\n")
        print(f"{nome}: {antes} -> {len(dados)} chaves (+{len(adicoes)})")


if __name__ == "__main__":
    main()
