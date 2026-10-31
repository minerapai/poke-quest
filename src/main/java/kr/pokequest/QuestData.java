package kr.pokequest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Quest progress per player, saved in the world folder (pokequest.json). Also read by the achievements mod. */
public final class QuestData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Record {
        public String name = "";
        /** ids of the finished steps */
        public Set<String> done = new LinkedHashSet<>();
        /** rival battles won */
        public int rivalWins;
        /** checked at least once (the first check finishes past steps and skips past rival battles) */
        public boolean started;
    }

    private static Map<UUID, Record> records = new LinkedHashMap<>();
    private static Path file;

    private QuestData() {
    }

    /** The record of a player, or null when there is none yet. */
    public static Record get(UUID player) {
        return records.get(player);
    }

    static Record create(UUID player) {
        return records.computeIfAbsent(player, u -> new Record());
    }

    static void load(Path path) {
        file = path;
        records = new LinkedHashMap<>();

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Map<UUID, Record> read = GSON.fromJson(reader, new TypeToken<Map<UUID, Record>>() { }.getType());

                if (read != null) {
                    records.putAll(read);
                }
            } catch (Exception e) {
                PokeQuest.LOGGER.error("Could not read {}, starting empty", path, e);
            }
        }
    }

    static void save() {
        if (file == null) {
            return;
        }

        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");

            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(records, writer);
            }

            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            PokeQuest.LOGGER.error("Could not save {}", file, e);
        }
    }
}
