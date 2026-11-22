package com.orijua.mitofus.feature.effect;

import com.mojang.serialization.MapCodec;

/** True while the caster is sneaking. */
public record WhileSneakingCondition() implements Condition {
    public static final MapCodec<WhileSneakingCondition> CODEC = MapCodec.unit(new WhileSneakingCondition());

    @Override
    public ConditionType type() {
        return ConditionType.WHILE_SNEAKING;
    }

    @Override
    public boolean test(EffectContext context) {
        return context.caster().isShiftKeyDown();
    }
}
