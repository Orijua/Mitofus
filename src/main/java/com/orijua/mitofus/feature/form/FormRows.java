package com.orijua.mitofus.feature.form;

import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.feature.progression.ClassSlot;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Pure: the rows of the Forms tab, from the player's classes and forms. Owned forms and the ones still locked. */
public final class FormRows {
    /** Why a form is not owned yet. */
    public enum Lock { NONE, CLASS_LEVEL, GRADE_QUEST, PREVIOUS_FORM }

    public enum State { EQUIPPED, OWNED, LOCKED }

    /** One row: {@code level} is the form level (1 if never worn) and {@code xp} its uses; {@code lock} why it is locked, if it is. */
    public record Row(ResourceLocation id, FormDefinition form, State state, Lock lock, int level, int xp,
                  Optional<ResourceLocation> blockedBy) {
    }

    private FormRows() {
    }

    /**
     * The forms of every class the player has, in slot order and then by unlock level. A form two slots give appears once, and as owned if either slot unlocks it (the same rule as
     * {@link FormCatalogue}, which the server uses to check an equip).
     */
    public static List<Row> rows(List<Definitions.Entry<FormDefinition>> all, List<ClassSlot> slots, PlayerForms forms) {
        List<Definitions.Entry<FormDefinition>> sorted = all.stream()
                .sorted(Comparator.comparingInt((Definitions.Entry<FormDefinition> e) -> e.value().unlockLevel())
                        .thenComparing(Definitions.Entry::id))
                .toList();
        return FormCatalogue.visible(sorted, slots, forms).stream()
                .sorted(Comparator.comparingInt(FormCatalogue.Visible::slotIndex))
                .map(v -> row(v, forms))
                .toList();
    }

    private static Row row(FormCatalogue.Visible visible, PlayerForms forms) {
        int xp = forms.xpOf(visible.id());
        if (!visible.unlocked()) {
            Lock lock = visible.blockedBy().isPresent() ? Lock.PREVIOUS_FORM
                    : visible.classLevel() < visible.form().unlockLevel() ? Lock.CLASS_LEVEL : Lock.GRADE_QUEST;
            return new Row(visible.id(), visible.form(), State.LOCKED, lock, visible.level(), xp, visible.blockedBy());
        }
        State state = forms.equipped().equals(Optional.of(visible.id())) ? State.EQUIPPED : State.OWNED;
        return new Row(visible.id(), visible.form(), state, Lock.NONE, visible.level(), xp, Optional.empty());
    }
}
