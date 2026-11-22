package com.orijua.mitofus.feature.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * A job (profession) as written in data/<namespace>/mitofus/job/<id>.json: how the player earns a living.
 * Like classes, the basic ones are {@code selectable}; the others can only be a quest's {@code job_reward}.
 * {@code smithing_bonus} is added to the smithing level (the blacksmith's).
 */
public record JobDefinition(
        Component name,
        Component description,
        boolean legendary,
        boolean selectable,
        Map<StatType, Integer> bonusStats,
        int smithingBonus,
        List<ResourceLocation> skills) {

    public static final Codec<JobDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ComponentSerialization.CODEC.fieldOf("name").forGetter(JobDefinition::name),
            ComponentSerialization.CODEC.optionalFieldOf("description", Component.empty()).forGetter(JobDefinition::description),
            Codec.BOOL.optionalFieldOf("legendary", false).forGetter(JobDefinition::legendary),
            Codec.BOOL.optionalFieldOf("selectable", true).forGetter(JobDefinition::selectable),
            Codec.unboundedMap(StatType.CODEC, Codec.INT).optionalFieldOf("bonus_stats", Map.of()).forGetter(JobDefinition::bonusStats),
            Codec.intRange(0, 100).optionalFieldOf("smithing_bonus", 0).forGetter(JobDefinition::smithingBonus),
            ResourceLocation.CODEC.listOf().optionalFieldOf("skills", List.of()).forGetter(JobDefinition::skills)
    ).apply(instance, JobDefinition::new));
}
