# TODO — Intoxicantes Mod (atualizado em v1.2.51)

O mercado, a economia e a fazenda estão completos e funcionais. O que sobra é lapidação:

## Próximos passos (ordem sugerida)

0d. **(FEITO na v1.2.51)** O OVERHAUL DO MERCADO: o playtest cravou três
   chagas — o display de fachada desalinhado (o template semeava SÓ o
   painel `J`; o `X` de extensão existia na paleta e nunca era usado — o
   texto "esticado por cima de parede errada"), o mini display de status
   esquecido e o Gago sumindo. Agora: (1) A FAIXA DE LED INTEIRA — o
   template semeia `X×7 + J + X×7` (15 blocos, texto de ponta a ponta), o
   renderer sem o código morto de FECHADO/esmaecimento, e o ZELADOR DA
   AUTOCURA completa a faixa em mundos 1.2.31–50 (semeia extensões em AR,
   PARA no primeiro obstáculo — parede de jogador NUNCA sobrescrita — e a
   largura do BE reflete a faixa real); (2) O MINI DISPLAY "ABERTO · 24H"
   — PainelLedBlock verde fixo ao lado da porta, editável pela Central de
   Comando; (3) A PORTA-GRADE DO GUICHÊ — bloco novo codado do zero
   (DoubleBlockHalf): embaixo vira parede de madrugada, a grade de cima
   vira guichê de atendimento (o Gago atrás dela), abre às 07:00 pelo
   relógio do jogo; (4) O GAGO ÂNCORA DUPLA — balcão de dia, guichê de
   madrugada, teleporte só na virada, mantendo NoAI + anti-sufocamento;
   (5) ZELADOR REFORMA MUNDOS VELHOS — troca a porta vanilla pela
   porta-grade, planta o mini display, completa a faixa. 3 game tests
   novos (faixa, guichê, âncora dupla) + validador de worldgen estendido
   (faixa, mini display, porta-grade). 60/60 testes passando.

0c. **(FEITO na v1.2.27)** O POSTE DE RUA DE VERDADE: o playtest pegou o
   poste "bugado" (caixas pretas desconexas) e o scanner do save cravou a
   causa — CORPO desgarrado 1 bloco fora do eixo (plantio velho) + texturas
   quase pretas. Agora: luminária EM CIMA da coluna (capitel + lente +
   tampa), aço galvanizado CINZA MÉDIO-CLARO legível contra o céu, autocura
   que REPARA peças erradas no lugar (não só preenche ar) e RECOLHE peça
   desgarrada (volta pro eixo do poste), varredura do zelador que cobre o
   pátio inteiro. E a placa virou AUTOCURÁVEL: ticker 1×/30s completa
   torres de mundo velho e adota o texto novo — o letreiro solitário do
   save vira a placa de 9 blocos sozinho ao carregar o chunk.

0. **(FEITO na v1.2.24, REFEITO na v1.2.25)** O LETREIRO DE VERDADE: a
   primeira rodada do concerto ainda entregou tracinhos miúdos num monólito
   preto (dupla contagem de largura no renderer + rotação 180° errada +
   moldura quase preta + margens transparentes virando preto sem cutout).
   Agora: placa de 9 blocos (torres a ±2, vão de 3 blocos de LED puro),
   texto MERCADO/ESQUINÃO em escala 1:1 com ciclo ABERTO/FECHADO gigante,
   frente E verso legíveis, moldura verde viva, texturas 100% opacas,
   guardas no game test (escala ≥ 0.9). Poste de 3 blocos e hidrante de
   15px da 1.2.24 seguem de pé.

0b. **(FEITO na v1.2.18 → v1.2.23)** O Esquinão com cara de rua: letreiro LED
   codado do zero, mercado com PÁTIO/ESTACIONAMENTO próprio (fundação
   `beard_box`, ar limpo, anti "loja engolida por vila"), linha
   ABERTO/FECHADO com cor e jingle na virada do horário (07h~00h), Gago
   recusando cliente fora do expediente, zumbido de LED de proximidade,
   blips de leitura e postes que acendem sozinhos 19h~5h sobre o asfalto
   demarcado. **v1.2.23 fechou a conta**: asfalto com blockstate (xadrez
   rosa morto), poste de luz de 2 BLOCOS (coluna com braços + luminária de
   sódio pendurada) e o LETREIRO 9-BLOCOS na calçada com FONTE DE LED 5×7
   própria em Java (`LedFont`) — a fonte do Minecraft saiu do letreiro pra
   sempre, e o texto triplicado morreu (block entity só no painel).

1. **(FEITO na v1.2.4 + v1.2.7 + v1.2.10)** Sons próprios: `soco_d12` (estouro da 12),
   `voz_gago` (blip de fala), `zumbido_uv` (hum da lâmpada), `caixa_registradora`,
   `balim_acerto`, `hic` (soluço do bêbado) e `glup` (o gole em si, com refluxo grave).

2. **(FEITO na v1.2.7)** Partículas custom — nota de R$ na venda, folha da planta madura,
   fumaça verde-erva do baseado contínua enquanto puxa.

3. **(FEITO na v1.2.26)** Mercado por bioma (fase 2) — o PRÉDIO agora nasce "do lugar":
   3 peles data-driven (Clássico/Sertão/Serra) no padrão das vilas vanilla, região
   persistida no save, rebuild pela pele e o Zelador reformando pátios velhos. De bônus:
   o Gago COCHILA fora do expediente (Zzz + frase de sono) e o Dono da Esquina (fidelidade
   tier máximo) tem saudação VIP própria ao ler o letreiro.

4. **(FEITO na v1.2.8)** Texturas das bebidas — as 16 refeitas no pipeline procedural
   (rampa de volume, brilho de vidro, silhueta própria por produto).

5. **(FEITO na v1.2.7)** Game tests das conquistas — fidelidade completa (raiz, tiers,
   descontos) e colheita perfeita validados por award programático.

6. **(FEITO na v1.2.9 + v1.2.10 + v1.2.13)** Embriaguez completa — dose por item, fala
   "fonar" embaralhada no chat (todo mundo lê), *hic!* audível e NA fala, persistência no
   mundo (sobrevive a relog/restart), limiares/decay/teto no config, refluxo no teto,
   cambaleio mecânico (náusea + fraqueza gradual + EMPURRÃO), glup sonoro, action bar
   GRÁFICA com rótulos traduzidos, o Gago zoeiro debochando do freguês bêbado no balcão,
   RESSACA com cabelo do cachorro e MARKUP pro freguês bêbado no preço do balcão.

## O que NÃO precisa mais fazer (já está pronto)
- v1.2.31: visual do Gago reformado — nariz pequeno (fim do lula-molusco),
  sem chapéu de palha, cabelo grisalho + óculos + bigode ralo de velhinho
  simples (modelo + as 7 skins)
- v1.2.30: ciclo do "Gago sumindo + mercado cheio de item" morto (posto
  sempre livre antes do teleporte/spawn, imunidade a sufocamento em
  serviço, morte em serviço sem loot, autocura de persistência — 36 testes)
- v1.2.29: UM hidrante na calçada ao lado da faixa (nunca em vaga), vagas
  100% livres, zelador recolhe os hidrantes velhos das vagas no mundo velho
- v1.2.28: blockstate da placa na convenção da FORNALHA (a textura de LED
  e o texto agora na MESMA face = FACING pros 4 lados — a 1.2.24~27 tinha
  metade wall-sign, metade fornalha: texto de um lado, matriz do outro)
- v1.2.27: poste com luminária EM CIMA + autocura (repara/recolhe peça
  desgarrada), placa autocurável por ticker, template semeia a placa
  COMPLETA (35 game tests)
- v1.2.24: letreiro consertado (winding + escala + faixa LED), poste de 3
  blocos completo no template, hidrante alto, config na raiz, LedFont
  blindada com teste (33 game tests)
- v1.2.19: letreiro vivo (ABERTO/FECHADO no LED com arpejo de virada),
  zumbido de LED de proximidade + blips por linha, poste de luz automático
  e estacionamento pavimentado com vagas (fila própria de notas no lugar do
  tell() que saiu no 26.3) — 27 game tests
- v1.2.17: Lâmpada UV com estado ligada/desligada por redstone (disjuntor: vizinho
  pode cortar a energia da fazenda indoor), raio no config (`uvRaio`), lâmpada como
  investimento (R$ 36, estoque 2/dia) e 25 game tests
- Estrutura do mercado: gerada por template NBT próprio (nada de Structure Block manual)
- Loot dos NPCs: Gago (cachaça, cervejas, R$, cartuchos) e Traficante (estoque)
- Reparo da escopeta: componente REPAIRABLE com cartucho (bigorna)
- v1.2.32: escopeta em nível gun mod — tubo interno + câmara como DataComponent da
  stack, reload shell-by-shell segurando o botão direito (interrompível), pump
  automático pós-tiro, recoil com kick de câmera S2C (retorno suave de ~55%), ADS
  ao segurar SHIFT (zoom de FOV via mixin em Camera.calculateFov, dispersão ½,
  alcance +25%) e HUD do mecanismo sob o crosshair; tudo calibrável no config
- v1.2.33: revólver .38 (o "três oitão") no mesmo padrão — tambor de 6 como
  bitmask no DataComponent, tiro consome a câmara alinhada e GIRA o tambor, reload
  shell-by-shell nos buracos vagos, giro no seco quando a câmara seca mas o tambor
  tem bala, fecho do ferrolho pós-tiro/recarga, kick de câmera (RecuoPayload
  compartilhado), ADS próprio (zoom 15%), cartucho_38 itemizado (chumbo+pólvora+
  2 latão) e ArmasClient unificado (kick/ADS/HUD das duas armas)
- Validador de worldgen no build: JSON/NBT quebrado NÃO sobe pro jar
- Config: config/intoxicantes.json (escopeta, cotação da rua, UV, embriaguez)
- v1.2.14: medidor de embriaguez SÓ de álcool (drogas têm efeitos próprios), todas
  as mensagens i18n (nada de pt cravado no código) e informativos no CHAT (action bar
  só pra sabor repetitivo como o *HIC!*)
- Conquistas: aba "O Esquinão" com 6 progressões
- 24 game tests de economia/estoque/escopeta/progressões/fumaça/embriaguez/withers (teto
  de saldo e config da 12 na v1.2.4; fidelidade e colheita perfeita na v1.2.7;
  escalonamento da fumaça no balcão na v1.2.8; dose/fonar/decay na v1.2.9;
  cap/refluxo/persistência/zoeira na v1.2.10; i-frames do 26.3 no chumbo na v1.2.11;
  gole interrompido/cafeína/cap de boss na v1.2.12; ressaca/cabelo do cachorro e
  markup/cambaleio na v1.2.13; medidor de álcool na v1.2.14; balance UV na v1.2.15)
