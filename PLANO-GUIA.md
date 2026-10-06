# PLANO — Guia do SNC Adventures (livro-guia in-game)

> Plano apenas. Nada implementado ainda. Baseado na análise real do repositório
> (v1.2.50+). Revisar este arquivo antes de começar — o código pode ter mudado.

## 0. Resultado da análise do projeto (o que existe de fato)

- **Loader/API:** Fabric (fabric-loom sem remapping, jogo não-ofuscado). **Minecraft 26.3**, Java 25, Fabric API `0.160.7+26.3`, loader 0.19.5.
- **Mod id/namespace:** `intoxicantes` (legado, mantém-se). **Nome oficial exibido: "SNC Adventures"** — já é o `name` no `fabric.mod.json`. O guia usa "SNC Adventures" em todo texto de jogador.
- **Não existe biblioteca de guide book** (Patchouli etc.) nem livro/guia atual (grep por `written_book|guide|guia` não acha nada). Guias de terceiros ficam de fora — interface própria leve, no padrão do projeto.
- **Registro central data-driven JÁ EXISTE:** `ProcessosBebida` (v1.2.50) é o livro-de-receitas das bebidas, com records `Dorna`, `Alambique`, `Barril` (com `tempoFermentacaoSeg`/`tempoMaturacaoSeg`), `Prima` (moenda/prensa/caldeirão, com segundo ingrediente e extra). **O guia leria daqui**, sem duplicar valores (regra data-driven do pedido já casando com a arquitetura).
- **Config:** `ModConfig` — Gson em `config/intoxicantes.json`, saneamento de valores. Novo campo entra nesse padrão.
- **Persistência por jogador:** padrão do projeto é JSON no diretório do mundo (`PlayerMoney` → `intoxicantes_money.json`/`intoxicantes_fiado.json`, init via `ServerLifecycleEvents.SERVER_STARTING`). A flag do guia segue o MESMO padrão (`intoxicantes_guia.json`).
- **Comandos:** padrão Brigadier via `CommandRegistrationCallback` (`MoneyCommands`). DECISÃO DO USUÁRIO: o guia NÃO terá comando — acesso só pelo item.
- **Telas:** padrão do projeto é `Screen` própria desenhada 100% por `GuiGraphicsExtractor` (fill/outline/text/item), hit-testing manual, paleta própria — sem textura de GUI e sem widget vanilla (`EsquinaoCardapioScreen`, `PontoTraficanteScreen`, `CentralComandoScreen`). Rede C2S/S2C própria (`EsquinaoNetworking`).
- **Inventário de conteúdo real:** 6 crops (maconha, lúpulo, uva, café, papoula, cevada — todos com `uv_age`); 4 máquinas de prima (moenda, prensa, caldeirão); dorna; alambique; 4 barris; 5 bebidas finais (cerveja, vinho, cachaça, hidromel, rum) + hidromel é crafting simples; intermediários (malte, caldo, melaço, mostos, jovens, bagaço); 6 drogas/ervas + 5 substâncias fictícias; 2 armas (escopeta 12, revólver .38) + 2 munições; 3 NPCs (Gago, Traficante, Juça); blocos de rua (poste, asfalto, faixa, hidrante, placa letreiro, painel LED, lâmpada UV); economia R$ (real, `/saldo`, `/pagar`, fiado, cotação); mercado Esquinão; ponto do Traficante; camisa Matanza; Central de Comando.
- **Receitas:** JSONs em `src/main/resources/data/intoxicantes/recipe/` (44+ arquivos). Para receitas de máquina (cadeia de bebidas), a fonte é `ProcessosBebida`, não os JSONs.

## 1. Decisão de arquitetura (o que será construído)

**Item:** `guia_snc` (namespace `intoxicantes`, padrão snake_case do projeto) — nome exibido: `Guia do SNC Adventures` (lang), lore: `Tudo que você precisa saber para explorar o SNC Adventures.` Item simples (classe própria só para abrir a tela no cliente), stacksTo(1), entra no topo da aba criativa do mod.

**Formato do livro:** tela própria (opção preferida do pedido), seguindo o padrão visual das telas do mod: `GuiGraphicsExtractor`, papel envelhecido (tons papel/sombra/tinta já usados no cardápio + verde da marca), divisórias, ícones de item renderizados com `ItemStack` real (sem PNG duplicado). Nada de livro vanilla escrito — o pedido pede manual navegável e o projeto já tem o padrão de UI.

**Modelo de dados (expansível, regra 32):**

- `GuiaCategoria` — lista ordenada de entradas + ícone + chave de lang.
- `GuiaEntrada` — id, ícone (ItemStack/supplier), páginas.
- `GuiaPagina` — variantes: `Texto` (título+parágrafos), `Cadeia` (passos com ícone+seta+quantidade), `Receita` (grade 3×3), `Maquina` (INPUT↓MÁQUINA↓OUTPUT + tempo), `Tabela` (efeitos, preços, drops), `ItemIndex` (lista de entradas).
- `GuiaConteudo` — registro estático central (como `ProcessosBebida`): monta as categorias uma vez; páginas de bebida/máquina leem tempos/quantidades **direto de `ProcessosBebida`** e a escala `ModConfig.bebidaVelocidade` na hora da abertura — nada de "5 minutos" hardcoded.
- `GuiaScreen` — navegação: menu → categoria → entrada → páginas; botões voltar/início/próx/anterior; hit-testing manual. Busca/favoritos ficam fora da v1.

**Textos:** 100% lang `pt_br.json` (e `en_us.json` espelhado, padrão do projeto), chaves `guia.intoxicantes.*` organizadas por categoria/entrada. Nada de texto cru no código (regra 29).

## 2. Entrega na primeira entrada (regras 3, 5, 31)

- `GuiaPrimeiraVez` — JSON `intoxicantes_guia.json` no mundo (mapa UUID→boolean), init no `SERVER_STARTING` ao lado do `PlayerMoney.init`.
- `ServerPlayConnectionEvents.JOIN` (Fabric API): se `!received` → marcar true + salvar + entregar 1 livro: tenta `player.getInventory().add(stack)`; se falhar (inventário cheio), `player.drop(stack, false)` — nunca apagar. Mensagem discreta + som de pickup.
- **Config:** `ModConfig.guiaNaPrimeiraEntrada = true` (servidor pode desligar a entrega automática).
- Funciona igual em singleplayer e dedicado (tudo server-side, payload não é necessário).

## 3. Recuperação (regra 4 — DECISÃO FINAL DO USUÁRIO)

- **SEM comando.** O guia NÃO é acessível por comando — nada de `/snc guide` (nada de graça; o livro é um item de valor, clássico dos mods). O ÚNICO caminho de acesso/reposição é TER O LIVRO, e quem perde CRAFTA outro.
- Receita de recuperação (ÚNICA via): livro vanilla + real → `guia_snc`. Barata, coerente com a economia do mod ("comprou o guia na banca da esquina") e ensina o jogador que R$ existem.

## 4. Estrutura de conteúdo (cadastrado no `GuiaConteudo`)

Categorias do menu (todas com conteúdo REAL, nenhuma vazia — regra 7):

1. **Começando** — o que é o SNC Adventures, o que fazer primeiro (achar sementes quebrando grama/samambaia/trepadeiras/papoula, primeiros R$ por zumbi/piglin, o Esquinão), visão geral das categorias, como craftar outro livro se perder.
2. **Bebidas** — página do sistema + uma entrada por bebida com cadeia completa lida de `ProcessosBebida`: cachaça (cana→moenda→caldo→dorna→alambique→jovem→barril 600s→4 garrafas), rum (cana→caldo→forna→melaço→dorna→alambique→jovem→barril 600s), cerveja (cevada→malte(smelt)→caldeirão+lúpulo→mosto lupulado→barril 480s+120s), vinho (uva→prensa→mosto→barril 300s+300s), hidromel (crafting direto). Efeitos reais por bebida (lang/lore). Tempos exibidos em "segundos de jogo ÷ 60" ou "minutos", formatados a partir dos valores de `ProcessosBebida` × `bebidaVelocidade`.
3. **Cultivos** — 6 crops: semente (drops de grama/vinha/samambaia-grande/papoula/baga doce/grama-taiga), plantio, estágios (age 0..4 + uv_age), maturação UV (lâmpada UV, raio do config, chance 35%), drops (semente/produto/quantidade dobrada com UV max), para que serve cada um.
4. **Fermentação / Destilação / Barris e envelhecimento** — páginas de sistema explicando dorna (entrada/saída/tempo), alambique (concentra 4→2), barris (fase FERMENTANDO/MATURANDO, rótulo, engarrafamento 1 a 1 com garrafa, 4 garrafas por lote — `ModConfig.bebidaGarrafasPorLote`), com ícones e tempos lidos do registro.
5. **Máquinas** — moenda, prensa, caldeirão, dorna, alambique, barris, lâmpada UV: como fabricar (receita JSON real renderizada em grade 3×3), como usar (input/local/saída), tempo, dica (bagaço como combustível).
6. **Armas** — Escopeta 12 (munição cartucho, tubo 5, reload shell-by-shell, pump, ADS no SHIFT, reparo na bigorna com 2 cartuchos) e Revólver .38 (tambor 6, mais preciso, reparo com cartucho .38). Números críticos só genéricos (dano/alcance por lang), detalhes sensíveis de balance não exagerados.
7. **Munições** — cartucho (polvora+prego+papel, drops de esqueleto 20%) e cartucho .38 (chumbo+polvora+latão, esqueleto 12%): receita, quantidade, arma compatível.
8. **Mobs** — Gago (onde aparece: só no Mercado Esquinão, 24h, comércio, cuidado ao bater — advertência→revólver; cochila fora do expediente), Traficante (spawn periódico aleatório, ponto próprio, fiado, cotação flutuante do config), Juça (Banda Matanza, cigarro Camel, camisa). Sem spoilers completos.
9. **Itens especiais** — ervas/drogas (seda→baseado, ópio, cocaína, heroína, LSD) e substâncias fictícias (pó estelar, cogumelo xamanico, névoa, raiz de sombra, cristal de euforia, extrato de cafeína): efeito/nível/duração reais (da mesma fonte que o tooltip), contrapartidas. Tom fictício/roleplay.
10. **Blocos** — ruas do Esquinão: poste, asfalto, faixa, hidrante, placa letreiro (ABERTO/FECHADO), painel LED + Central de Comando (tecla R/atalhos do jogador via InputConstants — sem número mágico no texto).
11. **Economia R$** — real, `/saldo`, `/pagar`, troca esmeralda↔real, fidelidade do Esquinão (tiers), fiado (juros/quitação/confiança), cotação da rua (min 0.7 / max 1.4), markup para bêbado, ressaca do café.
12. **Efeitos** — tabela resumida de consumíveis → efeitos (consulta rápida; nomes de efeito via chaves vanilla quando existirem).

**Progressão/desbloqueio (regra 25/26):** NÃO na v1. O modelo `GuiaEntrada` já deixa crescer depois (flag por entrada no mesmo JSON do jogador + toast discreto), mas a v1 entrega o guia completo aberto.

## 4b. Confirmações do usuário (decidido)

- **Livro no inventário ao iniciar o mundo**: entrega na PRIMEIRA entrada do jogador naquele mundo (uma vez por jogador, flag persistente). Confirmado: é isso que o usuário quer — o jogador começa o save já com o Guia do SNC Adventures na mão.
- **Acesso EXCLUSIVO pelo item (última decisão, vence):** NÃO existe comando para pegar/abrir o guia — nada de `/snc guide`. Quem quer ler, precisa do livro na mão. Perdeu? **Crafta outro** (livro vanilla + R$). Clássico dos mods: o livro é um item de valor, não um painel gratuito.
- Receita de recuperação (única via): **livro vanilla + Real (R$)** → Guia do SNC Adventures — barata, faz sentido na economia do mod ("você comprou o guia na banca da esquina") e ensina que R$ existem.

## 5. Fluxo de implementação (quando aprovado, seguindo AGENTS.md)

1. `GuiaItem` + registro + assets (`items/guia_snc.json`, modelo, textura 16×16 própria — livro com detalhe verde da marca) + lang pt_br/en_us + aba criativa + `intoxicantes.mixins.json` intocado.
2. `GuiaConteudo` + records de página + categorias (conteúdo do item 4, lendo `ProcessosBebida`/`ModConfig`).
3. `GuiaScreen` (client) + abertura no `use()` do item (client check + `client.execute`), paleta papel/madeira, navegação completa.
4. `GuiaPrimeiraVez` (persistência + JOIN event) + `ModConfig.guiaNaPrimeiraEntrada`.
5. ~~`/snc guide`~~ NÃO haverá comando (decisão do usuário).
6. Receita de recuperação (ÚNICA via de reposição): livro vanilla + real → `guia_snc`, em `data/intoxicantes/recipe/guia_snc.json`.
7. Testes game (padrão `fabricApi` client+server tests): flag única (relog não duplica), entrega com inventário cheio não perde, **craft do livro funciona** (livro + real → guia), NÃO existe comando do guia registrado, servidor dedicado não toca classe client, conteúdo do guia resolve sem crash (validar que toda página referencia Item/Block registrado e chave de lang existente — teste varre `GuiaConteudo` e o lang).
8. `gradlew.bat build` (com `JAVA_HOME` do JDK 25) + `runClientGameTest` (mudança de tela/interação).
9. CHANGELOG + build na MESMA versão atual (pedido do usuário: sem bump obrigatório para esta feature — registrar no changelog a entrada da versão corrente). Diff, commit `feat: adiciona o Guia do SNC Adventures (livro-guia in-game)`, push, tag, Release com o JAR validado — fluxo completo do AGENTS.md.

## 6. Riscos e cuidados

- **Minecraft 26.3:** APIs de tela/grafo de GUI mudaram (`GuiGraphicsExtractor`); copiar padrão das telas existentes do projeto, não de tutoriais de versões antigas.
- **Client-only:** `GuiaScreen` só no entrypoint client; item verifica `FMLEnvironment.dist`/FabricLoader antes de abrir (padrão das outras telas do mod).
- **`ProcessosBebida` é a fonte única** — se as etapas mudarem, o guia acompanha automaticamente; páginas manuais só para texto explicativo.
- **Performance:** construir o conteúdo uma vez (estático) e não por frame; ícones são `ItemStack` baratos.
- **Save:** nada de IDs antigos alterados; só adições (regra 30). Flag persiste em JSON no mundo (sobrevive a relog/restart, igual ao dinheiro).

## 7. Pendências conscientes da v1 (documentar no relatório)

- Busca/favoritos/histórico (opcional no pedido) — fora da v1.
- Páginas desbloqueáveis + notificação de descoberta — fase 2.
- `en_us.json` entra espelhado/simplificado junto (padrão do projeto é manter os dois idiomas); refinamento de tom em inglês pode ficar para depois.
- Qualquer item/sistema que ainda não existir no código NÃO ganha página (regra 15) — na data do plano, tudo listado no item 4 existe; conferir de novo na implementação.
