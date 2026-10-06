# Intoxicantes Mod

Mod de Fabric para Minecraft que adiciona plantações, bebidas e um mercadinho
com NPCs pra vender tudo — incluindo cultivo indoor com a **Lâmpada UV**.

## Visão geral

- **Culturas**: maconha, lúpulo, uva, café e papoula — crescem em 5 estágios
  (`age 0..4`) e depois **amadurecem** em 4 níveis de UV (`uv_age 0..3`).
- **Lâmpada UV** (`intoxicantes:lampada_uv`): bloco de luz 15 que acelera o
  crescimento (×2) e amadurece as plantações indoor. Colher no ponto máximo
  (`age=4` + `uv_age=3`) rende o produto de melhor qualidade.
- **Mercadinho**: NPC vendedor com caixa registradora, estantes e comércio
  de produtos.
- **Config**: `config/intoxicantes.json` (escopeta, cotação da rua, UV,
  embriaguez).

## Parreira de uva

Plante a semente de uva na terra ou terra arada, ao lado de uma cerca. Empilhe
até três cercas para a planta subir e conecte cercas no topo para formar uma
cobertura de até 5 × 5. A parreira acompanha apenas a armação ligada à raiz;
ela preserva as cercas originais e deixa os cachos pendurados sob a cobertura.

Use a mão principal vazia ou segurando uvas diretamente em cada cacho, ou na
cerca exata que o sustenta na cobertura. **Um cacho visível = uma uva**: dez
cachos são dez colheitas separadas. Colher um não remove os demais nem as
folhas. Cada cacho tem maturação própria e volta após pelo menos 60 segundos
com luz e apoio; UV amadurece o cacho sem multiplicar sua quantidade.

A rebrota e a maturação de cada posição são salvas. Sem luz ou conexão, a
recuperação pausa. Novos apoios exigem cultivo, e remover/recolocar cercas não
adianta a produção. Quebrar a raiz devolve apenas os frutos restantes.
Mundos antigos preservam a idade, o UV e o intervalo já iniciado.

O Guia do SNC Adventures inclui a montagem, colheita, rebrota e maturação UV.

## Saquinhos de sementes SNC

Os seis cultivos usam saquinhos 3D nativos, com tecido cru, pregas, costuras,
cordão, selo SNC e ilustração própria de cada planta. Modelos, atlas 128×128
em diretório próprio e perspectivas vêm de `tools/gen_sementes.py`.
Os IDs e o comportamento de plantar permanecem os mesmos.

Disponíveis na versão **1.2.68**, junto com os cachos individuais da parreira.
A prévia aprovada está em `preview/sementes.html`; a demonstração da colheita
fica em `preview/parreira.html`. A verificação e as capturas reais do Minecraft
acompanham a entrega em `dist/VERIFICACAO.md` e `dist/evidencias/sementes-cachos/`.

## Estrutura

| Pasta | Conteúdo |
|---|---|
| `src/` | Código Java (Fabric), assets e data do mod |
| `tools/` | Scripts Python que geram texturas, modelos e data-driven JSON |
| `backups/` | Cópias de segurança antigas do código |
| `dist/` | `.jar` prontos pra instalar no `.minecraft/mods` |
| `run/` | Ambiente de execução de desenvolvimento |

## Build

```bash
./gradlew build
```

O `.jar` sai em `build/libs/`.

Com Java 25 no ambiente, `gradlew.bat build` também executa os GameTests de
servidor. Para conferir plantio, colheita, renderização e Guia em um mundo
temporário do cliente: `gradlew.bat runClientGameTest -PsncClientTest=ParreiraClientTest`.
Sem `sncClientTest`, todos os ClientGameTests registrados continuam ativos.

## Documentação

- `AGENTS.md` — guia de arquitetura e convenções do projeto
- `CHANGELOG.md` — histórico de versões
- `LEIA-ME-COMERCIO.md` — como funciona o comércio de NPCs
