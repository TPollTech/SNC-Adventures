package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * O CÉREBRO DO PAINEL DE LED CRAFTÁVEL (v1.2.36): 2 linhas de texto, COR do
 * LED, BRILHO e MODO (fixo/scroll) — tudo editado pela Central de Comando e
 * persistido no NBT. O client recebe o estado inteiro pelo sync vanilla
 * (getUpdateTag = save) e o {@code PainelLedRenderer} desenha os pixels.
 */
public class PainelLedBlockEntity extends BlockEntity {

    /** Máximo de caracteres por linha (o renderer estica pra caber). */
    public static final int MAX_CARACTERES = 16;
    /** Modos de exibição. */
    public static final int MODO_FIXO = 0;
    public static final int MODO_SCROLL = 1;

    private final List<String> linhas = new ArrayList<>();
    private int cor = PlacaEsquinaoBlockEntity.COR_LED;
    /** Brilho 0..15 (o alpha do LED na tela). */
    private int brilho = 15;
    private int modo = MODO_FIXO;

    public PainelLedBlockEntity(BlockPos pos, BlockState state) {
        super(IntoxicantesMod.PAINEL_LED_ENTITY, pos, state);
        resetarPadrao();
    }

    private void resetarPadrao() {
        linhas.clear();
        linhas.add("PAINEL LED");
    }

    public List<String> getLinhas() {
        return linhas;
    }

    public int getCor() {
        return cor;
    }

    public int getBrilho() {
        return brilho;
    }

    public int getModo() {
        return modo;
    }

    /** Aplica tudo de uma vez (a Central manda o pacote completo). */
    public void aplicar(List<String> novasLinhas, int novaCor, int novoBrilho, int novoModo) {
        linhas.clear();
        for (String l : novasLinhas) {
            if (l != null && !l.isBlank()) linhas.add(l.substring(0, Math.min(l.length(), MAX_CARACTERES)));
        }
        if (linhas.isEmpty()) resetarPadrao();
        while (linhas.size() > 2) linhas.remove(linhas.size() - 1);
        this.cor = novaCor & 0xFFFFFF;
        this.brilho = Math.max(0, Math.min(15, novoBrilho));
        this.modo = novoModo == MODO_SCROLL ? MODO_SCROLL : MODO_FIXO;
        setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        linhas.clear();
        for (int i = 0; i < 2; i++) {
            String l = input.getStringOr("linha" + i, "");
            if (!l.isEmpty()) linhas.add(l);
        }
        if (linhas.isEmpty()) resetarPadrao();
        cor = input.getIntOr("cor", PlacaEsquinaoBlockEntity.COR_LED) & 0xFFFFFF;
        brilho = Math.max(0, Math.min(15, input.getIntOr("brilho", 15)));
        modo = input.getIntOr("modo", MODO_FIXO) == MODO_SCROLL ? MODO_SCROLL : MODO_FIXO;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (int i = 0; i < 2; i++) {
            output.putString("linha" + i, i < linhas.size() ? linhas.get(i) : "");
        }
        output.putInt("cor", cor);
        output.putInt("brilho", brilho);
        output.putInt("modo", modo);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Render data pro client (mesma convenção do letreiro). */
    @Override
    public Object getRenderData() {
        return this;
    }

    /**
     * v1.2.40 — EXTENSÃO É MUDA: este painel tem IRMÃO no lado A (clockwise
     * da facing)? Então ele é extensão da linha — o texto mora no CABEÇA (a
     * ponta sem irmão). A extensão NÃO desenha texto próprio: herda o do
     * cabeça (1 texto atravessando a TV inteira). O template 1.2.36–39
     * gravava NBT em todos os blocos da linha → 5 textos sobrepostos (o
     * display "bugado" do print). Verificação DINÂMICA (sem flag no NBT):
     * imune a save/load e a linhas montadas/desmontadas.
     */
    public boolean isExtensao() {
        if (this.level == null) return false;
        BlockState s = getBlockState();
        net.minecraft.core.Direction facing = s.hasProperty(PainelLedBlock.FACING)
                ? s.getValue(PainelLedBlock.FACING)
                : net.minecraft.core.Direction.NORTH;
        return this.level.getBlockEntity(
                this.worldPosition.relative(facing.getClockWise()))
                instanceof PainelLedBlockEntity;
    }
}
