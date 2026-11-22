package com.orijua.mitofus.feature.progression;

import com.orijua.mitofus.core.event.MitofusPlayerEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A player placed a class in a slot: chosen at the start ({@code granted} false) or given by a quest's
 * class_reward ({@code granted} true). Posted after its skills and bonuses apply.
 */
public final class ClassChangedEvent extends MitofusPlayerEvent {
    private final ResourceLocation playerClass;
    private final int slot;
    private final boolean granted;

    public ClassChangedEvent(ServerPlayer player, int slot, ResourceLocation playerClass, boolean granted) {
        super(player);
        this.slot = slot;
        this.playerClass = playerClass;
        this.granted = granted;
    }

    public ResourceLocation playerClass() {
        return playerClass;
    }

    /** The slot (0 to 2) the class was placed in. */
    public int slot() {
        return slot;
    }

    public boolean granted() {
        return granted;
    }
}
