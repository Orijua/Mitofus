package com.orijua.mitofus.core.client;

import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.client.Minecraft;

public final class HudVisibility {
    private HudVisibility() {
    }

    /** Same rule as vanilla's health and food bars: hidden in creative, spectator and with F1. */
    public static boolean showSurvivalBars(Minecraft minecraft) {
        return minecraft.player != null && !minecraft.options.hideGui
                && minecraft.gameMode != null && minecraft.gameMode.canHurtPlayer();
    }

    /** The mod's interface is only visible in Mitofus; in the real world it stays out of sight. */
    public static boolean inMitofus(Minecraft minecraft) {
        return minecraft.level != null && MitofusWorld.is(minecraft.level);
    }
}
