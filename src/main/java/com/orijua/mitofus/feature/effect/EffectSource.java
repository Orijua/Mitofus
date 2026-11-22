package com.orijua.mitofus.feature.effect;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Where an effect comes from, for messages and for rules that treat sources differently. */
public record EffectSource(Kind kind, ResourceLocation id, Component name) {
    public enum Kind { SKILL, ITEM_OPTION, TALENT, GEM, SET, BOSS, CONSUMABLE }

    public static EffectSource skill(ResourceLocation id, Component name) {
        return new EffectSource(Kind.SKILL, id, name);
    }
}
