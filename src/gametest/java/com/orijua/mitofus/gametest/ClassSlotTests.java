package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.effect.ActiveTraits;
import com.orijua.mitofus.feature.effect.BonusType;
import com.orijua.mitofus.feature.effect.PassiveDefinition;
import com.orijua.mitofus.feature.progression.ClassGrade;
import com.orijua.mitofus.feature.progression.ClassService;
import com.orijua.mitofus.feature.progression.ClassSlot;
import com.orijua.mitofus.feature.progression.ClassSlotRules;
import com.orijua.mitofus.feature.progression.PlayerClasses;
import com.orijua.mitofus.feature.progression.ProgressionRules;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.skill.SkillService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The class slots by player level, the one job, class levels and grades. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class ClassSlotTests {
    private static final ResourceLocation SWORD = Mitofus.id("swordsman");
    private static final ResourceLocation MAGE = Mitofus.id("mage");
    private static final ResourceLocation ARCHER = Mitofus.id("archer");
    private static final ResourceLocation BLACKSMITH = Mitofus.id("blacksmith");

    private ClassSlotTests() {
    }

    private static PlayerClasses classes(ServerPlayer player) {
        return player.getData(ModAttachments.PLAYER_CLASSES);
    }

    private static void setLevel(ServerPlayer player, int level) {
        ProgressionService.set(player, ProgressionService.get(player).withProgress(level, 0, 0));
    }

    private static PassiveDefinition passive(ServerPlayer player, String name) {
        return Definitions.get(player.level(), ModDatapackRegistries.PASSIVE, Mitofus.id(name)).orElseThrow();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void firstSlotAtLevelOneTeachesTheSkillAndActivatesThePassive(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        helper.assertTrue(ClassService.chooseClass(player, 0, SWORD), "slot 1 is open at level 1");
        helper.assertTrue(SkillService.get(player).knows(Mitofus.id("heavy_strike")), "the class skill is learned");
        helper.assertTrue(ActiveTraits.active(player).contains(passive(player, "tough_skin")), "the class passive is active");
        ClassSlot slot = classes(player).slot(0).orElseThrow();
        helper.assertTrue(slot.classLevel() == 1 && slot.classXp() == 0 && slot.grade() == ClassGrade.APPRENTICE, "starts at level 1: " + slot);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void secondSlotIsRefusedAtLevel19AndAcceptedAt20(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.chooseClass(player, 0, SWORD);
        setLevel(player, 19);
        helper.assertFalse(ClassService.chooseClass(player, 1, MAGE), "slot 2 needs level 20");
        helper.assertTrue(classes(player).slot(1).isEmpty(), "nothing was placed");
        setLevel(player, 20);
        helper.assertTrue(ClassService.chooseClass(player, 1, MAGE), "slot 2 opens at level 20");
        helper.assertTrue(classes(player).hasClass(MAGE), "placed");
        helper.assertFalse(ClassService.chooseClass(player, 2, ARCHER), "slot 3 needs level 50");
        helper.assertFalse(ClassService.chooseClass(player, 3, ARCHER), "there is no slot 4");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theSameClassCannotFillTwoSlots(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        setLevel(player, 20);
        ClassService.chooseClass(player, 0, SWORD);
        helper.assertFalse(ClassService.chooseClass(player, 1, SWORD), "the same class twice");
        helper.assertTrue(classes(player).slot(1).isEmpty(), "the second slot stays free");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void choosingTheSameSlotTwiceChangesNothing(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.chooseClass(player, 0, SWORD);
        PlayerClasses before = classes(player);
        helper.assertFalse(ClassService.chooseClass(player, 0, MAGE), "what is chosen is not changed");
        helper.assertTrue(classes(player).equals(before), "the classes are the same");
        helper.assertFalse(SkillService.get(player).knows(Mitofus.id("flame_bolt")), "and the refused class taught nothing");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void classesCannotBeChosenOutsideMitofus(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        MitofusOnlyTests.realWorld(helper, () -> {
            helper.assertFalse(ClassService.chooseClass(player, 0, SWORD), "refused in the real world");
            helper.assertFalse(ClassService.chooseJob(player, BLACKSMITH), "the job too");
        });
        helper.assertTrue(classes(player).slots().isEmpty() && classes(player).job().isEmpty(), "nothing was set");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void thirdSlotPassiveOnlyWorksInTheThirdSlot(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        PassiveDefinition swordMastery = passive(player, "sword_mastery");
        ClassService.chooseClass(player, 0, SWORD);
        helper.assertFalse(ActiveTraits.active(player).contains(swordMastery), "in the first slot the extra passive is off");
        setLevel(player, 50);
        ClassService.chooseClass(player, 1, ARCHER);
        ClassService.chooseClass(player, 2, MAGE);
        helper.assertTrue(ActiveTraits.active(player).contains(passive(player, "composure")), "the mage's extra passive works in the third slot");
        helper.assertFalse(ActiveTraits.active(player).contains(swordMastery), "the swordsman is not there");

        ServerPlayer other = TestSupport.fakePlayer(helper);
        setLevel(other, 50);
        ClassService.chooseClass(other, 0, MAGE);
        ClassService.chooseClass(other, 1, ARCHER);
        ClassService.chooseClass(other, 2, SWORD);
        helper.assertTrue(ActiveTraits.active(other).contains(swordMastery), "the swordsman in the third slot gets it");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theBonusesOfTwoClassesAndTheJobAdd(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        setLevel(player, 20);
        ClassService.chooseClass(player, 0, SWORD);
        ClassService.chooseClass(player, 1, MAGE);
        // Base 6 STR + 3 (swordsman); base 5 INT + 5 (mage).
        helper.assertTrue(ProgressionService.effectiveStats(player).strength() == 6 + 3, "swordsman +3 STR");
        helper.assertTrue(ProgressionService.effectiveStats(player).intelligence() == 5 + 5, "mage +5 INT");
        int dexterity = ProgressionService.effectiveStats(player).dexterity();
        ClassService.chooseJob(player, BLACKSMITH);
        helper.assertTrue(ProgressionService.effectiveStats(player).dexterity() == dexterity + 3, "the blacksmith's +3 DEX adds on top");
        helper.assertTrue(ProgressionService.effectiveStats(player).strength() == 6 + 3, "and the classes' bonuses are still there");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theJobIsChosenOnceAndTheBlacksmithAddsSmithing(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        int before = ProgressionService.effectiveSmithingLevel(player);
        helper.assertTrue(ClassService.chooseJob(player, BLACKSMITH), "the blacksmith job is selectable");
        helper.assertFalse(ClassService.chooseJob(player, Mitofus.id("tailor")), "a second job is refused");
        helper.assertTrue(ClassService.currentJob(player).filter(BLACKSMITH::equals).isPresent(), "still the blacksmith");
        helper.assertTrue(ProgressionService.effectiveSmithingLevel(player) == before + 5, "smithing_bonus 5");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void sanchosLegacyGivesTheLegendaryJobAndGuadaleteStillComesOut(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.chooseJob(player, BLACKSMITH);
        helper.assertTrue(ClassService.grantJob(player, GuadaleteTests.SUCCESSOR), "the quest's job_reward replaces the job");
        helper.assertTrue(ClassService.currentJob(player).filter(GuadaleteTests.SUCCESSOR::equals).isPresent(), "the legendary job");
        helper.assertTrue(GuadaleteTests.anvilWithGuadaleteInputs(helper, player).canForge(), "the Legendary Blacksmith can forge Guadalete");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aQuestClassGoesToTheFirstFreeSlotAndNeverBlocksWhenThereIsNone(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.chooseClass(player, 0, SWORD);
        helper.assertFalse(ClassService.grantClass(player, MAGE), "slot 2 is closed at level 1: nothing is lost, the player is told");
        setLevel(player, 20);
        helper.assertTrue(ClassService.grantClass(player, MAGE), "now there is a free slot");
        helper.assertTrue(classes(player).slot(1).filter(s -> s.classId().equals(MAGE)).isPresent(), "in the first free one");
        helper.succeed();
    }

    /** Both classes sit far below the player's level 20, so both get the late-starter catch-up (x2) and so the same XP. */
    @GameTest(template = TestSupport.EMPTY)
    public static void xpGoesToEveryPlacedClass(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        setLevel(player, 20);
        ClassService.chooseClass(player, 0, SWORD);
        ClassService.chooseClass(player, 1, MAGE);
        int amount = ProgressionRules.xpToNextLevel(Balance.current(), 1) + 3;
        ProgressionService.awardXp(player, amount);
        ClassSlot expected = ClassSlotRules.addClassXp(Balance.current(), ClassSlot.fresh(0, SWORD), amount * 2);
        for (int slot = 0; slot < 2; slot++) {
            ClassSlot placed = classes(player).slot(slot).orElseThrow();
            helper.assertTrue(placed.classLevel() == expected.classLevel() && placed.classXp() == expected.classXp(), "slot " + slot + " got the same XP: " + placed);
        }
        helper.succeed();
    }

    /** A class close to the player's level gets the plain XP, with no catch-up. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aClassCloseToThePlayerGetsNoCatchUp(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        setLevel(player, 20);
        ClassService.chooseClass(player, 0, SWORD);
        ClassService.setClassLevel(player, 0, 15);
        ProgressionService.awardXp(player, 3);
        ClassSlot placed = classes(player).slot(0).orElseThrow();
        helper.assertTrue(placed.classLevel() == 15 && placed.classXp() == 3, "plain XP: " + placed);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void gradeUpNeedsTheClassLevelAndGoesOneGradeAtATime(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.chooseClass(player, 0, SWORD);
        helper.assertTrue(ClassService.advanceGrade(player, 0) == ClassSlotRules.Advance.LEVEL_TOO_LOW, "level 1 is not enough");
        helper.assertTrue(ClassService.advanceGrade(player, 1) == ClassSlotRules.Advance.NO_CLASS, "an empty slot");
        ClassService.setClassLevel(player, 0, 300);
        helper.assertTrue(ClassService.advanceGrade(player, 0) == ClassSlotRules.Advance.OK, "Adept");
        helper.assertTrue(ClassService.advanceGrade(player, 0) == ClassSlotRules.Advance.OK, "Expert, one grade at a time");
        helper.assertTrue(classes(player).slot(0).orElseThrow().grade() == ClassGrade.EXPERT, "Expert");
        // The swordsman no longer asks for a job level: a job never blocks a grade.
        helper.assertTrue(ClassService.advanceGrade(player, 0) == ClassSlotRules.Advance.OK, "Master with no job");
        helper.assertTrue(classes(player).slot(0).orElseThrow().grade() == ClassGrade.MASTER, "Master");
        helper.assertTrue(ClassService.advanceGrade(player, 0) == ClassSlotRules.Advance.LEVEL_TOO_LOW, "Grand Master needs level 400");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theSlotsSurviveDeathAndResetAllEmptiesThem(GameTestHelper helper) {
        ServerPlayer old = TestSupport.fakePlayer(helper);
        setLevel(old, 20);
        ClassService.chooseClass(old, 0, SWORD);
        ClassService.chooseClass(old, 1, MAGE);
        ClassService.chooseJob(old, BLACKSMITH);
        PlayerClasses expected = classes(old);
        ServerPlayer fresh = TestSupport.fakePlayer(helper);
        fresh.restoreFrom(old, false);
        helper.assertTrue(classes(fresh).equals(expected), "after dying the slots and the job are kept: " + classes(fresh));

        ModAttachments.resetAll(fresh);
        helper.assertTrue(classes(fresh).equals(PlayerClasses.DEFAULT), "reset all empties them: " + classes(fresh));
        helper.assertTrue(ActiveTraits.total(fresh, BonusType.MAX_MANA) == 0, "and the passives go with them");
        helper.succeed();
    }
}
