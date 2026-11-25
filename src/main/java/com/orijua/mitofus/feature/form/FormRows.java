package com.orijua.mitofus.feature.form;

import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.feature.progression.ClassSlot;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Pure: the rows of the Forms tab, from the player's classes and forms. Owned forms and the ones still locked. */
public final class FormRows {
    /** Why a form is not owned yet. */
    public enum Lock { NONE, CLASS_LEVEL, GRADE_QUEST }

    public enum State { EQUIPPED, OWNED, LOCKED }

    /** One row: {@code level} is the form level (1 if never worn) and {@code xp} its uses; {@code lock} why it is locked, if it is. */
    public record Row(ResourceLocation id, FormDefinition form, State state, Lock lock, int level, int xp, boolean inherited) {
    }

    private FormRows() {
    }

    /**
     * The forms of every class the player has, in slot order and then by unlock level; the forms a hybrid class inherits
     * come after its own. A form owned by two slots appears once.
     */
    public static List<Row> rows(List<Definitions.Entry<FormDefinition>> all, List<ClassSlot> slots, PlayerForms forms) {
        List<Row> result = new ArrayList<>();
        Set<ResourceLocation> seen = new HashSet<>();
        for (ClassSlot slot : slots) {
            List<Definitions.Entry<FormDefinition>> sorted = all.stream()
                    .sorted(java.util.Comparator.comparingInt((Definitions.Entry<FormDefinition> e) -> e.value().unlockLevel())
                            .thenComparing(Definitions.Entry::id))
                    .toList();
            for (boolean inheritedPass : new boolean[] {false, true}) {
                for (Definitions.Entry<FormDefinition> entry : sorted) {
                    FormDefinition form = entry.value();
                    boolean own = form.playerClass().equals(slot.classId());
                    boolean inherited = !own && slot.isHybrid() && form.inheritable() && slot.origin().contains(form.playerClass());
                    if (inherited != inheritedPass || !(own || inherited) || !seen.add(entry.id())) {
                        continue;
                    }
                    result.add(row(entry.id(), form, slot.classLevel(), forms, inherited));
                }
            }
        }
        return result;
    }

    private static Row row(ResourceLocation id, FormDefinition form, int classLevel, PlayerForms forms, boolean inherited) {
        int xp = forms.xpOf(id);
        int level = FormRules.level(form.curve(), xp);
        boolean unlocked = FormRules.unlocked(form, classLevel, forms.isGranted(id), inherited);
        if (!unlocked) {
            Lock lock = classLevel < form.unlockLevel() ? Lock.CLASS_LEVEL : Lock.GRADE_QUEST;
            return new Row(id, form, State.LOCKED, lock, level, xp, inherited);
        }
        State state = forms.equipped().equals(Optional.of(id)) ? State.EQUIPPED : State.OWNED;
        return new Row(id, form, state, Lock.NONE, level, xp, inherited);
    }
}
