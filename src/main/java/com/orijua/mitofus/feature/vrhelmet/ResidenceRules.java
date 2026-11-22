package com.orijua.mitofus.feature.vrhelmet;

import com.orijua.mitofus.core.config.ResidenceBalance;

/** The rules of becoming resident, pure: one residence at a time and a cooldown so it is not a teleport. */
public final class ResidenceRules {
    public enum Result { OK, SAME_PLACE, COOLDOWN }

    private ResidenceRules() {
    }

    /** How long a change of residence must wait, in ticks (a game day by default; see the config). */
    public static long cooldownTicks() {
        return ResidenceBalance.current().cooldownTicks();
    }

    /** Whether the player may become resident of {@code site} at game time {@code now}. */
    public static Result check(Residence current, String site, long now) {
        if (!current.has()) {
            return Result.OK;
        }
        if (current.site().equals(site)) {
            return Result.SAME_PLACE;
        }
        return ticksLeft(current, now) > 0L ? Result.COOLDOWN : Result.OK;
    }

    /** The game time left before the next change, 0 if it can change now. */
    public static long ticksLeft(Residence current, long now) {
        if (!current.has() || now < current.changedAt()) {
            return 0L;
        }
        return Math.max(0L, cooldownTicks() - (now - current.changedAt()));
    }
}
