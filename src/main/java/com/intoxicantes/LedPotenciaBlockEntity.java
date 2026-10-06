package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Guarda a chave local e preserva a escolha dos saves anteriores. Potência
 * vem do bloco registrado; alimentação vem da rede, que escreve LIT.
 */
public class LedPotenciaBlockEntity extends BlockEntity {

    /** A chave local solicita funcionamento; a rede decide se há alimentação. */
    private boolean habilitada = true;

    public LedPotenciaBlockEntity(BlockPos pos, BlockState state) {
        super(IntoxicantesMod.LAMPADA_LED_POTENCIA_ENTITY, pos, state);
    }

    /** Entrada legada: a potência efetiva sempre vem do registro. */
    public void definirWatts(int novosWatts) {
        // Compatibilidade com chamadas antigas: o registro continua a fonte de potência.
        setChanged();
    }

    public int watts() {
        return LedPotenciaBlock.wattsDoBloco(getBlockState().getBlock());
    }

    public boolean habilitada() {
        return habilitada;
    }

    public long consumoW() {
        return habilitada ? watts() : 0L;
    }

    public void definirHabilitada(boolean valor) {
        if (habilitada == valor) return;
        habilitada = valor;
        setChanged();
        if (!valor) energizar(false);
        marcarRede();
    }

    /** Só a medição da rede acende a luminária; chave local não gera energia. */
    public void energizar(boolean alimentada) {
        if (!(level instanceof ServerLevel servidor)
                || servidor.getBlockEntity(worldPosition) != this) return;
        BlockState estado = servidor.getBlockState(worldPosition);
        if (!(estado.getBlock() instanceof LedPotenciaBlock)) return;
        boolean acesa = alimentada && habilitada;
        if (estado.getValue(LedPotenciaBlock.LIT) != acesa) {
            servidor.setBlock(worldPosition, estado.setValue(LedPotenciaBlock.LIT, acesa),
                    Block.UPDATE_CLIENTS);
        }
    }

    private void marcarRede() {
        if (level instanceof ServerLevel servidor) {
            com.intoxicantes.energia.EnergiaRedes.marcarRebuildProximo(servidor,
                    worldPosition, QuadroEletricoBlock.QuadroEletricoBlockEntity.RAIO_REDE);
        }
    }

    @Override
    public void setLevel(Level nivel) {
        super.setLevel(nivel);
        marcarRede();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        marcarRede();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        marcarRede();
    }

    // ==================================================== PERSISTÊNCIA

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ValueInput raiz = input.childOrEmpty("lampada");
        // Saves antigos usavam LIT como a chave local; preservar essa escolha.
        habilitada = raiz.getBooleanOr("habilitada",
                getBlockState().getValue(LedPotenciaBlock.LIT));
        marcarRede();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput raiz = output.child("lampada");
        raiz.putInt("watts", watts());
        raiz.putBoolean("habilitada", habilitada);
    }
}
