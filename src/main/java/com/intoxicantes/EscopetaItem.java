package com.intoxicantes;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A Escopeta do Gago versao do jogador — NIVEL GUN MOD (v1.2.32):
 *
 * - TUBO INTERNO de verdade: o cartucho vai pro tubo na recarga; o tiro consome
 *   a CAMARA (o cartucho engatilhado), nao "qualquer cartucho do inventario"
 * - RELOAD shell-by-shell: SEGURA o botao direito; a cada intervalo entra 1
 *   cartucho no tubo com "clac" proprio — solte quando quiser, o que entrou ficou
 * - PUMP-ACTION: depois do tiro o pump cicla sozinho (clack-clack) e a camara
 *   so volta a carregar com o tubo; tubo vazio = click seco
 * - RECOIL com KICK DE CAMERA: servidor manda payload S2C e o client chuta
 *   pitch+yaw com retorno suave (a mira volta sozinha pro alvo)
 * - ADS ao SEGURAR O BOTÃO DIREITO com a 12 na mao (v1.2.53): zoom de FOV suave, overlay de mira,
 *   dispersao pela metade e alcance maior
 *
 * Municao: intoxicantes:cartucho (polvora + papel + prego de ferro).
 */
public class EscopetaItem extends Item {

    /** Componente que guarda o mecanismo (tubo/camara/timer/fase) na stack. */
    public static final net.minecraft.core.component.DataComponentType<EscopetaEstado> ESTADO =
            IntoxicantesMod.TIPO_ESTADO_ESCOPETA;

    // ==================================================== CONFIG (calibravel em config/intoxicantes.json)
    private static int BALINS() { return ModConfig.get().escopetaBalins; }
    private static float DANO_POR_BALIM() { return ModConfig.get().escopetaDanoPorBalim; }
    private static double ALCANCE_MAXIMO() { return ModConfig.get().escopetaAlcanceMaximo; }
    private static int COOLDOWN_TICKS() { return ModConfig.get().escopetaCooldownTicks; }
    private static int CAPACIDADE_TUBO() { return ModConfig.get().escopetaCapacidadeTubo; }
    private static int TICKS_SHELL() { return ModConfig.get().escopetaTicksPorShell; }
    private static int TICKS_PUMP() { return ModConfig.get().escopetaTicksPump; }
    private static float KICK_PITCH() { return ModConfig.get().escopetaKickPitch; }
    private static float KICK_YAW() { return ModConfig.get().escopetaKickYaw; }

    public EscopetaItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<net.minecraft.network.chat.Component> output,
            net.minecraft.world.item.TooltipFlag flag) {
        EscopetaEstado estado = estado(stack);
        output.accept(net.minecraft.network.chat.Component.translatable(
                "item.intoxicantes.escopeta.dica"));
        output.accept(net.minecraft.network.chat.Component.translatable(
                "item.intoxicantes.escopeta.dicaR"));
        output.accept(net.minecraft.network.chat.Component.translatable(
                "item.intoxicantes.escopeta.dicaADS"));
        output.accept(net.minecraft.network.chat.Component.translatable(
                "item.intoxicantes.escopeta.estado",
                estado.camara() ? "\u2713" : "\u2014", estado.noTubo()));
        super.appendHoverText(stack, context, display, output, flag);
    }

    // ==================================================== ESTADO DO MECANISMO

    /** Estado atual (VAZIA se o item veio de save/comando sem componente). */
    public static EscopetaEstado estado(ItemStack stack) {
        EscopetaEstado e = stack.get(ESTADO);
        return e == null ? EscopetaEstado.VAZIA : e;
    }

    private static void guardar(ItemStack stack, EscopetaEstado estado) {
        stack.set(ESTADO, estado);
    }

    /** Cartuchos no inventario (−1 se criativo — nao gasta mesmo). */
    public static int contarCartuchos(Player player) {
        if (player.getAbilities().instabuild) {
            return -1;
        }
        int total = 0;
        var inventario = player.getInventory();
        for (int i = 0; i < inventario.getContainerSize(); i++) {
            ItemStack slot = inventario.getItem(i);
            if (slot.is(IntoxicantesMod.CARTUCHO)) {
                total += slot.getCount();
            }
        }
        return total;
    }

    /** Gasta 1 cartucho do inventario (criativo nao gasta). */
    private static boolean consumirMunicao(Player player) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        var inventario = player.getInventory();
        for (int i = 0; i < inventario.getContainerSize(); i++) {
            ItemStack slot = inventario.getItem(i);
            if (slot.is(IntoxicantesMod.CARTUCHO)) {
                slot.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static void tocar(ServerLevel level, Player player, SoundEvent som,
            float volume, float pitch) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                som, SoundSource.PLAYERS, volume, pitch);
    }

    // ==================================================== USO (segurar botao direito = recarregar)

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.SPEAR; // braco travado a frente: operacao manual
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity usuario) {
        // watchdog: 2.1s segurando = solta sozinho (ninguem carrega "pra sempre")
        return 41;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // v1.2.57: O BOTÃO DIREITO NÃO ATIRA NEM RECARREGA. Disparo é o GATILHO
        // ESQUERDO (MouseBotaoMixin → ArmasClient.gatilhoPuxado, v1.2.53) e
        // recarga é a TECLA R (recarregarViaTecla, v1.2.41/48). A mira é estado
        // do client (isRightPressed) — não é item.use(). Só fica o gate da
        // recarga por tecla em curso (o direito não rouba o mecanismo no meio).
        if (estado(stack).fase() == EscopetaEstado.FASE_TECLA) {
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /**
     * v1.2.57 — O DISPARO pelo gatilho esquerdo (GatilhoPayload C2S): o mesmo
     * caminho que o botão direito executava — camara carregada + mecanismo
     * pronto = ATIRA; senão, o click seco de percussão com o aviso. Validação
     * 100% servidor (fase, munição, cooldown).
     */
    void atirarViaGatilho(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        EscopetaEstado estado = estado(stack);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return; // em cooldown: silencioso, como o vanilla
        }
        if (estado.fase() == EscopetaEstado.FASE_TECLA) {
            return; // recarga por tecla em curso: o gatilho não rouba o mecanismo
        }
        if (estado.camara() && estado.pronta() && player.level() instanceof ServerLevel servidor) {
            atirar(servidor, player, stack, estado);
            return;
        }
        // sem câmara: click seco de percussão + aviso na action bar
        if (player.level() instanceof ServerLevel servidor) {
            tocar(servidor, player, SoundEvents.WOODEN_TRAPDOOR_CLOSE, 0.6F, 1.9F);
            player.sendOverlayMessage(Component.translatable("item.intoxicantes.escopeta.semmunicao"));
        }
    }

    // ==================================================== RECARGA PELA TECLA R (v1.2.41)

    /**
     * v1.2.41 — A tecla R: a mesma recarga shell-by-shell do botão direito, mas
     * SEM travar a pose (anda, mira e pula enquanto carrega).
     * v1.2.48 — R de UM APERTO: um toque abre a janela e o mecanismo fecha
     * SOZINHO ao encher o tubo (ou acabar a reserva) — soltar R não interrompe
     * mais. O servidor valida TUDO de novo (fase, espaço, reserva): client
     * malicioso ganha no máximo o que o botão direito permite. Chamado pelo
     * RecargaPayload (C2S).
     */
    public static void recarregarViaTecla(Player player, InteractionHand mao, boolean pressionar) {
        ItemStack stack = player.getItemInHand(mao);
        EscopetaEstado estado = estado(stack);

        // v1.2.48: soltar não interrompe mais — a janela fecha sozinha pelo
        // motor (tubo cheio ou reserva acabou). O release da tecla é inerte.
        if (!pressionar) {
            return;
        }

        // apertou: só inicia com o mecanismo livre (não interrompe pump nem tiro)
        if (!estado.pronta() || !(player.level() instanceof ServerLevel servidor)) {
            return;
        }
        boolean tuboTemEspaco = estado.noTubo() < CAPACIDADE_TUBO();
        boolean temReserva = player.getAbilities().instabuild || contarCartuchos(player) > 0;
        if (tuboTemEspaco && temReserva) {
            // timer=1: o 1º shell entra no próximo tick (resposta imediato)
            guardar(stack, new EscopetaEstado(estado.noTubo(), estado.camara(), 1,
                    EscopetaEstado.FASE_TECLA));
            tocar(servidor, player, SoundEvents.ITEM_FRAME_ADD_ITEM, 0.7F, 0.75F);
            if (player instanceof ServerPlayer sp) {
                RecargaPayload.TipoStatus.mandar(sp, true, "escopeta");
            }
        } else if (estado.noTubo() >= CAPACIDADE_TUBO()) {
            // tubo cheio: click seco de percussão (feedback de que a tecla vive)
            tocar(servidor, player, SoundEvents.WOODEN_TRAPDOOR_CLOSE, 0.5F, 1.9F);
        }
    }

    // ==================================================== RECARREGANDO (shell-by-shell)

    @Override
    public void onUseTick(Level level, LivingEntity usuario, ItemStack stack, int restante) {
        if (level.isClientSide()) {
            return; // o "clac" nasce no servidor (nao duplica)
        }
        if (!(usuario instanceof Player player)) {
            return;
        }
        EscopetaEstado estado = estado(stack);
        if (estado.fase() != EscopetaEstado.FASE_RECARREGANDO) {
            return;
        }
        ServerLevel servidor = (ServerLevel) level;

        // shell entra quando o tempo decorrido cruza cada multiplo de TICKS_SHELL
        int decorrido = getUseDuration(stack, usuario) - restante;
        int shellAtual = decorrido / TICKS_SHELL();
        int shellAnterior = (decorrido - 1) / TICKS_SHELL();

        if (shellAtual > shellAnterior && estado.noTubo() < CAPACIDADE_TUBO()
                && (player.getAbilities().instabuild || consumirMunicao(player))) {
            guardar(stack, new EscopetaEstado(estado.noTubo() + 1, estado.camara(),
                    estado.timer(), estado.fase()));
            tocar(servidor, player, SoundEvents.ITEM_FRAME_ADD_ITEM, 0.9F, 1.15F);
        }
    }

    /**
     * Fecha a recarga: fase PRONTA e, se a camara estava vazia, o fechamento
     * da bomba ja cama o primeiro cartucho do tubo (a 12 nunca fica "tubo
     * cheio, camara vazia e impossivel de atirar").
     */
    private static EscopetaEstado fecharRecarga(EscopetaEstado estado) {
        if (estado.camara() || estado.noTubo() <= 0) {
            return new EscopetaEstado(estado.noTubo(), estado.camara(), 0,
                    EscopetaEstado.FASE_PRONTA);
        }
        return new EscopetaEstado(estado.noTubo() - 1, true, 0, EscopetaEstado.FASE_PRONTA);
    }

    /**
     * v1.2.48: avisa o client que a recarga por tecla FECHOU — o motor fecha
     * sozinho no esquema de 1 aperto (o HUD sai do verde na hora).
     */
    private static void avisarFechoDaTecla(Entity dono) {
        if (dono instanceof ServerPlayer sp) {
            RecargaPayload.TipoStatus.mandar(sp, false, "escopeta");
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity usuario, int restante) {
        // soltou o botao: fecha a recarga — o que entrou no tubo, ficou
        if (!level.isClientSide()
                && estado(stack).fase() == EscopetaEstado.FASE_RECARREGANDO) {
            guardar(stack, fecharRecarga(estado(stack)));
            if (level instanceof ServerLevel servidor && usuario instanceof Player player) {
                tocar(servidor, player, SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.8F, 0.9F);
            }
        }
        return false;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity usuario) {
        // watchdog expirou (segurou ate o fim): fecha a recarga tambem
        if (!level.isClientSide()
                && estado(stack).fase() == EscopetaEstado.FASE_RECARREGANDO) {
            guardar(stack, fecharRecarga(estado(stack)));
        }
        return stack;
    }

    // ==================================================== PUMP-ACTION (o motor do mecanismo)

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity dono,
            EquipmentSlot slot) {
        EscopetaEstado estado = estado(stack);

        // v1.2.41: fase TECLA — shells entram no ritmo da recarga do botão
        // direito, mas sem travar pose; soltar R (ou encher/acabar a reserva)
        // fecha com o "clac" de fecho
        if (estado.fase() == EscopetaEstado.FASE_TECLA) {
            boolean semReserva = dono instanceof Player p
                    && !p.getAbilities().instabuild && contarCartuchos(p) <= 0;
            if (estado.noTubo() >= CAPACIDADE_TUBO() || semReserva) {
                guardar(stack, fecharRecarga(estado));
                if (dono instanceof Player player) {
                    tocar(level, player, SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.8F, 0.9F);
                }
                avisarFechoDaTecla(dono);
                return;
            }
            if (estado.timer() <= 1) {
                if (dono instanceof Player player
                        && (player.getAbilities().instabuild || consumirMunicao(player))) {
                    guardar(stack, new EscopetaEstado(estado.noTubo() + 1, estado.camara(),
                            TICKS_SHELL(), EscopetaEstado.FASE_TECLA));
                    tocar(level, player, SoundEvents.ITEM_FRAME_ADD_ITEM, 0.9F, 1.15F);
                } else {
                    guardar(stack, fecharRecarga(estado));
                    avisarFechoDaTecla(dono);
                }
            } else {
                guardar(stack, estado.tictac());
            }
            return;
        }

        if (estado.fase() != EscopetaEstado.FASE_BOMBA || estado.timer() <= 0) {
            return;
        }
        if (estado.timer() > 1) {
            guardar(stack, estado.tictac());
            return;
        }
        // ciclo do pump completou: vazio ejetado, proximo do tubo entra na camara
        boolean camara = estado.noTubo() > 0;
        int tubo = camara ? estado.noTubo() - 1 : 0;
        guardar(stack, new EscopetaEstado(tubo, camara, 0, EscopetaEstado.FASE_PRONTA));
        if (dono instanceof Player player) {
            // CLACK-CLACK: os dois tempos do ferrolho
            tocar(level, player, SoundEvents.PISTON_CONTRACT, 0.8F, 1.55F);
            tocar(level, player, SoundEvents.LEVER_CLICK, 0.7F, 0.65F);
            // v1.2.41: o VAZIO EJETADO — latão brilhando pro lado direito + "tlin"
            Vec3 olhando = player.getViewVector(1.0F);
            Vec3 saida = player.getEyePosition().add(olhando.scale(0.7))
                    .add(olhando.cross(new Vec3(0, 1, 0)).scale(0.35));
            level.sendParticles(ParticleTypes.GLOW, saida.x, saida.y - 0.15, saida.z,
                    2, 0.06, 0.06, 0.06, 0.0);
            tocar(level, player, SoundEvents.NOTE_BLOCK_HAT.value(), 0.5F, 1.8F);
            if (tubo == 0 && !player.getAbilities().instabuild) {
                player.sendOverlayMessage(Component.translatable(
                        "item.intoxicantes.escopeta.tuboVazio"));
            }
        }
    }

    // ==================================================== O TIRO

    private void atirar(ServerLevel level, Player player, ItemStack stack,
            EscopetaEstado estado) {
        // consome a CAMARA (nao o inventario!)
        guardar(stack, new EscopetaEstado(estado.noTubo(), false, 0, EscopetaEstado.FASE_PRONTA));

        Vec3 origem = player.getEyePosition();
        Vec3 olhando = player.getViewVector(1.0F);
        Vec3 boca = origem.add(olhando.scale(1.1));

        // som PROPRIETARIO do mod (soco_d12.ogg) + corpo grave
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                IntoxicantesMod.SOCO_D12, SoundSource.PLAYERS, 2.0F, 1.0F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 1.3F, 0.5F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, boca.x, boca.y, boca.z,
                8, 0.1, 0.1, 0.1, 0.02);
        level.sendParticles(ParticleTypes.FLAME, boca.x, boca.y, boca.z,
                4, 0.05, 0.05, 0.05, 0.01);

        // ADS: mira = metade da dispersao + 25% mais alcance (v1.2.53: botão
        // direito — MiraPayload sincroniza; sem arma, sem mira)
        boolean ads = MiraPayload.estaMirando((ServerPlayer) player);
        float fatorDisp = ads ? 0.5F : 1.0F;
        double alcanceEfetivo = ads ? ALCANCE_MAXIMO() * 1.25 : ALCANCE_MAXIMO();

        // balins hitscan por CONE amostrado (o sampler do ProjectileUtil varre por
        // celulas de chunk e deixava alvos finos escorrerem — detector proprio).
        // Dano acumulado por vitima e aplicado UMA vez (i-frames do vanilla).
        java.util.Map<LivingEntity, Double> feridos = new java.util.HashMap<>();
        java.util.List<LivingEntity> alvos = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(alcanceEfetivo + 4),
                a -> a != player && a.isAlive() && !a.isSpectator());
        int acertosTotais = 0;
        for (int i = 0; i < BALINS(); i++) {
            double desvio = 0.05 * fatorDisp;
            Vec3 mira = origem.add(olhando.scale(alcanceEfetivo)).add(
                    (player.getRandom().nextDouble() - 0.5) * 2 * desvio * alcanceEfetivo,
                    (player.getRandom().nextDouble() - 0.5) * 2 * desvio * alcanceEfetivo
                            + alcanceEfetivo * 0.005 * fatorDisp,
                    (player.getRandom().nextDouble() - 0.5) * 2 * desvio * alcanceEfetivo);
            Vec3 direcao = mira.subtract(origem).normalize();
            Vec3 fim = origem.add(direcao.scale(alcanceEfetivo + 1.5));

            // o balim ATRAVESSA vidro/pane (vitrine do mercado): vidro NAO e'
            // full-block -> nao trava o chumbo
            Vec3 inicio = origem;
            BlockHitResult bloco = level.clip(new ClipContext(inicio, fim,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            for (int perfurar = 0; perfurar < 4 && bloco.getType() != HitResult.Type.MISS
                    && !level.getBlockState(bloco.getBlockPos()).isCollisionShapeFullBlock(
                            level, bloco.getBlockPos()); perfurar++) {
                inicio = bloco.getLocation().add(direcao.scale(0.1));
                bloco = level.clip(new ClipContext(inicio, fim,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            }
            double alcance = bloco.getType() == HitResult.Type.MISS
                    ? alcanceEfetivo + 1.5
                    : bloco.getLocation().distanceTo(origem);

            // cone do balim: 12 amostras; a vitima mais PROXIMA leva
            LivingEntity vitima = null;
            double melhorDist = Double.MAX_VALUE;
            for (double d = 0.8; d <= alcance; d += (alcance - 0.4) / 12.0) {
                Vec3 ponto = origem.add(direcao.scale(d));
                for (LivingEntity a : alvos) {
                    double raio = a.getBbWidth() / 2.0 + 0.6 + d / alcanceEfetivo;
                    if (a.getBoundingBox().inflate(raio).contains(ponto) && d < melhorDist) {
                        vitima = a;
                        melhorDist = d;
                    }
                }
            }
            if (vitima != null) {
                // falloff: 100% de perto, decaendo ate 35% no limite do alcance
                double fator = Math.max(0.35, 1.0 - melhorDist / 32.0);
                feridos.merge(vitima, DANO_POR_BALIM() * fator, Double::sum);
                acertosTotais++;
                // v1.2.41: o rastro do balim (boca -> ponto de impacto)
                Chumbo.tracer(level, boca, origem.add(direcao.scale(melhorDist)));
            }
        }
        for (var ferido : feridos.entrySet()) {
            LivingEntity v = ferido.getKey();
            Chumbo.aplicar(level, v, player.damageSources().playerAttack(player),
                    ferido.getValue().floatValue());
            // v1.2.41: o chute físico do chumbo (respeita resistência a knockback)
            Chumbo.empurrar(v, player, 0.55 + ferido.getValue() * 0.08);
            // punchline do chumbo: "ding" agudo + nota subindo da vitima
            level.playSound(null, v.getX(), v.getY(), v.getZ(),
                    IntoxicantesMod.BALIM_ACERTO, SoundSource.PLAYERS, 0.7F, 1.0F);
            level.sendParticles(ParticleTypes.NOTE,
                    v.getX(), v.getY() + v.getBbHeight() + 0.2, v.getZ(),
                    1, 0.2, 0.1, 0.2, 1.0);
        }
        IntoxicantesMod.LOGGER.info("[Escopeta] tiro: {} balim(s) em {} vitima(s) (ads={})",
                acertosTotais, feridos.size(), ads);

        // ==================================================== RECOIL: EMPURRA + KICK DE CAMERA
        float kickPitch = KICK_PITCH() * (ads ? 0.6F : 1.0F);
        float kickYaw = (KICK_YAW() + player.getRandom().nextFloat() * 2.0F - 1.0F)
                * (ads ? 0.5F : 1.0F);
        player.push(-olhando.x * 0.6, 0.18, -olhando.z * 0.6);
        player.syncVelocity = true;
        if (player instanceof ServerPlayer sp && sp.connection != null) {
            ServerPlayNetworking.send(sp, new RecuoPayload(kickPitch, kickYaw));
        }

        // durabilidade (quebra com som, igual vanilla)
        if (!player.getAbilities().instabuild && player instanceof ServerPlayer serverPlayer) {
            stack.hurtAndBreak(1, level, serverPlayer, sq ->
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 1.0F, 0.9F));
        }

        // ==================================================== PUMP AUTOMATICO + cooldown
        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS());
        if (estado.noTubo() > 0) {
            guardar(stack, new EscopetaEstado(estado.noTubo(), false,
                    TICKS_PUMP(), EscopetaEstado.FASE_BOMBA));
        }
    }

    // ==================================================== PAYLOAD S2C (kick de camera)

    /**
     * O servidor manda o recuo do tiro; o client aplica o kick com retorno
     * suave (a mira volta ao alvo — constituicao de atirador, nao sprayer).
     */
}
