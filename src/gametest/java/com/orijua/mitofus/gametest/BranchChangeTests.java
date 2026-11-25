package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.economy.Money;
import com.orijua.mitofus.feature.economy.Wallet;
import com.orijua.mitofus.feature.effect.ActiveTraits;
import com.orijua.mitofus.feature.progression.ClassGrade;
import com.orijua.mitofus.feature.progression.ClassService;
import com.orijua.mitofus.feature.progression.ClassSlot;
import com.orijua.mitofus.feature.progression.PlayerClasses;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.skill.SkillService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Changing the branch of a slot at the village chief: dormant branches, the price and what is learned or forgotten. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class BranchChangeTests {
    private static final ResourceLocation SWORD = Mitofus.id("swordsman");
    private static final ResourceLocation MAGE = Mitofus.id("mage");
    private static final ResourceLocation ARCHER = Mitofus.id("archer");
    private static final ResourceLocation SPEAR = Mitofus.id("spearman");

    private BranchChangeTests() {
    }

    private static PlayerClasses classes(ServerPlayer player) {
        return player.getData(ModAttachments.PLAYER_CLASSES);
    }

    private static ServerPlayer playerWithSword(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ProgressionService.set(player, ProgressionService.get(player).withProgress(50, 0, 0));
        ClassService.chooseClass(player, 0, SWORD);
        ClassService.chooseClass(player, 1, MAGE);
        return player;
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theFirstChangeOfASlotIsFreeAndTheOldBranchSleepsWithItsProgress(GameTestHelper helper) {
        ServerPlayer player = playerWithSword(helper);
        ClassService.setClassLevel(player, 0, 30);
        helper.assertTrue(ClassService.changeBranch(player, 0, ARCHER), "the first change is free: the purse is empty");
        helper.assertTrue(classes(player).slot(0).orElseThrow().classId().equals(ARCHER), "the archer is in slot 1");
        ClassSlot sleeping = classes(player).dormant(SWORD).orElseThrow();
        helper.assertTrue(sleeping.classLevel() == 30, "the swordsman keeps level 30: " + sleeping);
        helper.assertTrue(!classes(player).hasClass(SWORD), "a dormant branch is not held");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aDormantBranchGivesNoSkillsOrPassivesAndGetsThemBack(GameTestHelper helper) {
        ServerPlayer player = playerWithSword(helper);
        helper.assertTrue(SkillService.get(player).knows(Mitofus.id("heavy_strike")), "the swordsman skill is known");
        var tough = com.orijua.mitofus.core.registry.Definitions.get(player.level(), com.orijua.mitofus.core.registry.ModDatapackRegistries.PASSIVE,
                Mitofus.id("tough_skin")).orElseThrow();
        ClassService.changeBranch(player, 0, ARCHER);
        helper.assertFalse(SkillService.get(player).knows(Mitofus.id("heavy_strike")), "a sleeping branch teaches nothing");
        helper.assertFalse(ActiveTraits.active(player).stream().anyMatch(p -> p.equals(tough)), "the swordsman passive is off");
        Wallet.add(player, Money.iron(5));
        helper.assertTrue(ClassService.changeBranch(player, 0, SWORD), "the second change costs 1 gold and the purse has 5");
        helper.assertTrue(SkillService.get(player).knows(Mitofus.id("heavy_strike")), "the skill is back");
        helper.assertTrue(classes(player).dormant(ARCHER).isPresent(), "the archer sleeps now");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theSecondChangeOfASlotCostsOneGoldCoin(GameTestHelper helper) {
        ServerPlayer player = playerWithSword(helper);
        helper.assertTrue(ClassService.changeBranch(player, 0, ARCHER), "free");
        helper.assertFalse(ClassService.changeBranch(player, 0, SPEAR), "no coins: refused");
        helper.assertTrue(classes(player).slot(0).orElseThrow().classId().equals(ARCHER), "nothing changed");
        Wallet.add(player, Money.iron(3));
        helper.assertTrue(ClassService.changeBranch(player, 0, SPEAR), "now it can pay");
        helper.assertTrue(Wallet.balance(player) == Money.iron(2), "1 gold was charged: " + Wallet.balance(player));
        helper.assertTrue(ClassService.changeBranch(player, 1, ARCHER), "slot 2 still has its free change (the archer comes back from sleep)");
        helper.assertTrue(Wallet.balance(player) == Money.iron(2), "and it was free");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aBranchHeldInAnotherSlotOrAnEmptyOrClosedSlotIsRefused(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.chooseClass(player, 0, SWORD);
        helper.assertFalse(ClassService.changeBranch(player, 1, ARCHER), "slot 2 is closed at level 1");
        ProgressionService.set(player, ProgressionService.get(player).withProgress(20, 0, 0));
        helper.assertFalse(ClassService.changeBranch(player, 1, ARCHER), "slot 2 is empty: choose instead");
        ClassService.chooseClass(player, 1, MAGE);
        helper.assertFalse(ClassService.changeBranch(player, 0, MAGE), "the mage is in slot 2");
        helper.assertFalse(ClassService.changeBranch(player, 0, SWORD), "the same branch");
        helper.assertTrue(classes(player).dormantBranches().isEmpty(), "nothing slept");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theGradeStaysWithTheBranchThatLeft(GameTestHelper helper) {
        ServerPlayer player = playerWithSword(helper);
        player.setData(ModAttachments.PLAYER_CLASSES, classes(player).withSlot(classes(player).slot(0).orElseThrow().withGrade(ClassGrade.ADEPT)));
        ClassService.changeBranch(player, 0, ARCHER);
        helper.assertTrue(classes(player).slot(0).orElseThrow().grade() == ClassGrade.APPRENTICE, "the new branch starts as apprentice");
        helper.assertTrue(classes(player).dormant(SWORD).orElseThrow().grade() == ClassGrade.ADEPT, "the sleeping one keeps its grade");
        helper.succeed();
    }
}
