package com.orijua.mitofus.feature.combination;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The class of a player is the set of branches they hold, whatever slots they sit in. A set is written as a key: its branches without
 * repeats, always in the same order (by id), so two slot orders give the same key. No Minecraft state here.
 */
public final class CombinationRules {
    /** The most branches in one combination: one per slot. */
    public static final int MAX_BRANCHES = 3;

    private CombinationRules() {
    }

    public static List<ResourceLocation> key(List<ResourceLocation> branches) {
        return branches.stream().distinct().sorted().toList();
    }

    /** The entry written for exactly this set of branches, if there is one. */
    public static <T> Optional<T> find(Map<List<ResourceLocation>, T> written, List<ResourceLocation> held) {
        return held.isEmpty() ? Optional.empty() : Optional.ofNullable(written.get(key(held)));
    }

    /** Every non-empty set of at most {@link #MAX_BRANCHES} of {@code branches}, as keys: 41 for six branches. */
    public static List<List<ResourceLocation>> allKeys(List<ResourceLocation> branches) {
        List<ResourceLocation> sorted = key(branches);
        List<List<ResourceLocation>> result = new ArrayList<>();
        int n = sorted.size();
        for (int a = 0; a < n; a++) {
            result.add(List.of(sorted.get(a)));
            for (int b = a + 1; b < n; b++) {
                result.add(List.of(sorted.get(a), sorted.get(b)));
                for (int c = b + 1; c < n; c++) {
                    result.add(List.of(sorted.get(a), sorted.get(b), sorted.get(c)));
                }
            }
        }
        return result;
    }

    /** {@code id} is the file id. */
    public record Facts(ResourceLocation id, List<ResourceLocation> branches) {
    }

    /** One problem at most per combination: an unknown branch, a size outside 1 to {@value #MAX_BRANCHES}, a branch twice, or the same set written twice. */
    public static List<String> validate(Set<ResourceLocation> knownBranches, List<Facts> combinations) {
        List<String> problems = new ArrayList<>();
        Set<List<ResourceLocation>> seen = new HashSet<>();
        for (Facts combination : combinations) {
            String from = "combination " + combination.id();
            List<ResourceLocation> key = key(combination.branches());
            if (key.isEmpty() || key.size() > MAX_BRANCHES) {
                problems.add(from + ": it needs 1 to " + MAX_BRANCHES + " different branches");
            } else if (key.size() != combination.branches().size()) {
                problems.add(from + ": a branch is listed twice");
            } else if (!knownBranches.containsAll(key)) {
                problems.add(from + ": it names a branch that does not exist");
            } else if (!seen.add(key)) {
                problems.add(from + ": another combination already names the same branches");
            }
        }
        problems.sort(null);
        return problems;
    }
}
