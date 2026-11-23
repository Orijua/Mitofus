package com.orijua.mitofus.feature.vrhelmet.client;

import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.feature.vrhelmet.ClientVrHelmet;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;

/**
 * The "Connecting…" screen while the player lies in the VR helmet: the view fades to dark and a Mitofus panel shows a
 * progress bar. It covers the whole screen, above every other layer, until the server moves the player.
 */
public final class VrHelmetConnectingLayer implements LayeredDraw.Layer {
    private static final int PANEL_WIDTH = 190;
    private static final int PANEL_HEIGHT = 46;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        float fraction = ClientVrHelmet.fraction(Util.getMillis());
        if (fraction < 0.0f) {
            // The screen has ended (the player arrived or it was cancelled): the dark fades out rather than vanishing in one frame.
            float fading = ClientVrHelmet.fadeOutDark(Util.getMillis());
            if (fading > 0.0f) {
                graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (Math.round(fading * 255) << 24));
            }
            return;
        }
        if (minecraft.player == null) {
            return;
        }
        float dark = ClientVrHelmet.dark(fraction);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (Math.round(dark * 255) << 24));

        Font font = minecraft.font;
        int x = (graphics.guiWidth() - PANEL_WIDTH) / 2;
        int y = (graphics.guiHeight() - PANEL_HEIGHT) / 2;
        UiStyle.panel(graphics, x, y, PANEL_WIDTH, PANEL_HEIGHT);
        int dots = (int) (Util.getMillis() / 400 % 4);
        Component title = Component.translatable("screen.mitofus.vrhelmet.connecting", ".".repeat(dots));
        graphics.drawCenteredString(font, title, x + PANEL_WIDTH / 2, y + 11, UiStyle.HEADING);
        UiStyle.bar(graphics, x + 14, y + 29, PANEL_WIDTH - 28, 6, fraction, UiStyle.MANA);
    }
}
