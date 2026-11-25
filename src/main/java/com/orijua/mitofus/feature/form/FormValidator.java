package com.orijua.mitofus.feature.form;

import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks that forms and elemental types point at things that exist, and that a class has one form per unlock level. */
public final class FormValidator {
    private FormValidator() {
    }

    public static List<String> validate(Map<ResourceLocation, FormDefinition> forms, Map<ResourceLocation, ElementTypeDefinition> types,
                                        Set<ResourceLocation> classes, Set<ResourceLocation> skills) {
        List<String> problems = new ArrayList<>();
        types.forEach((id, type) -> type.strongAgainst().stream().filter(t -> !types.containsKey(t))
                .forEach(t -> problems.add("element type " + id + ": strong_against unknown type " + t)));
        Set<String> taken = new HashSet<>();
        forms.forEach((id, form) -> {
            if (!classes.contains(form.playerClass())) {
                problems.add("form " + id + ": unknown class " + form.playerClass());
            }
            if (!types.containsKey(form.type())) {
                problems.add("form " + id + ": unknown type " + form.type());
            }
            form.skills().stream().filter(s -> !skills.contains(s)).forEach(s -> problems.add("form " + id + ": unknown skill " + s));
            if (!taken.add(form.playerClass() + "@" + form.unlockLevel())) {
                problems.add("form " + id + ": class " + form.playerClass() + " already has a form at level " + form.unlockLevel());
            }
        });
        return problems;
    }

    public static List<String> problems(RegistryAccess access) {
        return validate(entries(access, ModDatapackRegistries.FORM), entries(access, ModDatapackRegistries.ELEMENT_TYPE),
                entries(access, ModDatapackRegistries.PLAYER_CLASS).keySet(), entries(access, ModDatapackRegistries.SKILL).keySet());
    }

    private static <T> Map<ResourceLocation, T> entries(RegistryAccess access,
                                                        net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<T>> key) {
        Map<ResourceLocation, T> result = new HashMap<>();
        access.registry(key).ifPresent(registry -> registry.entrySet().forEach(e -> result.put(e.getKey().location(), e.getValue())));
        return result;
    }
}
