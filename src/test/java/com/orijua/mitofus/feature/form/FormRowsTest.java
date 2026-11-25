package com.orijua.mitofus.feature.form;

import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.feature.progression.ClassGrade;
import com.orijua.mitofus.feature.progression.ClassSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormRowsTest {
    private static final ResourceLocation SWORD = id("swordsman");
    private static final ResourceLocation MAGE = id("mage");
    private static final ResourceLocation HYBRID = id("spellsword");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mitofus", path);
    }

    private static Definitions.Entry<FormDefinition> form(String name, ResourceLocation owner, int level, FormSource source, boolean inheritable) {
        return new Definitions.Entry<>(id(name), new FormDefinition(Component.empty(), Component.empty(), owner, level, source, id("fire"),
                List.of(id("a"), id("b"), id("c")), 0.20, inheritable, 5, FormDefinition.Curve.DEFAULT));
    }

    private static ClassSlot slot(ResourceLocation classId, int level, ResourceLocation... origin) {
        return new ClassSlot(0, classId, level, 0, ClassGrade.APPRENTICE, List.of(), List.of(origin), 0);
    }

    private static final List<Definitions.Entry<FormDefinition>> ALL = List.of(
            form("sword_100", SWORD, 100, FormSource.GRADE_QUEST, true),
            form("sword_50", SWORD, 50, FormSource.LEVEL, true),
            form("mage_50", MAGE, 50, FormSource.LEVEL, false));

    @Test
    void showsEveryFormOfTheClassByUnlockLevelWithItsState() {
        var rows = FormRows.rows(ALL, List.of(slot(SWORD, 60)), PlayerForms.EMPTY);
        assertEquals(List.of(id("sword_50"), id("sword_100")), rows.stream().map(FormRows.Row::id).toList());
        assertEquals(FormRows.State.OWNED, rows.get(0).state());
        assertEquals(FormRows.State.LOCKED, rows.get(1).state());
        assertEquals(FormRows.Lock.CLASS_LEVEL, rows.get(1).lock());
    }

    @Test
    void aQuestFormAtItsLevelWithoutTheQuestSaysItNeedsTheQuest() {
        var rows = FormRows.rows(ALL, List.of(slot(SWORD, 120)), PlayerForms.EMPTY);
        assertEquals(FormRows.Lock.GRADE_QUEST, rows.get(1).lock());
        rows = FormRows.rows(ALL, List.of(slot(SWORD, 120)), PlayerForms.EMPTY.withGranted(id("sword_100")));
        assertEquals(FormRows.State.OWNED, rows.get(1).state());
    }

    @Test
    void theWornFormIsMarkedAndKeepsItsLevel() {
        var forms = PlayerForms.EMPTY.withXp(id("sword_50"), FormRules.usesToReach(FormDefinition.Curve.DEFAULT, 7)).withEquipped(Optional.of(id("sword_50")));
        var row = FormRows.rows(ALL, List.of(slot(SWORD, 60)), forms).get(0);
        assertEquals(FormRows.State.EQUIPPED, row.state());
        assertEquals(7, row.level());
    }

    @Test
    void aFusionListsItsOwnFormsThenTheInheritedOnesAndNeverTwice() {
        var rows = FormRows.rows(ALL, List.of(slot(HYBRID, 70, SWORD, MAGE), slot(MAGE, 60)), PlayerForms.EMPTY);
        assertEquals(List.of(id("sword_50"), id("sword_100"), id("mage_50")), rows.stream().map(FormRows.Row::id).toList());
        assertTrue(rows.get(0).inherited() && rows.get(1).inherited());
        assertEquals(FormRows.State.OWNED, rows.get(0).state());
        assertEquals(FormRows.State.LOCKED, rows.get(1).state(), "the inherited level 100 form waits for the fused class level");
        assertTrue(rows.get(2).state() == FormRows.State.OWNED && !rows.get(2).inherited(), "the mage's own form from its slot");
    }

    @Test
    void noClassesNoRows() {
        assertTrue(FormRows.rows(ALL, List.of(), PlayerForms.EMPTY).isEmpty());
    }
}
