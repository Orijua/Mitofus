package com.orijua.mitofus.feature.form;

import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.feature.progression.ClassSlot;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Pure: which forms a player owns, from the class slots and what they were granted. */
public final class FormCatalogue {
    /** A form the player can equip, with its current form level and whether it comes from a fusion's parent class. */
    public record Owned(ResourceLocation id, FormDefinition form, int level, boolean inherited) {
    }

    private FormCatalogue() {
    }

    /**
     * The forms unlocked by the player's classes. A class owns the forms written for it; a hybrid class also gets the
     * {@code inheritable} forms of its two parents, but only once it reaches the level that unlocks them.
     */
    public static List<Owned> owned(List<Definitions.Entry<FormDefinition>> all, List<ClassSlot> slots, PlayerForms forms) {
        List<Owned> result = new ArrayList<>();
        Set<ResourceLocation> seen = new HashSet<>();
        for (ClassSlot slot : slots) {
            for (Definitions.Entry<FormDefinition> entry : all) {
                FormDefinition form = entry.value();
                boolean own = form.playerClass().equals(slot.classId());
                boolean inherited = !own && slot.isHybrid() && form.inheritable() && slot.origin().contains(form.playerClass());
                if ((own || inherited) && FormRules.unlocked(form, slot.classLevel(), forms.isGranted(entry.id()), inherited)
                        && seen.add(entry.id())) {
                    result.add(new Owned(entry.id(), form, FormRules.level(form.curve(), forms.xpOf(entry.id())), inherited));
                }
            }
        }
        return result;
    }

    public static Optional<Owned> find(List<Owned> owned, ResourceLocation id) {
        return owned.stream().filter(o -> o.id().equals(id)).findFirst();
    }
}
