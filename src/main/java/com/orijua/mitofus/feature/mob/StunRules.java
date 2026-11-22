package com.orijua.mitofus.feature.mob;

import com.orijua.mitofus.core.config.StunBalance;

/** When a blow stuns. Pure: the event glue is in {@link StunService}. */
public final class StunRules {
    private StunRules() {
    }

    /** True if a blow of {@code damage} on a victim with {@code maxHealth} takes at least the configured share of its life. */
    public static boolean stuns(StunBalance balance, float damage, float maxHealth) {
        return balance.durationSeconds() > 0 && maxHealth > 0 && damage >= maxHealth * balance.lifeFraction();
    }

    /** The game tick until which a victim stunned at {@code now} cannot be stunned again: the stun itself plus the immunity after it. */
    public static long immuneUntil(StunBalance balance, long now) {
        return now + balance.durationTicks() + balance.immunityTicks();
    }

    /** Whether a victim whose immunity ends at {@code immuneUntil} is still protected at {@code now} (0 = never stunned). */
    public static boolean isImmune(long now, long immuneUntil) {
        return now < immuneUntil;
    }
}
