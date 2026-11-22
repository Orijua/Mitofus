package com.orijua.mitofus.feature.mob;

import com.orijua.mitofus.core.event.MitofusPlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** A player killed a non-player mob. Posted after the kill XP is awarded. */
public final class MobKilledEvent extends MitofusPlayerEvent {
    private final LivingEntity victim;

    public MobKilledEvent(ServerPlayer player, LivingEntity victim) {
        super(player);
        this.victim = victim;
    }

    public LivingEntity victim() {
        return victim;
    }
}
