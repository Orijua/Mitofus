package com.orijua.mitofus.feature.npc.client;

import com.orijua.mitofus.core.config.ApprenticeBalance;
import com.orijua.mitofus.core.client.MitofusButton;
import com.orijua.mitofus.core.client.MitofusStyledScreen;
import com.orijua.mitofus.core.client.PanelFit;
import com.orijua.mitofus.core.client.MitofusCard;
import com.orijua.mitofus.core.client.MitofusConfirmScreen;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.feature.economy.CarriedItems;
import com.orijua.mitofus.feature.economy.Money;
import com.orijua.mitofus.feature.economy.MoneyText;
import com.orijua.mitofus.feature.economy.Wallet;
import com.orijua.mitofus.feature.economy.client.CoinBalanceView;
import com.orijua.mitofus.feature.view.ViewRules;
import com.orijua.mitofus.feature.view.Observation;
import com.orijua.mitofus.feature.npc.ApprenticeActionPayload;
import com.orijua.mitofus.feature.npc.Workshop;
import com.orijua.mitofus.feature.npc.ApprenticeRules;
import com.orijua.mitofus.feature.npc.ApprenticeSmith;
import com.orijua.mitofus.feature.npc.NpcGrade;
import com.orijua.mitofus.feature.smithing.SmithingHammers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

/**
 * An apprentice's window (his master right-clicks him empty-handed): what he is doing and why not, his order, his wage
 * and his containers, with a button for each thing that can be done from here. Orders and stacks of gold still go
 * by right-clicking him with the item, as the hint at the bottom says. Everything shown is synced entity data, so the
 * window follows him live.
 */
public final class ApprenticeScreen extends Screen implements MitofusStyledScreen {
    private static final int WIDTH = 300;
    private static final int PADDING = 10;
    /** The line with the coins the player has, under the buttons. */
    private static final int COIN_LINE = 20;
    /** Kept tight: with the head and the three sections the window must still fit at GUI scale 3 on 1080p. */
    private static final int ROW = 20;
    private static final int CARD_HEIGHT = ROW - 2;
    private static final int LABEL_WIDTH = 70;
    private static final int BUTTON_WIDTH = 70;
    private static final int BUTTON_HEIGHT = 16;
    /** The window head: a small label, the title and the subtitle (see {@link UiStyle#titleBlock}) and a gap. */
    private static final int HEADER_HEIGHT = UiStyle.KICKER_HEIGHT + 2 * UiStyle.TITLE_HEIGHT + 2;
    /** Height of a section heading with its line and the gap under it. */
    private static final int SECTION_HEIGHT = UiStyle.LINE + 2;
    /** Margin inside a card: after the stripe, and before the right edge. */
    private static final int INNER = MitofusCard.STRIPE + 4;
    private static final int ICON_SPACE = 20;

    private enum Section {
        WORK("work"), CONTRACT("contract"), OBSERVATION("observation");

        final String key;

        Section(String key) {
            this.key = key;
        }
    }

    /** The rows of the window, top to bottom, each under its section. */
    private enum Row {
        STATUS(Section.WORK, "status"),
        ORDER(Section.WORK, "order"),
        HAMMER(Section.WORK, "hammer"),
        WAGE(Section.CONTRACT, "wage"),
        MATERIALS(Section.CONTRACT, "materials"),
        OUTPUT(Section.CONTRACT, "output"),
        WORK_INFO(Section.OBSERVATION, "work"),
        GRADE(Section.OBSERVATION, "grade");

        final Section section;
        final String key;

        Row(Section section, String key) {
            this.section = section;
            this.key = key;
        }
    }

    private final ApprenticeSmith apprentice;
    private List<FormattedCharSequence> hint = List.of();
    private Button cancel;
    private Button pay;
    private Button clear;
    private Button hammer;
    private int left;
    private int top;
    /** Top of the visible window, and of the content (which is higher by {@code scrollY} when scrolled). */
    private int panelTop;
    private int contentHeight;
    private int scrollY;
    private int panelHeight;

    private ApprenticeScreen(ApprenticeSmith apprentice) {
        super(apprentice.getDisplayName());
        this.apprentice = apprentice;
    }

    /** Client only: called from the apprentice's interaction on the client side. */
    public static void open(ApprenticeSmith apprentice) {
        Minecraft.getInstance().setScreen(new ApprenticeScreen(apprentice));
    }

    /** The top of a row's card: under the head, counting the section headings above it (its own included). */
    private int rowY(Row row) {
        return top + PADDING + HEADER_HEIGHT + 2 + (row.section.ordinal() + 1) * SECTION_HEIGHT + row.ordinal() * ROW;
    }

    private int sectionY(Section section) {
        for (Row row : Row.values()) {
            if (row.section == section) {
                return rowY(row) - SECTION_HEIGHT;
            }
        }
        return top;
    }

    private int buttonsY() {
        return rowY(Row.GRADE) + ROW + 2;
    }

    private int cardX() {
        return left + PADDING;
    }

    private int cardWidth() {
        return WIDTH - 2 * PADDING;
    }

    /** Where the value of a row starts, after its label. */
    private int valueX() {
        return cardX() + INNER + LABEL_WIDTH;
    }

    private int buttonX() {
        return cardX() + cardWidth() - 4 - BUTTON_WIDTH;
    }

    /** The daily wage in iron, the unit of the balance. */
    private static long wageIron() {
        return (long) ApprenticeBalance.current().dailyWage() * Money.IRON_PER_GOLD;
    }

    private int buttonY(Row row) {
        return rowY(row) + (CARD_HEIGHT - BUTTON_HEIGHT) / 2;
    }

    /** Text centered vertically in a card. */
    private int textY(Row row) {
        return rowY(row) + (CARD_HEIGHT - font.lineHeight) / 2 + 1;
    }

    @Override
    protected void init() {
        hint = font.split(Component.translatable("screen.mitofus.apprentice.hint", Money.format(ApprenticeBalance.current().dailyWage())), WIDTH - 2 * PADDING);
        top = 0;
        contentHeight = buttonsY() + 18 + 8 + COIN_LINE + hint.size() * 10 + PADDING;
        // On a small screen the window is as tall as the screen allows and its content scrolls.
        panelHeight = PanelFit.fit(WIDTH, contentHeight, width, height).height();
        panelHeight = Math.min(panelHeight, contentHeight);
        scrollY = Math.clamp(scrollY, 0, contentHeight - panelHeight);
        left = (width - WIDTH) / 2;
        panelTop = (height - panelHeight) / 2;
        top = panelTop - scrollY;
        cancel = rowButton(Row.ORDER, "screen.mitofus.apprentice.cancel", ApprenticeActionPayload.Action.CANCEL_ORDER);
        pay = addRenderableWidget(Button.builder(Component.translatable("screen.mitofus.apprentice.pay"), b -> send(
                        hasControlDown() ? ApprenticeActionPayload.Action.PAY_10_DAYS
                                : hasShiftDown() ? ApprenticeActionPayload.Action.PAY_5_DAYS : ApprenticeActionPayload.Action.PAY_DAY))
                .bounds(buttonX(), buttonY(Row.WAGE), BUTTON_WIDTH, BUTTON_HEIGHT).build(MitofusButton::new));
        pay.setTooltip(Tooltip.create(Component.translatable("screen.mitofus.apprentice.pay_tooltip", MoneyText.iron(wageIron()),
                ApprenticeBalance.current().maxPaidDays())));
        // Assigning closes the window so the next right-click can pick the container.
        addRenderableWidget(Button.builder(Component.translatable("screen.mitofus.apprentice.assign"), b -> assign(ApprenticeActionPayload.Action.ASSIGN_MATERIALS))
                .bounds(buttonX(), buttonY(Row.MATERIALS), BUTTON_WIDTH, BUTTON_HEIGHT).build(MitofusButton::new));
        addRenderableWidget(Button.builder(Component.translatable("screen.mitofus.apprentice.assign"), b -> assign(ApprenticeActionPayload.Action.ASSIGN_OUTPUT))
                .bounds(buttonX(), buttonY(Row.OUTPUT), BUTTON_WIDTH, BUTTON_HEIGHT).build(MitofusButton::new));
        // One button: give him your best hammer, or take his back.
        hammer = addRenderableWidget(Button.builder(Component.empty(), b -> send(apprentice.getMainHandItem().isEmpty()
                        ? ApprenticeActionPayload.Action.GIVE_HAMMER : ApprenticeActionPayload.Action.TAKE_HAMMER))
                .bounds(buttonX(), buttonY(Row.HAMMER), BUTTON_WIDTH, BUTTON_HEIGHT).build(MitofusButton::new));
        hammer.setTooltip(Tooltip.create(Component.translatable("screen.mitofus.apprentice.hammer_tooltip")));
        int bottomWidth = 120;
        clear = addRenderableWidget(Button.builder(Component.translatable("screen.mitofus.apprentice.clear"),
                        b -> send(ApprenticeActionPayload.Action.CLEAR_CONTAINERS))
                .bounds(left + WIDTH / 2 - bottomWidth - 4, buttonsY(), bottomWidth, 18).build(MitofusButton::new));
        addRenderableWidget(Button.builder(Component.translatable("screen.mitofus.apprentice.dismiss"), b -> confirmDismiss())
                .bounds(left + WIDTH / 2 + 4, buttonsY(), bottomWidth, 18).build(MitofusButton::new));
        updateButtons();
    }

    private Button rowButton(Row row, String key, ApprenticeActionPayload.Action action) {
        return addRenderableWidget(Button.builder(Component.translatable(key), b -> send(action))
                .bounds(buttonX(), buttonY(row), BUTTON_WIDTH, BUTTON_HEIGHT).build(MitofusButton::new));
    }

    private void send(ApprenticeActionPayload.Action action) {
        PacketDistributor.sendToServer(new ApprenticeActionPayload(apprentice.getId(), action));
    }

    private void assign(ApprenticeActionPayload.Action action) {
        send(action);
        onClose();
    }

    private void confirmDismiss() {
        minecraft.setScreen(new MitofusConfirmScreen(this, Component.translatable("screen.mitofus.apprentice.dismiss_title", apprentice.getDisplayName()),
                Component.translatable("screen.mitofus.apprentice.dismiss_message"), () -> send(ApprenticeActionPayload.Action.DISMISS)));
    }

    private void updateButtons() {
        cancel.active = !apprentice.shownOrder().isEmpty();
        pay.active = apprentice.shownDaysPaid() < ApprenticeBalance.current().maxPaidDays()
                && Wallet.canAfford(minecraft.player, wageIron());
        clear.active = apprentice.assigned(ApprenticeSmith.Slot.MATERIALS).isPresent() || apprentice.assigned(ApprenticeSmith.Slot.OUTPUT).isPresent();
        boolean hasHammer = !apprentice.getMainHandItem().isEmpty();
        hammer.setMessage(Component.translatable(hasHammer ? "screen.mitofus.apprentice.hammer_take" : "screen.mitofus.apprentice.hammer_give"));
        hammer.active = hasHammer || SmithingHammers.bestSlot(minecraft.level.registryAccess(), minecraft.player.getInventory()) >= 0;
    }

    /** Closes when he is gone or left behind; the buttons follow his synced state. */
    @Override
    public void tick() {
        if (!apprentice.isAlive() || apprentice.distanceToSqr(minecraft.player) > ApprenticeSmith.WINDOW_REACH * ApprenticeSmith.WINDOW_REACH) {
            onClose();
            return;
        }
        updateButtons();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        UiStyle.panel(graphics, left, panelTop, WIDTH, panelHeight);
        // Everything after the panel is clipped to it; render() ends the clip once the widgets and the text are drawn.
        graphics.enableScissor(left, panelTop, left + WIDTH, panelTop + panelHeight);
        int centerX = left + WIDTH / 2;
        // "Craftsman" goes once, in gold, as the small label; the title is his plain name (his display name carries the
        // Craftsman suffix too).
        Component role = apprentice.isCraftsman()
                ? Component.translatable("screen.mitofus.apprentice.craftsman").withColor(UiStyle.GOLD)
                : Component.translatable("entity.mitofus.apprentice_smith");
        Component name = ApprenticeUi.nameText(apprentice.baseName(), apprentice.grade() == NpcGrade.NAMED);
        int view = Observation.effectiveView(minecraft.player);
        UiStyle.titleBlock(graphics, font, role, name, ApprenticeUi.stageLine(apprentice, view), UiStyle.MUTED, centerX, top + PADDING);
        for (Section section : Section.values()) {
            UiStyle.sectionHeading(graphics, font, Component.translatable("screen.mitofus.apprentice.section." + section.key),
                    cardX(), sectionY(section), cardWidth(), UiStyle.HEADING);
        }
        // The cards go behind the buttons and the text, which are drawn later.
        ViewRules.Detail detail = ViewRules.detail(view);
        int soft = UiStyle.LINE_SOFT & 0xFFFFFF;
        for (Row row : Row.values()) {
            boolean hiddenRow = row == Row.WORK_INFO && detail == ViewRules.Detail.LOW || row == Row.GRADE && detail != ViewRules.Detail.HIGH;
            int accent = switch (row) {
                case STATUS -> ApprenticeUi.statusColor(apprentice.status());
                case WAGE -> ApprenticeUi.wageColor(apprentice.shownDaysPaid(), soft);
                default -> hiddenRow ? UiStyle.MUTED : soft;
            };
            MitofusCard.draw(graphics, cardX(), rowY(row), cardWidth(), CARD_HEIGHT, accent, MitofusCard.state(false, hiddenRow, false));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int view = Observation.effectiveView(minecraft.player);
        ViewRules.Detail detail = ViewRules.detail(view);

        ApprenticeSmith.Status status = apprentice.status();
        int statusColor = ApprenticeUi.statusColor(status);
        boolean progress = Workshop.showsProgress(status, apprentice.shownProgress());
        // With the bar under it, the status text moves up so both fit in the card.
        row(graphics, Row.STATUS, ApprenticeUi.statusText(apprentice.statusText(), status.stopped()), statusColor, progress ? rowY(Row.STATUS) + 3 : textY(Row.STATUS));
        if (progress) {
            // The piece on the anvil, filling up under the status.
            UiStyle.bar(graphics, valueX(), rowY(Row.STATUS) + 3 + font.lineHeight + 3, valueWidth(), 1, apprentice.shownProgress() / 100f, UiStyle.GOOD);
        }

        ItemStack order = apprentice.shownOrder();
        itemRow(graphics, Row.ORDER, order, order.isEmpty() ? Component.translatable("message.mitofus.apprentice_no_order_name")
                : order.getItem().getDescription(), order.isEmpty() ? UiStyle.MUTED : UiStyle.TEXT);

        ItemStack held = apprentice.getMainHandItem();
        itemRow(graphics, Row.HAMMER, held, held.isEmpty() ? Component.translatable("screen.mitofus.apprentice.no_hammer")
                        : held.getHoverName().copy().append(" " + SmithingHammers.format(SmithingHammers.skillBonus(minecraft.level.registryAccess(), held))),
                held.isEmpty() ? UiStyle.MUTED : UiStyle.TEXT);

        row(graphics, Row.WAGE, Component.translatable("screen.mitofus.apprentice.wage_days", apprentice.shownDaysPaid()),
                ApprenticeUi.wageColor(apprentice.shownDaysPaid(), UiStyle.TEXT), textY(Row.WAGE));
        row(graphics, Row.MATERIALS, container(ApprenticeSmith.Slot.MATERIALS, "screen.mitofus.apprentice.materials_default"), UiStyle.TEXT, textY(Row.MATERIALS));
        row(graphics, Row.OUTPUT, container(ApprenticeSmith.Slot.OUTPUT, "screen.mitofus.apprentice.output_default"), UiStyle.TEXT, textY(Row.OUTPUT));

        // Character Observation: how much more is revealed depends on the player's View.
        if (detail == ViewRules.Detail.LOW) {
            wideRow(graphics, Row.WORK_INFO, hidden(view, ViewRules.MEDIUM), UiStyle.MUTED);
        } else {
            wideRow(graphics, Row.WORK_INFO, Component.translatable("screen.mitofus.apprentice.work_value",
                    ApprenticeRules.failPercent(apprentice.shownLevel()), ApprenticeRules.secondsPerPiece(apprentice.shownLevel())), UiStyle.TEXT);
        }
        if (detail == ViewRules.Detail.HIGH) {
            NpcGrade grade = apprentice.grade();
            Component cap = grade.levelCap() == Integer.MAX_VALUE ? Component.translatable("screen.mitofus.apprentice.no_cap")
                    : Component.literal(String.valueOf(grade.levelCap()));
            wideRow(graphics, Row.GRADE, Component.translatable("screen.mitofus.apprentice.grade_value",
                    Component.translatable("npc.mitofus.grade." + grade.name().toLowerCase()), cap), grade == NpcGrade.NAMED ? UiStyle.GOLD : UiStyle.TEXT);
        } else {
            wideRow(graphics, Row.GRADE, hidden(view, ViewRules.HIGH), UiStyle.MUTED);
        }

        int coinsY = buttonsY() + 18 + 10;
        Component purseLabel = Component.translatable("screen.mitofus.apprentice.purse");
        UiStyle.text(graphics, font, purseLabel, left + PADDING, coinsY, UiStyle.MUTED);
        int coinsX = left + PADDING + font.width(purseLabel) + 6;
        int coinsWidth = CoinBalanceView.render(graphics, font, Wallet.totalCopper(minecraft.player), coinsX, coinsY, UiStyle.GOLD);
        boolean coinsTooltip = mouseX >= coinsX && mouseX < coinsX + coinsWidth && mouseY >= coinsY - 5 && mouseY < coinsY + 12
                && mouseY >= panelTop && mouseY < panelTop + panelHeight;
        int hintY = buttonsY() + 18 + 8 + COIN_LINE;
        for (FormattedCharSequence line : hint) {
            graphics.drawString(font, line, left + PADDING, hintY, UiStyle.MUTED);
            hintY += 10;
        }
        graphics.disableScissor();
        if (contentHeight > panelHeight) {
            int trackTop = panelTop + 4;
            int trackHeight = panelHeight - 8;
            int thumb = Math.max(8, trackHeight * panelHeight / contentHeight);
            int thumbY = trackTop + (trackHeight - thumb) * scrollY / (contentHeight - panelHeight);
            graphics.fill(left + WIDTH - 5, trackTop, left + WIDTH - 2, trackTop + trackHeight, 0x60FFFFFF);
            graphics.fill(left + WIDTH - 5, thumbY, left + WIDTH - 2, thumbY + thumb, UiStyle.LINE_SOFT);
        }
        if (coinsTooltip) {
            graphics.renderComponentTooltip(font, CoinBalanceView.tooltip(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (contentHeight > panelHeight) {
            int before = this.scrollY;
            this.scrollY = Math.clamp(before - (int) Math.signum(scrollY) * 12, 0, contentHeight - panelHeight);
            if (this.scrollY != before) {
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Buttons scrolled out of the window are still there, so clicks outside the window are ignored. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseY < panelTop || mouseY >= panelTop + panelHeight) {
            return false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Room for the value of a row that has a button. */
    private int valueWidth() {
        return buttonX() - 6 - valueX();
    }

    private void label(GuiGraphics graphics, Row row) {
        graphics.drawString(font, Component.translatable("screen.mitofus.apprentice." + row.key), cardX() + INNER, textY(row), UiStyle.MUTED);
    }

    private void row(GuiGraphics graphics, Row row, Component value, int color, int y) {
        label(graphics, row);
        graphics.drawString(font, clip(value, valueWidth()), valueX(), y, color);
    }

    /** A row whose value is an item (framed) followed by its name. */
    private void itemRow(GuiGraphics graphics, Row row, ItemStack stack, Component name, int color) {
        label(graphics, row);
        MitofusCard.icon(graphics, stack, valueX(), rowY(row) + (CARD_HEIGHT - 18) / 2);
        graphics.drawString(font, clip(name, valueWidth() - ICON_SPACE), valueX() + ICON_SPACE, textY(row), color);
    }

    /** A row with no button: its value may use the whole card. */
    private void wideRow(GuiGraphics graphics, Row row, Component value, int color) {
        label(graphics, row);
        graphics.drawString(font, clip(value, cardX() + cardWidth() - 4 - valueX()), valueX(), textY(row), color);
    }

    /** What stays hidden until the player's View reaches {@code needed}. */
    private static Component hidden(int view, int needed) {
        return Component.translatable("screen.mitofus.apprentice.hidden", view, needed);
    }

    /** Cut with "…", as in the Workshop tab. */
    private Component clip(Component text, int maxWidth) {
        return UiStyle.ellipsize(font, text, maxWidth);
    }

    /** The assigned container's block and position, or where he looks without one. */
    private Component container(ApprenticeSmith.Slot slot, String defaultKey) {
        Optional<BlockPos> pos = apprentice.assigned(slot);
        return pos.<Component>map(at -> Component.translatable("screen.mitofus.apprentice.at",
                        apprentice.level().getBlockState(at).getBlock().getName(), at.getX(), at.getY(), at.getZ()))
                .orElse(Component.translatable(defaultKey));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
