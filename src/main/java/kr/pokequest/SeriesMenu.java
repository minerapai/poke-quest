package kr.pokequest;

import com.gitlab.srcmc.rctmod.api.RCTMod;
import com.gitlab.srcmc.rctmod.api.data.pack.SeriesMetaData;
import com.gitlab.srcmc.rctmod.api.data.save.TrainerPlayerData;
import com.gitlab.srcmc.rctmod.api.service.SeriesManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Picks the RCT series (region) to play, what the trainer association NPC offers, as a chest screen anywhere
 * (/series, /시리즈, or from the quest screen). A series is open when its required series are completed. Leaving an
 * unfinished series drops its progress (RCT keeps one progress at a time), so that needs a shift click.
 */
public final class SeriesMenu extends GenericContainerScreenHandler {
    private static final int ROWS = 3;
    private static final int SIZE = ROWS * 9;
    private static final int FIRST = 9;
    /** The regions in their order: series id, Korean name, icon. */
    private static final String[][] SERIES = {
        {"kanto", "관동", "cobbleversebadges:kanto_badge_box"}, {"johto", "성도", "cobbleversebadges:johto_badge_box"},
        {"hoenn", "호연", "cobbleversebadges:hoenn_badge_box"}, {"sinnoh", "신오", "cobbleversebadges:sinnoh_badge_box"},
        {"unova", "하나", "minecraft:gold_block"}, {"kalos", "칼로스", "minecraft:lapis_block"},
        {"alola", "알로라", "minecraft:orange_glazed_terracotta"}, {"galar", "가라르", "minecraft:purple_glazed_terracotta"},
        {"paldea", "팔데아", "minecraft:redstone_block"}};

    private final ServerPlayerEntity player;
    private final SimpleInventory inventory;

    private SeriesMenu(int syncId, PlayerInventory playerInventory, SimpleInventory inventory, ServerPlayerEntity player) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inventory, ROWS);
        this.player = player;
        this.inventory = inventory;
        fill();
    }

    public static int open(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInventory, p) -> new SeriesMenu(syncId, playerInventory, new SimpleInventory(SIZE), player),
            Text.literal("트레이너 시리즈 (지방) 고르기")));
        return 1;
    }

    private static TrainerPlayerData data(ServerPlayerEntity player) {
        return RCTMod.getInstance().getTrainerManager().getData(player);
    }

    /** The series exists (its data pack is loaded) and every group of its required series has one completed. */
    private static boolean open(String series, Map<String, Integer> completed) {
        SeriesManager manager = RCTMod.getInstance().getSeriesManager();

        if (!manager.getSeriesIds().contains(series)) {
            return false;
        }

        SeriesMetaData meta = manager.getGraph(series).getMetaData();
        return meta.requiredSeries() == null || meta.requiredSeries().stream()
            .allMatch(group -> group.isEmpty() || group.stream().anyMatch(s -> completed.getOrDefault(s, 0) > 0));
    }

    private void fill() {
        inventory.clear();
        ItemStack filler = named(new ItemStack(Items.GRAY_STAINED_GLASS_PANE), Text.literal(" "), List.of());

        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setStack(slot, filler.copy());
        }

        TrainerPlayerData data = data(player);
        String current = data.getCurrentSeries();
        Map<String, Integer> completed = data.getCompletedSeries();
        inventory.setStack(4, named(new ItemStack(Items.PAPER), plain("지금 시리즈: " + korean(current), Formatting.GOLD), List.of(
            plain("관장, 사천왕, 챔피언은 지금 시리즈의 상대만 도전할 수 있습니다.", Formatting.GRAY),
            plain("트레이너 협회 NPC 를 찾아가지 않아도 여기서 바꿀 수 있습니다.", Formatting.GRAY))));

        for (int i = 0; i < SERIES.length; i++) {
            String id = SERIES[i][0];
            boolean now = id.equals(current);
            boolean done = completed.getOrDefault(id, 0) > 0;
            boolean open = open(id, completed);
            List<Text> lore = new ArrayList<>();
            lore.add(plain(done ? "클리어 " + completed.get(id) + "회" : "아직 클리어하지 않음", done ? Formatting.GREEN : Formatting.GRAY));

            if (now) {
                lore.add(plain("지금 이 시리즈를 진행 중입니다", Formatting.YELLOW));
            } else if (!open) {
                lore.add(plain("이전 지방을 먼저 클리어해야 합니다", Formatting.RED));
            } else if (!data.isSeriesCompleted() && !data.getDefeatedTrainerIds().isEmpty()) {
                lore.add(plain("지금 시리즈의 진행이 사라집니다! (클리어 기록은 남음)", Formatting.RED));
                lore.add(plain("쉬프트 클릭: 바꾸기", Formatting.YELLOW));
            } else {
                lore.add(plain("클릭: 이 시리즈로 바꾸기", Formatting.YELLOW));
            }

            ItemStack stack = named(now || open ? item(SERIES[i][2]) : new ItemStack(Items.BARRIER),
                plain(SERIES[i][1], now ? Formatting.GOLD : open ? Formatting.WHITE : Formatting.DARK_GRAY), lore);
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, now);
            inventory.setStack(FIRST + i, stack);
        }

        inventory.setStack(22, named(new ItemStack(Items.ARROW), plain("퀘스트로 돌아가기", Formatting.AQUA), List.of()));
        sendContentUpdates();
    }

    private static String korean(String series) {
        for (String[] s : SERIES) {
            if (s[0].equals(series)) {
                return s[1];
            }
        }

        return series == null || series.isEmpty() ? "없음" : series;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity clicker) {
        boolean shift = actionType == SlotActionType.QUICK_MOVE;

        if ((actionType == SlotActionType.PICKUP || shift) && slotIndex >= 0 && slotIndex < SIZE) {
            if (slotIndex == 22) {
                QuestMenu.open(player);
            } else if (slotIndex >= FIRST && slotIndex < FIRST + SERIES.length) {
                choose(SERIES[slotIndex - FIRST][0], shift);
            }
        }

        syncState(); // undo whatever the client already showed moving
    }

    private void choose(String series, boolean shift) {
        TrainerPlayerData data = data(player);

        if (series.equals(data.getCurrentSeries()) || !open(series, data.getCompletedSeries())) {
            return;
        }

        if (!data.isSeriesCompleted() && !data.getDefeatedTrainerIds().isEmpty() && !shift) {
            player.sendMessage(Text.literal("지금 시리즈의 진행이 사라집니다. 정말 바꾸려면 쉬프트 클릭하세요.").formatted(Formatting.RED));
            return;
        }

        // as the trainer association does: the progress carries over only when going back to the previous series
        data.setCurrentSeries(series, series.equals(data.getPreviousSeries()));
        data.sync();
        player.sendMessage(Text.literal("[시리즈] " + korean(series) + " 시리즈를 시작합니다!").formatted(Formatting.GOLD));
        PokeQuest.LOGGER.info("{} switched to the {} series", player.getGameProfile().getName(), series);
        fill();
    }

    /** OP: puts a player in any series, required series or not (progress of the current one is dropped). */
    static int force(net.minecraft.server.command.ServerCommandSource source, ServerPlayerEntity player, String series) {
        if (!RCTMod.getInstance().getSeriesManager().getSeriesIds().contains(series)) {
            source.sendError(Text.literal("없는 시리즈입니다: " + series + " (kanto johto hoenn sinnoh unova kalos alola galar paldea)"));
            return 0;
        }

        TrainerPlayerData data = data(player);
        data.setCurrentSeries(series, false);
        data.sync();
        source.sendFeedback(() -> Text.literal(player.getGameProfile().getName() + " 님을 " + korean(series) + " 시리즈로 바꿨습니다."), true);
        return 1;
    }

    @Override
    public ItemStack quickMove(PlayerEntity clicker, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity clicker) {
        return true;
    }

    private static ItemStack item(String id) {
        Identifier identifier = Identifier.tryParse(id);
        java.util.Optional<Item> item = identifier != null ? Registries.ITEM.getOrEmpty(identifier) : java.util.Optional.empty();
        return new ItemStack(item.orElse(Items.BOOK));
    }

    private static MutableText plain(String text, Formatting color) {
        return Text.literal(text).setStyle(Style.EMPTY.withItalic(false).withColor(color));
    }

    private static ItemStack named(ItemStack stack, Text name, List<Text> lore) {
        stack.set(DataComponentTypes.CUSTOM_NAME, name.copy().setStyle(name.getStyle().withItalic(false)));
        List<Text> lines = new ArrayList<>();

        for (Text line : lore) {
            lines.add(line.copy().setStyle(line.getStyle().withItalic(false)));
        }

        stack.set(DataComponentTypes.LORE, new LoreComponent(lines));
        stack.set(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE);
        return stack;
    }
}
