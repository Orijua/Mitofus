package com.orijua.mitofus.feature.form;

import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.feature.progression.ClassGrade;
import com.orijua.mitofus.feature.progression.ClassSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormRulesTest {
    private static final ResourceLocation SWORD = id("swordsman");
    private static final ResourceLocation MAGE = id("mage");
    private static final ResourceLocation FIRE = id("fire");
    private static final ResourceLocation WATER = id("water");
    private static final ResourceLocation EARTH = id("earth");
    private static final ResourceLocation LIGHT = id("light");
    private static final ResourceLocation SHADOW = id("shadow");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mitofus", path);
    }

    private static ElementTypeDefinition type(ResourceLocation... strong) {
        return new ElementTypeDefinition(Component.empty(), 0, List.of(strong), 0.15, 0.10);
    }

    private static FormDefinition form(ResourceLocation owner, int level, FormSource source) {
        return new FormDefinition(Component.empty(), Component.empty(), owner, level, source, FIRE,
                List.of(id("a"), id("b"), id("c")), 0.20, 5, FormDefinition.Curve.DEFAULT);
    }

    private static ClassSlot slot(ResourceLocation classId, int level) {
        return new ClassSlot(0, classId, level, 0, ClassGrade.APPRENTICE, List.of());
    }

    @Test
    void anAttackHitsHarderAgainstWhatItIsStrongAgainstAndSofterTheOtherWay() {
        var water = type(FIRE);
        var fire = type(EARTH);
        assertEquals(1.15, ElementRules.multiplier(Optional.of(WATER), Optional.of(water), Optional.of(FIRE), Optional.of(fire)), 1e-9);
        assertEquals(0.90, ElementRules.multiplier(Optional.of(FIRE), Optional.of(fire), Optional.of(WATER), Optional.of(water)), 1e-9);
        assertEquals(1.0, ElementRules.multiplier(Optional.of(WATER), Optional.of(water), Optional.of(EARTH), Optional.of(type())), 1e-9);
    }

    @Test
    void lightAndShadowHitEachOtherHarder() {
        var light = type(SHADOW);
        var shadow = type(LIGHT);
        assertEquals(1.15, ElementRules.multiplier(Optional.of(LIGHT), Optional.of(light), Optional.of(SHADOW), Optional.of(shadow)), 1e-9);
        assertEquals(1.15, ElementRules.multiplier(Optional.of(SHADOW), Optional.of(shadow), Optional.of(LIGHT), Optional.of(light)), 1e-9);
    }

    @Test
    void noTypeIsNeutral() {
        assertEquals(1.0, ElementRules.multiplier(Optional.empty(), Optional.empty(), Optional.of(FIRE), Optional.of(type())), 1e-9);
        assertEquals(1.0, ElementRules.multiplier(Optional.of(FIRE), Optional.of(type(EARTH)), Optional.empty(), Optional.empty()), 1e-9);
    }

    @Test
    void formLevelFollowsUsesAndStopsAtTheMaximum() {
        var curve = FormDefinition.Curve.DEFAULT;
        assertEquals(1, FormRules.level(curve, 0));
        assertEquals(1, FormRules.level(curve, 99));
        assertEquals(2, FormRules.level(curve, 100));
        assertEquals(FormRules.MAX_LEVEL, FormRules.level(curve, Integer.MAX_VALUE));
        for (int level = 1; level <= FormRules.MAX_LEVEL; level++) {
            assertEquals(level, FormRules.level(curve, FormRules.usesToReach(curve, level)), "reaching level " + level);
        }
    }

    @Test
    void lowFormsLevelFasterThanHighOnes() {
        var curve = FormDefinition.Curve.DEFAULT;
        assertEquals(2.0, FormRules.useValue(curve, 1));
        assertEquals(2.0, FormRules.useValue(curve, 24));
        assertEquals(1.0, FormRules.useValue(curve, 25));
    }

    @Test
    void typeBonusGrowsInAStraightLineUpToTheMaximum() {
        var form = form(SWORD, 50, FormSource.LEVEL);
        assertEquals(0.0, FormRules.typeBonus(form, 0), 1e-9);
        assertEquals(0.10, FormRules.typeBonus(form, 50), 1e-9);
        assertEquals(0.20, FormRules.typeBonus(form, 100), 1e-9);
        assertEquals(0.20, FormRules.typeBonus(form, 500), 1e-9, "never above the maximum");
    }

    @Test
    void theThreeSkillsComeAtFormLevels1_30And60() {
        var form = form(SWORD, 50, FormSource.LEVEL);
        assertEquals(List.of(id("a")), FormRules.unlockedSkills(form, 1));
        assertEquals(List.of(id("a")), FormRules.unlockedSkills(form, 29));
        assertEquals(List.of(id("a"), id("b")), FormRules.unlockedSkills(form, 30));
        assertEquals(List.of(id("a"), id("b"), id("c")), FormRules.unlockedSkills(form, 60));
    }

    @Test
    void aLevelFormNeedsOnlyTheClassLevelAndAQuestFormAlsoTheQuest() {
        var byLevel = form(SWORD, 50, FormSource.LEVEL);
        var byQuest = form(SWORD, 100, FormSource.GRADE_QUEST);
        assertFalse(FormRules.unlocked(byLevel, 49, false));
        assertTrue(FormRules.unlocked(byLevel, 50, false));
        assertFalse(FormRules.unlocked(byQuest, 100, false), "level alone is not enough");
        assertTrue(FormRules.unlocked(byQuest, 100, true));
        assertFalse(FormRules.unlocked(byQuest, 99, true), "the quest alone is not enough");
    }

    @Test
    void catalogueOwnsOnlyUnlockedFormsOfThePlayersClasses() {
        var f50 = new Definitions.Entry<>(id("sword_50"), form(SWORD, 50, FormSource.LEVEL));
        var f100 = new Definitions.Entry<>(id("sword_100"), form(SWORD, 100, FormSource.GRADE_QUEST));
        var mage50 = new Definitions.Entry<>(id("mage_50"), form(MAGE, 50, FormSource.LEVEL));
        var all = List.of(f50, f100, mage50);
        var owned = FormCatalogue.owned(all, List.of(slot(SWORD, 120)), PlayerForms.EMPTY);
        assertEquals(List.of(id("sword_50")), owned.stream().map(FormCatalogue.Owned::id).toList());
        owned = FormCatalogue.owned(all, List.of(slot(SWORD, 120)), PlayerForms.EMPTY.withGranted(id("sword_100")));
        assertEquals(2, owned.size());
    }

    @Test
    void everyHeldBranchGivesItsOwnFormsAndABranchNotHeldGivesNone() {
        var sword50 = new Definitions.Entry<>(id("sword_50"), form(SWORD, 50, FormSource.LEVEL));
        var mage50 = new Definitions.Entry<>(id("mage_50"), form(MAGE, 50, FormSource.LEVEL));
        var all = List.of(sword50, mage50);
        var both = FormCatalogue.owned(all, List.of(slot(SWORD, 60), slot(MAGE, 60)), PlayerForms.EMPTY);
        assertEquals(List.of(id("sword_50"), id("mage_50")), both.stream().map(FormCatalogue.Owned::id).toList());
        var onlySword = FormCatalogue.owned(all, List.of(slot(SWORD, 60)), PlayerForms.EMPTY);
        assertEquals(List.of(id("sword_50")), onlySword.stream().map(FormCatalogue.Owned::id).toList(), "a dormant branch is not in a slot: no forms");
    }

    @Test
    void playerFormsKeepOneEquippedFormAndSurviveTheCodec() {
        var forms = PlayerForms.EMPTY.withXp(id("sword_50"), 250).withGranted(id("sword_100")).withGranted(id("sword_100"))
                .withEquipped(Optional.of(id("sword_50")));
        assertEquals(List.of(id("sword_100")), forms.granted(), "granting twice keeps one entry");
        var decoded = PlayerForms.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                PlayerForms.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, forms).getOrThrow()).getOrThrow();
        assertEquals(forms, decoded);
        assertEquals(PlayerForms.EMPTY, PlayerForms.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, new com.google.gson.JsonObject()).getOrThrow());
    }

    @Test
    void validatorFindsBrokenReferencesAndRepeatedLevels() {
        var types = Map.of(FIRE, type(WATER, id("nope")), WATER, type());
        var good = form(SWORD, 50, FormSource.LEVEL);
        var repeated = form(SWORD, 50, FormSource.LEVEL);
        var badClass = form(MAGE, 50, FormSource.LEVEL);
        var problems = FormValidator.validate(Map.of(id("good"), good, id("repeated"), repeated, id("bad_class"), badClass), types,
                java.util.Set.of(SWORD), java.util.Set.of(id("a"), id("c")), java.util.Set.of(id("b")), Map.of(id("quest"), id("missing_form")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("unknown type mitofus:nope")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("unknown class mitofus:mage")), problems.toString());
        assertEquals(1, problems.stream().filter(p -> p.contains("already has a form at level 50")).count(), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("quest mitofus:quest: form_reward unknown form")), problems.toString());
        assertEquals(0, problems.stream().filter(p -> p.contains("unknown skill") || p.contains("unknown passive")).count(), problems.toString());
    }

    @Test
    void theUltimateAndThePassiveAreSplitFromTheSkillsAndKeepTheirLevels() {
        var form = form(SWORD, 50, FormSource.LEVEL);
        assertEquals(List.of(id("a")), FormRules.unlockedActiveSkills(form, 29));
        assertEquals(List.of(id("a")), FormRules.unlockedActiveSkills(form, 59));
        assertEquals(List.of(id("a"), id("c")), FormRules.unlockedActiveSkills(form, 60));
        assertEquals(Optional.empty(), FormRules.unlockedPassive(form, 29));
        assertEquals(Optional.of(id("b")), FormRules.unlockedPassive(form, 30));
    }

    @Test
    void theBarSlotOfAFormSkillIsRemembered() {
        var forms = PlayerForms.EMPTY.withBar(id("a"), Optional.of(2));
        assertEquals(Optional.of(2), forms.barSlot(id("a")));
        assertEquals(Optional.empty(), forms.withBar(id("a"), Optional.empty()).barSlot(id("a")));
    }

    @Test
    void aSteepCurveNeverOverflowsTheUseCounters() {
        var steep = new FormDefinition.Curve(1_000_000, 3.0, 25, 2.0);
        assertEquals(FormRules.MAX_STEP, FormRules.usesToAdvance(steep, 99));
        assertTrue(FormRules.usesToReach(steep, 100) > 0);
        assertEquals(1, FormRules.level(steep, 0));
        assertTrue(FormRules.level(steep, Integer.MAX_VALUE) <= FormRules.MAX_LEVEL);
        var common = new FormDefinition.Curve(100, 1.25, 25, 2.0);
        assertTrue(FormRules.usesToAdvance(common, 99) > 0);
        assertTrue(FormRules.level(common, 5_000) < FormRules.MAX_LEVEL);
    }

    @Test
    void aFormBelowTheClassLevelIsListedLockedAndNotOwned() {
        var ember = new Definitions.Entry<>(id("ember"), form(SWORD, 50, FormSource.LEVEL));
        var all = List.of(ember);
        var slots = List.of(slot(SWORD, 40));
        var locked = FormCatalogue.visible(all, slots, PlayerForms.EMPTY);
        assertFalse(locked.get(0).unlocked());
        assertTrue(FormCatalogue.owned(all, slots, PlayerForms.EMPTY).isEmpty());
        assertTrue(FormCatalogue.ownedForm(id("ember"), ember.value(), all, List.of(slot(SWORD, 50)), PlayerForms.EMPTY).isPresent());
    }

    @Test
    void aMissingGradeQuestFormHoldsBackEveryLaterFormOfTheClass() {
        var f50 = new Definitions.Entry<>(id("sword_50"), form(SWORD, 50, FormSource.LEVEL));
        var f100 = new Definitions.Entry<>(id("sword_100"), form(SWORD, 100, FormSource.GRADE_QUEST));
        var f150 = new Definitions.Entry<>(id("sword_150"), form(SWORD, 150, FormSource.LEVEL));
        var f200 = new Definitions.Entry<>(id("sword_200"), form(SWORD, 200, FormSource.GRADE_QUEST));
        var f250 = new Definitions.Entry<>(id("sword_250"), form(SWORD, 250, FormSource.LEVEL));
        var all = List.of(f50, f100, f150, f200, f250);
        var slots = List.of(slot(SWORD, 300));
        // level 300 but no quest: only the level 50 form, none of the later ones (not even the plain level forms)
        assertEquals(List.of(id("sword_50")), FormCatalogue.owned(all, slots, PlayerForms.EMPTY).stream().map(FormCatalogue.Owned::id).toList());
        var blocked = FormCatalogue.visible(all, slots, PlayerForms.EMPTY).stream().filter(v -> v.id().equals(id("sword_150"))).findFirst().orElseThrow();
        assertEquals(java.util.Optional.of(id("sword_100")), blocked.blockedBy());
        // the level 200 quest alone does not open the level 150 and 250 forms either
        var onlySecond = PlayerForms.EMPTY.withGranted(id("sword_200"));
        assertEquals(List.of(id("sword_50")), FormCatalogue.owned(all, slots, onlySecond).stream().map(FormCatalogue.Owned::id).toList());
        // with the first quest: 100 and 150 open, 200 and 250 wait for their own quest
        var first = PlayerForms.EMPTY.withGranted(id("sword_100"));
        assertEquals(List.of(id("sword_50"), id("sword_100"), id("sword_150")),
                FormCatalogue.owned(all, slots, first).stream().map(FormCatalogue.Owned::id).toList());
        // with both quests, everything
        assertEquals(5, FormCatalogue.owned(all, slots, first.withGranted(id("sword_200"))).size());
    }
}
