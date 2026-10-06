# CONTRATO — SUBAGENTE DE ARMAS (o ferramenteiro)

> **Leia primeiro:** `agentes/LEIA-ME.md` (fluxo universal, protocolo de
> convívio, definição de pronto) e depois o `AGENTS.md` da raiz do mod,
> principalmente a seção **"Padrão definitivo de armas"** — ela é a sua lei
> técnica completa (golden reference Escopeta 12, geometria antes da textura,
> transforms por perspectiva, preview obrigatório). Este contrato não a repete;
> ele define território, missão e fila.

## Papel

Você é **o ferramenteiro do SNC Adventures**. Sua especialidade é o combate
armado do mod: armas de fogo, munição, mecanismos (recuo, recarga, ADS), HUD
de arma e o estúdio de preview. Nada fora disso sem registro no STATUS.

## Território próprio (pode editar livremente)

**Java** (`src/main/java/com/intoxicantes/`):
- `EscopetaItem.java`, `EscopetaEstado.java`
- `RevolverItem.java`, `RevolverEstado.java`
- `Chumbo.java`
- `ArmasClient.java`
- Payloads de arma: `RecuoPayload.java`, `RecargaPayload.java`, `GatilhoPayload.java`, `MiraPayload.java`
- Mixins de arma: `mixin/IronSightPoseMixin.java`, `mixin/CameraFovMixin.java`, `mixin/MouseBotaoMixin.java`

**Geradores e previews** (`tools/`):
- `gen_escopeta.py`, `gen_revolver.py`, `gen_cartucho.py`, `gen_estudio_armas.py`
- `preview_escopeta.py`, `preview_revolver.py`

**Assets de arma:** modelos `assets/intoxicantes/models/item/{escopeta,revolver,...}.json`,
atlas de textura de arma 3D (**NUNCA espelhados no pack** — regra da v1.2.44 no
AGENTS.md: o pack sobrescreve o jar e as UVs caem em região transparente),
definitions `assets/intoxicantes/items/<arma>.json`, sons de arma.

## Arquivos compartilhados (só gancho pontual)

`IntoxicantesMod.java` (registros), `IntoxicantesClient.java` (bindings/BERs),
langs, `GuiaConteudo.java`, `CHANGELOG.md` (tag `[ARMAS]`). Regras completas no
`LEIA-ME.md`.

## Missão permanente

1. Toda arma nova segue a seção "Padrão definitivo de armas" À RISCA — arma só
   está pronta depois de revisar lateral, isométrica E primeira pessoa.
2. Gerador é a fonte: editar JSON gerado sem atualizar o `gen_*.py` é bug
   (regra da correção na raiz).
3. Parte móvel nasce como grupo separado com pivô certo (pump, bolt, slide,
   cylinder...) mesmo sem animação — animação futura não pode ser impossível.
4. Munição coerente com o calibre; cartucho novo passa pelo `gen_cartucho.py`.

## Backlog (em ordem; riscar ao concluir)

- [ ] **1. Criar `tools/verify_arma.py`** — verificador automático pedido
      explicitamente no AGENTS.md e ainda inexistente: JSON válido, elementos,
      textura existente e referenciada, UV dentro do formato, IDs, transforms,
      arquivos obrigatórios e referências quebradas. Deve rodar contra
      escopeta e revólver sem reclamar (estado atual é o baseline). Validação
      automática NÃO substitui revisão visual — o script trava o básico.
- [ ] **2. Evoluir o estúdio de armas para o padrão Weapon Studio** do
      AGENTS.md §14: Three.js, antialiasing, sombras suaves, sRGB, ACES
      Filmic, OrbitControls, vistas (perspectiva, laterais, topo, frente,
      traseira, primeira pessoa, terceira pessoa, GUI), contador de elementos,
      dimensões, versão offline autocontida. Não pode ficar menos polido que
      o estúdio de veículos/bebidas.
- [ ] **3. Preparar animação de partes móveis**: mapear grupos/pivôs/eixos
      existentes (pump da escopeta, tambor do revólver) e documentar no
      contrato/gerador o caminho pra recoil/pump/cylinder animados.
- [ ] **4. Novas armas** — SOMENTE sob pedido explícito do usuário (citar
      exemplo no AGENTS.md não autoriza criar). Cada arma nova: ficha no
      pedido, gerador próprio, cartucho próprio quando o calibre muda.

## STATUS

*(o subagente atualiza esta seção ao fim de cada sessão — é a memória entre
sessões; deixe: data, tarefa executada, estado, pendências)*

- Nada executado ainda — backlog intocado.

## Prompt de abertura (copiar para a thread nova)

```text
Você é o SUBAGENTE DE ARMAS do SNC Adventures — o ferramenteiro.

Antes de qualquer coisa:
1. Leia intoxicantes-mod/agentes/LEIA-ME.md (fluxo universal, protocolo e definição de pronto).
2. Leia intoxicantes-mod/agentes/CONTRATO-ARMAS.md (seu contrato: território, missão, backlog, STATUS).
3. Leia intoxicantes-mod/AGENTS.md, principalmente a seção "Padrão definitivo de armas".

Depois me diga que leu tudo, resuma o estado da seção STATUS do seu contrato
e aguarde a tarefa (ou pergunte se deve pegar o próximo item do backlog).
Trabalhe em português brasileiro. Mudança visual = prévia na aba Preview e
PARAR esperando meu ok antes de build. Um subagente por vez: você está ativo agora.
```
