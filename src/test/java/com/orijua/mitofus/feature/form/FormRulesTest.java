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
    private static final ResourceLocation SPELLSWORD = id("spellsword");
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

    private static FormDefinition form(ResourceLocation owner, int level, FormSource source, boolean inheritable) {
        return new FormDefinition(Component.empty(), Component.empty(), owner, level, source, FIRE,
                List.of(id("a"), id("b"), id("c")), 0.20, inheritable, FormDefinition.Curve.DEFAULT);
    }

    private static ClassSlot slot(ResourceLocation classId, int level, ResourceLocation... origin) {
        return new ClassSlot(0, classId, level, 0, ClassGrade.APPRENTICE, List.of(), List.of(origin), 0);
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
        var form = form(SWORD, 50, FormSource.LEVEL, false);
        assertEquals(0.0, FormRules.typeBonus(form, 0), 1e-9);
        assertEquals(0.10, FormRules.typeBonus(form, 50), 1e-9);
        assertEquals(0.20, FormRules.typeBonus(form, 100), 1e-9);
        assertEquals(0.20, FormRules.typeBonus(form, 500), 1e-9, "never above the maximum");
    }

    @Test
    void theThreeSkillsComeAtFormLevels1_30And60() {
        var form = form(SWORD, 50, FormSource.LEVEL, false);
        assertEquals(List.of(id("a")), FormRules.unlockedSkills(form, 1));
        assertEquals(List.of(id("a")), FormRules.unlockedSkills(form, 29));
        assertEquals(List.of(id("a"), id("b")), FormRules.unlockedSkills(form, 30));
        assertEquals(List.of(id("a"), id("b"), id("c")), FormRules.unlockedSkills(form, 60));
    }

    @Test
    void aLevelFormNeedsOnlyTheClassLevelAndAQuestFormAlsoTheQuest() {
        var byLevel = form(SWORD, 50, FormSource.LEVEL, false);
        var byQuest = form(SWORD, 100, FormSource.GRADE_QUEST, false);
        assertFalse(FormRules.unlocked(byLevel, 49, false, false));
        assertTrue(FormRules.unlocked(byLevel, 50, false, false));
        assertFalse(FormRules.unlocked(byQuest, 100, false, false), "level alone is not enough");
        assertTrue(FormRules.unlocked(byQuest, 100, true, false));
        assertFalse(FormRules.unlocked(byQuest, 99, true, false), "the quest alone is not enough");
    }

    @Test
    void catalogueOwnsOnlyUnlockedFormsOfThePlayersClasses() {
        var f50 = new Definitions.Entry<>(id("sword_50"), form(SWORD, 50, FormSource.LEVEL, true));
        var f100 = new Definitions.Entry<>(id("sword_100"), form(SWORD, 100, FormSource.GRADE_QUEST, true));
        var mage50 = new Definitions.Entry<>(id("mage_50"), form(MAGE, 50, FormSource.LEVEL, true));
        var all = List.of(f50, f100, mage50);
        var owned = FormCatalogue.owned(all, List.of(slot(SWORD, 120)), PlayerForms.EMPTY);
        assertEquals(List.of(id("sword_50")), owned.stream().map(FormCatalogue.Owned::id).toList());
        owned = FormCatalogue.owned(all, List.of(slot(SWORD, 120)), PlayerForms.EMPTY.withGranted(id("sword_100")));
        assertEquals(2, owned.size());
    }

    @Test
    void aFusionInheritsTheParentFormsOnlyWhenItReachesTheLevel() {
        var f50 = new Definitions.Entry<>(id("sword_50"), form(SWORD, 50, FormSource.LEVEL, true));
        var f100 = new Definitions.Entry<>(id("sword_100"), form(SWORD, 100, FormSource.GRADE_QUEST, true));
        var f150 = new Definitions.Entry<>(id("sword_150"), form(SWORD, 150, FormSource.LEVEL, false));
        var all = List.of(f50, f100, f150);
        assertTrue(FormCatalogue.owned(all, List.of(slot(SPELLSWORD, 49, SWORD, MAGE)), PlayerForms.EMPTY).isEmpty());
        var at50 = FormCatalogue.owned(all, List.of(slot(SPELLSWORD, 50, SWORD, MAGE)), PlayerForms.EMPTY);
        assertEquals(1, at50.size());
        assertTrue(at50.get(0).inherited());
        var at100 = FormCatalogue.owned(all, List.of(slot(SPELLSWORD, 100, SWORD, MAGE)), PlayerForms.EMPTY);
        assertEquals(2, at100.size(), "inherited without redoing the grade quest");
        assertEquals(2, FormCatalogue.owned(all, List.of(slot(SPELLSWORD, 200, SWORD, MAGE)), PlayerForms.EMPTY).size(),
                "forms that are not inheritable stay with the parent");
    }

    @Test
    void aNonHybridClassDoesNotInherit() {
        var f50 = new Definitions.Entry<>(id("sword_50"), form(SWORD, 50, FormSource.LEVEL, true));
        assertTrue(FormCatalogue.owned(List.of(f50), List.of(slot(MAGE, 200)), PlayerForms.EMPTY).isEmpty());
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
        var good = form(SWORD, 50, FormSource.LEVEL, false);
        var repeated = form(SWORD, 50, FormSource.LEVEL, false);
        var badClass = form(MAGE, 50, FormSource.LEVEL, false);
        var problems = FormValidator.validate(Map.of(id("good"), good, id("repeated"), repeated, id("bad_class"), badClass), types,
                java.util.Set.of(SWORD), java.util.Set.of(id("a"), id("b"), id("c")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("unknown type mitofus:nope")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("unknown class mitofus:mage")), problems.toString());
        assertEquals(1, problems.stream().filter(p -> p.contains("already has a form at level 50")).count(), problems.toString());
    }
}
