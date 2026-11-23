package com.orijua.mitofus.feature.talent;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Teaches a new skill that the class does not have from the start. */
public record GrantSkillTalentEffect(ResourceLocation skill) implements TalentEffect {
    public static final MapCodec<GrantSkillTalentEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("skill").forGetter(GrantSkillTalentEffect::skill)
    ).apply(instance, GrantSkillTalentEffect::new));

    @Override
    public TalentEffectType type() {
        return TalentEffectType.GRANT_SKILL;
    }
}
