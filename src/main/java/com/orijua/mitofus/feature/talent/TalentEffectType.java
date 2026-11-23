package com.orijua.mitofus.feature.talent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.StringRepresentable;

import java.util.function.Supplier;

public enum TalentEffectType implements StringRepresentable {
    STAT("stat", () -> StatTalentEffect.CODEC),
    SKILL_BOOST("skill_boost", () -> SkillBoostTalentEffect.CODEC),
    PASSIVE_BOOST("passive_boost", () -> PassiveBoostTalentEffect.CODEC),
    GRANT_SKILL("grant_skill", () -> GrantSkillTalentEffect.CODEC);

    public static final Codec<TalentEffectType> CODEC = StringRepresentable.fromEnum(TalentEffectType::values);

    private final String name;
    private final Supplier<MapCodec<? extends TalentEffect>> codec;

    TalentEffectType(String name, Supplier<MapCodec<? extends TalentEffect>> codec) {
        this.name = name;
        this.codec = codec;
    }

    public MapCodec<? extends TalentEffect> codec() {
        return codec.get();
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
