package kr.pokequest;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.npc.NPCClass;
import com.cobblemon.mod.common.api.npc.NPCClasses;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.api.pokemon.evolution.PreEvolution;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.ai.StrongBattleAI;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.gitlab.srcmc.rctapi.api.battle.BattleRules;
import com.gitlab.srcmc.rctapi.api.trainer.Trainer;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerBag;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerPlayer;
import com.gitlab.srcmc.rctmod.ModCommon;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The rival: battle n of the quest (1-9). Leads with the starter that beats the player's starter (same generation),
 * grown to the stage its level allows; the rest of the team is random, stronger with the level. Levels follow the
 * player's RCT level cap. The rival stands in front of the player for the battle (a Cobblemon NPC) and goes away
 * after it; its pokemon drop nothing (DeathDropsMixin).
 */
public final class Rival {
    /** Marks rival pokemon (persistent data) and the rival NPC (command tag). */
    public static final String TAG = "pokequest";
    private static final Random RANDOM = new Random();
    private static final double DISTANCE = 6;

    /** grass, fire, water starters of each generation */
    private static final String[][] STARTERS = {
        {"bulbasaur", "charmander", "squirtle"}, {"chikorita", "cyndaquil", "totodile"}, {"treecko", "torchic", "mudkip"},
        {"turtwig", "chimchar", "piplup"}, {"snivy", "tepig", "oshawott"}, {"chespin", "fennekin", "froakie"},
        {"rowlet", "litten", "popplio"}, {"grookey", "scorbunny", "sobble"}, {"sprigatito", "fuecoco", "quaxly"}};
    private static final Set<String> BANNED = Set.of("shedinja", "ditto", "smeargle", "wobbuffet", "wynaut", "unown");

    private record Pending(UUID player, int number, long startedAt, List<Entity> npcs) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Set<Entity> NPCS = new HashSet<>();

    private Rival() {
    }

    public static boolean inBattle(ServerPlayerEntity player) {
        return PENDING.values().stream().anyMatch(p -> p.player().equals(player.getUuid()));
    }

    /** Starts rival battle number n. Returns a problem, or null when it started. */
    public static String start(ServerPlayerEntity player, int number, String name) {
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
            return "배틀 중에는 라이벌과 싸울 수 없습니다.";
        }

        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        List<Pokemon> healthy = new ArrayList<>();

        for (int slot = 0; slot < party.size(); slot++) {
            Pokemon pokemon = party.get(slot);

            if (pokemon != null && !pokemon.isFainted()) {
                healthy.add(pokemon);
            }
        }

        if (healthy.isEmpty()) {
            return "싸울 수 있는 포켓몬이 없습니다. 먼저 회복하세요.";
        }

        int level = Math.max(5, Math.min(100, QuestConditions.levelCap(player)));
        Pokemon[] team = team(player, number, level);
        List<Entity> npcs = new ArrayList<>();
        TrainerNPC rival = new TrainerNPC(com.gitlab.srcmc.rctapi.api.util.Text.literal(name), team, new TrainerBag(),
            new StrongBattleAI(Math.min(5, 1 + number / 2)), entity(player, name, npcs));
        // both sides at the rival's level (copies, as the battle tower does): the level sync of RCT battles only covers
        // gym leaders and the like, so pokemon raised in earlier regions would otherwise walk over the rival
        BattleRules rules = new BattleRules.Builder().withMaxItemUses(number >= 4 ? 2 : 0).withAdjustPlayerLevels(true)
            .withAdjustNPCLevels(true).build();
        BattleFormat base = BattleFormat.Companion.getGEN_9_SINGLES();
        BattleFormat format = base.copy(base.getMod(), base.getBattleType(), base.getRuleSet(), base.getGen(), level);
        UUID battle = ModCommon.RCT.getBattleManager().startBattle(List.<Trainer>of(new TrainerPlayer(player)),
            List.<Trainer>of(rival), (com.gitlab.srcmc.rctapi.api.battle.BattleFormatProvider) () -> format, rules);

        if (battle == null) {
            discard(npcs);
            return "배틀을 시작하지 못했습니다. (서버 로그 참고)";
        }

        PENDING.put(battle, new Pending(player.getUuid(), number, System.currentTimeMillis(), npcs));
        player.sendMessage(Text.literal(name + ": 기다리고 있었어! 실력이 얼마나 늘었는지 보자!").formatted(Formatting.AQUA));
        PokeQuest.LOGGER.info("rival battle {} for {} at level {}", number, player.getGameProfile().getName(), level);
        return null;
    }

    // team

    private static Pokemon[] team(ServerPlayerEntity player, int number, int level) {
        int size = Math.min(6, number + 1);
        List<Pokemon> team = new ArrayList<>();
        Set<String> used = new HashSet<>();
        String ace = grown(counterStarter(player), level);
        used.add(ace);

        while (team.size() < size - 1) {
            Species species = randomSpecies(level, used);

            if (species == null) {
                break;
            }

            used.add(species.getResourceIdentifier().getPath());
            team.add(create(species.getResourceIdentifier().getPath(), Math.max(1, level - 2 - RANDOM.nextInt(3)), number));
        }

        team.add(create(ace, level, number)); // the ace comes last
        return team.toArray(new Pokemon[0]);
    }

    private static Pokemon create(String species, int level, int number) {
        Pokemon pokemon = PokemonProperties.Companion.parse("species=" + species + " level=" + level).create();
        int ivs = Math.min(31, 10 + number * 2);
        for (Stats stat : new Stats[]{Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED}) {
            pokemon.getIvs().set(stat, ivs);
        }

        pokemon.getPersistentData().putBoolean(TAG, true); // no death drops (DeathDropsMixin)
        pokemon.heal();
        return pokemon;
    }

    /** The starter (base stage) that beats the player's starter, from the same generation. */
    private static String counterStarter(ServerPlayerEntity player) {
        String root = root(starterSpecies(player));

        for (String[] trio : STARTERS) {
            for (int i = 0; i < 3; i++) {
                if (trio[i].equals(root)) {
                    return trio[(i + 1) % 3]; // grass -> fire, fire -> water, water -> grass
                }
            }
        }

        return STARTERS[0][RANDOM.nextInt(3)];
    }

    /** Species of the player's starter (Cobblemon remembers its uuid), or null when it is gone. */
    private static Species starterSpecies(ServerPlayerEntity player) {
        UUID starter = Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(player).getStarterUUID();

        if (starter == null) {
            return null;
        }

        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);

        for (Pokemon pokemon : party) {
            if (pokemon.getUuid().equals(starter)) {
                return pokemon.getSpecies();
            }
        }

        PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);

        for (Pokemon pokemon : pc) {
            if (pokemon.getUuid().equals(starter)) {
                return pokemon.getSpecies();
            }
        }

        return null;
    }

    private static String root(Species species) {
        Species current = species;

        for (int i = 0; current != null && i < 3; i++) {
            PreEvolution pre = current.getPreEvolution();

            if (pre == null) {
                break;
            }

            current = pre.getSpecies();
        }

        return current != null ? current.getResourceIdentifier().getPath() : "";
    }

    /** The evolution stage a starter line reaches by a level (second stage from 16, last from 36). */
    private static String grown(String base, int level) {
        Species species = PokemonSpecies.INSTANCE.getByName(base);

        for (int threshold : new int[]{16, 36}) {
            if (species == null || level < threshold || species.getEvolutions().isEmpty()) {
                break;
            }

            Evolution evolution = species.getEvolutions().iterator().next();
            String next = evolution.getResult().getSpecies();
            Species evolved = next != null ? PokemonSpecies.INSTANCE.getByName(next.contains(":") ? next.substring(next.indexOf(':') + 1) : next) : null;

            if (evolved == null) {
                break;
            }

            species = evolved;
        }

        return species != null ? species.getResourceIdentifier().getPath() : base;
    }

    /** A random team mate fitting the level: early on weak species, later fully evolved strong ones. */
    private static Species randomSpecies(int level, Set<String> used) {
        int min = level < 20 ? 250 : level < 35 ? 330 : level < 50 ? 420 : 480;
        int max = level < 20 ? 400 : level < 35 ? 480 : level < 50 ? 540 : 600;
        List<Species> pool = new ArrayList<>();

        for (Species species : PokemonSpecies.INSTANCE.getImplemented()) {
            Set<String> labels = species.getLabels();
            int bst = species.getStandardForm().getBaseStats().values().stream().mapToInt(Integer::intValue).sum();

            if (bst >= min && bst <= max && !labels.contains("legendary") && !labels.contains("mythical")
                && !labels.contains("restricted") && !BANNED.contains(species.getResourceIdentifier().getPath())
                && !used.contains(species.getResourceIdentifier().getPath())
                && (level < 35 || species.getStandardForm().getEvolutions().isEmpty())) {
                pool.add(species);
            }
        }

        return pool.isEmpty() ? null : pool.get(RANDOM.nextInt(pool.size()));
    }

    // the NPC

    private static LivingEntity entity(ServerPlayerEntity player, String name, List<Entity> spawned) {
        ServerWorld world = player.getServerWorld();
        double yaw = Math.toRadians(player.getYaw());
        double x = player.getX() - Math.sin(yaw) * DISTANCE;
        double z = player.getZ() + Math.cos(yaw) * DISTANCE;
        double y = ground(world, x, player.getY(), z);
        float facing = player.getYaw() + 180f;
        LivingEntity entity = null;

        try {
            NPCClass npcClass = NPCClasses.getByIdentifier(Identifier.of("cobblemon", "standard"));

            if (npcClass != null) {
                NPCEntity npc = new NPCEntity(world);
                npc.setNpc(npcClass);
                npc.initialize(1);
                npc.setMovable(false);
                npc.setInvulnerable(true);
                npc.setCustomNameVisible(true);
                npc.addCommandTag(TAG);
                entity = npc;
            }
        } catch (RuntimeException e) {
            PokeQuest.LOGGER.warn("could not create the rival NPC", e);
        }

        if (entity == null) { // an invisible stand in that is never spawned
            entity = EntityType.ARMOR_STAND.create(world);
        }

        // must stand off the player: Cobblemon sends the pokemon out between the two (same spot: NaN positions)
        entity.refreshPositionAndAngles(x, y, z, facing, 0);
        entity.setHeadYaw(facing);
        entity.setBodyYaw(facing);
        entity.setCustomName(Text.literal(name));

        if (entity instanceof NPCEntity) {
            NPCS.add(entity);

            if (world.spawnEntity(entity)) {
                spawned.add(entity);
            } else {
                NPCS.remove(entity);
            }
        }

        return entity;
    }

    private static double ground(ServerWorld world, double x, double y, double z) {
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int dy = 2; dy >= -4; dy--) {
            pos.set(MathHelper.floor(x), MathHelper.floor(y) + dy, MathHelper.floor(z));

            if (world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP)
                && world.getBlockState(pos).isAir() && world.getBlockState(pos.up()).isAir()) {
                return pos.getY();
            }
        }

        return y;
    }

    private static void discard(List<Entity> npcs) {
        npcs.forEach(Entity::discard);
        npcs.forEach(NPCS::remove);
    }

    /** Rival NPCs left over from a battle that was cut off (crash, restart) are removed when they load again. */
    public static boolean isLeftover(Entity entity) {
        return entity.getCommandTags().contains(TAG) && !NPCS.contains(entity);
    }

    // results

    public static void onVictory(MinecraftServer server, PokemonBattle battle, List<BattleActor> winners) {
        Pending pending = PENDING.remove(battle.getBattleId());

        if (pending != null) {
            discard(pending.npcs());
            boolean won = winners.stream().anyMatch(a -> a instanceof PlayerBattleActor p && p.getUuid().equals(pending.player()));
            PokeQuest.rivalResult(server, pending.player(), pending.number(), won);
        }
    }

    /** Battles that ended without a victory (left, forfeited): a loss. */
    public static void tick(MinecraftServer server) {
        for (Map.Entry<UUID, Pending> entry : new ArrayList<>(PENDING.entrySet())) {
            if (System.currentTimeMillis() - entry.getValue().startedAt() > 5000 && BattleRegistry.getBattle(entry.getKey()) == null) {
                PENDING.remove(entry.getKey());
                discard(entry.getValue().npcs());
                PokeQuest.rivalResult(server, entry.getValue().player(), entry.getValue().number(), false);
            }
        }
    }
}
