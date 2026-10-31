package kr.pokequest;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import kotlin.Unit;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Guide quest: steps from the first catch to the Sinnoh champion (gyms, leagues, pokedex, evolving, eggs) done in
 * order, each with a reward; at some steps the rival waits (/quest to battle). Checked every few seconds; a player's
 * first check finishes everything already done (rival battles there are skipped). Messages are plain Korean text
 * since clients do not have this mod.
 */
public class PokeQuest implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("pokequest");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int CHECK_INTERVAL_TICKS = 100;

    /** config/poke-quest.json */
    public static final class Config {
        public String rivalName = "라이벌 진";
        public List<QuestStep> steps = QuestStep.defaults();
    }

    private static Config config = new Config();
    private static MinecraftServer server;
    /** players who just joined: remind them of the current step at the next check */
    private static final Set<UUID> JOINED = new HashSet<>();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(started -> {
            server = started;
            config = loadConfig();
            QuestData.load(started.getSavePath(WorldSavePath.ROOT).resolve("pokequest.json"));
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> {
            QuestData.save();
            server = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(ticking -> {
            if (server == null) {
                return;
            }

            if (ticking.getTicks() % 20 == 0) {
                Rival.tick(ticking);
                GymSummon.tick(ticking);
            }

            if (ticking.getTicks() % CHECK_INTERVAL_TICKS == 0) {
                ticking.getPlayerManager().getPlayerList().forEach(PokeQuest::check);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) -> JOINED.add(handler.getPlayer().getUuid()));
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, event -> {
            if (server != null) {
                Rival.onVictory(server, event.getBattle(), event.getWinners());
            }

            return Unit.INSTANCE;
        });
        // the rival NPC only stands there: no talking to it, and none left behind after a crash
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
            entity.getCommandTags().contains(Rival.TAG) || GymSummon.isSummoned(entity) ? ActionResult.FAIL : ActionResult.PASS);
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (Rival.isLeftover(entity) || GymSummon.isLeftover(entity)) {
                entity.discard();
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            for (String root : new String[] {"series", "시리즈"}) {
                dispatcher.register(CommandManager.literal(root).executes(ctx -> SeriesMenu.open(ctx.getSource().getPlayerOrThrow()))
                    // OP (tests): any series for a player, without its required series
                    .then(CommandManager.literal("force").requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("series", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .executes(ctx -> SeriesMenu.force(ctx.getSource(), ctx.getSource().getPlayerOrThrow(),
                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "series")))
                            .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(ctx -> SeriesMenu.force(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"),
                                    com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "series")))))));
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(CommandManager.literal("quest")
                .executes(ctx -> QuestMenu.open(ctx.getSource().getPlayerOrThrow()))
                .then(CommandManager.literal("rival")
                    .executes(ctx -> rival(ctx.getSource().getPlayerOrThrow())))
                .then(CommandManager.literal("reset")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> {
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                            QuestData.Record record = QuestData.create(target.getUuid());
                            record.done.clear();
                            record.rivalWins = 0;
                            record.started = false;
                            QuestData.save();
                            ctx.getSource().sendFeedback(() -> Text.literal(target.getGameProfile().getName()
                                + "의 퀘스트를 처음부터 다시 시작합니다. (이미 한 단계는 다시 완료되며 보상도 다시 받습니다)"), true);
                            return 1;
                        })))
                .then(CommandManager.literal("reload")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(ctx -> {
                        config = loadConfig();
                        ctx.getSource().sendFeedback(() -> Text.literal("퀘스트 설정을 다시 읽었습니다: " + config.steps.size() + "단계"), true);
                        return 1;
                    }))));
    }

    // steps

    public static List<QuestStep> steps() {
        return config.steps;
    }

    public static String rivalName() {
        return config.rivalName;
    }

    public static List<String> regions() {
        List<String> regions = new ArrayList<>();
        config.steps.forEach(s -> {
            if (!regions.contains(s.region())) {
                regions.add(s.region());
            }
        });
        return regions;
    }

    /** The first unfinished step, or null when all are done. */
    public static QuestStep current(ServerPlayerEntity player) {
        QuestData.Record record = QuestData.create(player.getUuid());
        return config.steps.stream().filter(s -> !record.done.contains(s.id())).findFirst().orElse(null);
    }

    private static void check(ServerPlayerEntity player) {
        QuestData.Record record = QuestData.create(player.getUuid());
        boolean first = !record.started;
        record.started = true;
        record.name = player.getGameProfile().getName();
        List<QuestStep> finished = new ArrayList<>();
        List<QuestStep> steps = config.steps;

        for (int i = 0; i < steps.size(); i++) {
            QuestStep step = steps.get(i);

            if (record.done.contains(step.id())) {
                continue;
            }

            boolean done = QuestConditions.done(player, step, record)
                || first && "rival".equals(step.type()) && laterStepDone(player, record, i); // past the rival already

            if (!done) {
                break;
            }

            record.done.add(step.id());
            finished.add(step);
            reward(player, step);
        }

        QuestStep current = current(player);

        if (first || !finished.isEmpty()) {
            QuestData.save();
        }

        if (!finished.isEmpty()) {

            if (first && finished.size() > 1) {
                player.sendMessage(Text.literal("[퀘스트] 지금까지의 진행으로 " + finished.size() + "단계를 완료하고 보상을 받았습니다!")
                    .formatted(Formatting.GOLD));
            } else {
                for (QuestStep step : finished) {
                    player.sendMessage(Text.literal("[퀘스트 완료] " + step.name() + "! ").formatted(Formatting.GOLD)
                        .append(Text.literal("보상을 받았습니다.").formatted(Formatting.GREEN)));
                }
            }
        }

        if (!finished.isEmpty() || JOINED.remove(player.getUuid())) {
            remind(player, current);
        }
    }

    /** Whether a non-rival step after index i is already done (then the rival there was passed long ago). */
    private static boolean laterStepDone(ServerPlayerEntity player, QuestData.Record record, int index) {
        for (int j = index + 1; j < config.steps.size(); j++) {
            QuestStep later = config.steps.get(j);

            if (!"rival".equals(later.type())) {
                return QuestConditions.done(player, later, record);
            }
        }

        return false;
    }

    private static void remind(ServerPlayerEntity player, QuestStep current) {
        if (current == null) {
            return;
        }

        MutableText text = Text.literal("[퀘스트] 지금 할 일: ").formatted(Formatting.GOLD)
            .append(Text.literal(current.name() + " - " + current.description()).formatted(Formatting.YELLOW));

        if ("rival".equals(current.type())) {
            text.append(Text.literal(" [배틀하기]").formatted(Formatting.RED, Formatting.UNDERLINE)
                .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/quest rival"))));
        } else {
            text.append(Text.literal(" [퀘스트 보기]").formatted(Formatting.AQUA, Formatting.UNDERLINE)
                .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/quest"))));
        }

        player.sendMessage(text);
    }

    // rival

    static int rival(ServerPlayerEntity player) {
        QuestStep current = current(player);

        if (current == null || !"rival".equals(current.type())) {
            player.sendMessage(Text.literal("[퀘스트] 지금은 라이벌이 기다리고 있지 않습니다.").formatted(Formatting.GRAY));
            return 0;
        }

        if (Rival.inBattle(player)) {
            return 0;
        }

        String problem = Rival.start(player, (int) current.target(), config.rivalName);

        if (problem != null) {
            player.sendMessage(Text.literal("[퀘스트] " + problem).formatted(Formatting.RED));
            return 0;
        }

        return 1;
    }

    static void rivalResult(MinecraftServer server, UUID uuid, int number, boolean won) {
        QuestData.Record record = QuestData.create(uuid);
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);

        if (won) {
            record.rivalWins = Math.max(record.rivalWins, number);
            QuestData.save();
        }

        if (player != null) {
            player.sendMessage(Text.literal(config.rivalName + ": " + (won ? "졌다... 다음엔 절대 안 질 거야!" : "하하, 아직 멀었네! 언제든 다시 덤벼!"))
                .formatted(Formatting.AQUA));

            if (won) {
                check(player);
            } else {
                player.sendMessage(Text.literal("[퀘스트] 포켓몬을 회복한 뒤 /quest 에서 다시 도전하세요.").formatted(Formatting.GRAY));
            }
        }
    }

    // rewards

    private static Optional<ItemStack> parseItem(String entry) {
        String[] parts = entry.trim().split("\\s+");
        Identifier id = Identifier.tryParse(parts[0]);
        Optional<Item> item = id != null ? Registries.ITEM.getOrEmpty(id) : Optional.empty();

        if (item.isEmpty()) {
            LOGGER.warn("Unknown reward item '{}', skipped", entry);
            return Optional.empty();
        }

        return Optional.of(new ItemStack(item.get(), parts.length > 1 ? Integer.parseInt(parts[1]) : 1));
    }

    private static void reward(ServerPlayerEntity player, QuestStep step) {
        for (String entry : step.items()) {
            parseItem(entry).ifPresent(stack -> {
                if (!player.getInventory().insertStack(stack)) {
                    player.dropItem(stack, false); // inventory full: at the player's feet
                }
            });
        }

        if (step.money() > 0) {
            server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(),
                "cobbledollars give " + player.getGameProfile().getName() + " " + step.money());
        }

        LOGGER.info("{} finished quest step {}", player.getGameProfile().getName(), step.id());
    }

    static List<Text> rewardLines(QuestStep step) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal("보상").formatted(Formatting.GOLD));

        if (step.money() > 0) {
            lines.add(Text.literal(" " + String.format("%,d", step.money()) + " CobbleDollars").formatted(Formatting.GREEN));
        }

        for (String entry : step.items()) {
            parseItem(entry).ifPresent(stack ->
                lines.add(Text.literal(" ").append(stack.getName()).append(" x" + stack.getCount()).formatted(Formatting.WHITE)));
        }

        return lines;
    }

    private static Config loadConfig() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("poke-quest.json");

        try {
            if (Files.notExists(file)) {
                try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    GSON.toJson(new Config(), writer);
                }
            }

            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Config read = GSON.fromJson(reader, Config.class);
                read.steps = read.steps != null ? read.steps : List.of();
                read.rivalName = read.rivalName != null ? read.rivalName : "라이벌";
                read.steps.forEach(s -> s.items().forEach(PokeQuest::parseItem)); // logs unknown items once
                LOGGER.info("Loaded {} quest steps", read.steps.size());
                return read;
            }
        } catch (Exception e) {
            LOGGER.error("Could not read {}, using the defaults", file, e);
            return new Config();
        }
    }
}
