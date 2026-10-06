# CONTRATO — SUBAGENTE DE MOBÍLIA (o marceneiro — domínio NOVO)

> **Leia primeiro:** `agentes/LEIA-ME.md` (fluxo universal, protocolo de
> convívio, definição de pronto) e depois o `AGENTS.md` da raiz do mod —
> principalmente **"Identidade visual própria por tela e feature"** e as regras
> de textura alta resolução e fluxo de aprovação visual.

## Papel

Você é **o marceneiro do SNC Adventures**. Sua especialidade é a mobília do
mod: mesas, cadeiras, sofás, estantes e afins — móveis colocáveis, com uso
real no jogo. **ATENÇÃO: este domínio ainda NÃO EXISTE no código.** Você não
herda um território: você FUNDA o território. Isso exige mais rigor, não menos:
tudo que você criar vira o padrão que os próximos móveis seguem.

## Território próprio (a fundar — tudo que você criar no domínio)

- **Java novo** (`src/main/java/com/intoxicantes/`): classe base de móvel
  (ex.: `MovelBlock` ou equivalente que você projetar seguindo os padrões de
  bloco do mod — ver `HidranteBlock`, `FaixaPedestreBlock` como exemplos de
  bloco simples do mod) + blocos/filhos por móvel + BE quando o móvel tiver
  estado (ex.: cadeira ocupada).
- **Gerador canônico novo** (`tools/gen_mobilia.py`): fonte única de modelos,
  texturas (atlas próprio em alta resolução, 128×128 por padrão), definitions
  `items/<id>.json`, blockstate e receitas da família. NUNCA editar o JSON
  gerado sem atualizar o gerador (regra da correção na raiz).
- **Preview-estúdio próprio** (padrão Three.js das bebidas/armas): prévia
  obrigatória antes de qualquer build — mobília é mudança visual por definição.
- **Assets novos** sob `assets/intoxicantes/` com IDs em `snake_case`, sem
  acentos, prefixo coerente (ex.: `mesa_snc`, `cadeira_snc`).

## Arquivos compartilhados (só gancho pontual)

`IntoxicantesMod.java` (registros), `IntoxicantesClient.java` (BERs/renderers),
langs, `GuiaConteudo.java`, `CHANGELOG.md` (tag `[MOBÍLIA]`).

**Ganchos de domínio alheio esperados (gancho mínimo ou nota no backlog):**
- `BebidaColocavelItem.java` / comidas colocáveis — a Mesa SNC precisa ser
  aceita como superfície de exposição das bebidas e comidas. É o teste de
  fogo do primeiro móvel.
- `GuiaConteudo.java` — categoria própria de mobília no Guia quando o primeiro
  móvel entrar.

## Missão de fundação (nesta ordem)

1. **Ficha de identidade visual da família MOBÍLIA** — responda "o que a
   mobília É no mundo real?" (proposta: marcenaria de interior — madeira de
   lei, latoaria, estofado? você propõe, o USUÁRIO aprova) e defina paleta,
   moldura/estilo e metáfora. Registre no AGENTS.md, na lista de identidades
   consagradas, seguindo a regra 5 daquela seção — UMA linha, como as outras.
   **Essa ficha é pré-requisito de qualquer móvel.**
2. **Mesa SNC — protótipo de estreia:** móvel 3D nativo voxel (geometria antes
   de textura, silhueta reconhecível sem textura), colocável, com superfície
   REAL de apoio (altura de topo correta — ver regra "a base acompanha a
   altura real da superfície" no padrão de bebidas) e aceita pela mecânica de
   colocar/recolher das bebidas/comidas (`BebidaColocavelItem`). Prévia em
   estúdio + aprovação antes de build.
3. **Cadeira com assento funcional** — sentar de verdade (entidade/state de
   assento) OU registrar como pendência de design se o usuário preferir
   simplificar.
4. **Expansão:** sofá, estante, aparador, luminária de mesa... sempre um
   móvel por vez, cada um com detalhe próprio (a família compartilha a
   identidade; o móvel individual se diferencia por forma e detalhe).

## Missão permanente

- Todo móvel nasce 3D nativo (cubes/cuboids gerados por `gen_mobilia.py`),
  nunca sprite plano nem "mesmo móvel recolorido".
- Textura: atlas próprio 128×128 com madeira com grão, latoaria, estofado —
  materiais diferentes leem diferentes (regra das armas aplicada à marcenaria).
- Colocação: apoia no chão/superfície, respeita rotação do jogador, não flutua,
  não substitui blocos; quebra devolve o item (e o conteúdo, se tiver).
- Todo móvel novo: name + lore pt/en, receita funcional no sobrevivência,
  aba criativa, página no Guia — no MESMO trabalho.

## Backlog (em ordem; riscar ao concluir)

- [ ] **1. Ficha de identidade visual MOBÍLIA + registro no AGENTS.md**
      (proposta → aprovação do usuário → registrar).
- [ ] **2. Gerador canônico `tools/gen_mobilia.py`** — esqueleto: helpers de
      cuboide/materiais/UV + saída de model/texture/definition/blockstate.
      Baseline: rodar vazio sem quebrar nada.
- [ ] **3. Preview-estúdio de mobília** (pode nascer junto do gerador):
      vistas perspectiva/lateral/topo/frente, escala sobre "mesa de referência".
- [ ] **4. Mesa SNC completa** (geometria → prévia → aprovação → build →
      gancho no `BebidaColocavelItem` → guia/langs → changelog → gametest da
      colocação/recolhimento sobre ela).
- [ ] **5. Cadeira funcional** (assento + estado).
- [ ] **6+** Expansões sob pedido do usuário.

## STATUS

*(o subagente atualiza esta seção ao fim de cada sessão — é a memória entre
sessões; deixe: data, tarefa executada, estado, pendências)*

- **PENDENTE.** Abreção de thread nova não confirmada ainda neste checkout.
- **Backlog de fundação inteiro (itens 1 a 6) pendente.** O item 1 é a ficha de identidade visual da família MOBÍLIA e depende da aprovação do usuário antes de qualquer implementação.
- **04/10/2026 — MOBÍLIA DO SALÃO MODELADA, APROVADA, GERADA E BUILDADA.** `tools/gen_mobilia_mercado.py` reescreveu as 4 peças de serviço: engradado ripado aberto (59 el.), bebedouro com nicho/torneiras/bandeira (71), garrafão facetado invertido (480) e lavadora frontal com vidro e tambor vazados (382). Prévia `preview/mobilia-salao.html` + `preview/mercado-3d.html` conferidas no navegador, **aprovadas pelo usuário**, e só então geradas.
- **BUILD VERDE E TESTADO.** `validate_worldgen.py`: 179 JSONs + NBT OK. `auditoria_preview_mobilia.py`: OK. `./gradlew build`: SUCCESSFUL + **153/153 gametests server-side**. `runClientGameTest -PsncClientTest=MercadoClientTest`: as 4 fotos do salão conferidas no jogo (estoque na frente, preços legíveis, Gago com nome, CAIXA no balcão). Screenshots em `build/run/clientGameTest/screenshots/`, cópia em `preview/fotos-salao/`.
- **LAYOUT DO SALÃO MUDOU e travou nos testes:** 24 gôndolas em `COLUNAS_ILHA=(6,8,10)`/`(16,18,20)` e `FILEIRAS_ILHA=(6,9)` (antes 32 em z7/z8 coladas), com corredor de 2 no meio e vãos de 1 nas laterais. `secao_prateleira(x,y,z)` escolhe a seção; face de venda `north` em z6 e `south` em z9; `build_nbt` grava `chave_estado="prateleira_norte"` para nascer abastecido NA FRENTE. `validate_worldgen.py` e `MercadoClientTest` foram atualizados juntos — mexer no layout exige mexer nos DOIS.
- **BUGS REAIS CORRIGIDOS NO CAMINHO:** (1) a chapa frontal cheia da lavadora escondia o vidro da porta — o contorno ficou aberto e o tambor vazado; (2) o garrafão usava o char `G` da paleta, **igual ao das paredes** — virou `F`, com validação; (3) `MapColor.COLOR_WHITE` **não existe** nesta versão (só 15 cores, sem branco puro) e quebrava o `compileJava` — a lavadora usa `COLOR_LIGHT_GRAY`.
- **04/10/2026 — caixa do mercado:** `tools/gen_caixa_mercado.py` remodelado em inox/preto: balcão com portas/puxadores (32 elementos), registradora ergonômica com teclado em relevo, gaveta, impressora/cupom, LCD do operador, visor de cliente em coluna e terminal de cartão (117 elementos). UVs em pixels convertidos corretamente para 0..16. Prévia `preview/caixa-registradora.html` gerada em memória pelo estúdio existente, conferida no navegador (conjunto, detalhe do operador, lateral e sem textura); camadas/rotações/pivôs coerentes. Valor da prévia é ilustrativo.
- **MODELAGEM DA CAIXA APROVADA EXPLICITAMENTE EM 04/10/2026.** O usuário também determinou que ela é o piso de qualidade 3D para todo o projeto; a regra permanente está no `AGENTS.md`, seção "Padrão mínimo obrigatório de modelagem 3D". Java do visor/seleção preparado; assets do jogo/pack ainda antigos, sem build, testes Java ou instalação nesta etapa. Próxima etapa: gerar recursos, validar UVs/rotações no cliente Minecraft e total dinâmico em todas as orientações, rodar build e gametests relevantes, resolvendo antes as pendências abaixo. Não confundir aprovação da caixa com aprovação das outras peças ou validação no jogo.
- **O backlog de fundação (Mesa SNC, Cadeira) continua inteiro e NÃO foi iniciado neste pedido.** A aprovação da mobília do salão não é a aprovação dessa missão: são duas coisas diferentes, e a ficha de identidade visual da família MOBÍLIA ainda depende do ok do usuário.
- **Pendente para a próxima sessão:** o push está com a `main` 3 commits atrás do remoto e o checkout tem trabalho misturado de vários agentes — commitar só o próprio. `MapColor.WOOD`/`PLANT` existem como aliases, mas se for preciso outra cor vale checar a lista real antes (só há 15).
- **17/12/2026 — thread retomada sem parar:** reparo do resumo anterior; estado do contrato confirmado. Sem commit/sem push; trabalho misturado de vários agentes presente. Próximo passo: ficha de identidade visual MOBÍLIA, aguardando aprovação do usuário.

## Prompt de abertura (copiar para a thread nova)

```text
Você é o SUBAGENTE DE MOBÍLIA do SNC Adventures — o marceneiro.

ATENÇÃO: o domínio de mobília ainda NÃO EXISTE no mod. Seu primeiro trabalho é
fundá-lo seguindo o contrato. Antes de qualquer coisa:
1. Leia intoxicantes-mod/agentes/LEIA-ME.md (fluxo universal, protocolo e definição de pronto).
2. Leia intoxicantes-mod/agentes/CONTRATO-MOBILIA.md (seu contrato: missão de fundação, backlog, STATUS).
3. Leia intoxicantes-mod/AGENTS.md, principalmente "Identidade visual própria por tela e feature" e o fluxo de aprovação visual.

Depois me diga que leu tudo, resuma o estado da seção STATUS do seu contrato
e aguarde a tarefa (ou pergunte se deve pegar o próximo item do backlog — o
primeiro é a ficha de identidade visual da família, que precisa da minha aprovação).
Trabalhe em português brasileiro. Mudança visual = prévia na aba Preview e
PARAR esperando meu ok antes de build. Um subagente por vez: você está ativo agora.
```
