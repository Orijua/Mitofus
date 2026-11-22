package com.orijua.mitofus.core.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/** Tooltips inside the mod's screens ({@link MitofusStyledScreen}) use the Mitofus window colors instead of vanilla's purple frame. */
public final class MitofusTooltips {
    private MitofusTooltips() {
    }

    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof MitofusStyledScreen) {
            event.setBackgroundStart(UiStyle.WINDOW_TOP | 0xF0000000);
            event.setBackgroundEnd(UiStyle.WINDOW_BOTTOM | 0xF0000000);
            event.setBorderStart(UiStyle.LINE_BRIGHT);
            event.setBorderEnd(UiStyle.LINE_SOFT);
        }
    }
}
