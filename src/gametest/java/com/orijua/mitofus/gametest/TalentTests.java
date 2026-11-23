package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.effect.ActiveTraits;
import com.orijua.mitofus.feature.effect.BonusType;
import com.orijua.mitofus.feature.progression.ClassGrade;
import com.orijua.mitofus.feature.progression.ClassService;
import com.orijua.mitofus.feature.progression.ClassSlot;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.skill.SkillService;
import com.orijua.mitofus.feature.talent.TalentEffects;
import com.orijua.mitofus.feature.talent.TalentRules;
import com.orijua.mitofus.feature.talent.TalentService;
import com.orijua.mitofus.core.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Buying and refunding the nodes of the swordsman's talent tree. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class TalentTests {
    private static final ResourceLocation SWORD = Mitofus.id("swordsman");
    private static final ResourceLocation SHARP_EDGE = Mitofus.id("sharp_edge");
    private static final ResourceLocation HEAVY_STRIKE_1 = Mitofus.id("heavy_strike_1");
    private static final ResourceLocation MASTER_EDGE = Mitofus.id("master_edge");

    private TalentTests() {
    }

    private static ClassSlot slot(ServerPlayer player) {
        return ClassService.classes(player).slot(0).orElseThrow();
    }

    private static int available(ServerPlayer player) {
        return TalentRules.pointsAvailable(TalentService.all(player.level()), slot(player));
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aNewClassHasOnePointAndBuysItsFirstNode(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.chooseClass(player, 0, SWORD);
        helper.assertTrue(available(player) == 1, "level 1 gives 1 point: " + available(player));
        helper.assertTrue(TalentService.buy(player, 0, SHARP_EDGE) == TalentRules.Buy.OK, "the first node is bought");
        helper.assertTrue(slot(player).talents().contains(SHARP_EDGE), "it is saved in the slot");
        helper.assertTrue(available(player) == 0, "and the point is spent");
        helper.assertTrue(TalentService.buy(player, 0, SHARP_EDGE) == TalentRules.Buy.OWNED, "no buying twice");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void lockedNodesPointlessGradesAndMissingPointsAreRefused(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.chooseClass(player, 0, SWORD);
        helper.assertTrue(TalentService.buy(player, 0, HEAVY_STRIKE_1) == TalentRules.Buy.LOCKED, "needs sharp_edge first");
        ClassService.setClassLevel(player, 0, 100);
        helper.assertTrue(TalentService.buy(player, 0, MASTER_EDGE) == TalentRules.Buy.LOCKED, "the end of a branch is locked");
        helper.assertTrue(TalentService.buy(player, 1, SHARP_EDGE) == TalentRules.Buy.UNKNOWN, "an empty slot has no tree");
        helper.assertTrue(TalentService.buy(player, 0, Mitofus.id("no_such_talent")) == TalentRules.Buy.UNKNOWN, "unknown node");
        helper.assertTrue(slot(player).talents().isEmpty(), "nothing was bought");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void refundingFreesThePointsAndKeepsTheClass(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.chooseClass(player, 0, SWORD);
        ClassService.setClassLevel(player, 0, 5);
        TalentService.buy(player, 0, SHARP_EDGE);
        TalentService.buy(player, 0, HEAVY_STRIKE_1);
        helper.assertTrue(available(player) == 3, "5 points minus 2 spent: " + available(player));
        helper.assertTrue(TalentService.refundAll(player) == 2, "two points come back");
        helper.assertTrue(slot(player).talents().isEmpty() && available(player) == 5, "all five points are free again");
        helper.assertTrue(slot(player).classLevel() == 5 && slot(player).grade() == ClassGrade.APPRENTICE, "level and grade are kept");
        helper.assertTrue(TalentService.refundAll(player) == 0, "nothing more to refund");
        helper.succeed();
    }

    private static void buyAll(ServerPlayer player, String... nodes) {
        for (String node : nodes) {
            TalentService.buy(player, 0, Mitofus.id(node));
        }
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void statNodesRaiseTheEffectiveStatAndRefundTakesThemBack(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.chooseClass(player, 0, SWORD);
        int before = ProgressionService.effectiveStats(player).strength();
        buyAll(player, "sharp_edge");
        helper.assertTrue(ProgressionService.effectiveStats(player).strength() == before + 1, "sharp edge gives +1 strength");
        TalentService.refundAll(player);
        helper.assertTrue(ProgressionService.effectiveStats(player).strength() == before, "refunding takes it back");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aPassiveBoostAddsItsBonusToAPassiveTheClassHas(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.chooseClass(player, 0, SWORD);
        ClassService.setClassLevel(player, 0, 5);
        double armor = ActiveTraits.total(player, BonusType.ARMOR);
        buyAll(player, "thick_hide", "tough_skin_1");
        helper.assertTrue(ActiveTraits.total(player, BonusType.ARMOR) == armor + 1, "tough skin I gives +1 armor");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aSkillBoostChangesCostAndCooldownButNotOtherSkills(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.chooseClass(player, 0, SWORD);
        ClassService.setClassLevel(player, 0, 5);
        buyAll(player, "sharp_edge", "quick_recovery", "heavy_strike_1");
        var heavy = TalentEffects.tuning(player, Mitofus.id("heavy_strike"));
        helper.assertTrue(heavy.cooldown() == 0.9 && heavy.damage() == 1.1, "heavy strike: 10% faster and 10% stronger: " + heavy);
        helper.assertTrue(TalentEffects.tuning(player, Mitofus.id("war_cry")).equals(TalentEffects.SkillTuning.NONE), "other skills are untouched");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aGrantedSkillIsLearnedAndForgottenOnRefund(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.chooseClass(player, 0, SWORD);
        ClassService.setClassLevel(player, 0, 10);
        ResourceLocation slash = Mitofus.id("rending_slash");
        buyAll(player, "sharp_edge", "quick_recovery", "nimble_wrists");
        helper.assertFalse(SkillService.get(player).knows(slash), "not yet");
        buyAll(player, "rending_slash");
        helper.assertTrue(SkillService.get(player).knows(slash), "the node teaches the skill");
        TalentService.refundAll(player);
        helper.assertFalse(SkillService.get(player).knows(slash), "the refund takes it back");
        helper.assertTrue(SkillService.get(player).knows(Mitofus.id("heavy_strike")), "the class skill stays");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theTalentScrollRefundsTalentsAndIsUsedUp(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.chooseClass(player, 0, SWORD);
        ClassService.setClassLevel(player, 0, 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TALENT_SCROLL.get(), 2));
        helper.assertTrue(ModItems.TALENT_SCROLL.get().use(player.level(), player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL,
                "nothing to refund: the scroll is kept");
        helper.assertTrue(player.getMainHandItem().getCount() == 2, "still two scrolls");
        buyAll(player, "sharp_edge", "heavy_strike_1");
        ModItems.TALENT_SCROLL.get().use(player.level(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(slot(player).talents().isEmpty() && available(player) == 5, "all points are free again");
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "one scroll was used");
        helper.succeed();
    }
}
