package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.form.ElementRules;
import com.orijua.mitofus.feature.form.ElementTypeDefinition;
import com.orijua.mitofus.feature.effect.ActiveTraits;
import com.orijua.mitofus.feature.form.FormService;
import com.orijua.mitofus.feature.progression.ClassService;
import com.orijua.mitofus.feature.quest.QuestDefinition;
import com.orijua.mitofus.feature.skill.SkillService;
import com.orijua.mitofus.feature.form.PlayerForms;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/** Elemental types as data and the forms a player stores. The forms themselves come with each class's own PR. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class FormTests {
    private static final ResourceLocation FIRE = Mitofus.id("fire");
    private static final ResourceLocation WATER = Mitofus.id("water");
    private static final ResourceLocation EARTH = Mitofus.id("earth");
    private static final ResourceLocation LIGHTNING = Mitofus.id("lightning");
    private static final ResourceLocation LIGHT = Mitofus.id("light");
    private static final ResourceLocation SHADOW = Mitofus.id("shadow");
    private static final ResourceLocation SOME_FORM = Mitofus.id("no_such_form");

    private FormTests() {
    }

    private static double hit(GameTestHelper helper, ResourceLocation attacker, ResourceLocation defender) {
        var level = helper.getLevel();
        return ElementRules.multiplier(Optional.of(attacker), Definitions.get(level, ModDatapackRegistries.ELEMENT_TYPE, attacker),
                Optional.of(defender), Definitions.get(level, ModDatapackRegistries.ELEMENT_TYPE, defender));
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theSixTypesLoadWithTheirCycleOfAdvantages(GameTestHelper helper) {
        helper.assertTrue(Definitions.all(helper.getLevel(), ModDatapackRegistries.ELEMENT_TYPE).size() == 6, "six types");
        helper.assertTrue(Math.abs(hit(helper, WATER, FIRE) - 1.15) < 1e-9, "water beats fire");
        helper.assertTrue(Math.abs(hit(helper, FIRE, EARTH) - 1.15) < 1e-9, "fire beats earth");
        helper.assertTrue(Math.abs(hit(helper, EARTH, LIGHTNING) - 1.15) < 1e-9, "earth beats lightning");
        helper.assertTrue(Math.abs(hit(helper, LIGHTNING, WATER) - 1.15) < 1e-9, "lightning beats water");
        helper.assertTrue(Math.abs(hit(helper, FIRE, WATER) - 0.90) < 1e-9, "fire is weak against water");
        helper.assertTrue(Math.abs(hit(helper, LIGHT, SHADOW) - 1.15) < 1e-9 && Math.abs(hit(helper, SHADOW, LIGHT) - 1.15) < 1e-9,
                "light and shadow counter each other");
        helper.assertTrue(Math.abs(hit(helper, WATER, EARTH) - 1.0) < 1e-9, "unrelated types are neutral");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aPlayerStartsWithoutFormsAndCannotEquipOneThatDoesNotExist(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        helper.assertTrue(FormService.forms(player).equals(PlayerForms.EMPTY), "no forms at the start");
        helper.assertTrue(FormService.owned(player).isEmpty(), "nothing owned");
        helper.assertTrue(FormService.equip(player, SOME_FORM) == FormService.Result.UNKNOWN, "an unknown form is refused");
        helper.assertFalse(FormService.forceEquip(player, SOME_FORM), "the operator cannot equip it either");
        helper.assertFalse(FormService.addUse(player), "no form worn, no experience");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void formsAreKeptAndResetAllClearsThem(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        FormService.grant(player, SOME_FORM);
        player.setData(ModAttachments.PLAYER_FORMS, FormService.forms(player).withXp(SOME_FORM, 300).withEquipped(Optional.of(SOME_FORM)));
        PlayerForms stored = FormService.forms(player);
        helper.assertTrue(stored.isGranted(SOME_FORM) && stored.xpOf(SOME_FORM) == 300 && stored.equipped().equals(Optional.of(SOME_FORM)),
                "stored: " + stored);
        helper.assertTrue(FormService.equipped(player).isEmpty(), "a form the classes do not give is not worn");
        ModAttachments.resetAll(player);
        helper.assertTrue(FormService.forms(player).equals(PlayerForms.EMPTY), "reset all clears the forms");
        helper.succeed();
    }

    private static final ResourceLocation SWORD = Mitofus.id("swordsman");
    private static final ResourceLocation EMBER_BLADE = Mitofus.id("ember_blade");
    private static final ResourceLocation TIDEBREAKER = Mitofus.id("tidebreaker");
    private static final ResourceLocation FLAME_SLASH = Mitofus.id("flame_slash");
    private static final ResourceLocation INFERNO_BURST = Mitofus.id("inferno_burst");

    private static ServerPlayer swordsman(GameTestHelper helper, int classLevel) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.chooseClass(player, 0, SWORD);
        ClassService.setClassLevel(player, 0, classLevel);
        return player;
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theSwordsmanHasTenFormsOneEveryFiftyLevels(GameTestHelper helper) {
        var forms = Definitions.all(helper.getLevel(), ModDatapackRegistries.FORM).stream()
                .filter(e -> e.value().playerClass().equals(SWORD)).toList();
        helper.assertTrue(forms.size() == 10, "ten forms: " + forms.size());
        for (int i = 0; i < 10; i++) {
            final int level = 50 * (i + 1);
            helper.assertTrue(forms.stream().anyMatch(e -> e.value().unlockLevel() == level), "a form at class level " + level);
        }
        helper.assertTrue(forms.stream().allMatch(e -> e.value().skills().size() == 3), "three skills each");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aLevelFormIsOwnedAtItsLevelAndAQuestFormNeedsTheQuest(GameTestHelper helper) {
        ServerPlayer player = swordsman(helper, 49);
        helper.assertTrue(FormService.owned(player).isEmpty(), "nothing at level 49");
        ClassService.setClassLevel(player, 0, 100);
        helper.assertTrue(FormService.owned(player).stream().anyMatch(o -> o.id().equals(EMBER_BLADE)), "level 50 form owned at 100");
        helper.assertFalse(FormService.owned(player).stream().anyMatch(o -> o.id().equals(TIDEBREAKER)), "the level 100 form needs its quest");
        FormService.grant(player, TIDEBREAKER);
        helper.assertTrue(FormService.owned(player).stream().anyMatch(o -> o.id().equals(TIDEBREAKER)), "owned once granted");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void everyGradeQuestOfTheSwordsmanGivesAForm(GameTestHelper helper) {
        var quests = Definitions.all(helper.getLevel(), ModDatapackRegistries.QUEST).stream()
                .filter(e -> e.value().gradeQuest().isPresent() && e.value().requiredClass().equals(java.util.Optional.of(SWORD))).toList();
        helper.assertTrue(!quests.isEmpty(), "grade quests exist");
        for (var quest : quests) {
            helper.assertTrue(quest.value().formReward().isPresent(), quest.id() + " gives a form");
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void wearingAFormGivesItsSkillsAndTakingItOffRemovesThemWithTheirBarSlot(GameTestHelper helper) {
        ServerPlayer player = swordsman(helper, 50);
        helper.assertTrue(FormService.equip(player, EMBER_BLADE) == FormService.Result.OK, "equipped");
        helper.assertTrue(SkillService.get(player).knows(FLAME_SLASH), "the attack comes at form level 1");
        helper.assertFalse(SkillService.get(player).knows(INFERNO_BURST), "the ultimate waits for form level 60");
        int slot = SkillService.get(player).slots().indexOf(java.util.Optional.of(FLAME_SLASH));
        helper.assertTrue(slot >= 0, "it went to the bar");
        SkillService.assign(player, 2, java.util.Optional.of(FLAME_SLASH));
        helper.assertTrue(FormService.unequip(player) == FormService.Result.OK, "taken off");
        helper.assertFalse(SkillService.get(player).knows(FLAME_SLASH), "the skill leaves with the form");
        helper.assertTrue(SkillService.get(player).knows(Mitofus.id("heavy_strike")), "the class skills stay");
        FormService.equip(player, EMBER_BLADE);
        helper.assertTrue(SkillService.get(player).slot(2).equals(java.util.Optional.of(FLAME_SLASH)), "back in the slot it had");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theUltimateAndThePassiveComeWithTheFormLevel(GameTestHelper helper) {
        ServerPlayer player = swordsman(helper, 50);
        FormService.equip(player, EMBER_BLADE);
        var searing = Mitofus.id("searing_edge");
        helper.assertFalse(ActiveTraits.active(player).stream().anyMatch(p -> Definitions.get(helper.getLevel(), ModDatapackRegistries.PASSIVE, searing).orElseThrow() == p), "no passive at form level 1");
        FormService.setLevel(player, EMBER_BLADE, 30);
        helper.assertTrue(ActiveTraits.active(player).contains(Definitions.get(helper.getLevel(), ModDatapackRegistries.PASSIVE, searing).orElseThrow()), "the passive at form level 30");
        helper.assertFalse(SkillService.get(player).knows(INFERNO_BURST), "still no ultimate");
        FormService.setLevel(player, EMBER_BLADE, 60);
        helper.assertTrue(SkillService.get(player).knows(INFERNO_BURST), "the ultimate at form level 60");
        helper.assertTrue(FormService.typeBonus(player) > 0.11 && FormService.typeBonus(player) < 0.13, "bonus at 60: " + FormService.typeBonus(player));
        FormService.unequip(player);
        helper.assertTrue(FormService.typeBonus(player) == 0.0, "no bonus without a form");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aFormCannotBeChangedInCombatButCanAfterFiveSeconds(GameTestHelper helper) {
        ServerPlayer player = swordsman(helper, 50);
        com.orijua.mitofus.feature.vrhelmet.CombatClock.mark(player, player.server.getTickCount());
        helper.assertTrue(FormService.equip(player, EMBER_BLADE) == FormService.Result.IN_COMBAT, "refused right after damage");
        com.orijua.mitofus.feature.vrhelmet.CombatClock.mark(player, player.server.getTickCount() - 101);
        helper.assertTrue(FormService.equip(player, EMBER_BLADE) == FormService.Result.OK, "allowed after 5 seconds");
        com.orijua.mitofus.feature.vrhelmet.CombatClock.forget(player);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void usingTheFormLevelsItUp(GameTestHelper helper) {
        ServerPlayer player = swordsman(helper, 50);
        FormService.equip(player, EMBER_BLADE);
        boolean levelled = false;
        for (int i = 0; i < 50 && !levelled; i++) {
            levelled = FormService.addUse(player);
        }
        helper.assertTrue(levelled, "a low form levels up within 50 boosted uses");
        helper.assertTrue(FormService.equipped(player).orElseThrow().level() == 2, "level 2");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theFormsTabClickEquipsAndTakesOffAndRefusesWithAMessage(GameTestHelper helper) {
        ServerPlayer player = swordsman(helper, 100);
        FormService.handle(player, Optional.of(TIDEBREAKER));
        helper.assertTrue(FormService.forms(player).equipped().isEmpty(), "a form the player does not have is refused");
        FormService.handle(player, Optional.of(EMBER_BLADE));
        helper.assertTrue(FormService.forms(player).equipped().equals(Optional.of(EMBER_BLADE)), "worn");
        com.orijua.mitofus.feature.vrhelmet.CombatClock.mark(player, player.server.getTickCount());
        FormService.handle(player, Optional.empty());
        helper.assertTrue(FormService.forms(player).equipped().equals(Optional.of(EMBER_BLADE)), "not taken off in combat");
        com.orijua.mitofus.feature.vrhelmet.CombatClock.forget(player);
        FormService.handle(player, Optional.empty());
        helper.assertTrue(FormService.forms(player).equipped().isEmpty(), "taken off");
        helper.assertTrue(FormService.messageKey(FormService.Result.OK).isEmpty(), "no message when it works");
        helper.assertTrue(FormService.messageKey(FormService.Result.IN_COMBAT).equals(Optional.of("message.mitofus.form_in_combat")), "combat message");
        helper.succeed();
    }
}
