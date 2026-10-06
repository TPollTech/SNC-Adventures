package com.intoxicantes;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
 * O revólver .38 — "três oitão" — NIVEL GUN MOD (v1.2.33), mesmo padrão da 12:
 *
 * - TAMBOR de 6 de verdade: cada buraco é um bit no DataComponent da stack;
 *   o tiro consome a CÂMARA alinhada com o cano e o tambor GIRA
 * - RELOAD shell-by-shell: SEGURA o botão direito; a cada intervalo entra 1
 *   cartucho .38 num buraco vago com "clac" próprio — solte quando quiser,
 *   o que entrou ficou; encheu o tambor, o ferrolho fecha sozinho
 * - AÇÃO SIMPLES: sem pump (o giro do tambor re-alinha o próximo); depois do
 *   tiro o ferrolho fecha com o "tuc" do fecho — e apertar o gatilho com a
 *   câmara vazia (mas tambor carregado) GIRA o tambor no seco, como um .38 de verdade
 * - RECOIL com KICK DE CÂMERA: payload S2C compartilhado com a 12 (RecuoPayload)
 * - ADS ao SEGURAR O BOTÃO DIREITO (v1.2.53): zoom de FOV (mesmo mixin da 12), dispersão menor,
 *   alcance maior e kick reduzido
 *
 * Munição: intoxicantes:cartucho_38 (chumbo + pólvora + latão). Mais preciso
 * e forte por bala que a 12, sem o espalhamento do cartucho grosso.
 */
public class RevolverItem extends Item {

    /** Componente que guarda o mecanismo (tambor/câmara/timer/fase) na stack. */
    public static final net.minecraft.core.component.DataComponentType<RevolverEstado> ESTADO =
            IntoxicantesMod.TIPO_ESTADO_REVOLVER;

    // ==================================================== CONFIG (calibrável em config/intoxicantes.json)
    private static float DANO() { return ModConfig.get().revolverDano; }
    private static double ALCANCE_MAXIMO() { return ModConfig.get().revolverAlcanceMaximo; }
    private static int COOLDOWN_TICKS() { return ModConfig.get().revolverCooldownTicks; }
    private static int TICKS_SHELL() { return ModConfig.get().revolverTicksPorShell; }
    private static int TICKS_FECHO() { return ModConfig.get().revolverTicksFecho; }
    private static float KICK_PITCH() { return ModConfig.get().revolverKickPitch; }
    private static float KICK_YAW() { return ModConfig.get().revolverKickYaw; }

    public RevolverItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> output,
            net.minecraft.world.item.TooltipFlag flag) {
        RevolverEstado estado = estado(stack);
        output.accept(Component.translatable("item.intoxicantes.revolver.dica"));
        output.accept(Component.translatable("item.intoxicantes.revolver.dicaADS"));
        output.accept(Component.translatable("item.intoxicantes.revolver.estado",
                estado.balas(), RevolverEstado.CAPACIDADE));
        super.appendHoverText(stack, context, display, output, flag);
    }

    // ==================================================== ESTADO DO MECANISMO

    /** Estado atual (VAZIA se o item veio de save/comando sem componente). */
    public static RevolverEstado estado(ItemStack stack) {
        RevolverEstado e = stack.get(ESTADO);
        return e == null ? RevolverEstado.VAZIA : e;
    }

    private static void guardar(ItemStack stack, RevolverEstado estado) {
        stack.set(ESTADO, estado);
    }

    /** Cartuchos .38 no inventário (−1 se criativo — não gasta mesmo). */
    public static int contarCartucho38(Player player) {
        if (player.getAbilities().instabuild) {
            return -1;
        }
        int total = 0;
        var inventario = player.getInventory();
        for (int i = 0; i < inventario.getContainerSize(); i++) {
            ItemStack slot = inventario.getItem(i);
            if (slot.is(IntoxicantesMod.CARTUCHO_38)) {
                total += slot.getCount();
            }
        }
        return total;
    }

    /** Gasta 1 cartucho .38 do inventário (criativo não gasta). */
    private static boolean consumirMunicao(Player player) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        var inventario = player.getInventory();
        for (int i = 0; i < inventario.getContainerSize(); i++) {
            ItemStack slot = inventario.getItem(i);
            if (slot.is(IntoxicantesMod.CARTUCHO_38)) {
                slot.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static void tocar(Level level, Player player, SoundEvent som,
            float volume, float pitch) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                som, SoundSource.PLAYERS, volume, pitch);
    }

    // ==================================================== USO (segurar botão direito = recarregar)

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.SPEAR; // braço travado à frente: operação manual
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity usuario) {
        // watchdog: 2.2s segurando = solta sozinho (6 buracos não pedem mais que isso)
        return 43;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // v1.2.57: O BOTÃO DIREITO NÃO ATIRA NEM RECARREGA. Disparo é o GATILHO
        // ESQUERDO (MouseBotaoMixin → ArmasClient.gatilhoPuxado, v1.2.53), giro
        // no seco INCLUI (o gatilho chama o mesmo caminho de tiro), e recarga é
        // a TECLA R (recarregarViaTecla, v1.2.41/48). A mira é estado do client
        // (isRightPressed) — não é item.use(). Só fica o gate da recarga por
        // tecla em curso.
        if (estado(stack).fase() == RevolverEstado.FASE_TECLA) {
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /**
     * v1.2.57 — O DISPARO pelo gatilho esquerdo (GatilhoPayload C2S): o mesmo
     * caminho que o botão direito executava — câmara carregada ATIRA (e o giro
     * no seco segue de brinde, o .38 de filme); sem bala nenhuma, click seco
     * com aviso. Validação 100% servidor (fase, munição, cooldown).
     */
    void atirarViaGatilho(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        RevolverEstado estado = estado(stack);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return; // em cooldown: silencioso, como o vanilla
        }
        if (estado.fase() == RevolverEstado.FASE_TECLA) {
            return; // recarga por tecla em curso
        }
        if (!(player.level() instanceof ServerLevel servidor)) {
            return;
        }
        // câmara carregada e mecanismo pronto = ATIRA
        if (estado.alinhadaCarregada() && estado.pronta()) {
            atirar(servidor, player, stack, estado);
            return;
        }
        // câmara seca mas o tambor tem bala em OUTRO buraco = o giro no seco
        if (estado.balas() > 0 && estado.pronta()) {
            RevolverEstado girado = estado.dispararEGirar();
            guardar(stack, new RevolverEstado(girado.tambores(), girado.camara(),
                    TICKS_FECHO(), RevolverEstado.FASE_FERRAMENTA));
            tocar(servidor, player, SoundEvents.LEVER_CLICK, 0.9F, 1.5F);
            return;
        }
        // tambor vazio: click seco de percussão + aviso na action bar
        tocar(servidor, player, SoundEvents.WOODEN_TRAPDOOR_CLOSE, 0.6F, 2.0F);
        player.sendOverlayMessage(Component.translatable("item.intoxicantes.revolver.semmunicao"));
    }

    // ==================================================== RECARGA PELA TECLA R (v1.2.41)

    /**
     * v1.2.41 — A tecla R no .38: a mesma recarga buraco a buraco do botão
     * direito, mas SEM travar a pose.
     * v1.2.48 — R de UM APERTO: um toque abre a janela e o mecanismo fecha
     * SOZINHO ao encher o tambor (ou acabar a reserva) — soltar R não fecha
     * mais. O servidor valida TUDO de novo (fase, buraco vago, reserva).
     * Chamado pelo RecargaPayload (C2S).
     */
    public static void recarregarViaTecla(Player player, InteractionHand mao, boolean pressionar) {
        ItemStack stack = player.getItemInHand(mao);
        RevolverEstado estado = estado(stack);

        // v1.2.48: soltar não interrompe mais — o fecho acontece sozinho pelo
        // motor (tambor cheio ou reserva acabou). O release da tecla é inerte.
        if (!pressionar) {
            return;
        }

        // apertou: só inicia com o mecanismo livre
        if (!estado.pronta() || !(player.level() instanceof ServerLevel servidor)) {
            return;
        }
        boolean temReserva = player.getAbilities().instabuild || contarCartucho38(player) > 0;
        if (estado.temBuracoVago() && temReserva) {
            // timer=1: a 1ª bala entra no próximo tick (resposta imediata)
            guardar(stack, new RevolverEstado(estado.tambores(), estado.camara(), 1,
                    RevolverEstado.FASE_TECLA));
            tocar(servidor, player, SoundEvents.ITEM_FRAME_ADD_ITEM, 0.7F, 0.8F);
            if (player instanceof ServerPlayer sp) {
                RecargaPayload.TipoStatus.mandar(sp, true, "revolver");
            }
        } else if (!estado.temBuracoVago()) {
            // tambor cheio: click seco de percussão
            tocar(servidor, player, SoundEvents.WOODEN_TRAPDOOR_CLOSE, 0.5F, 2.0F);
        }
    }

    // ==================================================== RECARREGANDO (shell-by-shell)

    @Override
    public void onUseTick(Level level, LivingEntity usuario, ItemStack stack, int restante) {
        if (level.isClientSide()) {
            return; // o "clac" nasce no servidor (não duplica)
        }
        if (!(usuario instanceof Player player)) {
            return;
        }
        RevolverEstado estado = estado(stack);
        if (estado.fase() != RevolverEstado.FASE_RECARREGANDO) {
            return;
        }
        ServerLevel servidor = (ServerLevel) level;

        // shells inseridos quando o tempo decorrido cruza cada marco:
        // o 1º entra em t=1 (resposta imediata), os demais a cada TICKS_SHELL
        int duracao = getUseDuration(stack, usuario);
        int decorrido = duracao - restante;
        int marcoAtual = decorrido <= 0 ? 0 : Math.min(1 + (decorrido - 1) / TICKS_SHELL(),
                RevolverEstado.CAPACIDADE);
        int marcoAnterior = decorrido <= 1 ? 0 : Math.min(1 + (decorrido - 2) / TICKS_SHELL(),
                RevolverEstado.CAPACIDADE);

        if (marcoAtual > marcoAnterior && estado.temBuracoVago()
                && (player.getAbilities().instabuild || consumirMunicao(player))) {
            // carrega o PRIMEIRO buraco vago a partir da câmara (da mesma forma
            // que um revólver de verdade é alimentado buraco a buraco)
            int buraco = primeiroBuracoVago(estado);
            RevolverEstado carregado = estado.carregar(buraco);
            if (!carregado.temBuracoVago() || !temMaisReserva(player)) {
                // encheu tudo (ou a reserva acabou): fecha o tambor na hora
                guardar(stack, fecharRecarga(carregado));
                tocar(servidor, player, SoundEvents.LEVER_CLICK, 0.8F, 1.2F);
                if (usuario.isUsingItem()) {
                    usuario.stopUsingItem();
                }
            } else {
                guardar(stack, carregado);
                tocar(servidor, player, SoundEvents.ITEM_FRAME_ADD_ITEM, 0.9F, 1.2F);
            }
        }
    }

    /** Primeiro buraco vago contando a partir da câmara (ordem de recarga real). */
    private static int primeiroBuracoVago(RevolverEstado estado) {
        for (int passo = 0; passo < RevolverEstado.CAPACIDADE; passo++) {
            int buraco = (estado.camara() + passo) % RevolverEstado.CAPACIDADE;
            if (!estado.cheio(buraco)) {
                return buraco;
            }
        }
        return estado.camara(); // tambor cheio (não deveria acontecer)
    }

    private static boolean temMaisReserva(Player player) {
        return player.getAbilities().instabuild || contarCartucho38(player) > 0;
    }

    /**
     * Fecha o tambor: fase PRONTA com o "tuc" do fecho do ferrolho — o timer
     * de FERRAMENTA é o som/pose do fechamento (o mecanismo fica ocupado um
     * instante, igual revolver de verdade).
     */
    private static RevolverEstado fecharRecarga(RevolverEstado estado) {
        return new RevolverEstado(estado.tambores(), estado.camara(),
                TICKS_FECHO(), RevolverEstado.FASE_FERRAMENTA);
    }

    /**
     * v1.2.48: avisa o client que a recarga por tecla FECHOU — o motor fecha
     * sozinho no esquema de 1 aperto (o HUD sai do verde na hora).
     */
    private static void avisarFechoDaTecla(Entity dono) {
        if (dono instanceof ServerPlayer sp) {
            RecargaPayload.TipoStatus.mandar(sp, false, "revolver");
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity usuario, int restante) {
        // soltou o botão: fecha a recarga — o que entrou no tambor, ficou
        if (!level.isClientSide()
                && estado(stack).fase() == RevolverEstado.FASE_RECARREGANDO) {
            guardar(stack, fecharRecarga(estado(stack)));
            if (level instanceof ServerLevel servidor && usuario instanceof Player player) {
                tocar(servidor, player, SoundEvents.LEVER_CLICK, 0.8F, 1.0F);
            }
        }
        return false;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity usuario) {
        // watchdog expirou (segurou até o fim): fecha a recarga também
        if (!level.isClientSide()
                && estado(stack).fase() == RevolverEstado.FASE_RECARREGANDO) {
            guardar(stack, fecharRecarga(estado(stack)));
        }
        return stack;
    }

    // ==================================================== FECHO DO FERROLHO (o motor do mecanismo)

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity dono,
            EquipmentSlot slot) {
        RevolverEstado estado = estado(stack);

        // v1.2.41: fase TECLA — balas entram no ritmo da recarga do botão
        // direito, sem travar pose; soltar R (ou encher/acabar a reserva) fecha
        // o tambor com o fecho
        if (estado.fase() == RevolverEstado.FASE_TECLA) {
            boolean semReserva = dono instanceof Player p
                    && !p.getAbilities().instabuild && contarCartucho38(p) <= 0;
            if (!estado.temBuracoVago() || semReserva) {
                guardar(stack, fecharRecarga(estado));
                if (dono instanceof Player player) {
                    tocar(level, player, SoundEvents.LEVER_CLICK, 0.8F, 1.0F);
                }
                avisarFechoDaTecla(dono);
                return;
            }
            if (estado.timer() <= 1) {
                if (dono instanceof Player player
                        && (player.getAbilities().instabuild || consumirMunicao(player))) {
                    int buraco = primeiroBuracoVago(estado);
                    guardar(stack, estado.carregar(buraco)
                            .comTimer(TICKS_SHELL()));
                    tocar(level, player, SoundEvents.ITEM_FRAME_ADD_ITEM, 0.9F, 1.2F);
                } else {
                    guardar(stack, fecharRecarga(estado));
                    avisarFechoDaTecla(dono);
                }
            } else {
                guardar(stack, estado.tictac());
            }
            return;
        }

        if (estado.fase() != RevolverEstado.FASE_FERRAMENTA || estado.timer() <= 0) {
            return;
        }
        if (estado.timer() > 1) {
            guardar(stack, estado.tictac());
            return;
        }
        // fecho completou: tambor travado, pronto pro gatilho
        guardar(stack, new RevolverEstado(estado.tambores(), estado.camara(), 0,
                RevolverEstado.FASE_PRONTA));
        if (dono instanceof Player player) {
            tocar(level, player, SoundEvents.LEVER_CLICK, 0.6F, 0.75F);
        }
    }

    // ==================================================== O TIRO

    private void atirar(ServerLevel level, Player player, ItemStack stack,
            RevolverEstado estado) {
        // consome a CÂMARA alinhada e GIRA o tambor pro próximo buraco
        RevolverEstado girado = estado.dispararEGirar();
        guardar(stack, new RevolverEstado(girado.tambores(), girado.camara(),
                TICKS_FECHO(), RevolverEstado.FASE_FERRAMENTA));

        Vec3 origem = player.getEyePosition();
        Vec3 olhando = player.getViewVector(1.0F);
        Vec3 boca = origem.add(olhando.scale(1.1));

        // estouro do .38: blast de foguete (corpo) + porta de ferro (meta) —
        // sem ogg proprietário, a combinação dá o "PA" seco do três-oitão
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.8F, 0.7F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 1.2F, 0.55F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, boca.x, boca.y, boca.z,
                6, 0.08, 0.08, 0.08, 0.02);
        level.sendParticles(ParticleTypes.FLAME, boca.x, boca.y, boca.z,
                3, 0.04, 0.04, 0.04, 0.01);
        // cápsula ejetada (latão girando pro lado direito)
        level.sendParticles(ParticleTypes.CRIT, boca.x, boca.y - 0.2, boca.z,
                2, 0.1, 0.05, 0.1, 0.1);

        // ADS: mira = dispersão menor (40%) + 30% mais alcance (v1.2.53: botão
        // direito — MiraPayload sincroniza)
        boolean ads = MiraPayload.estaMirando((ServerPlayer) player);
        float fatorDisp = ads ? 0.4F : 1.0F;
        double alcanceEfetivo = ads ? ALCANCE_MAXIMO() * 1.3 : ALCANCE_MAXIMO();

        // bala única hitscan: amostragem por CONE igual à da 12 (o sampler do
        // ProjectileUtil varre por células de chunk e deixava alvos finos
        // escorrerem — detector próprio), mas com UM projétil pesado
        java.util.List<LivingEntity> alvos = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(alcanceEfetivo + 4),
                a -> a != player && a.isAlive() && !a.isSpectator());
        double desvio = 0.03 * fatorDisp;
        Vec3 mira = origem.add(olhando.scale(alcanceEfetivo)).add(
                (player.getRandom().nextDouble() - 0.5) * 2 * desvio * alcanceEfetivo,
                (player.getRandom().nextDouble() - 0.5) * 2 * desvio * alcanceEfetivo
                        + alcanceEfetivo * 0.003 * fatorDisp,
                (player.getRandom().nextDouble() - 0.5) * 2 * desvio * alcanceEfetivo);
        Vec3 direcao = mira.subtract(origem).normalize();
        Vec3 fim = origem.add(direcao.scale(alcanceEfetivo + 1.5));

        // a bala ATRAVESSA vidro/pane (vitrine do mercado), igual à 12
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

        // cone da bala: 14 amostras; a vítima mais PRÓXIMA leva
        LivingEntity vitima = null;
        double melhorDist = Double.MAX_VALUE;
        for (double d = 0.8; d <= alcance; d += (alcance - 0.4) / 14.0) {
            Vec3 ponto = origem.add(direcao.scale(d));
            for (LivingEntity a : alvos) {
                double raio = a.getBbWidth() / 2.0 + 0.35 + d / alcanceEfetivo;
                if (a.getBoundingBox().inflate(raio).contains(ponto) && d < melhorDist) {
                    vitima = a;
                    melhorDist = d;
                }
            }
        }
        if (vitima != null) {
            // o .38 segura o calibre longe: falloff de 100% a 80% no limite
            double fator = Math.max(0.8, 1.0 - melhorDist / (alcanceEfetivo * 4.0));
            Chumbo.aplicar(level, vitima, player.damageSources().playerAttack(player),
                    (float) (DANO() * fator));
            // v1.2.41: o chute da bala pesada + o rastro do .38 (boca -> alvo)
            Chumbo.empurrar(vitima, player, 0.8 + DANO() * fator * 0.1);
            Chumbo.tracer(level, boca, vitima.position().add(0, vitima.getBbHeight() * 0.5, 0));
            level.playSound(null, vitima.getX(), vitima.getY(), vitima.getZ(),
                    IntoxicantesMod.BALIM_ACERTO, SoundSource.PLAYERS, 0.7F, 0.8F);
            level.sendParticles(ParticleTypes.NOTE,
                    vitima.getX(), vitima.getY() + vitima.getBbHeight() + 0.2, vitima.getZ(),
                    1, 0.2, 0.1, 0.2, 1.0);
        }
        IntoxicantesMod.LOGGER.info("[Revolver] tiro .38: alvo={} dist={} (ads={})",
                vitima == null ? "nenhum" : vitima.getName().getString(),
                vitima == null ? -1 : (float) melhorDist, ads);

        // ==================================================== RECOIL: EMPURRA + KICK DE CÂMERA
        float kickPitch = KICK_PITCH() * (ads ? 0.6F : 1.0F);
        float kickYaw = (KICK_YAW() + player.getRandom().nextFloat() * 2.0F - 1.0F)
                * (ads ? 0.5F : 1.0F);
        player.push(-olhando.x * 0.35, 0.12, -olhando.z * 0.35);
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

        // cooldown curto entre tiros (revólver em cascavel)
        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS());
    }
}
