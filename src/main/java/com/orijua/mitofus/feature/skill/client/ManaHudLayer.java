package com.orijua.mitofus.feature.skill.client;

import com.orijua.mitofus.core.client.HudVisibility;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.progression.ProgressionRules;
import com.orijua.mitofus.feature.progression.ProgressionService;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;

/**
 * Mana bar above the food bar. Registered right above vanilla's food layer, so it takes the next
 * right-side row ({@link Gui#rightHeight}) and the air bubbles move up above it.
 */
public final class ManaHudLayer implements LayeredDraw.Layer {
    private static final int WIDTH = 81;
    private static final int BAR_HEIGHT = 5;
    private static final int TOP_OFFSET = 0;
    // 2 px more air with the food bar; the row grows 2 so it does not overlap the next one.
    private static final int ROW_HEIGHT = 12;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!HudVisibility.showSurvivalBars(minecraft)) {
            return;
        }
        float maxMana = ProgressionService.maxMana(player);
        float mana = player.getData(ModAttachments.MANA);

        Gui gui = minecraft.gui;
        int left = graphics.guiWidth() / 2 + 91 - WIDTH;
        int top = graphics.guiHeight() - gui.rightHeight + TOP_OFFSET;
        gui.rightHeight += ROW_HEIGHT;

        UiStyle.bar(graphics, left, top, WIDTH, BAR_HEIGHT, maxMana <= 0 ? 0 : mana / maxMana, UiStyle.MANA);
    }
}
