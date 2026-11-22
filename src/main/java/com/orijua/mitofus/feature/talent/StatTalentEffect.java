package com.orijua.mitofus.feature.talent;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.Codec;
import com.orijua.mitofus.feature.progression.StatType;

/** Adds flat points to one base stat. */
public record StatTalentEffect(StatType stat, int amount) implements TalentEffect {
    public static final MapCodec<StatTalentEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            StatType.CODEC.fieldOf("stat").forGetter(StatTalentEffect::stat),
            Codec.intRange(1, 1000).fieldOf("amount").forGetter(StatTalentEffect::amount)
    ).apply(instance, StatTalentEffect::new));

    @Override
    public TalentEffectType type() {
        return TalentEffectType.STAT;
    }
}
