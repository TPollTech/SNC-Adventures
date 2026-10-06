# CHANGELOG - Intoxicantes Mod

## [MÁQUINAS] Quadro legível e cabos suspensos ponto a ponto (06/10/2026)

- Aplicado o layout aprovado do quadro em 640×388, ajustado ao viewport: quatro módulos separados, medidores próprios, nomes com tooltip, rodapé e Fechar dentro da moldura. Texto sem sombra nem formatação herdada.
- Conector passivo nas faces dos componentes elétricos, bobinas nas cinco bitolas e cabos suspensos com curva pendente. Primeiro clique ancora; segundo conecta até 32 passos de bloco; Shift+clique recolhe e devolve exatamente uma bobina da bitola do trecho. Cabos de bloco existentes continuam compatíveis.
- Elos persistidos por dimensão e sincronizados ao cliente; travessia do quadro considera o vão, carga por trecho e conflitos entre saídas. Origem inválida/em outra dimensão é cancelada sem consumir cobre; conclusão limpa a origem restante; criativo não consome nem duplica bobinas.
- Seleção/colisão do conector corrigidas nas seis orientações. Quebra do terminal corta os elos e devolve cobre; mudanças e recargas revalidam a rede sem forçar chunks descarregados.
- Receitas, modelos voxel, texturas 128×128 e Guia PT/EN incluídos. Corrigida a limpeza de estados energizados no carregamento de chunks sem consulta recursiva ao cache.
- Ganchos mínimos para destravar o cliente: `MarketSystem` não busca mercado natural com geração de estruturas desligada; nomes repetidos dos volumes da moto do Peru recebem IDs únicos no gerador canônico, sem mudar geometria, textura ou articulações.
- Validação e limitações registradas em `verification/20261006-cabos-entrega.md`; a suíte geral inclui falhas de economia independentes, que não foram ocultadas nem tratadas como passes.

## [MÁQUINAS] Legibilidade do quadro — build solicitada e concluída (05/10/2026)

- Fonte da tela preparada com quatro módulos separados em 420×290: nome, alavanca, estado, consumo, A/V, limite do fio e total ocupam áreas próprias. Medidores de entrada separados e dicas em duas linhas, sem escapar da moldura.
- Texto sem sombra e sem códigos de estilo herdados dos nomes; nomes longos abreviados com tooltip completo. Escala ajustada à área disponível com transformação correspondente das regiões clicáveis.
- Fechamento centralizado, clique direito não altera circuito e edição respeita codepoints. Teste de cliente preparado para usar as coordenadas efetivas do novo layout.
- Prévia interativa em `preview/quadro-gui.html`, com fonte bitmap padrão extraída do Minecraft instalado. **Build solicitada explicitamente pelo usuário e concluída**: JAR 1.2.79 atualizado, 171 GameTests de servidor e worldgen aprovados. Cliente visual não repetido; JAR instalado permanece na build anterior. Evidência: `verification/20261005-conferencia-build.log`.

## v1.2.79 — [MÁQUINAS] Rede elétrica funcional (05/10/2026)

- Concluída a revisão elétrica iniciada anteriormente: bulbos 5–20 W, refletores 30–50 W e campânulas 100–200 W exigem alimentação real do quadro, via cabo ou tomada; a chave local não gera energia. O tubo decorativo do mercado (`lampada_led`) permanece separado e não foi convertido nesta revisão.
- Consumo exato por intervalo: 5 W = 10 E/s, 9 W = 18 E/s, 15 W = 30 E/s. Demanda sem alimentação não é cobrada e não causa desarme térmico. Fontes SNC Energies são consumidas pela API pública, com integração opcional sem dependência de produção.
- Cabos preservam a bitola através dos interruptores. Cada trecho é protegido pela carga que passa por ele, sem atribuir toda a carga de outro ramal ao fio fino. Disjuntor: 3 medições de sobrecarga; cabo: 5 medições. Circuitos unidos ou uma carga ligada a dois quadros são bloqueados.
- Remoção de fio/quadro e abertura do interruptor cortam cargas desconectadas. Recarga de chunks revalida estados energizados; quadros são registrados novamente e dimensões não apagam os registros umas das outras.
- Tomadas não consomem vazias e alimentam luminárias LED adjacentes; não servem de passagem para outro cabo. Soquetes aceitam bulbos, devolvem a lâmpada anterior com seus componentes e emitem luz conforme a potência instalada.
- Corrigida a interação real com mão vazia em soquetes e LEDs. O quadro não pausa o mundo em singleplayer, permitindo medição e proteção enquanto a tela está aberta.
- Multímetro consulta a ligação real; removida tradução duplicada que afirmava alimentação com soquete apagado. Diagnóstico de luminárias e taxa fracionária E/t corrigidos; Guia PT/EN atualizado com montagem, ramais, apagões e limites da simulação.
- Evidências e resultados finais: `verification/20261005-eletrica-entrega.md`. Testes em mundos isolados; mundos pessoais e a configuração global do Java não são alterados.

## v1.2.78 — [MOBÍLIA] O SALÃO COM RESPIRO E A MOBÍLIA DE VERDADE (04/10/2026)

### AS 24 GÔNDOLAS E O CORREDOR QUE ABRIU
- As duas ilhas de gôndola mudaram de forma: `COLUNAS_ILHA = (6, 8, 10)` e `(16, 18, 20)`, nas fileiras `FILEIRAS_ILHA = (6, 9)` — **24 blocos** (eram 32, em fileiras coladas z7/z8). Sobrou corredor de 2 blocos no meio (z7/z8), vãos de 1 nas laterais (x7/x9/x17/x19) e o corredor central x13/x14 livre da porta ao balcão. Dá para circular e comprar em qualquer gôndola sem ficar preso no corredor.
- Cada coluna abre sua seção do catálogo peloNOVO `secao_prateleira(x, y, z)` — as 7 seções continuam todas cobertas. A face de venda aponta para o corredor (`north` na fileira z6, `south` na z9) e o `build_nbt` grava `chave_estado="prateleira_norte"` na fileira da frente, para a gôndola nascer com os **18 slots NA FRENTE** (`_prateleira_nbt(secao, comFundo=True)`) — cliente novo vê estoque de frente, não pelas costas.
- Corrigido no caminho: o garrafão estava com o char `G`, **o mesmo das paredes** do salão. Virou `F`, com validação junto (era um bug que só apareceria no NBT).

### A MOBÍLIA VIROU BLOCO DO MOD (o que o usuário mandou fazer)
- Quatro peças remodeladas em 3D real por `tools/gen_mobilia_mercado.py`, **aprovadas visualmente e compiladas**: `engradado_mercado` (caixote ripado aberto com alças vazadas, cantoneiras e etiqueta nas 4 faces, empilhável — 59 elementos), `bebedouro_mercado` (nicho, duas torneiras de alavanca azul/vermelha, bandeja gradeada, encaixe de gargalo — 71), `bebedouro_garrafao` (garrafão de 20L invertido e facetado com nervuras — 480) e `lavadora_mercado` (frontal de verdade: aro, junta, guarnição, vidro com UV projetado, puxador em U, tambor vazado, gaveta, seletor, LCD, botões, rebites, mangueiras e entradas de água nas costas — 382).
- **Bug real corrigido na lavadora:** a chapa frontal cheia tapava o vidro da porta — o modelo ficou sem chapa no contorno e o tambor passou a ser vazado, então dá para ver o vidro por dentro. `tools/auditoria_preview_mobilia.py` trava isso (roda sem gravar nada).
- Colisão: o bebedouro ganhou forma única `Block.box(2.8, 0, 2.3, 13.2, 16, 13.3)` e a lavadora `Block.box(.9, 0, 0, 15.1, 16, 15.4)`, nas 4 orientações — dá para interagir sem acertar cubinho. O dorso central da gôndola abriu (`{1,1,7.5,15,16,8.5}`) para o fundo ser vazado de verdade.
- **Ajuste na API:** `MapColor.COLOR_WHITE` não existe nesta versão (só há 15 cores, sem branco puro) — a lavadora usa `COLOR_LIGHT_GRAY`, o mais próximo de eletro doméstico.

### TESTES E VALIDAÇÃO (tudo verde)
- `python tools/validate_worldgen.py`: **OK — 179 JSONs + NBT** contra o deobf 26.3 (DataVersion 5023). `python tools/auditoria_preview_mobilia.py`: OK.
- `./gradlew build`: **BUILD SUCCESSFUL**, `intoxicantes-1.2.78.jar` gerado e **153/153 gametests server-side passaram**.
- `./gradlew runClientGameTest -PsncClientTest=MercadoClientTest`: o salão do template REAL com as 4 fotos conferidas no jogo — salão geral (as 2 ilhas, o balcão, o Gago de plantão e as geladeiras), corredor central, gôndola de perto (estoque e preços legíveis) e o balcão com o CAIXA e o Gago **com o nome na cabeça**.
- Corrigido no caminho: `MapColor.COLOR_WHITE` quebrava o `compileJava` (build vermelho) — ver acima.

## [MOBÍLIA] Registradora remodelada (04/10/2026, anterior a esta, já integrada)

- **Aguardando aprovação visual; não gerado para o jogo, compilado ou instalado.** Fonte `tools/gen_caixa_mercado.py` remodelada em inox escovado/preto: 32 elementos no balcão e 117 nos equipamentos. Portas/puxadores, gaveta/fechadura, teclado inclinado com teclas individuais, impressora/cupom, LCD do operador, visor de cliente em coluna e terminal de cartão com suporte/cabo.
- Teclado e gaveta voltados ao Gago (+Z local); visor elevado e terminal voltados ao cliente (-Z). Fonte Java preparada com nova posição do total dinâmico e caixas de seleção; ainda sem build/testes.
- UVs corrigidos na raiz: pixels do atlas 128 convertidos para unidades 0..16. Prévia usa rotações dos elementos/pivôs, ilhas de textura, faces ausentes, detalhe dos equipamentos e modo sem textura. `R$ 128` da prévia é exemplo separado do atlas.
- Conferência no navegador: conjunto, operador e lateral; volumes positivos e UVs dentro de 0..16. Isso não substitui revisão no Minecraft após aprovação.
- Segurança do gerador do mercado: garrafão passou de `G` (colidia com paredes) para `F`, com validação correspondente. NBTs anteriores NÃO foram regenerados e continuam pendentes; não distribuir o estado atual antes de integração/validação das outras mudanças de mobília.

## v1.2.77 — O CALÇO DA GÔNDOLA, A MOBÍLIA DAS LATERAIS E O SALÃO EM 3D (03/10/2026)

### O TAPA NA GÔNDOLA TEM SOM DE REGISTRADORA
- Bateu na gôndola com a mercadoria DPC marcada? Agora toca `caixa_registradora.ogg` no **servidor** (`PrateleiraNetworking.tocarCalco`, `SoundSource.NEUTRAL`, volume .45) — o mesmo ogg do pagamento, que era o único som do mercado até agora.
- **Só quando o produto entra no carrinho**: `solicitarCompra` só chama o calço quando `adicionarAoCarrinho` devolve `true`. Tapar em vão (gôndola vazia, lugar já ocupado, item não-comprável) é mudo — sem machine gun de registro.
- O **pitch sobe conforme o carrinho enche daquela gôndola**: `0.94 + min(24, unidades) * 0.022`, onde `unidadesDaGondola` conta as doses que já saíram de lá para o carrinho. Primeira garrafa = calço grave; nona = calço agudinho — dá para saber pelo áudio quando a fila anda.
- Anti-spam: mapa `ULTIMO_CALCO` (UUID → tick) com `CALCO_INTERVALO = 2` ticks, limpo no `DISCONNECT` e no `SERVER_STOPPED` — segurar o clique não vira metralhadora nem vaza memória entre sessões.
- O som do pagamento (`responderCompra`) continua onde estava: o calço é feedback do tapa, o registro é feedback da compra.

### MOBÍLIA NAS LATERAIS DO SALÃO (as paredes ficaram nuas)
- Três peças novas, todas encostadas nas paredes x3 e x23 do salão, fora do caminho do cliente e do freguês:
  - **Engradados empilhados** (`minecraft:barrel`): pilhas de 2 em z4 e pilhas de 3 em z10, dos dois lados.
  - **Bebedouro** (`white_concrete` no corpo + `light_blue_stained_glass` na garrafa): x3 e x23, z10.
  - **Máquina de lavar**: x23, z13 (corpo + painel de vidro).
- O gerador canônico é `gen_mercado.py`: a lista `MOBILIA = (parede, z, [(y, char), ...])` + `_mobilia(y)`, com os chars novos `E`/`F`/`S` na paleta e **overrides por região** (sertão troca o `E` por `smooth_sandstone`, serra por `light_gray_concrete` — sem a máquina virar um bloco de concreto branco chapado no meio do cerrado).
- `validate_worldgen.py` travou o layout: cada peça da lista tem de existir com a cor certa (`CORES_MOBILIA`) e os CORREDORES (x13/x14 no centro e x4..x6/x19..x22 nas laterais) têm de continuar AR em z3..z16. Encher o salão de novo é erro de build, não "detalhe".

### O SALÃO EM 3D (preview navegável gerado do template)
- Novo `tools/preview_mercado_3d.py` gera `preview/mercado-3d.html` (autocontido, three.js embutido) **direto das ROWS do `gen_mercado.py`** — a mesma matriz que vira o NBT do mundo, então o preview não pode divergir do jogo.
- Cor por bloco: amostragem real das texturas do mod com PIL (`3×3` pixels) e `CORES_VANILLA` para os blocos vanilla; 33 blocos distintos, 3 peles regionais (clássico/sertão/serra).
- Recursos: troca de pele, 5 camadas (teto/fachada/pátio/gôndolas/mobiliá), 6 vistas prontas (lote inteiro, salão sem teto, corredor central, balcão + Gago, ilhas de gôndola, mobília lateral), `InstancedMesh` por (cor, geometria), ficha de bloco no clique com realce, Gago em caixa no balcão.
- Detalhes que só aparecem olhando: painel fino é obrigado para vitrine/geladeira/quadro (vira parede preta opaca se for cubo), as lâmpadas de teto (y3) andam junto com a camada teto (senão boiam no salão aberto), e o canvas precisa de `ResizeObserver` porque a aba Preview abre com viewport 0 e a cena some se o `resize` rodar cedo demais.

### TESTES E VALIDAÇÃO
- Build verde: `validate_worldgen` OK (175 JSONs + NBT contra o deobf, DataVersion 5023) e **153/153 gametests server-side**.
- `MercadoClientTest` rodando no cliente: o salão do TEMPLATE REAL com as 4 fotos (`mercado-salao-geral`, `mercado-ilhas-corredor-central`, `mercado-gondola-de-perto`, `mercado-balcao-gago-com-nome`).
- Chunks antigos do mercado não mudam sozinhos: `/gagomarket rebuild` dentro/perto do mercado recoloca o template novo.

## v1.2.76 — O SALÃO DO MERCADO SÓ COM GÔNDOLA DE VERDADE (02/10/2026)

### MOBILIÁRIO DO SALÃO (as "prateleiras" de mentira saíram)
- TIRADAS as gôndolas de quartzo/slab que só PARECIAM prateleira: os dois balcões de quartzo nas laterais (x3..4 e x22..23, z4..15 — 12 blocos de cada lado) e as pontas de quartzo do centro (z6/z9). Eram furniture mudo: o cliente via estoque e não podia comprar nada nelas (playtest).
- No lugar, DUAS ILHAS de gôndola de verdade (z7/z8 costas com costas, z7 = norte, z8 = sul): **4 colunas cada** (x7..10 e x15..18) = 16 blocos, 32 BEs, 8 gôndolas por corredor (4 no salão norte + 4 no salão do balcão). O corredor central (x11..x14) fica livre da porta ao balcão; as laterais (x3..x6, x19..x23) viraram piso livre.
- Cada COLUNA da ilha abre uma seção diferente do catálogo (`secao = base + coluna*2 + (z-7)*2 + (y-1)`, normalizado nas 7 seções): 4 gôndolas iguais lado a lado repetiriam a mercadoria. A fileira ilha (z7) continua nascendo com os 18 slots (frente + seção alternada) e a de trás com 9.
- As geladeiras de vidro (z1) e a vitrine da fachada (z17) foram MANTIDAS (escolha do usuário) — só o quartzo/slab de imitação saiu.
- `validate_worldgen.py` travou o layout: 32 blocos de gôndola nas duas ilhas (facing alternado), 18/9 slots de estoque em toda gôndola e **nenhum quartzo/slab no salão fora do balcão** (x8..12, z12) — a prateleira de imitação não volta.

### O GAGO SÓ ATENDE NO BALCÃO (o guichê da madrugada foi extinto)
- O plantão não muda mais com o relógio: `gerenciarGago` sempre ancora em `getPosBalcao` (v1.2.51 usava balcão de dia e guichê na porta às 00:00). Chegou a ser a única regra do gerenciador; o `naPorta` saiu do caminho do destino.
- A PORTA-GRADE não fecha mais sozinha (nem no zelador da madrugada, nem ao ser colocada/reformada): o mercado é 24h como o letreiro "ABERTO · 24H" e como o cardápio já prometiam. Fechar a grade é na mão do dono (clique direito), e as mensagens PT/EN falaram disso.
- Mortos no caminho: o anúncio "vou atender lá na porta" (virou "o Gago arruma o balcão e volta pro plantão" no resgate de Gago preso) e o `viradaDeHorario` (código morto desde a v1.2.44).
- `MarketOverhaulGameTest` foi junto: `gagoAncoraNoPostoDaVirada` virou `gagoAncoraNoBalcao` (e passou a exigir o nome na cabeça), `guicheFechaEBaixaColisao` virou `portaGradeFechaNaMaoEBaixaColisao` (a colisão da grade continua testada), e `CommerceGameTest` usa o balcão direto.

### O NOME DO GAGO NA CABEÇA
- Todo Gago nasce com o nome "Gago" visível acima da cabeça, igual o nome que os jogadores veem nos outros (a chave `entity.intoxicantes.gago`, traduzida). Reposto no `readAdditionalSaveData` porque o `Entity.load` do vanilla zera o CustomName quando a tag não está no NBT (template, ovo e save).
- `removeWhenFarAway` trocou o critério "tem nome" por "está de plantão": o dono da esquina nunca some com a chunk longe e um Gago solto longe do Esquinão continua sumindo.

### TESTES E VALIDAÇÃO
- NOVO `MercadoClientTest` (client gametest): coloca o TEMPLATE REAL (`structure/mercado_gago.nbt`) no mundo de teste e confere o salão bloco a bloco — 32 gôndolas, todo slot de estoque nasceu cheio, zero quartzo/slab de imitação, CAIXA nas duas metades do balcão, posto livre, Gago de plantão (NoAI, `estaDePlantao`) com nome visível — e tira 4 fotos do salão novo (`mercado-salao-geral`, `mercado-ilhas-corredor-central`, `mercado-gondola-de-perto`, `mercado-balcao-gago-com-nome`).
- Build verde: 153/153 gametests server-side + worldgen validado (175 JSONs) + o novo client gametest.
- Chunks antigos do mercado não mudam sozinhos: `/gagomarket rebuild` dentro/perto do mercado recoloca o template novo.

## v1.2.75 — A ilha de meio bloco e o CAIXA com registradora (02/10/2026)

### GÔNDOLA ILHA (meio bloco por lado, produtos na FRENTE e ATRÁS)
- A gôndola de mercado tem MEIO BLOCO de profundidade POR LADO: expositores na frente (-Z local, com etiqueta) e no FUNDO aberto (+Z local) — o corredor dos dois lados compra do mesmo móvel. Tampas de prateleira contínuas, dorso central, parafusos nas quatro arestas.
- 18 slots na BE: 0..8 (frente) + 9..17 (fundo). A mira escolhe o slot pela FACE clicada (hit.getDirection × FACING.getOpposite); colisão/seleção giram com o FACING como antes. Reposição manual e modo gerente funcionam nos dois corredores.
- A entrega de quarta (CalendarioEsquinao) abastece os dois lados: o fundo recebe a SEÇÃO ALTERNADA (secao+1 — a fileira dupla do salão não repete mercadoria entre corredores). Carrinho (PrateleiraNetworking) carimba o lado nas linhas; compra/revalidação/rollback intactos.
- Marcações do servidor: `marcarComercial` persiste sem reenvio do client sync (o clique não reenvia a BE inteira), `sincronizarVenda`/`sincronizarTudo` reenviam com estoque/reservas. Migração de save: 9 slots legados continuam valendo; o fundo nasce na próxima entrega.
- Template do salão (gen_mercado): a fileira do corredor central (z=7) nasce ILHA ABASTECIDA nos 18 slots; o validador (validate_worldgen) confere 18 slots/seção alternada na fileira ilha e 9 nas demais.
- BUG DO CLIQUE VAZIO (o client test achou): no 26.3 o clique com a mão VAZIA chega como `useItemOn` com stack=air, e o SUCCESS do bloco consumia a interação — `useWithoutItem` nunca rodava e o clique vazio NUNCA anotava no carrinho no jogo real. Fix em `PrateleiraMercadoBlock.useItemOn`: mão vazia desvia direto pro `anotarNoCarrinho` (caminho comum também usado pelo `useWithoutItem`).

### BALCÃO COM REGISTRADORA 3D E O GAGO NO POSTO
- NOVO BLOCO `caixa_mercado` (duas metades, DoubleBlockHalf): corpo de laminado com rodapé de aço embaixo; em cima, tampo de pedra escura e a REGISTRADORA 3D de frente pro freguês (visor verde com moldura, teclado e gaveta de dinheiro). Gaveta visível no bloco e no visor da câmera do freguês.
- O visor mostra o TOTAL DO CARRINHO do jogador: o servidor manda `CarrinhoResumoPayload` e o CaixaMercadoRenderer desenha "R$ total" em tempo real (a textura é só a moldura — o número é renderizado).
- O Gago fica FIXO atrás do balcão: `CaixaMercadoBlock.vincularAtendente` cola o Gago mais próximo no posto (`posAtendente`, +Z oposto ao FACING) com NoAI (`setupPostoMercado`); ticker a cada 40 ticks reconecta. Quebrou o balcão? Soltou o Gago e o posto refaz.
- FLUXO DA COMPRA: o TAPA (botão ESQUERDO) na gôndola ANOTA no carrinho (uma dose do slot); SHIFT+tapa anota a fileira. O clique DIREITO no caixa abre a tela: `abrirCaixa` cota o carrinho, a tela mostra as linhas com +/- de dose, total, saldo e confirmação. Debita 1x, entrega tudo; estoque/preço/estoque mudou → compra recusada sem débito; rollback completo (itens, inventário e dinheiro) em falha.
- Acabamento aprovado no estúdio (prévia antes/depois): laminado CINZA-NEUTRO com faixa de batida (o bege amarelado do ANTES ficou nas texturas `_antes` só pro estúdio), registradora com 6px de altura (o visor verde aparece do outro lado do balcão) e GAVETA saliente 0,2px com puxador em barra central fina. Corrigido UV fora dos limites no pedestal.
- O CAIXA entrou no TEMPLATE do mercado: `gen_mercado` nasce com o balcão em (10,1..2,12) facing south — EXATAMENTE na frente do posto (10,1,11) = `BALCAO_LOCAL` do MarketSystem, então o Gago do gerenciador atende no próprio caixa. `validate_worldgen` confere as duas metades (half/facing) e o posto em ar.
- Gerador canônico `tools/gen_caixa_mercado.py` (5 texturas 128×128 espelhadas no pack + blockstate 8 estados + 2 modelos + item + items definition + loot table que só dropa na metade de baixo). Lang PT/EN completas (`caixa.intoxicantes.*` + bloco).

### O GESTO É O TAPA (BOTÃO ESQUERDO) E O VISOR SOBE NA HORA
- COMPRAR NA GÔNDOLA = TAPA com o BOTÃO ESQUERDO, no estilo My Summer Car: cada tapa no produto soma UMA DOSE e o VISOR da registradora, virado pro freguês, mostra o total em tempo real (R$ 5 → R$ 10 → R$ 0 depois de pagar). O `AttackBlockCallback` (o mesmo que trata a pedrada no mercado) intercepta o soco na gôndola e devolve `SUCCESS_SERVER` cancelando a mineração.
- `PrateleiraMercadoBlock.anotarTapado` refaz a MIRA NO SERVIDOR (o callback só traz bloco+face): `level.clip` com o MESMO vetor de olhar e alcance 6 do cliente, com fallback no centro da face recebida, e só então chama `solverCompra`. Sem esse refaz a anotação pegaria o slot errado sempre que o jogador mira a etiqueta de lado.
- SHIFT+esquerdo (ou criativo) NÃO compra: quebra a gôndola do jeito normal (a obra). O clique DIREITO na gôndola virou PASS (não compra, não repõe com a mão vazia); com item na mão ele continua repondo o slot da mira e SHIFT+item recolhe no modo gerente. Tapa no CAIXA nunca é vandalismo.
- O `useWithoutItem` e o `useItemOn` com mão vazia da gôndola não consomem mais nada: comprar é SÓ o tapa, o resto do gesto é o vanilla. Guia PT/EN reescrito com o gesto novo, o visor ao vivo e o SHIFT quebrando a gôndola.

### TELA DO CARRINHO
- `PrateleiraCompraScreen` reescrita: lista do carrinho com botões +/− por dose e REMOVER (envia `AtualizarCarrinhoPayload`, o servidor RECOTA e reenvia), scroll, total/saldo ao vivo e confirmação/cancelamento. Botão travado quando falta saldo; título/moldura no estilo do mercado.

### TESTES E VALIDAÇÃO
- NOVO client gametest `CarrinhoClientTest` (roda no MINECRAFT de verdade, `runClientGameTest`): TAPA real (botão esquerdo) anota no carrinho e o visor da registradora mostra R$ 5 → R$ 10; o direito no CAIXA abre a tela; CONFIRMAR debita R$ 10, entrega 2 doses, baixa o estoque e zera o carrinho (visor volta a R$ 0); depois um tapa novo, `+` recota 1→2 e `−` devolve 2→1 sem debitar. A etiqueta do slot mirado é cravada no cenário (item + `definirPreco`) e todas as expectativas derivam da dose real, então o teste não depende do dia do calendário. Screenshots `carrinho-visor-5/10`, `carrinho-aberto`, `carrinho-pago`, `carrinho-ajustado` em `build/run/clientgametest/screenshots/`.
- `IdentidadeClientTest.validarPrateleiras` migrado pro gesto novo: cada gôndola do cenário ganha um BALCÃO com Gago no posto (o gesto de compra é a gôndola, a tela é o caixa), o TAPA entra por `keyAttack` e o direito no balcão abre a tela; `responderPrateleira` tira o centro dos botões da PRÓPRIA tela (`botaoConfirmarParaTeste`/`botaoCancelarParaTeste`) em vez do offset antigo `height/2+63` — que, desde a reescrita da tela (ALTURA 228), clicava fora do botão. As 4 faces × 9 slots (tapa → cota → cancelar → confirmar) passam com screenshots `prateleira-confirmacao-*`.
- Novo gametest server-side `fluxoDoCaixaAnotaCotaEAtualiza`: anota → cota → `atualizarCarrinho` +2/+1/0 valida dose, total e recotação sem débito.
- A `Oferta` carimba o ESTOQUE da cotação: confirmação revalida `pilha.getCount() != oferta.estoque()` → recusa (orçamento velho não vale mais depois de venda concorrente).
- Gametests atualizados pro fluxo do caixa: loja de teste com prateleira + balcão + Gago fixado no posto (`plantarLoja`), carrinho → orçamento → confirmação; índice inválido, distância, prateleira quebrada, saldo insuficiente e replay de token continuam cobertos.
- Prévia do estúdio (preview/prateleira.html) com a ILHA (frente+fundo, hitboxes dinâmicas), o BALCÃO/REGISTRADORA antes/depois e o bug do `texture_size` do three.js corrigido (gaveta aparecia preta).
- Build verde: 153/153 gametests server-side + client gametest do carrinho (tapa/visor/pagamento) + suíte de identidade no trecho do mercado + worldgen validado (175 JSONs).
- Pendência fora do gesto: `IdentidadeClientTest.validarAlimentos` reprova no `pao_cevada` (volume do modelo 3D x a caixa física da bebida decorativa) — falha anterior a esta mudança (nenhum screenshot `alimento-*` existe de execução anterior, porque o pack `minhas-texturas` faltava no cliente de teste e o teste morria antes).

## v1.2.74 — Instalação elétrica modular (01/10/2026)

- Módulos 3–6 da instalação: quadro com quatro saídas/circuitos, proteção contra sobrecorrente e bitolas de 1,5 a 10 mm²; tomada, multímetro e painel elétrico detalhados no Guia SNC PT/EN.
- Gametests cobrem ampacidade, rotas independentes, queda por sobrecarga de cabo, energização da tomada e leitura dos componentes; a integração com SNC Energies permanece opcional, isolada por reflexão.
- Gerador `tools/gen_eletrica.py` executado para recursos e receitas elétricas. A validação real do mod confirmou a tradução de `coqueiro_tronco` como “Tronco de Coqueiro” no tooltip.
- Correção da fila de reconstrução: processar um snapshot evita modificação concorrente quando a recomposição da topologia agenda novos rebuilds.
- Validação: 148/148 gametests, client gametest isolado do tooltip e validação de worldgen concluídos. Instalação do JAR pendente de autorização para incluir as demais alterações compartilhadas do checkout.

## v1.2.74 — Prateleiras alinhadas e mercado completo (01/10/2026)

- Gôndola ocupa um bloco inteiro, com três níveis e nove expositores apoiados, escala uniforme e costas encostadas nas fileiras opostas.
- Giro pelo centro nas quatro orientações, seleção alinhada ao móvel, colisão estrutural e mira consistente por produto/etiqueta. Estoque, preços, entregas e dados salvos dos nove slots preservados.
- Guia PT/EN atualizado; prévia abastecida e duas peças empilhadas no estúdio aprovadas pelo usuário antes da geração dos recursos e da build.
- Mercado ampliado nas fontes: oito bebidas e 50 alimentos/ingredientes culinários, com preços e doses canônicos. Sete seções distribuem os produtos nas gôndolas; reposição por ID salvo, conversão de compras para unidades e limite de pilha. Reposição manual de produto catalogado aplica o preço real, e pacote incompleto cobra a fração entregue.
- Pão de cevada, coco e uva passam a usar a exposição de mesa com dimensões próprias, retorno do ItemStack e consumo original. Uva permanece ingrediente. Migração preserva estoques antigos, inclusive a agenda de slots esgotados. Guia/tooltip PT e EN documentam compras, reposição e colocar/recolher.
- Aba de exclusivos usa a fronteira atual do catálogo e envia o índice correto nas compras; alimentos comuns continuam acessíveis sem fidelidade.
- Etiquetas mostram preço e dose reais; compras da prateleira pedem confirmação, com revalidação no servidor e cancelamento sem débito. Agachar+correr abaixa a câmera para espiar os níveis inferiores.

## v1.2.73 — BUGS DO PLAYTEST: O PACOTE COMPLETO (01/10/2026)

O PDF "Bugs SNC Mod" listou 19 defeitos do playtest. Os lógicos entraram em
v1.2.70-72; esta versão fecha o restante, incluindo os pendentes de ativo.

- **BUG 10 (A CAMISA DO MATANZA ERA O PEITORAL DE COURO):** a definição do item
  já apontava pro equipment asset `intoxicantes:camisa_matanza`, mas o JSON e a
  textura NUNCA existiram — o 26.3 caía no default LEATHER (cinza tingível).
  Criados na raiz: `equipment/camisa_matanza.json` (camadas humanoid/humanoid_baby
  SEM `dyeable`, mesma forma do iron.json) + textura humanoid 64×32 (torso preto
  com costuras e gola em V, caveira branca de olhos vermelhos, mangas completas;
  pernas transparentes — é camisa, não calça). Gerador novo `tools/gen_camisa_equip.py`.
- **BUG 3 (A MOEDA DE R$ 1 TRANSPARENTE NO MEIO):** no gerador, o degradê do aço
  era desenhado no canvas errado e a máscara do disco colava um canvas VAZIO — o
  miolo nascia semitransparente e o brilho diagonal vazava num quadrado cinza.
  Correção na raiz do `gen_dinheiro.py` (degradê no aço de verdade + clip final
  pelo disco via putalpha×máscara). Jar e pack com a mesma arte.
- **BUG 11 (A MIGALHA DO PÃO MOSTRAVA LETRA DE RÓTULO):** a partícula dos itens 3D
  apontava pro atlas INTEIRO da garrafa — migalha amostrava pixel aleatório e
  caía no rótulo "PAO CEVADA". Todos os 18 itens 3D de garrafa (bebidas, vazias,
  intermediários, pão de cevada, coco) ganharam textura dedicada
  `<id>_particula.png` (tile "cap" do atlas) e o modelo aponta pra ela.
- **BUG 17 (TEXTO ILEGÍVEL NAS TELAS):** no Ponto do Traficante, a TINTA_FRACA
  bege (8A7B5A) tinha 0,97:1 de contraste sobre o asfalto — subiu pra CBB896
  (6,9:1); o VERMELHO de dívida/saldo faltante (B03030, 1,7:1) subiu pra E06A52
  (4,6:1). No cardápio do Gago (papel claro), a tinta fraca virou castanho 6B5B3A
  (5:1 sobre o papel); o vermelho lá já era legível.
- **BUG 6 (O CAFÉ VERDE COM FURO):** fechado pelo catálogo 3D (atlas 128 com zero
  buracos internos); o sprite manual 256 ficou órfão. O consertor de vãos internos
  do `gen_farm_textures.py` foi reescrito (detector geométrico de componentes
  fechados + inpainting em cascas) e escopado a whitelist vazia — o detector
  agressivo confundia RECORTE DE DESIGN com defeito (hidrante e faixa têm vãos
  intencionais). Pra consertar outro manual: 1 linha na whitelist `CONCERTOS`.
- **BUG 16 (GUIA DESATUALIZADO):** fiado corrigido em pt/en (juros POR COMPRA:
  10% sem lançamento, 30% na confiança, 0% na palavra — não "10% por dia");
  cotação é do PONTO do traficante (markup bêbado/desesperado é do balcão do Gago,
  teto +30%). Números conferidos contra `TraficanteEntity`.
- **TESTE DO GOLE DESATUALIZADO:** o `BebidasClientTest` (client gametest) esperava
  a GARRAFA DE VIDRO VANILLA como sobra do consumo — desde a v1.2.65 cada bebida
  devolve A GARRAFA VAZIA DELA (USE_REMAINDER). O teste agora percorre o par
  certo (cerveja→cerveja_vazia, vinho→vinho_vazia, …) e valida interrupção e
  consumo completo contra o recipiente real do jogo.

## v1.2.72 — Identidade dos recursos (01/10/2026)

- Fontes e prévia para toda a auditoria: cacho da uva compartilhado entre item/parreira, coco pendurado/colhido coerente, luminária UV única e poste completo no inventário.
- Catálogo voxel original com atlas128: Guia, Central de Comando, camisa, três ovos, ingredientes e consumíveis. Malte ganha grãos processados distintos da cevada.
- Porta-grade: grade superior no ícone e sincronização dos materiais no pack. Prateleira: etiqueta com UV correto e acabamento128.
- TuboLED, dez potências e quatro componentes elétricos com materiais e detalhes próprios. Caixas de seleção da UV, dos cocos e das potências acompanham as peças.
- Geradores históricos preservam a propriedade do catálogo; geração antiga exclusiva de ópio absorvida pelo catálogo canônico. Guia da parreira atualizado em português e inglês.
- **Estado:** estúdio `preview/identidade.html` aprovado explicitamente pelo usuário em01/10/2026; recursos finais gerados no escopo aprovado. Build, testes reais e instalação em validação.


## v1.2.71 — HOTFIX: NOME CRUO NO TOOLTIP (TRONCO/FOLHAS DE COQUEIRO) (01/10/2026)

- **O TRONCO E AS FOLHAS DE COQUEIRO mostravam a chave crua** `item.intoxicantes.coqueiro_tronco`
  no tooltip, em vez de "Tronco de Coqueiro". Causa na raiz: no 26.3 o BlockItem deriva a
  chave de tradução do registry key com prefixo `item.` POR PADRÃO — e o lang tinha só a
  chave `block.*` (a canônica). O mesmo defeito latia no coco_bloco e nos blocos elétricos
  novos (cabo, soquete, interruptor, quadro).
- **Correção na raiz:** `useBlockDescriptionPrefix()` nos 3 pontos que criam BlockItem no
  IntoxicantesMod (os 2 `registerBlockWithItem` e o `registerBarril`). As SEMENTES seguem
  no prefixo `item.*` de propósito (a chave canônica delas é item).
- **Bônus:** 3 nomes mostrados ao jogador voltam ao canônico do lang — Caldeirão de Mostura
  (era "Caldeirão de Cerveja"), Dorna de Fermentação (era "Barril de Fermentação"), Moenda
  de Cana (era "Esmagadora de Cana").

## v1.2.70 — SISTEMA ELÉTRICO + INTEGRAÇÃO SNC ENERGIES (01/10/2026)

- **A REDE ELÉTRICA DO SNC ADVENTURES CHEGOU**: quadro elétrico, cabo de cobre,
  interruptor simples e soquete de teto formam uma instalação de verdade, com a
  energia vindo OFICIALMENTE do SNC Energies (fogão a lenha, gerador a carvão,
  Energy Cube). O mod continua funcionando sozinho sem o SNC Energies — integração
  opcional por reflexão (adapter único `energia/SNCEnergiesAdapter`), zero
  dependência de build/runtime, no contrato do PROGRESSAO.md deles.
- **Grandezas como na vida real:** W = E/t × 10 (o fogão a lenha deles entrega
  ~800 W na entrada). Circuitos com disjuntor de 10/16/20/25/32/40 A em 127 ou
  220 V; sobrecarga de 3 s desarma e o rearme é manual no quadro.
- **Quadro elétrico (o cérebro):** buffer-medidor de 10.000 E, 4 circuitos prontos
  (iluminação 10 A/127, tomadas 20 A/127, oficina 25 A/220, área externa 16 A/127);
  extrai energia das fontes adjacentes, mede consumo por circuito e acende/apaga os
  soquetes (tick da rede 1×/s). Topologia reconstruída por BFS nos cabos — interruptor
  desligado é parede elétrica.
- **Cabo de cobre 2,5 mm:** bloco PASSIVO (sem ticker/BE) que conecta visualmente em
  cabos, quadro, soquete, interruptor e fontes do SNC Energies (multipart núcleo +
  6 braços, sem colisão). **Soquete de teto:** veste qualquer LED da família 5–200 W
  (clique instala/troca/remove; só consome da rede quando aceso). **Interruptor
  simples:** liga/desliga a travessia.
- **Receitas com cobre:** cabo 2×2 → 8; soquete, interruptor e quadro (III/ICW/III).
- **Gametests com o mod real:** 134/134 verdes COM o `snc-energies.jar` no classpath
  de gametest — incluindo a cadeia completa: gerador a carvão DELES → quadro → cabo →
  soquete → LED aceso com CONSUMO REAL de energia no buffer deles. Sem o jar, o
  adapter degrada com graça (tudo 0/false, nada crasha).

## v1.2.70 — HOTFIX: O GATILHO ESQUERDO DAS ARMAS (30/09/2026)

- **A ESCOPETA E O .38 NÃO ATIRAVAM com o botão esquerdo** (nenhum tiro nos
  logs desde a v1.2.57). Causa na raiz: o MouseBotaoMixin comparava o botão
  do mouse com números GLFW (esquerdo = 0, direito = 1), mas o 26.3 usa
  entrada SDL e entrega os botões em códigos SDL (esquerdo = 1, direito =
  3 — os mesmos InputConstants.MOUSE_BUTTON_* que as telas do mod já
  usavam). Resultado: o clique ESQUERDO caía na comparação do direito, era
  engolido e o GatilhoPayload nunca saía; o direito passava reto e virava
  "usar" vanilla (colocar bloco etc.).
- Fix: comparação pelos InputConstants.MOUSE_BUTTON_LEFT/RIGHT (regra do
  AGENTS.md — nada de número mágico de GLFW com a entrada SDL) e a ação de
  pressionar documentada como constante (SDL: 1 = pressionar, igual o
  vanilla usa no onButton).
- **ADS consertado de verdade:** como o evento do direito é engolido pelo
  mixin, o vanilla NUNCA seta isRightPressed() — o mirando() lia sempre
  false e o zoom/dispersão/alcance do botão direito nunca ligavam. Agora o
  mixin grava a borda do botão direito num espelho no ArmasClient
  (definirDireitoPressionado) e o mirando() lê esse estado.
- Novo ArmasClientTest (ClientGameTest): aperta o clique ESQUERDO do mouse
  DE VERDADE, espera o tiro no servidor, valida o pump automático, o 2º
  disparo e o ADS do direito ligando/desligando. Se este teste falha, o
  gatilho quebrou de novo.

## v1.2.69 — HOTFIX GUI + PRATELEIRAS + CALENDÁRIO + LÂMPADAS POR POTÊNCIA (30/09/2026)

- FAMÍLIA DE LÂMPADAS LED POR POTÊNCIA (v1.2.69/70): dez potências como na
  vida real — 5, 9, 12, 15 e 20W (BULBO residencial), 30 e 50W (REFLETOR do
  quintal), 100, 150 e 200W (HIGH-BAY industrial do galpão). A luz ESCALA
  COM O WATT (7 → 15, os 15 níveis do Minecraft: a 200W ilumina como o sol;
  o bulbo tem zeroGlow — a torch é quem dona do glow 2). O clique
  LIGA/DESLIGA (o liga/desliga que ficou pra depois na 1.2.68); lâmpada
  morta é eterna (manutenção = craftar outra); troca rápida segurando
  outra potência e clicando. Receita em SÉRIE: 5W = vidro+tocha; cada
  degrau usa o anterior + vidro + glowstone (bulbo), ferro (refletor) ou
  diamante (high-bay) — a potência CUSTA mais. Gerador canônico:
  `tools/gen_lampada_led_potencia.py` (10 modelos 3D com cúpulas por
  família, 20 texturas espelhadas no pack, loot e receitas). O tubo do
  teto do mercado (`lampada_led`, sempre aceso) continua intocado.
- PRATELEIRA DE MERCADO (`prateleira_mercado`, novo bloco): a gôndola do
  Esquinão que VENDE — estrutura de metal cinza, 3 prateleiras brancas e
  etiqueta amarela de preço, modelo 3D canônico gerado por
  `tools/gen_prateleira.py` (3 texturas espelhadas no pack + blockstate
  FACING + items definition + loot). O template do mercado nasce com DUAS
  fileiras no corredor central (x9..10 e x14..15, z7..8, facing=south —
  olhando pra porta), já ABASTECIDAS pelo NBT de bloco (estoque de partida
  do cardápio do Gago: 8 bebidas + 2 sementes, formato Items do 26.3).

- PRATELEIRA DE MERCADO (`prateleira_mercado`, novo bloco): a gôndola do
  Esquinão que VENDE — estrutura de metal cinza, 3 prateleiras brancas e
  etiqueta amarela de preço, modelo 3D canônico gerado por
  `tools/gen_prateleira.py` (3 texturas espelhadas no pack + blockstate
  FACING + items definition + loot). O template do mercado nasce com DUAS
  fileiras no corredor central (x9..10 e x14..15, z7..8, facing=south —
  olhando pra porta), já ABASTECIDAS pelo NBT de bloco (estoque de partida
  do cardápio do Gago: 8 bebidas + 2 sementes, formato Items do 26.3).
- Compatibilidade TOTAL de item (o pedido do playtest): 9 slots genéricos —
  clique com QUALQUER item repõe o slot mirado (garrafa, semente, o que
  der); shift+clique com item é modo gerente (recolhe a pilha); clique
  vazio COMPRA o produto do slot (cobrado no PlayerMoney); shift+clique
  vazio leva o carrinho inteiro. A mira escolhe o slot pela coluna/altura
  do hit, girando com o FACING. Produto com preço de tabela cobra a
  etiqueta (a mesma DOSE do balcão: 1 garrafa, 4 sementes por R$ 4);
  produto do dono sem preço sai de graça.
- REESTOQUE SEMANAL estilo My Summer Car: as prateleiras repõem o estoque
  de partida SÓ no dia de entrega, QUARTA-FEIRA — a virada é detectada no
  tick da própria BE (dia comercial 07:00, portão 1x/dia) e o caminhão
  COMPLETA cada pilha da agenda até o stock diário do balcão. Anúncio no
  chat 1x/semana pra quem está no mercado (MarketSystem).
- CALENDÁRIO DO ESQUINÃO (`CalendarioEsquinao`): dias da semana derivados
  do dia comercial (sábado = dia 0 do mundo; quarta = dia de entrega),
  sem NBT — nunca desincroniza. O relógio do HUD mostra a abreviação
  ("qua 14:32").
- Gametests: +6 (123 no total, todos verdes) — calendário, venda/etiqueta,
  aceitação de qualquer item, reestoque de quarta 1x/dia e recorrência
  semanal, drop ao quebrar e mira por slot. `validate_worldgen.py` trava
  as prateleiras do template (base+corpo, facing, NBT abastecido).
- Clique direito na prensa de uvas (e em TODAS as máquinas: moenda,
  caldeirão, dorna, alambique, barril) dava erro no servidor e a GUI nunca
  abria — em qualquer mundo/máquina, o mesmo log: "Extended menu
  intoxicantes:maquina_snc must be opened with an ExtendedMenuProvider!".
  Causa: o menu "maquina_snc" é um ExtendedMenuType do fabric-menu-api-v1 (o
  TipoMaquina viaja como payload de abertura), mas as quatro BlockEntities
  eram MenuProvider simples — o Fabric exige ExtendedMenuProvider, que entrega
  o payload ao client. Fix: implementar ExtendedMenuProvider<Integer> nas
  quatro BEs (getScreenOpeningData → tipoMaquina().indice()); nada muda na
  tela, que já lia o tipo do menu.
- LAYOUT DA GUI (apareceu com a GUI abrindo): o painel ancorava no canto
  inferior direito da tela — offset DUPLO, o 26.3 já aplica o translate
  leftPos/topPos na pose antes dos hooks extract* e a tela somava de novo;
  as setas de paginação invadiam o JEI no topo e a hotbar cortava na borda.
  Fix: lx/ly viram coordenadas DE PAINEL (identidade) e o mouse converte com
  fx/fy; IMG_H 222→226 (hotbar cabe inteira). Validado por ClientGameTest
  novo (GuiMaquinasClientTest): abre a GUI, garante leftPos=(largura−208)/2
  e topPos=(altura−226)/2, shift de uvas real no slot e screenshots
  (preview/maquinas-gui/).
- CRASH DE RENDER (mesmo teste expôs): máquina PROCESSANDO no mundo
  derrubava o client — "Missing elements in vertex: UV2, UV3, Normal,
  LineWidth". O translucentMovingBlock do 26.3 exige o formato completo que
  o vanilla só alimenta via quad machinery; o faceLiquido alimentava só
  posição+cor. Fix: líquido migra pra entityTranslucent com o atlas LED da
  casa e vértice completo (cor + UV + overlay + luz do mundo + normal) —
  o padrão comprovado do ParreiraRenderer. Teste abre GUI, carrega 12 uvas,
  lote inicia e renderiza sem crash.

## v1.2.68 — SEMENTES SNC E CACHOS INDIVIDUAIS (30/09/2026)

- Seis saquinhos 3D originais: tecido cru, costuras, boca franzida, cordão,
  selo SNC e símbolos botânicos distintos. Gerador canônico com atlas 128×128;
  geradores antigos deixam de sobrescrever os modelos das sementes.
  Perspectivas de mão ajustadas no Minecraft para mostrar o saco inteiro
  e manter o rótulo visível em primeira e terceira pessoa.
- Cada cacho desenhado tem uma área de clique, estado, UV e rebrota próprios.
  Um clique entrega uma uva e remove só aquele cacho. É possível continuar
  colhendo enquanto segura as uvas. Raiz e coluna não colhem o teto inteiro.
- Estado individual salvo no mundo, migração do timer antigo, produção nova
  em expansões e pausa sem luz/apoio. Poda não elimina o tempo já iniciado.
  Quebra da raiz considera somente os frutos ainda presentes.
- Guia, lore e traduções PT/EN acompanham a mecânica. Prévia dos seis modelos
  e demonstração interativa da colheita separada aprovadas pelo usuário.
- GameTests cobrem colheita individual, inventário cheio em sobrevivência,
  quebra da raiz pelo jogador, rebrota, UV, poda, migração e persistência.
  ClientGameTests conferem os seis modelos no inventário e nas mãos, plantio
  real das seis culturas, colheita de cachos por clique e o Guia no Minecraft.
- Wrapper do Gradle no Windows preserva o código de saída: um build ou
  descoberta de Java que falhe deixa de ser reportado como sucesso pelo BAT.
- PÁTIO DO MERCADO SEM SENTIDO (playtest): o template v2 (1.2.61–67) nascia
  com hidrante NO MEIO do asfalto (10,20), QUATRO postes DENTRO das vagas
  (3/23, 20/23) e a travessia cruzando o estacionamento inteiro (z19..25).
  Agora o hidrante fica na CALÇADA ao lado da travessia (11,19), a travessia
  existe só na calçada (alinhada com a porta) e os 2 postes vão pros cantos
  do lote (0/26, 19) — nenhuma vaga morre. O zelador RECOLHE o legado bugado
  dos mundos antigos (hidrante/postes em vaga, tinta no asfalto) em vez de
  plantar/pintar de volta; validate_worldgen trava o template novo contra
  regressão (pátio sem obstáculo, travessia na calçada, postes nos cantos).
- VIDROS ALEATÓRIOS no salão (playtest): a vitrine das gôndolas nasceu como
  vidro-bloco cheio (o conserto do pane da 1.2.67) e virava cubo de vidro
  flutuando no meio do mercado. Vitrine removida — gôndola é slab + quartzo,
  prateleira aberta; validate_worldgen trava qualquer vidro cheio no interior.
- LÂMPADA LED (novo bloco): tubo de LED convencional de teto igual ao da vida
  real — difusor branco leitoso, ponteiras de alumínio, suspenso por hastes.
  Luz nível 15, SEMPRE acesa por enquanto (liga/desliga e energia ficam pra
  depois). Três fileiras cruzam o salão (z5/z10/z14) no lugar da vitrine.
  Gerador canônico tools/gen_lampada_led.py (texturas + modelos + loot +
  item definition + espelho do pack), entrada no Guia e gametest de luz cheia.

## v1.2.67 — PARREIRA DE UVA (30/09/2026)

- A uva é uma videira perene: plante na terra ou terra arada, conduza por
  cercas ao lado (até 3 blocos) e conecte uma cobertura de até 5×5 no alto.
  Ramos, folhas e cachos envolvem a armação sem substituir as cercas.
- Mão principal vazia na raiz ou em uma cerca coberta colhe uma produção
  por raiz: 1 uva, ou 3 no UV máximo. Folhas e ramos permanecem. O primeiro
  lote e cada rebrota exigem pelo menos 60 segundos com apoio e luz, além
  de atingir a idade madura; farinha de osso não elimina essa espera.
- Retirar apoios poda os trechos desconectados. Tetos compartilhados têm
  uma dona por apoio para alinhar o desenho e a colheita sem cintilação.
- IDs, sementes e loot de quebra existentes são preservados. Chunks antigos
  recebem os dados da parreira ao carregar, sem alterar idade ou maturação.
- Texturas próprias 128×128 e gerador canônico; todos os pares age/uv_age
  mapeados. Guia, traduções PT/EN e documentação incluem montagem e cultivo.
  Rolagem de dicas do Guia acompanha o texto; ESC/Backspace seguem a API SDL.
- GameTests cobrem armação, tempo, persistência, migração, colheita e duas
  raízes no mesmo teto. ClientGameTest usa plantio e colheita pelos controles
  reais, captura os estágios e abre a página da parreira no Guia.
- 🍾 O BARRIL engarrafa com a GARRAFA VAZIA DA PRÓPRIA bebida (na mão ou no
  slot da GUI), além da garrafa de vidro vanilla — o vidro que volta ao
  beber volta a encher. A vazia de OUTRA bebida é recusada. Resolução pelo
  registro (`ProcessosBebida.garrafaVaziaDe`, `{id}_vazia`, fallback vidro
  vanilla): bebida nova com vazia funciona no barril de graça. Gametest
  prova aceitar a certa, gastar 1 vazia, não gastar vidro vanilla e
  recusar a de outra bebida. Dica nova na GUI (pt/en).
- 🏪 ESTRUTURA DO MERCADO CONSERTADA (report com print): no nível y3 faltavam
  as colunas de parede verde — sobrava um vão corrido entre a parede e o
  telhado ("buracos pelas paredes"); e a vitrine das gôndolas usava PANE com
  conexão leste-oeste assada num corredor norte-sul — escada de vidros soltos
  ("vidro tudo errado", "blocos a mais lá fora"). Fonte: tools/gen_mercado.py
  — y3 fecha com G em x2/x24 (e moldura m no y1 da fachada, igual ao v1),
  vitrine vira vidro-BLOCO (v) que fecha em qualquer rotação do jigsaw (as 3
  peles). Mundos antigos: /gagomarket rebuild.

## v1.2.66 — A DIVISÃO DA ESQUINA: GAGO=BEBIDA, TRAFICANTE=DROGA (30/09/2026)

### 🍺 O Gago é bar: cardápio SÓ de bebida
- Removidos do balcão do Esquinão: baseado, cocaína, LSD, pó estelar, extrato
  de cafeína, pão, sementes de maconha/papoula/café, cartuchos, escopeta e
  revólver de fidelidade. Droga é assunto do PONTO do traficante.
- O cardápio ficou 21 itens de BEBIDA (0..20): 8 bebidas (agora com suco
  detox, água de coco e chá de lúpulo), as 8 GARRAFAS VAZIAS da v1.2.65
  (R$ 3, o fregues volta com o vidro), sementes de lúpulo/uva/cevada,
  lâmpada UV e garrafas de vidro. INDICE_EXCLUSIVOS = 21.
- Exclusivos de fidelidade no TEMA: chá de lúpulo (tier 1), hidromel ×4 e
  pack de 6 cervejas (tier 2), rum ×4 e 16 vidros (tier 3) — nada de arma
  no balcão.

### 💊 O camelo é camelô: só droga, compra e vende
- A aba "Vender colheita" do Ponto virou "Vender cru": o traficante COMPRA
  cru de DROGA na porta (seda ×8, ópio ×8, cocaína ×4 — 90% do preço do
  dia, cota 12/dia). Preço exibido vem do servidor (precoCruDe), sem
  número hardcoded no client.
- Cotação da rua: o teto agora é o preço do PRÓPRIO Ponto (o concorrente
  da rua é ele; o Gago não vende droga pra existir teto cruzado).

### 🌉 Interligação Traficante ↔ Gago
- Gago COMPRA insumo de bebida NA PORTA (mesma tabela/cota da aba de
  venda) — espelho do cru do camelo: planta bebida → Esquinão; planta
  droga → Ponto.
- Dívida do fiado é GLOBAL (já era, agora o jogo diz): "o Gago também
  cobra". Matou o Gago devendo? O corpo solta o fofoco do valor pro
  camelo (morte_fiado).
- Dicas cruzadas: Gago recusa droga mandando pro camelo (nao_vende_droga);
  tooltips do cru lembram que bebida é com o Gago; rodapés e hints
  apontam o parceiro certo.

### 🖥️ GUI do Ponto legível de verdade
- CAUSA DO ESTOURO: 3 abas de 100px + espaçamento = 324px num painel de
  310px (a 3ª saía 14px pra fora). Agora: larguraAba = (LARGURA−18)/3 =
  97px, as 3 cabem DENTRO da moldura.
- TODO texto é truncado pela largura real da fonte (plainSubstrByWidth):
  letreiro, subtítulo, barra de desconto, títulos do fiado, nomes,
  detalhes e rodapé — nada vaza do retângulo em nenhuma língua.
- Textos do letreiro/desconto encurtados (pt/en) pra caber de primeira.
- Fix de fundo: ingressarTraf agora dá refreshTradeStock() ANTES de
  montar o payload (o primeiro clique nascia com estoque -1/0 e preço do
  diamante errado até reabrir).

### ✅ Testes e lang
- Gametest novo: dealerBuysRawDrugsAndGagoBuysOnlyBeverageIngredients —
  guarda a divisão (cru não entra no Gago, colheita não entra no camelo,
  balcão sem droga em nenhum tier) e trava arbitragem do cru.
- everyPaymentFitsAndStreetPricesAreLower atualizado: balcão = 21 itens;
  oferta de rua nunca é item que o bar do Gago vende.
- Lang pt/en: chaves novas (aba.cru, cru_dica, bebe_no_gago, comprou_cru,
  gago.nao_vende_droga, gago.morte_fiado, gago.comprou_insumo,
  gago.cota_insumo), gago.title/hint e traficante.hint reescritos;
  colheitas/comprou_colheita removidas. Mesmas chaves e placeholders nos
  dois idiomas (validado).

## v1.2.65 — GARRAFAS VAZIAS, PADRÃO 3D UNIVERSAL E CORREÇÕES (29/09/2026)

### 🍺 O recipiente volta: 8 garrafas vazias
- Toda bebida 3D agora tem a SUA versão vazia (mesma identidade, tampa e rótulo,
  sem líquido nem espuma — atlas derivado da cheia). Beber devolve a garrafa vazia
  da PRÓPRIA bebida pelo USE_REMAINDER — a garrafa de vidro vanilla saiu do
  circuito das bebidas prontas. Empilha só consigo mesma (GarrafaVaziaItem).
- Os 8 produtos de etapa (caldo de cana, mostos, melaço, jovens) viraram FRASCOS
  3D de laboratório de bodega (96 cuboides cada) — mesmo atlas, rótulo e símbolo
  da etapa; IDs preservados, receitas e cadeias intocadas.
- Pão de cevada e coco (fruto) também ganharam corpo 3D no padrão do mod.
- Prévia 3D atualizada: 24 peças (cheias + vazias + frascos) em tools/garrafas.json.
- Itens sem skin CORRIGIDOS: coco, coqueiro tronco/folhas, coco_bloco e faixa
  pedestre estavam SEM a definition items/{id}.json (formato novo de item-model).

### 🚪 Porta-grade do guichê funcional
- O clique do player agora ABRE E FECHA de verdade (duas metades, tum de ferro);
  o relógio só define o estado de nascimento na colocação — o Zelador não briga
  mais com o fregues a cada 30s.
- Ícone do inventário vira sprite frontal (convenção da porta vanilla): o modelo
  3D de 2 blocos era recortado pelo GUI e aparecia vazio.

### 🧹 Limpeza e prontuário
- PROJETOR DE HOLOGRAMA e PENDRIVE DE PLANTA REMOVIDOS (v1.2.60 nunca pedida):
  9 classes, receitas, 56 chaves de lang e entradas do guia.
- Prontuário deixa de citar o Gago como doutor: "Clínica do Esquinão · auto-exame
  do fregues" — o Gago é o NPC do mercado.
- Gametests novos (4): vazia correta por bebida (via componente), efeito aplicado
  ao beber, menu das 6 máquinas construindo server-side — 92/92 verdes.
- Geradores protegidos: gen_bebidas e gen_texturas_v1258 se recusam a regravar os
  sprites 3D aposentados; gen_garrafas idempotente byte a byte.

## v1.2.64 — BEBIDAS 3D: AS GARRAFAS OFICIAIS DO MOD (29/09/2026)

### 🍺 Adeus sprite 2D, bem-vindo vidro de verdade
- As 8 bebidas (cerveja, vinho, cachaça, hidromel, rum, suco detox, água de coco e
  chá de lúpulo) agora são MODELOS 3D nativos — long neck âmbar com colar de espuma,
  bordeaux com cápsula, frasco de botica etc. O catálogo `tools/garrafas.json` é a
  fonte única: perfis de seções, paletas, tampas (coroa, rosca, rolha, lacre) e
  símbolos pixel-art dos rótulos.
- `tools/gen_garrafas.py` grava modelo JSON (165 cuboides só na cerveja!), definição
  de item e atlas 128×128 por bebida (`textures/item/garrafas/{id}.png`), com líquido
  visível pelas paredes de vidro (alpha 82), grão por material e rótulo desenhado.
- UVs em espaço NATIVO do Minecraft (0..16 por textura = pixels/8 do atlas) —
  documentado no gerador para não confundir com a fração 0..1 do arquivo PNG.
- Prévia aprovada 8/8 em `preview/garrafas.html`: mesma geometria do gerador,
  vistas perspectiva/frente/lateral/topo e "as 8 na mesa".
- A mecânica de mesa entra de vez: agachar+usar expõe a garrafa 3D na superfície
  (BebidaDecorativaEntity + Renderer, ItemDisplayContext.NONE); mão vazia recolhe.
- Sprites 2D antigos REMOVIDOS do mod e do pack: nada os referencia e a única
  fonte da arte é o gerador canônico. Os geradores históricos ganharam a guarda
  `BEBIDAS_3D` (`gen_textures.py`, `gen_texturas_saude.py`, `gen_texturas_v1258.py`)
  e se recusam a recriar esses PNGs; `gen_preview_guia.py` passa a usar o RÓTULO
  da garrafa (crop do atlas) como ícone. Regeneração completa validada: modelos
  3D byte a byte idênticos e zero sprites recriados.
- Atlas das garrafas NÃO vão pro pack `minhas-texturas` (regra do AGENTS: o pack
  não pode sobrescrever os atlas do mod); artes manuais antigas preservadas em
  `backups/20260922-*/item/`.

## v1.2.63 — CÉDULAS RETANGULARES, CÂMBIO COMPLETO NO GUIA E TEXTURAS REVISADAS (28/09/2026)

### 💵 A família R$ com cara de cédula
- O sprite das notas estava QUADRADO: o `minecraft:item/generated` estica a textura
  INTEIRA num quadrado, e a cédula 1024×432 virava um trapo no inventário. O
  `gen_dinheiro.py` agora LETTERBOXA: nota colada no centro de um canvas 1024×1024
  com alpha — no jogo ela aparece PROPORCIONAL (faixa horizontal), como cédula de
  verdade. Moeda de 1 intacta (círculo já ocupa o quadrado).
- Prévia 3D acompanha: o `embute()` do `gen_dinheiro_preview.py` recorta o letterbox
  antes de embutir, então a caixa 3D continua na proporção real 2.37:1.

### 📖 Guia: escada completa do câmbio + página da nota 500
- A entrada "notas" agora mostra TODOS os degraus de bancada (1→2, 1+2+2→5, 5+5→10,
  10+10→20, 20+20+10→50, 50+50→100, 100+100→200), extraídos dos JSONs — o gametest
  "guia ↔ receita" continua valendo.
- A 500 ganhou página própria ("nota500"): troféu do Wither, sem receita — dourada,
  golfinho gravado, R$ 32.000 por stack (pt_br + en_us).

### 🎨 Texturas revisitadas (suco detox, coco e coqueiro)
- **suco_detox**: garrafa de vidro de verdade — silhueta corpo+gargalo, líquido com
  polpa em suspensão e bolhas, rótulo creme com folha, tampa dourada serrilhada,
  brilhos de vidro.
- **agua_de_coco**: coco verde com volume esférico e contraluz, polpa branca no
  corte, água transbordando e canudo vermelho; olhos com brilho.
- **coco** (item): fibra agora DESENHADA SOBRE a esfera (antes ficava só nos cantos,
  fora do coco), sombreamento esférico e contraluz.
- **coqueiro_tronco**: gradiente de base, fibras em pares sombra/luz, volume
  cilíndrico e anéis com sombra; **tronco_topo**: sombreamento assimétrico;
  **folhas**: gradiente, folíolos ondulados e mais furos.
- CORREÇÃO DE MOTOR: fills translúcidos do `ImageDraw` SUBSTITUEM o pixel (furavam
  a textura com alpha 36–139). Novo helper `camada()` desenha translúcidos em layer
  própria + `alpha_composite`; gradiente por retângulos cheios (sem frestas).
  Auditoria por pixel: 0% de alpha parcial nas texturas cheias — tudo opaco.
- Espelhado no pack `minhas-texturas` (item + block), regra do AGENTS.md.

## v1.2.62 — MERCADO ESQUINÃO V2: LOTE 27×27, PRATELEIRAS FUNCIONAIS E CALÇADA ANTI-MATO (28/09/2026)

### 🏬 O template novo (mesma chave, mundos antigos compatíveis)
- O `mercado_gago.nbt` saiu do 15×8×19 para o **27×8×27** (lote inteiro 27×27), no MESMO
  arquivo — descoberta espiral, `/gagomarket rebuild` e reforms do zelador seguem
  ancorados no CENTRO GEOMÉTRICO (célula 13,13), que agora bate exato (a ambiguidade
  (7,5)/(7,9) do v1 morreu: fachada/porta em z17, posto da madrugada em (13,16),
  Gago atrás do balcão em (10,11)).
- **Prateleiras funcionais**: 4 gôndolas (base de slab + corpo de quartzo + vitrine de
  vidro), geladeiras de bebida no fundo, balcão do Gago com tampo e estoque de barris.
- **Pátio completo**: asfalto com 5 vagas demarcadas + vaga PCD azul, faixa de pedestre
  da porta ao meio-fio, 4 postes de luz e hidrante na calçada (nunca em vaga).
- **Calçada anti-mato**: piso sólido + meio-fio em TODO o perímetro do lote (a
  preocupação nº1 do playtest) — o template coloca o volume inteiro, inclusive o ar.
- Faixa de LED do letreiro continua com 15 blocos (7X+J+7X) montada sob o beiral;
  painel de ofertas (8..10,2,18) e mini display "ABERTO · 24H" (18,1,18) nascem nos
  pontos EXATOS das reforms do zelador — as reforms viram no-op, sem display duplicado
  nos mundos migrados.
- **Zero troncos no template**: o zelador destrói qualquer `#minecraft:logs` no box
  (árvore invasora) — colunas do v1 sumiriam na primeira passada; gôndolas agora de
  slab/quartzo, molduras de concreto.
- `validate_worldgen.py` refeito pra anatomia v2: tamanho 27×27, porta-grade em
  (13,1..2,17), âncoras PORTA_LOCAL/BALCAO_LOCAL em ar, no-ops do zelador conferidos,
  Gago único no posto (10,1,11), paleta sem tronco.

## v1.2.61 — MÁQUINAS VIVAS: CENAS ANIMADAS, EFEITOS SONOROS E RECEITAS EM DATAPACK (26/09/2026)

### ⚗️ Catálogo de receitas virou DATAPACK (data-driven de verdade)
- As receitas de dorna, alambique, barril, moenda, prensa e caldeirão saíram
  do código e agora moram em **JSON** em
  `data/intoxicantes/processo_bebida/<maquina>/` — **um arquivo por receita**, com
  serializer próprio (`CatalogoBebidas`, `SimpleJsonResourceReloadListener`).
- Um datapack pode **adicionar, mudar ou remover receitas SEM tocar em código**
  (ou sem o mod): campos `input/qtdIn/output/qtdOut`, insumo secundário
  (`secIn/secQtd`), subproduto (`extraOut/extraQtd`) e `tempoSeg`.
- **FONTE ÚNICA preservada**: máquinas, GUIs e o Guia continuam lendo pelo
  `ProcessosBebida` (fachada intacta, que delega ao catálogo) — nada escrito
  em dois lugares. Se o datapack não trouxer nada, os defaults de fábrica
  alimentam o catálogo (nunca quebra o jogo).
- 11 receitas default espelhadas em JSON: 2 dorna, 2 alambique, 4 barril,
  1 moenda, 1 prensa, 1 caldeirão.

### 🎬 Cenas das GUIs agora ANIMAM com o lote real (nada inventado no client)
- Todas as 6 máquinas têm animação própria na cena, guiada pelo
  **`ContainerData`** (restante/total/fase): lote parado = cena imóvel —
  o relógio de frames só escolhe o quadro da oscilação (mesmo papel do bob
  de itens vanilla).
- Dorna: borbulhas aceleram conforme o progresso; Caldeirão: vapor aumenta;
  Alambique: a serpentina goteja com o calor; Prensa: o fuso desce com o
  avanço; Moenda: o rolo gira; Barril: a tampa pulsa na fermentação.

### 🔊 Sons ambientes + partículas por máquina processando (padrão v1.2.55)
- Server-side, no tick, com intensidade guiada pelo progresso real do lote:
- **Dorna**: borbulhar (`BUBBLE_COLUMN_UPWARDS_AMBIENT`) + `BUBBLE_POP` na
  superfície; fervura forte escurece com fumaça de fogueira.
- **Alambique**: chiado/vapores + gotejar da serpentina
  (`POINTED_DRIPSTONE_DRIP_WATER`) com brilho; fogo baixo solta fumaça.
- **Moenda**: tranco do rolo (`GRINDSTONE_USE`) + faíscas de bagaço (`CRIT`).
- **Prensa**: tranco do fuso (`PISTON_EXTEND`) + respingo do mosto (`SPLASH`).
- **Caldeirão**: borbulhar de fervura + fumaça; **Barril**: borbulhar na
  fermentação e o `BOTTLE_FILL` do engarrafamento já existentes mantidos.

### 📖 Guia do SNC
- Nova seção **MÁQUINAS → Como conversar com suas máquinas**: clique-direito
  abre a **GUI cênica**, **Shift + item** insere/coleta direto, **Shift + mão
  vazia** recolhe o conteúdo. As 5 dicas antigas de interação (que ensinavam
  a rota pré-GUI) foram reescritas — pt_br e en_us.

### ✅ Qualidade
- **82 gametests verdes**: 76 do rework + 1 novo do catálogo datapack (o
  JSON carrega fim a fim e o Guia/GUIs leem a mesma fonte) + 5 novos de
  layout das GUIs (slots no painel, menu = tela, sem colisão, filtro por
  catálogo, shift-click vai e volta sem vazar).
- Correções do codec de receita pro formato da DFU 10 (adaptador do record
  `Prima` com `Optional<Item>`; `ByteBufCodecs.list()` via `apply`).

## v1.2.60 — PLANEJADOR 3D + PROJETOR DE HOLOGRAMA: A FÁBRICA SE PLANEJA ANTES DE NASCER (26/09/2026)

### 🧊 O Projetor de Holograma (feature nova no jogo)
- **Bloco NOVO `holograma`**: pedestal de aço (7px) que projeta a planta de uma
  construção como **maquete wireframe flutuante** — verde-LED pra genérico,
  azul pra pedra/ferro, âmbar pra madeira. Sem sinal de redstone ele DORME
  (o holograma some, mesma economia do painel de LED de dia).
- **Clique direito** abre a tela de status: nome da planta, total de blocos,
  ROTAÇÃO da maquete (gira de 90° em 90° pra estudar o encaixe), botão de
  **ESCANEAR** a área (raio 3→5→8→10, autoritativo no servidor) e a
  **LISTA DE MATERIAIS** — quantos blocos de cada tipo a obra vai gastar.
- **Pendrive de Planta** (item novo): carrega 1 planta num DataComponent
  (mesma disciplina do tambor do .38) e grava no projetor com clique direito.
- Varredura truncada em 400 blocos (protege o renderer); formato dos blocos:
  `x,y,z:id` — o MESMO do Planejador 3D.
- Craft do projetor: ferro + olho de ender + redstone + pedra lisa + diamante.
  Pendrive: ferro + vidro + lápis-lazúli. Texturas HD 128×128 geradas
  (base escovada com rebites, anel de LED com faíscas, pendrive com janela).
- Rede própria: `AcaoPayload` (girar/escanear, revalidado no server) +
  `AbrirPayload` (status). Documentado no Guia (categoria BLOCOS).

### 📐 O Planejador 3D (prévia no Freebuff, `preview/previa-planejador-3d.html`)
- **Lista de materiais em tempo real**: painel lateral conta blocos e
  ferramentas por tipo, atualizada a cada mudança na laje.
- **Salvar/Carregar plantas em JSON**: baixa a planta (formato
  `snc-planta-3d`, compatível com o holograma) e carrega de volta — várias
  propostas de layout pra comparar.
- **Texturas no estilo SNC**: tábuas com veios, tronco, vidro com reflexo,
  ferro com rebites, cobre com faixas de tonel, asfalto, letreiro de LED
  pontilhado, barris com ARO DE FERRO (v1.2.50) e galpão de exemplo 9×5×7
  completo (paredes, vidro, portão, letreiro e interior mobiliado).

## v1.2.59 — AS MÁQUINAS GANHARAM GUI (rework do sistema de bebidas)

### 🖥 GUI interativa pras 6 máquinas
- **Botão direito abre a GUI de verdade** (menu sincronizado com o servidor):
  Moenda, Prensa, Caldeirão, Dorna, Alambique e Barril — cada uma com uma
  CENA própria desenhada no corpo (rolos dentados com cana entrando, fuso de
  prensa esmagando uvas, tacho de cobre sobre chamas, tanque com visor de
  nível e bolhas, alambique de cebola com serpentina em tonel d'água, barril
  deitado com rótulo da bebida).
- **Barra de progresso REAL**: lê o ContainerData da BlockEntity (servidor é
  a autoridade) — nada de barra decorativa desgarrada do jogo.
- **Painel de receitas na tela**: a máquina lista o que sabe fazer (insumo →
  produto, dose e tempo direto do ProcessosBebida) e DIZ POR QUE não começou
  ("sem água", "sem fogo", "saída cheia", "aguardando o lúpulo").
- Rotas de interação: **clique = GUI**, **Shift+item = inserção rápida**,
  **Shift+mão vazia = coleta**. Slots funcionam de verdade (pegar/por item).

### ⚙ Motor de lotes (o fim do "exatamente 6")
- Pilha **maior que a dose** é aceita: entram só DOSES INTEIRAS (até 4), a
  sobra fica na mão do jogador — 13 uvas = 2 lotes + 1 de volta.
- **Produção contínua**: fechou um lote, o seguinte começa sozinho com o que
  sobrou no buffer.
- **Saída cheia NÃO perde produto**: o lote fica pendente e entra quando
  sobrar espaço (a GUI avisa).
- Caldeirão mostura→fervura encadeados na mesma estação (lúpulo no slot
  próprio; fervura começa sozinha quando ele chega).

### 🛠 Correções que o rework trouxe à tona
- **Barril quebrado sem jogador (explosão/pistão) perdia o lote**: a BE dele
  não é Container; agora `spawnAfterBreak` devolve buffer + lote em curso em
  qualquer destruição (idempotente com a rota do jogador).
- **Bagaço duplicado na moenda**: se o slot extra estivesse cheio no
  fechamento do lote, o retry re-produzia o caldo principal (4 → 8 de uma
  dose só). Agora o retry só entrega o que faltou.
- **Lote fantasma**: com sobra menor que a dose, a máquina não abre mais um
  lote de timer órfão (a GUI mostrava "processando" eterno).
- **Caldeirão sem água não avisava**: a fase SEM ÁGUA existia mas nunca era
  reportada pra GUI.
- Dicas de coleta atualizadas ("Shift + mão vazia recolhe").

### 🧪 Testes
- 6 gametests novos: buffer de doses inteiras + sobra na mão, produção
  contínua encadeada (12 uvas → 8 mostos), saída cheia guardando lote,
  quebra devolvendo tudo (dorna e barril), barra da GUI lendo o
  ContainerData real. Suíte completa: 76 testes verdes.

---

## v1.2.58 — HUD NO LUGAR CERTO + OS 6 BUGS DA ESQUINA + TEXTURAS HD (25/09/2026)

### 🧪 Barras da saúde ancoradas no HUD vanilla
- **Sede agora fica EM CIMA da barra de fome** (lado direito do hotbar) e o
  **vício EM CIMA da barra de vida** (lado esquerdo) — acabou o painelzinho
  escondido no canto da tela, agora é leitura direta junto do que importa.
- Barras slim com contorno, na largura da fileira vanilla (81px), só quando
  fazem sentido (sede < 100, vício > 0) — casa saudável não desenha nada.
- **Deslizam pra cima** quando a armadura ou as bolhas de ar ocuparem a fileira.
- O rótulo de ABSTINÊNCIA agora paira sobre a barra do vício (e o tremor da
  síndrome continua, agora treme cada barra no lugar).

### 🛠 Os 6 bugs da esquina (raio-x pedido pelo usuário)
- **Café sem fumaça**: beber o Extrato de Cafeína não solta mais fumaça de
  fogueira na cara do fregues sóbrio (a cura da ressaca carregava um
  `CAMPFIRE_COSY_SMOKE` esquecido na Embriaguez).
- **Relógio enxergado**: o HUD do canto mostra SÓ A HORA (adeus "Dia 47") e a
  fórmula foi conferida contra os markers oficiais do 26.3 (noon = tick 6000,
  midnight = tick 18000) — a hora bate com a posição do sol. O "⏰ HH:MM" que
  o mercado spamava no actionbar a cada 5s foi REMOVIDO.
- **Suco e coco nasceram de novo**: as texturas eram placeholder de cor
  chapada; agora são **HD 1254×1254** (padrão do pack, pintadas com shading),
  e a ÁGUA DE COCO ganhou fonte de verdade (a receita antiga usava COCOA
  BEANS — cacau! kkkk).
- **Efeitos das drogas funcionando DE VERDADE**: os buffs vanilla duravam
  10-15s dentro de viagens de 4-7 MINUTOS (eram aplicados 1× no início). Agora
  cada efeito RENOVA os buffs a cada 5s enquanto a viagem dura — o pique, o
  casaco e a visão do LSD aguentam a viagem inteira. Os 6 ícones HD + nomes
  pt/en mataram o quadriculado roxo do inventário.
- **Lúpulo e cevada com vida própria**: Chá de Lúpulo (regen 10s + remove
  Overdrive/Viagem — o antídoto do psicodélico) e Pão de Cevada (+6 fome,
  saturação 0.6).

### 🌴 O coqueiro (o novo ciclo do coco)
- Coqueiro construído do zero: tronco curvado, coroa de folhas e 1-2 COCOS
  pendurados; spawn raro em Beach/Jungle/Stony Shore.
- Coco no pé quebra pro item Coco — comível (+2 fome, +30 sede, a água da
  mordida) — e Coco + garrafa d'água vira Água de Coco (+50 sede).

## v1.2.57 — "O QUÃO FUDIDO TU ESTÁ" + TEXTURAS HD + CONTROLES DE FPS (25/09/2026)

### 📋 O Prontuário renasce (tecla H)
- **Renomeado**: "Prontuário do Fregues" → **"O Quão Fudido Tu Está"** (a ficha
  médica da esquina com o nome que ela merecia).
- **Redesenhado de verdade**, fiel ao preview aprovado: painel CINZA estilo GUI
  vanilla com bevel (o clássico branco/cinza), um bloco por órgão com nome +
  estágio na mesma linha, BARRA SUNKEN destrutível (verde → amarelo →
  vermelho) e a consequência de gameplay na terceira linha.
- **A consequência agora é VERDADE** (eram só promessas na tela): fígado ruim
  (250+/500+/750+ de dano) multiplica a RESSACA em até 2.5×; pulmão ruim dá
  TOSSE seca (fraqueza + fumaça) depois de todo baseado; estômago ruim alonga
  a NÁUSEA da queda do LSD em até 2.5×.
- Histórico em GRADE 2×2 (bebidas/ervas/pós/pílulas), DIAS LIMPOS exibidos,
  caixa de vício rosada com PIPS (|||||·····) e status DEPENDENTE/sob
  controle, aviso de abstinência e o veredito do Dr. Gago na caixa sunken do
  rodapé.

### 🎨 Texturas HD embutidas no jar
- **70 texturas importadas do resourcepack** `minhas-texturas` pra dentro do
  mod: **asfalto**, as 6 fases da **uva**, café, lúpulo, maconha, papoula,
  postes, hidrantes, placa do Esquinão, sementes e itens (uva, vinho, rum,
  LSD, heroína, pó estelar…) — até 1254×1254 de arte original, agora no jar.
  O pack fica redundante (mas segue funcionando por cima).

### 🔫 Controles de FPS definitivos
- **Botão direito NÃO atira mais** — em nenhuma situação. Direito SEGURADO =
  MIRA (ADS, client-side, não é mais `item.use()`); disparo é o GATILHO
  ESQUERDO via payload próprio (GatilhoPayload), com validação server-side
  completa; recarga segue na TECLA R (um aperto). O `use()` dos itens ficou
  INERTE (PASS) — o vazamento que deixava o direito atirar (interação com
  entidade → PASS → use()) morreu.
- Testes de arma migrados pros caminhos novos (gatilho/R). **70/70 game
  tests passando.**

## v1.2.56 — BALANCEAMENTO DA SAÚDE PRA PRIMEIRA PARTIDA (25/09/2026)

### 💧 Sede
- Ciclo de dreno 60 → **75s** por ponto: ~2h parado, ~1h correndo, ~30 min na
  bebedeira correndo — a barra dá o ar da graça sem dominar a partida.
- Correr agora desidrata 2× (era 2.5×): jogar normal (correndo) não seca mais
  a barra em 40 minutos.
- **Dreno fracionário** (v1.2.56): multiplicadores aceleram o relógio SEM
  arredondar — bêbado bebe água a cada 37.5s, não 38 (o round() antigo fazia
  o bêbado drenar na MESMA cadência do sóbrio com ciclo ímpar).

### ⛓ Vício e abstinência
- **Janela de doses DECAY**: 5 minutos limpo da droga derrete metade das
  doses (piso 1). A intensidade da viagem era memória vitalícia — depois de
  uma sessão, TODA viagem vinha em intensidade 3 pra sempre. Agora é memória
  RECENTE: pare de usar e a intensidade desce de volta.
- **Abstinência 90 → 150s** até o pico: início aos 75s (era 45s — tremia
  antes do player entender o que tava acontecendo), grave aos 5 min, colapso
  aos 10 min. Dá tempo de correr atrás da dose ou do detox.

### 🍄 Viagens (duração e vício por dose)
| Droga | Duração | Vício/dose |
|---|---|---|
| Baseado | 150 → **240s** | 3 → **2** |
| Seda | 120 → **180s** | 2 → **1** |
| Ópio | 180 → **300s** | 6 (mantém) |
| Heroína | 200 → **240s** | 10 (mantém) |
| **Cocaína** | 120 → **180s** | 12 (mantém) |
| LSD | 240 → **420s** | 4 → **3** |

- A cocaína era a viagem mais CURTA do jogo (120s) sendo a mais viciante — o
  risco não compensava. LSD vira a viagem-mãe (7 min). Leves quase não prendem
  (1-2/dose: precisaria de 15+ doses seguidas pra depender de seda).

### 🔧 Interno
- Testes de abstinência lêem `saudeAbstinenciaSegundos` do config (sem número
  mágico quebrando a cada balanceamento). Teste novo: decay da janela de
  doses (5→2 metades, piso 1). **70/70 game tests passando.**

## v1.2.55 — MÁQUINAS VIVAS + CALIBRAGEM DA SAÚDE (25/09/2026)

### ⚙️ As máquinas vivas (a versão chique das animações)
- 4 renderers novos (`MaquinasVivasRenderer`): a máquina desenho POR CIMA do
  modelo 3D SÓ quando processa — **ociosa = custo zero** (early-out na
  primeira linha do submit; nem entra na geometria).
- **Dorna**: caldo de cana translúcido subindo na boca com a superfície
  ondulando (shader de água vanilla) + vapor server-side.
- **Alambique**: tacho de cobre pulsando quente (só com fogo embaixo) + o
  CALOR ANDANDO elipse por elipse na serpentina (ciclo de 2.4s) + bico
  brilhando quando o lote tá acabando (>85%).
- **Moenda**: aletas girando nos 2 rolos (a cana sendo mastigada).
- **Prensa**: parafuso DESCENDO com o progresso do lote + suco de uva
  subindo na calha.
- **Caldeirão**: mosto fervendo com onda + a "pata" quente rodando na
  superfície (a corrente da fervura).
- **Barril**: espuma subindo no buraco da rolha SÓ na FERMENTAÇÃO (a
  maturação continua silenciosa, como no código real).
- Sync server→client por update tag (mesmo padrão do PainelLed): estado
  manda nas MUDANÇAS (carregar/fim de lote) + progresso a cada segundo
  do lote; zero tráfego com máquina parada.
- Zero textura nova: líquido no shader translúcido do mundo e brilho no
  atlas LED da casa com cor de vértice (o mesmo pipeline do letreiro).

### 🎚 Calibragem da saúde (primeira partida)
- `saudeSedeSegundos` 45 → **60**: ~100 min parado, ~40 correndo, ~20 na
  bebedeira correndo — perceptível sem irritar (45 secia rápido demais).
- Suco detox: cura de órgão 120 → **60 por unidade** — reverter uma vida
  de bebedeira custa ~9 sucos (a redenção é lenta como tem que ser).
- Game tests agora leem o config (sem número mágico): mudar o balance
  não quebra a suíte.

## v1.2.54 — SAÚDE DO FREGUES + EFEITOS POR DROGA (25/09/2026)

### A espinha: SaudeData (intoxicantes_saude.json)
- Um arquivo por mundo, padrão Embriaguez/PlayerMoney: hidratação, dano dos
  3 órgãos, vício (nível + droga), doses por substância (janela das viagens),
  histórico de doses da vida (alcool/erva/pó/pílula), relógio limpo e curas
  seguidas. Server-authoritative; o client recebe por payload S2C.

### 💧 Sede
- Barra própria 0-100 que some do HUD quando cheia. Drena 1 ponto a cada
  `saudeSedeSegundos` (45s), x2.5 correndo, x2 no Nether e x2 bêbado.
- Zerada: Weakness + Slowness + Nausea — **NUNCA dano** (castiga, não mata).
- Fontes: garrafa d'água +20, **Água de Coco** (nova, +50), **Suco Detox** +35.
- Droga pesada e álcool desidratam (a boca de algodão é real).

### ⛓ Vício e abstinência
- Dose repetida da mesma droga sobe a dependência (peso por tier: cocaína
  12/dose, heroína 10, ópio 6, LSD 4, erva 2-3). Acima de 30 (config): dependente.
- SÍNDROME DE ABSTINÊNCIA em 4 estágios (½/1x/2x/4x do config, 90s):
  efeito próprio + nausea + weakness + trevas intermitentes + tremor.
- COLAPSO (estágio 4): dano periodico que desce a vida **até 1 coração e
  para** — o mod NÃO mata por abstinência (regra de ouro).
- RECAÍDA: usar na síndrome alivia na hora, mas zera a cura e sobe o vício.
- CURA: dia limpo (1200s sem dose) derrete 10 de vício; zerou = curado.

### 📋 Prontuário do Fregues (tecla H)
- Sem item novo: **keybind H** abre a ficha médica (configurável); H fecha.
- Papel-moeda bege igual ao cardápio: 3 órgãos com barra verde→amarela→
  vermelha e estágio com humor ("Fígado de Aço" → **"Cirrose em estágio de
  Jucelino"**), histórico de doses da vida e veredito do Dr. Gago.
- HUDzinho contextual: só aparece com sede < 100 ou vício > 0; em
  abstinência o painel TREME junto com o fregues.
- Gameplay dos órgãos (extensões futuras usam os mesmos getters): fígado
  ruim multiplica a ressaca, pulmão ruim = tosse, estômago = náusea.
- MARKUP DO DESPERADO: fregues em abstinência paga até +30% no Gago
  (config `saudeMarkupDesesperado`) — ele aceita qualquer preço kkkk.

### 🍄 Efeitos por droga (as "Viagens")
- 5 MobEffects novos registrados com ícone próprio: **Tranquilo** (T1
  baseado — fome da boa + regen), **Morno** (T2 ópio — casaco quente),
  **Sonho** (T2.5 heroína — o nod), **Overdrive** (T3 cocaína — coração de
  metralhadora que queima saturação), **Viagem** (T4 LSD).
- Intensidade por janela de doses (1-2: I, 3-4: II, 5+: III) — dose
  repetida = viagem mais forte.
- QUEDA: aftershock no fim de toda viagem T2+ (ópio = cansaço, heroína =
  fome+lentidão, cocaína = fraqueza+mining fatigue, LSD = trevas+náusea).
- Alucinações client-only: no LSD o **Gago GIGANTE (4x)** atravessa o céu
  (entidade fantasma criada só no client — outros players não veem nada);
  FOV pulsa com a batida no overdrive e respira no LSD (mixin novo).

### Cura e hidratação (itens novos)
- **Suco Detox** (garrafa + maçã + melão → 2): regenera os 3 órgãos (120 de
  dano cada) e hidrata +35.
- **Água de Coco** (garrafa + cacau → 1): o isotônico do sertão (+50).

### Validação
- 8 game tests novos (sede drena/desidrata bêbado, debuff sem dano, detox,
  vício em estágios, **colapso nunca mata**, recaída/cura, efeito por droga,
  intensidade por dose, persistência no relog) — **69/69 passando**.
- Config novo: `saudeSedeSegundos`, `saudeVicioLimiar`,
  `saudeAbstinenciaSegundos`, `saudeMarkupDesesperado` (clamped).

## v1.2.53 — CONSERTO DO MERCADO DO GAGO + DETALHES FINOS (25/09/2026)

### Mercado do Gago (bugs do playtest)
- **Paredes viravam porta (causa raiz):** no gen_mercado.py o char "G" da paleta
  estava definido 2x — 1ª green_concrete (parede), depois metade de cima da
  porta-grade. Python deixa a última vencer => o mercado inteiro nascia porta.
  Corrigido com char próprio ("d") e os 3 templates (mercado/serra/sertão)
  regenerados (paleta com green_concrete de volta).
- **Conserto em mundos existentes:** o Zelador do mercado (varredura de 30s)
  detecta porta-superior sem a inferior embaixo (parede que nasceu porta) e
  restaura a parede na pele da região (verde/terracota/pinho).
- **sincronizar não planta mais meia-porta:** se o bloco de cima não é porta,
  ele é deixado em paz em vez de virar metade de cima da porta.

### Porta-grade
- **Xadrez rosa (sem skin):** o blockstate declarava variantes lit=true/false,
  mas o bloco não tem a propriedade lit (herança da placa antiga) — nenhuma
  variante casava => modelo faltando. Regenerado só com facing × half × fechada.
- **Modelo 3D HD:** gerador novo tools/gen_porta_grade.py (substitui o velho
  gen_blockstate_porta_grade.py, sem arquivos v2): 4 modelos 3D + texturas
  128×128, grade de ferro com remaches e cutout (vãos transparentes de
  verdade), balcão de madeira na posição aberta, plaquinha FECHADO + cadeado
  na fechada, item mostrando a porta inteira. Prévia: tools/preview_porta.py.

### Cardápio do Mercado
- **Crash ArrayIndexOutOfBounds:** scrollAbas tinha 2 posições pra 3 abas —
  abrir Exclusives travava a tela e matava todos os cliques (buy/sell). Fix na
  raiz + aba Exclusives vazia (Gago) agora mostra "passe no ponto do
  Traficante" em vez de ficar em branco.
- **Teste client:** coordenadas do clique de venda atualizadas pro layout de
  3 abas (clicava na aba errada e dava timeout falso).

### Detalhes finos (pedidos do playtest)
- **Baseado:** animação de arco-e-flecha ao fumar (2,2s = 3 puxadas curtas,
  braço recua levando o baseado à boca) + estalo de fósforo acendendo e bafo.
- **Drogas:** pó e cápsulas vão ao NARIZ (animação de luneta, 1,6s, som de
  papel) — o player não "come" mais droga.
- **Botões CoD/BF:** ESQUERDO atira (1 clique = 1 tiro), DIREITO mira
  (segurar). Com arma na mão o esquerdo não soca/quebra e o direito não coloca
  bloco. Exceção: mirando num NPC (Gago/Traficante/Juça) o direito abre o menu.
- **Iron sight de verdade:** mirando, a arma sobe até os olhos e centraliza
  (mixin da pose de primeira pessoa + fator de zoom suave). SHIFT voltou a ser
  só agachar. Mira sincronizada ao servidor via payload (dispersão/alcance
  continuam validados lá). Tooltips atualizados nos 2 idiomas.
- **Relógio sempre visível:** painel translúcido no canto superior esquerdo com
  sol/lua, HH:MM (06:00 = amanhecer) e Dia N — some com tela aberta.

### Validação
- 60/60 game tests (servidor) + client gametest verde (compra pagando:
  carteira 500 -> 485, 1 purchase).
- Alvos dos 3 mixins conferidos por javap nos mappings 26.3.

---

---

## v1.2.52 — TEXTURAS HD + CONSERTOS DO PLAYTEST DAS BEBIDAS (24/09/2026)

### Texturas em alta resolução (regra nova do AGENTS.md: tudo 128×128)
- **Cevada (8 estágios) refeita em 128×128:** colmos com nós, lâminas em
  arco, sombra de contato, espigas douradas com grãos aos pares + arestas
  (as "barbas"), estágio final amarelecendo.
- **12 itens intermediários refeitos em 128×128** (eram ASCII-art 16×16 e
  alguns saíam 100% TRANSPARENTES no jogo — bug do _png16 corrigido na
  raiz, com guarda de regressão no gerador): garrafas com vidro bojudo,
  gradiente, bolhas e brilho; feixes de cevada/malte amarrados; pote de
  melaço com rolha; montinho de bagaço; pacote de sementes kraft
  (absorvido pro gerador — tinha nascido sem fonte).

### Consertos do playtest (reports da sessão de hoje)
- **Abas do cardápio do Mercado travadas:** `scrollAbas = new int[2]` mas a
  1.2.44 criou a 3ª aba (Exclusives) — clicar nela lançava
  ArrayIndexOutOfBoundsException e a tela ficava PRESA na aba. Agora é
  `new int[3]`.
- **Compras do Mercado não funcionavam:** `ComprarPayload`/`VenderPayload`
  registrados 2× — o 2º receiver (traficante) SOBRESCREVIA o 1º (Mercado)
  e a compra morria silenciosamente. Agora: receiver único que roteia pela
  sessão ativa (Mercado ⇄ Traficante).
- **Máquinas exigiam quantidade EXATA na mão** (6 uvas = 6 uvas, nem 7):
  seletores de dorna/alambique/prima agora aceitam pilha MAIOR e consomem
  só o necessário.
- **Bebidas envelheciam 20× rápido demais:** durações estavam declaradas
  em SEGUNDOS mas consumidas como TICKS (600 "s" = 30s reais). Conversor
  único `ModConfig.ticksDeSegundos` aplicado em barril/dorna/alambique/
  máquinas de prima. Barril agora: 10 min de maturação de verdade.
- **Gago andava durante o atendimento** (sessão aberta, tela fechava por
  watchdog): congelado no início da sessão (padrão estatueta do Traficante).

### Testes
- Gancho de escala de teste virou STATIC com boot único
  (`TestesBebidaBoot`, x20): testes paralelos não brigam mais pela escala
  (as 4 cadeias falhavam por reset cruzado). 60/60 verdes.

---

## v1.2.51 — OVERHAUL DO MERCADO: FAIXA DE LED COMPLETA, MINI DISPLAY 24H, PORTA-GRADE E GAGO ÂNCORA DUPLA (24/09/2026)

### O conserto do caos do display (spec: playtest da 1.2.44–50)
- **A faixa de LED de ponta a ponta:** o gerador do mercado semeava SÓ o
  painel central — o char de extensão (`X`) existia na paleta e nunca era
  usado, e o texto esticava por cima da parede errada. Agora a fileira é
  `X×7 + J + X×7` (15 blocos) e o texto cruza a fachada inteira.
- **Zelador da autocura completa a faixa** em mundos 1.2.31–50: semeia
  extensões só em AR, PARA no primeiro obstáculo (parede de jogador nunca
  sobrescrita, sem pular por cima) e a largura do BE reflete a faixa real.
- **Renderer limpo:** código morto de FECHADO/esmaecimento removido; o
  letreiro agora é só o nome + status 24H.

### A porta-grade do guichê (bloco novo, codado do zero)
- `PortaGradeBlock` (DoubleBlockHalf + FECHADA): de madrugada o vão de
  baixo vira parede sólida e a grade de cima vira guichê; abre às 07:00
  pelo relógio do jogo (e fecha na virada). Colisão validada em game test
  (fechado = max 1.0, aberto ≤ 0.26).
- Blockstate completo (32 variantes), modelos com texturas vanilla
  (spruce + ferro), loot table, lang pt/en.

### Mini display "ABERTO · 24H"
- PainelLed verde fixo ao lado da porta (o mercado é 24h desde a 1.2.44 —
  o letreiro agora conta a verdade), editável pela Central de Comando,
  semeado pelo template e plantado pelo zelador em mundos velhos.

### O Gago nunca mais sumido (âncora dupla)
- Balcão de dia, guichê (atrás da grade) de madrugada; teleporte seguro
  só na virada, com NoAI, anti-sufocamento e anti-fantasma mantidos.
- O zelador sincroniza a porta e planta o mini display junto da reforma
  do pátio.

### Validação
- 3 game tests novos (faixa, guichê, âncora dupla) — **60/60 passando**;
  validador de worldgen estendido (faixa 1+14, mini display na x12,
  porta-grade no template) sobre os 3 NBTs regenerados.
- Jar instalado na instância "mod cet" (hash conferido); 1.2.50 em
  `backups/`.

---

## v1.2.50 — SISTEMA DE BEBIDAS COMPLETO: CADEIAS, MÁQUINAS HD E BARRIS 3D (24/09/2026)

### O sistema (spec: bebida deixa de ser `ingrediente + garrafa`)
- **Cadeia real por bebida** (`ProcessosBebida` = fonte central): cana → moenda →
  caldo → dorna → alambique → barril → 4 cachaças | uva → prensa → mosto → barril
  → vinho | cevada → malte → caldeirão → mostura (+lúpulo, fervura) → barril →
  cerveja | melaço → dorna → alambique → barril → rum. Engarrafamento 1 lote = 4
  garrafas. Cevada como crop (8 estágios, sementes, worldgen).
- **Pipeline fiel** (regra gravada no AGENTS.md): destiladas passam pela DORNA
  (fermentar) e ALAMBIQUE (destilar) — o barril só MATURA; fermentadas (cerveja,
  vinho) fermentam e condicionam DIRETO no barril. 
- Receitas shapeless antigas removidas; lang pt/en; advancements de descoberta.

### Máquinas 3D com texturas em alta resolução (128×128, células de 64px)
- Dorna, Alambique de Cobre, Moenda de Cana, Prensa de Uvas e Caldeirão de
  Mostura — tábuas com fresta/nó/grão, chapas rebitadas, líquido borbulhando,
  cobre com pátina de envelhecimento (serpentina mais oxidada que a caldeira).
- Modelos consertados na revisão: dorna aberta com fundo interno (era oca),
  caldeirão com mosto visível pela boca (estava enterrado no sólido) e circuito
  do alambique conectado de ponta a ponta (serpentina atravessava a tampa e
  terminava no ar; agora mergulha no balde).

### Barris v2.3 (aprovados em prévia)
- 7 bandas bojudas, 5 aros geométricos, cabeças recessadas com anéis, tábuas
  com fresta, torneira na cerveja, carbonização no rum, rolha de cortiça.

### Cevada refeita (8 estágios 1:1 com os ages)
- Colmos com nós e lâminas em arco, broto em V, espigas com barbas, estágio 7
  amarelecendo; blockstate↔modelo↔textura sem órfãos.

### Correções debugs que o playtest pegaria
- **12 itens intermediários invisíveis**: `_png16` tratava pixels RGBA como
  ASCII-art → tudo transparente. Corrigido na raiz + guarda de regressão no
  gerador (textura 100% transparente agora aborta o build do asset).
- 38 chaves de lang (fiado/ponto) sem tradução EN + validação de placeholders.
- Loot da cevada no formato vanilla; `mosto_rum_fermentado` nasce da fonte.

### GUIA OFICIAL DO SNC ADVENTURES (item `guia_snc`)
- **O livro existe de verdade**: item craftável que abre a tela do guia (nada de
  comando — decisão do dono: só o livro dá acesso, clássico dos mods).
- **Primeira entrada**: jogador novo nasce com o guia no inventário (1× por
  jogador, flag persistente `intoxicantes_guia.json` no mundo; inventário
  cheio → dropa nos pés; servidor pode desligar via config).
- **Perdeu? Crafta outro**: livro vanilla + Real (R$) → `guia_snc`.
- **Conteúdo = espelho do mod** (14 categorias, 48 entradas): bebidas com cadeia
  completa lida do `ProcessosBebida`, máquinas, barris, cultivos, ingredientes,
  armas, munições, itens especiais, blocos, economia e sistemas — com receitas
  3×3 e tempos ao vivo do config.
- **Visual próprio** (regra de identidade do AGENTS.md): capa de couro com
  costura e rebite, papel envelhecido, sumário pontilhado, carimbos, fichas
  pautadas — sem ecoar a fachada do Esquinão. Design aprovado em prévia HTML
  (`preview/guia/previa-guia.html`, gerador `tools/gen_preview_guia.py`).
- Validado por `GuiaGameTest`: grades declaradas == JSONs de recipe reais,
  tempos espelhados == `ModConfig`, todo texto do guia tem chave de lang.

### Validação
- 55/55 game tests (48 de comércio/cadeias + 7 do guia: grades declaradas ==
  JSONs reais, tempos == config, todo ícone tem tradução); varredura do pacote:
  0 assets ausentes, 0 UVs fora do range, 0 texturas transparentes.

---

## v1.2.47 — HOTFIX: RELÓGIO LIMPO NO HUD (24/09/2026)

- O relógio do HUD (v1.2.45) agora mostra SÓ o relógio: `⏰ HH:MM` — o
  sufixo `| Mercado Esquinão — 24h` saiu (pedido do dono: "um relógio mesmo,
  apenas"). pt_br + en_us.

---

## v1.2.46 — CONSERTOS DO PLAYTEST: O GAGO DE PERTO, O FANTASMA E A CAFEÍNA (24/09/2026)

### FIX: "não to conseguindo bater no gago"
- **Causa raiz (dupla):** o Gago de plantão fica com `NoAI` — e o soco nele não
  gerava NENHUMA reação (sem dano visível, sem som, sem raiva): batia e nada.
  E Gagos fantasma de saves antigos, entalados fora do raio de gestão do
  gerenciador, nem tinham corpo onde a voz saía.
- **O soco acorda o vendedor**: `hurtServer` no Gago em serviço agora aplica o
  dano de verdade e entra no MESMO canal do vandalismo — 1º tapa = advertência
  (cara feia + aviso), repetiu = saca a 12 e vai atrás. SEM expulsão do
  mercado (a `enraivecer` teleporta o jogador pra longe — não era o caso;
  aqui quem decidiu bater fica e assume). Guard: em criativo não provoca.

### FIX: a voz fantasma (fala no chat, não aparece no mercado)
- **Causa raiz:** a varredura do gerenciador só olhava 48 blocos; Gago de save
  antigo ficava FORA dela: invisível, imbatível e respondendo no chat (o
  ouvinte de chat procura Gago num raio de 48 de QUEM FALA).
- **Varredura larga (96) que ADOTA o fantasma**: achou Gago entalado/preso
  fora da gestão → libera o posto dele, teleporte de volta, vende normal.
  Excedente sem dono é dispensado — só 1 dono da esquina.

### FIX: extrato de cafeína não curava (e o tempo "não curava também")
- **Causa raiz (a hilária):** a ressaca era gravada em **TICKS**
  (`segundos × 20`) e decrementada **1 por segundo** — a ressaca de 90s
  durava **30 minutos**. Por isso o "não sei se ta curando com o tempo também".
- Dentro da ressaca a cafeína não fazia NADA: `curar()` saía cedo com o nível
  já em 0. E sair da bebedeira PELO CAFÉ ainda **provocava** ressaca nova
  (bebeu remédio, pagou a manhã do mesmo jeito).
- Agora: ressaca em **segundos reais** (com migração do save antigo no
  `load()`), o **café mata a ressaca na hora** (mensagem própria) e nunca
  mais provoca a manhã seguinte. A bebedeira natural continua cobrando.

### Técnico
- **43/43 game tests** (2 novos: cura da ressaca pelo extrato + soco acorda
  o Gago de plantão; o player-mock dos testes nasce com `instabuild=false`
  explícito e a seleção do posto ficou limitada ao raio de gestão).

---

## v1.2.45 — O PONTO DO TRAFICANTE, MERCADO 24H E O RELÓGIO NO HUD (24/09/2026)

### NOVO: o PONTO do traficante (overhaul completo)
- **Tela própria dele** (estilo rua: asfalto, madeira, neon — adeus UI de
  vilarejo), com **3 abas**: Estoque (7 produtos, preço do dia), **Fiado**
  ("me empresta aí" — R$ 50, juros 10% no nível 0, quitar 2× = confiança 30%,
  4× = palavra 0%; a dívida persiste no save) e **Vender colheita** (ele
  compra na porta).
- **Fila = desconto**: cada freguês junto na fila dá 5% off (saiu da fila,
  perdeu). **Fidelidade da rua**: a cada 5 compras, +5% de desconto
  permanente. **Lançamento do dia**: um produto sai pela metade, 1 por freguês.
- O traficante equipa o .38 na mão (pose de mira quando fica agressivo).

### NOVO: mercado 24 HORAS
- Portão, recusa, cochilo do Gago e ABERTO/FECHADO no letreiro: exterminados
  do código. Gago de save velho com flag "fechado" acorda no primeiro tick.

### NOVO: o relógio no HUD (o pedido lá do início, de verdade)
- `⏰ HH:MM | Mercado Esquinão — 24h` persistente no action bar, pra QUALQUER
  player no overworld (o raio de 32 blocos antigo era o motivo de nunca ter
  aparecido), reenviado a cada 5s — o client mantém a última na tela, sem
  piscar. Formato morto "na porta/no balcão" removido.

### Skins: os buracos sumiram (ANTES×DEPOIS aprovado na prévia)
- O Gago tinha uma **coluna de 24px furada na frente do tronco** e o
  traficante **306px de buracos** nos membros (a conversão antiga lia o
  layout errado). O `converte_skins_player.py` agora é **autocurável**:
  preenche qualquer pixel transparente que o modelo amostra com o vizinho de
  tecido — e **falha o build se sobrar 1 buraco** (auditoria nas 9 skins).

### Cardápio do Gago
- O "negrito" era a **sombra de 1px** do texto: removida de toda a tela.
- **★ Exclusivos** virou aba própria (Compra / Venda / Exclusivos).

### FIX: o Gago fantasma (fala no chat, não aparece) — 3 elos
- O gerenciador **teleportava o Gago a cada ciclo** (nascia entalado = fala
  no chat, invisível, soltando item). Agora ele conserta ONDE o Gago está.
- **Gagos excedentes** (ovo duplicado/spawn dobrado) são dispensados: só 1
  dono da esquina.
- Homecoming só quando ele realmente está longe do posto.

---

## v1.2.44 — FIX: BRAÇO ESQUERDO INVISÍVEL NOS NPCs (23/09/2026)

### FIX: layer de bake errado (`SKELETON` → `PLAYER`)
- **Causa raiz** (confirmada no bytecode da 26.3): o `ModelLayers.SKELETON`
  declara textura **64×32** com braços de esqueleto **2×12×2** e membro
  esquerdo por **espelhamento** (`texOffs(40,16)+mirror()`) — o UV caía nas
  regiões transparentes da skin steve 64×64 e o braço esquerdo ficava
  invisível (vale pra perna esquerda). Escolha errada minha na v1.2.40.
- **Fix**: os três renderers bakam **`ModelLayers.PLAYER`** —
  `PlayerModel.createMesh(NONE, false)`: 64×64, braços 4px, `left_arm` em
  `texOffs(32,48)` e `left_leg` em `(16,48)` SEM mirror — exatamente o layout
  que as skins pintam (e que o `converte_skins_player.py` escreve).
- Regra nova no AGENTS.md: NUNCA `SKELETON` pra skin steve 64×64.

---

## v1.2.43 — SOMBRA NAS SKINS, .38 NA MÃO DO TRAFICANTE E CARTUCHO DA 12 (23/09/2026)

### ADD: sombreamento suave nos membros das skins (aproveitando o formato steve)
- `tools/converte_skins_player.py` agora pinta **luz direcional por face** nos
  braços e pernas (frente integral, lado 0.95/0.90, costas 0.93, baixo 0.82) e
  um **gradiente vertical** ombro→mão / quadril→pé (−1%/linha, máx −11%) — as
  12 linhas de membro do formato steve que o barrigão 4×8 do layout velho não
  tinha. Validado pixel a pixel contra o jar 1.2.41: membros −6,6% a −10,5% de
  luminância, **cabeça e tronco 0,0% (intactos)**. Reaplica nas 9 skins.

### ADD: traficante com o .38 na mão ANIMADA (braços de player de verdade)
- **`TraficanteEntity.finalizeSpawn`**: equipa o `REVOLVER` na MAINHAND com
  `setDropChance(MAINHAND, 0)` (a arma é dele — não entra no loot).
- **`TraficanteRenderer.getArmPose`** (override do hook do `HumanoidMobRenderer`,
  que na 26.3 só detecta lança): braço direito em `ArmPose.ITEM` andando com a
  arma na cintura da mão; **agressivo = `BOW_AND_ARROW`**, a mira em que os
  DOIS braços apontam junto com a cabeça. Item desenhado pelo `ItemInHandLayer`
  que o renderer humanoide já adiciona.

### TEXTURE: cartucho da 12 com a arte real (foto com fundo transparente)
- `cartucho.png` refeito a partir da foto (recorte do conteúdo, 256×256
  LANCZOS, centralizado) — espelhado no mod e no pack, antigas em backup.
  `cartucho_38` do revólver não foi tocado.

---

## v1.2.42 — REVÓLVER .38 REFEITO NO PADRÃO DE ARMAS 3D (23/09/2026)

### REWORK: revólver .38 com geometria realista (regra do padrão de armas)
- **Modelo JSON nativo reescrito** (`tools/gen_revolver.py`): tambor de latão
  com câmaras e flutes, top strap, martelo armado (22,5°), ejector rod,
  guarda-mato com gatilho, cabo com rake natural — 31 elements, perfil de
  revólver de verdade (cano fino, tambor saliente).
- **Regra 16 corrigida de verdade**: o gerador velho remapeava
  `from/to/origin` mas esquecia o EIXO das rotações (a coronha girava no
  eixo errado). Agora remapeamento `(x,y,z)->(z,x,y)` converte rotações junto.
- **Primeira pessoa (regra 10)**: roll de −50° removido — o .38 agora aponta
  pra frente como FPS, não deitado. GUI/ground usam só o transform display
  (regra 12), modelo continua grande.
- **Atlas de materiais 128×128** novo (aço azulado, latão, madeira, borracha)
  no estilo da escopeta aprovada. A arte manual 256×256 do revólver
  (ícone de inventário) preservada em `backups/20260923-armas-padrao/`.
- `tools/preview_revolver.py` novo: perfil/isométrica/GUI/primeira pessoa,
  ANTES×DEPOIS aprovado antes do build (regra do fluxo do AGENTS.md).
- Escopeta e revólver auditados contra as 20 regras — padrão salvo no AGENTS.md.

---

## v1.2.41 — NPCs COM CORPO DE PLAYER (23/09/2026)

### CHANGE: traficante, Gago e Juça agora são humanoides de verdade
- **Fim do corpo de villager/barrigão**: os 3 NPCs usam o `HumanoidModel`
  padrão do player (o mesmo caminho do zumbi vanilla —
  `HumanoidMobRenderer` + `ModelLayers.SKELETON`). Braços que balançam ao
  andar, item na mão DIREITA pelo `ItemInHandLayer` (a escopeta/.38
  continuam na mão, agora apontando pra frente). As entidades já tinham
  hitbox de player (0.6×1.95) — nada de física/IA mudou.
- **Skins convertidas pro layout steve 64×64** (`tools/converte_skins_player.py`):
  a arte pintada foi remapeada 1:1 — rosto, fantasias por bioma (as 7 do
  Gago), colete/camisa, calça+meia, óculos, corrente do traficante
  (repintada no peito) e o Camel na boca do Juça. Nariz do Gago virou
  2×2 pintado no rosto; o avental R$ virou pixels no torso + faixa no
  quadril; chinelos viraram o pé da textura.
- **GagoModel aposentado** (deletado): o tremer de cabeça do modo PUTO
  (e do doidão do Juça) vive agora no `GagoHumanoidModel`, um
  `HumanoidModel` com o zRot da cabeça. A fantasia por bioma continua
  intacta (`roupa` → nome do PNG).
- **`juca.png` agora existe no pack também** (antes só no jar do mod).
- Skins antigas preservadas em `backups/20260923-skins-player/` (mod + pack).
- **Regra nova de skin**: pintar SEMPRE no layout steve 64×64 (base =
  qualquer skin de player). O layout 128×128 do GagoModel não existe mais.

### DEBUG: ferramentas novas em `tools/`
- `converte_skins_player.py` — remapeia skins (gago 128×128, villager 64×64
  ou upscale blocky) pro layout steve, lendo SEMPRE dos backups.
- `debug_atlas.py` — valida as 20+ regiões do layout (magenta = vazio,
  vermelho = região que devia ter arte e não tem).
- `gen_preview_skins.py` — a prévia antes×depois com visão frontal montada.

---

## v1.2.40 — CONSERTOS DO PLAYTEST (23/09/2026)

### FIX: display de fachada bugado (o "ABE PTTO" com blocos pretos)
- **Causa raiz:** o template plantava a fileira do painel de LED em x5..x9 —
  atravessando a PORTA (x7) — e gravava NBT de texto nos 5 blocos: 5 block
  entities = 5 renderers sobrepostos, letras em cima de letra.
- Painel remontado À ESQUERDA da porta (x2..x4, 3 telas), NBT só no cabeça.
- **Extensão é muda no código** (PainelLedBlockEntity.isExtensao): tem irmão
  no lado do cabeça → não desenha. Imune a save/load e a remontagem.
- O texto estica pela LINHA INTEIRA (PainelLedRenderer usa TELAS×16px).
- **Zelador v1.2.40:** mundos 1.2.36–39 tem a fileira velha demolida e
  remontada fora da porta, preservando texto/cor/brilho/modo.

### FIX: "ABERTO" entrando no meio do letreiro
- O ciclo NOME↔STATUS de 4s (1.2.25) saiu: o letreiro mostra SÓ o nome,
  esticado, 100% do tempo. O display dedicado ABERTO/FECHADO é o próximo
  passo ( pedido do dono, separado).

### FIX: crosswalk dupla (tinta sobre tinta)
- O template pintava a faixa de pedestre em DUAS camadas (y0 E y1).
  Agora: tinta só na de cima, CONCRETO BRANCO embaixo (diagnóstico do Skyu).
- Zelador converte mundos velhos (a camada de baixo vira concreto).

### FIX: hidrante sem gráfico / fora do JEI
- O items/hidrante.json (definição de item da 26.3) estava VAZIO (arquivo
  corrompido numa queda de sessão). Recriado — item visível de novo.

### FIX: riff do Juça tocando "aleatório" (vai dar acidente aqui)
- O riff disparava a cada 45s ENQUANTO alguém estivesse a 10 blocos — não
  era na chegada. Agora é tema de ENTRADA (edge-trigger): toca 1× quando
  alguém ACABA de chegar; gente parada perto = silêncio. Cooldown 45s manda
  no re-trigger (afastou-e-voltou).

### FIX: "imune a lava" (camisa do Matanza)
- A camisa aplicava fire resistance a cada tick mas NUNCA removia ao tirar
  (11s de imunidade fantasma — o banho de lava do Skyu sem esquentar).
- Tirou a camisa → efeito vai junto no mesmo tick. Efeito da camisa é
  AMBIENT; fire resistance de POÇÃO de outra fonte não é tocado.

### CHANGE: café verde agora tem uso (report do dono)
- **Extrato de Cafeína** = café verde + açúcar + garrafa (era cacau —
  herança de antes do cafezal existir). Cadeia completa: planta → colhe →
  extrai.

### NOTA: receita alternativa da maconha é INTENCIONAL
- 2 samambaias → 2 maconha_seda = bootstrap pré-farm (primeiras sedas →
  sementes → plantação + UV). Confirmado com o dono.

---

## v1.2.39 — JUCELINO "JUÇA" (23/09/2026)

### NOVO: o NPC Jucelino, o parça do Gago
- **Juça nasce rondando a frente do mercado** (lado da rua) junto do ciclo do
  Gago — e tem ovo de spawn preto/amarelo próprio.
- **Skin própria** (mesmo corpo barrigão do Gago, modelo reusado): franja preta
  desigual, olho cerrado, barba de 3 dias e o **Camel amarelo pendurado na boca
  com brasa acesa**. Fumaça na boca a cada ~3s, eternamente.
- **Tema de entrada cowpunk ORIGINAL** (síntese própria, power chord em Mi com
  gallop — vibe southern rock): toca quando alguém chega a 10 blocos dele,
  cooldown de 45s. Música DO Matanza não é reproduzida (copyright).
- **Fala "juça" no chat** → ele responde com o "hé hé" grave e puxa o Camel.
- **Doidão com cachaça**: jogue uma cachaça no chão perto dele → engole a
  garrafa, fica doidão 30s em zigue-zague e grita no chat `G-GAGO!!! VEM AQUI
  MANO!!!` — e o Gago responde (a dupla).
- **A Troca do Camel (estilo piglin)**: jogue um Cigarro Camel pra ele → bolsa,
  admira e devolve a troca arremessada de volta (cerveja gelada, 4 R$ ou 2
  baseados). **2 Camels de uma vez** = ele devolve a CAMISA DO MATANZA.
- Drop ao morrer: 2–5 Camels (e 1/5 de chance de dropar a camisa).

### NOVO: itens
- **Cigarro Camel** (fumável estilo baseado): papel + cenoura dourada → 3;
  pressa + tontura. Textura amarela característica.
- **Camisa do Matanza** (peito): lã preta ×7; **resistência ao fogo permanente**
  vestindo (poderes matanzísticos); repara com couro; asset de equipamento
  próprio (não usa a textura do couro vanilla).
- Sons: `juca_riff` (o tema) e `juca_hehe` (a risada). Lang pt_br/en_us completa.

---

## v1.2.38 — CONTROLE REMOTO (22/09/2026)

### Gago: o fim do ciclo de morte (causa-raiz da raiz)
- **O posto agora PERSISTE no save** (`PostoMercado` + `EmPosto` no NBT):
  antes, recarregar o mundo apagava o vínculo e a imunidade do Gago em
  serviço — ele morria sufocado, dropava cachaça/cerveja/R$/cartucho e o
  gerente repunha no mesmo lugar, eternamente. Reload não quebra mais.
- Teste do Gago corrigido: a limpeza do posto derruba os blocos plantados
  pelo teste (reforma, não loot) — a asserção agora mede só o loot de morte.

### A Central de Comando virou ITEM (v1.2.37 do painel preservado)
- **Novo item: `Central de Comando (Controle)`** — controle remoto de 1 slot
  (raro/épico). Segure, APONTE pro Painel de LED (ou pro letreiro do
  mercado) e clique: abre a tela de edição (2 linhas, 10 cores, brilho,
  FIXO/ANDANDO) sem tocar no bloco. Raytrace de 8 blocos: edita de longe.
  No ar sem alvo → avisa "Aponte o controle pra um Painel de LED ou pro
  letreiro!".
- **Tranca do letreiro respeitada**: letreiro trancado + controle → só abre
  com a CHAVE (comparador) na MÃO SECUNDÁRIA (a principal segura o
  controle).
- **Craft do controle**: vidro branco (tela) + ferro (corpo) + vidro,
  bloco de redstone e botão de pedra (teclas) — padrão `WIW/GRG/ S `.
- O painel de LED continua abrindo a Central por clique direito também
  (os dois caminhos coexistem); o payload/servidor não mudou.

### Recursos & infra
- Textura procedural do controle (telinha verde de LED + teclado com power
  vermelho) no `gen_textures.py`; modelo `minecraft:item/generated`.
- Item + receita gerados no `gen_farm_resources.py`; aba criativa atualizada;
- lang pt_br/en_us (nome + mensagem de sem alvo).

### Da v1.2.37 (já no jar anterior, presente nesta build)
- TV de tela plana (painel fino de 3px), texto esticado de ponta a ponta,
  ABERTO/FECHADO removido do letreiro (display dedicado vem depois),
  Central completa (texto/cor/brilho/modo), painel craftável em linha até
  3 telas e **fix definitivo do Gago** (barril intocável = loop de morte;
  agora o posto é liberado, o Gago é resgatado e o loot preso é bloqueado).

---

## v1.2.37 — TEXTURAS PADRAO DO MOD (22/09/2026)

### As 89 texturas do pack do usuario viram o padrao embutido no mod
- **Catalogo inteiro renovado**: 46 texturas de bloco (incluindo os 30 estagios
  das 5 plantacoes, refeitos em 1254×1254), 8 skins de NPC (Gago e variantes de
  bioma 128×128 + Traficante 64×64) e 35 itens — todas as artes manuais do
  usuario agora vem DENTRO do jar. O pack `minhas-texturas` deixa de ser
  necessario pra ver o art novo (e o F3+T tambem).
- **Corrigido: plantas nascendo VOANDO** — a causa era margem transparente
  embaixo das texturas de estagio (o modelo `cross` mapeia o PNG inteiro no
  bloco; margem = planta suspensa). O art novo ja sai ancorado no chao
  (margem 0px verificada nas 30 texturas) e agora vale sem pack.
- **Cadeia das plantacoes auditada** (maconha, uva, cafe, lupulo, papoula):
  blockstates completos (age 0–4 × uv_age 0–3, todos com modelo), loot por
  estagio (semente sempre; produto so no age=4; produto dobrado + semente
  extra no uv_age=3), sementes como BlockItem das plantas. Tudo conferido.
- **AGENTS.md ganhou a secao "Padrao para plantacoes"**: checklist obrigatoria
  pra planta nova ou alterada — estagios, ancoragem no chao (com o historico
  do bug), progressao visual de altura, loot por estagio e arquivos da semente.
- Texturas antigas preservadas em `backups/20260922-texturas-do-pack/`;
  as 6 exclusivas do mod (escopeta, placa, poste e 3 particulas) intocadas.
- Obs. de arte: `cafe_stage4` ficou ~2% mais baixo que o `stage3` — se nao for
  intencional, realinhar na proxima revisao do art.
- Previa antes×depois aprovada pelo usuario: `preview/previa-texturas-padrao.html`

---

## v1.2.35 — O CATÁLOGO DO ARTISTA (22/09/2026)

### As 27 texturas de item redesenhadas à mão (artes do usuário)
- **Catálogo inteiro em alta resolução**: as 27 texturas de item do
  consumível/arma/utilitário agora são artes manuais do usuário em 256×256
  (bebidas, pó, ovos, cartuchos, revólver, real, lâmpada, hidrante,
  faixa de pedestre, cultivos...). Ícones nítidos no inventário.
- **Blinda completa nos geradores**: os 6 scripts que geravam essas
  texturas (`gen_farm_textures`, `gen_textures`, `gen_opio`, `gen_real`,
  `gen_revolver`, `gen_npc_textures`) agora pulam as artes manuais
  ("SKIP (manual do usuário)") — rodar gerador NÃO sobrescreve mais o
  trabalho de mão. Regenerar uma exige mover o PNG pra backups/ antes.
- Originais 16×16 preservados em `backups/20260922-letreiro-fachada/`
- Downscale das artes originais (1254px) pra 256×256 com Lanczos —
  qualidade de inventário sem inflar o jar (~2,1 MB no total)
- ATENÇÃO revólver: a textura veste o modelo 3D (atlas); se o visual na
  mão ficar esquisito, separar ícone de inventário do atlas UV

## v1.2.34 — O DISPLAY DA FACHADA (22/09/2026)

### O letreiro vira display de fachada (pedido do usuário: "placa de verdade,
### na fachada, sem os dois pólos, estilo Satisfactory")
- **Sem torres/pólos**: a estrutura esquisita com 2 postes nas pontas
  (1.2.24–1.2.30) saiu da calçada — o vão em frente à porta ficou livre
- **Faixa montada NA FACHADA**: o letreiro atravessa os 15 blocos do prédio
  (largura `LARGURA_FACHADA`), colado na parede sob o beiral, ACIMA da porta;
  bloco CHEIO (a faixa É a parede, nada flutua na frente da entrada)
- **Nome esticado pelo tamanho da construção**: "MERCADO ESQUINÃO" numa linha
  só (`LINHAS_PADRAO` = 1 linha), o renderer escala até PREENCHER a largura
  (sem teto de 1.0; limitado pela altura do bloco ≈ 90% da fachada)
- **Uma tela só**: textura nova `placa_esquinao_tela` (sem moldura por bloco);
  a moldura verde fica só na placa avulsa; frente do modelo = tela contínua
- **Migração automática**: `VERSAO_ANATOMIA` (1/ausente = 4 linhas velhas,
  2 = torres, 3 = fachada) no NBT; o zelador da autocura derruba as torres
  velhas SEM drop e remonta a faixa na fachada (1 acima, 1 pra dentro) em
  ~30s, preservando texto, vínculo com o mercado e estado ABERTO/FECHADO
- **Nova parte `extensao`**: só o painel central tem block entity (o texto
  cruza a faixa toda); quebrar um bloco derruba a faixa inteira
- Template dos 3 mercados regionais regenerado com a faixa na fachada;
  faixa de pedestre do pátio volta a ficar LIVRE (a placa não mora mais ali)
- Testes atualizados: display de fachada (sem torres, extensões sem BE,
  queda em cadeia) + teste novo da migração; caixa de LED agora 240×16px

## v1.2.33 — O TRÊS-OITÃO (22/09/2026)

### Revólver .38 no mesmo padrão gun mod da 12
- **Tambor de 6 de verdade**: cada buraco é um bit no componente da stack;
  o tiro consome a CÂMARA alinhada com o cano e o tambor GIRA
- **Reload shell-by-shell**: segurar o botão direito carrega 1 cartucho por
  vez nos buracos vagos, começando pela câmara; encheu o tambor (ou acabou a
  reserva), o ferrolho fecha sozinho; soltar fecha com o que entrou
- **Giro no seco**: câmara vazia com bala em outro buraco = o tambor gira no
  click (o .38 de filme) em vez de exigir recarga; não consome a bala
- **Recoil com kick de câmera**: payload S2C compartilhado com a 12
  (RecuoPayload extraído pra classe própria); 5° pra cima, retorno suave
- **ADS ao segurar SHIFT**: zoom próprio (15% vs 20% da 12), dispersão 40%,
  +30% de alcance, kick reduzido; retícula tática verde unificada
- **Cartucho itemizado**: `cartucho_38` (chumbo + pólvora + 2 latão), sprite
  de latão com ponta de chumbo exposta; receita: 4 por craft; esqueleto dropa
  (12%); fidelidade do Gago vende (8 por R$ 60); arma reparável na bigorna
- **Modelo 3D próprio**: coronha de madeira inclinada, armação de aço, TAMBOR
  de latão com furos visíveis, cano curto e grosso com ponto vermelho na boca;
  poses de 1ª/3ª pessoa estilo besta, encolhidas pro tamanho de revólver
- **HUD do tambor**: 6 células sob o crosshair — câmara alinhada em latão,
  carregadas em vermelho, fases CARREGANDO/FECHANDO O TAMBOR
- **ArmasClient unificado**: kick/ADS/HUD das duas armas num lugar só
  (EscopetaClient aposentado); cooldown de cascavel 14 ticks; 7 de dano por
  bala (até 42 no tambor cheio), alcance 30, tudo em config/intoxicantes.json

### Verificação
- **38/38 game tests** (novo: `revolverMechanismReloadSpinAndCascavelFire` —
  recarga 1-a-1, fecho do ferrolho, 3 tiros em cascavel consumindo exatamente
  1 câmara cada, giro no seco sem consumir bala, prova aritmética de que
  nenhum cartucho some)
- Worldgen validado; `intoxicantes-1.2.33.jar` instalado em `../mods`

---

## v1.2.32 — A 12 EM NÍVEL GUN MOD (21/09/2026)

### Escopeta refeita como arma de mecânica de verdade
- **Tubo interno + câmara**: a stack da escopeta agora carrega um componente de
  estado (`tubo/câmara/timer/fase`) — cada arma é UM mecanismo; o tiro consome a
  CÂMARA (o cartucho engatilhado), não "qualquer cartucho do inventário"
- **Reload shell-by-shell**: segure o botão direito — a cada intervalo entra 1
  cartucho no tubo com "clac" próprio; solte quando quiser e o que entrou ficou
  (watchdog de 2.1s fecha sozinho se segurar até o fim)
- **Pump-action**: depois de cada tiro o pump cicla sozinho (clack-clack em 2
  tempos) e só então a câmara recarrega do tubo; tubo vazio avisa na action bar
- **Recoil com kick de câmera**: o servidor manda payload S2C e o client chuta
  pitch+yaw na hora, com 55% do chute voltando suavemente (~12 ticks) —
  constituição de atirador; kick reduzido no ADS
- **ADS ao segurar SHIFT**: zoom de FOV suave via mixin no `Camera.calculateFov`
  (o hook antigo morreu no 26.3), retícula tática própria, dispersão pela metade
  e +25% de alcance
- **HUD do mecanismo**: medidor sob o crosshair — câmara + cartuchos do tubo +
  fase (RECARREGANDO/PUMP)
- **Tooltip mostra o mecanismo**: câmara (✓/—) e cartuchos no tubo
- Config novos: `escopetaCapacidadeTubo` (5), `escopetaTicksPorShell` (5),
  `escopetaTicksPump` (8), `escopetaKickPitch` (7°), `escopetaKickYaw` (1.5°),
  `escopetaAdsFov` (0.8)
- **37 game tests** (novo: ciclo completo click seco → recarga 1-a-1 → câmara
  automática → tiro → pump → re-câmara, com prova de que nenhum cartucho some)

---

## v1.2.31 — O GAGO DE ÓCULOS (21/09/2026)

Playtest: "o Gago tá parecendo o Lula Molusco com aquele narigão; na vida
real ele é um cara simples, meio velho, que usa óculos — não faz sentido
aquele chapelão de palha".

### REFORMADO (modelo + textura, os 7 skins por bioma)
- **Nariz de gente simples**: o narigão de 2 patamares com a ponta
  pendurada (o "lula molusco") virou um nariz 2×2 discreto — sobra só
  2px pra fora da face
- **CHAPÉU DE PALHA REMOVIDO** do modelo (aba 16×16 + coroa 12×12) —
  no lugar: **cabelo grisalho pintado na textura** (topo da cabeça +
  têmporas ralas)
- **ÓCULOS de armação fina de tartaruga**: lentes com vidro (brilho +
  pupila mansa por trás), ponte e hastes nas orelhas
- **Cara de velhinho simples**: sobrancelhas calmas horizontais (não mais
  as "brabas"), bigode ralo grisalho sobre o sorriso de vendedor
- Mantidos: barrigão, avental verde com R$, braços cruzados (âncora do
  item na mão), chinelos, brinco de ouro e as 7 fantasias por bioma

## v1.2.30 — O GAGO NÃO SUFFOCA, NÃO SOME E NÃO VIRA LOOT (21/09/2026)

Playtest: "às vezes o Gago simplesmente some, e fica dropando um monte de
item do mod dentro do mercado".

### CAUSA RAIZ (ciclo de 3 elos, todos provados no código)
1. O gerenciador de plantão TELEPORTAVA o Gago pro posto (balcão/porta)
   SEM checar o destino: bloco novo no posto (reforma de pele da 1.2.26,
   plantio velho, offset de estrutura girada) = nasce DENTRO do bloco.
2. O vanilla mata sufocado (IN_WALL): o Gago morre e dropa cachaça +
   cervejas + R$ + cartuchos (o "monte de item do mod")
3. O gerenciador não vê mais Gago → nasce OUTRO no MESMO posto → morre de
   novo. Loop: "sumiu" + chão cheio de item.
   E o 2º caminho de sumiço: Gago de save velho sem persistência despawna
   a 64+ blocos (removeWhenFarAway permitia).

### CORRIGIDO (os 3 elos + defesa em profundidade)
- **Posto sempre livre antes do teleporte/spawn** (garantirPostoLivre):
  limpa o corpo inteiro do posto — intocáveis contêiner/porta/cama (nunca
  baú de jogador)
- **Imunidade a IN_WALL/CRAMMING em serviço** (emPostoMercado): mesmo se
  um bloco aparecer em cima depois, o sufocamento não passa
- **Feriamento fatal no posto solta a IA** em vez de morrer parado: ele
  escapa andando (sem mais estatua morta)
- **Morte em serviço não dropa loot** (guarda pela posição do posto): o
  loot de morte é pra quem MATOU o dono do bar de propósito — não pra
  encher a loja de item sozinho
- **Autocura de save velho**: Gago sem persistenceRequired vira
  permanente no 1º tick de server (nunca mais despawna)
- **Game test novo** (36º): posto ocupado → limpo → Gago vivo; IN_WALL
  ignorado em serviço; morte no posto sem loot. 36/36 verdes.

## v1.2.29 — O HIDRANTE NO LUGAR CERTO (21/09/2026)

Playtest: hidrantes "estão onde os carros deviam estacionar… não faz
sentido, e tem 2". Correto — hidrante em vaga é o OPOSTO da vida real
(perto de hidrante é onde se PROÍBE estacionar).

### CORRIGIDO
- **UM hidrante, na CALÇADA ao pé do meio-fio, ao lado da faixa de
  pedestre** (x4,z12 do template) — como na rua de verdade: no caminho do
  pedestre, nunca na vaga.
- **As vagas ficam 100% livres** — demarcação limpa, nada plantado nelas.
- **Zelador RECOLHE os 2 hidrantes velhos** das vagas (1.2.24~28): na
  primeira passada, hidrante sobre o asfalto do mercado nos offsets
  antigos (2,14/12,14) some sem drop — o mundo do tester se conserta
  sozinho, sem comando.
- Reforma de pátio velho planta o hidrante no offset novo; game test
  atualizado (novo nasce na calçada, velhos recolhidos, idempotente).

## v1.2.28 — A PLACA DE FRENTE PRA RUA (21/09/2026)

Playtest: a placa "piorou MUITO" — da rua só se via a CAIXA metálica cinza
com o texto verde flutuando colado nela; a cara de letreiro (matriz de LED
verde + moldura) tinha sumido.

### CAUSA RAIZ (regressão de convenção, prova matemática nos 4 facings)
- Na 1.2.24 a face `front` do modelo era a SOUTH local + blockstate
  wall-sign (south=0) — texto errado, textura certa.
- Nas 1.2.25~27 o texto foi consertado (`-toYRot`, convenção da fornalha)
  e o modelo movido pra FRONT na NORTH local (convenção da fornalha) — mas
  o blockstate **continou wall-sign**. Resultado: texto correto na direção
  certa, e a TEXTURA da matriz de LED no lado OPOSTO da caixa, para TODOS
  os facings. Quem olhava de frente via o verso metálico com texto pelado.

### CORRIGIDO
- Blockstate da placa regenerado na convenção da FORNALHA (north=0,
  east=90, south=180, west=270) — texto e textura agora caem na MESMA face
  física (= FACING) para os 4 facings; textura de LED de frente pra rua,
  texto na frente dela, dos dois lados da caixa.
- Auditoria ponta a ponta: renderer (ciclo NOME↔STATUS, rotação
  `-toYRot`, dupla-face), modelo (16×16, z5..11), blockstate (72 estados),
  anatomia (torres a ±2 com autocura 1×/30s), atlas LED no jar,
  LINHAS_PADRAO = MERCADO/ESQUINÃO.

## v1.2.27 — O POSTE DE VERDADE E A PLACA QUE SE CONSERTA (21/09/2026)

Novo playtest flagrou o poste "muito bugado" (caixas pretas desconexas no
pátio) — e a investigação com scanner de save provou a causa raiz de TUDO:
o mundo real nunca tinha visto a montagem que os testes viam.

### DIAGNÓSTICO COM PROVA DO MUNDO (não foi chute)
- Scanner próprio de .mca decodificou o save do tester: a placa era **UM
  bloco só** (`parte=painel` em (11,69,153)) — a "torre de 9" NUNCA nasceu;
  o poste A estava inteiro mas o poste B tinha o **CORPO desgarrado 1 bloco
  pro lado** (base x17, corpo x18, topo x17) — as caixas pretas flutuando
- Causa raiz compartilhada: o template só semeava PAINEL/BASE soltos e
  contava com `onPlace` — que a geração de estrutura **NUNCA chama** (flag
  16 do vanilla). Game tests passavam porque `setBlock` programático
  dispara; mundo real não. Falso-negativo desde a 1.2.23
- A anatomia de torre da 1.2.25 tinha defeito matemático: descia dy=-4..+1
  → **2 blocos ENTERRADOS** onde `montarTorre` recusa sólido → torre nunca
  nascia nem no jogo

### CORRIGIDO: o poste (luminária EM CIMA + aço visível + autocura)
- **Luminária agora EM CIMA da coluna**, como poste de rua de verdade:
  coluna 0..10 → capitel → lente 11..13 → tampa 13..14 (o braço lateral
  saiu — era ele que virava "negócio esquisito")
- **Texturas de aço galvanizado CLARO** (a coluna era RGB(35,32,28) e a
  luminária apagada RGB(21,21,23) — quase pretas: coluna fina + mata
  escura = caixas pretas desconexas do print)
- **AUTOCURA por tick agendado**: BASE reconstrói corpo+topo em cima (em
  ar/substituível, nunca em bloco alheio); TOPO reconstrói corpo+base por
  baixo; parte errada do próprio poste é reclassificada no lugar
- **Peça desgarrada é RECOLHIDA**: bloco de poste fora do eixo de qualquer
  coluna (sem base na própria x/z, COM base num vizinho ±1) some na
  passada do zelador — o poste B volta pro eixo sozinho

### CORRIGIDO: a placa (torre acima do solo + autocura + template completo)
- Torre agora é **4 blocos ACIMA do solo**: rodapé 1 abaixo do painel,
  2 colunas, capitel 2 acima — nada enterrado, nada flutuando; o rodapé
  pode tomar a faixa de pedestre (tinta nossa, 1px)
- **O template semeia a placa COMPLETA** (rodapés no y1, painel no y2,
  colunas y2/y3, capitéis no y4) + postes completos — nada mais depende
  de `onPlace` na geração
- **Autocura do painel por ticker** (1×/30s): placa de mundo velho adota o
  texto novo e ganha as torres que nunca nasceram — o letreiro solitário
  do save do tester vira a placa de 9 blocos ao carregar o chunk
- NBT do template: "MERCADO"/"ESQUINÃO" + marca `nova=1`

### MELHORADO: o zelador acelera
- Varredura **adaptativa**: nos primeiros 5 min após descobrir o mercado
  roda a cada 10s (a autocura apressa), depois 30s como sempre

### TÉCNICO
- **35/35 game tests** (anatomia nova da placa coberta: torre de 4 acima
  do solo, montagem/desmontagem nos dois sentidos, guarda anti-enterrado)
- Build completo verde; worldgen validado (62 JSONs + 3 NBTs)

---

## v1.2.26 — O MERCADO DO LUGAR (21/09/2026)

Refinamento de fundo: o mod agora nasce **do lugar** que ele ocupa, e o dono
da esquina também descansa. Itens 2 e 3 do TODO fechados.

### ADICIONADO: mercados regionais — o prédio nasce do bioma
- **Clássico** (plains/forest/selva/brejo/savanna): o branco/verde do SUL
  DISTRIBUIDORA que você já conhece
- **Sertão** (desert/badlands): adobe laranja, arenito lapidado, colunas de
  acácia — a bodega de encruzilhada
- **Serra** (taiga/neve/montanha): pedra fria, pinho escuro, smooth stone —
  mercadinho de serra com chão de freezer
- Padrão das vilas vanilla: 1 structure_set espalha, tags de bioma disjuntas
  decidem a pele — nenhum bioma fica sem mercado, nenhum bioma tem dois
- A descoberta (espiral) testa as 3 estruturas por chunk e grava a região no
  `intoxicantes_market.dat`; o `/gagomarket rebuild` ergue a pele do save
  (com fallback pro clássico)

### ADICIONADO: o cochilo do Gago
- 00:00 ~ 07:00 (fora do expediente) o Gago DORME na cadeira do balcão:
  sentado (pose SITTING), Zzz em baforadas de nuvem a cada 4s
- Tocar nele acorda com blip grave e frase embolada de sono (máx 1 a cada
  10s) — em vez da fila normal de portão fechado

### ADICIONADO: saudação VIP ao Dono da Esquina
- Quem alcança o tier máximo de fidelidade (30 compras) lê o letreiro e recebe
  o chamado respeitoso da casa — com corações e frase exclusiva (pt + en)

### MELHORADO: o zelador agora REFORMA
- Pátios de mundos 1.2.23/1.2.24 (sem faixa/hidrantes) ganham a travessia e
  os hidrantes na primeira passada do zelador (~30s após o chunk carregar)
- A reforma só nasce sobre o asfalto do mercado: nada em cima de bloco de
  jogador, terreno estranho ou ar

### TÉCNICO
- 35/35 game tests (2 novos: reforma idempotente do pátio; round-trip da
  região no NBT do mercado)
- `validate_worldgen` OK — 62 JSONs + 3 NBTs (mercado/clássico/sertão/serra)
  validados contra o jar mapeado do 26.3

---

## v1.2.25 — O LETREIRO DE VERDADE (21/09/2026)

O playtest da 1.2.24 mostrou o letreiro **pior ainda**: uns tracinhos verdes
miúdos no meio de um monólito preto. A investigação fechou 4 causas raiz —
todas com prova no código — e a placa foi refeita como letreiro de posto de
verdade.

### CORRIGIDO: os "tracinhos miúdos" (dupla contagem de largura)
- O renderer multiplicava `largura(linha) × GLIFO_ESPACO`, mas `largura()`
  **já conta** o espaço entre letras (5px de glifo + 1px de gap): a linha
  "media" 6× mais largo que a faixa e a autodefesa de escala espremia o
  texto pra ~0.17 → os tracinhos
- `LedFont.escalaPara` também misturava unidades (dividia a ALTURA pelo
  passo HORIZONTAL 6) → escala ~0.06. As duas medidas agora são px do grid,
  com teto 1.0 (nada de gigantismo)
- Guarda no game test: escala do conteúdo ≥ 0.9 — letra miúda no letreiro
  quebra o build

### CORRIGIDO: o texto nascia ATRÁS da caixa (convenção de rotação)
- `-toYRot() + 180` — mas `toYRot()` é convenção de ENTIDADE (sul=0), e a
  fornalha vanilla prova o blockstate (north=0°, frente no −Z local). O
  +180 jogava o texto pro lado de trás; só o verso dupla-face escapava
  (exatamente o print do tester)
- Agora: `-toYRot` puro, com o modelo na convenção da fornalha (frente na
  face local north), texto desenhado nos DOIS lados da caixa

### NOVO: anatomia de 5 blocos + CICLO NOME↔STATUS (letreiro de posto)
- A placa de 3 blocos não comportava faixa de 48px — o texto vazava 3× pra
  fora (os "negócios esquisitos no chão" da 1.2.23). Agora são 9 blocos:
  TORRE (6: rodapé, 4 colunas, capitel) + PAINEL × 3 + TORRE; vão de 3
  blocos de LED puro entre as torres
- O NOME ("MERCADO / ESQUINÃO") ocupa a faixa em escala 1:1 ("ESQUINÃO" =
  47px em 48px — letra grande de posto de gasolina); o status ABERTO/FECHADO
  entra SOZINHO a cada 4s, TAMBÉM gigante
- Quebrar qualquer parte derruba a placa inteira (coluna de 6); montagem
  robusta: nunca apaga bloco alheio, vão entre torre e painel fica livre

### CORRIGIDO: o monólito preto (moldura invisível + margens transparentes)
- A moldura era quase preta (`0E5A2E`) sobre painel preto — de longe tudo
  virava um bloco de carvão. Moldura verde VIVA (`27D96A`) + bisel + matriz
  de LED apagada desenhada na textura (cara de painel de verdade)
- As texturas da coluna/verso tinham pixels TRANSPARENTES — e o mod não
  registra camada cutout (a API do Fabric morreu no 26.3 e não há substituto
  client-side) → transparência renderiza PRETO. Texturas agora 100% opacas

### HIGIENE
- NBT velho de placa de 4 linhas: linhas vazias ("linha2"/"linha3" = "")
  não ressuscitam mais — nada de fantasmas espremendo o grid
- Template do mercado grava o MESMO LINHAS_PADRAO do código + marca `nova`
- game tests: anatomia 5 blocos, vão livre, ciclo LED com guarda anti-tracinhos

---

## v1.2.24 — O CONCERTO DA ESQUINA: LETREIRO, POSTE E HIDRANTE (21/09/2026)

A rodada do playtest flagrou a esquina inteira: **o letreiro bugado** (nada de
frente, letras gigantes de trás, "negócios esquisitos" no chão), **o poste
desconexo** (luminária flutuando sem coluna) e **o hidrante nanico**. E o
crash do render frame no mercado. Tudo morreu de uma vez.

### CORRIGIDO: CRASH ao chegar no mercado (LedFont)
- O mapa de glifos antigo era um array indexado por posição — um glifo com
  uma linha a mais atrasava TODOS os seguintes; qualquer letreiro com
  **espaço** (ou acento) pedia índice além do fim e estourava o render
  frame inteiro (o "crash ao chegar no mercado" do tester)
- LedFont reescrita: mapa por caractere (`Map<Character, glifo>`), cada
  glifo validado no registro (7 linhas, sempre), fallback = espaço em
  branco — **impossível** estourar índice de novo
- Acentos normalizados (Ã→A, Ç→C...) — o LED de esquina lê português

### CORRIGIDO: o letreiro (nada de frente / letras gigantes de trás / sujeira no chão)
- **Nada de frente**: o renderer desenhava com `scale Y negativo` — a face
  frontal nascia com winding invertido e o backface culling DESCARTAVA ela;
  de trás aparecia espelhada. Agora a pose nunca espelha e cada pixel é um
  **quad DUPLA-FACE** (frente + verso, como o texto vanilla): lê dos dois
  lados da rua, em qualquer rotação
- **Letras gigantes**: o pixel tinha 2px num painel de 16px —
  "DISTRIBUIDORA" (77px de fonte) desenhava ~9 blocos FORA da placa (era
  isso os "negócios esquisitos no chão"). O texto agora mora na FAIXA DE
  LED entre as torres (48px × 10px úteis) com escala uniforme calculada
  por `LedFont.escalaPara(...)` — nunca vaza da caixa, por mais texto que
  tenha
- **Texto de fábrica curto e grande**: `MERCADO / ESQUINÃO` (2 linhas) enche
  a faixa em escala 1:1 — letra de posto de gasolina de verdade (as 4
  linhas antigas encolhiam pra metade)
- Rotação alinhada com o blockstate (tabela da placa vanilla);
  ABERTO/FECHADO piscante, flicker de neon, pulso e zumbido mantidos

### CORRIGIDO: o poste de luz desconexo (luminária flutuando)
- O template do mercado semeava só o TOPO esperando "a base sobe pelo
  onPlace" — mas o onPlace só construía PRA CIMA: luminárias flutuando sem
  coluna no pátio, e o que o jogador colocava virava toco + caixa solta
- **Poste de 3 BLOCOS de verdade**: BASE (pedestal de concreto + arranque
  da coluna) + CORPO (coluna comprida) + TOPO (braço com a luminária de
  sódio pendurada) — colocou 1, ergueu 3; quebrou 1, caiu 3
- A montagem funciona nos DOIS SENTIDOS (base→sobe, topo→desce) e NUNCA
  apaga bloco alheio no caminho (o `setBlockAndUpdate` cego sumia com o
  que estivesse em cima)
- A luz (14) mora SÓ no TOPO aceso; template semeia o poste completo;
  texturas novas: chapa de metal com costuras, bulbo de sódio aceso e
  pedestal de concreto

### BALANCEADO: o hidrante deixou de ser nanico
- De 10px para **15px de altura** (quase um bloco): pedestal largão, corpo
  gordo, 4 braços laterais, boné e domo — hidrante de esquina de verdade
- O jato d'água cômico continua (usar a mão = splash + partículas)

### CORRIGIDO: o config nascia na pasta errada
- `config/intoxicantes.json` nascia em `saves/config/` (a conta antiga
  subia 2 diretórios e escorregava) — editar o config não fazia NADA
- Agora o mod acha a raiz da instância de verdade (a pasta que contém
  `mods/` e `saves/`) e sobe quantos níveis precisar

### NOVO: FAIXA DE PEDESTRE
- A tinta branca da travessia: bloco PLANO de 1px (anda por cima sem
  degrau), nasce em fileiras na saída da loja
- Textura procedural com falha de rolo; o asfalto debaixo cedeu? a tinta
  vai junto (nada de faixa flutuante)

### NOVO: HIDRANTE DA ESQUINA
- O vermelhão de ferro fundido: corpo com barras de sombra, tampa com
  parafuso, flange na base — 2 hidrantes nascem no pátio entre as vagas
- Usar a mão nele dispara o **jato d'água cômico** (splash + partículas)

### VERIFICAÇÃO
- 4 game tests novos: a fonte LED varre TODO caractere acentuado (7 linhas
  sempre, sem OOB) + conteúdo cabe na faixa; poste de 3 blocos montado dos
  dois lados sem apagar bloco alheio; hidrante alto + faixa plana; zelador
  seletivo — **33/33**
- Mundo velho: sem migração (a anatomia da placa não mudou; o template
  novo semeia postes completos em mundos NOVOS)

---

## v1.2.23 — A PLACA DE RUA DE VERDADE (21/09/2026)

O tester flagrou os 3 pecados capitais da esquina: **asfalto com xadrez rosa**,
**postes-toco de um bloco** e **o letreiro com 3 dizeres repetidos na fonte do
Minecraft**. Os 3 morreram de uma vez.

### CORRIGIDO: asfalto com xadrez rosa
- O `blockstates/asfalto.json` **nunca tinha sido gerado** (o modelo existia
  órfão — sem blockstate o jogo nunca liga o bloco ao modelo)
- Agora o gerador de recursos cria o blockstate; estacionamento negro liso
  de verdade, com as linhas cinza das vagas por cima

### NOVO: POSTE DE LUZ DE 2 BLOCOS
- Anatomia real de poste de rua: **base** (coluna de metal com braços curvos
  pros dois lados + travessa) + **topo** (caixa da luminária de sódio pendurada)
- Colocar 1 bloco ergue o poste inteiro (`onPlace` monta o topo); quebrar
  qualquer metade derruba as duas; o estado LIT é espelhado entre elas
- Modelos JSON com geometria própria (nada de `cube_all`); luminária tem
  textura acesa/apagada e continua ligando sozinha às 19h e apagando às 5h
- O template do mercado semeia o topo (a base sobe pelo onPlace)

### NOVO: LETREIRO DO ESQUINÃO 9-BLOCOS COM FONTE LED PRÓPRIA
- **Placa livre de rua na calçada** (saiu da fachada): 2 torres de 4 blocos
  (pedestal de concreto → coluna → capitel) segurando o painel suspenso na
  altura do olho — colocar 1 bloco ergue os 9; quebrar 1 derruba os 9
- **FONTE DE LED 5×7 codada em Java** (`LedFont`): bitmap A–Z, 0–9 e
  símbolos — a fonte do Minecraft saiu do letreiro pra NUNCA mais voltar
- O renderer desenha cada pixel como quad com atlas próprio
  (`submitCustomGeometry` + `RenderTypes.textPolygonOffset`), full-bright,
  com pulso de alimentação, flicker de neon e status ABERTO/FECHADO piscante
- **FIM do texto triplicado**: só o PAINEL tem block entity — as colunas
  deixaram de renderizar o letreiro 3× (a causa raiz do bug)
- Painel apaga o LED de dia (`LIT` do bloco) e o texto se ajusta sozinho a
  qualquer conteúdo (4 linhas + status cabem sempre)

### CONCERTO DE MUNDOS VELHOS
- A primeira passada do zelador (30s após descobrir o mercado) desmonta a
  placa velha de 3 blocos colada na fachada; a placa nova NÃO remonta sozinha
  (sem gerar em cima de quem tá olhando) — mande o tester colocar uma nova na
  calçada, ela nasce com as torres inteiras

### Verificação
- 29/29 game tests (placa cobrindo montagem 9-blocos, BE único, desmontagem
  total e persistência); build limpo; worldgen validado (DataVersion 5023)

---

## v1.2.22 — O GAGO DE VERDADE (21/09/2026)

Adeus, villager remendado. O dono do Esquinão agora tem **modelo codado do 0**
com a anatomia que ele sempre mereceu, e skin 128×128 desenhada pixel a pixel
no pipeline procedural do mod.

### NOVO: GagoModel — anatomia própria
- **Barrigão de 10 de largura** (o corpo do villager é 8 — o Gago come bem)
- **Narigão de 2 patamares**: o nariz de família com ponta que pende
- **Chapéu de palha** com aba 16×16, coroa e fita vermelha (palha tecida em
  trama xadrez, borda escura na aba)
- **Avental verde-dinheiro** com **R$ pintado no bolso do peito** — a farda
  do comércio
- Camisa branca com colete de couro aberto, calça de brim com barra dobrada,
  meia branca e **chinelos de dedo azuis** que são FILHOS das pernas (balançam
  com a passada)
- Rosto novo: sobrancelha braba, olho pequeno e esperto, sorriso de quem sabe
  o preço de tudo, brincos de ouro nas orelhas
- **Modo PUTO** ganhou expressão própria: a cabeça treme de raiva (o canal do
  `isUnhappy` do villager, no nosso modelo)

### NOVO: skin 128×128 por pipeline
- 7 fantasias temáticas por bioma mantidas e refeitas na skin nova (clássica,
  Sertão com cartucheira, Mata com flor, Cerrado com trançado, Sede Sul com
  cachecol de lã, Serra com faixa de tricô, Brejo com salpicos de lama)
- Todas validadas (largura de dados íntegra — nada de xadrez rosa)

### Técnico
- `ModelLayerRegistry` (Fabric 26.3) registra a layer `intoxicantes:gago`;
  renderer próprio com `GagoRenderState` (roupa + puto synched)
- Braços cruzados com a geometria EXATA do villager (pivot 0,3,−1, xRot −0.75)
  — a escopeta/bebida continua ancorada no colo pela `CrossedArmsItemLayer`
- Escala de sombra 0.6 (mais gordo, mais sombra)

---

## v1.2.21 — O ZELADOR DO ESQUINÃO (21/09/2026)

O tester flagrou: **folha de árvore DENTRO da loja**. Causa: a decoração do
bioma roda DEPOIS da estrutura na geração — árvore nascida no chunk vizinho
enfia copa e tronco pra dentro do prédio (o ar limpo do template só cobre o
próprio box da estrutura).

### NOVO: zelador da propriedade
- A cada 30s, uma varredura da propriedade do mercado (caixa 21×8×21 do
  centro) **expulsa material de árvore invasora**: folhas, troncos, mudas
  e bambu — sem drop, sem fumaça de serra, some quieto
- Só age em chunk carregado; custo desprezível (uma passada por 30s)
- A loja em si (lâmpada UV, plantação, estruturas do mod) fica ilesa
- Game test novo (29 total): planta folha+tronco+muda na propriedade e
  prova a expulsão com a lâmpada intacta

---

## v1.2.20 — A PLACA NASCIDA CERTA (21/09/2026)

O letreiro da 1.2.19 nasceu bugado na tela do tester: texto atravessado na
parede (espelhado, flutuando 1 bloco na frente do painel) e o item com
xadrez rosa/preto na mão. Três bugs de raiz, todos corrigidos:

### Corrigido: item com textura corrompida (xadrez rosa)
- 3 linhas do mapa de pixels do ícone tinham **17px** (sobrou um pixel de
  texto) — o PNG nasce com linhas de largura errada e o Minecraft recusa
- Ícone regenerado com as linhas nos 16px, e **validador novo** confere o
  tamanho dos dados descomprimidos de TODAS as texturas do mod

### Corrigido: texto do LED voando na parede / espelhado
- Rotação adotava a fórmula errada (180° extra) — o texto nascia de frente
  pra parede, espelhado; agora é o molde exato do vanilla
  (`−toYRot`, decodificado do bytecode de `PlainSignRenderer`)
- O texto era lançado no CENTRO do bloco (1 bloco na frente do painel);
  agora sai encostado na face do painel, 1px flutuando (sem z-fighting)
- Bloco de linhas descentrado: agora centrado de verdade no painel
- Linha de status encolheu pra caber: 5 linhas de 10.5px = 52.5px ≤ 64px
  úteis (antes o texto estourava o painel de 9px de altura)

### Corrigido: blockstate de east/west invertidos (placa "de lado")
- Tabela de rotação do blockstate não batia com a do wall sign vanilla
  (oficial: south=0, west=90, north=180, east=270) — o modelo nascia 90°
  fora da fachada; corrigido no JSON **e no gerador de recursos**

### Corrigido: multi-bloco desalinhado com a fachada
- As colunas nasciam SEMPRE no eixo X; com a fachada virada pra leste/oeste
  a placa desmontava no ar. Agora o eixo das colunas segue o FACING

### CORRIGIDO: a expulsão jogava o freguês lá pra baixo, trancado nas pedras (wtf mesmo)
- Report do playtest: o teleporte era `mercado+5/+1/+5` com `teleportTo(x,y,z)`
  SEM dimensão — na API do 26.3 essa assinatura manda o jogador PRO FUNDO DO
  MUNDO: caía na bedrock, preso dentro das pedras
- Além disso o offset fixo ignorava a rotação da estrutura e o terreno: com
  a fundação da v1.2.18 podia dar de cara em mureta/prédio em qualquer lado
- Agora o Gago dá uma CASA de verdade pro expulso: anel de raio 5..9 em
  volta do mercado, girando com a estrutura, só vale lugar com piso sólido,
  corpo e cabeça em ar e sem água/lava — priorizando LONGE do Gago (teto de
  24 blocos) e, no empate, o mais perto do mercado; sem candidato válido,
  ninguém teleporta (fica onde está)
- Zerado momento e dano de queda no teleporte: sem “aparecer já caindo”
- Game test novo (28 no total): chão sólido, corpo livre, longe do Gago,
  permanência no overworld e teleporte sem velocidade herdada

### CORRIGIDO: falas do Gago sem sentido
- "meu amor!" virou "m-meu chegado!" — o Gago é o simpático da esquina, não
  pretendente (em inglês já era "my friend")
- "VAI VER O CÉU!" virou "VAI TOMAR CHUMBO!" — ameaça que combina com a 12

### Técnico
- `teleportTo` ganhou o boolean final (26.3) e `isSolidRender` perdeu os
  argumentos — alinhado com a API oficial (conflito com sessão paralela)

---

## v1.2.19 — O LETREIRO VIVO (21/09/2026)

O letreiro agora respira com o horário da loja, o estacionamento ganhou
asfalto e postes que acendem sozinhos — e o Esquinão fecha às 00:00.

### NOVO: ABERTO/FECHADO no letreiro
- Linha de status no LED: **ABERTO (verde)** de 07:00 às 00:00,
  **FECHADO (vermelho)** na madrugada — mesma régua do plantão do Gago
- Estado **persistente** (sobrevive a save/load) e sincronizado ao client
- Na virada: **arpejo de campainha** sobe ao abrir (C maior, bom-dia pra
  comprar) e desce ao fechar, + poeira colorida sobre o painel
- Loja fechada de verdade: o Gago recusa o cliente com voz educada fora do
  horário (e anuncia "porta fechada" na virada, com a voz dele)
- LED esmaece com a loja fechada; o status **pisca** entre 23h e 00h
  ("fechando!") e dobra o zumbido de LED no fim do expediente

### NOVO: zumbido de LED + blips de leitura
- Perto do letreiro, um **zumbido sutil de lâmpada** (loop de proximidade,
  só quando o painel está visível); no fim do expediente ele acelera
- Terminou de ler? **Um blip por linha** (4 boops subindo de tom, som de
  note block de hat) — som server-side, todo mundo perto escuta

### NOVO: estacionamento com postes de luz
- Bloco novo **Poste de Luz** (coluna + tulipa): acende sozinho às 19h e
  apaga às 5h, sem redstone — e o MarketSystem reforça o horário
  (anti-fuso, mesmo com chunk longe)
- Bloco novo **Asfalto**: pátio do estacionamento pavimentado, vagas
  demarcadas com faixa clara, linhas de rolamento e mureta + canteiro
- Postes espalhados pelo pátio no template do mercado

### Técnico
- O `tell()`/`TickTask` do server saiu no 26.3: fila própria de notas
  (`MarketSystem.agendarNota`) drenada no fim de cada tick toca os jingles
- Dust particles do 26.3 (`DustParticleOptions(int, float)`) na virada
- Game test novo (27 total): estado ABERTO/FECHADO persistindo, portão do
  Gago recusando/aceitando cliente e poste seguindo o relógio
  (meio-dia apaga, madrugada acende) via `clockManager`

---

## v1.2.18 — O LETREIRO CODADO DO ZERO (21/09/2026)

Adeus, placa de carvalho do vanilla. O Esquinão agora tem LETREIRO DE VERDADE.

### NOVO: placa 100% custom (bloco + block entity + renderizador)
- **Multi-bloco 3×2×1**: duas colunas de sustentação + painel de LED central;
  quebrou uma parte, cai tudo (a placa é UM objeto na rua)
- **Visual codado do zero**: fundo preto profundo, moldura dupla verde e 4
  linhas de texto na fonte de LED (verde-esmeralda #39FF6E) com brilho
  full-bright — lê no escuro total — e **flicker de neon** ocasional que dá
  vida ao letreiro
- O texto gira com a FACING do bloco e fica legível dos dois lados da rua
- Clique direito na placa: som de blip + o Gago cumprimenta o leitor (a
  mecânica da placa vanilla, agora com a placa da casa)
- O texto sobrevive a save/load (block entity persistente)
- Colocação robusta: jogador, /setblock e o template do worldgen montam o
  multi-bloco igual (onPlace); desmontagem cobre jogador, explosão e máquina
  (affectNeighborsAfterRemoval do 26.3)
- Texturas geradas no pipeline procedural (painel, coluna, item)

### NOVO: o mercado ganhou terreno próprio (anti-“loja engolida por vila”)
- Template ampliado de 9×5×13 para **15×8×19**: o prédio agora vem com
  **pátio/estacionamento** em volta e praça própria
- **Fundação sólida** (beard_box): acabou o mercado flutuando na encosta ou
  afundado no terreno
- **Limpeza total de ar** sobre a estrutura: nada de água de rio invadindo o
  balcão ou árvore nascendo no meio da loja
- Todas as bordas do pátio ganham **mureta + canteiro** (a praça do Esquinão
  fica visível de longe)
- O letreiro novo entra no template no lugar da placa de carvalho (saves
  antigos mantêm a placa vanilla, que continua funcional)

### Verificação
- Game test novo (26 no total): multi-bloco monta/desmonta de uma vez, texto
  de fábrica gravado, leitura interativa e persistência em save/load
- 26/26 game tests verdes

---

## v1.2.17 — O DISJUNTOR DA LÂMPADA UV (20/09/2026)

Plantação indoor agora tem RISCO: alguém pode cortar a energia da sua lâmpada.

### NOVO: estado ligada/desligada controlado por redstone
- A Lâmpada UV ganhou um estado `lit` real (blockstate com textura própria:
  vidro violeta brilhante x apagado escuro)
- A regra é previsível e salva os mundos antigos:
  - **Energia chegando** (redstone ativo adjacente) = LIGADA
  - **Sem energia, com controle adjacente** (alavanca/repetidor/etc.) = DESLIGADA
    — alguém abriu o disjuntor
  - **Sem circuito nenhum** = LIGADA (plugada na tomada; lâmpadas antigas
    continuam funcionando)
- O zumbido toca só quando está ligada; quebrar a lâmpada desligada dá o som de
  desligar
- `isUvLit` e `temLampadaUvPerto` respeitam o estado: lâmpada desligada NÃO
  matura plantação (o boost ×2 de crescimento também para)

### BALANCE: a lâmpada vira investimento
- **Raio no config** (`uvRaio`, padrão 2 — era hardcode; dono de servidor
  calibra farm industrial subindo pra 3+)
- **Custo na loja: R$ 24 → 36** e **estoque 4 → 2/dia** — com a maturação
  confiável ano-redondo, o preço precisava acompanhar o poder
- O custo operacional é o próprio design do mod: **vigilância** — com circuito
  de redstone, qualquer vizinho pode abrir o disjuntor; sem circuito, a lâmpada
  está plugada direto na tomada

### VERIFICAÇÃO
- Game test novo (25 total): disjuntor nos 3 casos (tomada/energia/corte),
  maturação pausada com lâmpada apagada e religada, alavanca ligada energizando

---

## v1.2.16 — AS FAZES DA PLANTA CONTAM A HISTÓRIA CERTA (20/09/2026)

Segunda rodada do playtest (Jumbalaio + Poll, com o mod Jade na mão): as fases
de crescimento estavam difíceis de ler.

### VISUAL: "a 75% é idêntica à madura" / "só tem 2 estágios?"
- Medição por pixels confirmou os reports: stage2 vs stage3 tinham diferença média
  **55/765** (praticamente a mesma imagem) e a fase "crescida esperando UV"
  compartilhava textura com a quase-madura
- Nova leitura visual em **6 fases inequívocas**:
  1. broto → 2. meia verde → 3. alta verde → 4. **cor surgindo** (esmaecida, age 3)
  → 5. **cor cheia** sem brilho (madura, esperando UV) → 6. **dourada** (colhe!)
- A cor do fruto agora é sinal de MATURAÇÃO, não de crescimento; o brilho xadrez
  só aparece na fase madura; o corte de altura dos estágios 2/3 foi aberto
- Diferença média entre fases vizinhas: 55 → **182-390**; novo modelo/textura
  `*_plant_dormant` pra fase esperando-UV (blockstates e modelos regenerados)

### CORRIGIDO: "às vezes é quase instantâneo" (variação de crescimento)
- Era o boost ×2 no SOL forte (corrigido na v1.2.15 — o tester ainda não tinha
  pegado o jar); planta a céu aberto agora anda no ritmo vanilla

### CORRIGIDO: deriva de loot (regeneração expôs)
- O gerador escrevia `set_count 3` (+1 da pool base = **4x**), mas as tables em
  disco estavam afinadas à mão pra **3x total** — o game test guardava a spec
- Gerador = disk = teste: esperar a maturação vale **3x uniforme** pra todas as
  culturas (a semente sempre volta)

### NOTA
- "Acho que a papoula cresce mais rápido": ilusão das fases idênticas — o código
  das 5 culturas é idêntico (mesma `velocidadeCrescimento`); com as fases agora
  legíveis, a percepção de salto some

### TESTADO (24 game tests)
- Teste de colheita atualizado pra spec uniforme de 3x (gerador alinhado)

---

## v1.2.15 — BALANCE DA FAZENDA: O SOL NÃO É LÂMPADA UV (20/09/2026)

Segunda rodada de reports do playtest — um balance real e um esclarecimento de design.

### BALANCEADO: "as plantas crescem bem rápido"
- O `isUvLit` do mod considerava SOL FORTE (luz ≥ 12 + céu aberto) como "UV" — e o
  boost ×2 de crescimento valia pra ele: **toda fazenda ao ar livre andava 2× o ritmo
  vanilla, o dia todo**, lâmpada ou não
- Agora o **boost ×2 é EXCLUSIVO da Lâmpada UV** (a tecnologia do mod tem que valer o
  investimento); a fazenda a céu aberto anda no ritmo vanilla
- A **maturação** (UV_AGE 0→3) continua valendo sob sol forte — o early game sem
  lâmpada segue funcionando; o sol agora é caminho lento, a lâmpada é o caminho rápido
- Lógica extraída pra `UvCropBlock.velocidadeCrescimento` — conta exata, coberta por
  game test (0.15 solo a céu aberto / 0.30 com lâmpada)

### ESCLARECIMENTO: "maximizou 1 Gago, o resto fica com preço bom"
- É DESIGN, não bug: o Cartão Fidelidade é **do FREGUÊS** (por UUID do jogador), não
  da loja — você é conhecido em **qualquer esquina** onde abrir um Gago. A loja é dele,
  o cartão é seu kkkk

### TESTADO: +1 game test (24 no total)
- `uvLampBoostsGrowthButSunlightDoesNot`: conta exata das velocidades (solo a céu
  aberto = base; lâmpada = ×2) e a maturação sob sol preservada

---

## v1.2.14 — CORREÇÕES DO BETA TESTER: MEDIDOR SÓ DE ÁLCOOL, SEM PT/EN MISTURADO, MENSAGENS LEGÍVEIS (20/09/2026)

Três reports da rodada de testes — todos reais, todos corrigidos.

### CORRIGIDO: ficar "bêbado com maconha" ("não faz sentido")
- O medidor de embriaguez era ÚNICO: maconha, baseado, ópio, cocaína, LSD etc.
  alimentavam o mesmo medidor da cachaça
- Agora **SÓ ÁLCOOL emborracha** (cerveja, vinho, hidromel, cachaça, rum); erva, pó e
  comprimido têm os efeitos PRÓPRIOS dos itens — o medidor é de bebedeira, ponto
- O Traficante continua vendendo tudo; o Gago continua debochando de quem cruza o
  limiar (que agora só é cruzado por bebida)

### CORRIGIDO: português e inglês misturados ("you are caído")
- Raiz do problema: ~25 mensagens estavam ESCRITAS no código (Component.literal pt)
  e nunca passaram pelo lang — cliente em inglês lia português cravado
- Tudo migrado pra chaves i18n com tradução real nos dois idiomas: saldo/pagar/darreal/
  depositar/sacar, gagomarket (set/rebuild/pos), clock do mercado, anúncio de
  descoberta, expulsão do Gago, encerramento de atendimento…
- `money.intoxicantes.*`, `market.intoxicantes.*` e `entity.gago.expulso` novos nos
  dois langs

### MELHORADO: mensagens rápidas demais pra ler ("aparece no chat")
- Action bar dura ~2s — status de embriaguez, ressaca (entrada/fim), BLOP, cura e
  cabelo do cachorro AGORA VÃO PRO CHAT (dá pra ler, rolar e reler)
- Exceção proposital: o `*HIC!*` continua na action bar — é sabor que repete a cada
  ~10s; no chat viraria spam (o som e a nota já comunicam)

### TESTADO (23 game tests)
- Teste do gole atualizado: cerveja completa = dose 2, **baseado completo = 0 no
  medidor** (prova direta do report)

---

## v1.2.13 — O REFINAMENTO SUPIMPA: RESSACA, CAMBALEIO E A VANTAGEM DO GAGO (20/09/2026)

Polimento da embriaguez em quatro frentes — o ciclo completo da bebedeira agora tem
consequência, física, preço e cara própria.

### NOVO: RESSACA de verdade (o dia seguinte da bebedeira)
- Voltar a **nível zero vindo de estado bêbado** (≥ limiar da fala) deixa o freguês
  **DE RESSACA** por `embriaguezRessacaSegundos` (novo config, padrão 90s): náusea +
  lentidão + fraqueza constantes, com aviso de entrada e saída
- Zerar vindo de "alegre" não dá ressaca — só quem cruzou o limiar paga a conta
- **CABELO DO CACHORRO**: qualquer dose durante a ressaca cura ela na hora (e conta
  como dose normal, claro) — o clássico, agora mecânica kkkk
- Ressaca **persiste** no `intoxicantes_embriaguez.json` (relog não cura)
- Config em `0` desliga a feature

### NOVO: o Gago aproveita do bêbado (markup)
- Freguês **visivelmente bêbado** paga **+10% por nível** acima do limiar da fala
  (`embriaguezMarkup`, novo config), com **teto absoluto de 30%**
- Implementado em `FidelidadeData.precoComDesconto` — o **ponto único de preço**: a
  tela e a cobrança usam o mesmo método, o markup aparece pro freguês ANTES de pagar
- Limpo e "alegre" pagam normal; o desconto do fidelidade é aplicado primeiro

### NOVO: cambaleio mecânico
- No nível do soluço, **empurrão horizontal aleatório** (25%/s, config
  `embriaguezCambaleio`) — sem componente vertical: bêbado cambaleia, não voa

### MELHORADO: action bar gráfica + tradução de verdade
- A mensagem de estado virou **barra**: `▮▮▮▮▯▯▯ 4/7 — bêbado`, com cor por faixa
  (verde alegre, âmbar bêbado, vermelho caído) e rótulo "de ressaca" quando na conta
- Os rótulos eram texto pt_br HARDCODED (o en_us exibia português!) — agora são chaves
  `effect.intoxicantes.rotulo.*`: sober/tipsy/drunk/wasted/hungover

### TESTADO: +2 game tests (23 no total)
- `hangoverHitsAfterTheLastLevelAndDogHairCuresIt`: ressaca só depois do limiar,
  efeitos do combo, cabelo do cachorro, config desligada
- `drunkCustomerPaysMarkupAndStumbles`: matemática exata do markup (limpo, alegre,
  limiar, teto absoluto de 30%) e o empurrão do cambaleio no delta

---

## v1.2.12 — RELATÓRIO DO BETA TESTER: CURA, DOSE HONESTA E O GAGO NÃO É SERRILHA (20/09/2026)

Três achados do playtest (valeu, Jumbalaio!): um bug real, um buraco de design e um
desequilíbrio — todos corrigidos e cobertos por game tests.

### CORRIGIDO: dose contava com CLIQUE sem terminar o gole ("fica bêbado sem beber")
- O detector antigo contava a dose quando o uso ACABAVA — mas soltar o botão, trocar de
  item ou ter o baseado apagado pelo Gago também "acabam" com o uso
- Agora a dose (e a cura) só conta se o gole **COMPLETOU**: os ticks restantes de uso
  (`getUseItemRemainingTicks`) têm que chegar ao fim; interrupção no meio não conta nada

### NOVO: a cura da bebedeira ("tem a cura da cachaçada?")
- O **Extrato de Cafeína** agora SOBRIAt: derruba **2 níveis de embriaguez** na hora
  (com piso zero e aviso na tela: "sentiu a cabeça clarear...")
- Café não é mágico: sem reset total de uma vez — o fregues continua bêbado o
  suficiente pra lembrar da vergonha; pt_br e en_us

### BALANCEADO: o Gago não é mais serrilha de boss ("o wither não durou 5 segundos")
- Com o fix dos i-frames da v1.2.11 + tiro por segundo + munição infinita, o volley do
  Gago derretia um wither (300 de vida) em ~15s de automação
- Contra alvo de **vida máxima > 100** (withers, dragões, wardens...), o volley dele é
  **capado** em `escopetaCapDanoBoss` (novo config, padrão 6.0 — ~50s pra derrubar um
  wither: é comédia, não boss-killer); freguês comum continua levando o volley inteiro
- A 12 do **player** não sofre cap nenhum (quem paga cartucho tem dano pleno)

### NOTA
- Falar "gago" no chat enfurecer os NPCs da redondeza é a feature do mod desde a v1.1.0
  — o tester achou o gatilho, não um bug kkkk

### TESTADO: +2 game tests (21 no total)
- `interruptedSipDoesNotDoseAndCaffeineCures`: gole interrompido não conta nada, gole
  completo conta, cafeína cura 2 níveis, cura com piso zero
- `gagoVolleyIsCappedAgainstBossesButNotCustomers`: volley no wither capado, volley no
  freguês pleno

---

## v1.2.11 — O CASO DO WITHER: I-FRAMES DO 26.3 (20/09/2026)

### CORRIGIDO: a escopeta (do Gago E do jogador) não feria o Wither direito
- **Causa raiz decifrada no bytecode + provada empiricamente com game tests**: o 26.3
  MIGROU os i-frames de dano — o cooldown vivo agora é o campo público
  `damageCooldownTime`, e com ele ativo só entra dano **MAIOR que o último hit**
  (`lastHurt`). O `setInvulnerableTime(0)` que o mod chamava é OUTRO campo (legado)
  e nunca limpou o cooldown real
- Efeito: os 8 balins do mesmo tamanho viravam **1 hit** (o 2º em diante quicava), e o
  volley seguinte do Gago (dano igual a cada 1s) quicava inteiro — contra um chefe de
  300 de vida parecia "o gago não consegue dar hit no wither"
- **Fix**: `Chumbo.aplicar` (novo helper compartilhado pelos DOIS atiradores) zera o
  `damageCooldownTime` real a cada aplicação — o tiro inteiro entra e todo volley é
  limpo; de quebra, unifica a blindagem anti-Invulnerable (save antigo com escudo)
  num só lugar
- Nota de investigação: a primeira hipótese ("escudo anti-flecha do wither aceita só
  direct entity AbstractArrow/WindCharge" — lido do bytecode do `WitherBoss.hurtServer`)
  estava INVERTIDA; o diagnóstico empírico com matriz de dano desmentiu (o blindado
  recusa flecha e aceita melee fresco) e levou à causa real: o artefato de cooldown
  contaminava TODOS os resultados. Sem game test empírico, o fix teria sido o oposto

### Notas de API 26.3 (registros descobertos nessa rodada)
- Tipos vanilla saíram de `EntityType.WITHER` — o teste busca no registry (`lookupOrThrow`)
- Pacotes reorganizados: `WitherBoss`, `monster/zombie/Zombie`

### TESTADO: +2 game tests (19 no total)
- `gagoBuckshotLandsOnTheWitherThroughItsShield`: reprodução direta do report — dois
  volleys completos de 8 balins iguais entram num wither blindado (vida ≤ 50%,
  `isPowered`), com o segundo espelhando o primeiro
- `shotgunIFrameResetUsesThe263CooldownField`: prova da mecânica — hit inicia o
  cooldown do 26.3, segundo hit igual entra com o reset correto, e o reset legado
  NÃO limpa o campo (a prova do porquê do fix)

---

## v1.2.10 — EMBRIAGUEZ SUPIMPA: PERSISTÊNCIA, CONFIG, REFLUXO E O GAGO ZOEIRO (20/09/2026)

### NOVO: o sistema de embriaguez virou sistema de verdade
- **Persistência**: embriaguez ATRAVESSA relog/restart (`intoxicantes_embriaguez.json` no
  diretório do mundo, padrão PlayerMoney/Fidelidade) — o fregues bêbado continua bêbado
  quando volta; o logout só esquece o consumível em uso
- **Config calibrável**: `embriaguezDecaySegundos`, `embriaguezLimiarFonar`,
  `embriaguezLimiarHic`, `embriaguezCap` e `embriaguezTrancaTeto` no
  config/intoxicantes.json (com saneamento e coerência automática: a fala embaralha
  sempre antes do soluço, limiares nunca passam do teto)
- **Teto com tranca = REFLUXO**: dose no cap com a tranca ligada e o corpo DEVOLVE o gole
  (`*BLOP!*` grave + fumaça + aviso na tela) — nada de emborrachar além do cap
- **Cambaleio de carne e osso**: bêbado de verdade ganha náusea constante renovada e
  **fraqueza gradual** (braços de geleia que escalam com o nível) — efeitos ambient,
  sem chuva de partículas piscando no HUD
- **Gole com som próprio**: `glup.ogg` (0,22s, engolida descendo) ao terminar qualquer
  dose; pitch varia por gole e o refluxo usa o grave
- **Action bar com estado**: "Você está bêbado (nível 4/7)" — reenviada só quando o
  nível muda, sem spam
- ***hic!* NA fala**: no nível do soluço o fonar sai com `*hic!*` intercalado entre as
  palavras (determinístico, igual ao resto)

### NOVO: o Gago zoeiro da borracharia
- O fregues cruzou o limiar da fala bêbada perto do balcão (raio 8)? **Deboche com voz
  + fala gaguejada**: "E-ei! B-balança no pé!", "O b-balde de gelo é GRÁTIS, tá?!"
- Gatilho AUTOMÁTICO: o gole que cruza o limiar puxa a zoeira na hora — sem fila de
  espera pro próximo scan
- 30s de throttle entre deboches; freguês sóbrio não leva deboche nem de graça; raiva
  total resolve na bala, sem deboche
- Falas em pt_br e en_us

### TESTADO: +2 game tests novos + 1 atualizado (17 no total)
- `drunkennessDosesScrambleChatAndDecayOverTime` atualizado: passadas de 1s alinhadas
  com o tick (o bug que o teste antigo mascarava), `*hic!*` na fala do caído
- `drunkennessCapRefluxAndPersistenceRoundTrip`: cap vem do config (não 7 fixo),
  refluxo no teto, round-trip completo save/load da persistência e decay do config
- `gagoRidesADrunkCustomerAtTheCounter`: deboche automático ao cruzar o limiar, nada
  abaixo dele, throttle bloqueia spam, sóbrio não leva deboche de gatilho direto

---

## v1.2.9 — EMBRIAGUEZ: HIC + FALA EMBARALHADA NO CHAT (20/09/2026)

### NOVO: sistema de embriaguez com 4 estados
- **Dose por item**: cada bebida/droga consumida sobe o nível — cerveja/vinho/hidromel 2,
  cachaça/rum/LSD 3, ervas leves 1 (extrato de cafeína não emborracha, óbvio)
- **Decaimento**: 1 nível a cada 60s de vida limpa; dose nova zera o relógio
- **Estados**: 0 limpo · 1-2 alegre · 3-4 bêbado (**fala "fonar" embaralhada**) ·
  5-6 caído (fala pior + **HIC audível** a cada 8-14s com som próprio, pitch subindo
  com o nível, nota musical e `*HIC!*` na action bar)
- **Fala embaralhada via decorator de chat server-side** (`ServerMessageDecoratorEvent`,
  CONTENT_PHASE): o servidor reescreve a mensagem ANTES de distribuir — **todo mundo lê
  o fonar**, e troca de letras vizinhas + alongamento de vogais escala com o nível;
  determinístico (mesma mensagem = mesmo fonar, testável)
- Náusea curta do vanilla nos estados 3+ (a tela gira, como manda o figurino)
- Logout limpa o estado; sintetizado novo `hic.ogg` (pipa dupla agudo+grave)
- +1 game test: dose, limiar, fonar escalonado, decaimento exato em 60 passadas

---

## v1.2.8 — ARTE NOVA DA PRIMEIRA À ÚLTIMA GOTA + O GAGO VS. BASEADO (20/09/2026)

### NOVO: as 16 texturas de bebidas/consumíveis refeitas (item 4 do TODO)
- Mesmo pipeline procedural do resto do mod (`gen_textures.py` v2): pixel maps novos com
  **volume por rampa vertical** (líquido escurecendo pra baixo), **brilho de vidro** na
  parede dos recipientes e **contorno escuro automático**
- Silhueta própria por produto: caneca com espuma em 3 tons e bolhas (cerveja), taça de
  vidro translúcido (vinho), garrafa alta com rótulo amarelo/verde (cachaça), frasco
  bojudo com lacre de cera (hidromel), garrafa quadrada preta com faixa ouro (rum),
  baggie com zip e folhinha estampada (maconha-seda), **cone na diagonal com brasa
  acesa e fumaça** (baseado), espelho negro com linhas (cocaína), seringa com
  graduações (heroína), selo cartão com mandala (LSD), poeira dourada cintilando (pó
  estelar), cogumelo clássico (xamânico), vidrinho com névoa lilás (deserto), raiz
  retorcida em S (sombra), drusa facetada rosa (cristal) e ampola de farmácia (cafeína)
- Ícone do mod (caneca) regenerado da arte nova

### NOVO: o Gago DETESTA baseado no balcão
- Alguém puxando um baseado num raio de 4 blocos do balcão? O dono da esquina reclama:
  **voz própria + fala gaguejada** ("A-AQUI É BEBIDA fria, não erva!")
- Escalonado com memória enquanto a fumaça durar: 1ª vez reclama, 2ª vez reclama de
  novo ("o cheiro i-impregna no b-balcão!"), **na 3ª APAGA NA MÃO** — interrompe o puxão,
  trava o baseado por 10s e solta *"A paciência acabou! \*apaga com a mão\*"*
- 6s entre falas (throttle), contador zera quando a fumaça passa; raiva total (o "gago"
  no chat) resolve na bala, sem conversa
- Falas em pt_br e en_us; +1 game test cobrindo o escalonamento completo

---

## v1.2.7 — Partículas próprias, sons de transação e punchline nos balins (20/09/2026)

### NOVO: partículas do mod (item 2 do TODO)
- **dinheiro**: a nota de R$ voando quando a venda sai — sobe serpenteando como fumaça
  de fogueira, com a cara da própria cédula (sprite 16×16 gerado por código); a esquina
  que vende é a esquina que se vê
- **folha_maconha**: folhinha de sete pontas caindo da planta no ponto (age 4 + uv 3) —
  sinal visual de que vale colher; pousa, para de girar e se desfaz
- **fumaca_erva**: o bafo verde-erva do Baseado — tinta verde no client (setColor), sobe
  devagar expandindo; enquanto o fregues puxa (use duration), o Boca emite contínuo
  (server tick, custo zero quando ninguém fuma)
- Pipeline completo: 3 sprites gerados no mesmo pipeline das texturas do mod
  (`gen_particulas_sons.py`), atlas `particles/*.json`, `FabricParticleTypes.simple`,
  providers no client com `SingleQuadParticle` (26.3)

### NOVO: sons de comércio e combate
- **caixa_registradora**: "ca-ching" de duas campainhas metálicas quando a compra/venda
  sai no cardápio — transação tem festa agora
- **balim_acerto**: estalo seco + corpo grave quando o balim da 12 acerta — dano
  mecânico, comédia auditiva: quem toma 12 fica com o punchline (+ nota musical subindo
  da vítima)

### TESTADO: +2 game tests das progressões (12 no total)
- `loyaltyCardTiersAndAdvancementsProgressCorrectly`: cartão fidelidade completo —
  4 compras não dão nada, a 5ª sobe pra tier 1 (5%) e concede raiz + Freguês da
  Esquina, a 30ª é Dono da Esquina (15%), desconto calculado e piso de R$ 1
- `perfectHarvestAdvancementOnlyAtFullRipeness`: colher imatura não concede; no ponto
  (age 4 + uv 3) concede Colheita Perfeita

---

## v1.2.6 — Ingredientes das bebidas e correções do cardápio

- Gago compra lúpulo, uva, trigo, cana do mod, frascos de mel e garrafas vazias, conforme suas receitas.
- Café, folhas e ópio deixam de ser comprados; cotas antigas de lúpulo e uva são preservadas.
- Lotes e pagamentos explícitos, dicas de uso e motivos de indisponibilidade ao passar o cursor.
- Fidelidade e detalhes dos itens sem sobreposição; arrasto da barra e tecla de inventário corrigidos.
- Posição de rolagem preservada por aba; respostas de compra não reabrem um menu já fechado.

---

## v1.2.5 — Comércio e colheitas integrados (20/09/2026)

- Gago compra cinco colheitas em lotes de oito, com pagamento em saldo virtual.
- Aba Vender colheita integrada ao cardápio e à fidelidade existentes.
- Estoque diário por NPC, persistido no save, com reposição às 07h e adiamento durante atendimento.
- Gago vende sementes e lâmpadas UV; exclusivos de fidelidade também possuem cota.
- Traficante oferece três produtos diferentes por dia, quatro unidades de cada, por preços menores nos itens em comum.
- Catálogo e preços centralizados; servidor valida sessão, distância, saldo, quantidade e estoque.
- Correção das tabelas de colheita para 26.3 e continuação da maturação UV depois do crescimento.
- Testes de servidor e de cliente real em source set separado.
- Cliques do cardápio compatíveis com os códigos SDL do Minecraft 26.3.
- Preservadas as melhorias paralelas até 1.2.4, incluindo cotação diária da rua.

---

## v1.2.4 — SONS PRÓPRIOS + DESCOBERTA COM CURSOR + TESTES DE ECONOMIA/CONFIG (19/09/2026)

### NOVO: sons próprios do mod (item 1 do TODO)
- **soco_d12**: estouro proprietário da 12 (.ogg sintetizado) — substitui os fireworks no
  item do jogador E no tiro do Gago; a arma agora tem identidade sonora própria
- **voz_gago**: blip de fala (sine + vibrato) no lugar do `VILLAGER_AMBIENT` — o Gago
  finalmente tem voz própria, e não a do vilarejo vanilla
- **zumbido_uv**: hum elétrico grave ambiente da Lâmpada UV (~1 a cada 50s, animateTick) —
  quem ouve o zumbido sabe que tem plantação indoor na esquina kkkk
- Pipeline completo: 3 .ogg sintetizados com ffmpeg + `sounds.json` + registro Java;
  nova classe `LampadaUvBlock` (o bloco precisava de `animateTick` próprio)

### OTIMIZADO: descoberta da estrutura não revista mais chunks
- Era: cada tentativa (a cada 30s) revarrava a espiral INTEIRA desde o centro (~2.400
  checagens de estrutura por passada, mesmo já descartadas)
- Agora: cursor progressivo (anel + índice) com orçamento de 600 chunks NOVOS por
  tentativa — nunca re-checa; rodada completa sem achar = backoff de 10 min (log 1x)
- Centro da espiral fixado por sessão (spawn mudando no meio não escorrega o cursor)
- Bônus: cotação da rua cacheada por dia comercial (o refresh de 1s dos NPCs não roda
  mais o `Random` 7x por NPC a cada segundo — pauta fixa do dia, mesmo resultado)

### TESTADO: +2 game tests (10 no total)
- `balanceSaturatesAtCeilingAndNeverGoesNegative`: o teto de R$ 2.147.483.647 (fix do
  v1.2.3) — overflow satura, `/pagar` sem fundo não mexe no pagador, ajuste negativo
  funciona sob saturação
- `gagoShotgunFollowsConfigNotHardcodedValues`: a 12 do Gago obedece o config —
  cooldown exato do config no tiro do NPC, dano calibrado aplica, acalmar guarda a arma

---

## v1.2.3 — AUDITORIA: OVERFLOW DE SALDO + A 12 DO GAGO OBEDECE O CONFIG (19/09/2026)

### CORRIGIDO: saldo podia virar negativo por overflow de int
- `PlayerMoney.add` somava em `int`: `/pagar 2000000000` pro cara com saldo alto estourava
  e virava saldo **negativo**, que o `set` zerava — "meu saldo sumiu sozinho kkkk"
- Agora soma em `long` e satura em `Integer.MAX_VALUE` (2.147.483.647 R$ — fundo suficiente)
- Todos os caminhos (`/pagar`, `/darreal`, venda de colheita) herdam a proteção

### CORRIGIDO: a escopeta do Gago ignorava o config/intoxicantes.json
- Dano por balim, nº de balins, cooldown e alcance eram hardcoded (3.0/8/60/10) no NPC —
  o admin baixava o dano no config e o Gago continuava metralhando com os valores antigos
- Agora lê do mesmo config da 12 do jogador: quem calibra a arma calibra os DOIS atiradores;
  mudança de comportamento: cooldown do NPC 60 → 20 ticks (igual ao do item, que é o padrão do config)
- `[Escopeta] tiro:` no log passa a refletir balins do config também pro Gago

### AUDITADO (sem mudança)
- Rede/sessão do cardápio (handlers já rodam na main thread), validação de compra,
  watchdog, estoque diário, worldgen JSONs, texts de lang, client screen

---

## v1.2.2 — CONQUISTAS + CONFIG + ÓPIO COM NICHO (19/09/2026)

### NOVO: progressões — aba "O Esquinão" (6 conquistas)
- **O Esquinão** (raiz) → **Salve, Freguês!** (primeira negociação com o Gago ou Traficante, trigger `villager_trade`) → **Freguês da Esquina** (5 compras no cartão) → **Dono da Esquina** (fidelidade máxima + a 12 pra casa, challenge)
- **Colheita Perfeita**: colher uma planta no ponto exato da maturação UV (award programático no `playerWillDestroy` do `UvCropBlock`)
- **Dono da Doze** (challenge): despachar o Traficante — trigger `player_killed_entity` com predicate de entity_type
- Conquistas de fidelidade usam o padrão `impossible` + award programático em `FidelidadeData.registrarCompra` (avança junto com o cartão)

### NOVO: config/intoxicantes.json
- Criado com padrões no primeiro boot (raiz da instância, irmã de `mods/`); editável sem recompilar
- **Escopeta**: cooldown, dano por balim, alcance, nº de balins
- **Cotação da rua**: liga/desliga a flutuação e define o intervalo (mín/máx)
- **UV**: chance de maturação por tick e liga/desliga a cue de som+partículas
- Saneamento de faixa: config escrito errado cai pro padrão em vez de derrubar o mod

### BALANCEADO: ópio com nicho real
- Era 100% desvantagem (enjoo + fraqueza) — ninguém consumiria; agora é o **anestésico de rua**: Resistência 30s + Fraqueza 15s (tanque barato que deixa lento, a lore finally bate com o efeito)

### AUDITORIA (nada a fazer, já existia)
- Loot dos NPCs: Gago (cachaça, cervejas, R$ 5–25, 2–5 cartuchos) e Traficante (estoque + partículas) já implementados
- Reparo da escopeta: componente `REPAIRABLE` com cartucho já configurado (tooltip era honesto)

### Nota técnica
- `conditions.entity` em advancement no 26.3 é **objeto único** (a lista quebra o registro — o game test pegou); padrão confirmado contra o `kill_a_mob` vanilla

---

## v1.2.1 — LAPIDAÇÃO: ECONOMIA, MATURAÇÃO, ESCOPETA E ACABAMENTO DO MERCADO (19/09/2026)

Rodada de profundidade no que já existe, na ordem sugerida — nada de item novo, tudo refinado:

### NOVO: cotação da rua flutua por dia (escolha real entre vendedores)
- Os preços do **Traficante oscilam −30%..+40% por dia comercial** (seed = dia + produto; a mesma oferta custa igual o dia inteiro, muda às 07h)
- A rua **nunca fica mais cara que o Esquinão** (teto = preço do Gago −1): a escolha é "rua barata e escassa (4 unid.)" vs "Gago caro e abastecido" — esperar a cotação baixar virou estratégia
- Game test atualizado pra assinatura nova (`traficante(random, dia)`) e roda 100 seeds confirmando rotação completa + invariantes de preço

### NOVO: maturação UV que vale a espera
- O **momento** de amadurecer é audível e visível: som de composto pronto + burst de partículas de nota (cifrão no ar = dinheiro maduro)
- Planta madura solta **partículas discretas** em loop (client-side, custo zero) — dá pra achar a planta pronta de longe
- Textura madura com **brilho dourado** a mais (semiótica de "colhe agora")
- Loot de UV_AGE=3 reescrito: **3× produto + semente garantida** — esperar deixa claro que compensa

### NOVO: personalidade nos consumíveis
- **Lore para todos os itens** (tooltip com a "cara" de cada um: "Turbinha de mineração: cava em velocidade absurda, paga com cansaço") — cada item agora tem nicho e situação pra usar

### NOVO: advertência do Gago antes da bronca
- Clique acidental no Gago não vira mais caçada: ele dá **1 advertência de 30s** ("E-ei! Respeito, na esquina! Próxima, SACO A DOZE!")
- Só a **segunda** ofensa no prazo saca a 12 — a provocação de propósito continua exagerada, do jeito que tem que ser

### MELHORADO: sensação da escopeta
- **Som em 3 camadas** (estouro de cano + bass sub + recarga do ferrolho no cooldown): tiro encorpado
- **Indicador de munição na action bar** a cada tiro (`§7Cartuchos: §fN`) e **click seco + aviso** quando está vazia
- Cooldown de 1s visível no vanilla já existente + recuo (kick) mantidos; falloff de dano por distância da rodada anterior

### MELHORADO: texturas da fazenda (v2, redesenhadas)
- **Sementes**: de 3 pontinhos pra **pacote de sementes** (sachê de papel kraft com costura, broto estampado e etiqueta na cor da cultura) — legível no inventário como o vanilla
- **Plantações**: de rabiscos pra **plantas construídas por código** com identidade por cultura — maconha com buda no topo e folhas caídas, lúpulo com cones pendurados, uva com cacho em losango sob copa, café como arbusto com cerejas em pares, papoula com flor de cápsula; crescimento revela a planta de baixo pra cima (broto → média → alta → madura)
- **Lâmpada UV**: de quadrado roxo chapado pra **luminária industrial** — item com carcaça escura, corrente e dois tubos roxos brilhando; bloco com painel metálico, tubos verticais com highlight de vidro e grade de ventilação
- Aprovada por inspeção visual (preview HTML com zoom pixelado) em 3 rodadas de iteração

### MELHORADO: acabamento do mercado
- **Luz de noite**: fileira de shroomlight embutida no teto interno — a loja fica iluminada depois que escurece
- **Quadro de preços interno** (placa "— CARDÁPIO —" com valores base) na parede do fundo, atrás do balcão — menu de bodega visível de dentro
- Validador worldgen agora **rejeita wall_sign sem suporte** e NBT de placa na paleta (os dois bugs do letreiro nunca mais passam)

---

## v1.2.0 — CARDÁPIO DO ESQUINÃO + CARTÃO FIDELIDADE (19/09/2026)

### NOVO: tela de comércio própria (fim da UI genérica de vilarejo)
- **Clique-direito no Gago abre o CARDÁPIO DO MERCADO ESQUINÃO**: tela customizada com letreiro verde "SUL DISTRIBUIDORA / & MERCADO ESQUINÃO", papel-moeda bege e cartão fidelidade — nada de cara de vilarejo vanilla
- **Abas COMPRAR e VENDER**: compra bebidas/drogas/exclusivos com R$ do saldo; **o Gago COMPRA suas colheitas** (8 unidades por lote) com cota diária — plantar agora tem comprador fixo na esquina
- Desenho 100% vetorial pelo pipeline de GUI do 26.3 (`GuiGraphicsExtractor`), hit-testing manual: o que parece clicável é clicável (botões R$ por linha, scrollbar arrastável, rodapé com saldo e Fechar)
- Tecla **E** fecha o cardápio e abre o inventário sem prender a sessão; Esc e botão Fechar encerram o atendimento limpo

### NOVO: cartão fidelidade do Esquinão
- Cada compra conta no seu cartão (persistido em `intoxicantes_fidelidade.json` no mundo, por UUID)
- **4 níveis**: Freguês · **Freguês da Esquina** (5 compras, −5%) · **Cabaré VIP** (15, −10%) · **Dono da Esquina** (30, −15%) — desconto aplicado direto no preço, com preço antigo rasurado na tela
- **Itens exclusivos por nível**: Cartucho ×8 (R$ 60) no nível 1, Hidromel ×4 (R$ 120) no VIP e **a própria ESCOPETA por R$ 300** pro Dono da Esquina
- Card na tela mostra nível, contador, barra de progresso e quanto falta pro próximo nível; a compra reenvia o cardápio (subiu de nível = exclusivo aparece na hora)

### Detalhes técnicos
- Rede própria por payload (`abrir_cardapio` S2C, `comprar`/`vender_colheita`/`fechar_cardapio` C2S) com **validação 100% server-side**: sessão ativa, Gago vivo a ≤6 blocos, estoque, saldo — cliente desatualizado não compra nada
- **Sessão de atendimento com watchdog** (meio segundo): fregues longe, morto ou em outra dimensão derruba a sessão, fecha a tela no client e solta o Gago pro posto; Gago PUTO encerra atendimento
- Catálogo/estoque/cotas no servidor (`TradeCatalog`, `DailyTradeStock`, `MarketInventory`, `MarketTransactions`); reposição diária às 07h
- Descobertas de API do 26.3 aplicadas: `Gui.setScreen` (o `Minecraft.setScreen` não existe mais), `mouseReleased(MouseButtonEvent)` de 1 argumento, `PayloadTypeRegistry.clientboundPlay()/serverboundPlay()`

---

## v1.1.1 — MERCADO FUNCIONAL DE VERDADE (19/09/2026)

### CORRIGIDO: NPCs ficam parados durante o comércio (rodada 8)
- **Gago e Traficante viram estatuetas enquanto a tela de troca está aberta**: IA congelada (`setNoAi`), navegação parada e velocidade zerada a cada tick de atendimento — o vendedor não passeia mais no meio da compra
- **MarketSystem não teleporta mais o Gago no meio do atendimento**: o gerenciador respeita `isTrading()` — a mudança de plantão (00:00, porta↔balcão) espera a tela fechar; o `stopTrading` devolve ele ao posto sozinho (ou ao passeio, se for Gago de ovo fora do mercado)
- **Gago PUTO não atende mais**: com a 12 na mão, clique-direito falha — faz sentido e evita abrir cardápio durante a perseguição
- Gancho no `stopTrading` (chamado pelo vanilla ao fechar a tela / afastar / morrer): restaura o estado certo — posto do mercado com IA travada, ou IA livre pra passear

### NOVO: Gago com fantasia temática por bioma (rodada 7)
- **7 roupas temáticas** (mesma cara bege do Rei do Bar, muda o tecido + um detalhe desenhado): **Plains** camisa vermelha clássica · **Deserto/Badlands** túnica de couro com cinto (Gago do Sertão) · **Selva** verde-mata com flor no peito · **Savanna** ocre do capim dourado com faixa de palha · **Neve** casaco azul-petróleo com botões (a "sede matriz" do Sul kkkk) · **Taiga/montanha** verde-pinho com faixa de lã · **Swamp** marrom-pantano com salpicos de lama
- A variante é **detectada no 1º tick do servidor** pelo bioma onde ele está (template/ovo/comando/save antigo — todos cobertos) e **gravada no NBT** (`Roupa`): o Gago mantém a roupa dele mesmo te perseguindo pra outro bioma — não troca de fantasia no meio da bronca
- Implementação: dado sincronizado próprio no servidor (`DATA_ROUPA`); no cliente o renderer o traduz pro **VillagerType vanilla** correspondente (os 7 tipos do vanilla = nossas 7 roupas) no campo `villagerData` do render state — o mesmo canal que o vanilla usa pra textura de vilarejo, sem subclassificar o render state (a `CrossedArmsItemLayer` exige o generics exato)
- Gerador `gen_npc_textures.py` refatorado: função `pintar_gago(tecido)` + tabela de 7 variantes com detalhes desenhados por região do torso

### NOVO: vida no mercado — Gago reage ao letreiro e a pedradas (rodada 6)
- **Ler o letreiro** (botão direito em qualquer placa perto do mercado, raio 24): o Gago cumprimenta o fregues — 3 falas novas em pt_br e en_us ("É issso aí! O melhor mercado da esquina, lêlogo lê!"), com partículas felizes e throttle de 8s (não vira spam)
- **Vandalismo** (soco/bomba em bloco num raio de 16 do mercado, jogador NÃO-criativo): o Gago **saca a 12 por 30 segundos** e vai atrás do vândalo — falas novas ("Vandalismo na MINHA esquina?! SACA A DOZE!"). Diferente do "gago" no chat (5 min de raiva total); passou os 30s, guarda a arma e volta ao balcão
- **Fix de oportunidade**: quando a raiva expirava, a 12 ficava na mão do Gago pra sempre (o código só adicionava a arma, nunca removia). Agora ele GUARDA a arma ao acalmar
- Eventos via `UseBlockCallback`/`AttackBlockCallback` (Fabric API), ambos com `PASS` — a edição da placa e o soco funcionam normal por cima

### CORRIGIDO DE VEZ: letreiro + dano da 12 (rodada 5, com prova empírica)
- **Letreiro — causa raiz achada por estatística do jar vanilla**: o NBT do texto da placa estava na **PALETA** do template, mas o `StructureTemplate` do 26.3 **só repassa o nbt da ENTRADA DE BLOCO** (`blocks[*].nbt`) — varredura dos 1511 templates vanilla: **0** casos na paleta, **5597** na entrada (baús de shipwreck etc). A placa sempre nascia SEM TEXTO. NBT movido pra entrada + validador agora REJEITA nbt na paleta
- **Letreiro — 2º bug**: a placa estava no plano da fachada (z9) com **ar atrás** (z8, interior) — wall_sign desanexa sem suporte e virava item caindo. Agora ela fica na FRENTE do alpendre (z10, x7, acima da porta, igual à foto) com a parede branca de suporte atrás; validador checa suporte de toda wall_sign (facing → bloco oposto tem que existir e não ser ar)
- **A 12 não dava dano em NADA**: o `getEntityHitResult` do vanilla varre alvos por **células de chunk no stream** — alvos finos (placas, falanges) escorriam entre células e o raio de 24 blocos não pegava NINGUÉM às vezes. Substituído por detector de **cone amostrado** (12 amostras por balim, teste esfera-AABB, vitima mais próxima leva — o balim "para" no que atravessa). Mesma coisa no tiro do Gago
- **Blindagem anti-invulnerável**: NPCs de saves antigos podiam estar gravados com `Invulnerable=1` — o `hurt` engole o dano SEM LOG nenhum (parece "não dá dano"). Agora: template grava `Invulnerable=0`, boot derruba o escudo de Gago/Traficante no load (`ServerEntityEvents.ENTITY_LOAD`), e o tiro derruba na hora se encontrar escudo (com warn no log)
- **Diagnóstico embutido**: cada tiro loga `[Escopeta] tiro: N balim(s) em M vitima(s)` — se algum dia "não der dano" de novo, o log diz exatamente onde a cadeia quebrou (0 balins = mira/AABB; vítima presente sem dano = escudo/source)
- **Perfuração de vidro generalizada**: trocado `TransparentBlock` (classe) por `isCollisionShapeFullBlock` (propriedade física) — pane/vidro/grade/barras NÃO são full-block e não travam chumbo; concreto cheio trava. Vale pro tiro do player e do Gago
- Mercado agora nasce em **37 biomas** (tag própria), incluindo neve — o bug "só na neve" ficou pra trás (confirmado pelo usuário)

### CORRIGIDO: rodada 4 do playtest (rebuild + biomas)
- **Mercado só nascia na NEVE**: o `structure/mercado_gago.json` apontava pra tag vanilla `#minecraft:has_structure/igloo` (referência copiada do igloo — o igloo só existe em bioma nevado!). Criada tag própria `#intoxicantes:has_structure/mercado_esquinao` com 21 biomas quentes/temperados (plains, savanna, desert, badlands, jungle, forest, beach...)
- **Validador aceita tags de bioma do próprio mod**: falso positivo travou o build anterior — ele só conhecia tags vanilla e rejeitava `#intoxicantes:...`. Agora valida tags do namespace do mod conferindo que o arquivo existe em `tags/worldgen/biome/`
- **`/gagomarket rebuild` não alterava nada**: o jar instalado era anterior às correções (o build quebrado no validador nunca foi instalado) e a versão do comando colocava o template com rotação NONE na âncora errada (7 blocos deslocado). Agora: âncora centro→origem com rotação derivada dos offsets persistidos

### CORRIGIDO: lote do playtest 3
- **Lâmpada UV virava cubo de vidro**: o model do BLOCO usava a textura do ITEM (ícone com cantos transparentes) — dava pra ver o céu através do bloco. Textura full-face dedicada (`textures/block/lampada_uv.png`, painel repetido 3×3) e model apontando pra ela
- **Escopeta deitada na mão**: o modelo era construído com o cano no +X (deitado); o display X=-90 do crossbow precisa de cano no +Y. Modelo remapeado `(x,y,z)→(z,x,y)` no gerador + display verbatim da besta vanilla — agora a arma fica EMPUNHADA (cano apontando pra onde mira, coronha pra baixo)
- **Gago desarmado por padrão**: só SACA a 12 quando ofendem ele; guarda de volta quando a raiva passa (o template NBT também não traz mais arma)
- **Escopeta não feria o Gago**: os balins paravam nos VIDROS da vitrine (clip do vanilla para em TransparentBlock) — agora o chumbo **perfura vidro/pane** (até 4 camadas), no tiro do player E no do Gago (ele atira através da vitrine de dentro)
- **`/gagomarket rebuild`**: estrutura gerada é imutável — chunks antigos NUNCA atualizam. O comando recoloca o template atual (letreiro, moldura, tudo) na posição do mercado do save
- **Porta desalinhada dos vidros**: moldura de concreto cinza (x2/x12) entre as colunas verdes e as vitrines — as panes conectam entre si e não "vazam" pro vão da porta

### CORRIGIDO: escopeta calibre 12 sem dano (rodada 2, playtest)
- **Escopeta do player**: os 8 balins voavam no mesmo tick e o vanilla rejeita dano repetido dentro dos i-frames (`invulnerableTime > 10`) — 7 dos 8 chumbinhos eram engolidos e a rajada virava um tapa de 3.0. Agora o dano é **acumulado por vítima** e aplicado de uma vez (até 24 de perto, decaindo pela dispersão natural)
- **Escopeta do Gago (pior)**: o raycast **excluía o próprio alvo** (`e != alvo`) — o tiro dele nunca podia acertar quem estava na mira, só quem passasse na frente do chumbo. Corrigido; mesmo esquema de dano acumulado

### POLIMENTO DO PLAYTEST (rodada 2)
- **Escopeta aponta pra frente** (1ª e 3ª pessoa): display agora usa a rotação X=-90 da besta/tridente vanilla (cano pro -Z, onde o player mira) em vez da diagonal de espada; ganhou ponto de mira vermelho na boca do cano
- **Gago saca a escopeta de verdade**: os renderers não tinham a `CrossedArmsItemLayer` (a camada que o WanderingTrader usa pra desenhar o item nas mãos cruzadas) — sem ela o heldItem nunca renderizava. Adicionada no Gago e no Traficante
- **Gago e Traficante interagíveis**: botão direito abre o cardápio (padrão `mobInteract` do WanderingTrader decifrado do bytecode). Causa raiz do "não abre": entidades de **template NBT não passam por `finalizeSpawn`** (confirmado no bytecode do StructureTemplate) — o Gago do mercado nascia com ofertas nulas; agora as ofertas são criadas sob demanda no primeiro clique
- **Letreiro do mercado de verdade**: formato de placa corrigido contra o igloo/bottom.nbt — `messages`/`filtered_messages` são TAG_List de 4 compounds `{text, color}` (não strings JSON; o texto JSON apareceria literal na placa). Letreiro "SUL / DISTRIBUIDORA / & MERCADO / ESQUINÃO" em verde escuro, glow ativo, igual à foto
- **Gago de pele bege**: a pele e o robe do villager têm a mesma paleta terrosa — o recolor por matiz pintava a cara junto com a camisa. Agora o recolor é POR REGIÃO da textura (y<16 = pele bege, y≥16 = camisa vermelha)

### CORRIGIDO CRÍTICO: crash ao abrir o inventário criativo
- `IllegalStateException: Accidentally adding the same item stack twice [Opium] to a Creative Mode Tab` — o Ópio entrou **duas vezes** na aba do mod (duplicata de uma correção anterior). Qualquer duplicata crasha a tela de inventário inteira. Removida; lista da aba revisada (sem outras duplicatas)

### NOVO: Mercado idêntico à referência (SUL DISTRIBUIDORA & MERCADO ESQUINÃO)
- Template reconstruído como **prédio de esquina de verdade** (15×5×11, 484 blocos): paredes de concreto verde, faixa superior branca, vitrines pretas com moldura, porta de abeto central, colunas de tronco de abeto, alpendre coberto na frente, piso interno de quartz polido e calçada de smooth stone
- **Placa de parede** na fachada com o nome em 4 linhas: `SUL / DISTRIBUIDORA / & MERCADO / ESQUINÃO` (voltada pra rua, texto NBT embutido na paleta do template)
- **Gago embutido no template** (entidade no NBT, nasce atrás do balcão junto com a estrutura — não depende mais só da descoberta por código)
- **NBT reescrito no formato real do 26.3** (decifrado direto dos templates vanilla): `size` = lista de 3 ints, `blocks` = lista de entradas `{pos, state}` (não o array legacy de índices), paleta com chave `id`. O formato antigo que o gerador escrevia **nunca geraria** no mundo
- Offsets do Gago (balcão **e** porta) giram junto com a rotação do jigsaw e persistem no NBT do save

### NOVO: Validador de worldgen nível 2
- Parser NBT completo (13 tipos) byte a byte, com **auto-teste contra o igloo vanilla** — se o parser estiver quebrado, o validador se auto-rejeita em vez de passar falso-positivo (bug real encontrado: `d[self.st()] = self.val(tt)` avalia o payload ANTES do nome em Python e dessincroniza o stream)
- Valida formato 26.3 do template: size/placa/entidades/paleta/pos, textos da placa (front_text/messages), entidade embutida com id

### NOVO: Validador automático de worldgen (`tools/validate_worldgen.py`)
- Roda em **todo `gradlew build`** (task `validateWorldgen`) e **falha o build** se achar referência órfã — nunca mais mundo travado em "Preparing for world creation"
- Valida contra o jar vanilla do cache do Loom (fonte da verdade, sobe de versão junto):
  - start_pool → template_pool, placed_feature → feature, structure_set → structure, processors
  - feature type / placement type / element_type (coletados dos JSONs vanilla da versão)
  - blocos e blockstates (`id[prop=valor]`) contra os blockstates do jar (variants + multipart)
  - tags de bloco e de bioma
  - NBT da estrutura: parse byte a byte, DataVersion cruzada com `version.json`, paleta, x*y*z, índices
- **Pegou um bug grave no dia 1**: o `blocks.data` do NBT do mercado era placeholder sequencial (o grid real nunca tinha sido encodado!) — corrigido no `gen_mercado.py`, agora codifica o layout ROWS[y][z][x] de verdade
- Teste de regressão: bug `minecraft:none` reinjetado → detectado com exit 1; restaurado → exit 0

### CORRIGIDO CRÍTICO: trava na criação de mundo
- O template_pool do mercado referenciava `"processors": "minecraft:none"` — esse valor **não existe** no 26.3 (a referência órfã derrubava o registry `worldgen/processor_list` inteiro com "Unbound values" e o jogo ficava preso em "Preparing for world creation"). Agora usa processors **inline** (`{"processors": []}`), igual aos pools vanilla, e `element_type: legacy_single_pool_element`

### CORRIGIDO: Assets quebrados (achados nos logs do jogo)
- **Escopeta invisível na mão**: o gerador escrevia UV em pixels da textura (0–32) mas model usa UV NORMALIZADO 0–16 — metade dos elementos fora do range, o jogo recusava o bake (`Cannot compute translucency out of bounds`). Corrigido na fonte (`gen_escopeta.py` divide por 2) e o preview agora lê UV normalizado também
- **R$ era um PNG truncado** (122 bytes, gerador da v1.1.0 morreu no meio): sprite nova de cédula verde (`tools/gen_real.py`)
- **Lâmpada UV rejeitada no block atlas**: o model de bloco usava textura da pasta `item/` (atlas errado no 26.3) — cópia criada em `textures/block/`

### POLIMENTO: o que já existia ficou redondo
- **Rotação do jigsaw respeitada**: a estrutura nasce girada aleatoriamente e a "porta" (onde o Gago atende de madrugada) agora acompanha a rotação real lida do piece — em 3 de 4 rotações ele ficaria de frente pro muro
- **Offset da porta persiste** no NBT (não se perde no reboot)
- **Traficante entrou na economia**: aceitava só esmeralda (ignorava o R$ inteiro) — agora cobra em R$ 10~20 por item, mais barato que o Gago (a rua é a rua), com estoque limitado; e passou a vender o Ópio
- **Gago se apresenta**: a frase de chegada existia mas ninguém chamava — agora ele fala pro freguês mais próximo ao nascer no posto
- **Escopeta fala**: click seco agora avisa na tela ("Click! Sem cartuchos...") e o tooltip ensina o reparo ("Repara com 2 Cartuchos na bigorna")
- **Escopeta reparável**: componente REPAIRABLE com cartuchos (2 cartuchos na bigorna restauram a durabilidade)
- **Versão do jar sincronizada**: `intoxicantes-1.1.1.jar` (era 1.0.0)

### CORRIGIDO: Sistema do Mercado (era o buraco da v1.1.0)
- **marketPos era estático e se perdia ao reiniciar** — agora salva em NBT (`intoxicantes_market.dat` no diretório do mundo) e carrega no boot
- **Auto-descoberta da estrutura**: se o admin nunca setou, o servidor acha a estrutura `mercado_gago` sozinho (espiral de chunks estilo /locate, raio 24 chunks do spawn, 1 tentativa a cada 30s)
- **A estrutura agora EXISTE de verdade**: a v1.1.0 prometia o NBT mas ele nunca foi criado. Template 11×4×9 gerado por código (DataVersion 5023), piso de andesite polido, balcão de slab com prateleira e canto de quartz, vitrine, lanterna, barril e fundação — o jigsaw rotaciona na geração
- Worldgen completo: `template_pool` + `structure` (jigsaw em superfície, terrain_adaptation beard_thin) + `structure_set` (spawn raro estilo igloo: spacing 40 / separation 22)
- **Gago do mercado tinha NoAI eterno** (estátua que nem reagia): agora tem IA de posto fixo — parado no balcão/porta, IA liberada quando fica puto (persegue, atira, volta pro posto quando a raiva passa)
- Gerenciador do Gago agora roda 1× a cada 5s (era 20×/s, spam de teleport)
- **Relógio do HUD errado**: usava `gameTime` (não avança com /time set). Agora usa o clock do overworld (API nova de clocks do 26.3)
- Âncora do Gago no PISO da estrutura (minY), não no meio do telhado

### NOVO: Economia fechada (item ↔ saldo)
- `/depositar` — deposita todo R$ do inventário no saldo virtual
- `/sacar <quantia>` — saca do saldo em R$ de inventário (o que não couber cai no chão)
- `/gagomarket set` agora salva na hora (não perde se o servidor cair)

### CORRIGIDO: Itens
- **ÓPIO era item invisível** (dropava da papoula sem sprite/model): sprite 16×16 de seiva dourada + model + receita (2 papoulas → 1 ópio)
- en_us.json: `itemGroup` ainda em português (regressão da v1.0.1)

### MELHORADO: Escopeta
- Durabilidade pela API vanilla (`hurtAndBreak`): som e lógica de quebra idênticos aos itens do jogo

### ARQUIVOS NOVOS
- `tools/gen_mercado.py` — gera todos os JSONs de worldgen + o template NBT
- `tools/gen_opio.py` — sprite do ópio
- `worldgen/template_pool/mercado_gago/start.json`, `worldgen/structure/mercado_gago.json`, `worldgen/structure_set/mercado_gago.json`
- `structure/mercado_gago.nbt` — **NÃO precisa mais criar em creative!**
- `texture/item/opio.png`, `models/item/opio.json`, `items/opio.json`, `recipe/opio.json`

### ARQUIVOS MODIFICADOS
- `MarketSystem.java` — reescrito: persistência NBT, descoberta de estrutura, throttle, clock correto, API 26.3
- `GagoEntity.java` — `setupPostoMercado()` (IA travada/solta), expulsão via getter
- `MoneyCommands.java` — `/depositar`, `/sacar`, save imediato no `/gagomarket set`
- `IntoxicantesMod.java` — `MarketSystem.load/save` no ciclo de vida, ópio na aba
- `EscopetaItem.java` — `hurtAndBreak`
- `en_us.json` — itemGroup em inglês

### NOTAS
- Mundo NOVO: a estrutura nasce perto do spawn (raio ~24 chunks) e o Gago aparece sozinho no balcão
- Mundo JÁ EXISTENTE: a estrutura só nasce em chunks nunca gerados — nesse caso use `/gagomarket set <x> <y> <z>` (ou ache um chunk inexplorado longe)
- O NBT do template foi validado byte a byte contra a spec de NBT (parser independente)

---

## v1.1.0 — MERCADO ESQUINAO DO GAGO (18/09/2026)

### NOVO: Sistema de Dinheiro R$
- Item `intoxicantes:real` — moeda do mercado, stackavel em 64
- Comandos: `/saldo`, `/pagar <player> <quantia>`, `/darreal <player> <quantia>` (op)
- Recipes: 1 Esmeralda = 1 R$, 9 R$ = 1 Bloco de Esmeralda
- Drops: Zumbis (15%) e Piglins (25%) dropam R$
- Gago dropa 5-20 R$ ao morrer

### NOVO: Sistema 24 Horas
- Gago atende o dia todo, nunca some
- De 07:00~00:00 fica no balcão (área principal)
- De 00:00~07:00 vai pra porta do mercado
- Anuncia quando muda de posição ("Vou fechar o balcão, vou atender na porta!")

### NOVO: Relogio no HUD
- Action bar mostra `14:30 | Mercado Esquinao no balcao` quando perto do mercado
- Atualiza a cada 10 segundos

### NOVO: Gago no Mercado
- Gago fica parado no balcao durante horario
- Cardapio com 12 itens todos em R$ (R$15 a R$100)
- Se chamar de "gago" dentro do mercado, ele te expulsa com teleport + porrada
- Escopeta sempre na mao durante raiva

### NOVO: Worldgen — Mercado Esquinao
- Estrutura gera em planicies e perto de vilas
- Template NBT: `data/intoxicantes/structure/mercado_gago.nbt` (CRIAR EM CREATIVE)
- JSONs de worldgen criados (structure + structure_set)

### ARQUIVOS NOVOS
- `RealItem.java` — classe do item R$
- `PlayerMoney.java` — persistencia JSON (saldo do player)
- `MoneyCommands.java` — registrador de comandos
- `MarketSystem.java` — horario + relogio + spawn do Gago
- `real.png` — textura placeholder do R$
- `items/real.json`, `models/item/real.json` — model do item
- `recipe/real_to_emerald.json` — 9 R$ = 1 bloco de esmeralda
- `recipe/emerald_to_real.json` — 1 bloco = 9 R$
- `recipe/emerald_to_real_single.json` — 1 esmeralda = 1 R$
- `recipe/real_to_emerald_single.json` — 1 R$ = 1 esmeralda
- `worldgen/structure/mercado_gago.json` — definicao da estrutura
- `worldgen/structure_set/mercado_gago.json` — placamento

### ARQUIVOS MODIFICADOS
- `IntoxicantesMod.java` — registrado REAL, comandos, market system, removido spawn aleatorio do Gago
- `GagoEntity.java` — cardapio em R$, falas de mercado, expulsao, drops de R$
- `pt_br.json` — traducoes novas (R$, fechando)
- `en_us.json` — traducoes novas (R$, closing)

### NOTAS
- O Traficante continua spawnando aleatoriamente
- Para criar a estrutura: constroa em creative, exporte com structure block como `mercado_gago.nbt`
- Coloque o arquivo em `data/intoxicantes/structure/mercado_gago.nbt`
- PlayerMoney usa JSON persistente (compativel com todas as versoes)

---

## v1.0.1 — CORRECOES DE BUGS

1. Crash Creative Tab duplicado (semente_maconha)
2. Gago nao despawnava (lag)
3. Traducoes en_us.json em portugues
4. Worldgen plantas inuteis (age=4 -> age=3)
5. Performance isUvLit (raio 3->2, sol primeiro)
6. CANA_DE_ACUCAR sem receita
7. Gago nao equipava escopeta ao ficar puto

---

**Creditos:** OpenCode (analise e implementacao)
**Autor do mod:** enzop
