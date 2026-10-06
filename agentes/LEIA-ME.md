# Sistema de Subagentes por Domínio — SNC Adventures

Este diretório define os **contratos de domínio** do projeto. Cada contrato
transforma uma thread nova do Freebuff num **subagente especialista** num
território do mod: armas, máquinas, plantações ou mobília.

> **Fonte das regras:** este sistema é uma extensão do `AGENTS.md` da raiz do
> mod. Nada aqui sobrescreve o AGENTS.md — o contrato especializa e aponta
> trechos dele. Em conflito, vale o AGENTS.md.

---

## Como abrir um subagente (passo a passo)

1. Abra uma **thread nova** do Freebuff no mesmo projeto.
2. Cole o **prompt de abertura** que está no final do contrato do domínio
   (ex.: `CONTRATO-ARMAS.md`), sem editar.
3. Quando a thread responder que leu o contrato e o AGENTS.md, mande a tarefa:
   - do **backlog** do contrato ("execute o item 1 do backlog"), ou
   - uma tarefa sua do domínio ("cadastra no backlog e execute: ...").
4. Acompanhe. Mudança visual exige **prévia + sua aprovação** antes de build
   (regra do AGENTS.md) — o subagente vai parar e esperar o "ok".
5. Ao concluir, exija o fechamento: **STATUS do contrato atualizado**,
   **CHANGELOG.md** com a entrada tagueada, tarefa riscada no backlog.

## A regra de ouro: UM subagente ativo por vez

Escolha do usuário, registrada como lei do sistema:

- **Nunca** há dois subagentes de domínio trabalhando ao mesmo tempo neste
  checkout.
- Antes de abrir a próxima especialidade, a anterior deve ter **terminado a
  tarefa** e atualizado o STATUS do contrato. Se precisar interromper, peça a
  ela que salve o estado no STATUS antes de fechar a thread.
- O STATUS do contrato é a **memória entre sessões**: é lá que o próximo
  prompt descobre em que pé ficou o trabalho.

## Fluxo universal de trabalho (todos os contratos)

1. Ler o contrato do domínio (este diretório).
2. Ler `AGENTS.md` da raiz do mod — principalmente a seção do próprio domínio.
3. Ler a seção **STATUS** do contrato (o que ficou pendente da última sessão).
4. Definir a tarefa e conferir o **território** (arquivos próprios × compartilhados).
5. Implementar no **arquivo-fonte certo** (regra da correção na raiz: nada de
   scripts-patch, `v2`, `fix_*` — conserta no Java ou no gerador `gen_*.py`).
6. Mudança com resultado visual → **prévia na aba Preview + PARAR e esperar
   aprovação** antes de rodar geradores de produção, build ou testes.
7. Build e gametests (quando cabem):
   - `JAVA_HOME="C:/Users/enzop/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2" gradlew.bat build`
   - O `java` do PATH (JDK 21) NÃO serve pro build — Loom exige JDK 25.
   - Gametests síncronos: no pacote `energia`, chamar `reconstruirTopologia()`
     antes de `tickRede` (a fila do `EnergiaRedes` não roda dentro do teste).
8. **CHANGELOG.md** com entrada tagueada pelo domínio: `[ARMAS]`, `[MÁQUINAS]`,
   `[PLANTAÇÕES]`, `[MOBÍLIA]`.
9. Feature relevante → página no **Guia do SNC Adventures** e langs `pt_br`/`en_us`
   no MESMO trabalho (a feature não está completa sem o guia).
10. Atualizar o **STATUS** do contrato e riscar a tarefa do backlog.

## Territórios e arquivos compartilhados (resumo)

| Domínio | Papel | Contrato |
|---|---|---|
| **Armas** | o ferramenteiro | `CONTRATO-ARMAS.md` |
| **Máquinas** | o operador da oficina | `CONTRATO-MAQUINAS.md` |
| **Plantações** | o agrônomo | `CONTRATO-PLANTACOES.md` |
| **Mobília** | o marceneiro (domínio NOVO) | `CONTRATO-MOBILIA.md` |

**Arquivos compartilhados por todos** (edite SEMPRE de forma pontual, nunca
refatore código de outro domínio neles):

- `src/main/java/com/intoxicantes/IntoxicantesMod.java` — hub de registros
- `src/main/java/com/intoxicantes/IntoxicantesClient.java` — bindings/BERs de cliente
- `src/main/resources/assets/intoxicantes/lang/pt_br.json` e `en_us.json`
- `src/main/java/com/intoxicantes/GuiaConteudo.java` (Guia) e `CHANGELOG.md`
- `tools/validate_worldgen.py` — quando a mudança mexer em worldgen/template

**Cruzamento de domínios:** o agente ativo faz só o **gancho mínimo** no
território alheio (ex.: aceitar a nova mesa como superfície no
`BebidaColocavelItem`) **ou** deixa a tarefa anotada no backlog do contrato do
outro domínio — e registra a decisão no próprio STATUS. Nunca refaz trabalho
alheio, nunca renomeia ID de outro domínio.

## Protocolo de convívio (rígido)

1. **Um subagente ativo por vez** (regra de ouro acima).
2. Edições nos compartilhados são **pontuais e cirúrgicas**; releia o trecho
   antes de editar (o arquivo pode ter mudado desde a sua leitura).
3. **Changelog tagueado** por domínio em cada entrada.
4. Tarefa que cruza domínios → gancho mínimo **ou** nota no backlog alheio +
   registro no STATUS. Nada silencioso.
5. Backups: mudança ampla ou migração → cópia dos arquivos afetados em
   `backups/` com data e finalidade (regra do AGENTS.md).
6. Preserve arquivos e alterações de outras sessões — o histórico da conversa
   pode estar desatualizado; confira o estado real dos arquivos.

## Definição de pronto (todos os domínios)

Uma tarefa só está pronta quando TODOS couberem:

- [ ] Código/gerador na fonte correta, sem arquivos de remendo
- [ ] Assets completos (model, textura 128×128 quando item/bloco novo,
      `items/<id>.json` de definition, blockstate, loot, receita quando houver)
- [ ] Traduções `pt_br` + `en_us` (nome, lore, guia)
- [ ] Página do Guia atualizada (quando a feature é visível ao jogador)
- [ ] Receita/obtenção funcional no sobrevivência (quando há item novo)
- [ ] Prévia aprovada pelo usuário (quando houve mudança visual)
- [ ] Build verde no JDK 25 + gametests relevantes passando
- [ ] `CHANGELOG.md` tagueado + STATUS do contrato atualizado + backlog riscado

O que não foi verificado, deve ser dito exatamente como pendente — nunca
escondido.
