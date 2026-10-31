package kr.pokequest;

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
import java.util.Optional;

/**
 * /quest as a chest screen (vanilla screen, clients need no mod): what to do now with its rewards, the rival button
 * when the rival is waiting, and the steps of one region (tabs at the bottom). Nothing can be taken out.
 */
public final class QuestMenu extends GenericContainerScreenHandler {
    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final int SLOT_CURRENT = 4;
    private static final int SLOT_RIVAL = 6;
    private static final int SLOT_GYM = 2;
    private static final int SLOT_SERIES = 0;
    private static final int FIRST_STEP = 18;
    private static final int[] TAB_SLOTS = {46, 48, 50, 52};
    private static final String[] TAB_ICONS = {"cobbleversebadges:kanto_badge_box", "cobbleversebadges:johto_badge_box",
        "cobbleversebadges:hoenn_badge_box", "cobbleversebadges:sinnoh_badge_box"};

    private final ServerPlayerEntity player;
    private final SimpleInventory inventory;
    private String region;

    private QuestMenu(int syncId, PlayerInventory playerInventory, SimpleInventory inventory, ServerPlayerEntity player) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, ROWS);
        this.player = player;
        this.inventory = inventory;
        QuestStep current = PokeQuest.current(player);
        List<String> regions = PokeQuest.regions();
        this.region = current != null ? current.region() : regions.get(regions.size() - 1);
        fill();
    }

    public static int open(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInventory, p) -> new QuestMenu(syncId, playerInventory, new SimpleInventory(SIZE), player),
            Text.literal("가이드 퀘스트")));
        return 1;
    }

    private void fill() {
        inventory.clear();
        ItemStack filler = named(new ItemStack(Items.GRAY_STAINED_GLASS_PANE), Text.literal(" "), List.of());

        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setStack(slot, filler.copy());
        }

        QuestData.Record record = QuestData.create(player.getUuid());
        QuestStep current = PokeQuest.current(player);
        List<QuestStep> steps = PokeQuest.steps();
        long done = steps.stream().filter(s -> record.done.contains(s.id())).count();

        if (current == null) {
            inventory.setStack(SLOT_CURRENT, named(new ItemStack(Items.NETHER_STAR), plain("모든 퀘스트 완료!", Formatting.GOLD),
                List.of(plain("관동부터 신오까지 모든 여행을 마쳤습니다.", Formatting.GRAY))));
        } else {
            List<Text> lore = new ArrayList<>();
            lore.add(plain("[" + current.region() + "] " + current.description(), Formatting.GRAY));
            lore.add(plain(progressText(current, record), Formatting.DARK_AQUA));
            lore.add(Text.empty());
            lore.addAll(PokeQuest.rewardLines(current));
            lore.add(Text.empty());
            lore.add(plain("전체 진행: " + done + " / " + steps.size(), Formatting.YELLOW));
            inventory.setStack(SLOT_CURRENT, named(icon(current), plain("지금 할 일: " + current.name(), Formatting.GOLD), lore));

            if ("rival".equals(current.type())) {
                inventory.setStack(SLOT_RIVAL, named(new ItemStack(Items.TARGET), plain("라이벌과 배틀하기", Formatting.RED), List.of(
                    plain(PokeQuest.rivalName() + "이(가) 기다리고 있습니다.", Formatting.GRAY),
                    plain("라이벌과 내 포켓몬 모두 지금 레벨 제한(Lv." + Math.max(5, Math.min(100, QuestConditions.levelCap(player))) + ")으로 맞춰 싸웁니다.", Formatting.GRAY),
                    plain("클릭하면 바로 배틀이 시작됩니다.", Formatting.YELLOW))));
            }
        }

        fillGym();
        inventory.setStack(SLOT_SERIES, named(new ItemStack(Items.NAME_TAG), plain("시리즈(지방) 바꾸기", Formatting.AQUA), List.of(
            plain("지금 시리즈: " + GymSummon.seriesName(player), Formatting.GRAY),
            plain("트레이너 협회 NPC 없이 여기서 다음 지방으로 바꿀 수 있습니다. (/시리즈)", Formatting.GRAY),
            plain("클릭: 열기", Formatting.YELLOW))));
        List<QuestStep> shown = steps.stream().filter(s -> s.region().equals(region)).toList();

        for (int i = 0; i < shown.size() && FIRST_STEP + i < 45; i++) {
            QuestStep step = shown.get(i);
            boolean finished = record.done.contains(step.id());
            boolean now = current != null && current.id().equals(step.id());
            List<Text> lore = new ArrayList<>();
            lore.add(plain(step.description(), Formatting.GRAY));

            if (now) {
                lore.add(plain(progressText(step, record), Formatting.DARK_AQUA));
            }

            lore.add(Text.empty());
            lore.addAll(PokeQuest.rewardLines(step));
            ItemStack stack = finished || now ? icon(step) : new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
            Text name = plain((finished ? "✔ " : now ? "▶ " : "") + step.name(),
                finished ? Formatting.GREEN : now ? Formatting.YELLOW : Formatting.DARK_GRAY);
            ItemStack named = named(stack, name, lore);
            named.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, now);
            inventory.setStack(FIRST_STEP + i, named);
        }

        List<String> regions = PokeQuest.regions();

        for (int i = 0; i < regions.size() && i < TAB_SLOTS.length; i++) {
            String tab = regions.get(i);
            long regionDone = steps.stream().filter(s -> s.region().equals(tab) && record.done.contains(s.id())).count();
            long regionAll = steps.stream().filter(s -> s.region().equals(tab)).count();
            ItemStack stack = named(item(TAB_ICONS[i]), plain(tab + " (" + regionDone + "/" + regionAll + ")",
                tab.equals(region) ? Formatting.GOLD : Formatting.WHITE), List.of(plain("클릭해서 이 지방의 퀘스트 보기", Formatting.GRAY)));
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, tab.equals(region));
            inventory.setStack(TAB_SLOTS[i], stack);
        }

        sendContentUpdates();
    }

    /** The next gym leader / elite four / champion of the player's RCT series, summoned with a click. */
    private void fillGym() {
        java.util.Map.Entry<String, com.gitlab.srcmc.rctmod.api.data.pack.TrainerMobData> next;

        try {
            next = GymSummon.next(player);
        } catch (RuntimeException e) {
            PokeQuest.LOGGER.warn("could not find the next RCT trainer of {}", player.getGameProfile().getName(), e);
            return;
        }

        if (next == null) {
            inventory.setStack(SLOT_GYM, named(new ItemStack(Items.IRON_SWORD), plain("다음 관장 도전", Formatting.GRAY), List.of(
                plain("지금 시리즈(" + GymSummon.seriesName(player) + ")에 남은 상대가 없습니다.", Formatting.GRAY),
                plain("다음 시리즈는 트레이너 협회에서 고를 수 있습니다.", Formatting.GRAY))));
            return;
        }

        inventory.setStack(SLOT_GYM, named(new ItemStack(Items.DIAMOND_SWORD), plain("다음 상대에게 도전", Formatting.RED), List.of(
            plain(GymSummon.seriesName(player) + ": ", Formatting.GRAY).append(GymSummon.name(next.getValue()).copy().formatted(Formatting.YELLOW)),
            plain("에이스 Lv." + GymSummon.aceLevel(next.getValue()) + " (레벨 보정 적용)", Formatting.GRAY),
            plain("클릭하면 눈앞에 나타나 바로 배틀합니다. 지면 언제든 다시 부를 수 있습니다.", Formatting.YELLOW))));
    }

    private String progressText(QuestStep step, QuestData.Record record) {
        long progress = Math.min(QuestConditions.progress(player, step, record), step.target());
        return switch (step.type()) {
            case "defeat", "series" -> progress >= step.target() ? "달성" : "아직 이기지 못함";
            case "rival" -> "라이벌을 이기면 완료";
            default -> "진행도: " + progress + " / " + step.target();
        };
    }

    private static ItemStack icon(QuestStep step) {
        return item(step.icon() == null ? "minecraft:book" : step.icon());
    }

    private static ItemStack item(String id) {
        Identifier identifier = Identifier.tryParse(id);
        Optional<Item> item = identifier != null ? Registries.ITEM.getOrEmpty(identifier) : Optional.empty();
        return new ItemStack(item.orElse(Items.BOOK));
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity clicker) {
        // never move items: only react to plain clicks on the menu's own slots
        if (actionType == SlotActionType.PICKUP && slotIndex >= 0 && slotIndex < SIZE) {
            if (slotIndex == SLOT_SERIES) {
                SeriesMenu.open(player);
                syncState();
                return;
            }

            if (slotIndex == SLOT_GYM && inventory.getStack(SLOT_GYM).isOf(Items.DIAMOND_SWORD)) {
                player.closeHandledScreen();
                String problem = GymSummon.challenge(player);

                if (problem != null) {
                    player.sendMessage(Text.literal(problem).formatted(Formatting.RED));
                }
            }

            if (slotIndex == SLOT_RIVAL && inventory.getStack(SLOT_RIVAL).isOf(Items.TARGET)) {
                player.closeHandledScreen();
                PokeQuest.rival(player);
            }

            List<String> regions = PokeQuest.regions();

            for (int i = 0; i < TAB_SLOTS.length && i < regions.size(); i++) {
                if (slotIndex == TAB_SLOTS[i]) {
                    region = regions.get(i);
                    fill();
                }
            }
        }

        syncState(); // undo whatever the client already showed moving
    }

    @Override
    public ItemStack quickMove(PlayerEntity clicker, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity clicker) {
        return true;
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
