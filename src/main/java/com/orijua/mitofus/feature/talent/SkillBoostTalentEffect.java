package com.orijua.mitofus.feature.talent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * Improves a skill the class already has. Each multiplier is applied on top of the skill's own value and defaults to 1
 * (no change): a damage multiplier above 1 hurts more, a cost or cooldown multiplier below 1 is cheaper or faster.
 */
public record SkillBoostTalentEffect(ResourceLocation skill, double damageMultiplier, double manaCostMultiplier,
                                     double cooldownMultiplier) implements TalentEffect {
    public static final MapCodec<SkillBoostTalentEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("skill").forGetter(SkillBoostTalentEffect::skill),
            Codec.doubleRange(0.1, 10).optionalFieldOf("damage_multiplier", 1.0).forGetter(SkillBoostTalentEffect::damageMultiplier),
            Codec.doubleRange(0.1, 10).optionalFieldOf("mana_cost_multiplier", 1.0).forGetter(SkillBoostTalentEffect::manaCostMultiplier),
            Codec.doubleRange(0.1, 10).optionalFieldOf("cooldown_multiplier", 1.0).forGetter(SkillBoostTalentEffect::cooldownMultiplier)
    ).apply(instance, SkillBoostTalentEffect::new));

    /** A boost that changes nothing is a data mistake. */
    public boolean changesAnything() {
        return damageMultiplier != 1.0 || manaCostMultiplier != 1.0 || cooldownMultiplier != 1.0;
    }

    @Override
    public TalentEffectType type() {
        return TalentEffectType.SKILL_BOOST;
    }
}
