package com.orijua.mitofus.feature.synergy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orijua.mitofus.feature.progression.ClassGrade;
import com.orijua.mitofus.feature.progression.StatType;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A class + job synergy as written in data/&lt;namespace&gt;/mitofus/synergy/&lt;id&gt;.json: a small bonus for holding a class and a job
 * together. With no {@code class} it is the job's default, used by every class that has no entry of its own.
 * Each of the (up to three) {@code steps} asks for a class grade and a job level and gives stat points; the best step reached applies
 * (steps do not add up), so the steps must grow from one to the next.
 */
public record SynergyDefinition(Optional<ResourceLocation> playerClass, ResourceLocation job, List<Step> steps) {
    public static final int MAX_STEPS = 3;

    /** One step: needs {@code grade} (at least) and {@code jobLevel} (at least) and gives {@code stats}. */
    public record Step(ClassGrade grade, int jobLevel, Map<StatType, Integer> stats) {
        public static final Codec<Step> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ClassGrade.CODEC.fieldOf("grade").forGetter(Step::grade),
                Codec.intRange(1, 10_000).fieldOf("job_level").forGetter(Step::jobLevel),
                Codec.unboundedMap(StatType.CODEC, Codec.INT).fieldOf("bonus_stats").forGetter(Step::stats)
        ).apply(instance, Step::new));

        /** The sum of the stat points, to compare two synergies. */
        public int total() {
            return stats.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    public static final Codec<SynergyDefinition> CODEC = RecordCodecBuilder.<SynergyDefinition>create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("class").forGetter(SynergyDefinition::playerClass),
            ResourceLocation.CODEC.fieldOf("job").forGetter(SynergyDefinition::job),
            Step.CODEC.listOf().fieldOf("steps").forGetter(SynergyDefinition::steps)
    ).apply(instance, SynergyDefinition::new)).validate(definition -> {
        if (definition.steps().isEmpty() || definition.steps().size() > MAX_STEPS) {
            return DataResult.error(() -> "a synergy has 1 to " + MAX_STEPS + " steps");
        }
        for (int i = 1; i < definition.steps().size(); i++) {
            Step before = definition.steps().get(i - 1);
            Step step = definition.steps().get(i);
            if (step.grade().ordinal() < before.grade().ordinal() || step.jobLevel() < before.jobLevel() || step.total() < before.total()) {
                return DataResult.error(() -> "each synergy step must ask for at least the grade and job level of the one before and give at least as much");
            }
        }
        return DataResult.success(definition);
    });
}
