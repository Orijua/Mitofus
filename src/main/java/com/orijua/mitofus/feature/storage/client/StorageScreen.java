package com.orijua.mitofus.feature.storage.client;

import com.orijua.mitofus.core.client.MitofusButton;
import com.orijua.mitofus.core.client.MitofusStyledScreen;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.feature.economy.MoneyText;
import com.orijua.mitofus.feature.storage.StoragePayload;
import com.orijua.mitofus.feature.storage.StorageRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** The storekeeper's window: open the storage (buying it the first time) or buy one more row. */
public final class StorageScreen extends Screen implements MitofusStyledScreen {
    private static final int WIDTH = 230;
    private static final int PADDING = 10;
    private static final int BUTTON_HEIGHT = 18;

    private final Entity npc;
    private List<FormattedCharSequence> lines = List.of();
    private int left;
    private int top;
    private int height;

    private StorageScreen(Entity npc) {
        super(Component.translatable("screen.mitofus.storage_title"));
        this.npc = npc;
    }

    /** Client only. */
    public static void open(Entity npc) {
        Minecraft.getInstance().setScreen(new StorageScreen(npc));
    }

    @Override
    protected void init() {
        Component info = Component.translatable("screen.mitofus.storage_info", MoneyText.iron(StorageRules.openCost()),
                StorageRules.FIRST_ROWS * StorageRules.SLOTS_PER_ROW, MoneyText.iron(StorageRules.nextRowCost(StorageRules.FIRST_ROWS)));
        lines = font.split(info, WIDTH - 2 * PADDING);
        height = PADDING + UiStyle.LINE + 4 + lines.size() * UiStyle.LINE + 8 + 2 * (BUTTON_HEIGHT + 4) + PADDING;
        left = (width - WIDTH) / 2;
        top = (super.height - height) / 2;
        int y = top + height - PADDING - 2 * (BUTTON_HEIGHT + 4) + 4;
        addRenderableWidget(Button.builder(Component.translatable("screen.mitofus.storage_open"), b -> {
            PacketDistributor.sendToServer(new StoragePayload(npc.getId(), false));
            onClose();
        }).bounds(left + PADDING, y, WIDTH - 2 * PADDING, BUTTON_HEIGHT).build(MitofusButton::new));
        y += BUTTON_HEIGHT + 4;
        addRenderableWidget(Button.builder(Component.translatable("screen.mitofus.storage_expand"), b -> {
            PacketDistributor.sendToServer(new StoragePayload(npc.getId(), true));
            onClose();
        }).bounds(left + PADDING, y, WIDTH - 2 * PADDING, BUTTON_HEIGHT).build(MitofusButton::new));
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
