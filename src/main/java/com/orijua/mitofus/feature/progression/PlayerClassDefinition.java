package com.orijua.mitofus.feature.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A class as written in data/<namespace>/mitofus/player_class/<id>.json: how the player fights.
 * Basic classes are {@code selectable} from the Profile tab; the others ({@code "selectable": false})
 * can only be obtained as a quest's {@code class_reward}. {@code legendary} marks the strong versions.
 * {@code skills} and {@code passives} may be empty (a class does not have to give any); {@code third_slot_passive}
 * is an extra passive that only works when the class sits in the third slot (index 2). {@code grade_requirements}
 * lists what going up to each grade asks for besides the class level (a job level).
 */
public record PlayerClassDefinition(
        Component name,
        Component description,
        boolean legendary,
        boolean selectable,
        Map<StatType, Integer> bonusStats,
        List<ResourceLocation> skills,
        List<ResourceLocation> passives,
        Optional<ResourceLocation> thirdSlotPassive,
        List<GradeRequirement> gradeRequirements) {

    public static final Codec<PlayerClassDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ComponentSerialization.CODEC.fieldOf("name").forGetter(PlayerClassDefinition::name),
            ComponentSerialization.CODEC.optionalFieldOf("description", Component.empty()).forGetter(PlayerClassDefinition::description),
            Codec.BOOL.optionalFieldOf("legendary", false).forGetter(PlayerClassDefinition::legendary),
            Codec.BOOL.optionalFieldOf("selectable", true).forGetter(PlayerClassDefinition::selectable),
            Codec.unboundedMap(StatType.CODEC, Codec.INT).optionalFieldOf("bonus_stats", Map.of()).forGetter(PlayerClassDefinition::bonusStats),
            ResourceLocation.CODEC.listOf().optionalFieldOf("skills", List.of()).forGetter(PlayerClassDefinition::skills),
            ResourceLocation.CODEC.listOf().optionalFieldOf("passives", List.of()).forGetter(PlayerClassDefinition::passives),
            ResourceLocation.CODEC.optionalFieldOf("third_slot_passive").forGetter(PlayerClassDefinition::thirdSlotPassive),
            GradeRequirement.CODEC.listOf().optionalFieldOf("grade_requirements", List.of()).forGetter(PlayerClassDefinition::gradeRequirements)
    ).apply(instance, PlayerClassDefinition::new));
}
