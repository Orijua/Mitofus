package com.orijua.mitofus.feature.combination;

import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Reads the loaded registries into what {@link CombinationRules#validate} needs. */
public final class CombinationScanner {
    private CombinationScanner() {
    }

    public static List<String> problems(RegistryAccess access) {
        Set<ResourceLocation> classes = access.registry(ModDatapackRegistries.PLAYER_CLASS).map(registry -> Set.copyOf(registry.keySet())).orElse(Set.of());
        List<CombinationRules.Facts> facts = new ArrayList<>();
        access.registry(ModDatapackRegistries.COMBINATION).ifPresent(registry -> registry.entrySet().forEach(e ->
                facts.add(new CombinationRules.Facts(e.getKey().location(), e.getValue().branches()))));
        return CombinationRules.validate(classes, facts);
    }
}
