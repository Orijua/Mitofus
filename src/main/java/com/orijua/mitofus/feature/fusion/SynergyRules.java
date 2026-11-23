package com.orijua.mitofus.feature.fusion;

import com.orijua.mitofus.feature.progression.ClassGrade;
import com.orijua.mitofus.feature.progression.StatType;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which stat bonus a class + job synergy gives, with no Minecraft state. A pair has its own entry or falls back to the job's default;
 * a hybrid with no entry of its own gets the better of its parents' synergies times the fusion's multiplier.
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

    public static Map<StatType, Integer> times(Map<StatType, Integer> stats, double multiplier) {
        Map<StatType, Integer> result = new EnumMap<>(StatType.class);
        stats.forEach((stat, value) -> {
            int scaled = (int) Math.round(value * multiplier);
            if (scaled != 0) {
                result.put(stat, scaled);
            }
        });
        return result;
    }

    private static int total(Map<StatType, Integer> stats) {
        return stats.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * The bonus the class gives with the job. {@code origin} is empty for a normal class; for a hybrid it holds its two parents and
     * {@code multiplier} is its fusion's synergy multiplier, used only when the hybrid has no entry of its own.
     */
    public static Map<StatType, Integer> bonus(Index index, ResourceLocation classId, List<ResourceLocation> origin, double multiplier,
                                               ResourceLocation job, ClassGrade grade, int jobLevel) {
        if (!origin.isEmpty() && index.own(classId, job).isEmpty()) {
            Map<StatType, Integer> best = Map.of();
            for (ResourceLocation parent : origin) {
                Map<StatType, Integer> candidate = index.of(parent, job).map(s -> reached(s, grade, jobLevel)).orElse(Map.of());
                if (total(candidate) > total(best)) {
                    best = candidate;
                }
            }
            return times(best, multiplier);
        }
        return index.of(classId, job).map(synergy -> reached(synergy, grade, jobLevel)).orElse(Map.of());
    }
}
