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
    public record Owned(ResourceLocation id, FormDefinition form, int level, boolean inherited, int xp) {
    }

    private FormCatalogue() {
    }

    /**
     * One form of the player's classes: {@code unlocked} says whether it is owned; a locked one still has its level.
     * {@code blockedBy} is the earlier grade-quest form of the same class that is still missing, when that is why it is locked.
     */
    public record Visible(ResourceLocation id, FormDefinition form, int level, boolean inherited, boolean unlocked, int classLevel,
                          int slotIndex, Optional<ResourceLocation> blockedBy, int xp) {
    }

    /**
     * Every form the player's classes can give, owned or not, once per form. A class owns the forms written for it; a
     * hybrid class also gets the {@code inheritable} forms of its two parents. When two slots give the same form, the
     * one that unlocks it wins, so the tab and the server never disagree. In a hybrid, forms of the same type upgrade each
     * other (see {@link #mergeByType}). Forms are cumulative: while a grade-quest form
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
        return mergeByType(result, slots, forms);
    }

    /**
     * In a hybrid class the forms of one type are a single line: the highest one unlocked is the form (the lowest, locked,
     * while none is), and it keeps the form uses of the whole line, so a new fire form carries on from the old one.
     */
    private static List<Visible> mergeByType(List<Visible> list, List<ClassSlot> slots, PlayerForms forms) {
        java.util.Map<String, Visible> head = new java.util.HashMap<>();
        java.util.Map<String, Integer> xp = new java.util.HashMap<>();
        for (Visible v : list) {
            if (!slots.get(v.slotIndex()).isHybrid()) {
                continue;
            }
            String key = v.slotIndex() + "|" + v.form().type();
            xp.merge(key, v.xp(), Math::max);
            Visible current = head.get(key);
            if (current == null || upgrades(v, current)) {
                head.put(key, v);
            }
        }
        List<Visible> result = new ArrayList<>();
        for (Visible v : list) {
            if (!slots.get(v.slotIndex()).isHybrid()) {
                result.add(v);
                continue;
            }
            String key = v.slotIndex() + "|" + v.form().type();
            if (head.get(key) == v) {
                int total = xp.get(key);
                result.add(new Visible(v.id(), v.form(), FormRules.level(v.form().curve(), total), v.inherited(), v.unlocked(),
                        v.classLevel(), v.slotIndex(), v.blockedBy(), total));
            }
        }
        return result;
    }

    /** Whether {@code candidate} should be the form of its type line instead of {@code current}. */
    private static boolean upgrades(Visible candidate, Visible current) {
        if (candidate.unlocked() != current.unlocked()) {
            return candidate.unlocked();
        }
        return candidate.unlocked() ? candidate.form().unlockLevel() > current.form().unlockLevel()
                : candidate.form().unlockLevel() < current.form().unlockLevel();
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
        int xp = forms.xpOf(id);
        return new Visible(id, form, FormRules.level(form.curve(), xp), inherited, unlocked, slot.classLevel(), slotIndex, blockedBy, xp);
    }

    /** The forms unlocked by the player's classes. */
    public static List<Owned> owned(List<Definitions.Entry<FormDefinition>> all, List<ClassSlot> slots, PlayerForms forms) {
        return visible(all, slots, forms).stream().filter(Visible::unlocked)
                .map(v -> new Owned(v.id(), v.form(), v.level(), v.inherited(), v.xp())).toList();
    }

    /** Whether the player owns this one form, without building the whole catalogue. */
    public static Optional<Owned> ownedForm(ResourceLocation id, FormDefinition form, List<Definitions.Entry<FormDefinition>> all,
                                            List<ClassSlot> slots, PlayerForms forms) {
        for (int i = 0; i < slots.size(); i++) {
            Visible found = visibleIn(id, form, all, slots.get(i), i, forms);
            if (found == null) {
                continue;
            }
            if (slots.get(i).isHybrid()) {
                final int slotIndex = i;
                Optional<Owned> head = visible(all, slots, forms).stream()
                        .filter(v -> v.slotIndex() == slotIndex && v.unlocked() && v.form().type().equals(form.type()))
                        .map(v -> new Owned(v.id(), v.form(), v.level(), v.inherited(), v.xp())).findFirst();
                if (head.isPresent()) {
                    return head;
                }
            } else if (found.unlocked()) {
                return Optional.of(new Owned(id, form, found.level(), found.inherited(), found.xp()));
            }
        }
        return Optional.empty();
    }

    public static Optional<Owned> find(List<Owned> owned, ResourceLocation id) {
        return owned.stream().filter(o -> o.id().equals(id)).findFirst();
    }
}
