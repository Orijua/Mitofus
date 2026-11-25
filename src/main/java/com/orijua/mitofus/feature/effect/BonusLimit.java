package com.orijua.mitofus.feature.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The global cap of one kind of bonus, as written in data/&lt;namespace&gt;/mitofus/bonus_limit/&lt;bonus type&gt;.json
 * (the file is named after the bonus type, e.g. {@code crit_chance.json}). However many passives, skills or items add
 * to a kind, the total never goes above {@code cap}.
 */
public record BonusLimit(double cap) {
    public static final Codec<BonusLimit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(0, 1_000_000).fieldOf("cap").forGetter(BonusLimit::cap)
    ).apply(instance, BonusLimit::new));
}
