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
        List<GradeRequirement> gradeRequirements,
        CatchUp catchUp) {

    /**
     * Extra class XP for a class that sits far below the player's level (a late starter): while the class is at least {@code minGap}
     * levels below the player, the XP it gets is multiplied by {@code multiplier}. {@code enabled: false} switches it off for the class.
     */
    public record CatchUp(boolean enabled, double multiplier, int minGap) {
        public static final CatchUp DEFAULT = new CatchUp(true, 2.0, 10);

        public static final Codec<CatchUp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(CatchUp::enabled),
                Codec.doubleRange(1.0, 10.0).optionalFieldOf("multiplier", 2.0).forGetter(CatchUp::multiplier),
                Codec.intRange(1, 1000).optionalFieldOf("min_gap", 10).forGetter(CatchUp::minGap)
        ).apply(instance, CatchUp::new));
    }

    public static final Codec<PlayerClassDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ComponentSerialization.CODEC.fieldOf("name").forGetter(PlayerClassDefinition::name),
            ComponentSerialization.CODEC.optionalFieldOf("description", Component.empty()).forGetter(PlayerClassDefinition::description),
            Codec.BOOL.optionalFieldOf("legendary", false).forGetter(PlayerClassDefinition::legendary),
            Codec.BOOL.optionalFieldOf("selectable", true).forGetter(PlayerClassDefinition::selectable),
            Codec.unboundedMap(StatType.CODEC, Codec.INT).optionalFieldOf("bonus_stats", Map.of()).forGetter(PlayerClassDefinition::bonusStats),
            ResourceLocation.CODEC.listOf().optionalFieldOf("skills", List.of()).forGetter(PlayerClassDefinition::skills),
            ResourceLocation.CODEC.listOf().optionalFieldOf("passives", List.of()).forGetter(PlayerClassDefinition::passives),
            ResourceLocation.CODEC.optionalFieldOf("third_slot_passive").forGetter(PlayerClassDefinition::thirdSlotPassive),
            GradeRequirement.CODEC.listOf().optionalFieldOf("grade_requirements", List.of()).forGetter(PlayerClassDefinition::gradeRequirements),
            CatchUp.CODEC.optionalFieldOf("catch_up", CatchUp.DEFAULT).forGetter(PlayerClassDefinition::catchUp)
    ).apply(instance, PlayerClassDefinition::new));
}
