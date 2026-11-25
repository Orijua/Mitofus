package com.orijua.mitofus.feature.synergy;

import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Reads the loaded registries into what {@link SynergyValidator} needs: the Minecraft side of that check. */
public final class SynergyScanner {
    private SynergyScanner() {
    }

    private static <T> Set<ResourceLocation> ids(RegistryAccess access, ResourceKey<Registry<T>> key) {
        return access.registry(key).map(registry -> Set.copyOf(registry.keySet())).orElse(Set.of());
    }

    public static List<String> problems(RegistryAccess access) {
        List<SynergyValidator.SynergyFacts> synergies = new ArrayList<>();
        access.registry(ModDatapackRegistries.SYNERGY).ifPresent(registry -> registry.entrySet().forEach(e ->
                synergies.add(new SynergyValidator.SynergyFacts(e.getKey().location(), e.getValue().playerClass().orElse(null), e.getValue().job()))));
        return SynergyValidator.validate(ids(access, ModDatapackRegistries.PLAYER_CLASS), ids(access, ModDatapackRegistries.JOB), synergies);
    }
}
