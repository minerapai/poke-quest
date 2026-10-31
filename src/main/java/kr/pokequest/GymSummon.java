package kr.pokequest;

import com.cobblemon.mod.common.battles.BattleRegistry;
import com.gitlab.srcmc.rctapi.api.models.PokemonModel;
import com.gitlab.srcmc.rctmod.api.RCTMod;
import com.gitlab.srcmc.rctmod.api.data.pack.TrainerMobData;
import com.gitlab.srcmc.rctmod.api.data.save.TrainerPlayerData;
import com.gitlab.srcmc.rctmod.api.service.TrainerManager;
import com.gitlab.srcmc.rctmod.world.entities.TrainerMob;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The next key trainer of the player's RCT series (gym leader, elite four, champion), summoned in front of them from
 * the quest screen: the real RCT trainer, so RCT runs the battle (level cap, progress, rewards) as anywhere else. It
 * stands only for its challenger and leaves when the battle is over.
 */
final class GymSummon {
    static final String TAG = "pokequest_gym";
    private static final double DISTANCE = 5;
    /** Seconds a summoned trainer waits for its battle to start / to end at most. */
    private static final int START_SECONDS = 10;
    private static final int MAX_SECONDS = 30 * 60;
    private static final Map<String, String> SERIES_NAMES = Map.of("kanto", "관동", "johto", "성도", "hoenn", "호연",
        "sinnoh", "신오", "unova", "하나", "kalos", "칼로스", "alola", "알로라", "galar", "가라르", "paldea", "팔데아");

    /** A summoned trainer and how long it has stood there. */
    private record Summoned(TrainerMob mob, UUID challenger, int[] seconds, boolean[] battled) {
    }

    private static final Map<UUID, Summoned> SUMMONED = new HashMap<>();

    private GymSummon() {
    }

    /** The next key trainer of a player's current series: its id and data, or null with no current series / all beaten. */
    static Map.Entry<String, TrainerMobData> next(ServerPlayerEntity player) {
        TrainerManager manager = RCTMod.getInstance().getTrainerManager();
        TrainerPlayerData progress = manager.getData(player);
        String series = progress.getCurrentSeries();

        if (series == null || series.isEmpty() || progress.isInEmptySeries()) {
            return null;
        }

        Set<String> defeated = progress.getDefeatedTrainerIds();
        return manager.getAllData().filter(e -> e.getValue().isOfSeries(series) && !e.getValue().isOptional()
                && !defeated.contains(e.getKey()) && unlocked(e.getValue().getRequiredDefeats(series), defeated))
            .findFirst().orElse(null);
    }

    /** Every group of required defeats has at least one trainer beaten. */
    private static boolean unlocked(List<Set<String>> required, Set<String> defeated) {
        return required.stream().allMatch(group -> group.isEmpty() || group.stream().anyMatch(defeated::contains));
    }

    static String seriesName(ServerPlayerEntity player) {
        String series = RCTMod.getInstance().getTrainerManager().getData(player).getCurrentSeries();
        return SERIES_NAMES.getOrDefault(series, series);
    }

    static Text name(TrainerMobData data) {
        return data.getTrainerTeam().getName().getComponent();
    }

    static int aceLevel(TrainerMobData data) {
        return data.getTrainerTeam().getTeam().stream().mapToInt(PokemonModel::getLevel).max().orElse(0);
    }

    static boolean waiting(UUID player) {
        return SUMMONED.values().stream().anyMatch(s -> s.challenger().equals(player));
    }

    /** Summons the next trainer in front of the player and lets it challenge them. Returns a problem, or null. */
    static String challenge(ServerPlayerEntity player) {
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
            return "배틀 중에는 부를 수 없습니다.";
        }

        if (waiting(player.getUuid())) {
            return "이미 불러낸 상대가 있습니다.";
        }

        Map.Entry<String, TrainerMobData> next = next(player);

        if (next == null) {
            return "도전할 다음 상대가 없습니다. 이 시리즈를 모두 이겼다면 트레이너 협회에서 다음 시리즈를 고르세요.";
        }

        ServerWorld world = player.getServerWorld();
        TrainerMob mob = TrainerMob.getEntityType().create(world);

        if (mob == null) {
            return "트레이너를 불러내지 못했습니다.";
        }

        double yaw = Math.toRadians(player.getYaw());
        double x = player.getX() - Math.sin(yaw) * DISTANCE;
        double z = player.getZ() + Math.cos(yaw) * DISTANCE;
        float facing = player.getYaw() + 180f;
        mob.setTrainerId(next.getKey());
        mob.setPersistent(true, true); // no despawning while it waits; RCT's spawner is not told, it is not one of its spawns
        mob.setAiDisabled(true);
        mob.setInvulnerable(true);
        mob.addCommandTag(TAG);
        Registries.ITEM.getOrEmpty(Identifier.of("mega_showdown", "omni_ring"))
            .ifPresent(ring -> mob.equipStack(EquipmentSlot.OFFHAND, new ItemStack(ring))); // Z moves, dynamax, ...
        mob.refreshPositionAndAngles(x, ground(world, x, player.getY(), z), z, facing, 0);
        mob.setHeadYaw(facing);
        mob.setBodyYaw(facing);
        SUMMONED.put(mob.getUuid(), new Summoned(mob, player.getUuid(), new int[1], new boolean[1]));

        if (!world.spawnEntity(mob)) {
            SUMMONED.remove(mob.getUuid());
            return "트레이너를 불러내지 못했습니다.";
        }

        player.sendMessage(Text.literal("[퀘스트] ").formatted(Formatting.GOLD).append(name(next.getValue()).copy()
            .formatted(Formatting.AQUA)).append(Text.literal(" 이(가) 나타났습니다!").formatted(Formatting.YELLOW)));
        mob.startBattleWith(player); // RCT says itself when the battle can not start (level cap, ...)
        PokeQuest.LOGGER.info("{} summoned {} to battle", player.getGameProfile().getName(), next.getKey());
        return null;
    }

    /** Every second: summoned trainers leave after their battle (or when it never starts / the player is gone). */
    static void tick(MinecraftServer server) {
        for (Summoned summoned : List.copyOf(SUMMONED.values())) {
            TrainerMob mob = summoned.mob();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(summoned.challenger());
            int seconds = ++summoned.seconds()[0];
            boolean inBattle = mob.isAlive() && mob.isInBattle();
            summoned.battled()[0] |= inBattle;

            boolean done = !mob.isAlive() || player == null || seconds > MAX_SECONDS
                || (summoned.battled()[0] && !inBattle) || (!summoned.battled()[0] && seconds > START_SECONDS);

            if (done) {
                SUMMONED.remove(mob.getUuid());
                mob.discard();
            }
        }
    }

    static boolean isSummoned(Entity entity) {
        return entity.getCommandTags().contains(TAG);
    }

    /** Summoned trainers left over from a restart are removed when they load again. */
    static boolean isLeftover(Entity entity) {
        return isSummoned(entity) && !SUMMONED.containsKey(entity.getUuid());
    }

    private static double ground(ServerWorld world, double x, double y, double z) {
        BlockPos.Mutable at = new BlockPos.Mutable();

        for (int dy = 3; dy >= -6; dy--) {
            at.set(MathHelper.floor(x), MathHelper.floor(y) + dy, MathHelper.floor(z));

            if (world.getBlockState(at.down()).isSideSolidFullSquare(world, at.down(), Direction.UP)
                && world.getBlockState(at).isAir() && world.getBlockState(at.up()).isAir()) {
                return at.getY();
            }
        }

        return y;
    }
}
