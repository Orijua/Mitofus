package com.orijua.mitofus.feature.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * Pure rules of the XP per hour: a window of the last {@link #WINDOW_MINUTES} minutes played (14 full minutes plus the
 * one in progress), counted second by second. No Minecraft state here.
 */
public final class XpRateRules {
    /** The window shown to the player, in minutes. */
    public static final int WINDOW_MINUTES = XpRate.MAX_MINUTES + 1;
    /** Seconds that must be measured before a rate is shown. */
    public static final int MIN_SAMPLE_SECONDS = 60;
    /** An ETA above this (99 h) is not shown. */
    public static final long MAX_ETA_MINUTES = 99L * 60L;

    private XpRateRules() {
    }

    /** One more second played; on the 60th the minute in progress closes and the oldest one drops out of the window. */
    public static XpRate tick(XpRate rate) {
        int seconds = rate.currentSeconds() + 1;
        if (seconds < 60) {
            return new XpRate(rate.minutes(), rate.currentXp(), seconds);
        }
        List<Integer> minutes = new ArrayList<>(rate.minutes().size() + 1);
        minutes.add(rate.currentXp());
        minutes.addAll(rate.minutes());
        return new XpRate(minutes, 0, 0);
    }

    /** {@code amount} more XP in the minute in progress (saturating); an amount that is not positive changes nothing. */
    public static XpRate record(XpRate rate, int amount) {
        if (amount <= 0) {
            return rate;
        }
        int xp = (int) Math.min(Integer.MAX_VALUE, (long) rate.currentXp() + amount);
        return new XpRate(rate.minutes(), xp, rate.currentSeconds());
    }

    /** Seconds of play the window covers. */
    public static int coveredSeconds(XpRate rate) {
        return rate.minutes().size() * 60 + rate.currentSeconds();
    }

    /** All the XP in the window. */
    public static long totalXp(XpRate rate) {
        long total = rate.currentXp();
        for (int xp : rate.minutes()) {
            total += xp;
        }
        return total;
    }

    /** XP per hour over the window, or empty while less than {@link #MIN_SAMPLE_SECONDS} are measured. */
    public static OptionalLong xpPerHour(XpRate rate) {
        int covered = coveredSeconds(rate);
        if (covered < MIN_SAMPLE_SECONDS) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.round(totalXp(rate) * 3600.0 / covered));
    }

    /** Minutes (at least 1, rounded up) to earn {@code remaining} XP at {@code xpPerHour}; empty if no pace, nothing left or over {@link #MAX_ETA_MINUTES}. */
    public static OptionalLong etaMinutes(int remaining, long xpPerHour) {
        if (remaining <= 0 || xpPerHour <= 0) {
            return OptionalLong.empty();
        }
        long minutes = Math.max(1L, (remaining * 60L + xpPerHour - 1) / xpPerHour);
        return minutes > MAX_ETA_MINUTES ? OptionalLong.empty() : OptionalLong.of(minutes);
    }
}
