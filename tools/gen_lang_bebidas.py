# -*- coding: utf-8 -*-
"""v1.2.49: lang pt/en das cadeias de bebidas (barris, dorna, alambique, moenda, prensa, caldeirão, cevada)."""
import json, os, re, collections

os.chdir(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

NOVOS_PT = collections.OrderedDict([
    ('entity.intoxicantes.bebida_decorativa', 'Bebida na mesa'),
    ('item.intoxicantes.bebida_colocavel', 'Agache e use no topo de uma superfície para colocar. Mão vazia para recolher.'),
    ('bebida.intoxicantes.sem_apoio', 'A garrafa precisa de uma superfície firme e plana.'),
    ('bebida.intoxicantes.sem_espaco', 'Não há espaço para colocar a garrafa aqui.'),
    ('guia.intoxicantes.bebidas.garrafas.titulo', 'Garrafas na mesa'),
    ('guia.intoxicantes.bebidas.garrafas.hero', 'Da mão para a decoração'),
    ('guia.intoxicantes.bebidas.garrafas.colocar', 'Com uma bebida pronta na mão, agache e use no topo de uma superfície livre. Você coloca uma unidade exatamente na altura do apoio; mesas e lajes com superfície compatível também funcionam.'),
    ('guia.intoxicantes.bebidas.garrafas.recolher', 'Use a mão vazia na garrafa para recolher a mesma bebida. Colocar e recolher não aplicam efeitos nem criam garrafas vazias. O nome e os demais dados da bebida são mantidos.'),
    ('guia.intoxicantes.bebidas.garrafas.consumir', 'Para beber, segure a bebida e use normalmente. Os efeitos, a saúde e o recipiente devolvido seguem as regras da bebida. Cerveja, vinho, cachaça, hidromel, rum, suco detox, água de coco e chá de lúpulo podem decorar a mesa.'),
    ('guia.intoxicantes.bebidas.garrafas.apoio', 'Se o apoio for removido, a bebida cai como item. No modo criativo, quebrar a decoração a remove sem soltar outra cópia.'),
    # ---- blocos
    ("block.intoxicantes.barril_cachaca", "Barril de Cachaça"),
    ("block.intoxicantes.barril_cerveja", "Barril de Cerveja"),
    ("block.intoxicantes.barril_rum", "Barril de Rum"),
    ("block.intoxicantes.barril_vinho", "Barril de Vinho"),
    ("block.intoxicantes.dorna_bebida", "Dorna de Fermentação"),
    ("block.intoxicantes.alambique", "Alambique de Cobre"),
    ("block.intoxicantes.moenda_cana", "Moenda de Cana"),
    ("block.intoxicantes.prensa_uvas", "Prensa de Uvas"),
    ("block.intoxicantes.caldeirao_mostura", "Caldeirão de Mostura"),
    ("block.intoxicantes.cevada_plant", "Cevada"),
    # ---- itens
    ("item.intoxicantes.cevada", "Cevada"),
    ("item.intoxicantes.semente_cevada", "Sementes de Cevada"),
    ("item.intoxicantes.malte", "Malte de Cevada"),
    ("item.intoxicantes.caldo_de_cana", "Caldo de Cana"),
    ("item.intoxicantes.bagaco_de_cana", "Bagaço de Cana"),
    ("item.intoxicantes.melaco", "Melaço"),
    ("item.intoxicantes.mosto_cana_fermentado", "Mosto de Cana Fermentado"),
    ("item.intoxicantes.mosto_rum_fermentado", "Mosto de Rum Fermentado"),
    ("item.intoxicantes.mosto_de_uva", "Mosto de Uva"),
    ("item.intoxicantes.mosto_cerveja_lupulado", "Mosto Lupulado"),
    ("item.intoxicantes.cachaca_jovem", "Cachaça Jovem (Branca)"),
    ("item.intoxicantes.rum_jovem", "Rum Jovem"),
    # ---- lore das etapas
    ("item.intoxicantes.caldo_de_cana.lore", "O caldo doce da cana fresca — primeira etapa da cachaça."),
    ("item.intoxicantes.mosto_cana_fermentado.lore", "O caldo borbulhou na dorna. Hora do alambique."),
    ("item.intoxicantes.melaco.lore", "Caldo de cana cozido até virar melaço — a alma do rum."),
    ("item.intoxicantes.mosto_rum_fermentado.lore", "O melaço fermentou. O alambique chama."),
    ("item.intoxicantes.mosto_de_uva.lore", "Uvas esmagadas na prensa — o vinho começa aqui."),
    ("item.intoxicantes.mosto_cerveja_lupulado.lore", "Malte + lúpulo + água. O barril completa a obra."),
    ("item.intoxicantes.cachaca_jovem.lore", "Recém-saída do alambique. Descansa no barril de cachaça."),
    ("item.intoxicantes.rum_jovem.lore", "Cristalino e forte. A madeira escura vai domar esse fogo."),
    # v1.2.65: o recipiente devolvido de cada bebida
    ("item.intoxicantes.cerveja_vazia", "Garrafa Vazia de Cerveja"),
    ("item.intoxicantes.vinho_vazia", "Garrafa Vazia de Vinho"),
    ("item.intoxicantes.cachaca_vazia", "Garrafa Vazia de Cachaça"),
    ("item.intoxicantes.hidromel_vazia", "Garrafa Vazia de Hidromel"),
    ("item.intoxicantes.rum_vazia", "Garrafa Vazia de Rum"),
    ("item.intoxicantes.suco_detox_vazia", "Garrafa Vazia de Suco Detox"),
    ("item.intoxicantes.agua_de_coco_vazia", "Garrafa Vazia de Água de Coco"),
    ("item.intoxicantes.cha_lupulo_vazia", "Garrafa Vazia de Chá de Lúpulo"),
    ("item.intoxicantes.cerveja_vazia.lore", "O recipiente devolvido: a bebida acabou, mas a garrafa fica — identidade, tampa e rótulo."),
    ("item.intoxicantes.vinho_vazia.lore", "O recipiente devolvido: a bebida acabou, mas a garrafa fica — identidade, tampa e rótulo."),
    ("item.intoxicantes.cachaca_vazia.lore", "O recipiente devolvido: a bebida acabou, mas a garrafa fica — identidade, tampa e rótulo."),
    ("item.intoxicantes.hidromel_vazia.lore", "O recipiente devolvido: a bebida acabou, mas a garrafa fica — identidade, tampa e rótulo."),
    ("item.intoxicantes.rum_vazia.lore", "O recipiente devolvido: a bebida acabou, mas a garrafa fica — identidade, tampa e rótulo."),
    ("item.intoxicantes.suco_detox_vazia.lore", "O recipiente devolvido: a bebida acabou, mas a garrafa fica — identidade, tampa e rótulo."),
    ("item.intoxicantes.agua_de_coco_vazia.lore", "O recipiente devolvido: a bebida acabou, mas a garrafa fica — identidade, tampa e rótulo."),
    ("item.intoxicantes.cha_lupulo_vazia.lore", "O recipiente devolvido: a bebida acabou, mas a garrafa fica — identidade, tampa e rótulo."),
    ("guia.intoxicantes.bebidas.garrafas.consumir", "Para beber, segure a bebida e use normalmente. Ao terminar, a GARRAFA VAZIA da própria bebida volta pra sua mão. Cerveja, vinho, cachaça, hidromel, rum, suco detox, água de coco e chá de lúpulo podem decorar a mesa."),
    ("item.intoxicantes.malte.lore", "Cevada torrada na fornalha — o coração da cerveja."),
    # ---- dorna
    ("block.intoxicantes.dorna_vazia", "Dorna vazia — carregue com o mosto (4 unidades)"),
    ("block.intoxicantes.dorna_carregada", "Fermentação começou! (%d:%02d no jogo)"),
    ("block.intoxicantes.dorna_fermentando", "Fermentando... %d:%02d (%d%%)"),
    ("block.intoxicantes.dorna_servido", "Fermentação pronta — recolha com a mão vazia"),
    # ---- alambique
    ("block.intoxicantes.alambique_vazio", "Alambique frio — carregue com o mosto fermentado (4 un.)"),
    ("block.intoxicantes.alambique_carregado", "Destilação começou! (%d:%02d no jogo)"),
    ("block.intoxicantes.alambique_destilando", "Destilando... %d:%02d (%d%%)"),
    ("block.intoxicantes.alambique_sem_fogo", "SEM FOGO embaixo! A destilação pausou (%d:%02d restantes)"),
    ("block.intoxicantes.alambique_servido", "Destilado pronto — recolha com a mão vazia"),
    # ---- barril
    ("block.intoxicantes.barril_vazio", "Barril vazio"),
    ("block.intoxicantes.barril_vazio_receita", "Barril vazio — carregue com %s"),
    ("block.intoxicantes.barril_fermentando", "Fermentando... %d:%02d (%d%%)"),
    ("block.intoxicantes.barril_maturando", "Maturando... %d:%02d (%d%%)"),
    ("block.intoxicantes.barril_pronto", "%s pronto! Use garrafas de vidro (restam %d)"),
    ("block.intoxicantes.barril_engarrafou", "Uma garrafa! Restam %d no barril"),
    ("block.intoxicantes.barril_lote_acabou", "Lote acabou — o barril está vazio"),
    ("block.intoxicantes.barril_falta_insumo", "Faltam insumos: precisa de %dx %s"),
    # ---- máquinas de prima
    ("block.intoxicantes.prima_carregada", "Processando... (%ds no jogo)"),
    ("block.intoxicantes.prima_processando", "Processando... %ds (%d%%)"),
    ("block.intoxicantes.prima_vazia", "Máquina vazia — clique com o insumo"),
    ("block.intoxicantes.prima_segunda_dose", "Fervura com lúpulo começou! (%ds no jogo)"),
    ("block.intoxicantes.prima_falta_segunda", "Falta o lúpulo! (1 unidade no caldeirão)"),
    ("block.intoxicantes.caldeirao_sem_agua", "O caldeirão precisa de ÁGUA embaixo (fonte ou caldeirão cheio)"),
    # ---- advancements
    ("advancements.intoxicantes.destilaria.title", "A Pequena Destilaria"),
    ("advancements.intoxicantes.destilaria.desc", "Comece sua produção de bebidas artesanais"),
    ("advancements.intoxicantes.cerveja.title", "Cervejaria da Esquina"),
    ("advancements.intoxicantes.cerveja.desc", "Conseguiu sementes de cevada — malte, lúpulo e fermentação te esperam"),
    ("advancements.intoxicantes.cachaca.title", "Pinga Forte"),
    ("advancements.intoxicantes.cachaca.desc", "Cana na mão: moer, fermentar, destilar e descansar na madeira"),
    ("advancements.intoxicantes.rum.title", "O Diabo do Caribe"),
    ("advancements.intoxicantes.rum.desc", "Melaço na mão — o caminho mais longo da destilaria"),
    ("advancements.intoxicantes.vinho.title", "Adega da Vila"),
    ("advancements.intoxicantes.vinho.desc", "Uvas colhidas — prensar, fermentar e maturar"),
])

NOVOS_EN = collections.OrderedDict([
    ('entity.intoxicantes.bebida_decorativa', 'Placed drink'),
    ('item.intoxicantes.bebida_colocavel', 'Sneak and use on top of a surface to place. Use an empty hand to pick up.'),
    ('bebida.intoxicantes.sem_apoio', 'The bottle needs a firm, flat surface.'),
    ('bebida.intoxicantes.sem_espaco', 'There is no room to place the bottle here.'),
    ('guia.intoxicantes.bebidas.garrafas.titulo', 'Bottles on the table'),
    ('guia.intoxicantes.bebidas.garrafas.hero', 'From your hand to the table'),
    ('guia.intoxicantes.bebidas.garrafas.colocar', 'Hold a finished drink, sneak and use it on top of a clear surface. One bottle is placed at the support height; tables and slabs with a suitable surface work too.'),
    ('guia.intoxicantes.bebidas.garrafas.recolher', 'Use an empty hand on the bottle to pick up the same drink. Placing and picking it up apply no effects and create no empty bottles. Its name and other data are preserved.'),
    ('guia.intoxicantes.bebidas.garrafas.consumir', 'To drink, hold the bottle and use it normally. Effects, health and the returned container follow that drink’s rules. Beer, wine, cachaca, mead, rum, detox juice, coconut water and hop tea can decorate your table.'),
    ('guia.intoxicantes.bebidas.garrafas.apoio', 'If the support is removed, the drink drops as an item. In creative mode, breaking the decoration removes it without dropping another copy.'),
    ("block.intoxicantes.barril_cachaca", "Cachaça Barrel"),
    ("block.intoxicantes.barril_cerveja", "Beer Barrel"),
    ("block.intoxicantes.barril_rum", "Rum Barrel"),
    ("block.intoxicantes.barril_vinho", "Wine Barrel"),
    ("block.intoxicantes.dorna_bebida", "Fermentation Vat"),
    ("block.intoxicantes.alambique", "Copper Alembic Still"),
    ("block.intoxicantes.moenda_cana", "Sugarcane Mill"),
    ("block.intoxicantes.prensa_uvas", "Grape Press"),
    ("block.intoxicantes.caldeirao_mostura", "Mash Tun"),
    ("block.intoxicantes.cevada_plant", "Barley"),
    ("item.intoxicantes.cevada", "Barley"),
    ("item.intoxicantes.semente_cevada", "Barley Seeds"),
    ("item.intoxicantes.malte", "Malted Barley"),
    ("item.intoxicantes.caldo_de_cana", "Sugarcane Juice"),
    ("item.intoxicantes.bagaco_de_cana", "Bagasse"),
    ("item.intoxicantes.melaco", "Molasses"),
    ("item.intoxicantes.mosto_cana_fermentado", "Fermented Cane Must"),
    ("item.intoxicantes.mosto_rum_fermentado", "Fermented Rum Must"),
    ("item.intoxicantes.mosto_de_uva", "Grape Must"),
    ("item.intoxicantes.mosto_cerveja_lupulado", "Hopped Wort"),
    ("item.intoxicantes.cachaca_jovem", "White (Young) Cachaça"),
    ("item.intoxicantes.rum_jovem", "Young Rum"),
    ("item.intoxicantes.caldo_de_cana.lore", "Fresh sweet cane juice — first step of cachaça."),
    ("item.intoxicantes.mosto_cana_fermentado.lore", "The juice bubbled in the vat. The still awaits."),
    ("item.intoxicantes.melaco.lore", "Cane juice boiled down to molasses — the soul of rum."),
    ("item.intoxicantes.mosto_rum_fermentado.lore", "The molasses fermented. Time for the still."),
    ("item.intoxicantes.mosto_de_uva.lore", "Grapes crushed at the press — wine begins here."),
    ("item.intoxicantes.mosto_cerveja_lupulado.lore", "Malt + hops + water. The barrel finishes the work."),
    ("item.intoxicantes.cachaca_jovem.lore", "Fresh from the still. Rests in the cachaça barrel."),
    ("item.intoxicantes.rum_jovem.lore", "Clear and strong. The dark wood will tame this fire."),
    # v1.2.65: the returned container of every drink
    ("item.intoxicantes.cerveja_vazia", "Empty Beer Bottle"),
    ("item.intoxicantes.vinho_vazia", "Empty Wine Bottle"),
    ("item.intoxicantes.cachaca_vazia", "Empty Cachaça Bottle"),
    ("item.intoxicantes.hidromel_vazia", "Empty Mead Bottle"),
    ("item.intoxicantes.rum_vazia", "Empty Rum Bottle"),
    ("item.intoxicantes.suco_detox_vazia", "Empty Detox Juice Bottle"),
    ("item.intoxicantes.agua_de_coco_vazia", "Empty Coconut Water Bottle"),
    ("item.intoxicantes.cha_lupulo_vazia", "Empty Hops Tea Bottle"),
    ("item.intoxicantes.cerveja_vazia.lore", "The returned container: the drink is gone, but the bottle remains — identity, cap and label."),
    ("item.intoxicantes.vinho_vazia.lore", "The returned container: the drink is gone, but the bottle remains — identity, cap and label."),
    ("item.intoxicantes.cachaca_vazia.lore", "The returned container: the drink is gone, but the bottle remains — identity, cap and label."),
    ("item.intoxicantes.hidromel_vazia.lore", "The returned container: the drink is gone, but the bottle remains — identity, cap and label."),
    ("item.intoxicantes.rum_vazia.lore", "The returned container: the drink is gone, but the bottle remains — identity, cap and label."),
    ("item.intoxicantes.suco_detox_vazia.lore", "The returned container: the drink is gone, but the bottle remains — identity, cap and label."),
    ("item.intoxicantes.agua_de_coco_vazia.lore", "The returned container: the drink is gone, but the bottle remains — identity, cap and label."),
    ("item.intoxicantes.cha_lupulo_vazia.lore", "The returned container: the drink is gone, but the bottle remains — identity, cap and label."),
    ("guia.intoxicantes.bebidas.garrafas.consumir", "To drink, hold the bottle and use it normally. When you finish, the drink's own EMPTY BOTTLE returns to your hand. Beer, wine, cachaca, mead, rum, detox juice, coconut water and hop tea can decorate your table."),
    ("item.intoxicantes.malte.lore", "Barley kilned in the furnace — the heart of beer."),
    ("block.intoxicantes.dorna_vazia", "Vat empty — load the must (4 units)"),
    ("block.intoxicantes.dorna_carregada", "Fermentation started! (%d:%02d game time)"),
    ("block.intoxicantes.dorna_fermentando", "Fermenting... %d:%02d (%d%%)"),
    ("block.intoxicantes.dorna_servido", "Fermentation done — collect with empty hand"),
    ("block.intoxicantes.alambique_vazio", "Still cold — load fermented must (4 units)"),
    ("block.intoxicantes.alambique_carregado", "Distillation started! (%d:%02d game time)"),
    ("block.intoxicantes.alambique_destilando", "Distilling... %d:%02d (%d%%)"),
    ("block.intoxicantes.alambique_sem_fogo", "NO FIRE below! Distillation paused (%d:%02d left)"),
    ("block.intoxicantes.alambique_servido", "Distillate ready — collect with empty hand"),
    ("block.intoxicantes.barril_vazio", "Empty barrel"),
    ("block.intoxicantes.barril_vazio_receita", "Empty barrel — load with %s"),
    ("block.intoxicantes.barril_fermentando", "Fermenting... %d:%02d (%d%%)"),
    ("block.intoxicantes.barril_maturando", "Aging... %d:%02d (%d%%)"),
    ("block.intoxicantes.barril_pronto", "%s ready! Use glass bottles (%d left)"),
    ("block.intoxicantes.barril_engarrafou", "One bottle! %d left in the barrel"),
    ("block.intoxicantes.barril_lote_acabou", "Batch finished — barrel is empty"),
    ("block.intoxicantes.barril_falta_insumo", "Missing supplies: need %dx %s"),
    ("block.intoxicantes.prima_carregada", "Processing... (%ds game time)"),
    ("block.intoxicantes.prima_processando", "Processing... %ds (%d%%)"),
    ("block.intoxicantes.prima_vazia", "Machine empty — click with the ingredient"),
    ("block.intoxicantes.prima_segunda_dose", "Hop boil started! (%ds game time)"),
    ("block.intoxicantes.prima_falta_segunda", "Missing hops! (1 unit in the tun)"),
    ("block.intoxicantes.caldeirao_sem_agua", "The mash tun needs WATER below (source or filled cauldron)"),
    ("advancements.intoxicantes.destilaria.title", "The Little Distillery"),
    ("advancements.intoxicantes.destilaria.desc", "Start your craft drink production"),
    ("advancements.intoxicantes.cerveja.title", "Corner Brewery"),
    ("advancements.intoxicantes.cerveja.desc", "Got barley seeds — malting, hops and fermentation await"),
    ("advancements.intoxicantes.cachaca.title", "Strong Moonshine"),
    ("advancements.intoxicantes.cachaca.desc", "Cane in hand: mill, ferment, distill and rest in wood"),
    ("advancements.intoxicantes.rum.title", "The Caribbean Devil"),
    ("advancements.intoxicantes.rum.desc", "Molasses in hand — the longest road of the distillery"),
    ("advancements.intoxicantes.vinho.title", "Village Cellar"),
    ("advancements.intoxicantes.vinho.desc", "Grapes harvested — press, ferment and age"),
])


def placeholders(s):
    """Conta %d, %s, %02d etc. na string."""
    return sorted(re.findall(r"%(?:0?\d+)?[ds]", s))


for lang, novos in (("pt_br", NOVOS_PT), ("en_us", NOVOS_EN)):
    path = "src/main/resources/assets/intoxicantes/lang/%s.json" % lang
    dados = json.load(open(path, encoding="utf-8"), object_pairs_hook=collections.OrderedDict)
    antes = len(dados)
    dados.update(novos)
    json.dump(dados, open(path, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
    json.load(open(path, encoding="utf-8"))  # valida JSON
    print("%s: %d -> %d chaves (+%d)" % (lang, antes, len(dados), len(novos)))

# coerência pt/en: mesmas chaves e mesmos placeholders
pt = json.load(open("src/main/resources/assets/intoxicantes/lang/pt_br.json", encoding="utf-8"))
en = json.load(open("src/main/resources/assets/intoxicantes/lang/en_us.json", encoding="utf-8"))
faltando_en = set(novos) - set(en)
faltando_pt = set(novos) - set(pt)
assert not faltando_en and not faltando_pt, (faltando_en, faltando_pt)
for k in novos:
    assert placeholders(pt[k]) == placeholders(en[k]), "placeholders divergentes: %s | %r vs %r" % (k, pt[k], en[k])
print("pt/en coerentes: chaves e placeholders conferidos OK")
