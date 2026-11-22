package com.orijua.mitofus.feature.effect;

import com.mojang.serialization.Codec;

/** A predicate on an {@link EffectContext}. The JSON "type" field picks the implementation (see {@link ConditionType}). */
public interface Condition {
    Codec<Condition> CODEC = ConditionType.CODEC.dispatch("type", Condition::type, ConditionType::codec);

    ConditionType type();

    boolean test(EffectContext context);
}
