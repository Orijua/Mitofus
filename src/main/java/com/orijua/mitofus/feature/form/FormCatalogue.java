package com.orijua.mitofus.feature.form;

import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.feature.progression.ClassSlot;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Pure: which forms a player owns, from the class slots and what they were granted. */
public final class FormCatalogue {
    /** A form the player can equip, with its current form level and whether it comes from a fusion's parent class. */
    public record Owned(ResourceLocation id, FormDefinition form, int level, boolean inherited) {
    }

    private FormCatalogue() {
    }

    /**
     * One form of the player's classes: {@code unlocked} says whether it is owned; a locked one still has its level.
     * {@code blockedBy} is the earlier grade-quest form of the same class that is still missing, when that is why it is locked.
     */
    public record Visible(ResourceLocation id, FormDefinition form, int level, boolean inherited, boolean unlocked, int classLevel,
                          int slotIndex, Optional<ResourceLocation> blockedBy) {
    }

    /**
     * Every form the player's classes can give, owned or not, once per form. A class owns the forms written for it; a
     * hybrid class also gets the {@code inheritable} forms of its two parents. When two slots give the same form, the
     * one that unlocks it wins, so the tab and the server never disagree. Forms are cumulative: while a grade-quest form
     * of a class is missing, every later form of that class stays locked, whatever the class level.
     */
    public static List<Visible> visible(List<Definitions.Entry<FormDefinition>> all, List<ClassSlot> slots, PlayerForms forms) {
        List<Visible> result = new ArrayList<>();
        java.util.Map<ResourceLocation, Integer> index = new java.util.HashMap<>();
        for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
            ClassSlot slot = slots.get(slotIndex);
            for (Definitions.Entry<FormDefinition> entry : all) {
                Visible found = visibleIn(entry.id(), entry.value(), all, slot, slotIndex, forms);
                if (found == null) {
                    continue;
                }
                Integer at = index.get(entry.id());
                if (at == null) {
                    index.put(entry.id(), result.size());
                    result.add(found);
                } else if (found.unlocked() && !result.get(at).unlocked()) {
                    result.set(at, found);
                }
            }
        }
        return result;
    }

    /** The earliest grade-quest form of {@code classId} the player has not been granted: it holds back every later form of the class. */
    static Optional<Definitions.Entry<FormDefinition>> missingQuestForm(List<Definitions.Entry<FormDefinition>> all, ResourceLocation classId,
                                                                        PlayerForms forms) {
        Definitions.Entry<FormDefinition> missing = null;
        for (Definitions.Entry<FormDefinition> entry : all) {
            FormDefinition form = entry.value();
            if (form.playerClass().equals(classId) && form.source() == FormSource.GRADE_QUEST && !forms.isGranted(entry.id())
                    && (missing == null || form.unlockLevel() < missing.value().unlockLevel())) {
                missing = entry;
            }
        }
        return Optional.ofNullable(missing);
    }

    /** {@code form} as the slot sees it, or null when the slot's class does not give it. */
    private static Visible visibleIn(ResourceLocation id, FormDefinition form, List<Definitions.Entry<FormDefinition>> all, ClassSlot slot,
                                     int slotIndex, PlayerForms forms) {
        boolean own = form.playerClass().equals(slot.classId());
        boolean inherited = !own && slot.isHybrid() && form.inheritable() && slot.origin().contains(form.playerClass());
        if (!own && !inherited) {
            return null;
        }
        boolean unlocked = FormRules.unlocked(form, slot.classLevel(), forms.isGranted(id), inherited);
        Optional<ResourceLocation> blockedBy = Optional.empty();
        if (unlocked) {
            blockedBy = missingQuestForm(all, form.playerClass(), forms)
                    .filter(missing -> missing.value().unlockLevel() < form.unlockLevel()).map(Definitions.Entry::id);
            unlocked = blockedBy.isEmpty();
        }
        return new Visible(id, form, FormRules.level(form.curve(), forms.xpOf(id)), inherited, unlocked, slot.classLevel(), slotIndex, blockedBy);
    }

    /** The forms unlocked by the player's classes. */
    public static List<Owned> owned(List<Definitions.Entry<FormDefinition>> all, List<ClassSlot> slots, PlayerForms forms) {
        return visible(all, slots, forms).stream().filter(Visible::unlocked)
                .map(v -> new Owned(v.id(), v.form(), v.level(), v.inherited())).toList();
    }

    /** Whether the player owns this one form, without building the whole catalogue. */
    public static Optional<Owned> ownedForm(ResourceLocation id, FormDefinition form, List<Definitions.Entry<FormDefinition>> all,
                                            List<ClassSlot> slots, PlayerForms forms) {
        for (int i = 0; i < slots.size(); i++) {
            Visible found = visibleIn(id, form, all, slots.get(i), i, forms);
            if (found != null && found.unlocked()) {
                return Optional.of(new Owned(id, form, found.level(), found.inherited()));
            }
        }
        return Optional.empty();
    }

    public static Optional<Owned> find(List<Owned> owned, ResourceLocation id) {
        return owned.stream().filter(o -> o.id().equals(id)).findFirst();
    }
}
