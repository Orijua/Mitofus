package com.orijua.mitofus.feature.npc.client;

import com.orijua.mitofus.feature.npc.NameColors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

/** Paints the name above an NPC or boss with its color (see {@link NameColors}), unless its name already has one. */
public final class NpcNameColors {
    private NpcNameColors() {
    }

    public static void onRenderNameTag(RenderNameTagEvent event) {
        ChatFormatting color = NameColors.of(event.getEntity());
        if (color == null || event.getContent().getStyle().getColor() != null) {
            return;
        }
        event.setContent(Component.empty().append(event.getContent()).withStyle(color));
    }
}
