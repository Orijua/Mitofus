package com.orijua.mitofus.core.registry;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.CommonHooks;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Read access to the JSON-defined content, the same way on server and client. */
public final class Definitions {
    public record Entry<T>(ResourceLocation id, T value) {
    }

    private Definitions() {
    }

    public static <T> Optional<T> get(Level level, ResourceKey<Registry<T>> registry, ResourceLocation id) {
        return level.registryAccess().registry(registry).flatMap(r -> r.getOptional(id));
    }

    public static <T> Optional<T> get(HolderLookup.Provider registries, ResourceKey<Registry<T>> registry, ResourceLocation id) {
        return registries.lookup(registry)
                .flatMap(lookup -> lookup.get(ResourceKey.create(registry, id)))
                .map(holder -> holder.value());
    }

    /** For code with no level at hand (e.g. item attribute events): the running server's or the client's registries. */
    public static <T> Optional<T> get(ResourceKey<Registry<T>> registry, ResourceLocation id) {
        return Optional.ofNullable(CommonHooks.resolveLookup(registry))
                .flatMap(lookup -> lookup.get(ResourceKey.create(registry, id)))
                .map(holder -> holder.value());
    }

    /** Every entry of a registry by id, for the content checks that work on a whole registry. */
    public static <T> java.util.Map<ResourceLocation, T> entries(net.minecraft.core.RegistryAccess access, ResourceKey<Registry<T>> registry) {
        java.util.Map<ResourceLocation, T> result = new java.util.HashMap<>();
        access.registry(registry).ifPresent(r -> r.entrySet().forEach(e -> result.put(e.getKey().location(), e.getValue())));
        return result;
    }

    /** All entries sorted by id, so lists in the UI have a stable order. */
    public static <T> List<Entry<T>> all(Level level, ResourceKey<Registry<T>> registry) {
        return level.registryAccess().registry(registry)
                .map(r -> r.entrySet().stream()
                        .map(e -> new Entry<>(e.getKey().location(), e.getValue()))
                        .sorted(Comparator.comparing(Entry::id))
                        .toList())
                .orElse(List.of());
    }
}
