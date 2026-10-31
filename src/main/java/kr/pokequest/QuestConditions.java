package kr.pokequest;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.gitlab.srcmc.rctmod.api.RCTMod;
import com.gitlab.srcmc.rctmod.api.data.save.TrainerPlayerData;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;

/** Progress of a player on a quest step (see QuestStep for the types); rival steps are settled by Rival. */
public final class QuestConditions {
    private QuestConditions() {
    }

    public static long progress(ServerPlayerEntity player, QuestStep step, QuestData.Record record) {
        return switch (step.type()) {
            case "stat" -> stat(player, step.key());
            case "dex" -> caught(player);
            case "defeat" -> defeated(player, step.key()) ? 1 : 0;
            case "series" -> rct(player).getCompletedSeries().getOrDefault(step.key(), 0);
            case "rival" -> record.rivalWins >= step.target() ? step.target() : record.rivalWins;
            default -> 0;
        };
    }

    public static boolean done(ServerPlayerEntity player, QuestStep step, QuestData.Record record) {
        return progress(player, step, record) >= step.target();
    }

    private static TrainerPlayerData rct(ServerPlayerEntity player) {
        return RCTMod.getInstance().getTrainerManager().getData(player);
    }

    /** Beaten in the current series, or its whole series (the trainer id starts with it: "kanto_brock") is done. */
    private static boolean defeated(ServerPlayerEntity player, String trainer) {
        TrainerPlayerData data = rct(player);

        if (data.getDefeatedTrainerIds().contains(trainer)) {
            return true;
        }

        String series = trainer.contains("_") ? trainer.substring(0, trainer.indexOf('_')) : trainer;
        return data.getCompletedSeries().getOrDefault(series, 0) > 0;
    }

    private static long stat(ServerPlayerEntity player, String key) {
        Identifier id = Identifier.tryParse(key);

        if (id == null || !Registries.CUSTOM_STAT.containsId(id)) {
            return 0;
        }

        return player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Registries.CUSTOM_STAT.get(id)));
    }

    private static long caught(ServerPlayerEntity player) {
        PokedexManager dex = Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(player);
        return dex.getSpeciesRecords().keySet().stream()
            .filter(id -> dex.getKnowledgeForSpecies(id) == PokedexEntryProgress.CAUGHT).count();
    }

    /** The player's RCT level cap (rival levels follow it). */
    public static int levelCap(ServerPlayerEntity player) {
        return rct(player).getLevelCap();
    }
}
