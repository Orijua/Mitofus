package com.orijua.mitofus.feature.fatigue.client;

import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.feature.fatigue.ClientFatigue;
import com.orijua.mitofus.feature.fatigue.FatigueRules;

/** The pure rules of the energy HUD (what shows, which color, how it flashes), apart from drawing so they can be tested. */
public final class FatigueHudRules {
    private FatigueHudRules() {
    }

    /** The color of the bar for a level. */
    static int colorFor(FatigueRules.Level level) {
        return switch (level) {
            case CRITICAL -> 0xFF000000 | UiStyle.BAD;
            case LOW -> UiStyle.COOLDOWN;
            case NORMAL -> UiStyle.FATIGUE;
        };
    }

    /** Whether the bar shows: AUTO only while the energy is not full (as the player reads it). */
    static boolean showsBar(ClientFatigue.Display mode, float energy) {
        return switch (mode) {
            case HIDDEN -> false;
            case ALWAYS -> true;
            case AUTO -> FatigueRules.percent(energy) < 100;
        };
    }

    /** Whether the number shows next to the bar: AUTO only when a warning applies. */
    static boolean showsNumber(ClientFatigue.Display mode, FatigueRules.Level level) {
        return switch (mode) {
            case HIDDEN -> false;
            case ALWAYS -> true;
            case AUTO -> level != FatigueRules.Level.NORMAL;
        };
    }

    /**
     * Flashes when about to run out: dim in the "off" half of the cycle. {@code millis} is {@code Util.getMillis()}, never
     * negative (a negative value would just shift the cycle).
     */
    static float alpha(FatigueRules.Level level, float energy, long millis) {
        return level == FatigueRules.Level.CRITICAL && energy > 0 && (millis / 300) % 2 == 0 ? 0.45f : 1.0f;
    }
}
