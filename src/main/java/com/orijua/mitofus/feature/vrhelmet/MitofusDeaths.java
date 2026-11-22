package com.orijua.mitofus.feature.vrhelmet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What the server remembers of a player's deaths in Mitofus. Saved with the player, kept on death, never synced.
 *
 * @param lastDeathAt       the game time of the last death that counts, or 0 for "none yet" (the first death after a
 *                          double death starts counting again from zero)
 * @param pendingDisconnect the second death in the window happened and the disconnection has not run yet
 * @param unlockAt          the game time (overworld) from which the player may connect to Mitofus again after being
 *                          disconnected by a double death, or 0 for "no lock". It is separate from {@code lastDeathAt}
 *                          because the double death resets that counter. Saves without it read as 0.
 */
public record MitofusDeaths(long lastDeathAt, boolean pendingDisconnect, long unlockAt) {
    public static final MitofusDeaths EMPTY = new MitofusDeaths(0L, false, 0L);

    public static final Codec<MitofusDeaths> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("last_death_at", 0L).forGetter(MitofusDeaths::lastDeathAt),
            Codec.BOOL.optionalFieldOf("pending_disconnect", false).forGetter(MitofusDeaths::pendingDisconnect),
            Codec.LONG.optionalFieldOf("unlock_at", 0L).forGetter(MitofusDeaths::unlockAt)
    ).apply(instance, MitofusDeaths::new));

    public MitofusDeaths withoutPending() {
        return new MitofusDeaths(lastDeathAt, false, unlockAt);
    }

    public MitofusDeaths withUnlockAt(long unlockAt) {
        return new MitofusDeaths(lastDeathAt, pendingDisconnect, unlockAt);
    }
}
