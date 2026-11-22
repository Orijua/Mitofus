package com.orijua.mitofus.feature.fatigue.client;

import com.orijua.mitofus.core.client.HudVisibility;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.config.ClientConfig;
import com.orijua.mitofus.core.config.VrHelmetBalance;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetText;
import com.orijua.mitofus.core.config.FatigueBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.fatigue.ClientFatigue;
import com.orijua.mitofus.feature.fatigue.FatigueRules;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The energy bar: a thin lavender bar (like the XP one) on the right-hand column between the mana and XP bars.
 * Unlike the rest of the mod's interface it also shows in the real world, where the energy refills. By default it
 * only shows while the energy is not full; the fatigue key cycles AUTO, ALWAYS and HIDDEN. The percentage sits to
 * the right of the column: in AUTO only under a warning, in ALWAYS always.
 */
public final class FatigueHudLayer implements LayeredDraw.Layer {
    private static final int WIDTH = 81;
    private static final int BAR_HEIGHT = 2;
    /** Where the bar sits inside its row: top = guiHeight - rightHeight + TOP_OFFSET. */
    private static final int TOP_OFFSET = 6;
    private static final int ROW_HEIGHT = 6;
    /** Pixels between the column and the number, and the number's vertical offset from the bar's top. */
    private static final int NUMBER_GAP = 4;
    private static final int NUMBER_Y_OFFSET = -3;
    private static final int RESTED_ROW_HEIGHT = 10;

    /** The color of the bars for a level; the terminal uses it too, so both warn the same. */
    public static int colorFor(FatigueRules.Level level) {
        return FatigueHudRules.colorFor(level);
    }

    /** The color of the bars for an energy against the configured thresholds. */
    public static int colorFor(float energy, FatigueBalance balance) {
        return colorFor(FatigueRules.shownLevel(energy, balance.warnAt()));
    }

    /** The energy as the player reads it ("80 %"), shared by the HUD and the terminal. */
    public static Component energyText(float energy) {
        return Component.translatable("hud.mitofus.fatigue", FatigueRules.percent(energy));
    }

    /** The rested bonus as the player reads it ("Rested 09:59 (+10 % XP)"). */
    public static Component restedText(int seconds) {
        return Component.translatable("hud.mitofus.rested",
                VrHelmetText.clock(seconds), VrHelmetBalance.current().restedXpPercent());
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        FatigueBalance balance = FatigueBalance.current();
        if (!HudVisibility.showSurvivalBars(minecraft) || !balance.enabled()) {
            return;
        }
        ClientFatigue.Display mode = ClientConfig.fatigueDisplay();
        float energy = player.getData(ModAttachments.FATIGUE);
        if (!FatigueHudRules.showsBar(mode, energy)) {
            return;
        }
        FatigueRules.Level level = FatigueRules.shownLevel(energy, balance.warnAt());
        int color = FatigueHudRules.colorFor(level);
        float alpha = FatigueHudRules.alpha(level, energy, Util.getMillis());

        Gui gui = minecraft.gui;
        int left = graphics.guiWidth() / 2 + 91 - WIDTH;
        int top = graphics.guiHeight() - gui.rightHeight + TOP_OFFSET;
        gui.rightHeight += ROW_HEIGHT;

        UiStyle.bar(graphics, left, top, WIDTH, BAR_HEIGHT, energy, color, alpha);
        if (FatigueHudRules.showsNumber(mode, level)) {
            UiStyle.text(graphics, minecraft.font, energyText(energy), graphics.guiWidth() / 2 + 91 + NUMBER_GAP,
                    top + NUMBER_Y_OFFSET, UiStyle.withAlpha(color, alpha));
        }
    }

    /** In Mitofus, while the diamond VR helmet's rested bonus lasts: one line on the right-hand column, above the XP. */
    public static void renderRestedLayer(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!HudVisibility.showSurvivalBars(minecraft)) {
            return;
        }
        int seconds = player.getData(ModAttachments.RESTED_SECONDS);
        if (seconds <= 0 || !MitofusWorld.is(player.level())) {
            return;
        }
        Gui gui = minecraft.gui;
        Component text = restedText(seconds);
        int right = graphics.guiWidth() / 2 + 91;
        int top = graphics.guiHeight() - gui.rightHeight + 2;
        gui.rightHeight += RESTED_ROW_HEIGHT;
        UiStyle.text(graphics, minecraft.font, text, right - minecraft.font.width(text), top - 2, UiStyle.GOOD);
    }
}
