package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * Conteúdo do LETREIRO DO ESQUINÃO (v1.2.31): o NOME da loja em UMA linha de
 * LED verde esticada pela largura da fachada + a LINHA DE STATUS (ABERTO
 * verde / FECHADO vermelho, v1.2.19) que entra no ciclo. O servidor guarda
 * as linhas, a LARGURA da faixa, a VERSÃO da anatomia, o VÍNCULO com o
 * mercado e o estado aberto/fechado; o client recebe tudo pelo sync vanilla
 * de block entity (getUpdateTag com o save completo).
 *
 * O texto nasce fixo (SUL DISTRIBUIDORA & MERCADO ESQUINÃO), gravado pelo
 * template da estrutura via nbtDoTemplate(). Placa solta (colocada pelo
 * jogador) nasce MUDA e sempre "aberta" — só a placa do template do mercado
 * é gravada com mercado=1: é ela que pulsa, muda de cor no horário e toca
 * o jingle da virada.
 */
public class PlacaEsquinaoBlockEntity extends BlockEntity {

    /** Cor do LED (verde-esmeralda do letreiro, mesma família da fachada). */
    public static final int COR_LED = 0x39FF6E;
    /** Cor do brilho/faísca (mais claro). */
    public static final int COR_LED_CLARO = 0x7CFFA4;
    /** Cor do status ABERTO (o verde da casa, mais forte). */
    public static final int COR_ABERTO = 0x39FF6E;
    /** Cor do status FECHADO (vermelho de "volte amanhã"). */
    public static final int COR_FECHADO = 0xFF5A48;

    /**
     * Texto de fábrica — o nome da loja em UMA LINHA (v1.2.31): o display de
     * fachada tem a largura inteira do prédio, então "MERCADO ESQUINÃO" vai
     * esticado de ponta a ponta (o renderer escala até PREENCHER a faixa),
     * letreiro de display de Satisfactory. As linhas de baixo ficam pro
     * status ABERTO/FECHADO do ciclo.
     */
    static final String[] LINHAS_PADRAO = {
            "MERCADO ESQUINÃO"
    };
    /** Linha de status (v1.2.19) — texto do letreiro do posto, pt-br da loja. */
    static final String STATUS_ABERTO = "ABERTO";
    static final String STATUS_FECHADO = "FECHADO";

    /**
     * ACORDE DA VIRADA (v1.2.19): os 4 tons de C maior subindo (abriu) ou
     * descendo (fechou) — a "campainha" do letreiro LED.
     */
    public static final float[] ACORDE_VIRADA = {0.6F, 0.8F, 1.0F, 1.2F};

    /** v1.2.36 — A COR DO LED, gravada no NBT (a Central de Comando troca).
     * Padrão: o verde da casa. Qualquer RGB vale — o renderer pinta o pixel. */
    private int cor = COR_LED;
    /** v1.2.36 — TRAVA do letreiro do mercado: com tranca, a Central só abre
     * com a chave (comparador) na mão. Placa solta do jogador nunca nasce trancada. */
    private boolean trancada = false;
    private final List<String> linhas = new ArrayList<>();
    // placa solta (colocada pelo jogador) nasce MUDA — só a placa do template
    // do mercado é gravada com mercado=1 (é ela que pulsa e pisca)
    private boolean linkMercado = false;
    /** Estado atual ABERTO/FECHADO (só tem significado com linkMercado). */
    private boolean aberto = true;
    /**
     * v1.2.31 — VERSÃO DA ANATOMIA gravada no NBT: 1/ausente = placa antiga
     * de 4 linhas (1.2.18–1.2.23); 2 = letreiro de TORRES na calçada
     * (1.2.24–1.2.30); 3 = DISPLAY DE FACHADA (faixa larga montada na parede,
     * estilo Satisfactory). O zelador migra 1→3 e 2→3 automaticamente.
     */
    public static final int VERSAO_ANATOMIA = 3;
    /**
     * v1.2.31 — A LARGURA DO DISPLAY DE FACHADA, em blocos: a faixa atravessa
     * TODO o prédio do mercado (15 blocos de fachada no template), estilo
     * Satisfactory. O texto ESTICA dentro dela — "MERCADO ESQUINÃO" numa
     * linha só preenche a fachada.
     */
    public static final int LARGURA_FACHADA = 15;
    /**
     * v1.2.24 — MARCA DE ANATOMIA (legado): true = placa nova. Mantida para
     * saves antigos carregarem; o zelador usa VERSAO_ANATOMIA agora.
     */
    private boolean nova = true;
    /**
     * v1.2.27 — RITMO DA AUTOCURA: passada lenta (a cada 30s) — barata
     * (o jogo carregou: 1 nó + 12 setBlocks raros) e suficiente: a placa
     * nasce consertada na 1ª passada depois do chunk carregar.
     */
    private static final long INTERVALO_AUTOCURA = 600L;
    private long proximaAutocura;
    /** v1.2.31: largura da faixa em blocos (15 na fachada do mercado). */
    private int largura = LARGURA_FACHADA;
    /** v1.2.31: versão da anatomia (2 = torres velhas, 3 = fachada). */
    private int versao = VERSAO_ANATOMIA;

    public PlacaEsquinaoBlockEntity(BlockPos pos, BlockState state) {
        super(IntoxicantesMod.PLACA_ESQUINAO_ENTITY, pos, state);
        resetarPadrao();
    }

    /**
     * O ZELADOR DA AUTOCURA (ticker de server, v1.2.27): desperta o BE
     * toda passada; a cada 30s (e na 1ª), 1) adota o texto novo se for
     * placa de mundo velho (linha0 "SUL", "DISTRIBUIDORA"... — o template
     * velho não semeava torres nem texto grande) e 2) ERGUE AS TORRES que
     * faltam (a v1.2.25 contava com onPlace — que a geração de estrutura
     * NUNCA chama; por isso o letreiro do print era 1 bloco solto).
     */
    static void zeladorDaAutocura(Level level, BlockPos pos,
            PlacaEsquinaoBlockEntity be) {
        if (level.isClientSide() || level.getGameTime() < be.proximaAutocura) return;
        be.proximaAutocura = level.getGameTime() + INTERVALO_AUTOCURA;

        // v1.2.51 — A FAIXA INTEIRA: mundos da 1.2.31–1.2.50 nasceram com SÓ
        // o painel (1 bloco de placa no meio da parede de concreto — o texto
        // desenhado POR CIMA da fachada errada, o "caos" do playtest).
        completarFaixa(level, pos, be);

        // v1.2.31: placa de anatomia VELHA (versao<3, torres na calçada) é
        // MIGRADA pro display de fachada — 1× só (a faixa nova nasce versao 3)
        if (be.versao < VERSAO_ANATOMIA) {
            be.versao = VERSAO_ANATOMIA; // marca ANTES de mexer no mundo: sem loop
            PlacaEsquinaoBlock.migrarParaFachada(level, pos, be);
            return;
        }

        // v1.2.36 — A ESTICADA DO TEXTO: placas de mundos 1.2.31–1.2.35
        // carregavam o nome em 2 linhas ("MERCADÃO"/"DA ESQUINA") que deixava
        // o display de fachada meio vazio. Adota o padrão de 1 linha — o nome
        // esticado de ponta a ponta (estilo Satisfactory) — MANTENDO cor,
        // trava e vínculo com o mercado. Texto já em 1 linha (inclusive
        // customizado pela Central) não é tocado.
        if (be.linhas.size() > 1) {
            be.linhas.clear();
            for (String l : LINHAS_PADRAO) be.linhas.add(l);
            be.setChanged();
            level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
        }

        // placa 1.2.18–1.2.23 (4 linhas velhas): adota o texto novo
        if (!be.isNova()) {
            be.linhas.clear();
            for (String l : LINHAS_PADRAO) be.linhas.add(l);
            be.nova = true;
            be.setChanged();
            level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
        }
    }

    private void resetarPadrao() {
        linhas.clear();
        for (String l : LINHAS_PADRAO) linhas.add(l);
    }

    /** v1.2.51: ajusta a largura da faixa (só encolhe — nunca some o nome). */
    public void setLargura(int nova) {
        int ajustada = Math.max(1, nova);
        if (ajustada == this.largura) return;
        this.largura = ajustada;
        setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * v1.2.51 — A AUTOCURA DA FAIXA: completa o display de fachada com os
     * blocos de EXTENSÃO que faltam (±7 no eixo da fachada = 15 no total).
     * Sempre verde vivo. Seguro por construção: só SEMEIA em AR — parede do
     * próprio mercado ou bloco de jogador NUNCA é sobrescrito (a varredura
     * para no primeiro não-ar de cada lado). Idempotente: extensão que já
     * existe é contada, não re-montada.
     */
    static void completarFaixa(Level level, BlockPos pos, PlacaEsquinaoBlockEntity be) {
        if (level.isClientSide() || !be.isLinkMercado()) return;
        BlockState minha = be.getBlockState();
        BlockState caixa = IntoxicantesMod.PLACA_ESQUINAO.defaultBlockState()
                .setValue(PlacaEsquinaoBlock.FACING, minha.getValue(PlacaEsquinaoBlock.FACING))
                .setValue(PlacaEsquinaoBlock.NIVEL, PlacaEsquinaoBlock.Nivel.COLUNA)
                .setValue(PlacaEsquinaoBlock.LIT, minha.getValue(PlacaEsquinaoBlock.LIT));
        Direction eixo = minha.getValue(PlacaEsquinaoBlock.FACING).getClockWise();
        int montados = 1; // o painel (este bloco) já conta
        for (int lado = -1; lado <= 1; lado += 2) {
            for (int d = 1; d <= PlacaEsquinaoBlockEntity.LARGURA_FACHADA / 2; d++) {
                BlockPos p = pos.relative(eixo, lado * d);
                BlockState s = level.getBlockState(p);
                if (s.getBlock() == IntoxicantesMod.PLACA_ESQUINAO) {
                    montados++;
                    continue;
                }
                if (!s.isAir()) break; // parede/jogador: NUNCA sobrescreve
                level.setBlockAndUpdate(p, caixa.setValue(PlacaEsquinaoBlock.PARTE,
                        PlacaEsquinaoBlock.Parte.EXTENSAO));
                montados++;
            }
        }
        if (montados != be.getLargura()) {
            be.setLargura(montados);
        }
    }

    public List<String> getLinhas() {
        return linhas;
    }

    /** v1.2.36: cor atual do LED (RGB sem alpha). */
    public int getCor() {
        return cor;
    }

    /**
     * v1.2.36 — A CENTRAL DE COMANDO TROCA A COR: grava, sincroniza e o
     * client redesenha na hora (mesmo caminho do abertoChanged).
     */
    public void setCor(int novaCor) {
        this.cor = novaCor & 0xFFFFFF;
        setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * v1.2.36 — A CENTRAL DE COMANDO TROCA O TEXTO: substitui as linhas
     * (a Central manda até 3; linhas em branco são descartadas aqui) e
     * sincroniza. Serve pro letreiro do mercado E pro painel craftável.
     */
    public void setLinhas(List<String> novas) {
        linhas.clear();
        for (String l : novas) {
            if (l != null && !l.isBlank()) linhas.add(l);
        }
        if (linhas.isEmpty()) resetarPadrao();
        setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** v1.2.36: trava de edição (só o letreiro do mercado usa). */
    public boolean isTrancada() {
        return trancada;
    }

    public void setTrancada(boolean trancada) {
        this.trancada = trancada;
        setChanged();
    }

    public boolean isLinkMercado() {
        return linkMercado;
    }

    public boolean isAberto() {
        return aberto;
    }

    /** v1.2.24: true = anatomia nova (o zelador NÃO desmonta). */
    public boolean isNova() {
        return nova;
    }

    /** v1.2.31: versão da anatomia gravada no NBT (3 = display de fachada). */
    public int getVersao() {
        return versao;
    }

    /** v1.2.31: largura da faixa em blocos (o texto estica dentro dela). */
    public int getLargura() {
        return largura;
    }

    /**
     * v1.2.31 — CONTEÚDO da faixa (a migração copia texto/vínculo/estado da
     * placa velha pro display novo antes do painel velho sair).
     */
    public void definirConteudo(List<String> novasLinhas, boolean link, boolean abertoAgora,
            int larguraNova) {
        linhas.clear();
        linhas.addAll(novasLinhas);
        this.linkMercado = link;
        this.aberto = abertoAgora;
        this.largura = Math.max(1, larguraNova);
        setChanged();
    }

    /**
     * O MERCADO VIROU (server): troca o estado e força o sync — o client
     * redesenha o status e as trocas de cor são perceptíveis na hora.
     */
    public void abertoChanged(boolean novoAberto) {
        this.aberto = novoAberto;
        setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // v1.2.25: linhas VAZIAS não ressuscitam — o NBT de placa de 4 linhas
        // velha guarda "linha2"/"linha3" = ""; carregar esses fantasmas fazia
        // o renderer espremer o texto (grid cheio de linhas em branco).
        linhas.clear();
        for (int i = 0; i < 4; i++) {
            String l = input.getStringOr("linha" + i, "");
            if (!l.isEmpty()) linhas.add(l);
        }
        if (linhas.isEmpty()) resetarPadrao();
        linkMercado = input.getBooleanOr("mercado", false);
        aberto = input.getBooleanOr("aberto", true);
        // placa de mundo velho (1.2.18~1.2.23) não tem a chave: false = velha
        nova = input.getBooleanOr("nova", false);
        // v1.2.31: versão da anatomia (ausente em save velho = 1) e largura
        // da faixa (só importa na anatomia 3; ausente = fachada padrão)
        versao = input.getIntOr("versao", 1);
        largura = input.getIntOr("largura", LARGURA_FACHADA);
        if (largura < 1) largura = LARGURA_FACHADA;
        // v1.2.36: cor do LED (ausente = o verde da casa) e trava (ausente = destrancada)
        cor = input.getIntOr("cor", COR_LED) & 0xFFFFFF;
        trancada = input.getBooleanOr("trancada", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (int i = 0; i < 4; i++) {
            output.putString("linha" + i, i < linhas.size() ? linhas.get(i) : "");
        }
        output.putBoolean("mercado", linkMercado);
        output.putBoolean("aberto", aberto);
        output.putBoolean("nova", nova);
        output.putInt("versao", versao);
        output.putInt("largura", largura);
        output.putInt("cor", cor);
        output.putBoolean("trancada", trancada);
    }

    /**
     * Sync vanilla pro client: o update tag leva o save completo (linhas,
     * vínculo e estado ABERTO/FECHADO) e o packet padrão dispara o re-sync.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Render data pro client (Fabric RenderDataBlockEntity): retorna a própria
     * instância do lado CLIENTE — os campos já chegaram pelo sync vanilla de
     * block entity, então o renderer lê as linhas direto daqui.
     */
    @Override
    public Object getRenderData() {
        return this;
    }

    /** Tag NBT que o template do mercado grava na entrada de bloco da placa. */
    public static CompoundTag nbtDoTemplate() {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < LINHAS_PADRAO.length; i++) {
            tag.putString("linha" + i, LINHAS_PADRAO[i]);
        }
        tag.putBoolean("mercado", true);
        tag.putBoolean("nova", true);
        tag.putInt("versao", VERSAO_ANATOMIA);
        tag.putInt("largura", LARGURA_FACHADA);
        return tag;
    }

    /**
     * O ARPEJO DA VIRADA (server): C maior subindo no "abriu" (07:00 — bom dia
     * pra comprar), descendo no "fechou" (00:00 — vai durmir). Pitch 0.5 de
     * distância entre notas: soa como o programa de horário do letreiro de
     * posto de gasolina antigo.
     */
    public void tocarAcordeVirada(boolean abriu) {
        if (!(this.level instanceof net.minecraft.server.level.ServerLevel server)) return;
        // a placa pode ter sido quebrada entre a virada e agora
        if (isRemoved()) return;
        float[] acorde = PlacaEsquinaoBlockEntity.ACORDE_VIRADA;
        float base = abriu ? 0.9F : 1.2F;
        for (int i = 0; i < acorde.length; i++) {
            final float pitch = abriu ? base * acorde[i] : base * acorde[acorde.length - 1 - i];
            MarketSystem.agendarNota(server, this.worldPosition,
                    IntoxicantesMod.LETREIRO_VIRADA, 0.8F, pitch, i * 6);
        }
        // a poeira da virada: vermelho fechou, verde abriu — poeira sobre o painel
        int cor = abriu ? COR_ABERTO : COR_FECHADO;
        server.sendParticles(
                new net.minecraft.core.particles.DustParticleOptions(cor, 1.0F),
                this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.1,
                this.worldPosition.getZ() + 0.5, 14, 0.9, 0.15, 0.35, 0.01);
    }
}
