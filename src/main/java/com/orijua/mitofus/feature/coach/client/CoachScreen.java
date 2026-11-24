package com.orijua.mitofus.feature.coach.client;

import com.orijua.mitofus.core.client.MitofusButton;
import com.orijua.mitofus.core.client.MitofusConfirmScreen;
import com.orijua.mitofus.core.client.MitofusStyledScreen;
import com.orijua.mitofus.core.client.PanelFit;
import com.orijua.mitofus.core.client.ScrollList;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.config.CoachBalance;
import com.orijua.mitofus.feature.coach.CoachInfoPayload;
import com.orijua.mitofus.feature.coach.CoachTripPayload;
import com.orijua.mitofus.feature.economy.MoneyText;
import com.orijua.mitofus.feature.economy.Wallet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** The coachman's window: one "Travel to ..." button for each village, and the fare that starts from. */
public final class CoachScreen extends Screen implements MitofusStyledScreen {
    private static final int WIDTH = 230;
    private static final int PADDING = 10;
    private static final int BUTTON_HEIGHT = 18;

    private final CoachInfoPayload info;
    private List<FormattedCharSequence> lines = List.of();
    private int left;
    private int top;
    private int height;
    private final ScrollList scroll = new ScrollList(1);

    private CoachScreen(CoachInfoPayload info) {
        super(Component.translatable("screen.mitofus.coach_title"));
        this.info = info;
    }

    /** Client only: opens the window with the fares the server worked out. */
    public static void show(CoachInfoPayload info) {
        Minecraft.getInstance().setScreen(new CoachScreen(info));
    }

    @Override
    protected void init() {
        Component text = info.destinations().isEmpty() ? Component.translatable("screen.mitofus.coach_none")
                : Component.translatable("screen.mitofus.coach_info", Component.translatable("screen.mitofus.coach_travel_time",
                CoachBalance.current().travelSeconds()));
        lines = font.split(text, WIDTH - 2 * PADDING);
        int total = info.destinations().size();
        int fixed = PADDING + UiStyle.LINE + 4 + lines.size() * UiStyle.LINE + 8 + PADDING;
        int destinations = Math.min(total, PanelFit.rows(super.height - 2 * PanelFit.MARGIN - fixed, BUTTON_HEIGHT + 4));
        scroll.setVisibleRows(destinations);
        scroll.clamp(total);
        height = fixed + destinations * (BUTTON_HEIGHT + 4);
        left = (width - WIDTH) / 2;
        top = (super.height - height) / 2;
        int y = top + height - PADDING - destinations * (BUTTON_HEIGHT + 4) + 4;
        for (int i = scroll.first(); i < scroll.end(total); i++) {
            String destination = info.destinations().get(i);
            long fare = info.fares().get(i);
            Component place = Component.translatable("site.mitofus." + destination);
            Button button = addRenderableWidget(Button.builder(Component.translatable("screen.mitofus.coach_travel", place, MoneyText.iron(fare)),
                            b -> confirm(destination, place, fare))
                    .bounds(left + PADDING, y, WIDTH - 2 * PADDING, BUTTON_HEIGHT).build(MitofusButton::new));
            button.active = Wallet.canAfford(Minecraft.getInstance().player, fare);
            if (!button.active) {
                button.setTooltip(Tooltip.create(Component.translatable("screen.mitofus.coach_no_coins", MoneyText.iron(fare))));
            }
            y += BUTTON_HEIGHT + 4;
        }
    }

    /** The total cost is shown before accepting, next to what the player has. */
    private void confirm(String destination, Component place, long fare) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new MitofusConfirmScreen(this, Component.translatable("screen.mitofus.coach_confirm_title", place),
                Component.translatable("screen.mitofus.coach_confirm", place, MoneyText.iron(fare), MoneyText.iron(Wallet.balance(minecraft.player))),
                () -> {
                    PacketDistributor.sendToServer(new CoachTripPayload(info.npc(), destination));
                    minecraft.tell(() -> minecraft.setScreen(null));
                }));
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scroll.scroll(scrollY, info.destinations().size())) {
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
