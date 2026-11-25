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

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mitofus", path);
    }

    private static Definitions.Entry<FormDefinition> form(String name, ResourceLocation owner, int level, FormSource source) {
        return new Definitions.Entry<>(id(name), new FormDefinition(Component.empty(), Component.empty(), owner, level, source, id("fire"),
                List.of(id("a"), id("b"), id("c")), 0.20, 5, FormDefinition.Curve.DEFAULT));
    }

    private static ClassSlot slot(ResourceLocation classId, int level) {
        return new ClassSlot(0, classId, level, 0, ClassGrade.APPRENTICE, List.of());
    }

    private static final List<Definitions.Entry<FormDefinition>> ALL = List.of(
            form("sword_100", SWORD, 100, FormSource.GRADE_QUEST),
            form("sword_50", SWORD, 50, FormSource.LEVEL),
            form("mage_50", MAGE, 50, FormSource.LEVEL));

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
    void twoHeldBranchesListTheirFormsInSlotOrder() {
        var swordSlot = slot(SWORD, 70);
        var mageSlot = new ClassSlot(1, MAGE, 60, 0, ClassGrade.APPRENTICE, List.of());
        var rows = FormRows.rows(ALL, List.of(swordSlot, mageSlot), PlayerForms.EMPTY);
        assertEquals(List.of(id("sword_50"), id("sword_100"), id("mage_50")), rows.stream().map(FormRows.Row::id).toList());
        assertEquals(FormRows.State.OWNED, rows.get(0).state());
        assertEquals(FormRows.State.LOCKED, rows.get(1).state());
        assertEquals(FormRows.State.OWNED, rows.get(2).state(), "the mage branch's own form");
    }

    @Test
    void noClassesNoRows() {
        assertTrue(FormRows.rows(ALL, List.of(), PlayerForms.EMPTY).isEmpty());
    }

    @Test
    void theProgressToTheNextLevelGoesFromZeroToOne() {
        var curve = FormDefinition.Curve.DEFAULT;
        assertEquals(0f, FormRules.progress(curve, 0), 1e-6);
        assertEquals(0.5f, FormRules.progress(curve, curve.firstStep() / 2), 1e-2);
        assertEquals(1f, FormRules.progress(curve, Integer.MAX_VALUE), 1e-6);
    }

    @Test
    void aFormHeldBackByAMissingQuestFormSaysWhichOne() {
        var q100 = form("q_100", SWORD, 100, FormSource.GRADE_QUEST);
        var l150 = form("l_150", SWORD, 150, FormSource.LEVEL);
        var rows = FormRows.rows(List.of(q100, l150), List.of(slot(SWORD, 300)), PlayerForms.EMPTY);
        var held = rows.stream().filter(r -> r.id().equals(id("l_150"))).findFirst().orElseThrow();
        assertEquals(FormRows.State.LOCKED, held.state());
        assertEquals(FormRows.Lock.PREVIOUS_FORM, held.lock());
        assertEquals(java.util.Optional.of(id("q_100")), held.blockedBy());
        var quest = rows.stream().filter(r -> r.id().equals(id("q_100"))).findFirst().orElseThrow();
        assertEquals(FormRows.Lock.GRADE_QUEST, quest.lock());
    }
}
