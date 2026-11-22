package com.orijua.mitofus.feature.npc.client;

import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.feature.view.ViewRules;
import com.orijua.mitofus.feature.npc.ApprenticeSmith;
import net.minecraft.network.chat.Component;

/**
 * What an apprentice looks like on screen, shared by his own window, the Workshop tab and the Jade tooltip
 * (compat.jade) so they always agree: add a new rule (a colour, a threshold) here, never in one of them only.
 */
public final class ApprenticeUi {
    private ApprenticeUi() {
    }

    /** Green while he forges, grey with no order, red when he is stopped. */
    static int statusColor(ApprenticeSmith.Status status) {
        if (status.stopped()) {
            return UiStyle.BAD;
        }
        return status == ApprenticeSmith.Status.WORKING ? UiStyle.GOOD : UiStyle.MUTED;
    }

    /** His level as the player may know it: "??" until their View reveals it (in his window and in the Workshop). */
    public static String levelText(int view, int level) {
        return ViewRules.detail(view) == ViewRules.Detail.LOW ? "??" : String.valueOf(level);
    }

    /** "Beginner · smithing 12" under his name, the level hidden as "??" until the player's View reveals it. */
    public static Component stageLine(ApprenticeSmith apprentice, int view) {
        Component stage = Component.translatable("npc.mitofus.stage." + apprentice.shownStage().key());
        return Component.translatable("screen.mitofus.apprentice.subtitle_stage", stage, levelText(view, apprentice.shownLevel()));
    }

    /** His name without any title: gold when he is a named smith, white otherwise. */
    static Component nameText(String name, boolean named) {
        return Component.literal(name).withColor(named ? UiStyle.GOLD : UiStyle.TEXT);
    }

    /** His status with a red "!" in front when he is stopped, so a stop stands out the same way everywhere. */
    public static Component statusText(Component status, boolean stopped) {
        if (!stopped) {
            return status;
        }
        return Component.literal("! ").withColor(UiStyle.BAD).append(status);
    }

    /** Red when no day of wage is left. */
    static int wageColor(int daysPaid, int normal) {
        return daysPaid > 0 ? normal : UiStyle.BAD;
    }
}
