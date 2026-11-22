package com.orijua.mitofus.feature.fatigue.client;

import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.registry.ModAttachments;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;

/** The safety protocol: a big countdown in the middle-top of the screen while the grace period before the forced disconnection runs. */
public final class FatigueGraceLayer implements LayeredDraw.Layer {
    private static final int WIDTH = 240;
    private static final int HEIGHT = 70;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        int seconds = minecraft.player.getData(ModAttachments.FATIGUE_GRACE);
        if (seconds < 0) {
            return;
        }
        Font font = minecraft.font;
        int x = (graphics.guiWidth() - WIDTH) / 2;
        int y = graphics.guiHeight() / 5;
        UiStyle.panel(graphics, x, y, WIDTH, HEIGHT);

        Component title = Component.translatable("hud.mitofus.fatigue_grace.title");
        graphics.drawString(font, title, x + (WIDTH - font.width(title)) / 2, y + 6, UiStyle.GOLD);

        Component number = Component.literal(String.valueOf(seconds));
        graphics.pose().pushPose();
        graphics.pose().translate(x + WIDTH / 2.0f, y + 18, 0);
        graphics.pose().scale(3.0f, 3.0f, 1.0f);
        graphics.drawString(font, number, -font.width(number) / 2, 0, UiStyle.TEXT);
        graphics.pose().popPose();

        Component frozen = Component.translatable("hud.mitofus.fatigue_grace.frozen");
        graphics.drawString(font, frozen, x + (WIDTH - font.width(frozen)) / 2, y + HEIGHT - 24, UiStyle.BAD);

        Component note = Component.translatable("hud.mitofus.fatigue_grace.safe");
        graphics.drawString(font, note, x + (WIDTH - font.width(note)) / 2, y + HEIGHT - 12, UiStyle.GOOD);
    }
}
