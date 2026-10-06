package com.intoxicantes;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

public final class MoneyCommands {
    private MoneyCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            dispatcher.register(Commands.literal("saldo")
                    .requires(src -> src.isPlayer())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        int saldo = PlayerMoney.get(player);
                        player.sendSystemMessage(
                                Component.translatable("money.intoxicantes.saldo", saldo));
                        player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5F, 1.2F);
                        return Command.SINGLE_SUCCESS;
                    })
            );

            dispatcher.register(Commands.literal("pagar")
                    .requires(src -> src.isPlayer())
                    .then(Commands.argument("jogador", EntityArgument.player())
                            .then(Commands.argument("quantia", IntegerArgumentType.integer(1))
                                    .executes(ctx -> {
                                        ServerPlayer remetente = ctx.getSource().getPlayerOrException();
                                        ServerPlayer destinatario = EntityArgument.getPlayer(ctx, "jogador");
                                        int quantia = IntegerArgumentType.getInteger(ctx, "quantia");

                                        if (remetente == destinatario) {
                                            remetente.sendSystemMessage(
                                                    Component.translatable("money.intoxicantes.si_mesmo"));
                                            return 0;
                                        }
                                        if (!PlayerMoney.tem(remetente, quantia)) {
                                            remetente.sendSystemMessage(
                                                    Component.translatable("money.intoxicantes.insuficiente", PlayerMoney.get(remetente)));
                                            return 0;
                                        }

                                        PlayerMoney.subtrair(remetente, quantia);
                                        PlayerMoney.add(destinatario, quantia);

                                        remetente.sendSystemMessage(
                                                Component.translatable("money.intoxicantes.enviado", quantia, destinatario.getName()));
                                        destinatario.sendSystemMessage(
                                                Component.translatable("money.intoxicantes.recebido", quantia, remetente.getName()));

                                        remetente.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5F, 1.0F);
                                        destinatario.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5F, 1.0F);

                                        IntoxicantesMod.LOGGER.info("[Dinheiro] {} pagou R$ {} pra {}",
                                                remetente.getName().getString(), quantia, destinatario.getName().getString());
                                        return Command.SINGLE_SUCCESS;
                                    })
                            )
                    )
            );

            dispatcher.register(Commands.literal("darreal")
                    .requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.argument("jogador", EntityArgument.player())
                            .then(Commands.argument("quantia", IntegerArgumentType.integer(1))
                                    .executes(ctx -> {
                                        ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                                        int quantia = IntegerArgumentType.getInteger(ctx, "quantia");
                                        PlayerMoney.add(alvo, quantia);
                                        alvo.sendSystemMessage(
                                                Component.translatable("money.intoxicantes.do_admin", quantia));
                                        alvo.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                                        ctx.getSource().sendSuccess(
                                                () -> Component.translatable("money.intoxicantes.admin_deu", quantia, alvo.getName()),
                                                true);
                                        return Command.SINGLE_SUCCESS;
                                    })
                            )
                    )
            );

            dispatcher.register(Commands.literal("gagomarket")
                    .requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.literal("set")
                            .then(Commands.argument("x", IntegerArgumentType.integer())
                                    .then(Commands.argument("y", IntegerArgumentType.integer())
                                            .then(Commands.argument("z", IntegerArgumentType.integer())
                                                    .executes(ctx -> {
                                                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                        int x = IntegerArgumentType.getInteger(ctx, "x");
                                                        int y = IntegerArgumentType.getInteger(ctx, "y");
                                                        int z = IntegerArgumentType.getInteger(ctx, "z");
                                                        BlockPos pos = new BlockPos(x, y, z);
                                                        MarketSystem.setMarketPosComando(ctx.getSource().getServer(), pos);
                                                        player.sendSystemMessage(
                                                                Component.translatable("money.intoxicantes.market_set", x, y, z));
                                                        player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                                                        IntoxicantesMod.LOGGER.info("[Market] Posicao setada por {}: {} {} {}",
                                                                player.getName().getString(), x, y, z);
                                                        return Command.SINGLE_SUCCESS;
                                                    })
                                            )
                                    )
                            )
                    )
                    .then(Commands.literal("rebuild")
                            .executes(ctx -> {
                                // Estrutura gerada e imutavel: chunks antigos NUNCA atualizam.
                                // Este comando recoloca o template (letreiro novo, moldura,
                                // vidros) na posicao do mercado do save atual.
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                BlockPos p = MarketSystem.getMarketPos();
                                if (p == null) {
                                    player.sendSystemMessage(Component.translatable(
                                            "money.intoxicantes.market_nao_setado"));
                                    return 0;
                                }
                                ServerLevel level = ctx.getSource().getServer().overworld();
                                // v1.2.25: ergue a PELE persistida do save (classico/
                                // sertao/serra) — mundos velhos (sem regiao gravada)
                                // recebem o prédio branco original
                                String regiao = MarketSystem.getRegiaoMercado();
                                String sufixo = (regiao.isEmpty() || regiao.equals("classico"))
                                        ? "" : "_" + regiao;
                                var tpl = level.getStructureTemplateManager().get(
                                        net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                                IntoxicantesMod.MOD_ID, "mercado_gago" + sufixo));
                                if (tpl.isEmpty() && !sufixo.isEmpty()) {
                                    // pele gravada sem template (rollback?): cai pro clássico
                                    tpl = level.getStructureTemplateManager().get(
                                            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                                    IntoxicantesMod.MOD_ID, "mercado_gago"));
                                }
                                if (tpl.isEmpty()) {
                                    player.sendSystemMessage(Component.translatable(
                                            "money.intoxicantes.market_template_ausente"));
                                    return 0;
                                }
                                // remove Gagos antigos da area (senao o rebuild empilha um
                                // novo por cima do que ja esta gravado no chunk)
                                for (GagoEntity velho : level.getEntitiesOfClass(GagoEntity.class,
                                        net.minecraft.world.phys.AABB.ofSize(
                                                net.minecraft.world.phys.Vec3.atCenterOf(p), 24, 12, 24))) {
                                    velho.discard();
                                }

                                // marketPos e o CENTRO do predio; placeInWorld quer a ORIGEM
                                // (canto 0,0,0 do template). E a rotacao tem que ser a MESMA
                                // que a estrutura original nasceu (os offsets do Gago/persistidos
                                // so fazem sentido nela) — derivamos a Rotation dos offsets.
                                int px = MarketSystem.getPortaOffset().getX();
                                int pz = MarketSystem.getPortaOffset().getZ();
                                net.minecraft.world.level.block.Rotation rot =
                                        (px == 0 && pz > 0) ? net.minecraft.world.level.block.Rotation.NONE
                                        : (px < 0 && pz == 0) ? net.minecraft.world.level.block.Rotation.CLOCKWISE_90
                                        : (px == 0 && pz < 0) ? net.minecraft.world.level.block.Rotation.CLOCKWISE_180
                                        : net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
                                net.minecraft.core.Vec3i tam = tpl.get().getSize(rot);
                                BlockPos origem = new BlockPos(
                                        p.getX() - tam.getX() / 2,
                                        p.getY(),
                                        p.getZ() - tam.getZ() / 2);

                                var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings()
                                        .setRotation(rot)
                                        .setMirror(net.minecraft.world.level.block.Mirror.NONE)
                                        .setIgnoreEntities(false); // o Gago do template nasce junto
                                boolean ok = tpl.get().placeInWorld(level, origem, origem, settings,
                                        level.getRandom(), 2);
                                player.sendSystemMessage(Component.translatable(
                                        ok ? "money.intoxicantes.market_rebuild"
                                           : "money.intoxicantes.market_rebuild_parcial",
                                        origem.getX(), origem.getY(), origem.getZ()));
                                player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 0.8F);
                                IntoxicantesMod.LOGGER.info("[Market] rebuild executado por {} em {}",
                                        player.getName().getString(), p);
                                return Command.SINGLE_SUCCESS;
                            })
                    )
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        BlockPos p = MarketSystem.getMarketPos();
                        if (p != null) {
                            player.sendSystemMessage(
                                    Component.translatable("money.intoxicantes.market_pos", p.getX(), p.getY(), p.getZ()));
                        } else {
                            player.sendSystemMessage(
                                    Component.translatable("money.intoxicantes.market_nao_setado"));
                        }
                        return Command.SINGLE_SUCCESS;
                    })
            );

            // /depositar: transforma todo R$ do inventario em saldo virtual
            // v1.2.60: soma VALOR de nota (a nota de 2 vale 2, a de 200 vale 200)
            dispatcher.register(Commands.literal("depositar")
                    .requires(src -> src.isPlayer())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        var inventario = player.getInventory();
                        int total = RealItem.totalNoInventario(player);
                        if (total == 0) {
                            player.sendSystemMessage(
                                    Component.translatable("money.intoxicantes.sem_reais"));
                            return 0;
                        }
                        for (int i = 0; i < inventario.getContainerSize(); i++) {
                            ItemStack slot = inventario.getItem(i);
                            if (RealItem.eDinheiro(slot.getItem())) {
                                inventario.setItem(i, ItemStack.EMPTY);
                            }
                        }
                        PlayerMoney.add(player, total);
                        player.sendSystemMessage(
                                Component.translatable("money.intoxicantes.depositado", total, PlayerMoney.get(player)));
                        player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7F, 1.4F);
                        return Command.SINGLE_SUCCESS;
                    })
            );

            // /sacar <quantia>: transforma saldo virtual em R$ de inventario
            dispatcher.register(Commands.literal("sacar")
                    .requires(src -> src.isPlayer())
                    .then(Commands.argument("quantia", IntegerArgumentType.integer(1))
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                int pediu = IntegerArgumentType.getInteger(ctx, "quantia");
                                int saldo = PlayerMoney.get(player);
                                if (saldo <= 0) {
                                    player.sendSystemMessage(
                                            Component.translatable("money.intoxicantes.saldo_zerado"));
                                    return 0;
                                }
                                int quantia = Math.min(pediu, saldo);
                                PlayerMoney.subtrair(player, quantia);

                                // v1.2.60: entrega o TROCO CERTINHO em notas (greedy,
                                // da nota alta pra baixo — a moeda de 1 fecha qualquer
                                // valor exato; o que não couber cai no chao)
                                for (ItemStack pilha : RealItem.trocar(quantia)) {
                                    if (!player.getInventory().add(pilha)) {
                                        player.drop(pilha, false, net.minecraft.util.Prediction.PREDICTED);
                                    }
                                }
                                player.sendSystemMessage(
                                        Component.translatable("money.intoxicantes.sacado", quantia, PlayerMoney.get(player)));
                                player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7F, 1.1F);
                                return Command.SINGLE_SUCCESS;
                            })
                    )
            );
        });
    }
}
