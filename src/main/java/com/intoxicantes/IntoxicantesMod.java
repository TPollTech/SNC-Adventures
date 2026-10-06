package com.intoxicantes;

import java.text.Normalizer;
import java.io.File;
import java.util.List;
import java.util.Locale;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TintedParticleLeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.PlaySoundConsumeEffect;
import net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bebidas, drogas e PLANTACOES da esquina, tudo fabricado dentro do jogo.
 * Roleplay de mundo de GTA: planta, destila, gasta e se vira com a ressaca.
 */
public class IntoxicantesMod implements ModInitializer {
    public static final String MOD_ID = "intoxicantes";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // ============================================================ BLOCOS: CULTURAS
    // (declarados antes das sementes, que sao BlockItems deles)
    public static final Block MACONHA_PLANT = registerCropBlock("maconha_plant", true);
    public static final Block LOUPULO_PLANT = registerCropBlock("lupulo_plant", false);
    public static final Block UVA_PLANT = registerCropBlock("uva_plant", true);
    public static final net.minecraft.world.level.block.entity.BlockEntityType<ParreiraBlockEntity> PARREIRA_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceKey.create(Registries.BLOCK_ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(MOD_ID, "parreira")),
                    new net.minecraft.world.level.block.entity.BlockEntityType<>(
                            ParreiraBlockEntity::new, java.util.Set.of(UVA_PLANT)));
    public static final Block CAFE_PLANT = registerCropBlock("cafe_plant", false);
    public static final Block PAPOULA_PLANT = registerCropBlock("papoula_plant", false);

    // ============================================================ BLOCOS: COQUEIRO (v1.2.58)
    // A praia tem dono: tronco curvado, folhas e coco comível. O coco no pé
    // (CocoBlock) dropa o item; a AGUA_DE_COCO vira craft real.
    public static final Block COQUEIRO_TRONCO = registerBlockWithItem("coqueiro_tronco",
            new RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .strength(0.8F)
                    .sound(SoundType.WOOD)
                    .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "coqueiro_tronco")))));
    public static final Block COQUEIRO_FOLHAS = registerBlockWithItem("coqueiro_folhas",
            new TintedParticleLeavesBlock(0.3F, BlockBehaviour.Properties.of()
                    .strength(0.2F)
                    .randomTicks()
                    .sound(SoundType.GRASS)
                    .noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "coqueiro_folhas")))));
    public static final Block COCO_BLOCO = registerBlockWithItem("coco_bloco",
            new CocoBlock(BlockBehaviour.Properties.of()
                    .strength(0.5F)
                    .sound(SoundType.WOOD)
                    .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "coco_bloco")))));

    // ============================================================ BLOCOS: LAMPADA UV
    public static final Block LAMPADA_UV = registerBlockWithItem("lampada_uv", new LampadaUvBlock(
            BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(0.8F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> state.getValue(LampadaUvBlock.LIT) ? 15 : 0)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "lampada_uv")))));

    // ============================================================ BLOCOS: POSTE DE LUZ (v1.2.19)
    // O lampeão do estacionamento: acende sozinho às 19h, apaga às 5h.
    // noOcclusion: corpo fino (coluna 4x16x4) — sem isso o vizinho "some".
    // v1.2.24: a LUZ (14) mora só no TOPO aceso — a fonte é a luminária,
    // não a coluna (3 blocos iluminados “de graça” inflava o light engine).
    public static final Block POSTE_LUZ = registerBlockWithItem("poste_luz", new PosteLuzBlock(
            BlockBehaviour.Properties.of()
                    .strength(0.6F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(PosteLuzBlock.PARTE) == PosteLuzBlock.Parte.TOPO
                            && state.getValue(PosteLuzBlock.LIT) ? 14 : 0)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "poste_luz")))));

    // ============================================================ BLOCOS: LAMPADA LED (v1.2.68)
    // O tubo de LED convencional de teto do mercado — igual ao da vida real:
    // difusor branco leitoso, ponteiras de alumínio, suspenso por hastes.
    // SEMPRE aceso por enquanto (o playtest pediu iluminação de mercado);
    // o liga/desliga e a energia entram depois. noOcclusion: tubo fino não
    // pode fazer o vizinho "sumir".
    public static final Block LAMPADA_LED = registerBlockWithItem("lampada_led",
            new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .lightLevel(state -> 15)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "lampada_led")))));

    // ============================================================ BLOCOS: PRATELEIRA DE MERCADO (v1.2.69)
    // A gôndola do Esquinão: metal cinza, 3 prateleiras brancas, etiqueta
    // amarela. Vende/estoca QUALQUER item (9 slots genéricos no BE), cobra
    // R$ pelo preço de tabela do Gago e REABASTECE QUARTA-FEIRA (o dia de
    // entrega do CalendarioEsquinao — estilo My Summer Car). A estanteria é
    // o modelo 3D; os produtos são desenhados pelo BER (client). noOcclusion:
    // a caixa de colisão não fecha o salão.
    public static final Block PRATELEIRA_MERCADO = registerBlockWithItem("prateleira_mercado",
            new PrateleiraMercadoBlock(BlockBehaviour.Properties.of()
                    .strength(1.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "prateleira_mercado")))));
    public static final net.minecraft.world.level.block.entity.BlockEntityType<PrateleiraMercadoBlockEntity> PRATELEIRA_MERCADO_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceKey.create(Registries.BLOCK_ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(MOD_ID, "prateleira_mercado")),
                    new net.minecraft.world.level.block.entity.BlockEntityType<>(
                            PrateleiraMercadoBlockEntity::new, java.util.Set.of(PRATELEIRA_MERCADO)));

    public static final Block CAIXA_MERCADO = registerBlockWithItem("caixa_mercado",
            new CaixaMercadoBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F).sound(SoundType.METAL).noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "caixa_mercado")))));
    public static final net.minecraft.world.level.block.entity.BlockEntityType<CaixaMercadoBlockEntity> CAIXA_MERCADO_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceKey.create(Registries.BLOCK_ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(MOD_ID, "caixa_mercado")),
                    new net.minecraft.world.level.block.entity.BlockEntityType<>(
                            CaixaMercadoBlockEntity::new, java.util.Set.of(CAIXA_MERCADO)));

    // ============================================================ BLOCOS: LÂMPADAS LED POR POTÊNCIA (v1.2.70)
    // A família 5W → 200W igual à vida real (bulbo residencial, refletor,
    // high-bay industrial). UMA classe, DEZ IDs (padrão dos barris): a
    // potência mora no registro (lampada_led_50w → 50) e a LUZ ESCALA COM O
    // WATT (7 → 15). O clique LIGA/DESLIGA (o que ficou pra depois na
    // 1.2.68); a lâmpada morta é eterna (manutenção = craftar outra).
    public static final Block LAMPADA_LED_5W = registerLedPotencia("lampada_led_5w");
    public static final Block LAMPADA_LED_9W = registerLedPotencia("lampada_led_9w");
    public static final Block LAMPADA_LED_12W = registerLedPotencia("lampada_led_12w");
    public static final Block LAMPADA_LED_15W = registerLedPotencia("lampada_led_15w");
    public static final Block LAMPADA_LED_20W = registerLedPotencia("lampada_led_20w");
    public static final Block LAMPADA_LED_30W = registerLedPotencia("lampada_led_30w");
    public static final Block LAMPADA_LED_50W = registerLedPotencia("lampada_led_50w");
    public static final Block LAMPADA_LED_100W = registerLedPotencia("lampada_led_100w");
    public static final Block LAMPADA_LED_150W = registerLedPotencia("lampada_led_150w");
    public static final Block LAMPADA_LED_200W = registerLedPotencia("lampada_led_200w");

    /** A família inteira (o registro da BE e o gametest usam). */
    public static final java.util.List<Block> LAMPADAS_POTENCIA = java.util.List.of(
            LAMPADA_LED_5W, LAMPADA_LED_9W, LAMPADA_LED_12W, LAMPADA_LED_15W,
            LAMPADA_LED_20W, LAMPADA_LED_30W, LAMPADA_LED_50W, LAMPADA_LED_100W,
            LAMPADA_LED_150W, LAMPADA_LED_200W);

    public static final net.minecraft.world.level.block.entity.BlockEntityType<LedPotenciaBlockEntity> LAMPADA_LED_POTENCIA_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceKey.create(Registries.BLOCK_ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(MOD_ID, "lampada_led_potencia")),
                    new net.minecraft.world.level.block.entity.BlockEntityType<>(
                            LedPotenciaBlockEntity::new,
                            java.util.Set.copyOf(LAMPADAS_POTENCIA)));

    // ============================================================ ELETRICIDADE (v1.2.70)
    // A INSTALAÇÃO ELÉTRICA do Esquinão — distribuição/proteção/controle da
    // energia do SNC ENERGIES (que é a FONTE; adapter por reflexão em
    // energia.SNCEnergiesAdapter). Cabo passivo (sem ticker), soquete de
    // teto (a lâmpada LED da família vira consumidor real), interruptor de
    // parede e o quadro com disjuntores. Nomes reais: bitola 2,5 mm².
    public static final Block CABO_COBRE_2_5MM = registerBlockWithItem("cabo_cobre_2_5mm",
            new CaboEletricoBlock(BlockBehaviour.Properties.of()
                    .strength(0.3F)
                    .sound(SoundType.COPPER)
                    .noOcclusion()
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "cabo_cobre_2_5mm"))),
                    2.5));
    public static final Block SOQUETE_TETO = registerBlockWithItem("soquete_teto",
            new SoqueteTetoBlock(BlockBehaviour.Properties.of()
                    .strength(0.5F)
                    .sound(SoundType.STONE)
                    .noOcclusion()
                    .lightLevel(estado -> estado.getValue(SoqueteTetoBlock.ACESA)
                            ? estado.getValue(SoqueteTetoBlock.BRILHO) : 0)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "soquete_teto")))));
    public static final net.minecraft.world.level.block.entity.BlockEntityType<SoqueteTetoBlock.SoqueteBlockEntity> SOQUETE_TETO_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceKey.create(Registries.BLOCK_ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(MOD_ID, "soquete_teto")),
                    new net.minecraft.world.level.block.entity.BlockEntityType<>(
                            SoqueteTetoBlock.SoqueteBlockEntity::new, java.util.Set.of(SOQUETE_TETO)));
    public static final Block INTERRUPTOR_SIMPLES = registerBlockWithItem("interruptor_simples",
            new InterruptorSimplesBlock(BlockBehaviour.Properties.of()
                    .strength(0.5F)
                    .sound(SoundType.STONE)
                    .noOcclusion()
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "interruptor_simples")))));
    public static final Block QUADRO_ELETRICO = registerBlockWithItem("quadro_eletrico",
            new QuadroEletricoBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F, 4.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "quadro_eletrico")))));
    public static final net.minecraft.world.level.block.entity.BlockEntityType<QuadroEletricoBlock.QuadroEletricoBlockEntity> QUADRO_ELETRICO_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceKey.create(Registries.BLOCK_ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(MOD_ID, "quadro_eletrico")),
                    new net.minecraft.world.level.block.entity.BlockEntityType<>(
                            QuadroEletricoBlock.QuadroEletricoBlockEntity::new, java.util.Set.of(QUADRO_ELETRICO)));
    // v1.2.71 (módulo 4): a TOMADA DE PAREDE — o ponto de plug da instalação.
    public static final Block TOMADA = registerBlockWithItem("tomada",
            new TomadaBlock(BlockBehaviour.Properties.of()
                    .strength(0.5F)
                    .sound(SoundType.STONE)
                    .noOcclusion()
                    .lightLevel(estado -> estado.getValue(TomadaBlock.ENERGIZADA) ? 3 : 0)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "tomada")))));
    public static final net.minecraft.world.level.block.entity.BlockEntityType<TomadaBlock.TomadaBlockEntity> TOMADA_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceKey.create(Registries.BLOCK_ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(MOD_ID, "tomada")),
                    new net.minecraft.world.level.block.entity.BlockEntityType<>(
                            TomadaBlock.TomadaBlockEntity::new, java.util.Set.of(TOMADA)));
    // v1.2.71 (módulo 5): o MULTÍMETRO do eletricista.
    public static final Item MULTIMETRO = registerItem("multimetro",
            new MultimetroItem(new Item.Properties().stacksTo(1)
                    .setId(itemKey("multimetro"))));
    // v1.2.71 (módulo 5): as BITOLAS da instalação (2,5 já existia).
    public static final Block CABO_COBRE_1_5MM = registerBlockWithItem("cabo_cobre_1_5mm",
            new CaboEletricoBlock(BlockBehaviour.Properties.of()
                    .strength(0.3F)
                    .sound(SoundType.COPPER)
                    .noOcclusion()
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "cabo_cobre_1_5mm"))),
                    1.5));
    public static final Block CABO_COBRE_4MM = registerBlockWithItem("cabo_cobre_4mm",
            new CaboEletricoBlock(BlockBehaviour.Properties.of()
                    .strength(0.3F)
                    .sound(SoundType.COPPER)
                    .noOcclusion()
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "cabo_cobre_4mm"))),
                    4.0));
    public static final Block CABO_COBRE_6MM = registerBlockWithItem("cabo_cobre_6mm",
            new CaboEletricoBlock(BlockBehaviour.Properties.of()
                    .strength(0.3F)
                    .sound(SoundType.COPPER)
                    .noOcclusion()
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "cabo_cobre_6mm"))),
                    6.0));
    public static final Block CABO_COBRE_10MM = registerBlockWithItem("cabo_cobre_10mm",
            new CaboEletricoBlock(BlockBehaviour.Properties.of()
                    .strength(0.3F)
                    .sound(SoundType.COPPER)
                    .noOcclusion()
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "cabo_cobre_10mm"))),
                    10.0));

    // v1.2.75: o CABO SUSPENSO (ponto a ponto, no estilo Immersive
    // Engineering). O CONECTOR se prende na face de um bloco da instalação;
    // a BOBINA estende um elo de cobre pelo vão (até 32 blocos, curva
    // pendente) e o elo entra na mesma travessia do quadro — a bitola
    // continua sendo o limite de corrente do trecho.
    public static final Block CONECTOR_ELETRICO = registerBlockWithItem("conector_eletrico",
            new ConectorEletricoBlock(BlockBehaviour.Properties.of()
                    .strength(0.4F)
                    .sound(SoundType.COPPER)
                    .noOcclusion()
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "conector_eletrico")))));
    /** A origem pendurada na bobina (primeiro clique; some ao estender). */
    public static final DataComponentType<com.intoxicantes.energia.EloCabo.Extremo> TIPO_ORIGEM_ELO =
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                    ResourceKey.create(Registries.DATA_COMPONENT_TYPE,
                            Identifier.fromNamespaceAndPath(MOD_ID, "origem_elo")),
                    DataComponentType.<com.intoxicantes.energia.EloCabo.Extremo>builder()
                            .persistent(com.intoxicantes.energia.EloCabo.Extremo.CODEC)
                            .networkSynchronized(com.intoxicantes.energia.EloCabo.Extremo.STREAM_CODEC)
                            .build());
    public static final Item BOBINA_COBRE_1_5MM = registrarBobina(1.5);
    public static final Item BOBINA_COBRE_2_5MM = registrarBobina(2.5);
    public static final Item BOBINA_COBRE_4MM = registrarBobina(4.0);
    public static final Item BOBINA_COBRE_6MM = registrarBobina(6.0);
    public static final Item BOBINA_COBRE_10MM = registrarBobina(10.0);

    /** Uma bobina por bitola (mesmo nome de série do cabo de bloco). */
    private static Item registrarBobina(double bitola) {
        String id = "bobina_cobre_" + (bitola == 1.5 ? "1_5mm"
                : bitola == 2.5 ? "2_5mm"
                : bitola == 4.0 ? "4mm"
                : bitola == 6.0 ? "6mm" : "10mm");
        return registerItem(id, new BobinaCaboItem(new Item.Properties().stacksTo(16)
                .setId(itemKey(id)), bitola));
    }

    /**
     * O registro da família: um bloco por potência (mesma classe), com a
     * luz da potência gravada nas propriedades (o getLight lê o registro;
     * o JSON nasce pelo gerador).
     */
    private static Block registerLedPotencia(String name) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath(MOD_ID, name));
        // os watts saem do PRÓPRIO ID (lampada_led_50w → 50): a fonte única
        // é o registro, e o construtor grava o default com o wattage certo
        int watts = Integer.parseInt(name.substring("lampada_led_".length(),
                name.length() - 1));
        Block bloco = new LedPotenciaBlock(BlockBehaviour.Properties.of()
                .strength(0.5F)
                .sound(SoundType.GLASS)
                .noOcclusion()
                // a luz lê o LIT do estado e os watts do construtor — nunca
                // o WATTAGE herdado por colisão de propriedade (o colador do
                // NBT/estrutura não sabe de watts)
                .lightLevel(estado -> estado.getValue(LedPotenciaBlock.LIT)
                        ? LedPotenciaBlock.luzPorWatts(watts) : 0)
                .setId(blockKey), watts);
        Registry.register(BuiltInRegistries.BLOCK, blockKey, bloco);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(MOD_ID, name));
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                new LedPotenciaBlock.ItemLedPotencia(bloco,
                        new Item.Properties().setId(itemKey)));
        return bloco;
    }

    // v1.2.19: ASFALTO do estacionamento (piso denso da esquina — o pátio do
    // mercado nasce pavimentado; o bloco fica disponível pra construir rua)
    public static final Block ASFALTO = registerBlockWithItem("asfalto",
            BlockBehaviour.Properties.of()
                    .strength(1.2F)
                    .sound(SoundType.STONE));

    // ============================================================ BLOCOS: DECORACAO DA ESQUINA (v1.2.24)
    // FAIXA DE PEDESTRE: a tinta branca da travessia — bloco PLANO (1px) sobre
    // o asfalto (anda por cima sem degrau); some se o chão ceder.
    public static final Block FAIXA_PEDESTRE = registerBlockWithItem("faixa_pedestre",
            new FaixaPedestreBlock(BlockBehaviour.Properties.of()
                    .strength(0.8F)
                    .sound(SoundType.STONE)
                    .noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_GRAY)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "faixa_pedestre")))));

    // HIDRANTE: o vermelhão de ferro na calçada — decorativo, com jato de
    // água cômico ao usar (a esquina inteira é interativa).
    public static final Block HIDRANTE = registerBlockWithItem("hidrante",
            new HidranteBlock(BlockBehaviour.Properties.of()
                    .strength(1.0F, 4.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_RED)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "hidrante")))));

    // ============================================================ BLOCOS: MOBÍLIA DE SERVIÇO DO SALÃO (v1.2.78)
    // Regra do usuário: o enfeite do mercado NÃO é improviso com bloco vanilla.
    // Engradado de estoque, bebedouro e máquina de lavar são BLOCOS DO MOD,
    // modelados em 3D e pintados em 128 por tools/gen_mobilia_mercado.py.
    // O ENGRADADO é o caixote ripado com tampa de travessas, cinta, cantoneiras
    // e etiqueta (empilhável — as pilhas do salão são o mesmo bloco). O BEBEDOURO
    // e a LAVADORA são peças COM FRENTE: usam BlocoOrientadoBlock, o gerador
    // grava as 4 variantes giradas e o blockstate escolhe pela `facing`.
    public static final Block ENGRADADO_MERCADO = registerBlockWithItem("engradado_mercado",
            new Block(BlockBehaviour.Properties.of()
                    .strength(0.8F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BROWN)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "engradado_mercado")))));

    // Forma única e segura: envolve o gabinete, o nicho, as duas torneiras e a
    // bandeja — quem dá de cara com o bloco não precisa acertar
    // um cubinho de 0.5 para interagir.
    public static final Block BEBEDOURO_MERCADO = registerBlockWithItem("bebedouro_mercado",
            new BlocoOrientadoBlock(BlockBehaviour.Properties.of()
                    .strength(1.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_GRAY)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "bebedouro_mercado"))),
                    net.minecraft.world.level.block.Block.box(2.8, 0.0, 2.3, 13.2, 16.0, 13.3),
                    net.minecraft.world.level.block.Block.box(2.8, 0.0, 2.3, 13.2, 16.0, 13.3),
                    net.minecraft.world.level.block.Block.box(2.8, 0.0, 2.3, 13.2, 16.0, 13.3),
                    net.minecraft.world.level.block.Block.box(2.8, 0.0, 2.3, 13.2, 16.0, 13.3)));

    // O GARRAFÃO DE 20L: o bloco de CIMA do bebedouro de garrafão. SEM
    // `facing` de propósito — é um cilindro, gira igual em qualquer lado, e
    // assim o jogador nunca deixa o garrafão "de costas" em cima da base.
    public static final Block BEBEDOURO_GARRAFAO = registerBlockWithItem("bebedouro_garrafao",
            new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_BLUE)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "bebedouro_garrafao")))));

    // Carcaça cheia em planta (o vidro da porta fica DENTRO do volume, visível
    // pelo tambor vazado do modelo). A forma recua 0.9 dos lados e 0.6 na frente
    // para o aro da porta não atravessar a quina do bloco vizinho. MapColor:
    // esta versão só tem 15 cores e não expõe branco puro — cinza-claro é o
    // mais próximo de um eletro domestico.
    public static final Block LAVADORA_MERCADO = registerBlockWithItem("lavadora_mercado",
            new BlocoOrientadoBlock(BlockBehaviour.Properties.of()
                    .strength(1.6F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_GRAY)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "lavadora_mercado"))),
                    net.minecraft.world.level.block.Block.box(.9, 0.0, .0, 15.1, 16.0, 15.4),
                    net.minecraft.world.level.block.Block.box(.9, 0.0, .0, 15.1, 16.0, 15.4),
                    net.minecraft.world.level.block.Block.box(.9, 0.0, .0, 15.1, 16.0, 15.4),
                    net.minecraft.world.level.block.Block.box(.9, 0.0, .0, 15.1, 16.0, 15.4)));

    // ============================================================ BLOCOS: PORTA-GRADE DO ESQUINÃO (v1.2.51)
    // A porta do guichê: de madrugada o guichê FECHA (colisão plena no vão
    // embaixo) e o Gago atende POR TRÁS da grade de ferro — de dia abre
    // (passagem livre). A virada é do Zelador do mercado (MarketSystem).
    public static final Block PORTA_GRADE = registerBlockWithItem("porta_grade",
            new PortaGradeBlock(BlockBehaviour.Properties.of()
                    .strength(1.2F, 4.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BROWN)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "porta_grade")))));

    // ============================================================ BLOCOS: DESTILARIA (v1.2.50)
    // A cadeia das bebidas: máquinas de prima, fermentação, destilação e
    // maturação — CADA bebida com o SEU barril (spec 6/8: identidade visual
    // própria, 1 classe + 4 IDs, como os signs do vanilla). Processos em
    // ProcessosBebida; o BE de barril é ÚNICO e válido pros 4 blocos.
    public static final Block BARRIL_CACHACA = registerBarril("barril_cachaca", "cachaca");
    public static final Block BARRIL_CERVEJA = registerBarril("barril_cerveja", "cerveja");
    public static final Block BARRIL_RUM = registerBarril("barril_rum", "rum");
    public static final Block BARRIL_VINHO = registerBarril("barril_vinho", "vinho");
    public static final net.minecraft.world.level.block.entity.BlockEntityType<BarrilBebidaBlockEntity> BARRIL_BEBIDA_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceKey.create(Registries.BLOCK_ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(MOD_ID, "barril_bebida")),
                    new net.minecraft.world.level.block.entity.BlockEntityType<>(
                            BarrilBebidaBlockEntity::new,
                            java.util.Set.of(BARRIL_CACHACA, BARRIL_CERVEJA, BARRIL_RUM, BARRIL_VINHO)));

    public static final Block DORNA_BEBIDA = registerBlockWithItem("dorna_bebida", new DornaBebidaBlock(
            BlockBehaviour.Properties.of()
                    .strength(1.2F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "dorna_bebida")))));
    public static final net.minecraft.world.level.block.entity.BlockEntityType<DornaBebidaBlockEntity> DORNA_BEBIDA_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceKey.create(Registries.BLOCK_ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(MOD_ID, "dorna_bebida")),
                    new net.minecraft.world.level.block.entity.BlockEntityType<>(
                            DornaBebidaBlockEntity::new, java.util.Set.of(DORNA_BEBIDA)));

    public static final Block ALAMBIQUE = registerBlockWithItem("alambique", new AlambiqueBlock(
            BlockBehaviour.Properties.of()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.COPPER)
                    .noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_ORANGE)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "alambique")))));
    public static final net.minecraft.world.level.block.entity.BlockEntityType<AlambiqueBlockEntity> ALAMBIQUE_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceKey.create(Registries.BLOCK_ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(MOD_ID, "alambique")),
                    new net.minecraft.world.level.block.entity.BlockEntityType<>(
                            AlambiqueBlockEntity::new, java.util.Set.of(ALAMBIQUE)));

    public static final Block MOENDA_CANA = registerBlockWithItem("moenda_cana", new MaquinaPrimaBlock(
            BlockBehaviour.Properties.of()
                    .strength(1.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "moenda_cana"))),
            MaquinaPrimaBlock.Tipo.MOENDA));
    public static final net.minecraft.world.level.block.entity.BlockEntityType<MaquinaPrimaBlockEntity> MOENDA_CANA_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceKey.create(Registries.BLOCK_ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(MOD_ID, "moenda_cana")),
                    new net.minecraft.world.level.block.entity.BlockEntityType<>(
                            (pos, state) -> new MaquinaPrimaBlockEntity(pos, state,
                                    MaquinaPrimaBlock.Tipo.MOENDA),
                            java.util.Set.of(MOENDA_CANA)));

    public static final Block PRENSA_UVAS = registerBlockWithItem("prensa_uvas", new MaquinaPrimaBlock(
            BlockBehaviour.Properties.of()
                    .strength(1.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "prensa_uvas"))),
            MaquinaPrimaBlock.Tipo.PRENSA));
    public static final net.minecraft.world.level.block.entity.BlockEntityType<MaquinaPrimaBlockEntity> PRENSA_UVAS_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceKey.create(Registries.BLOCK_ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(MOD_ID, "prensa_uvas")),
                    new net.minecraft.world.level.block.entity.BlockEntityType<>(
                            (pos, state) -> new MaquinaPrimaBlockEntity(pos, state,
                                    MaquinaPrimaBlock.Tipo.PRENSA),
                            java.util.Set.of(PRENSA_UVAS)));

    public static final Block CALDEIRAO_MOSTURA = registerBlockWithItem("caldeirao_mostura", new MaquinaPrimaBlock(
            BlockBehaviour.Properties.of()
                    .strength(2.0F)
                    .sound(net.minecraft.world.level.block.SoundType.METAL)
                    .noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "caldeirao_mostura"))),
            MaquinaPrimaBlock.Tipo.CALDEIRAO));
    public static final net.minecraft.world.level.block.entity.BlockEntityType<MaquinaPrimaBlockEntity> CALDEIRAO_MOSTURA_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceKey.create(Registries.BLOCK_ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(MOD_ID, "caldeirao_mostura")),
                    new net.minecraft.world.level.block.entity.BlockEntityType<>(
                            (pos, state) -> new MaquinaPrimaBlockEntity(pos, state,
                                    MaquinaPrimaBlock.Tipo.CALDEIRAO),
                            java.util.Set.of(CALDEIRAO_MOSTURA)));

    // ============================================================ GUI DAS MÁQUINAS
    // v1.2.59: UM MenuType pros SEIS tipos de máquina — o TipoMaquina viaja no
    // payload de abertura (ExtendedMenuType do fabric-menu-api-v1) e a tela do
    // client reabre com a mesma geometria (MenuMaquinaSNC.reabrir). O conteúdo
    // dos slots e o ContainerData (progresso REAL) são sync vanilla.
    public static final net.fabricmc.fabric.api.menu.v1.ExtendedMenuType<MenuMaquinaSNC, Integer> MENU_MAQUINA_SNC =
            Registry.register(BuiltInRegistries.MENU,
                    ResourceKey.create(Registries.MENU,
                            Identifier.fromNamespaceAndPath(MOD_ID, "maquina_snc")),
                    new net.fabricmc.fabric.api.menu.v1.ExtendedMenuType<>(
                            (id, inv, tipoOrdinal) -> MenuMaquinaSNC.reabrir(id, inv, tipoOrdinal),
                            net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    // ============================================================ CROP: CEVADA
    // v1.2.50: crop vanilla-style (7 estágios) — a matéria-prima da cerveja.
    public static final Block CEVADA_PLANT = Registry.register(BuiltInRegistries.BLOCK,
            ResourceKey.create(Registries.BLOCK,
                    Identifier.fromNamespaceAndPath(MOD_ID, "cevada_plant")),
            new CevadaCropBlock(BlockBehaviour.Properties.of()
                    .noCollision()
                    .randomTicks()
                    .instabreak()
                    .sound(SoundType.CROP)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "cevada_plant")))));

    // ============================================================ BLOCOS: LETREIRO DO ESQUINAO
    // v1.2.18: a placa DO ZERO — painel preto com texto verde de LED, renderizado
    // por código (PlacaEsquinaoRenderer). Nada de wall_sign vanilla na fachada.
    public static final Block PLACA_ESQUINAO = registerBlockWithItem("placa_esquinao", new PlacaEsquinaoBlock(
            BlockBehaviour.Properties.of()
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "placa_esquinao")))));
    // Block entity do letreiro (texto + vínculo com o mercado); valida só contra
    // o bloco da placa (colunas e painel compartilham o mesmo bloco/BE)
    public static final net.minecraft.world.level.block.entity.BlockEntityType<PlacaEsquinaoBlockEntity> PLACA_ESQUINAO_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceKey.create(Registries.BLOCK_ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(MOD_ID, "placa_esquinao")),
                    new net.minecraft.world.level.block.entity.BlockEntityType<>(
                            PlacaEsquinaoBlockEntity::new, java.util.Set.of(PLACA_ESQUINAO)));

    // ============================================================ BLOCOS: PAINEL DE LED CRAFTÁVEL
    // v1.2.36 — a TV de tela plana do Esquinão: painel FINO (3px) que o
    // jogador crafta e programa pela CENTRAL DE COMANDO (texto, cor, brilho,
    // modo). Suporta linha de até 3 (o painel-cabeça manda o texto).
    public static final Block PAINEL_LED = registerBlockWithItem("painel_led", new PainelLedBlock(
            BlockBehaviour.Properties.of()
                    .strength(1.0F, 4.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "painel_led")))));
    public static final net.minecraft.world.level.block.entity.BlockEntityType<PainelLedBlockEntity> PAINEL_LED_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceKey.create(Registries.BLOCK_ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(MOD_ID, "painel_led")),
                    new net.minecraft.world.level.block.entity.BlockEntityType<>(
                            PainelLedBlockEntity::new, java.util.Set.of(PAINEL_LED)));

    // ============================================================ ITENS DE COMANDO
    // v1.2.38: o CONTROLE REMOTO do painel de LED — aponta pro display e
    // edita (texto, cor, brilho, modo) sem tocar no bloco
    public static final Item CENTRAL_COMANDO = registerItem("central_comando",
            new CentralComandoItem(new Item.Properties().stacksTo(1)
                    .setId(itemKey("central_comando"))));

    /** Bebida exposta: conserva o mesmo item, apoiado em mesas ou outras superfícies. */
    public static final EntityType<BebidaDecorativaEntity> BEBIDA_DECORATIVA =
            registerEntity("bebida_decorativa",
                    EntityType.Builder.of(BebidaDecorativaEntity::new, MobCategory.MISC)
                            .sized(BebidaDecorativaEntity.LARGURA, BebidaDecorativaEntity.ALTURA)
                            .clientTrackingRange(8)
                            .updateInterval(20)
                            .noLootTable());

    /** Cada cacho usa uma hitbox temporária; os frutos são salvos na raiz. */
    public static final EntityType<CachoParreiraEntity> CACHO_PARREIRA =
            registerEntity("cacho_parreira",
                    EntityType.Builder.of(CachoParreiraEntity::new, MobCategory.MISC)
                            .sized(CachoParreiraEntity.LARGURA, CachoParreiraEntity.ALTURA)
                            .clientTrackingRange(8)
                            .updateInterval(20)
                            .noSave()
                            .noLootTable());

    // ============================================================ ENTIDADES: NPCs
    // O Traficante: vendedor ambulante que aparece de vez em quando.
    public static final EntityType<TraficanteEntity> TRAFICANTE = registerEntity("traficante",
            EntityType.Builder.of(TraficanteEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .updateInterval(2));
    // O Gago: vende bebidas. CHAMA ELE DE GAGO NO CHAT E VOCE VAI VER kkkk
    public static final EntityType<GagoEntity> GAGO = registerEntity("gago",
            EntityType.Builder.of(GagoEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .updateInterval(2));
    // v1.2.39: O JUÇA — o parça do Gago. Fuma Camel, veste o Matanza e tem
    // tema de entrada (o riff toca na aproximação). Cachaça nele = show.
    public static final EntityType<JucelinoEntity> JUCA = registerEntity("juca",
            EntityType.Builder.of(JucelinoEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .updateInterval(2));

    public static final EntityType<PeruEntity> PERU = registerEntity("peru",
            EntityType.Builder.of(PeruEntity::new, MobCategory.CREATURE)
                    .sized(.6F, 1.95F).clientTrackingRange(10).updateInterval(2));
    public static final EntityType<MotoPeruEntity> MOTO_PERU = registerEntity("moto_peru",
            EntityType.Builder.of(MotoPeruEntity::new, MobCategory.MISC)
                    .sized(2.2F, 1.35F).clientTrackingRange(10).updateInterval(2));

    // Ovos de spawn: SpawnEggItem le a entidade do componente ENTITY_DATA
    public static final Item OVO_TRAFICANTE = registerItem("ovo_traficante",
            new SpawnEggItem(new Item.Properties().stacksTo(64)
                    .setId(itemKey("ovo_traficante"))
                    .component(DataComponents.ENTITY_DATA,
                            TypedEntityData.of(TRAFICANTE, new CompoundTag()))));
    public static final Item OVO_GAGO = registerItem("ovo_gago",
            new SpawnEggItem(new Item.Properties().stacksTo(64)
                    .setId(itemKey("ovo_gago"))
                    .component(DataComponents.ENTITY_DATA,
                            TypedEntityData.of(GAGO, new CompoundTag()))));
    // v1.2.39: o ovo do Juça (base preta, mancha amarela-Camel)
    public static final Item OVO_JUCA = registerItem("ovo_juca",
            new SpawnEggItem(new Item.Properties().stacksTo(64)
                    .setId(itemKey("ovo_juca"))
                    .component(DataComponents.ENTITY_DATA,
                            TypedEntityData.of(JUCA, new CompoundTag()))));

    // ============================================================ SONS PROPRIOS
    // O estouro da 12 (a voz da escopeta), o blip do Gago e o zumbido da lampada.
    // Assets: assets/intoxicantes/sounds/*.ogg mapeados no sounds.json
    public static final SoundEvent SOCO_D12 = registrarSom("soco_d12");
    public static final SoundEvent VOZ_GAGO = registrarSom("voz_gago");
    public static final SoundEvent ZUMBIDO_UV = registrarSom("zumbido_uv");
    // v1.2.7: o "ca-ching" da venda e o "tum" do balim acertando
    public static final SoundEvent CAIXA_REGISTRADORA = registrarSom("caixa_registradora");
    public static final SoundEvent BALIM_ACERTO = registrarSom("balim_acerto");
    public static final SoundEvent HIC = registrarSom("hic");
    // v1.2.10: o gole em si tem som proprio (e o refluxo tambem, pitch grave)
    public static final SoundEvent GLUP = registrarSom("glup");
    // v1.2.19: o arpejo da virada do letreiro (ABERTO verde sobe, FECHADO desce)
    public static final SoundEvent LETREIRO_VIRADA = registrarSom("letreiro_virada");
    // v1.2.39: o TEMA do Juça (riff cowpunk original) e a voz dele
    public static final SoundEvent JUCA_RIFF = registrarSom("juca_riff");
    public static final SoundEvent JUCA_VOZ = registrarSom("juca_voz");

    // v1.2.32: o MECANISMO da escopeta (tubo/camara/timer/fase) vive num
    // DataComponent da stack — acompanha o item no bau, no chao e pela rede.
    public static final DataComponentType<EscopetaEstado> TIPO_ESTADO_ESCOPETA =
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                    ResourceKey.create(Registries.DATA_COMPONENT_TYPE,
                            Identifier.fromNamespaceAndPath(MOD_ID, "estado_escopeta")),
                    DataComponentType.<EscopetaEstado>builder()
                            .persistent(EscopetaEstado.CODEC)
                            .networkSynchronized(EscopetaEstado.STREAM_CODEC)
                            .build());

    // Cartucho do calibre 12: polvora + prego + papel (declarado antes: a escopeta usa no reparo)
    public static final Item CARTUCHO = registerItem("cartucho",
            new Item(new Item.Properties().stacksTo(64).setId(itemKey("cartucho"))));
    // A escopeta: 128 usos, municao propria e REPARAVEL com 2 cartuchos na bigorna
    public static final Item ESCOPETA = registerItem("escopeta",
            new EscopetaItem(new Item.Properties().stacksTo(1)
                    .durability(128)
                    .component(DataComponents.REPAIRABLE,
                            new net.minecraft.world.item.enchantment.Repairable(
                                    net.minecraft.core.HolderSet.direct(CARTUCHO.builtInRegistryHolder())))
                    .setId(itemKey("escopeta"))));

    // ============================================================ CAMISA DO MATANZA (v1.2.39)
    /**
     * O EQUIPMENT ASSET da camisa (a textura de corpo). ResourceKey
     * declarado ANTES do item (o construtor lê). Aponta pra
     * assets/intoxicantes/equipment/camisa_matanza.json.
     */
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> CAMISA_ASSET =
            net.minecraft.resources.ResourceKey.create(
                    net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID,
                    Identifier.fromNamespaceAndPath(MOD_ID, "camisa_matanza"));

    /**
     * A camisa da banda do Juça — PODERES MATANZÍSTICOS DEMONÍACOS (idéia
     * do Discord): peito de couro com FIRE_RESISTANCE permanente enquanto
     * vestida (quem veste o Matanza não queima). Repara com couro.
     *
     * v1.2.71 — CAMISA DE VERDADE (bug do playtest: "tem aparência de
     * peitoral de couro"): o EquipmentAssets.LEATHER emprestava a TEXTURA
     * cinza-tingível do couro vanilla — no corpo ficava uma coiraza. Agora
     * o asset é PRÓPRIO (intoxicantes:camisa_matanza) com layer humanoide
     * pintado na paleta da banda (camisa preta, caveira branca, mangas),
     * nas mesmas regiões que a armadura vanilla pinta (corpo 24×16 @16,16,
     * mangas 16×16 @40,16, espelhadas pro braço esquerdo pelo renderer).
     * SEM dyeable: preto é preto.
     */
    public static final Item CAMISA_MATANZA = registerItem("camisa_matanza",
            new Item(new Item.Properties()
                    .humanoidArmor(new net.minecraft.world.item.equipment.ArmorMaterial(
                            10, // durabilidade (proximo do couro)
                            java.util.Map.of(net.minecraft.world.item.equipment.ArmorType.CHESTPLATE, 4),
                            3, // encantabilidade
                            SoundEvents.ARMOR_EQUIP_LEATHER, // já é Holder<SoundEvent>
                            0.0F, 0.0F,
                            net.minecraft.tags.ItemTags.REPAIRS_LEATHER_ARMOR,
                            CAMISA_ASSET),
                            net.minecraft.world.item.equipment.ArmorType.CHESTPLATE)
                    .component(DataComponents.EQUIPPABLE,
                            net.minecraft.world.item.equipment.Equippable.builder(
                                            net.minecraft.world.entity.EquipmentSlot.CHEST)
                                    .setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
                                    .setAsset(CAMISA_ASSET)
                                    .build())
                    .setId(itemKey("camisa_matanza"))));

    // ============================================================ REVÓLVER .38 (o "três oitão", v1.2.33)
    // Estado do tambor (6 buracos) — declarado antes dos itens que o usam
    public static final DataComponentType<RevolverEstado> TIPO_ESTADO_REVOLVER =
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                    ResourceKey.create(Registries.DATA_COMPONENT_TYPE,
                            Identifier.fromNamespaceAndPath(MOD_ID, "estado_revolver")),
                    DataComponentType.<RevolverEstado>builder()
                            .persistent(RevolverEstado.CODEC)
                            .networkSynchronized(RevolverEstado.STREAM_CODEC)
                            .build());

    // Cartucho .38: chumbo + polvora + latao (o tres-oitao tem munição própria)
    public static final Item CARTUCHO_38 = registerItem("cartucho_38",
            new Item(new Item.Properties().stacksTo(64).setId(itemKey("cartucho_38"))));
    // O revólver: tambor de 6, mais preciso e forte por bala que a 12, reparável com 2 cartuchos .38
    public static final Item REVOLVER = registerItem("revolver",
            new RevolverItem(new Item.Properties().stacksTo(1)
                    .durability(256)
                    .component(DataComponents.REPAIRABLE,
                            new net.minecraft.world.item.enchantment.Repairable(
                                    net.minecraft.core.HolderSet.direct(CARTUCHO_38.builtInRegistryHolder())))
                    .setId(itemKey("revolver"))));

    // ============================================================ DINHEIRO R$ (família completa)
    // UPGRADE DA ECONOMIA: antes só a cédula azul existia e valia 1 (a nota de
    // 2 "valia 1"). Agora a família inteira existe e cada nota VALE o que
    // diz: moeda_1=1, real=2, nota_5, nota_10, nota_20, nota_50, nota_100,
    // nota_200 e nota_500 (edição comemorativa: SÓ o Wither dropa).
    // A fonte da verdade do valor é RealItem.VALORES; a ordem crescente aqui
    // define a ordem do troco (RealItem.trocar e custoDe leem de trás pra frente).
    public static final Item MOEDA_1 = dinheiro("moeda_1", 1);
    public static final Item REAL = dinheiro("real", 2);
    public static final Item NOTA_5 = dinheiro("nota_5", 5);
    public static final Item NOTA_10 = dinheiro("nota_10", 10);
    public static final Item NOTA_20 = dinheiro("nota_20", 20);
    public static final Item NOTA_50 = dinheiro("nota_50", 50);
    public static final Item NOTA_100 = dinheiro("nota_100", 100);
    public static final Item NOTA_200 = dinheiro("nota_200", 200);
    // v1.2.61: troféu de boss — não tem receita nem venda; o Wither dropa
    // (entra no troco/custoDe sozinho pela ordem de registro abaixo).
    public static final Item NOTA_500 = dinheiro("nota_500", 500);

    // ============================================================ GUIA DO SNC ADVENTURES (v1.2.51)
    // O livro-guia oficial: use com o botão direito e a tela abre (client).
    // Entrega única na 1ª entrada (GuiaPrimeiraVez); recuperação SÓ por craft
    // (livro + R$) — sem comando, decisão do usuário.
    public static final Item GUIA_SNC = registerItem("guia_snc",
            new GuiaItem(new Item.Properties().stacksTo(1).setId(itemKey("guia_snc"))));

    // ============================================================ RÓTULO DO BARRIL (v1.2.50)
    // DataComponent string no BlockItem: qual bebida o barril carrega
    // (o craft carimba; o BE lê na hora da colocação). Codec simples.
    public static final DataComponentType<String> ROTULO_BARRIL =
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                    ResourceKey.create(Registries.DATA_COMPONENT_TYPE,
                            Identifier.fromNamespaceAndPath(MOD_ID, "rotulo_barril")),
                    DataComponentType.<String>builder()
                            .persistent(com.mojang.serialization.Codec.STRING)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8)
                            .build());

    // ============================================================ SEMENTES (BlockItem das plantas)
    public static final Item SEMENTE_MACONHA = seedItem("semente_maconha", MACONHA_PLANT);
    public static final Item SEMENTE_LOUPULO = seedItem("semente_lupulo", LOUPULO_PLANT);
    public static final Item SEMENTE_UVA = seedItem("semente_uva", UVA_PLANT);
    public static final Item SEMENTE_CAFE = seedItem("semente_cafe", CAFE_PLANT);
    public static final Item SEMENTE_PAPOULA = seedItem("semente_papoula", PAPOULA_PLANT);
    // v1.2.50: a cevada entra na família (mesmo modelo de pacote de sementes)
    public static final Item SEMENTE_CEVADA = seedItem("semente_cevada", CEVADA_PLANT);

    // ============================================================ PRODUTOS DAS PLANTACOES
    public static final Item LOUPULO_FRESCO = product("lupulo");
    // Cacho compartilhado com a parreira: 4,656 × 4,656 × 8,368 unidades.
    // Continua ingrediente, sem FOOD/CONSUMABLE; a exposição não torna a uva comestível.
    public static final Item UVA = registerAlimentoColocavel("uva", new Item.Properties().stacksTo(64),
            new BebidaColocavelItem.Exposicao(0.43F, 0.53F, 0.15F));
    public static final Item CAFE_VERDE = product("cafe_verde");
    public static final Item CANA_DE_ACUCAR = product("cana_de_acucar");

    // ============================================================ MATÉRIA-PRIMA DAS BEBIDAS (v1.2.50)
    // Cerveja: cevada (crop novo) → malte (forja) → mosto (caldeirão + lúpulo)
    public static final Item CEVADA = product("cevada");
    public static final Item MALTE = product("malte");
    // Cachaça: cana → caldo (moenda); caldo → mosto fermentado (dorna)
    public static final Item CALDO_DE_CANA = product("caldo_de_cana");
    public static final Item MOSTO_CANA_FERMENTADO = product("mosto_cana_fermentado");
    // Rum: cana → melaço (forna caldo) → mosto (dorna)
    public static final Item MELACO = product("melaco");
    public static final Item MOSTO_RUM_FERMENTADO = product("mosto_rum_fermentado");
    // Vinho: uva → mosto de uva (prensa)
    public static final Item MOSTO_DE_UVA = product("mosto_de_uva");
    // Cerveja: malte + agua (caldeirao) + lupulo na fervura -> mosto lupulado
    public static final Item MOSTO_CERVEJA_LUPULADO = product("mosto_cerveja_lupulado");
    // Destilados jovens (saem do alambique; o barril completa)
    public static final Item CACHACA_JOVEM = product("cachaca_jovem");
    public static final Item RUM_JOVEM = product("rum_jovem");
    // Subproduto da moenda (combustível de fornalha)
    public static final Item BAGACO_DE_CANA = registerItem("bagaco_de_cana",
            new Item(new Item.Properties().stacksTo(64).setId(itemKey("bagaco_de_cana"))));

    // ==================================================== GARRAFAS VAZIAS (v1.2.65)
    // O recipiente de cada bebida: devolvido ao beber (USE_REMAINDER acima),
    // reutilizável pra encher de novo nos receitas de transferência do mercado.
    // REGISTRA ANTES das bebidas: o drink() consulta garrafaVazia() na hora.
    public static final Item CERVEJA_VAZIA = vazio("cerveja_vazia");
    public static final Item VINHO_VAZIA = vazio("vinho_vazia");
    public static final Item CACHACA_VAZIA = vazio("cachaca_vazia");
    public static final Item HIDROMEL_VAZIA = vazio("hidromel_vazia");
    public static final Item RUM_VAZIA = vazio("rum_vazia");
    public static final Item SUCO_DETOX_VAZIA = vazio("suco_detox_vazia");
    public static final Item AGUA_DE_COCO_VAZIA = vazio("agua_de_coco_vazia");
    public static final Item CHA_LUPULO_VAZIA = vazio("cha_lupulo_vazia");

    /** v1.2.65: item registrador — o bebida 3D vazia SÓ empilha com ela mesma. */
    private static Item vazio(String name) {
        return registerItem(name, new GarrafaVaziaItem(
                comLore(name, new Item.Properties().stacksTo(16).setId(itemKey(name)))));
    }

    // ============================================================ BEBIDAS
    public static final Item CERVEJA = drink("cerveja",
            effect(MobEffects.STRENGTH, 900, 0),
            effect(MobEffects.NAUSEA, 200, 0));

    public static final Item VINHO = drink("vinho",
            effect(MobEffects.REGENERATION, 600, 0),
            effect(MobEffects.NAUSEA, 300, 0));

    public static final Item CACHACA = drink("cachaca",
            effect(MobEffects.STRENGTH, 900, 1),
            effect(MobEffects.NAUSEA, 400, 0),
            effect(MobEffects.SLOWNESS, 300, 0));

    public static final Item HIDROMEL = drink("hidromel",
            effect(MobEffects.ABSORPTION, 1200, 0),
            effect(MobEffects.NAUSEA, 200, 0));

    public static final Item RUM = drink("rum",
            effect(MobEffects.FIRE_RESISTANCE, 1200, 0),
            effect(MobEffects.NAUSEA, 300, 0));

    // ============================================================ ERVAS & DROGAS
    /** Seda: folha colhida direto, precisa secar/rolar. */
    public static final Item MACONHA_SEDA = powder("maconha_seda",
            effect(MobEffects.NAUSEA, 200, 0),
            effect(MobEffects.HUNGER, 200, 0));

    /** Baseado: fumavel (tipo colunar), efeito tranquilo. */
    public static final Item BASEADO = smoke("baseado",
            effect(MobEffects.REGENERATION, 300, 0),
            effect(MobEffects.SLOW_FALLING, 600, 0),
            effect(MobEffects.NAUSEA, 150, 0));

    /**
     * v1.2.39 — CIGARRO CAMEL (amarelo): o cigarro do Juça. Trago curto
     * (colunar igual o baseado) com um shot rápido de pressa + tontura —
     * é nicotina de roleplay, não remédio.
     */
    public static final Item CIGARRO_CAMEL = smoke("cigarro_camel",
            effect(MobEffects.SPEED, 200, 0),
            effect(MobEffects.NAUSEA, 100, 0));

    /** Opio: anestesico de rua — tanque barato que deixa lento (nicho real de uso). */
    public static final Item OPIO = powder("opio",
            effect(MobEffects.RESISTANCE, 600, 0),
            effect(MobEffects.NAUSEA, 300, 0),
            effect(MobEffects.WEAKNESS, 300, 0));

    /** Cocaina: burst de energia brutal + queda brutal depois. */
    public static final Item COCAINA = powder("cocaina",
            effect(MobEffects.SPEED, 1200, 2),
            effect(MobEffects.HASTE, 1200, 1),
            effect(MobEffects.MINING_FATIGUE, 400, 0),
            effect(MobEffects.WEAKNESS, 300, 0));

    /** Heroína: anestesia total, câmera lenta e regeneração. */
    public static final Item HEROINA = powder("heroina",
            effect(MobEffects.RESISTANCE, 600, 1),
            effect(MobEffects.REGENERATION, 400, 0),
            effect(MobEffects.SLOWNESS, 600, 2),
            effect(MobEffects.NAUSEA, 300, 0));

    /** LSD: viagem total — levitação, visão distorcida, trevas. */
    public static final Item LSD = pill("lsd",
            effect(MobEffects.LEVITATION, 200, 0),
            effect(MobEffects.NIGHT_VISION, 2400, 0),
            effect(MobEffects.DARKNESS, 200, 0),
            effect(MobEffects.NAUSEA, 400, 0));

    // ============================================================ SUBSTÂNCIAS DA ESQUINA
    public static final Item PO_ESTELAR = powder("po_estelar",
            effect(MobEffects.SPEED, 1800, 1),
            effect(MobEffects.HASTE, 1800, 0),
            effect(MobEffects.HUNGER, 200, 0));

    public static final Item COGUMELO_XAMANICO = powder("cogumelo_xamanico",
            effect(MobEffects.NAUSEA, 400, 0),
            effect(MobEffects.NIGHT_VISION, 2400, 0),
            effect(MobEffects.LEVITATION, 100, 0));

    public static final Item NEVOA_DO_DESERTO = powder("nevoa_do_deserto",
            effect(MobEffects.BLINDNESS, 300, 0),
            effect(MobEffects.NIGHT_VISION, 2400, 0),
            effect(MobEffects.JUMP_BOOST, 600, 1));

    public static final Item RAIZ_DE_SOMBRA = powder("raiz_de_sombra",
            effect(MobEffects.SLOW_FALLING, 1200, 0),
            effect(MobEffects.WEAKNESS, 900, 0));

    public static final Item CRISTAL_DE_EUFORIA = powder("cristal_de_euforia",
            effect(MobEffects.REGENERATION, 400, 1),
            effect(MobEffects.SPEED, 1200, 0),
            effect(MobEffects.JUMP_BOOST, 1200, 0));

    public static final Item EXTRATO_CAFEINA = powder("extrato_cafeina",
            effect(MobEffects.HASTE, 2400, 0),
            effect(MobEffects.SPEED, 2400, 0));

    // ==================================================== SAUDE: O CAMINHO DE CURA (v1.2.54)
    // SUCO DETOX: a redenção do fregues — regenera os órgãos devagar (o
    // SaudeSystem aplica a cura e a hidratação no fim do gole).
    public static final Item SUCO_DETOX = drink("suco_detox");
    // AGUA DE COCO: o isotônico do sertão (+50 de hidratação).
    public static final Item AGUA_DE_COCO = drink("agua_de_coco");

    // ==================================================== A VIDA ALÉM DA CERVEJA (v1.2.58)
    // COCO: o fruto in natura — quebra o coco no pé, come e já mata fome e sede
    // (a hidratação +15 o SaudeSystem aplica no fim da mordida).
    // gen_garrafas: corpo 8,4 × 8,4, altura 9 e base 5,2 × 5,2 unidades.
    public static final Item COCO_FRUTO = registerAlimentoColocavel("coco", comLore("coco",
            new Item.Properties().stacksTo(64)
                    .food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).alwaysEdible().build())),
            new BebidaColocavelItem.Exposicao(0.75F, 0.57F, 0.47F));
    // CHÁ DE LÚPULO: a calma da flor — corta a viagem na hora (é o "café" do
    // psicodélico) e regenera devagar. Devolve a garrafa vazia.
    public static final Item CHA_LUPULO = comLoreConsumivel("cha_lupulo",
            new Item.Properties().stacksTo(16),
            Consumable.builder()
                    .animation(ItemUseAnimation.DRINK)
                    .sound(SoundEvents.GENERIC_DRINK)
                    .onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
                            new MobEffectInstance(MobEffects.REGENERATION, 200, 0))))
                    .onConsume(new RemoveStatusEffectsConsumeEffect(HolderSet.direct(
                            Efeitos.OVERDRIVE, Efeitos.VIAGEM)))
                    .build());
    // PÃO DE CEVADA: a comida honesta da colheita — mata fome de verdade
    // (o trigo tem pão, a cevada tem o dela agora)
    // gen_garrafas: corpo 10,2 × 7,6, altura 8,05 e base 9 × 7 unidades.
    public static final Item PAO_CEVADA = registerAlimentoColocavel("pao_cevada", comLore("pao_cevada",
            new Item.Properties().stacksTo(64)
                    .food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.6F).build())),
            new BebidaColocavelItem.Exposicao(0.80F, 0.51F, 0.73F));

    /** Abas vanilla onde os itens tambem aparecem (facilidade de descoberta). */
    private static final ResourceKey<CreativeModeTab> FOOD_AND_DRINKS = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("minecraft", "food_and_drinks"));
    private static final ResourceKey<CreativeModeTab> INGREDIENTS = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("minecraft", "ingredients"));
    private static final ResourceKey<CreativeModeTab> NATURAL = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("minecraft", "natural"));
    private static final ResourceKey<CreativeModeTab> FUNCTIONAL = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("minecraft", "functional"));

    public static final CreativeModeTab TAB = FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.intoxicantes"))
            .icon(() -> new ItemStack(CERVEJA))
            .displayItems((parameters, output) -> {
                // Sementes
                output.accept(SEMENTE_MACONHA);
                output.accept(SEMENTE_LOUPULO);
                output.accept(SEMENTE_UVA);
                output.accept(SEMENTE_CAFE);
                output.accept(SEMENTE_PAPOULA);
                // Ovos dos NPCs
                output.accept(OVO_TRAFICANTE);
                output.accept(OVO_GAGO);
                // Lampada (as plantas nao entram: a semente e o proprio BlockItem delas)
                output.accept(LAMPADA_UV);
                // v1.2.18: o letreiro da casa (placa custom de LED)
                output.accept(PLACA_ESQUINAO);
                // v1.2.36: o painel de LED craftável (a TV de tela plana)
                output.accept(PAINEL_LED);
                // v1.2.38: o CONTROLE REMOTO (Central de Comando portátil)
                output.accept(CENTRAL_COMANDO);
                // v1.2.19: o poste de luz do estacionamento (acende de noite)
                output.accept(POSTE_LUZ);
                // v1.2.68: a lâmpada LED do teto do mercado (sempre acesa, por enquanto)
                output.accept(LAMPADA_LED);
                // v1.2.70: a família por potência (5W..200W — bulbo/refletor/high-bay)
                for (Block lampada : LAMPADAS_POTENCIA) {
                    output.accept(lampada);
                }
                // v1.2.74: a instalacao eletrica — cabos, soquete, interruptor,
                // quadro e tomada. Registrados desde v1.2.70/71, mas em NENHUMA
                // aba do Criativo: dava /give, nao dava para achar na busca.
                output.accept(QUADRO_ELETRICO);
                output.accept(SOQUETE_TETO);
                output.accept(INTERRUPTOR_SIMPLES);
                output.accept(TOMADA);
                output.accept(CABO_COBRE_1_5MM);
                output.accept(CABO_COBRE_2_5MM);
                output.accept(CABO_COBRE_4MM);
                output.accept(CABO_COBRE_6MM);
                output.accept(CABO_COBRE_10MM);
                // v1.2.75: o cabo suspenso — conector + as 5 bobinas
                output.accept(CONECTOR_ELETRICO);
                output.accept(BOBINA_COBRE_1_5MM);
                output.accept(BOBINA_COBRE_2_5MM);
                output.accept(BOBINA_COBRE_4MM);
                output.accept(BOBINA_COBRE_6MM);
                output.accept(BOBINA_COBRE_10MM);
                // v1.2.69: a gôndola do mercado (vende qualquer item, entrega na quarta)
                output.accept(PRATELEIRA_MERCADO);
                output.accept(CAIXA_MERCADO);
                output.accept(ASFALTO);
                // v1.2.51: a porta-grade do guichê (a porta com dono)
                output.accept(PORTA_GRADE);
                // Produtos agricolas
                output.accept(LOUPULO_FRESCO);
                output.accept(UVA);
                output.accept(CAFE_VERDE);
                output.accept(CANA_DE_ACUCAR);
                // v1.2.58: o coqueiro (bloco + fruto)
                output.accept(COQUEIRO_TRONCO);
                output.accept(COQUEIRO_FOLHAS);
                output.accept(COCO_FRUTO);
                // v1.2.50: a cadeia das bebidas (matéria-prima, máquinas e barris)
                output.accept(SEMENTE_CEVADA);
                output.accept(CEVADA);
                output.accept(MALTE);
                output.accept(MOENDA_CANA);
                output.accept(CALDO_DE_CANA);
                output.accept(BAGACO_DE_CANA);
                output.accept(DORNA_BEBIDA);
                output.accept(MOSTO_CANA_FERMENTADO);
                output.accept(ALAMBIQUE);
                output.accept(CACHACA_JOVEM);
                output.accept(MELACO);
                output.accept(MOSTO_RUM_FERMENTADO);
                output.accept(RUM_JOVEM);
                output.accept(PRENSA_UVAS);
                output.accept(MOSTO_DE_UVA);
                output.accept(CALDEIRAO_MOSTURA);
                output.accept(MOSTO_CERVEJA_LUPULADO);
                output.accept(BARRIL_CACHACA);
                output.accept(BARRIL_CERVEJA);
                output.accept(BARRIL_RUM);
                output.accept(BARRIL_VINHO);
                // Ervas
                output.accept(MACONHA_SEDA);
                output.accept(BASEADO);
                output.accept(CIGARRO_CAMEL);
                output.accept(OPIO);
                // Quimicos
                output.accept(COCAINA);
                output.accept(HEROINA);
                output.accept(LSD);
                // Bebidas
                output.accept(CERVEJA);
                // v1.2.65: o recipiente devolvido acompanha a bebida na aba
                output.accept(CERVEJA_VAZIA);
                output.accept(VINHO);
                output.accept(CACHACA);
                output.accept(CACHACA_VAZIA);
                output.accept(HIDROMEL);
                output.accept(HIDROMEL_VAZIA);
                output.accept(RUM);
                output.accept(RUM_VAZIA);
                // Substancias misticas
                output.accept(PO_ESTELAR);
                output.accept(COGUMELO_XAMANICO);
                output.accept(NEVOA_DO_DESERTO);
                output.accept(RAIZ_DE_SOMBRA);
                output.accept(CRISTAL_DE_EUFORIA);
                output.accept(EXTRATO_CAFEINA);
                // v1.2.54: o caminho de cura (saúde do fregues)
                output.accept(SUCO_DETOX);
                output.accept(SUCO_DETOX_VAZIA);
                output.accept(AGUA_DE_COCO);
                output.accept(AGUA_DE_COCO_VAZIA);
                // v1.2.58: a vida além da cerveja (chá, pão de cevada)
                // (COCO_FRUTO não entra aqui de novo: já está no grupo do
                // coqueiro acima — item repetido na MESMA aba quebra o client
                // com "Accidentally adding the same item stack twice")
                output.accept(CHA_LUPULO);
                output.accept(CHA_LUPULO_VAZIA);
                output.accept(PAO_CEVADA);
                // Armas do Gago
                output.accept(ESCOPETA);
                output.accept(CARTUCHO);
                // O três-oitão
                output.accept(REVOLVER);
                output.accept(CARTUCHO_38);
                // Dinheiro: a família completa (moeda + todas as cédulas)
                output.accept(MOEDA_1);
                output.accept(REAL);
                output.accept(NOTA_5);
                output.accept(NOTA_10);
                output.accept(NOTA_20);
                output.accept(NOTA_50);
                output.accept(NOTA_100);
                output.accept(NOTA_200);
                output.accept(NOTA_500);
                // v1.2.51: o livro-guia (topo da aba é a cerveja; o guia fica
                // no fim, junto do dinheiro — manual de consulta)
                output.accept(GUIA_SNC);
            })
            .build();

    @Override
    public void onInitialize() {
        // v1.2.7: partículas proprias — registrar AQUI (mod init), antes do freeze
        // das registries. Referenciar Particulas.DINHEIRO do Server thread depois
        // que a registry congelou explode "Registry is already frozen".
        Particulas.init();

        // v1.2.41: a TECLA R das armas — payload C2S de recarga + canal S2C de status
        RecargaPayload.registrar();
        GatilhoPayload.registrar();
        RecargaPayload.registrarStatus();

        // v1.2.53: botões CoD/BF — estado de mira (ADS) via payload C2S, limpa no logout
        MiraPayload.registrar();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                MiraPayload.limpar(handler.player));

        ResourceKey<CreativeModeTab> tabKey = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabKey, TAB);

        // Bebidas tambem na aba vanilla "Comidas e Bebidas"
        CreativeModeTabEvents.modifyOutputEvent(FOOD_AND_DRINKS).register(output -> {
            output.accept(CERVEJA);
            output.accept(VINHO);
            output.accept(CACHACA);
            output.accept(HIDROMEL);
            output.accept(RUM);
            // v1.2.54: os sucos entram na família
            output.accept(SUCO_DETOX);
            output.accept(AGUA_DE_COCO);
        });
        // Sementes na aba "Natureza" — nao duplica se ja estiver na aba do mod
        // Lampada UV na aba "Funcional"
        CreativeModeTabEvents.modifyOutputEvent(FUNCTIONAL).register(output -> {
            output.accept(LAMPADA_UV);
        });
        // Ervas e quimicos tambem na aba vanilla "Ingredientes"
        CreativeModeTabEvents.modifyOutputEvent(INGREDIENTS).register(output -> {
            output.accept(MACONHA_SEDA);
            output.accept(BASEADO);
            output.accept(OPIO);
            output.accept(COCAINA);
            output.accept(HEROINA);
            output.accept(LSD);
            output.accept(CANA_DE_ACUCAR);
            // v1.2.50: intermediários da destilaria também descobríveis
            output.accept(CEVADA);
            output.accept(MALTE);
            output.accept(CALDO_DE_CANA);
            output.accept(MELACO);
            output.accept(PO_ESTELAR);
            output.accept(COGUMELO_XAMANICO);
            output.accept(NEVOA_DO_DESERTO);
            output.accept(RAIZ_DE_SOMBRA);
            output.accept(CRISTAL_DE_EUFORIA);
            output.accept(EXTRATO_CAFEINA);
            output.accept(MOEDA_1);
            output.accept(REAL);
            output.accept(NOTA_5);
            output.accept(NOTA_10);
            output.accept(NOTA_20);
            output.accept(NOTA_50);
            output.accept(NOTA_100);
            output.accept(NOTA_200);
            output.accept(NOTA_500);
        });

        // ======================================================== LOOT TABLES
        // Sementes: drops naturais ao quebrar grama, samambaia, trepadeiras e papoula
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!source.isBuiltin()) {
                return;
            }
            // chaves de loot tables de bloco vanilla: minecraft:blocks/<bloco>
            java.util.function.Function<String, ResourceKey<LootTable>> blockTable = id -> ResourceKey.create(
                    Registries.LOOT_TABLE, Identifier.withDefaultNamespace("blocks/" + id));
            addSeedPool(tableBuilder, key, blockTable.apply("short_grass"), SEMENTE_MACONHA, 0.10F);
            addSeedPool(tableBuilder, key, blockTable.apply("fern"), SEMENTE_MACONHA, 0.10F);
            // v1.2.50: a cevada brota selvagem na taiga e nas planícies frias
            addSeedPool(tableBuilder, key, blockTable.apply("short_grass"), SEMENTE_CEVADA, 0.06F);
            addSeedPool(tableBuilder, key, blockTable.apply("vine"), SEMENTE_UVA, 0.20F);
            addSeedPool(tableBuilder, key, blockTable.apply("jungle_leaves"), SEMENTE_UVA, 0.05F);
            addSeedPool(tableBuilder, key, blockTable.apply("large_fern"), SEMENTE_CAFE, 0.10F);
            addSeedPool(tableBuilder, key, blockTable.apply("poppy"), SEMENTE_PAPOULA, 0.08F);
            addSeedPool(tableBuilder, key, blockTable.apply("sweet_berry_bush"), SEMENTE_LOUPULO, 0.15F);
            // Papoula vanilla passa a dropar o OPIO (seiva) junto
            addSeedPool(tableBuilder, key, blockTable.apply("poppy"), OPIO, 0.35F);
            // Zumbis dropam R$ (15% chance, 1-3 unidades)
            addSeedPool(tableBuilder, key, ResourceKey.create(Registries.LOOT_TABLE,
                    Identifier.fromNamespaceAndPath("minecraft", "entities/zombie")), REAL, 0.15F);
            // Piglins tambem dropam R$ (25% chance)
            addSeedPool(tableBuilder, key, ResourceKey.create(Registries.LOOT_TABLE,
                    Identifier.fromNamespaceAndPath("minecraft", "entities/piglin")), REAL, 0.25F);
            // v1.2.61: o Wither SEMPRE dropa a nota de 500 (troféu de boss;
            // chance 1.0 — matar o Wither é a única forma de ter a cédula)
            addSeedPool(tableBuilder, key, ResourceKey.create(Registries.LOOT_TABLE,
                    Identifier.fromNamespaceAndPath("minecraft", "entities/wither")), NOTA_500, 1.0F);
            // Esqueletos dropam cartucho (20% chance): fonte alternativa de municao
            addSeedPool(tableBuilder, key, ResourceKey.create(Registries.LOOT_TABLE,
                    Identifier.fromNamespaceAndPath("minecraft", "entities/skeleton")), CARTUCHO, 0.20F);
            // Esqueletos dropam cartucho .38 (12% chance): munição do três-oitão
            addSeedPool(tableBuilder, key, ResourceKey.create(Registries.LOOT_TABLE,
                    Identifier.fromNamespaceAndPath("minecraft", "entities/skeleton")), CARTUCHO_38, 0.12F);
        });

        // ======================================================== NPCS: atributos + ovos nas abas
        FabricDefaultAttributeRegistry.register(TRAFICANTE, TraficanteEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(GAGO, GagoEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(JUCA, JucelinoEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(PERU, PeruEntity.createAttributes());
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> {
            output.accept(OVO_TRAFICANTE);
            output.accept(OVO_GAGO);
            output.accept(OVO_JUCA);
        });

        // ======================================================== SISTEMA DE DINHEIRO R$
        MoneyCommands.register();

        // ======================================================== GUIA DO SNC ADVENTURES
        // Entrega única na 1ª entrada (server-side; flag em JSON no mundo).
        GuiaPrimeiraVez.registrar();

        // ======================================================== MERCADO ESQUINÃO
        MarketSystem.register();

        // ==================================================== ELETRICIDADE (v1.2.70)
        // O gerenciador das redes elétricas (server-side; integração opcional
        // com o SNC Energies via energia.SNCEnergiesAdapter — reflexão)
        com.intoxicantes.energia.EnergiaRedes.register();
        QuadroEletricoNetworking.register();
        PeruNetworking.register();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            JogoDoBicho.tick(server);
            PeruSystem.tick(server);
            PeruNetworking.tick(server);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof PeruEntity peru) PeruSystem.observar(peru);
            if (entity instanceof MotoPeruEntity moto) PeruSystem.observar(moto);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PeruNetworking.reset(); PeruSystem.reset(); JogoDoBicho.reset(); PlayerMoney.reset();
        });
        CabosNetworking.register();

        // v1.2.7: fumaca propria do baseado (enquanto o fregues puxa)
        BaseadoFumaca.register();

        // v1.2.9: embriaguez — dose, fala fonar no chat e HIC
        Embriaguez.register();

        // ==================================================== SAUDE + VIAGENS (v1.2.54)
        // Os MobEffects assinatura precisam estar registrados ANTES do freeze
        // das registries: tocar a classe dispara o static que registra.
        Efeitos.carga();
        // Rede da saúde (sync S2C + tecla H do prontuário) e o motor (sede,
        // vício/abstinência, queda das viagens, efeitos por droga)
        SaudeNetworking.registrar();
        SaudeSystem.register();

        // ======================================================== CARDAPIO DO ESQUINAO (rede)
        EsquinaoNetworking.register();
        PrateleiraNetworking.register();

        // v1.2.36: rede da CENTRAL DE COMANDO (painel de LED + letreiro)
        CentralComandoNetworking.register();

        // v1.2.32: rede da 12 — o kick de camera (S2C). O registro do codec e'
        // global na JVM: precisa existir antes do 1o tiro em qualquer lado.
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(
                RecuoPayload.TYPE, RecuoPayload.STREAM_CODEC);

        // ======================================================== SPAWN PERIODICO DE TRAFICANTE
        // O Gago agora so aparece no Mercado Esquinão (24h, muda de posicao).
        // O traficante continua aparecendo aleatoriamente.
        final int[] cooldown = {120 * 20};
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (--cooldown[0] > 0) {
                return;
            }
            cooldown[0] = 120 * 20;
            RandomSource rng = RandomSource.create();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.isSpectator()) {
                    continue;
                }
                ServerLevel lvl = player.level() instanceof ServerLevel sl ? sl : null;
                if (lvl == null) {
                    continue;
                }
                for (int i = 0; i < 3; i++) {
                    int dx = rng.nextInt(17) - 8;
                    int dz = rng.nextInt(17) - 8;
                    // v1.2.71 — PERTO DEMAIS PRA PERDER (bug do playtest: "o
                    // traficante não spawna"): a faixa antiga 24..40 blocos
                    // nascia atrás das colinas, fora do render curto, e o
                    // andarilho SEM persistência despawnava antes do freguês
                    // chegar perto. Agora nasce a 16..30 blocos (área visível
                    // de verdade) e SÓ onde há 2 blocos de ar sobre chão
                    // sólido (nunca dentro de árvore/terreno).
                    int dist = 16 + rng.nextInt(15);
                    double ang = rng.nextDouble() * Math.PI * 2;
                    int x = (int) (player.getX() + Math.cos(ang) * dist) + dx / 2;
                    int z = (int) (player.getZ() + Math.sin(ang) * dist) + dz / 2;
                    if (!lvl.hasChunkAt(new BlockPos(x, 0, z))) {
                        continue;
                    }
                    int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
                    if (y < lvl.getMinY() + 1) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!lvl.getFluidState(pos.below()).isEmpty()
                            || !lvl.getBlockState(pos.below()).isSolidRender()
                            || !lvl.getBlockState(pos).isAir()
                            || !lvl.getBlockState(pos.above()).isAir()) {
                        continue;
                    }
                    // So traficante aparece aleatoriamente (1/6 chance)
                    if (rng.nextInt(6) == 0) {
                        TraficanteEntity npc = TRAFICANTE.create(lvl, EntitySpawnReason.EVENT);
                        if (npc != null) {
                            npc.absSnapTo(x + 0.5, y, z + 0.5, rng.nextFloat() * 360F, 0F);
                            npc.finalizeSpawn(lvl, lvl.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
                            lvl.addFreshEntity(npc);
                            npc.anunciarChegada(lvl);
                        }
                    }
                }
            }
        });

        // ======================================================== CHAT: O SISTEMA DO "GAGO"
        // Se alguem escrever "gago" (com ou sem acento) no chat, TODO gago num raio
        // de 48 blocos fica PUTO DA CARA e vai atras do infeliz kkkk
        // Delay de 5 ticks pra mensagem do player aparecer primeiro
        java.util.List<Runnable> gagoQueue = new java.util.ArrayList<>();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            synchronized (gagoQueue) {
                if (!gagoQueue.isEmpty()) {
                    for (Runnable r : gagoQueue) r.run();
                    gagoQueue.clear();
                }
            }
        });
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String texto = message.signedContent();
            String normalizada = Normalizer.normalize(texto, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "")
                    .toLowerCase(Locale.ROOT);
            if (!normalizada.contains("gago")) {
                return;
            }
            for (ServerLevel level : sender.level().getServer().getAllLevels()) {
                for (var npc : level.getEntitiesOfClass(GagoEntity.class,
                        sender.getBoundingBox().inflate(48.0))) {
                    GagoEntity g = npc;
                    synchronized (gagoQueue) {
                        gagoQueue.add(() -> g.ouvirChat(sender, texto));
                    }
                }
            }
        });

        // ==================================================== CHAT: O SISTEMA DO "JUÇA"
        // v1.2.39: escreveu "juca" (com ou sem acento)? O Juça responde com o
        // "hé hé" e uma puxada no Camel — o Gago fica PUTO, o Juça só zoa.
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String texto = message.signedContent();
            String normalizada = Normalizer.normalize(texto, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "")
                    .toLowerCase(Locale.ROOT);
            if (!normalizada.contains("juca")) {
                return;
            }
            for (ServerLevel level : sender.level().getServer().getAllLevels()) {
                for (JucelinoEntity juca : level.getEntitiesOfClass(JucelinoEntity.class,
                        sender.getBoundingBox().inflate(48.0))) {
                    JucelinoEntity j = juca;
                    synchronized (gagoQueue) {
                        gagoQueue.add(() -> j.ouvirChat(sender, level));
                    }
                }
            }
        });

        // ==================================================== A CAMISA DO MATANZA
        // v1.2.39: PODERES MATANZISTICOS DEMONIACOS — quem veste a camisa da
        // banda não queima (fire resistance permanente enquanto no peito).
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            MobEffectInstance chama = new MobEffectInstance(
                    MobEffects.FIRE_RESISTANCE, 220, 0, true, false, true);
            for (ServerPlayer jogador : server.getPlayerList().getPlayers()) {
                ItemStack peito = jogador.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
                if (peito.is(IntoxicantesMod.CAMISA_MATANZA)) {
                    jogador.addEffect(chama);
                }
                // v1.2.40 — SEM IMUNIDADE FANTASMA: tirou a camisa, o efeito
                // vai junto. O efeito da camisa é AMBIENT (poção de fogo
                // nunca é) — o fire res de poção/fogueira do jogador não é
                // tocado. O print do Skyu: banhou em lava e nem esquentou
                // 11s depois de guardar a camisa.
                else if (jogador.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                    MobEffectInstance atual = jogador.getEffect(MobEffects.FIRE_RESISTANCE);
                    if (atual != null && atual.isAmbient()) {
                        jogador.removeEffect(MobEffects.FIRE_RESISTANCE);
                    }
                }
            }
        });

        // Uma colheita por raiz; o apoio continua sendo a cerca original.
        UseBlockCallback.EVENT.register(UvaParreiraBlock::interagir);
        ServerChunkEvents.CHUNK_LOAD.register(UvaParreiraBlock::migrarChunk);

        // ======================================================== LETREIRO + PEDRADAS NO MERCADO
        // Direita no letreiro = fregues lendo o nome da loja: o Gago cumprimenta.
        // v1.2.18: só pra placa VANILLA (saves antigos); a placa nova cumprimenta
        // no próprio PlacaEsquinaoBlock.useWithoutItem
        UseBlockCallback.EVENT.register((player, level, mao, hit) -> {
            if (level.isClientSide()) return net.minecraft.world.InteractionResult.PASS;
            if (!(level.getBlockState(hit.getBlockPos()).getBlock()
                    instanceof net.minecraft.world.level.block.SignBlock)) {
                return net.minecraft.world.InteractionResult.PASS;
            }
            BlockPos posPlaca = hit.getBlockPos();
            var mercado = MarketSystem.getMarketPos();
            if (mercado == null || !posPlaca.closerThan(mercado, 24.0)) {
                return net.minecraft.world.InteractionResult.PASS;
            }
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                for (GagoEntity g : level.getEntitiesOfClass(GagoEntity.class,
                        sp.getBoundingBox().inflate(32.0))) {
                    g.cumprimentarLeitor((ServerLevel) level, sp);
                    break;
                }
            }
            return net.minecraft.world.InteractionResult.PASS; // nao consome: a placa abre a edicao normal
        });
        // Soco/bomba em bloco na area do mercado = vandalismo: Gago saca a 12 por 30s.
        // (quebrar bloco de verdade ja ativa o HurtByTargetGoal vanilla via dano? NAO —
        // quebrar bloco nao machuca ninguem, por isso o evento dedicado)
        // v1.2.75: a GÔNDOLA fica FORA da pedrada — o tapa do freguês (botão
        // esquerdo) é o GESTO DE COMPRA, não vandalismo. O TAPA cancela a
        // mineração; SHIFT+esquerdo ou criativo quebram normal (a obra).
        AttackBlockCallback.EVENT.register((player, level, mao, pos, direcao) -> {
            if (level.isClientSide()) return net.minecraft.world.InteractionResult.PASS;
            if (level.getBlockState(pos).getBlock() instanceof PrateleiraMercadoBlock) {
                if (player instanceof net.minecraft.server.level.ServerPlayer sp
                        && !sp.isShiftKeyDown() && !sp.getAbilities().instabuild) {
                    PrateleiraMercadoBlock.anotarTapado((ServerLevel) level, sp, pos, direcao);
                    return net.minecraft.world.InteractionResult.SUCCESS_SERVER;
                }
                return net.minecraft.world.InteractionResult.PASS; // obra: quebra normal
            }
            if (level.getBlockState(pos).getBlock() instanceof CaixaMercadoBlock) {
                return net.minecraft.world.InteractionResult.PASS; // tapa no caixa não é vandalismo
            }
            var mercado = MarketSystem.getMarketPos();
            if (mercado == null || !pos.closerThan(mercado, 16.0)) {
                return net.minecraft.world.InteractionResult.PASS;
            }
            if (player instanceof net.minecraft.server.level.ServerPlayer sp
                    && !sp.getAbilities().instabuild) { // criativo e obra, nao vandalismo kkkk
                for (GagoEntity g : level.getEntitiesOfClass(GagoEntity.class,
                        sp.getBoundingBox().inflate(32.0))) {
                    g.naPedrada((ServerLevel) level, sp);
                    break;
                }
            }
            return net.minecraft.world.InteractionResult.PASS; // o soco funciona normal
        });

        // ======================================================== DINHEIRO + MERCADO: persistencia
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            File worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile();
            ModConfig.init(server);
            PlayerMoney.init(worldDir);
            JogoDoBicho.init(worldDir);
            PeruSystem.init(worldDir);
            FidelidadeData.init(worldDir);
            // v1.2.10: embriaguez sobrevive a relog/restart (intoxicantes_embriaguez.json)
            Embriaguez.init(worldDir);
            // v1.2.54: saúde do fregues sobrevive a relog/restart (intoxicantes_saude.json)
            SaudeData.init(worldDir);
            MarketSystem.load(worldDir);
            // v1.2.51: flag "recebeu o guia" (intoxicantes_guia.json)
            GuiaPrimeiraVez.init(worldDir);
            LOGGER.info("[Intoxicantes] Sistema de dinheiro R$ e Mercado Esquinao inicializados.");
        });
        // Blindagem anti-Invulnerable: saves antigos (template com Invulnerable=1)
        // gravaram NPCs com escudo de dano que o hurt engole SEM LOG — "a 12 nao da
        // dano". No primeiro tick de cada NPC do mod, derruba o escudo.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((ent, level) -> {
            if (!level.isClientSide()
                    && (ent instanceof GagoEntity || ent instanceof TraficanteEntity)
                    && ent.isInvulnerable()) {
                ent.setPermanentlyInvulnerable(false);
                LOGGER.warn("[Intoxicantes] {} carregou com Invulnerable=1 — escudo derrubado",
                        ent.getName().getString());
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            File worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile();
            MarketSystem.save(worldDir);
        });

        // ======================================================== BEBIDAS: O LIVRO-RECEITAS
        // v1.2.50: o registro central dos processos (dorna, alambique, barris,
        // moenda, prensa, caldeirão). Chamar antes de qualquer máquina rodar.
        ProcessosBebida.registrar();
        // v1.2.60: o catálogo virou DATAPACK (JSON em
        // data/intoxicantes/processo_bebida/<maquina>/) com serializer próprio;
        // os defaults de fábrica continuam valendo se a pasta vier vazia.
        CatalogoBebidas.registrar();

        // ======================================================== WORLDGEN: MATOS SELVAGENS
        // Plantacoes abandonadas/plantas selvagens espalhadas pelo mundo.
        // Cada cultura tem seu bioma de preferencia, igual weed na natureza kkkk
        addWildPatches();

        // v1.2.58: a FEATURE do coqueiro (java) + o JSON de placed_feature que
        // o patch() acima injeta nas praias
        registrarFeature("coqueiro", CoqueiroFeature.CODEC);

        LOGGER.info("[Intoxicantes] Plantacoes, bebidas, NPCs, substancias e a economia R$ registradas. Boa esquina!");
    }

    // ============================================================ WORLDGEN
    /**
     * Plantações abandonadas espalhadas pelo mundo: cada cultura nasce num bioma
     * que combina com ela, em manchas pequenas e raras (achar é metade da diversão).
     * Os JSONs de feature/placed_feature ficam em data/intoxicantes/worldgen/.
     */
    private static void addWildPatches() {
        patch("maconha_selvagem",
                BiomeSelectors.includeByKey(
                        Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.FOREST, Biomes.BIRCH_FOREST));
        patch("lupulo_selvagem",
                BiomeSelectors.includeByKey(
                        Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST));
        patch("uva_selvagem",
                BiomeSelectors.includeByKey(
                        Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.WOODED_BADLANDS));
        patch("cafe_selvagem",
                BiomeSelectors.includeByKey(
                        Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA));
        patch("papoula_selvagem",
                BiomeSelectors.includeByKey(
                        Biomes.SWAMP, Biomes.SAVANNA, Biomes.MEADOW));
        // v1.2.50: a cevada selvagem (planícies e taiga — o cereal do frio)
        patch("cevada_selvagem",
                BiomeSelectors.includeByKey(
                        Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.TAIGA, Biomes.SNOWY_PLAINS));
        // v1.2.58: o coqueiro nas praias — a fonte do coco (e da água de coco)
        patch("coqueiro_praia",
                BiomeSelectors.includeByKey(
                        Biomes.BEACH, Biomes.JUNGLE, Biomes.STONY_SHORE));
    }

    /** Injeta a placed_feature (JSON) nos biomas selecionados, na etapa de vegetação. */
    private static void patch(String nome,
            java.util.function.Predicate<BiomeSelectionContext> seletor) {
        ResourceKey<PlacedFeature> chave = ResourceKey.create(Registries.PLACED_FEATURE,
                Identifier.fromNamespaceAndPath(MOD_ID, nome));
        BiomeModifications.addFeature(seletor,
                GenerationStep.Decoration.VEGETAL_DECORATION, chave);
    }

    /**
     * v1.2.58: registra uma feature JAVA (a do coqueiro) no registry do
     * worldgen — JSON sozinho não segura lógica de construção (tronco curvo,
     * coroa, cocos pendurados).
     */
    private static void registrarFeature(String nome, com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.levelgen.feature.Feature> codec) {
        Registry.register(BuiltInRegistries.FEATURE_TYPE,
                ResourceKey.create(Registries.FEATURE_TYPE,
                        Identifier.fromNamespaceAndPath(MOD_ID, nome)), codec);
    }

    // ============================================================ UV / LUZ
    /**
     * Luz "UV" pra maturação: sol forte (>= 12, sem chuva em cima) ou lampada UV
     * a ate 3 blocos de distancia.
     */
    public static boolean isUvLit(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        // Sol direto: luz >= 12, ceu aberto e sem chuva caindo no bloco (checagem mais barata)
        if (level.getRawBrightness(pos, 0) >= 12
                && level.canSeeSky(pos)
                && !level.isRainingAt(pos)) {
            return true;
        }
        return temLampadaUvPerto(level, pos);
    }

    /**
     * v1.2.15: a LÂMPADA UV sozinha — separada do isUvLit porque o BOOST DE
     * CRESCIMENTO agora é exclusivo dela (sol é sol; a lâmpada é a tecnologia
     * do mod e tem que valer o investimento — report do beta tester:
     * "as plantas crescem bem rápido"). Raio 2 (125 blocos vs 343).
     */
    public static boolean temLampadaUvPerto(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        // v1.2.17: raio configurável (uvRaio, padrão 2 = 5x5x5)
        int raio = Math.max(1, ModConfig.get().uvRaio);
        for (int dx = -raio; dx <= raio; dx++) {
            for (int dy = -raio; dy <= raio; dy++) {
                for (int dz = -raio; dz <= raio; dz++) {
                    var estado = level.getBlockState(pos.offset(dx, dy, dz));
                    // v1.2.17: só conta se a lâmpada estiver LIGADA (redstone
                    // cortou a energia? a maturação para no tempo)
                    if (estado.is(LAMPADA_UV)
                            && estado.getValueOrElse(LampadaUvBlock.LIT, Boolean.TRUE)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ============================================================ LOOT HELPER
    private static void addSeedPool(LootTable.Builder table, ResourceKey<LootTable> key,
                                    ResourceKey<LootTable> alvo, Item item, float chance) {
        if (!key.equals(alvo)) {
            return;
        }
        table.withPool(LootPool.lootPool()
                .when(LootItemRandomChanceCondition.randomChance(chance))
                .setRolls(Holder.direct(
                        new net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue(1)))
                .add(LootItem.lootTableItem(item)));
    }

    // ============================================================ FABRICAS DE ITEM
    private static MobEffectInstance effect(Holder<MobEffect> holder, int durationTicks, int amplifier) {
        return new MobEffectInstance(holder, durationTicks, amplifier);
    }

    /** Bebida SEM efeito (v1.2.54: suco detox e água de coco — a saúde aplica no consumo). */
    private static Item drink(String name) {
        return drink(name, new MobEffectInstance[0]);
    }

    /** Bebida: animação de beber + devolve A GARRAFA VAZIA DELA ao terminar. */
    private static Item drink(String name, MobEffectInstance... effects) {
        return bebida(name, effects, garrafaVazia(name));
    }

    /** Fábrica comum: bebida 3D que devolve o recipiente certo (v1.2.65). */
    private static Item bebida(String name, MobEffectInstance[] effects, Item vazio) {
        Consumable.Builder builder = Consumable.builder()
                .animation(ItemUseAnimation.DRINK)
                .sound(SoundEvents.GENERIC_DRINK);
        return registerBebida(name, comLore(name, new Item.Properties().stacksTo(16)
                .food(alwaysEdible(), withEffects(builder, effects))
                .component(net.minecraft.core.component.DataComponents.USE_REMAINDER,
                        new UseRemainder(new ItemStackTemplate(vazio)))));
    }

    /**
     * v1.2.65: a VERSÃO VAZIA de cada bebida (o recipiente devolvido ao acabar,
     * com a mesma identidade 3D). Se a cheia ainda não existir neste ponto do
     * registro, cai na garrafa de vidro vanilla — nunca null.
     */
    private static Item garrafaVazia(String name) {
        var holder = BuiltInRegistries.ITEM.get(itemKey(name + "_vazia"));
        if (holder.isPresent() && holder.get().value() instanceof GarrafaVaziaItem vazio) {
            return vazio;
        }
        return Items.GLASS_BOTTLE;
    }

    /** Lore de personalidade: cada consumível explica o nicho dele no tooltip. */
    private static Item.Properties comLore(String name, Item.Properties properties) {
        return properties.component(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                        net.minecraft.network.chat.Component.translatable(
                                "item.intoxicantes." + name + ".lore"))));
    }

    /**
     * v1.2.58: consumível com lore + efeitos de consumo + sobra (a garrafa).
     * O chá de lúpulo usa isto em vez do drink() padrão porque PRECISA limpar
     * a viagem ativa (RemoveStatusEffects) — coisa que o drink() não faz.
     * v1.2.65: a sobra agora é a garrafa vazia DA bebida.
     */
    private static Item comLoreConsumivel(String name, Item.Properties properties,
            Consumable consumavel) {
        return registerBebida(name, comLore(name, properties
                .food(new FoodProperties.Builder().alwaysEdible().build(), consumavel)
                .component(net.minecraft.core.component.DataComponents.USE_REMAINDER,
                        new UseRemainder(new ItemStackTemplate(garrafaVazia(name))))));
    }

    /** Mantém o ID e todos os componentes de consumo, acrescentando a colocação. */
    private static Item registerBebida(String name, Item.Properties properties) {
        return registerItem(name, new BebidaColocavelItem(properties.setId(itemKey(name))));
    }

    /** Expõe alimentos 3D sem alterar fome, efeitos, hidratação ou componentes do item. */
    private static Item registerAlimentoColocavel(String name, Item.Properties properties,
            BebidaColocavelItem.Exposicao exposicao) {
        return registerItem(name, new BebidaColocavelItem(properties.setId(itemKey(name)), exposicao,
                "item.intoxicantes.alimento_colocavel"));
    }

    /** Registra um SoundEvent (o sounds.json define quais .ogg ele toca). */
    private static SoundEvent registrarSom(String name) {
        return SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath(MOD_ID, name));
    }

    /** Registra um item ja construido (pra classes que nao sao Item puro). */
    private static Item registerItem(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, itemKey(name), item);
    }

    /**
     * Dinheiro da família R$: nota/moeda com VALOR FACIAL próprio (a fonte
     * da verdade fica no RealItem.VALORES — a ordem de registro aqui define
     * a ordem do troco). Empilha 64 como o resto do dinheiro.
     */
    private static Item dinheiro(String name, int valor) {
        Item item = registerItem(name,
                new RealItem(new Item.Properties().stacksTo(64).setId(itemKey(name))));
        RealItem.registrarValor(item, valor);
        return item;
    }

    /** Chave de item padrao. */
    private static ResourceKey<Item> itemKey(String name) {
        return ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(MOD_ID, name));
    }

    /** Registra entidade com ID setado no builder (obrigatorio nessa versao). */
    private static <T extends Entity> EntityType<T> registerEntity(String name,
            EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    /** Semente: BlockItem que planta o bloco, empilhável, nome próprio. */
    private static Item seedItem(String name, Block plant) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(MOD_ID, name));
        Item.Properties properties = new Item.Properties().stacksTo(64).setId(key);
        comLore(name, properties);
        return Registry.register(BuiltInRegistries.ITEM, key,
                new BlockItem(plant, properties));
    }

    /** Produto agricola: item simples. */
    private static Item product(String name) {
        return register(name, new Item.Properties().stacksTo(64));
    }

    /** Pó/substância: v1.2.53 — SPYGLASS (leva a mão ao nariz, como cheirar) em vez de EAT (comer): droga não é comida. */
    private static Item powder(String name, MobEffectInstance... effects) {
        Consumable.Builder builder = Consumable.builder()
                .animation(ItemUseAnimation.SPYGLASS)
                .consumeSeconds(1.6F)
                .sound(Holder.direct(SoundEvents.WOOL_STEP));  // rufar surdo do papel/ficato
        return register(name, comLore(name, new Item.Properties().stacksTo(16)
                .food(alwaysEdible(), withEffects(builder, effects))));
    }

    /** Comprimido/pílula. */
    private static Item pill(String name, MobEffectInstance... effects) {
        return powder(name, effects);
    }

    /** Erva pra fumar: v1.2.53 — BOW (a mão leva o baseado à boca e "puxa" como arco e flecha) + fósforo. */
    private static Item smoke(String name, MobEffectInstance... effects) {
        Holder<SoundEvent> flint = Holder.direct(SoundEvents.FLINTANDSTEEL_USE);
        Consumable.Builder builder = Consumable.builder()
                .animation(ItemUseAnimation.BOW) // puxada: o braço recua igual ao arco
                .consumeSeconds(2.2F)           // 3 puxadas curtas (o fumo não é golado)
                .sound(flint)
                .onConsume(new PlaySoundConsumeEffect(Holder.direct(SoundEvents.FIRE_EXTINGUISH)));
        return register(name, comLore(name, new Item.Properties().stacksTo(16)
                .food(alwaysEdible(), withEffects(builder, effects))));
    }

    private static FoodProperties alwaysEdible() {
        return new FoodProperties.Builder().alwaysEdible().build();
    }

    private static Consumable withEffects(Consumable.Builder builder, MobEffectInstance... effects) {
        builder.onConsume(new ApplyStatusEffectsConsumeEffect(List.of(effects), 1.0f));
        return builder.build();
    }

    private static Item register(String name, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(MOD_ID, name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
    }

    // ============================================================ BLOCOS
    /** Registra um bloco JA CONSTRUIDO (com comportamento proprio, ex. LampadaUvBlock) + o item. */
    private static Block registerBlockWithItem(String name, Block bloco) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath(MOD_ID, name));
        Registry.register(BuiltInRegistries.BLOCK, blockKey, bloco);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(MOD_ID, name));
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(bloco, new Item.Properties().setId(itemKey)
                        .useBlockDescriptionPrefix()));
        return bloco;
    }

    private static Block registerBlockWithItem(String name, BlockBehaviour.Properties properties) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath(MOD_ID, name));
        Block block = new Block(properties.setId(blockKey));
        Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(MOD_ID, name));
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(block, new Item.Properties().setId(itemKey)
                        .useBlockDescriptionPrefix()));
        return block;
    }

    /**
     * Barril de bebida (v1.2.50): 1 classe, 4 IDs — o rótulo nasce no ITEM
     * (DataComponent string) e o BE lê do bloco colocado. O loot e o craft
     * são por barril; as receitas de processo consultam o rótulo.
     */
    private static Block registerBarril(String name, String rotulo) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath(MOD_ID, name));
        Block bloco = new BarrilBebidaBlock(BlockBehaviour.Properties.of()
                .strength(1.8F)
                .sound(SoundType.WOOD)
                .noOcclusion()
                .mapColor(rotulo.equals("vinho")
                        ? net.minecraft.world.level.material.MapColor.COLOR_PURPLE
                        : net.minecraft.world.level.material.MapColor.WOOD)
                .setId(blockKey));
        Registry.register(BuiltInRegistries.BLOCK, blockKey, bloco);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(MOD_ID, name));
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(bloco,
                new Item.Properties().setId(itemKey)
                        .useBlockDescriptionPrefix()
                        .component(ROTULO_BARRIL, rotulo)));
        return bloco;
    }

    /** Registra só o bloco da cultura; a semente (BlockItem) vem depois. */
    private static Block registerCropBlock(String name, boolean aceitaSoloComum) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath(MOD_ID, name));
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .noCollision()
                .randomTicks()
                .instabreak()
                .sound(SoundType.CROP)
                .setId(blockKey);
        UvCropBlock crop = name.equals("uva_plant")
                ? new UvaParreiraBlock(properties)
                : new UvCropBlock(properties, aceitaSoloComum);
        Registry.register(BuiltInRegistries.BLOCK, blockKey, crop);
        return crop;
    }
}
