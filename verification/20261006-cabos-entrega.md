# Quadro legível e cabos suspensos — 06/10/2026

## Resultado

- Layout aprovado aplicado ao quadro: 640×388, quatro módulos separados, nomes legíveis, texto sem sombra/formatação herdada, medidores e rodapé dentro da moldura. Clique nas alavancas usa a mesma transformação do desenho.
- Conector elétrico passivo com seleção/colisão nas seis orientações; instalado nas faces dos componentes elétricos compatíveis.
- Cinco bobinas: 1,5 / 2,5 / 4 / 6 / 10 mm². Primeiro clique fixa origem, segundo conecta; uma bobina por trecho. Limite: **32 passos de bloco (distância Manhattan)**, na mesma dimensão. Não é uma cópia integral do Immersive Engineering: usa o mesmo fluxo de ligação ponto a ponto.
- Cabos suspensos com curva pendente, persistidos em SavedData por dimensão e sincronizados ao cliente. Os cabos de bloco antigos continuam disponíveis.
- Shift+clique no conector com qualquer bobina corta um dos trechos ligados e devolve exatamente a bobina da bitola cortada, inclusive com pilha igual na mão. Criativo não consome nem duplica bobinas. Quebrar o terminal corta os trechos e solta as bobinas.
- Origem removida, rotacionada, descarregada ou em outra dimensão não cria elo nem consome cobre; completar conexão limpa o componente de origem da pilha restante.
- A rede do quadro atravessa os elos nos dois sentidos, limita corrente por trecho, protege apenas o circuito correspondente e detecta união indevida de saídas. Gerador SNC Energies ligado à ponta remota pode alimentar o quadro por reflexão.
- Receitas, definições de itens, modelos voxel, texturas 128×128, traduções e Guia PT/EN completos.

## Verificação final

| Verificação | Resultado | Evidência |
|---|---|---|
| Build final Java 25 com SNC Energies e suíte completa | **PASSOU**, 189/189 testes; encerrou normalmente | [build final](20261006-build-final-com-energies.log) |
| Build final Java 25 sem SNC Energies e suíte completa | **PASSOU**, 189/189 testes; encerrou normalmente | [build standalone](20261006-build-final.log) |
| Build Java 25 com suíte elétrica selecionada | **PASSOU**, 38/38 testes; encerrou normalmente | [build elétrico](20261006-cabos-build.log) |
| Eletricidade sem SNC Energies | **PASSOU**, 38/38; encerrou normalmente | [standalone](20261006-cabos-standalone.log) |
| Cliente real após últimas correções | **PASSOU**; conexão, alimentação, corte, devolução, sync; quadro nas escalas GUI 2/3/4 | [cliente](20261006-cabos-cliente-final.log) |
| Guia/receitas/traduções | **PASSOU**, 7/7 | [Guia](20261006-cabos-guia.log) |
| Worldgen | **PASSOU**, 186 JSONs + NBT, DataVersion 5023 | [build](20261006-cabos-build.log) |
| Suíte geral anterior | **FALHOU**, 186/189; falhas agora corrigidas e reexecutadas | [execução anterior](20261006-cabos-suite-final.log) |
| Artefato | Classes, modelos e receitas presentes; sem classes `com/snc/` embutidas | [manifesto](20261006-cabos-artefato.json) |

Os dois builds finais executaram a suíte inteira, sem filtro: 189/189 passaram com e sem SNC Energies. As três falhas anteriores foram corrigidas: o teste de limite de saldo usava reflexão para um campo que não existe mais e agora usa o ledger real; duas compras falhavam quando o Windows negava brevemente a troca atômica do arquivo e agora a persistência retenta de forma limitada, mantendo a operação atômica e falhando visivelmente após esgotar as tentativas. Nenhum teste foi removido ou enfraquecido.

Capturas **reais** do cliente, 1280×800, conferidas no navegador: [galeria](cabos/capturas.html), [quadro](cabos/quadro.png), [cabo conectado](cabos/cabo-suspenso.png), [cabo recolhido](cabos/cabo-recolhido.png).

## Falhas corrigidas no fluxo

- Recolhimento perdia ou devolvia a bitola da ferramenta em vez da bitola do elo; corrigido no item e coberto por interação real.
- Pilha restante mantinha origem antiga; agora limpa após concluir/cortar.
- Sincronização periódica e inicialização de render faltavam no estado retomado; restauradas, com isolamento de dimensão e limpeza ao desconectar.
- Visitação global de um elo impedia travessia por outro circuito; agora a visita é controlada pelos limites/estado do nó por circuito.
- Recarga consultava o cache durante o próprio carregamento do chunk, inclusive via `setChanged` da tomada; usa diretamente o chunk recebido. Desligamento final não tenta carregar chunks na parada do servidor.
- Teste de recarga assumia que três posições consecutivas estavam no mesmo chunk; agora dispara evento para todos os chunks usados, mantendo todas as asserções.
- Testes de sobrevivência usavam mock criativo; modo explicitamente ajustado, sem mudar as expectativas de conservação.
- Gancho mínimo em `MarketSystem`: não buscar mercado natural quando geração de estruturas está desligada, que bloqueava o mundo superplano de teste.
- Bloqueio independente do cliente: nomes duplicados dos 341 volumes da moto do Peru. Corrigido na fonte `tools/gen_peru.py`, exportando somente o modelo; verificação confirmou que geometria, materiais, grupos e articulações não mudaram. Validação do renderer não foi removida.

## Limitações e trabalho preservado

- As falhas anteriores da suíte geral foram corrigidas e os 189 testes agora passam nos builds com e sem SNC Energies.
- Cabos não verificam obstrução/collision do vão; podem atravessar blocos. Não há choque, perdas por comprimento ou física dinâmica. A bitola protege os caminhos de cargas; a entrada remota não introduz um modelo novo de perdas/proteção de alimentador.
- A persistência dos elos foi testada por round-trip do codec SavedData e índices. Não houve teste manual em mundo pessoal nem ensaio multiplayer com dois clientes.
- Avisos ambientais de Java/Fabric, Realms, anisotropic filtering, mipmap de poste e loot do coqueiro continuam nos logs; não foram mascarados.
- Nenhum mundo pessoal, configuração global do Java ou mod instalado foi alterado. Sem commit, push ou deploy. A instância ainda usa o JAR 1.2.79; o novo JAR está em `build/libs/snc-adventures-1.2.81.jar`.

## Uso

1. Instale um **Conector de cabo** em cada ponta da instalação.
2. Com a bobina da bitola desejada, clique no conector A e depois no B.
3. Para cortar/recolher, agache e clique num dos conectores com uma bobina.
4. O quadro e o multímetro continuam sendo os instrumentos da mesma rede elétrica.
