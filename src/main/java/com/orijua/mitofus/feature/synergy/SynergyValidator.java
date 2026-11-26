package com.orijua.mitofus.feature.synergy;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Cross-checks the synergies, with no Minecraft state: every one must name a class and a job that exist, once. */
public final class SynergyValidator {
    private SynergyValidator() {
    }

    /** {@code id} is the file id; {@code playerClass} is null for a job's default. */
    public record SynergyFacts(ResourceLocation id, ResourceLocation playerClass, ResourceLocation job) {
    }

    public static List<String> validate(Set<ResourceLocation> classes, Set<ResourceLocation> jobs, List<SynergyFacts> synergies) {
        List<String> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SynergyFacts synergy : synergies) {
            String from = "synergy " + synergy.id();
            if (!jobs.contains(synergy.job())) {
                problems.add(from + ": job " + synergy.job() + " does not exist");
            }
            if (synergy.playerClass() != null && !classes.contains(synergy.playerClass())) {
                problems.add(from + ": class " + synergy.playerClass() + " does not exist");
            }
            if (!seen.add(synergy.playerClass() + "|" + synergy.job())) {
                problems.add(from + ": there is another synergy for the same class and job");
            }
        }
        problems.sort(null);
        return problems;
    }
}
