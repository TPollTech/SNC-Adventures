# CONTRATO — SUBAGENTE DE MÁQUINAS (o operador da oficina)

> **Leia primeiro:** `agentes/LEIA-ME.md` (fluxo universal, protocolo de
> convívio, definição de pronto) e depois o `AGENTS.md` da raiz do mod —
> principalmente **"Regra do pipeline de bebidas"** (destiladas × fermentadas)
> e **"Eletricidade — integração com o SNC Energies"**. Este contrato define
> território, missão e fila; o AGENTS.md é a lei técnica.

## Papel

Você é **o operador da oficina do SNC Adventures**. Sua especialidade é a
indústria do mod: máquinas de produção de bebidas (moenda, dorna, alambique,
barril), o pipeline `ProcessosBebida` e TODO o sistema elétrico (quadro,
circuitos, cabos, disjuntores, lâmpadas, tomadas, multímetro). Nada fora
disso sem registro no STATUS.

## Território próprio (pode editar livremente)

**Java** (`src/main/java/com/intoxicantes/`):
- Base de máquinas: `MaquinaGrandeBlock.java`
- Máquinas: `MaquinaPrimaBlock.java` + `MaquinaPrimaBlockEntity.java`,
  `DornaBebidaBlock.java` + `DornaBebidaBlockEntity.java`,
  `AlambiqueBlock.java` + `AlambiqueBlockEntity.java`,
  `BarrilBebidaBlock.java` + `BarrilBebidaBlockEntity.java`
- GUI de máquina: `MenuMaquinaSNC.java`, `TelaMaquinaBase.java`
- Renderer: `MaquinasVivasRenderer.java`
- Pipeline: `ProcessosBebida.java`, `CatalogoBebidas.java`
- **Pacote `energia/` INTEIRO**: `SNCEnergiesAdapter.java`,
  `EnergiaRedes.java`, `CircuitoEletrico.java`, `BufferQuadro.java`,
  `Grandezas.java`
- Blocos elétricos: `QuadroEletricoBlock.java`, `QuadroEletricoScreen.java`,
  `QuadroEletricoNetworking.java`, `CaboEletricoBlock.java`,
  `InterruptorSimplesBlock.java`, `SoqueteTetoBlock.java`, `TomadaBlock.java`,
  `LedPotenciaBlock.java`, `LedPotenciaBlockEntity.java`,
  `LampadaUvBlock.java`, `MultimetroItem.java`

**Geradores** (`tools/`): `gen_eletrica.py`, `gen_lampada_led.py`,
`gen_lampada_led_potencia.py`

## Arquivos compartilhados (só gancho pontual)

`IntoxicantesMod.java` (registros), `IntoxicantesClient.java` (BERs/telas),
langs, `GuiaConteudo.java`, `CHANGELOG.md` (tag `[MÁQUINAS]`),
`tools/validate_worldgen.py` quando o template do mercado for afetado.

## Missão permanente

1. **Pipeline de bebidas é lei:** destilada = matéria-prima → máquina → dorna
   (fermentação) → alambique (destilação) → barril SÓ pra maturação
   (`tempoFermentacao = 0` no `ProcessosBebida.Barril`); fermentada = mosto
   direto no barril (fermentação + condicionamento nele). Destilado novo NÃO
   cria alambique novo — um registro no `ProcessosBebida` basta.
2. **Leis do SNC Energies (NÃO violar nunca):** o `SNCEnergiesAdapter` é o
   ÚNICO contato com o SNC Energies, 100% por reflexão (MethodHandles em
   cache) — NUNCA dependência de build/runtime, NUNCA import `com.snc.**`
   fora do adapter. O cabo é PASSIVO (sem ticker); a topologia é BFS por
   eventos na fila do `EnergiaRedes`. O quadro SÓ EXTRAI energia das fontes
   (nunca insere). W = E/t × 10; circuitos 127/220 V com disjuntor.
3. **Gametest de energia:** testes são síncronos — chamar
   `reconstruirTopologia()` antes de `tickRede` porque a fila do
   `EnergiaRedes` não roda dentro do teste.
4. **Identidade visual por sistema:** cada máquina tem metáfora e paleta
   próprias (AGENTS.md); não reciclar a cara de outra máquina ou tela.

## Backlog (em ordem; riscar ao concluir)

- [ ] **1. Primeira máquina elétrica de verdade** — um consumidor que pluga na
      `TomadaBlock` e consome energia REAL da rede (o plano de eletricidade
      reserva "máquinas futuras" pra tomada). Começar pelo consumo simples e
      mensurável pelo multímetro; circuito 220 V obrigatório se a carga for
      pesada. Precedente de padrão: `LedPotenciaBlock` na rede.
- [ ] **2. Sons/partículas próprios por máquina** — a oficina hoje não tem o
      vocabulário audiovisual que o mercado tem (caixa registradora, blips).
      Propor a família de sons no pedido de prévia antes de implementar.
- [ ] **3. Nova bebida destilada/fermentada** — SOMENTE sob pedido explícito
      do usuário (citar uísque/saquê no AGENTS.md não autoriza criar). Entrar
      por REGISTRO no `ProcessosBebida`, reusar alambique/barril existentes,
      garrafa vazia automática via `ProcessosBebida.garrafaVaziaDe`.
- [ ] **4. Expansão elétrica** (fase de expansão do plano): condutores e
      caixas de passagem, interruptor duplo/paralelo — só sob pedido.

## STATUS

- **2026-10-06 — Quadro legível e cabos suspensos, após aprovação das prévias:** layout 640×388 aplicado e conferido no cliente; bobinas/conectores, curva pendente, SavedData por dimensão, sync e integração à BFS/proteção. Clique real conecta/alimenta; Shift+clique recolhe e devolve bitola correta; escalas GUI 2/3/4 validadas. **38/38 testes elétricos com SNC Energies, 38/38 standalone, 7/7 Guia**, worldgen 186 JSONs/NBT; build com seleção elétrica passou. Suíte completa **186/189**, três falhas independentes de economia/prateleira (`moneyMap` removido/AccessDenied ao salvar dinheiro) preservadas. Ganchos mínimos extraordinários: `MarketSystem` evita busca natural em mundo sem estruturas; `tools/gen_peru.py` exporta nomes únicos dos volumes da moto, sem alterar desenho, para destravar reload de recursos. Relatório `verification/20261006-cabos-entrega.md`; capturas `verification/cabos/`; JAR `build/libs/snc-adventures-1.2.81.jar`, hash no manifesto. Não instalado: mods e mundos pessoais preservados. Sem commit/push; melhoria elétrica concluída, pendências gerais registradas; bastão com o agente principal.

- **2026-10-05 — Conferência rápida e build atual, por pedido explícito:** build 1.2.79 concluída em 2m18s, com 171 GameTests de servidor e 179 JSONs/NBT de worldgen aprovados. JAR e classes conferidos em `build/libs/intoxicantes-1.2.79.jar`; evidências `verification/20261005-conferencia-build.log` e `20261005-conferencia-artefato.json`. Cliente visual não repetido e instalação anterior preservada. Bastão com o agente principal.

*(o subagente atualiza esta seção ao fim de cada sessão — é a memória entre
sessões; deixe: data, tarefa executada, estado, pendências)*

- **2026-10-05 — Quadro ilegível reportado pelo usuário:** corrigida a fonte `QuadroEletricoScreen` para separar texto e mecanismos, texto sem sombra/formatação, quatro módulos 420×290, medidores/dicas separados, nomes longos com tooltip e transformação de desenho/clique consistente. Prévia `preview/quadro-gui.html` usa bitmap real do Minecraft; revisada no navegador, incluindo desarme, nome longo e edição. Teste cliente adaptado às coordenadas novas. **Aguardando aprovação visual**; sem build, GameTests ou instalação nesta etapa. JAR 1.2.79 anterior permanece ativo; validação Java/cliente e revisão em escalas de GUI após o ok. Bastão aguardando usuário.

- **2026-10-05 — Continuação e conclusão da revisão funcional elétrica:** SNC Adventures **1.2.79** compilado e instalado com backup/hash. Bulbos/refletores/campânulas com energia real e consumo exato; proteção por circuito e ramal, interruptores, tomadas, soquetes, diagnósticos, persistência e eventos de chunks revisados. Corrigidos fallback de mão vazia, tradução duplicada, luz residual e remoção do registro da BE nova pelo quadro antigo. Guia PT/EN atualizado; nenhuma remodelagem de assets/telas. **171/171 GameTests com SNC Energies e 171/171 standalone**, mais **EletricidadeClientTest** com clique real em interruptor/quadro. Relatório `verification/20261005-eletrica-entrega.md`, manifesto `verification/20261005-eletrica-artefato.json`, capturas `verification/eletrica/`. Produção preserva integração opcional. Tubo decorativo do mercado e rede industrial não convertidos; layout apertado do quadro continua pendência visual que exige prévia/aprovação. Mundos pessoais intactos; sem commit/push. Pedido funcional concluído; bastão com agente principal.

- **2026-10-05 — Aprovação visual e somente build, conforme pedido do usuário:** exportados os recursos do SNC Energies, auditoria passou e estrutura 33×9×25 validada; Java 25 compilou servidor e cliente, `BUILD SUCCESSFUL in 59s`. JAR em `../snc-energies/build/libs/snc-energies.jar`; log e hash em `../snc-energies/verification/20261005-build-aprovado.log` e `20261005-build-artifact.json`. Corrigida no gerador canônico a substituição das peças da escada, mantendo os 804 volumes aprovados. Não instalado nem testado no jogo; novas suítes após a integração continuam pendentes. Pedido desta etapa concluído; bastão com o agente principal.

- **2026-10-04 — Revisão dos veículos e mercado do projeto irmão `../snc-energies`**, por pedido explícito do usuário. Território extraordinário: colheitadeira, trator/arado, carreta, silo, comércio/estrutura e geradores canônicos Energies. Uma especialidade ativa; oficina entregou o trabalho ao agente principal, que concluiu a revisão das integrações. Nenhuma classe de produção do SNC Adventures foi alterada nesta etapa.
- **Lógica validada com os recursos anteriores:** suítes completas standalone e com o JAR instalado Adventures 1.2.75 passaram; operação prolongada sobre farmland, retomada após colisão, conservação de colheita/óleo, engates persistentes e exclusivos, persistência do silo e compra integral sem cobrança quando não cabe. Geração normal e retrogen passaram com caixas e atendentes reais após corrigir o formato NBT e a paleta da estrutura antiga 21×6×13. Evidências em `../snc-energies/verification/20261004-revisao-veiculos-standalone.log`, `20261004-revisao-veiculos-adventures.log` e `20261004-revisao-mercadao-worldgen.log`.
- **Fontes visuais preparadas após esses testes, ainda sem execução:** renderização de faces dos rigs, chão/nível da carreta, mecanismos da colheitadeira, painel de 27 células em 320×238, novo mercado 33×9×25 e balcões/caixas detalhados, colisão e orientação de produtos/atendentes. Regressões adicionais dessas mudanças escritas, aguardando aprovação. Prévia principal `../snc-energies/previews/mercadao.html`, com links para veículos e painéis. Gerador canônico da colheitadeira absorveu o conteúdo único do script paralelo antes da remoção com backup.
- **Pendências e limites:** aprovação explícita das prévias; exportação dos novos recursos, auditoria, build Java 25, repetição das suítes com os novos assets, cliente Minecraft e instalação do JAR com backup/hash. Sucesso no servidor com o desenho antigo não valida a nova modelagem nem o painel ampliado. Logs preservam as falhas corrigidas e avisos ambientais do Windows/OSHI e de ticks atrasados. Backlog permanente acima permanece intocado; bastão com o agente principal.

- **2026-10-01 — Trator e plantadeira independentes no projeto irmão `../snc-energies`**, por pedido explícito do usuário. Território extraordinário registrado: `TractorEntity`, `PlanterEntity` e `TractorFunctionalTest`; integração de menus, teclas, telas, modelos, guia e changelog coordenada pelo agente principal. Backlog permanente acima permanece intocado.
- **Implementado, ainda não validado em execução:** trator sozinho sem plantio embutido; plantadeira independente com carga própria; engate exclusivo atrás e parado, posição inicial erguida, desengate parado; UUID persistido nos dois veículos, resolução tardia sem forçar chunks, liberação na destruição; sementes legadas preservadas e transferidas conservativamente por fileira; pés de plantio alinhados ao modelo; bloqueios de ré/parado/erguido/sem combustível; óleo vegetal em porções completas de 400 ticks, preservado no criativo/tanque cheio; painel por Shift+clique; movimento do servidor com gravidade, colisão e interpolação de cliente, rodas sincronizadas.
- **Revisão do fluxo:** corrigido também o risco de UUID órfão quando a contraparte é destruída enquanto seu chunk está descarregado: última posição persistida, reserva mantida sem carregar chunks e expiração somente depois de 100 ticks com o chunk da contraparte efetivamente carregado e UUID ausente. IDs numéricos antigos de engate não são confiáveis após recarga e são ignorados; o implemento antigo continua independente e seu inventário permanece salvo.
- **Revisão estática final das integrações:** encontrados e corrigidos pelo agente principal dois erros concretos: abastecimento do menu ignorava parada/criativo/permissões e o botão Guia trocava de tela sem fechar o container, bloqueando as teclas ao voltar ao jogo. As interações diretas nas entidades agora validam dimensão, jogador vivo, espectador e operador antes de criar painel; a plantadeira recusa painel em movimento. Corrigido ainda o sinal invertido de A/D no trator, com resposta de ré; adicionadas regressões do tick real com `ServerPlayer.Input` e do abastecimento por menu movendo/parado/cheio/criativo. Mapeamento das fileiras e pés conferido com a transformação do renderer; dados e reservas dos menus revisados. Tudo permanece sem execução até a aprovação visual. Bastão devolvido ao agente principal; nenhum outro domínio aberto.
- **Revisão do item de colocação:** achado adicional corrigido pelo agente principal em `TractorItem`/`PlanterItem`: `useOn` procurava veículo num raio de 2 blocos e abria painel sem validar motorista/movimento, impedindo colocar outra máquina perto. Painéis ficam exclusivamente na interação direta/teclas; colocação requer face superior, suporte e volume livre, verifica também sobreposição com veículos/seres vivos (os implementos são deliberadamente não sólidos) e só consome depois de `addFreshEntity` aceitar. Regressões acrescentadas no teste existente usando o item registrado e `UseOnContext`: face lateral, corpo obstruído além da célula central, trator sozinho, duplicação por sobreposição, plantadeira dentro de trator, vizinhos próximos independentes e conservação no criativo. Nenhuma dessas regressões foi executada; seguem pendentes da aprovação visual.
- **Pendências obrigatórias:** prévia visual e aprovação explícita do usuário; somente depois gerar assets de produção, compilar com Java 25/Minecraft 26.3, executar regressões funcionais e validar no cliente real as teclas, telas, engate, combustível, plantio e recarga. Nenhum gerador, build ou teste foi executado neste estágio; apenas fontes e assinaturas da versão instalada foram inspecionadas. Não declarar a funcionalidade pronta nem o JAR atualizado antes dessas validações.

## Prompt de abertura (copiar para a thread nova)

```text
Você é o SUBAGENTE DE MÁQUINAS do SNC Adventures — o operador da oficina.

Antes de qualquer coisa:
1. Leia intoxicantes-mod/agentes/LEIA-ME.md (fluxo universal, protocolo e definição de pronto).
2. Leia intoxicantes-mod/agentes/CONTRATO-MAQUINAS.md (seu contrato: território, missão, backlog, STATUS).
3. Leia intoxicantes-mod/AGENTS.md, principalmente "Regra do pipeline de bebidas", "Eletricidade — integração com o SNC Energies" e as regras de bebidas.

Depois me diga que leu tudo, resuma o estado da seção STATUS do seu contrato
e aguarde a tarefa (ou pergunte se deve pegar o próximo item do backlog).
Trabalhe em português brasileiro. Mudança visual = prévia na aba Preview e
PARAR esperando meu ok antes de build. Um subagente por vez: você está ativo agora.
```
