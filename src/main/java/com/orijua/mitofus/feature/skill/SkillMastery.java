package com.orijua.mitofus.feature.skill;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Successful uses of each learned skill. A separate attachment from {@link PlayerSkills}, which is synced whole. */
public record SkillMastery(Map<ResourceLocation, Integer> uses) {
    public static final SkillMastery EMPTY = new SkillMastery(Map.of());
    public static final Codec<SkillMastery> CODEC = Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
            .xmap(SkillMastery::new, SkillMastery::uses);
    public static final StreamCodec<ByteBuf, SkillMastery> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public int uses(ResourceLocation skill) {
        return uses.getOrDefault(skill, 0);
    }

    public int level(ResourceLocation skill) {
        return MasteryRules.level(uses(skill));
    }

    public SkillMastery withUse(ResourceLocation skill) {
        Map<ResourceLocation, Integer> updated = new HashMap<>(uses);
        updated.merge(skill, 1, Integer::sum);
        return new SkillMastery(Map.copyOf(updated));
    }
}
