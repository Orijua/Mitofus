package com.orijua.mitofus.feature.effect;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * A set of continuous bonuses that one source gives (a class passive today; equipment options, talents, gems and sets
 * will share it). Read it with {@link TraitRules}; the player's total comes from {@link ActiveTraits}.
 */
public record Trait(List<Bonus> bonuses) {
    public static final MapCodec<Trait> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Bonus.CODEC.listOf().fieldOf("bonuses").forGetter(Trait::bonuses)
    ).apply(instance, Trait::new));
}
