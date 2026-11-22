package com.orijua.mitofus.feature.effect;

import com.mojang.serialization.MapCodec;

/** True when the context has a target. */
public record RequiresTargetCondition() implements Condition {
    public static final MapCodec<RequiresTargetCondition> CODEC = MapCodec.unit(new RequiresTargetCondition());

    @Override
    public ConditionType type() {
        return ConditionType.REQUIRES_TARGET;
    }

    @Override
    public boolean test(EffectContext context) {
        return context.target().isPresent();
    }
}
