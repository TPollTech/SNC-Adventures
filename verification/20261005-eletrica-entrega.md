# SNC Adventures 1.2.79 — revisão elétrica

## Entrega

- Instalado em `../../mods/intoxicantes-1.2.79.jar` (caminho relativo a este relatório).
- JAR de build: `../build/libs/intoxicantes-1.2.79.jar`.
- SHA256: `62d1708cc6ba4a3bc91b5b60cc38cf471555b8745314a55e4ee13586b58f0073`.
- Versão anterior preservada em `../backups/20261005-eletrica-instalacao/intoxicantes-1.2.78.jar`.
- Manifesto: [20261005-eletrica-artefato.json](20261005-eletrica-artefato.json).
- Não houve commit, push, alterações de mundos pessoais ou instalação de outra versão do SNC Energies.

## Critérios atendidos

1. Bulbos 5–20 W, refletores 30–50 W e campânulas 100–200 W precisam de energia real. Clique controla a chave local; sem alimentação ficam apagados.
2. Cabo passivo, contínuo e por eventos; cinco bitolas preservam seus limites através do interruptor. Cargas são terminais, sem transformar lâmpadas em fios.
3. Consumo por circuito corresponde à energia removida: 5 W = 10 E/s, 9 W = 18 E/s, 15 W = 30 E/s. Sem fornecimento não há cobrança nem sobrecarga térmica.
4. Interruptor em série corta a jusante; caminho paralelo constitui desvio elétrico e continua alimentando, como esperado de uma ligação em paralelo.
5. Proteção de disjuntor em três medições de sobrecorrente e fio em cinco; ramal fino recebe só a carga que o atravessa. União de circuitos ou carga alimentada por dois quadros bloqueada.
6. Tomada vazia não consome, fornece energia a luminárias adjacentes e não repassa para outro cabo. Soquete instala/troca/remove bulbos com componentes preservados; inventário cheio devolve exatamente um item ao mundo.
7. Quebra do cabo/quadro e abertura da chave apagam cargas desconectadas. Eventos de carga/descarga de chunk revalidam estados; substituição da BE não perde o registro do quadro novo.
8. Dados de quadro, disjuntor, tensão, energia acumulada e lâmpada sobrevivem à serialização. Migração preserva a chave local dos LEDs antigos.
9. Multímetro segue o quadro conectado, não o mais próximo; leitura de soquete apagado não afirma energia; tradução da luminária e E/t fracionário disponíveis em PT/EN.
10. Quadro aberto não pausa o singleplayer. Clique real na tela corta/rearma o circuito; medição continua no servidor.
11. Guia PT/EN atualizado. Produção não exige SNC Energies e não contém classes de teste.

## Verificações finais

- [Build + suíte com SNC Energies](20261005-eletrica-validada-com-energies.log): **171/171**, BUILD SUCCESSFUL; verificador de worldgen aprovou 179 JSONs + NBT contra Minecraft 26.3.
- [Suíte sem SNC Energies](20261005-eletrica-validada-sem-energies.log): **171/171**, BUILD SUCCESSFUL; mundo de teste novo, ausência confirmada pelo adapter e pelo loader.
- [Cliente Minecraft](20261005-eletrica-cliente-final.log): **EletricidadeClientTest passou**, BUILD SUCCESSFUL. Gerador real, clique real no interruptor, corte/religamento do refletor, abertura e clique nas alavancas do quadro, consumo no servidor e traduções carregadas.
- [Galeria de capturas reais](eletrica/index.html): refletor alimentado, interruptor cortado e medição viva no quadro. Capturas revistas no navegador; mostram a interface existente, sem remodelagem visual nesta tarefa.
- Classes compiladas e recursos gerados conferidos byte a byte com o JAR; traduções no JAR iguais à fonte; nenhum GameTest/ClientTest no artefato de produção. Hash da cópia instalada igual ao JAR validado. Uma única versão do Adventures ativa em `mods`.

## Falhas encontradas e corrigidas

- Teste herdado falhava na remoção de bulbo: retorno PASS bloqueava o fallback de mão vazia. Corrigido para TRY_WITH_EMPTY_HAND e testado pelo ServerPlayerGameMode, não apenas chamada direta ao bloco.
- Tradução duplicada de soquete sobrescrevia o diagnóstico correto; removida em ambos os idiomas.
- Regressão adicional detectou remoção do registro do quadro novo pela BE antiga durante substituição no LevelChunk. Corrigido usando o mapa de BEs do chunk, sem criar BE durante descarregamento. Suítes finais repetidas depois do reparo.
- Tentativa inicial de filtro de GameTests não selecionou testes; descartada como evidência. Executada a suíte completa.
- Primeiro teste standalone reutilizava dados de gamerule do Energies; repetido em mundo de teste novo, preservando o anterior em backup.
- Cópia inicial de instalação encontrou o JAR antigo bloqueado pelo daemon Gradle. A cópia nova foi retirada para não deixar dois mods ativos; daemon parado, versão antiga movida para backup e nova instalação/hashes conferidos.

## Limites honestos

- Simulação inspirada na instalação real, não cálculo normativo NBR: ampacidades simplificadas, cargas LED tratadas como bivolt 127/220 V, sem neutro separado, aterramento/DR, queda de tensão, curto com aquecimento/incêndio ou curva térmica contínua.
- Medição de alimentação/consumo a cada 20 ticks; montagem/religamento pode aguardar até um segundo. Corte por mudança topológica acontece pela fila no fim do tick.
- Tubo decorativo do mercado `lampada_led`, poste e painel programável não foram convertidos para a rede. Evitado apagar o mercado inteiro sem infraestrutura elétrica própria.
- Máquinas industriais SNC Energies não são consumidores desta tomada; usam a rede do próprio mod. Tomada atualmente alimenta a família de luminárias.
- Estados persistentes e eventos de chunks foram testados em mundos isolados; não houve reabertura de um save pessoal antigo ou playtest da instância CurseForge completa.
- Tela do quadro mantém layout anterior com textos apertados/sobrepostos nas capturas. A funcionalidade foi validada; redesign visual requer prévia e aprovação separadas conforme AGENTS.md.
- Permanecem avisos ambientais Windows Perflib/OSHI, Realms e filtragem anisotrópica; não representam falhas dos testes elétricos.
