package com.orijua.mitofus.feature.effect.status;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.effect.Bonus;
import com.orijua.mitofus.feature.effect.BonusType;
import com.orijua.mitofus.feature.effect.PassiveDefinition;
import com.orijua.mitofus.feature.skill.SkillDefinition;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Checks the status and bonus data: defaults under their maximums, bonuses that name a real status, caps named after a bonus kind. */
public final class StatusValidator {
    private StatusValidator() {
    }

    /**
     * @param statuses   the limits of every status by id
     * @param bonuses    the bonuses of every passive and skill passive, by the id of whoever carries them
     * @param limitIds   the ids of the files in {@code bonus_limit}
     */
    public static List<String> validate(Map<ResourceLocation, StatusRules.Limits> statuses, Map<ResourceLocation, List<Bonus>> bonuses,
                                        Set<ResourceLocation> limitIds) {
        List<String> problems = new ArrayList<>();
        statuses.forEach((id, limits) -> {
            if (limits.durationTicks() > limits.maxDurationTicks()) {
                problems.add("status " + id + ": duration_ticks is above max_duration_ticks");
            }
            if (limits.intensity() > limits.maxIntensity()) {
                problems.add("status " + id + ": intensity is above max_intensity");
            }
        });
        bonuses.forEach((owner, list) -> list.stream().filter(b -> b.type() == BonusType.APPLY_STATUS).forEach(bonus -> {
            if (bonus.status().isEmpty()) {
                problems.add(owner + ": an apply_status bonus has no status");
            } else if (!statuses.containsKey(bonus.status().get())) {
                problems.add(owner + ": apply_status names unknown status " + bonus.status().get());
            }
        }));
        Set<ResourceLocation> kinds = java.util.Arrays.stream(BonusType.values())
                .map(type -> Mitofus.id(type.getSerializedName())).collect(Collectors.toSet());
        limitIds.stream().filter(id -> !kinds.contains(id))
                .forEach(id -> problems.add("bonus_limit " + id + ": no bonus kind has this name"));
        return problems;
    }

    public static List<String> problems(RegistryAccess access) {
        Map<ResourceLocation, StatusRules.Limits> statuses = new HashMap<>();
        Definitions.entries(access, ModDatapackRegistries.STATUS).forEach((id, status) -> statuses.put(id, status.limits()));
        Map<ResourceLocation, List<Bonus>> bonuses = new HashMap<>();
        Definitions.entries(access, ModDatapackRegistries.PASSIVE)
                .forEach((id, passive) -> bonuses.put(id, passive.trait().bonuses()));
        Definitions.entries(access, ModDatapackRegistries.SKILL)
                .forEach((id, skill) -> skill.passive().ifPresent(trait -> bonuses.put(id, trait.bonuses())));
        return validate(statuses, bonuses, Definitions.entries(access, ModDatapackRegistries.BONUS_LIMIT).keySet());
    }
}
