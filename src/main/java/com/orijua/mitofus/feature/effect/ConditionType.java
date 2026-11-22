package com.orijua.mitofus.feature.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.StringRepresentable;

import java.util.function.Supplier;

public enum ConditionType implements StringRepresentable {
    IN_MITOFUS("in_mitofus", () -> InMitofusCondition.CODEC),
    REQUIRES_TARGET("requires_target", () -> RequiresTargetCondition.CODEC),
    WHILE_SNEAKING("while_sneaking", () -> WhileSneakingCondition.CODEC);

    public static final Codec<ConditionType> CODEC = StringRepresentable.fromEnum(ConditionType::values);

    private final String name;
    private final Supplier<MapCodec<? extends Condition>> codec;

    ConditionType(String name, Supplier<MapCodec<? extends Condition>> codec) {
        this.name = name;
        this.codec = codec;
    }

    public MapCodec<? extends Condition> codec() {
        return codec.get();
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
