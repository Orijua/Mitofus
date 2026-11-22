package com.orijua.mitofus.feature.progression;

import com.orijua.mitofus.core.event.MitofusPlayerEvent;
import net.minecraft.server.level.ServerPlayer;

/** A player gained one or more Mitofus levels at once. Posted after the new stats are saved. */
public final class LevelUpEvent extends MitofusPlayerEvent {
    private final int previousLevel;
    private final int newLevel;

    public LevelUpEvent(ServerPlayer player, int previousLevel, int newLevel) {
        super(player);
        this.previousLevel = previousLevel;
        this.newLevel = newLevel;
    }

    public int previousLevel() {
        return previousLevel;
    }

    public int newLevel() {
        return newLevel;
    }
}
