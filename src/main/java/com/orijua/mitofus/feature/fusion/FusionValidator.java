package com.orijua.mitofus.feature.fusion;

import com.orijua.mitofus.feature.progression.StatType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cross-checks the fusions and synergies, with no Minecraft state: a hybrid must be worth more than keeping its two parents (it keeps
 * every stat point, skill and passive of both and has talent nodes of its own), the parents and the result must exist and match in rank,
 * and every synergy must name a class and a job that exist, once.
 */
public final class FusionValidator {
    private FusionValidator() {
    }

    /** What the checks need to know about a class. {@code trunkNodes} counts its talent nodes that are not the root. */
    public record ClassFacts(boolean legendary, Map<StatType, Integer> stats, Set<ResourceLocation> skills, Set<ResourceLocation> passives, int trunkNodes) {
    }

    public record FusionFacts(ResourceLocation id, List<ResourceLocation> parents, ResourceLocation result) {
    }

    /** {@code id} is the file id; {@code playerClass} is null for a job's default. */
    public record SynergyFacts(ResourceLocation id, ResourceLocation playerClass, ResourceLocation job) {
    }

    public static List<String> validate(List<FusionFacts> fusions, Map<ResourceLocation, ClassFacts> classes, Set<ResourceLocation> jobs,
                                        List<SynergyFacts> synergies) {
        List<String> problems = new ArrayList<>();
        Set<ResourceLocation> results = new HashSet<>();
        Set<String> pairs = new HashSet<>();
        for (FusionFacts fusion : fusions) {
            String from = "fusion " + fusion.id();
            ClassFacts result = classes.get(fusion.result());
            if (result == null) {
                problems.add(from + ": result class " + fusion.result() + " does not exist");
            }
            if (!results.add(fusion.result())) {
                problems.add(from + ": result class " + fusion.result() + " is also the result of another fusion");
            }
            List<ResourceLocation> sorted = fusion.parents().stream().sorted().toList();
            if (!pairs.add(sorted.toString())) {
                problems.add(from + ": the pair " + sorted + " already has a fusion");
            }
            for (ResourceLocation parentId : fusion.parents()) {
                ClassFacts parent = classes.get(parentId);
                if (parent == null) {
                    problems.add(from + ": parent class " + parentId + " does not exist");
                    continue;
                }
                if (result == null) {
                    continue;
                }
                if (parent.legendary() != result.legendary()) {
                    problems.add(from + ": result class " + fusion.result() + " has a different legendary value than parent " + parentId);
                }
                if (!result.skills().containsAll(parent.skills())) {
                    problems.add(from + ": result class lacks a skill of parent " + parentId);
                }
                if (!result.passives().containsAll(parent.passives())) {
                    problems.add(from + ": result class lacks a passive of parent " + parentId);
                }
            }
            if (result != null && fusion.parents().stream().allMatch(classes::containsKey)) {
                for (StatType stat : StatType.values()) {
                    int parents = fusion.parents().stream().mapToInt(p -> classes.get(p).stats().getOrDefault(stat, 0)).sum();
                    if (result.stats().getOrDefault(stat, 0) < parents) {
                        problems.add(from + ": result class gives less " + stat.getSerializedName() + " than its two parents together");
                    }
                }
                if (result.trunkNodes() < 1) {
                    problems.add(from + ": result class has no talent nodes of its own beyond the merge node");
                }
            }
        }
        Set<String> seen = new HashSet<>();
        for (SynergyFacts synergy : synergies) {
            String from = "synergy " + synergy.id();
            if (!jobs.contains(synergy.job())) {
                problems.add(from + ": job " + synergy.job() + " does not exist");
            }
            if (synergy.playerClass() != null && !classes.containsKey(synergy.playerClass())) {
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
