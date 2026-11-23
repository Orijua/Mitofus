package com.orijua.mitofus.feature.talent;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orijua.mitofus.feature.effect.Bonus;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Improves a passive the class already has: {@code bonuses} are added to the ones the passive gives, in the same
 * format as a passive's own {@code bonuses}.
 */
public record PassiveBoostTalentEffect(ResourceLocation passive, List<Bonus> bonuses) implements TalentEffect {
    public static final MapCodec<PassiveBoostTalentEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("passive").forGetter(PassiveBoostTalentEffect::passive),
            Bonus.CODEC.listOf().fieldOf("bonuses").forGetter(PassiveBoostTalentEffect::bonuses)
    ).apply(instance, PassiveBoostTalentEffect::new));

    @Override
    public TalentEffectType type() {
        return TalentEffectType.PASSIVE_BOOST;
    }
}
