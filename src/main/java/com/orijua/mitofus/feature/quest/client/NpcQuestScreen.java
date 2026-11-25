package com.orijua.mitofus.feature.quest.client;

import com.orijua.mitofus.core.client.MitofusButton;
import com.orijua.mitofus.feature.vrhelmet.ResidencePayload;
import com.orijua.mitofus.core.client.MitofusCard;
import com.orijua.mitofus.core.client.MitofusStyledScreen;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.npc.NpcTradePayload;
import com.orijua.mitofus.feature.quest.NpcQuestPayload;
import com.orijua.mitofus.feature.quest.PlayerQuests;
import com.orijua.mitofus.feature.quest.QuestDeadlines;
import com.orijua.mitofus.feature.quest.QuestDefinition;
import com.orijua.mitofus.feature.quest.QuestService;
import com.orijua.mitofus.feature.quest.goal.DeliverGoal;
import com.orijua.mitofus.feature.rumor.Rumors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * An NPC's quest window (right-click on Smith or the village chief): the quests it gives that the player can accept or
 * has active, each with Accept, Deliver or Claim, and under them today's rumor (see {@link Rumors}). A merchant also
 * gets a Trade button. Quests with a giver can only be
 * dealt with here; the server checks it again (QuestService.atGiver).
 */
public final class NpcQuestScreen extends Screen implements MitofusStyledScreen {
    private static final int WIDTH = 270;
    private static final int PADDING = 10;
    private static final int ROW = 30;
    /** Under the title block: a small label, the title and a gap (see {@link UiStyle#titleBlock}). */
    private static final int HEADER_HEIGHT = UiStyle.KICKER_HEIGHT + UiStyle.TITLE_HEIGHT + 2;
    private static final int MAX_ROWS = 5;
    private static final int BUTTON_WIDTH = 64;
    private static final int MAX_RUMOR_LINES = 4;

    private record Row(Definitions.Entry<QuestDefinition> quest, Button button, NpcQuestPayload.Action action) {
    }

    private final Entity npc;
    private final boolean merchant;
    /** The village chief also offers to make the player a resident of his village. */
    private final boolean resident;
    private final List<Row> rows = new ArrayList<>();
    private List<FormattedCharSequence> rumor = List.of();
    private PlayerQuests shownState;
    private int left;
    private int top;
    private int panelHeight;

    private NpcQuestScreen(Entity npc, boolean merchant, boolean resident) {
        super(npc.getDisplayName());
        this.npc = npc;
        this.merchant = merchant;
        this.resident = resident;
    }

    /** Client only: called from the NPC's interaction on the client side. */
    public static void open(Entity npc, boolean merchant) {
        Minecraft.getInstance().setScreen(new NpcQuestScreen(npc, merchant, false));
    }

    /** Client only: the village chief's window, with the button to become a resident. */
    public static void openChief(Entity npc) {
        Minecraft.getInstance().setScreen(new NpcQuestScreen(npc, false, true));
    }

    private Player player() {
        return minecraft.player;
    }

    private int listTop() {
        return top + PADDING + HEADER_HEIGHT + 4;
    }

    /** Today's rumor sits under the quests: a heading and its lines, or nothing when the NPC has none left. */
    private int rumorHeight() {
        return rumor.isEmpty() ? 0 : 4 + UiStyle.LINE + 2 + rumor.size() * 10;
    }

    private int cardHeight() {
        return ROW - 2;
    }

    private int textX() {
        return left + PADDING + MitofusCard.STRIPE + 4;
    }

    /** Room for a quest's text: from after the stripe to before the button. */
    private int textWidth() {
        return WIDTH - 2 * PADDING - MitofusCard.STRIPE - 4 - BUTTON_WIDTH - 4 - 4;
    }

    @Override
    protected void init() {
        rows.clear();
        shownState = QuestService.get(player());
        List<Definitions.Entry<QuestDefinition>> quests = Definitions.all(player().level(), ModDatapackRegistries.QUEST).stream()
                .filter(entry -> entry.value().isGivenBy(npc.getType()))
                .filter(entry -> shownState.isActive(entry.id()) || QuestService.canAccept(player(), entry.id(), entry.value()))
                .limit(MAX_ROWS)
                .toList();
        rumor = Rumors.today(player(), npc.getType())
                .map(entry -> font.split(entry.value().text().copy().withStyle(ChatFormatting.ITALIC), WIDTH - 2 * PADDING))
                .map(lines -> lines.subList(0, Math.min(lines.size(), MAX_RUMOR_LINES)))
                .orElse(List.of());
        panelHeight = PADDING + HEADER_HEIGHT + 4 + Math.max(1, quests.size()) * ROW + rumorHeight() + (merchant || resident ? 24 : 0) + PADDING;
        left = (width - WIDTH) / 2;
        top = (height - panelHeight) / 2;
        for (int i = 0; i < quests.size(); i++) {
            Definitions.Entry<QuestDefinition> entry = quests.get(i);
            NpcQuestPayload.Action action = actionFor(entry);
            Button button = Button.builder(label(action), b -> send(entry, action))
                    .bounds(left + WIDTH - PADDING - 4 - BUTTON_WIDTH, listTop() + i * ROW + (cardHeight() - 16) / 2, BUTTON_WIDTH, 16)
                    .build(MitofusButton::new);
            button.active = enabled(entry, action);
            addRenderableWidget(button);
            rows.add(new Row(entry, button, action));
        }
        if (resident) {
            addRenderableWidget(Button.builder(Component.translatable("screen.mitofus.npc_residence"),
                            b -> PacketDistributor.sendToServer(new ResidencePayload(npc.getId())))
                    .bounds(left + PADDING, top + panelHeight - PADDING - 18, (WIDTH - 2 * PADDING - 4) / 2, 18)
                    .build(MitofusButton::new));
            addRenderableWidget(Button.builder(Component.translatable("screen.mitofus.branch_change"),
                            b -> Minecraft.getInstance().setScreen(new com.orijua.mitofus.feature.progression.client.BranchChangeScreen(this, npc.getId())))
                    .bounds(left + PADDING + (WIDTH - 2 * PADDING - 4) / 2 + 4, top + panelHeight - PADDING - 18, (WIDTH - 2 * PADDING - 4) / 2, 18)
                    .build(MitofusButton::new));
        }
        if (merchant) {
            addRenderableWidget(Button.builder(Component.translatable("screen.mitofus.npc_trade"),
                            b -> PacketDistributor.sendToServer(new NpcTradePayload(npc.getId())))
                    .bounds(left + (WIDTH - 90) / 2, top + panelHeight - PADDING - 18, 90, 18)
                    .build(MitofusButton::new));
        }
    }

    /** Accept a new quest; for an active one, Deliver while items are still missing, otherwise Claim. */
    private NpcQuestPayload.Action actionFor(Definitions.Entry<QuestDefinition> entry) {
        if (!shownState.isActive(entry.id())) {
            return NpcQuestPayload.Action.ACCEPT;
        }
        boolean delivering = entry.value().goal() instanceof DeliverGoal;
        return delivering && !isDone(entry) ? NpcQuestPayload.Action.DELIVER : NpcQuestPayload.Action.CLAIM;
    }

    private boolean enabled(Definitions.Entry<QuestDefinition> entry, NpcQuestPayload.Action action) {
        return switch (action) {
            case ACCEPT -> true;
            case DELIVER -> entry.value().goal() instanceof DeliverGoal goal && carries(goal);
            case CLAIM -> isDone(entry);
        };
    }

    private boolean carries(DeliverGoal goal) {
        Inventory inventory = player().getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (goal.accepts(inventory.getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private boolean isDone(Definitions.Entry<QuestDefinition> entry) {
        Integer stored = shownState.active().get(entry.id());
        return stored != null && QuestService.progress(player(), entry.value(), stored) >= entry.value().goal().target();
    }

    private static Component label(NpcQuestPayload.Action action) {
        return Component.translatable(switch (action) {
            case ACCEPT -> "screen.mitofus.quest_accept";
            case DELIVER -> "screen.mitofus.quest_deliver";
            case CLAIM -> "screen.mitofus.quest_claim";
        });
    }

    private void send(Definitions.Entry<QuestDefinition> entry, NpcQuestPayload.Action action) {
        PacketDistributor.sendToServer(new NpcQuestPayload(npc.getId(), entry.id(), action));
    }

    /** Closes when the NPC is gone or left behind; rebuilds when quests, progress or the inventory change the buttons. */
    @Override
    public void tick() {
        if (!npc.isAlive() || npc.distanceToSqr(player()) > QuestService.GIVER_REACH * QuestService.GIVER_REACH) {
            onClose();
            return;
        }
        if (!QuestService.get(player()).equals(shownState)
                || rows.stream().anyMatch(row -> row.button().active != enabled(row.quest(), row.action()))) {
            rebuildWidgets();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        UiStyle.panel(graphics, left, top, WIDTH, panelHeight);
        UiStyle.titleBlock(graphics, font, Component.translatable("screen.mitofus.npc_quests.kicker"), title, null, UiStyle.MUTED,
                left + WIDTH / 2, top + PADDING);
        // The cards go behind the buttons and the text, which are drawn later.
        for (int i = 0; i < rows.size(); i++) {
            Definitions.Entry<QuestDefinition> entry = rows.get(i).quest();
            int y = listTop() + i * ROW;
            Integer stored = shownState.active().get(entry.id());
            int accent = QuestUi.accent(stored != null, stored != null && isDone(entry));
            MitofusCard.State state = MitofusCard.state(overCard(mouseX, mouseY, y), false, false);
            MitofusCard.draw(graphics, left + PADDING, y, WIDTH - 2 * PADDING, cardHeight(), accent, state);
        }
    }

    private boolean overCard(int mouseX, int mouseY, int y) {
        return mouseX >= left + PADDING && mouseX < left + WIDTH - PADDING && mouseY >= y && mouseY < y + cardHeight();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderRumor(graphics);
        if (rows.isEmpty()) {
            UiStyle.text(graphics, font, Component.translatable("screen.mitofus.npc_no_quests"), left + PADDING, listTop() + 4,
                    UiStyle.MUTED);
            return;
        }
        int textWidth = textWidth();
        for (int i = 0; i < rows.size(); i++) {
            Definitions.Entry<QuestDefinition> entry = rows.get(i).quest();
            QuestDefinition quest = entry.value();
            int y = listTop() + i * ROW;
            UiStyle.text(graphics, font, quest.name(), textX(), y + 3, UiStyle.TEXT);
            Integer stored = shownState.active().get(entry.id());
            Component detail = quest.goal().describe();
            int color = UiStyle.MUTED;
            if (stored != null) {
                int progress = QuestService.progress(player(), quest, stored);
                detail = detail.copy().append("  " + progress + "/" + quest.goal().target());
                OptionalLong deadline = shownState.deadline(entry.id());
                if (deadline.isPresent()) {
                    detail = detail.copy().append("  ⌛").append(QuestDeadlines.timeLeft(deadline.getAsLong(), QuestDeadlines.now(player())));
                }
                color = progress >= quest.goal().target() ? UiStyle.GOOD : UiStyle.MUTED;
                int target = Math.max(1, quest.goal().target());
                UiStyle.bar(graphics, textX(), y + 24, textWidth, 1, progress / (float) target, QuestUi.barColor(progress >= quest.goal().target()));
            }
            graphics.drawString(font, UiStyle.ellipsize(font, detail, textWidth), textX(), y + 14, color);
            if (mouseX >= textX() && mouseX < textX() + textWidth && mouseY >= y && mouseY < y + cardHeight()) {
                graphics.renderTooltip(font, tooltip(quest), mouseX, mouseY);
            }
        }
    }

    private void renderRumor(GuiGraphics graphics) {
        if (rumor.isEmpty()) {
            return;
        }
        int y = listTop() + Math.max(1, rows.size()) * ROW + 4;
        y = UiStyle.sectionHeading(graphics, font, Component.translatable("screen.mitofus.npc_rumor"), left + PADDING, y,
                WIDTH - 2 * PADDING, UiStyle.GOLD);
        for (FormattedCharSequence line : rumor) {
            graphics.drawString(font, line, left + PADDING, y, UiStyle.TEXT);
            y += 10;
        }
    }

    private List<FormattedCharSequence> tooltip(QuestDefinition quest) {
        List<FormattedCharSequence> lines = new ArrayList<>(font.split(quest.description(), 200));
        lines.add(quest.goal().describe().getVisualOrderText());
        lines.add(Component.translatable("screen.mitofus.quest_reward", quest.xpReward(), quest.itemRewards().size()).getVisualOrderText());
        return lines;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
