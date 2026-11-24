package com.orijua.mitofus.feature.npc.client;

import com.orijua.mitofus.core.config.ApprenticeBalance;
import com.orijua.mitofus.core.client.MitofusCard;
import com.orijua.mitofus.core.client.MitofusEmptyState;
import com.orijua.mitofus.core.client.ScrollList;
import com.orijua.mitofus.core.client.MitofusTab;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.view.Observation;
import com.orijua.mitofus.feature.npc.ApprenticeRules;
import com.orijua.mitofus.feature.npc.ApprenticeSmith;
import com.orijua.mitofus.feature.npc.Workshop;
import com.orijua.mitofus.feature.npc.WorkshopEntry;
import com.orijua.mitofus.feature.progression.ProgressionService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The workshop: every apprentice of the player as a card with his stage, what he is doing (or why he stopped), a bar
 * with the piece on the anvil, his order and his wage, as last reported by him. A tooltip gives what is cut short, and a
 * click opens his window when he is at hand. His level shows with enough View, as in his own window. Opened from the
 * Mitofus window or the workshop ledger.
 */
public class WorkshopTab extends MitofusTab {
    private static final int ROW = 34;
    private static final int CARD = 32;
    private static final int LIST_TOP = 28;
    /** Where the text of a card starts: after the 18 px item frame. */
    private static final int TEXT_OFFSET = 27;
    private static final int BAR_SPACE = 6;

    private final ScrollList scroll = new ScrollList(4);

    public WorkshopTab() {
        super(Component.translatable("tab.mitofus.workshop"));
    }

    private List<WorkshopEntry> entries() {
        return Workshop.entries(player()).stream()
                .filter(entry -> player().getData(ModAttachments.APPRENTICES).contains(entry.apprentice()))
                .toList();
    }

    private static long now() {
        return Minecraft.getInstance().level.getGameTime();
    }

    private int visibleRows() {
        return Math.max(1, (height - LIST_TOP) / ROW);
    }

    private int cardWidth() {
        return width - BAR_SPACE;
    }

    private int cardTop(int visibleIndex) {
        return top + LIST_TOP + visibleIndex * ROW;
    }

    @Override
    public boolean mouseScrolled(double wheel) {
        scroll.setVisibleRows(visibleRows());
        return scroll.scroll(wheel, entries().size());
    }

    /** A click on a card opens that apprentice's window if he is near; otherwise the system says he is far. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY) {
        List<WorkshopEntry> entries = entries();
        scroll.setVisibleRows(visibleRows());
        scroll.clamp(entries.size());
        for (int i = scroll.first(); i < scroll.end(entries.size()); i++) {
            if (hovered(mouseX, mouseY, cardTop(i - scroll.first()))) {
                open(entries.get(i));
                return false;
            }
        }
        return false;
    }

    private void open(WorkshopEntry entry) {
        Minecraft minecraft = Minecraft.getInstance();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof ApprenticeSmith apprentice && apprentice.getUUID().equals(entry.apprentice()) && apprentice.isAlive()
                    && apprentice.isOwnedBy(player())
                    && apprentice.distanceToSqr(player()) <= ApprenticeSmith.WINDOW_REACH * ApprenticeSmith.WINDOW_REACH) {
                ApprenticeScreen.open(apprentice);
                return;
            }
        }
        player().displayClientMessage(Component.translatable("message.mitofus.workshop_far", entry.name()), false);
    }

    private boolean hovered(double mouseX, double mouseY, int y) {
        return mouseX >= left && mouseX < left + cardWidth() && mouseY >= y && mouseY < y + CARD;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        List<WorkshopEntry> entries = entries();
        int smithing = ProgressionService.effectiveSmithingLevel(player());
        int allowed = ApprenticeRules.allowedApprentices(smithing);
        int hired = player().getData(ModAttachments.APPRENTICES).size();
        int next = ApprenticeRules.levelForApprentice(hired);
        Component header = next < 0 || hired < allowed
                ? Component.translatable("screen.mitofus.workshop.count", hired, allowed)
                : Component.translatable("screen.mitofus.workshop.count_next", hired, allowed, next);
        UiStyle.sectionHeading(graphics, font(), header, left, top, cardWidth(), UiStyle.HEADING);
        Component smithingLevel = Component.translatable("screen.mitofus.smithing", smithing);
        UiStyle.text(graphics, font(), smithingLevel, left + cardWidth() - font().width(smithingLevel), top, UiStyle.MUTED);

        if (entries.isEmpty()) {
            MitofusEmptyState.draw(graphics, font(), left, top + LIST_TOP, width, height - LIST_TOP,
                    Component.translatable("screen.mitofus.workshop.empty", ApprenticeBalance.current().firstLevel()), MitofusEmptyState.Kind.EMPTY);
            return;
        }
        int stopped = Workshop.stoppedCount(entries, now());
        graphics.drawString(font(), Component.translatable("screen.mitofus.workshop.totals", Workshop.working(entries, now()), stopped),
                left, top + UiStyle.LINE + 2, stopped > 0 ? UiStyle.BAD : UiStyle.MUTED);

        scroll.setVisibleRows(visibleRows());
        scroll.clamp(entries.size());
        int view = Observation.effectiveView(player());
        WorkshopEntry tooltipEntry = null;
        for (int i = scroll.first(); i < scroll.end(entries.size()); i++) {
            int y = cardTop(i - scroll.first());
            boolean hover = hovered(mouseX, mouseY, y);
            renderCard(graphics, entries.get(i), y, hover, view);
            if (hover) {
                tooltipEntry = entries.get(i);
            }
        }
        scroll.renderBar(graphics, left + width - 3, top + LIST_TOP, visibleRows() * ROW - 2, entries.size());
        if (tooltipEntry != null) {
            graphics.renderComponentTooltip(font(), tooltip(tooltipEntry), mouseX, mouseY);
        }
    }

    private void renderCard(GuiGraphics graphics, WorkshopEntry entry, int y, boolean hover, int view) {
        int cardWidth = cardWidth();
        // Out of reach, his last report may be old: the card dims and shows a line of colour instead of news.
        boolean stale = entry.isStale(now());
        ApprenticeSmith.Status status = stale ? ApprenticeSmith.Status.NO_ORDER : entry.statusValue();
        int statusColor = ApprenticeUi.statusColor(status);
        MitofusCard.draw(graphics, left, y, cardWidth, CARD, statusColor, MitofusCard.state(hover, stale, false));

        Optional<Item> order = Workshop.orderItem(entry);
        MitofusCard.icon(graphics, order.map(ItemStack::new).orElse(ItemStack.EMPTY), left + 5, y + 7);

        int textX = left + TEXT_OFFSET;
        Component statusText = ApprenticeUi.statusText(Workshop.displayStatus(entry, now()), status.stopped());
        int statusWidth = font().width(statusText);
        graphics.drawString(font(), statusText, left + cardWidth - statusWidth - 3, y + 3, statusColor);

        MutableComponent title = ApprenticeUi.nameText(entry.name(), entry.named()).copy()
                .append(Component.literal(" · ").withColor(UiStyle.MUTED))
                .append(Component.translatable("npc.mitofus.stage." + entry.stage().key()).withColor(UiStyle.MUTED));
        if (entry.craftsman()) {
            title.append(Component.translatable("entity.mitofus.apprentice_smith.craftsman_suffix").withColor(UiStyle.GOLD));
        }
        title.append(Component.translatable("screen.mitofus.workshop.level", ApprenticeUi.levelText(view, entry.level()))
                .withColor(UiStyle.MUTED));
        graphics.drawString(font(), UiStyle.ellipsize(font(), title, cardWidth - TEXT_OFFSET - statusWidth - 10), textX, y + 3, UiStyle.TEXT);

        Component detail = Component.translatable("screen.mitofus.workshop.detail",
                order.map(Item::getDescription).orElse(Component.translatable("message.mitofus.apprentice_no_order_name")), entry.daysPaid());
        graphics.drawString(font(), UiStyle.ellipsize(font(), detail, cardWidth - TEXT_OFFSET - 4), textX, y + 14,
                stale ? UiStyle.MUTED : ApprenticeUi.wageColor(entry.daysPaid(), UiStyle.MUTED));

        if (Workshop.showsProgress(status, entry.progress())) {
            // The piece on the anvil, filling up along the bottom of the card.
            UiStyle.bar(graphics, textX, y + 25, cardWidth - TEXT_OFFSET - 6, 3, entry.progress() / 100f, UiStyle.GOOD);
        }
    }

    /** What the cards cut short: the full order, the wage and what he lacks. */
    private List<Component> tooltip(WorkshopEntry entry) {
        List<Component> lines = new ArrayList<>();
        lines.add(ApprenticeUi.nameText(entry.name(), entry.named()));
        boolean stale = entry.isStale(now());
        lines.add(Workshop.displayStatus(entry, now()));
        Component order = Workshop.orderItem(entry).map(Item::getDescription)
                .orElse(Component.translatable("message.mitofus.apprentice_no_order_name"));
        lines.add(Component.translatable("screen.mitofus.workshop.tip.order", order).withColor(UiStyle.MUTED));
        lines.add(Component.translatable("screen.mitofus.workshop.tip.wage", entry.daysPaid())
                .withColor(stale ? UiStyle.MUTED : ApprenticeUi.wageColor(entry.daysPaid(), UiStyle.MUTED)));
        if (stale) {
            lines.add(Component.translatable("screen.mitofus.workshop.tip.stale").withColor(UiStyle.MUTED));
        } else {
            Workshop.missingItem(entry).ifPresent(item -> lines.add(Component.translatable("screen.mitofus.workshop.tip.missing",
                    entry.missingCount(), item.getDescription()).withColor(UiStyle.BAD)));
        }
        lines.add(Component.translatable("screen.mitofus.workshop.tip.open").withColor(UiStyle.MUTED));
        return lines;
    }
}
