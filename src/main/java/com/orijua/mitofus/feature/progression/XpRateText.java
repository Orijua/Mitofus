package com.orijua.mitofus.feature.progression;

import net.minecraft.network.chat.Component;

import java.util.OptionalLong;

/** The text side of the XP per hour, shared by the status tab and the debug command. */
public final class XpRateText {
    private XpRateText() {
    }

    /** Minutes to earn {@code remaining} XP at the measured pace; empty if there is no pace yet (see {@link XpRateRules#etaMinutes}). */
    public static OptionalLong etaMinutes(XpRate rate, int remaining) {
        OptionalLong perHour = XpRateRules.xpPerHour(rate);
        return perHour.isPresent() ? XpRateRules.etaMinutes(remaining, perHour.getAsLong()) : OptionalLong.empty();
    }

    /** "~N min" style duration: "5 min", "2 h 5 min". */
    public static Component duration(long minutes) {
        return minutes < 60
                ? Component.translatable("screen.mitofus.duration.minutes", minutes)
                : Component.translatable("screen.mitofus.duration.hours", minutes / 60, minutes % 60);
    }
}
