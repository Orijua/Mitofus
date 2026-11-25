package com.orijua.mitofus.feature.form;

import com.orijua.mitofus.core.registry.Definitions;
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
                                        Set<ResourceLocation> classes, Set<ResourceLocation> skills,
                                        Set<ResourceLocation> passives, Map<ResourceLocation, ResourceLocation> questRewards) {
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
            for (int i = 0; i < form.skills().size(); i++) {
                ResourceLocation ref = form.skills().get(i);
                boolean passive = i == FormDefinition.PASSIVE_INDEX;
                if (!(passive ? passives : skills).contains(ref)) {
                    problems.add("form " + id + ": unknown " + (passive ? "passive " : "skill ") + ref);
                }
            }
            if (!taken.add(form.playerClass() + "@" + form.unlockLevel())) {
                problems.add("form " + id + ": class " + form.playerClass() + " already has a form at level " + form.unlockLevel());
            }
        });
        questRewards.forEach((quest, form) -> {
            if (!forms.containsKey(form)) {
                problems.add("quest " + quest + ": form_reward unknown form " + form);
            }
        });
        return problems;
    }

    public static List<String> problems(RegistryAccess access) {
        return validate(Definitions.entries(access, ModDatapackRegistries.FORM), Definitions.entries(access, ModDatapackRegistries.ELEMENT_TYPE),
                Definitions.entries(access, ModDatapackRegistries.PLAYER_CLASS).keySet(), Definitions.entries(access, ModDatapackRegistries.SKILL).keySet(),
                Definitions.entries(access, ModDatapackRegistries.PASSIVE).keySet(), questRewards(access));
    }

    private static Map<ResourceLocation, ResourceLocation> questRewards(RegistryAccess access) {
        Map<ResourceLocation, ResourceLocation> result = new HashMap<>();
        Definitions.entries(access, ModDatapackRegistries.QUEST).forEach((id, quest) -> quest.formReward().ifPresent(form -> result.put(id, form)));
        return result;
    }
}
