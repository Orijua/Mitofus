package com.orijua.mitofus.feature.fusion;

import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.progression.PlayerClassDefinition;
import com.orijua.mitofus.feature.talent.TalentDefinition;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads the loaded registries into what {@link FusionValidator} needs: the Minecraft side of that check. */
public final class FusionScanner {
    private FusionScanner() {
    }

    private static <T> Map<ResourceLocation, T> entries(RegistryAccess access, ResourceKey<Registry<T>> key) {
        Map<ResourceLocation, T> result = new HashMap<>();
        access.registry(key).ifPresent(registry -> registry.entrySet().forEach(e -> result.put(e.getKey().location(), e.getValue())));
        return result;
    }

    /** The result classes of every fusion: only a fusion can give them, so they are not "unobtainable". */
    public static Set<ResourceLocation> resultClasses(RegistryAccess access) {
        Set<ResourceLocation> results = new HashSet<>();
        entries(access, ModDatapackRegistries.FUSION).values().forEach(f -> results.add(f.resultClass()));
        return results;
    }

    public static List<String> problems(RegistryAccess access) {
        Map<ResourceLocation, Integer> trunk = new HashMap<>();
        entries(access, ModDatapackRegistries.TALENT).values().forEach((TalentDefinition node) -> {
            if (!node.isRoot()) {
                trunk.merge(node.playerClass(), 1, Integer::sum);
            }
        });
        Map<ResourceLocation, FusionValidator.ClassFacts> classes = new HashMap<>();
        entries(access, ModDatapackRegistries.PLAYER_CLASS).forEach((id, definition) -> {
            Set<ResourceLocation> passives = new HashSet<>(definition.passives());
            classes.put(id, new FusionValidator.ClassFacts(definition.legendary(), Map.copyOf(definition.bonusStats()),
                    Set.copyOf(definition.skills()), passives, trunk.getOrDefault(id, 0)));
        });
        List<FusionValidator.FusionFacts> fusions = new ArrayList<>();
        entries(access, ModDatapackRegistries.FUSION).forEach((id, fusion) -> fusions.add(new FusionValidator.FusionFacts(id, fusion.parents(), fusion.resultClass())));
        List<FusionValidator.SynergyFacts> synergies = new ArrayList<>();
        entries(access, ModDatapackRegistries.SYNERGY).forEach((id, synergy) ->
                synergies.add(new FusionValidator.SynergyFacts(id, synergy.playerClass().orElse(null), synergy.job())));
        Set<ResourceLocation> jobs = Set.copyOf(entries(access, ModDatapackRegistries.JOB).keySet());
        return FusionValidator.validate(fusions, classes, jobs, synergies);
    }
}
