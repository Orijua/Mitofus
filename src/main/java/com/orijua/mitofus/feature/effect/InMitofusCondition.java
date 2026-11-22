package com.orijua.mitofus.feature.effect;

import com.mojang.serialization.MapCodec;
import com.orijua.mitofus.feature.world.MitofusWorld;

/** True while the caster is in Mitofus. The engine checks it for every effect, whatever the data says. */
public record InMitofusCondition() implements Condition {
    public static final InMitofusCondition INSTANCE = new InMitofusCondition();
    public static final MapCodec<InMitofusCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public ConditionType type() {
        return ConditionType.IN_MITOFUS;
    }

    @Override
    public boolean test(EffectContext context) {
        return MitofusWorld.is(context.level());
    }
}
