package com.orijua.mitofus.feature.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.StringRepresentable;

import java.util.function.Supplier;

public enum EffectType implements StringRepresentable {
    AREA_DAMAGE("area_damage", () -> AreaDamageEffect.CODEC),
    SELF_BUFF("self_buff", () -> SelfBuffEffect.CODEC),
    PROJECTILE("projectile", () -> ProjectileEffect.CODEC);

    public static final Codec<EffectType> CODEC = StringRepresentable.fromEnum(EffectType::values);

    private final String name;
    private final Supplier<MapCodec<? extends Effect>> codec;

    EffectType(String name, Supplier<MapCodec<? extends Effect>> codec) {
        this.name = name;
        this.codec = codec;
    }

    public MapCodec<? extends Effect> codec() {
        return codec.get();
    }

    /** Whether an effect of this kind hurts others (refused during the safety protocol of the fatigue). */
    public boolean dealsDamage() {
        return switch (this) {
            case AREA_DAMAGE, PROJECTILE -> true;
            case SELF_BUFF -> false;
        };
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
