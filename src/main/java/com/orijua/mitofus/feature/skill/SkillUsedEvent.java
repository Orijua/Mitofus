package com.orijua.mitofus.feature.skill;

import com.orijua.mitofus.core.event.MitofusPlayerEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** A player successfully used a skill (effect applied, mana spent, cooldown started). */
public final class SkillUsedEvent extends MitofusPlayerEvent {
    private final ResourceLocation skill;

    public SkillUsedEvent(ServerPlayer player, ResourceLocation skill) {
        super(player);
        this.skill = skill;
    }

    public ResourceLocation skill() {
        return skill;
    }
}
