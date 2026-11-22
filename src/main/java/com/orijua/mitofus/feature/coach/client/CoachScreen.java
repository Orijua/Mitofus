package com.orijua.mitofus.feature.coach.client;

import com.orijua.mitofus.core.client.MitofusButton;
import com.orijua.mitofus.core.client.MitofusStyledScreen;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.config.CoachBalance;
import com.orijua.mitofus.feature.coach.CoachRules;
import com.orijua.mitofus.feature.coach.CoachTripPayload;
import com.orijua.mitofus.feature.economy.MoneyText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** The coachman's window: one "Travel to ..." button for each village, and the fare that starts from. */
public final class CoachScreen extends Screen implements MitofusStyledScreen {
    private static final int WIDTH = 230;
    private static final int PADDING = 10;
    private static final int BUTTON_HEIGHT = 18;

    private final Entity npc;
    private List<FormattedCharSequence> lines = List.of();
    private int left;
    private int top;
    private int height;

    private CoachScreen(Entity npc) {
        super(Component.translatable("screen.mitofus.coach_title"));
        this.npc = npc;
    }

    /** Client only. */
    public static void open(Entity npc) {
        Minecraft.getInstance().setScreen(new CoachScreen(npc));
    }

    @Override
    protected void init() {
        Component info = Component.translatable("screen.mitofus.coach_info", MoneyText.iron(CoachBalance.current().baseIron()));
        lines = font.split(info, WIDTH - 2 * PADDING);
        int destinations = CoachRules.DESTINATIONS.size();
        height = PADDING + UiStyle.LINE + 4 + lines.size() * UiStyle.LINE + 8
                + destinations * (BUTTON_HEIGHT + 4) + PADDING;
        left = (width - WIDTH) / 2;
        top = (super.height - height) / 2;
        int y = top + height - PADDING - destinations * (BUTTON_HEIGHT + 4) + 4;
        for (String destination : CoachRules.DESTINATIONS) {
            addRenderableWidget(Button.builder(Component.translatable("screen.mitofus.coach_travel",
                            Component.translatable("site.mitofus." + destination)), b -> {
                        PacketDistributor.sendToServer(new CoachTripPayload(npc.getId(), destination));
                        onClose();
                    })
                    .bounds(left + PADDING, y, WIDTH - 2 * PADDING, BUTTON_HEIGHT).build(MitofusButton::new));
            y += BUTTON_HEIGHT + 4;
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
