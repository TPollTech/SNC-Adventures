package com.intoxicantes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * O POSTE DE LUZ DO ESTACIONAMENTO (v1.2.19, REFEITO na v1.2.24) — poste de
 * rua de VERDADE, 3 BLOCOS: BASE (pedestal de concreto no chão + arranque da
 * coluna), CORPO (coluna comprida de metal) e TOPO (braço com a luminária de
 * sódio pendurada). Colocar QUALQUER parte ergue/derruba o poste INTEIRO —
 * e sem apagar bloco alheio no caminho (o bug do setBlockAndUpdate).
 *
 * O LIGA SOZINHO às 19:00 e DESLIGA às 05:00 — luz de sódio da esquina (o
 * mercado fecha à meia-noite, mas o pátio fica aceso pra ninguém ser
 * assaltado voltando do bar kkkk). A checagem é AGENDADA (scheduleTick,
 * re-avalia a cada ~5s): barato e no MESMO relógio do letreiro. A batida de
 * ligar/desligar é o "bzzt" do reator fluorescente.
 *
 * O estado LIT vive nas TRÊS partes (o lightLevel 14 do bloco vale só no
 * TOPO aceso — a fonte de luz é a luminária, não a coluna).
 */
public class PosteLuzBlock extends Block {

    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    /** Qual parte do poste este bloco é (v1.2.24: 3 partes, poste de 3 blocos). */
    public static final EnumProperty<Parte> PARTE = EnumProperty.create("parte", Parte.class);

    public enum Parte implements net.minecraft.util.StringRepresentable {
        BASE("base"), CORPO("corpo"), TOPO("topo");

        private final String nome;

        Parte(String nome) {
            this.nome = nome;
        }

        @Override
        public String getSerializedName() {
            return nome;
        }
    }

    /** BASE: pedestal 10/16 no chão + arranque da coluna no alto do bloco. */
    private static final VoxelShape SHAPE_BASE = Block.box(5.0, 0.0, 5.0, 11.0, 6.0, 11.0);
    /** CORPO: coluna de metal 4/16 vazada, o bloco inteiro. */
    private static final VoxelShape SHAPE_CORPO = Block.box(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);
    /**
     * TOPO (v1.2.27): a luminária É O TOPO DO POSTE — coluna curta + capitel,
     * lente acesa e tampa, TUDO EM CIMA (simétrico, visível de qualquer lado;
     * o braço lateral antigo punha a lâmpada de lado e fora do eixo).
     */
    private static final VoxelShape SHAPE_TOPO = Block.box(4.0, 0.0, 4.0, 12.0, 14.0, 12.0);

    public PosteLuzBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(LIT, Boolean.TRUE)
                .setValue(PARTE, Parte.BASE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, PARTE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return switch (state.getValue(PARTE)) {
            case BASE -> SHAPE_BASE;
            case CORPO -> SHAPE_CORPO;
            case TOPO -> SHAPE_TOPO;
        };
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
        // nasce no estado certo pro horário atual (a base; corpo+topo sobem no onPlace)
        return this.defaultBlockState()
                .setValue(LIT, estaNoHorarioDeLuz(ctx.getLevel()))
                .setValue(PARTE, Parte.BASE);
    }

    /** Deve estar aceso AGORA? 19:00 ~ 05:00 (mesma conta do letreiro). */
    static boolean estaNoHorarioDeLuz(LevelReader level) {
        long t = nivelTicks(level);
        return t >= 13000L && t < 23000L;
    }

    /** Ticks do dia do overworld (client e server: o mesmo relógio). */
    private static long nivelTicks(LevelReader level) {
        if (level instanceof Level l) return l.getOverworldClockTime() % 24000L;
        return 0L;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
            BlockState estadoAntigo, boolean movidoPorPistao) {
        super.onPlace(state, level, pos, estadoAntigo, movidoPorPistao);
        if (level.isClientSide() || movidoPorPistao) return;
        if (!(state.getBlock() instanceof PosteLuzBlock)) return;
        if (!(level instanceof ServerLevel server)) return;

        // v1.2.27: QUALQUER parte ergue o poste — MAS com política anti-dupla-
        // coluna: BASE constrói pra CIMA; TOPO constrói pra BAIXO (o template
        // semeia o topo); CORPO SOLTO não constrói NADA (um corpo desgarrado
        // que erguesse base+topo viraria um SEGUNDO poste no lugar errado —
        // o zelador recolhe essa peça em vez de multiplicá-la)
        Parte parte = state.getValue(PARTE);
        if (parte == Parte.BASE) {
            selfHealAPartirDaBase(server, pos);
        } else if (parte == Parte.TOPO && !temOutroPostePerto(server, pos)) {
            selfHealAPartirDoTopo(server, pos);
        }
        agendaProximaVirada(level, pos);
    }

    /**
     * v1.2.27 — A AUTOCURA DO POSTE: o mundo de verdade tem erros de
     * montagem que "só preencher ar" não conserta — o erro do template da
     * 1.2.24 deslocava o CORPO 1 bloco pro lado (sweep de estrutura sobre
     * seed parcial) e ficava PRA SEMPRE (a peça solta nunca era tocada).
     * Cada passada agora: constrói a parte que FALTA em ar/substituível,
     * PROMOVE partes erradas (base→corpo, corpo→topo) e MOVE de volta peças
     * da mesma coluna que nasceram deslocadas — e NUNCA apaga bloco alheio.
     *
     * A partir da BASE: conserta o corpo em cima e o topo em cima dele.
     */
    static void selfHealAPartirDaBase(ServerLevel level, BlockPos basePos) {
        BlockState modelo = level.getBlockState(basePos);
        if (!(modelo.getBlock() instanceof PosteLuzBlock)) return;
        consertarParte(level, basePos.above(), modelo, Parte.CORPO);
        consertarParte(level, basePos.above(2), modelo, Parte.TOPO);
    }

    /**
     * A partir do TOPO (ou de um CORPO solto): conserta o corpo logo acima
     * da base e a base no chão — a coluna inteira volta pro eixo.
     */
    static void selfHealAPartirDoTopo(ServerLevel level, BlockPos topoPos) {
        BlockState modelo = level.getBlockState(topoPos);
        if (!(modelo.getBlock() instanceof PosteLuzBlock)) return;
        BlockPos base = topoPos.below(2);
        consertarParte(level, topoPos.below(), modelo, Parte.CORPO);
        consertarParte(level, base, modelo, Parte.BASE);
    }

    /**
     * Conserta UMA posição da coluna pra ser {@code alvo}:
     * <ul>
     *   <li>mesma parte, no lugar: nada a fazer (mantém o LIT próprio);</li>
     *   <li>vazio/substituível: constrói;</li>
     *   <li>PosteLuzBlock de OUTRA parte (erro de montagem) ou posto
     *       deslocado de poste SEM nicho no eixo: reposiciona (só quando o
     *       bloco errado pertence a ESTE poste — a coluna é nossa).</li>
     *   <li>bloco alheio sólido: intocável (nada de pagar parede de vizinho).</li>
     * </ul>
     */
    private static void consertarParte(ServerLevel level, BlockPos pos,
            BlockState modelo, Parte alvo) {
        BlockState atual = level.getBlockState(pos);
        if (atual.getBlock() instanceof PosteLuzBlock) {
            Parte parteAtual = atual.getValue(PARTE);
            if (parteAtual == alvo) return; // certo
            // parte errada do PRÓPRIO poste (ex.: topo onde era corpo):
            // reclassifica no lugar, preservando o LIT dali
            level.setBlock(pos, atual.setValue(PARTE, alvo), 3);
            return;
        }
        if (!atual.isAir() && !atual.canBeReplaced()) return; // alheio: intocável
        level.setBlock(pos, modelo.setValue(PARTE, alvo), 3);
    }

    /**
     * Agenda a próxima virada 19:00/05:00 (máx ~5min). Package-private: o
     * game test chama direto pra avançar o relógio e verificar a troca.
     */
    /**
     * v1.2.27 — PEÇA DESGARRADA? True se este bloco de poste NÃO pertence a
     * nenhuma coluna: sem BASE na própria x/z (até 3 abaixo, mesmo com buraco
     * no meio) e SIM um poste com base numa coluna vizinha (±1 no plano) —
     * é o erro do template antigo que plantava o corpo 1 bloco pro lado (o
     * "negócio preto flutuando" do print). O zelador recolhe só essas;
     * qualquer coisa solta sem poste perto fica intocada (não somos vandalas).
     */
    static boolean eDesgarrada(ServerLevel level, BlockPos pos) {
        // a própria coluna tem base? (desce até 3, pulando buracos)
        for (int dy = 1; dy <= 3; dy++) {
            BlockState abaixo = level.getBlockState(pos.below(dy));
            if (abaixo.getBlock() instanceof PosteLuzBlock
                    && abaixo.getValue(PARTE) == Parte.BASE) {
                return false;
            }
        }
        // coluna vizinha (±1 no plano) com base? então esta peça fugiu dela
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos vizinho = pos.relative(d);
            for (int dy = 0; dy <= 2; dy++) {
                BlockState s = level.getBlockState(vizinho.below(dy));
                if (s.getBlock() instanceof PosteLuzBlock
                        && s.getValue(PARTE) == Parte.BASE) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Outro poste (BASE) num raio de 2 blocos horizontais? (Se sim, um TOPO
     * recém-colocado aqui é peça desgarrada DELE — não deve fundar coluna
     * própria.)
     */
    private static boolean temOutroPostePerto(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos vizinho = pos.relative(d);
            for (int dy = -2; dy <= 0; dy++) {
                BlockState s = level.getBlockState(vizinho.below(-dy));
                if (s.getBlock() instanceof PosteLuzBlock
                        && s.getValue(PARTE) == Parte.BASE) {
                    return true;
                }
            }
        }
        return false;
    }

    static void agendaProximaVirada(Level level, BlockPos pos) {
        long t = nivelTicks(level);
        long ateVirada;
        if (t < 13000L) {
            ateVirada = 13000L - t;          // falta pra ACENDER (19:00)
        } else if (t < 23000L) {
            ateVirada = 23000L - t;          // falta pra APAGAR (05:00)
        } else {
            ateVirada = (24000L - t) + 13000L; // madrugada: falta pra ACENDER
        }
        // mínimo de 1 tick, teto de 5s (100 ticks): re-avalia sempre que o
        // mundo deixar (a conta exata até a virada é refeita a cada tick)
        if (level.getBlockState(pos).getBlock() instanceof PosteLuzBlock poste) {
            level.scheduleTick(pos, poste, (int) Math.max(1, Math.min(100, ateVirada)));
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean alvo = estaNoHorarioDeLuz(level);
        if (state.getValue(LIT) != alvo) {
            BlockState novo = state.setValue(LIT, alvo);
            level.setBlock(pos, novo, 3);
            // espalha pro poste INTEIRO (v1.2.24: 3 partes)
            BlockPos b = baseDo(level, pos);
            if (b != null) {
                for (BlockPos p : new BlockPos[]{b, b.above(), b.above(2)}) {
                    BlockState s = level.getBlockState(p);
                    if (s.getBlock() instanceof PosteLuzBlock && s.getValue(LIT) != alvo) {
                        level.setBlock(p, s.setValue(LIT, alvo), 3);
                    }
                }
            }
            if (alvo) {
                // o "bzzt-tick" do reator ligando
                level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5F, 1.6F);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                        pos.getX() + 0.5, pos.getY() + 1.8, pos.getZ() + 0.5,
                        6, 0.15, 0.15, 0.15, 0.02);
            } else {
                // apagando ao amanhecer: click grave e a luz morre
                level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.4F, 0.7F);
            }
        }
        agendaProximaVirada(level, pos);
    }

    /** Acha a BASE do poste (desce enquanto houver PosteLuzBlock abaixo). */
    private static BlockPos baseDo(ServerLevel level, BlockPos pos) {
        BlockPos p = pos;
        for (int i = 0; i < 3; i++) {
            BlockPos abaixo = p.below();
            if (level.getBlockState(abaixo).getBlock() instanceof PosteLuzBlock) {
                p = abaixo;
            } else {
                return p;
            }
        }
        return p;
    }

    /**
     * Quebrou uma parte? o poste INTEIRO cai (v1.2.24: 3 partes).
     * Sem drop dobrado: as irmãs somem quietinhas (dropBlock=false).
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level,
            BlockPos pos, boolean movidoPorPistao) {
        super.affectNeighborsAfterRemoval(state, level, pos, movidoPorPistao);
        if (movidoPorPistao) return;
        BlockPos base = baseDo(level, pos);
        for (BlockPos p : new BlockPos[]{base, base.above(), base.above(2)}) {
            if (!p.equals(pos) && level.getBlockState(p).getBlock() instanceof PosteLuzBlock) {
                level.destroyBlock(p, false);
            }
        }
    }

    /**
     * O chão debaixo da base cede (jogador quebrou, areia caiu)? a base cai
     * e o poste inteiro vem abaixo.
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level,
            net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos posVizinho,
            BlockState estadoVizinho, RandomSource random) {
        if (state.getValue(PARTE) == Parte.BASE
                && direction == Direction.DOWN && !estadoVizinho.isFaceSturdy(
                        level, posVizinho, Direction.UP)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, posVizinho,
                estadoVizinho, random);
    }

    /** Zumbido ambiente suave do reator (só de noite, igual à lâmpada UV). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        if (random.nextFloat() < 0.015F) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                    IntoxicantesMod.ZUMBIDO_UV, net.minecraft.sounds.SoundSource.BLOCKS,
                    0.25F, 1.35F, false);
        }
    }
}
