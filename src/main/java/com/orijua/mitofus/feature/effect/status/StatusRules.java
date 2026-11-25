package com.orijua.mitofus.feature.effect.status;

import java.util.Optional;

/** Pure rules of statuses: how the chance, the intensity and the duration are kept inside the status's limits. */
public final class StatusRules {
    /** The numbers of a status, apart from the game effect that carries it. */
    public record Limits(int durationTicks, int maxDurationTicks, int intensity, int maxIntensity, double maxChance, int immunityTicks) {
    }

    private StatusRules() {
    }

    /** The chance asked for, kept between 0 and the status's maximum. */
    public static double chance(double asked, Limits status) {
        return Math.max(0, Math.min(asked, status.maxChance()));
    }

    /** The duration in ticks: the one asked for, or the status's own, never above its maximum. */
    public static int duration(Optional<Integer> asked, Limits status) {
        return Math.max(1, Math.min(asked.orElse(status.durationTicks()), status.maxDurationTicks()));
    }

    /** The intensity: the one asked for, or the status's own, never above its maximum. */
    public static int intensity(Optional<Integer> asked, Limits status) {
        return Math.max(1, Math.min(asked.orElse(status.intensity()), status.maxIntensity()));
    }

    /** Game tick until which a target cannot get the status again: from now, through its duration and then the immunity window. */
    public static long immuneUntil(long now, int durationTicks, Limits status) {
        return status.immunityTicks() > 0 ? now + durationTicks + status.immunityTicks() : 0;
    }

    public static boolean isImmune(long now, long immuneUntil) {
        return now < immuneUntil;
    }
}
