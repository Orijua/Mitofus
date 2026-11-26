package com.orijua.mitofus.feature.synergy;

import com.orijua.mitofus.feature.progression.ClassGrade;
import com.orijua.mitofus.feature.progression.StatType;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

/**
 * Which stat bonus a class + job synergy gives, with no Minecraft state. A pair has its own entry or falls back to the job's default;
 * nothing is added up across classes: each held branch has its own entry.
 */
public final class SynergyRules {
    private SynergyRules() {
    }

    /** The synergies that exist: {@code specific} by class then job, {@code defaults} by job. */
    public record Index(Map<ResourceLocation, Map<ResourceLocation, SynergyDefinition>> specific, Map<ResourceLocation, SynergyDefinition> defaults) {
        public Optional<SynergyDefinition> own(ResourceLocation classId, ResourceLocation job) {
            return Optional.ofNullable(specific.getOrDefault(classId, Map.of()).get(job));
        }

        /** The entry of the pair, else the job's default. */
        public Optional<SynergyDefinition> of(ResourceLocation classId, ResourceLocation job) {
            return own(classId, job).or(() -> Optional.ofNullable(defaults.get(job)));
        }
    }

    /** The best step of {@code synergy} that {@code grade} and {@code jobLevel} reach, as stat points (empty if the first step is not reached). */
    public static Map<StatType, Integer> reached(SynergyDefinition synergy, ClassGrade grade, int jobLevel) {
        Map<StatType, Integer> best = Map.of();
        for (SynergyDefinition.Step step : synergy.steps()) {
            if (grade.ordinal() >= step.grade().ordinal() && jobLevel >= step.jobLevel()) {
                best = step.stats();
            }
        }
        return best;
    }

    /** How many steps are reached (0 to 3). */
    public static int stepsReached(SynergyDefinition synergy, ClassGrade grade, int jobLevel) {
        int count = 0;
        for (SynergyDefinition.Step step : synergy.steps()) {
            if (grade.ordinal() >= step.grade().ordinal() && jobLevel >= step.jobLevel()) {
                count++;
            }
        }
        return count;
    }

    /** The bonus the class gives with the job: its own entry or the job's default. */
    public static Map<StatType, Integer> bonus(Index index, ResourceLocation classId, ResourceLocation job, ClassGrade grade, int jobLevel) {
        return index.of(classId, job).map(synergy -> reached(synergy, grade, jobLevel)).orElse(Map.of());
    }
}
