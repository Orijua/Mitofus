package com.orijua.mitofus.feature.storage.client;

import com.orijua.mitofus.core.client.MitofusButton;
import com.orijua.mitofus.core.client.MitofusStyledScreen;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.feature.economy.MoneyText;
import com.orijua.mitofus.feature.economy.Wallet;
import com.orijua.mitofus.feature.storage.StorageInfoPayload;
import com.orijua.mitofus.feature.storage.StoragePayload;
import com.orijua.mitofus.feature.storage.StorageRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The storekeeper's window. It shows what the player has (rows and free slots) and only offers what can be done: buying the
 * storage while there is none, opening it once there is one, and one more row (with its real price) until the most. Client only.
 */
public final class StorageScreen extends Screen implements MitofusStyledScreen {
    private static final int WIDTH = 230;
    private static final int PADDING = 10;
    private static final int BUTTON_HEIGHT = 18;

    private StorageInfoPayload info;
    private List<FormattedCharSequence> lines = List.of();
    private int left;
    private int top;
    private int height;

    private StorageScreen(StorageInfoPayload info) {
        super(Component.translatable("screen.mitofus.storage_title"));
        this.info = info;
    }

    /** Opens the window, or refreshes it if it is already open (after buying a row). */
    public static void show(StorageInfoPayload info) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof StorageScreen open && open.info.npc() == info.npc()) {
            open.info = info;
            open.rebuildWidgets();
        } else {
            minecraft.setScreen(new StorageScreen(info));
        }
    }

    private boolean opened() {
        return info.rows() > 0;
    }

    private boolean canExpand() {
        return opened() && info.nextRowCost() >= 0L;
    }

    /** What the window says, by state: no storage yet, with rows to buy, or at the most. */
    private Component text() {
        if (!opened()) {
            return Component.translatable("screen.mitofus.storage_new", MoneyText.iron(info.openCost()),
                    StorageRules.FIRST_ROWS * StorageRules.SLOTS_PER_ROW);
        }
        Component state = Component.translatable("screen.mitofus.storage_state", info.rows(), info.slots(), info.freeSlots());
        Component next = canExpand()
                ? Component.translatable("screen.mitofus.storage_next", StorageRules.SLOTS_PER_ROW, MoneyText.iron(info.nextRowCost()))
                : Component.translatable("screen.mitofus.storage_max", StorageRules.MAX_ROWS);
        return Component.empty().append(state).append(Component.literal(" ")).append(next);
    }

    @Override
    protected void init() {
        lines = font.split(text(), WIDTH - 2 * PADDING);
        int buttons = canExpand() ? 2 : 1;
        height = PADDING + UiStyle.LINE + 4 + lines.size() * UiStyle.LINE + 8 + buttons * (BUTTON_HEIGHT + 4) + PADDING;
        left = (width - WIDTH) / 2;
        top = (super.height - height) / 2;
        int y = top + height - PADDING - buttons * (BUTTON_HEIGHT + 4) + 4;
        Component openLabel = opened() ? Component.translatable("screen.mitofus.storage_open")
                : Component.translatable("screen.mitofus.storage_buy", MoneyText.iron(info.openCost()));
        Button open = addRenderableWidget(Button.builder(openLabel, b -> {
            PacketDistributor.sendToServer(new StoragePayload(info.npc(), false));
            onClose();
        }).bounds(left + PADDING, y, WIDTH - 2 * PADDING, BUTTON_HEIGHT).build(MitofusButton::new));
        if (!opened()) {
            open.active = Wallet.canAfford(Minecraft.getInstance().player, info.openCost());
            if (!open.active) {
                open.setTooltip(Tooltip.create(Component.translatable("screen.mitofus.storage_no_coins")));
            }
        }
        if (canExpand()) {
            y += BUTTON_HEIGHT + 4;
            // The window stays open after buying, so the next price and the free slots update in front of the player.
            Button expand = addRenderableWidget(Button.builder(Component.translatable("screen.mitofus.storage_expand", MoneyText.iron(info.nextRowCost())),
                            b -> PacketDistributor.sendToServer(new StoragePayload(info.npc(), true)))
                    .bounds(left + PADDING, y, WIDTH - 2 * PADDING, BUTTON_HEIGHT).build(MitofusButton::new));
            expand.active = Wallet.canAfford(Minecraft.getInstance().player, info.nextRowCost());
            if (!expand.active) {
                expand.setTooltip(Tooltip.create(Component.translatable("screen.mitofus.storage_no_coins")));
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        UiStyle.panel(graphics, left, top, WIDTH, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = left + WIDTH / 2;
        int y = top + PADDING;
        graphics.drawCenteredString(font, title, centerX, y, UiStyle.HEADING);
        y += UiStyle.LINE;
        graphics.fill(left + PADDING, y, left + WIDTH - PADDING, y + 1, UiStyle.LINE_SOFT);
        y += 4;
        for (FormattedCharSequence line : lines) {
            graphics.drawCenteredString(font, line, centerX, y, UiStyle.TEXT);
            y += UiStyle.LINE;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
