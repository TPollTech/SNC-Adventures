package com.intoxicantes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;

/** Reservas globais incluem NPCs em chunks descarregados. Nenhum chunk é forçado. */
public final class PeruSystem {
    private PeruSystem() {}
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Reserva> reservas = new ArrayList<>();
    private static final Map<UUID, Integer> ausente = new HashMap<>();
    private static Path arquivo;
    private static long relogio;
    private static int contador;
    private static boolean persistenciaPendente;
    private static long ultimoErroLog;
    private static final class Reserva {
        UUID peru, moto;
        int x, y, z, motoX, motoY, motoZ;
        boolean ativo = true;
        long liberaEm;
    }
    private record Salvo(int formato, List<Reserva> reservas) {}
    public static void init(File mundo) {
        reset(); arquivo = mundo.toPath().resolve("intoxicantes_peru.json");
        if (!Files.exists(arquivo)) return;
        try {
            Salvo s = JSON.fromJson(Files.readString(arquivo), Salvo.class);
            if (s == null || s.formato != 1 || s.reservas == null)
                throw new IOException("Formato inválido das reservas do Peru");
            // Lápides legítimas acumulam com mortes: 64 não é limite de vida útil de um mundo.
            Set<UUID> identificadores = new HashSet<>();
            for (Reserva r : s.reservas) {
                if (r == null || r.peru == null || r.moto == null || r.liberaEm < 0
                        || !identificadores.add(r.peru) || !identificadores.add(r.moto))
                    throw new IOException("Reserva incompleta ou UUID duplicado");
                reservas.add(r);
            }
        } catch (Exception e) { throw new IllegalStateException("Não foi possível ler " + arquivo, e); }
    }
    public static void reset() {
        reservas.clear(); ausente.clear(); arquivo = null; contador = 0; relogio = 0;
        persistenciaPendente = false; ultimoErroLog = 0;
    }
    private static boolean salvar() {
        if (arquivo == null) return false;
        try {
            PlayerMoney.gravarAtomico(arquivo, new Salvo(1, reservas));
            persistenciaPendente = false;
            return true;
        } catch (IOException e) {
            persistenciaPendente = true;
            long agora = System.nanoTime();
            if (ultimoErroLog == 0 || agora - ultimoErroLog > 30_000_000_000L) {
                ultimoErroLog = agora;
                IntoxicantesMod.LOGGER.error("[SNC Adventures] Reservas do Peru não persistiram; spawn suspenso e dados mantidos", e);
            }
            return false;
        }
    }
    public static void observar(MotoPeruEntity moto) {
        if (!(moto.level() instanceof ServerLevel nivel) || nivel != nivel.getServer().overworld()) return;
        for (Reserva r : reservas) if (r.ativo && r.moto.equals(moto.getUUID())) {
            BlockPos p = moto.blockPosition();
            if (r.motoX != p.getX() || r.motoY != p.getY() || r.motoZ != p.getZ()) {
                r.motoX = p.getX(); r.motoY = p.getY(); r.motoZ = p.getZ(); salvar();
            }
            return;
        }
    }
    public static void observar(PeruEntity peru) {
        if (!(peru.level() instanceof ServerLevel nivel) || nivel != nivel.getServer().overworld()) return;
        for (Reserva r : reservas) if (r.ativo && r.peru.equals(peru.getUUID())) {
            BlockPos p = peru.blockPosition();
            MotoPeruEntity moto = peru.moto();
            BlockPos m = moto != null ? moto.blockPosition() : new BlockPos(r.motoX, r.motoY, r.motoZ);
            if (r.x != p.getX() || r.y != p.getY() || r.z != p.getZ()
                    || r.motoX != m.getX() || r.motoY != m.getY() || r.motoZ != m.getZ()) {
                r.x = p.getX(); r.y = p.getY(); r.z = p.getZ();
                r.motoX = m.getX(); r.motoY = m.getY(); r.motoZ = m.getZ(); salvar();
            }
            return;
        }
    }
    public static void retiring(UUID peru) {
        for (Reserva r : reservas) if (r.ativo && r.peru.equals(peru)) {
            r.ativo = false; r.liberaEm = relogio + ModConfig.get().peruCooldownTicks;
            ausente.remove(peru); salvar(); return;
        }
    }
    public static void tick(MinecraftServer server) {
        ServerLevel sl = server.overworld(); relogio = sl.getGameTime();
        if (++contador % 20 != 0) return;
        boolean mudou = false;
        for (Reserva r : reservas) {
            Entity peru = sl.getEntityInAnyDimension(r.peru), moto = sl.getEntityInAnyDimension(r.moto);
            if (r.ativo && ((peru != null && peru.level() != sl) || (moto != null && moto.level() != sl))) {
                r.ativo = false; r.liberaEm = relogio + ModConfig.get().peruCooldownTicks;
                ausente.remove(r.peru); mudou = true;
            }
            if (!r.ativo) {
                // Contraparte reaparece mais tarde: continua sendo a mesma reserva encerrada.
                if (peru instanceof PeruEntity p) { p.estacionar(); p.discard(); }
                if (moto instanceof MotoPeruEntity m) m.discard();
                continue;
            }
            // Terreno carregado não significa que o armazenamento de entidades terminou de abrir.
            boolean peruCarregado = sl.areEntitiesLoaded(ChunkPos.pack(new BlockPos(r.x, r.y, r.z)));
            boolean motoCarregada = sl.areEntitiesLoaded(ChunkPos.pack(new BlockPos(r.motoX, r.motoY, r.motoZ)));
            if ((peru == null && peruCarregado) || (moto == null && motoCarregada)) {
                int ticks = ausente.merge(r.peru, 20, Integer::sum);
                if (ticks >= 200) {
                    r.ativo = false; r.liberaEm = relogio + ModConfig.get().peruCooldownTicks;
                    mudou = true;
                }
            } else ausente.remove(r.peru);
        }
        // Mantém lápides: um chunk antigo pode carregar a contraparte muito depois do cooldown.
        if ((mudou || persistenciaPendente) && !salvar()) return;
        ModConfig c = ModConfig.get();
        if (!c.peruSpawnAtivo || contador % 200 != 0
                || reservas.stream().filter(r -> r.ativo).count() >= c.peruMaximoNoMundo) return;
        List<ServerPlayer> jogadores = server.getPlayerList().getPlayers().stream()
                .filter(p -> p.level() == sl && p.isAlive() && !p.isSpectator()).toList();
        if (jogadores.isEmpty()) return;
        for (ServerPlayer p : jogadores) {
            if (ocupada(p.blockPosition(), c.peruSeparacaoRegioes)) continue;
            BlockPos local = procurar(sl, p, jogadores);
            if (local != null) { criar(sl, local); return; }
        }
    }
    private static boolean ocupada(BlockPos p, int raio) {
        for (Reserva r : reservas) if ((r.ativo || relogio < r.liberaEm)
                && p.distSqr(new BlockPos(r.x, r.y, r.z)) < (double) raio * raio) return true;
        return false;
    }
    private record Candidato(BlockPos pos, int prioridade) {}
    private static BlockPos procurar(ServerLevel sl, ServerPlayer jogador, List<ServerPlayer> todos) {
        ModConfig c = ModConfig.get(); List<Candidato> candidatos = new ArrayList<>();
        BlockPos mercado = MarketSystem.getMarketPos();
        for (int tentativa = 0; tentativa < 56; tentativa++) {
            double angulo = sl.getRandom().nextDouble() * Math.PI * 2;
            double raio = c.peruDistanciaMinima + sl.getRandom().nextDouble()
                    * (c.peruDistanciaMaxima - c.peruDistanciaMinima);
            int x = (int) Math.floor(jogador.getX() + Math.sin(angulo) * raio);
            int z = (int) Math.floor(jogador.getZ() + Math.cos(angulo) * raio);
            BlockPos consulta = new BlockPos(x, jogador.blockPosition().getY(), z);
            if (!sl.hasChunkAt(consulta)) continue;
            BlockPos pos = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, consulta);
            if (!adequado(sl, pos) || ocupada(pos, c.peruSeparacaoRegioes)) continue;
            Vec3 motoPos = Vec3.atBottomCenterOf(pos), peruPos = motoPos.add(1.55, 0, 0);
            boolean perto = todos.stream().anyMatch(p -> Math.min(p.distanceToSqr(motoPos), p.distanceToSqr(peruPos))
                    < (double) c.peruDistanciaMinima * c.peruDistanciaMinima);
            if (perto) continue;
            if (Math.max(jogador.distanceToSqr(motoPos), jogador.distanceToSqr(peruPos))
                    > (double) c.peruDistanciaMaxima * c.peruDistanciaMaxima) continue;
            String piso = BuiltInRegistries.BLOCK.getKey(sl.getBlockState(pos.below()).getBlock()).getPath();
            int prioridade = mercado != null && mercado.distSqr(pos) < 64 * 64 ? 0
                    : sl.isCloseToVillage(pos, 2) ? 1
                    : sl.getBlockState(pos.below()).is(Blocks.DIRT_PATH) || piso.contains("asfalto") ? 2 : 3;
            if (prioridade == 3 && todos.stream().anyMatch(p -> {
                if (p.distanceToSqr(peruPos) > (double) c.peruDistanciaMaxima * c.peruDistanciaMaxima * 4) return false;
                Vec3 direcao = Vec3.atCenterOf(pos).subtract(p.getEyePosition()).normalize();
                return p.getLookAngle().dot(direcao) > .25;
            })) continue;
            candidatos.add(new Candidato(pos, prioridade));
        }
        return candidatos.stream().min(Comparator.comparingInt(Candidato::prioridade))
                .map(Candidato::pos).orElse(null);
    }
    public static boolean adequado(ServerLevel sl, BlockPos pos) {
        if (!sl.hasChunkAt(pos) || !sl.canSeeSky(pos) || sl.getMaxLocalRawBrightness(pos) < 8
                || !sl.getWorldBorder().isWithinBounds(pos)) return false;
        for (int x = -1; x <= 2; x++) for (int z = -1; z <= 1; z++) {
            BlockPos pe = pos.offset(x, 0, z), piso = pe.below();
            if (!sl.hasChunkAt(pe) || !sl.getFluidState(pe).isEmpty()
                    || !MotoPeruEntity.apoioFirme(sl, piso)
                    || sl.getBlockState(piso).is(BlockTags.LEAVES)
                    || sl.getBlockState(piso).is(BlockTags.LOGS)) return false;
            // Materiais usuais de terreno/rua, nunca um telhado aleatório.
            var s = sl.getBlockState(piso);
            String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
            if (!(s.is(BlockTags.DIRT) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.STONE)
                    || s.is(Blocks.COBBLESTONE) || s.is(Blocks.GRAVEL) || s.is(Blocks.SAND)
                    || s.is(Blocks.RED_SAND) || id.contains("asfalto"))) return false;
            // Um teto de pedra também vê o céu: exigir terreno/aterro sob a plataforma.
            for (int profundidade = 1; profundidade <= 2; profundidade++)
                if (!sl.getBlockState(piso.below(profundidade)).isSolidRender()
                        || !sl.getFluidState(piso.below(profundidade)).isEmpty()) return false;
        }
        AABB espaco = new AABB(pos.getX() - .6, pos.getY() + .01, pos.getZ() - .6,
                pos.getX() + 3, pos.getY() + 2.5, pos.getZ() + 1.6);
        return sl.hasChunksAt(BlockPos.containing(espaco.minX, espaco.minY, espaco.minZ),
                        BlockPos.containing(espaco.maxX, espaco.maxY, espaco.maxZ))
                && sl.getWorldBorder().isWithinBounds(espaco) && !sl.containsAnyLiquid(espaco)
                && sl.noCollision(espaco) && sl.getEntities((Entity) null, espaco).isEmpty();
    }
    private static void criar(ServerLevel sl, BlockPos pos) {
        PeruEntity peru = new PeruEntity(IntoxicantesMod.PERU, sl);
        MotoPeruEntity moto = new MotoPeruEntity(IntoxicantesMod.MOTO_PERU, sl);
        moto.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        peru.setPos(pos.getX() + 2.05, pos.getY(), pos.getZ() + .5);
        peru.vincularMoto(moto, true);
        Reserva r = new Reserva(); r.peru = peru.getUUID(); r.moto = moto.getUUID();
        r.x = peru.blockPosition().getX(); r.y = pos.getY(); r.z = pos.getZ();
        r.motoX = pos.getX(); r.motoY = pos.getY(); r.motoZ = pos.getZ(); reservas.add(r);
        if (!salvar()) { reservas.remove(r); return; }
        if (!sl.addFreshEntity(moto) || !sl.addFreshEntity(peru)) {
            peru.discard(); moto.discard(); retiring(r.peru);
        }
    }
}
