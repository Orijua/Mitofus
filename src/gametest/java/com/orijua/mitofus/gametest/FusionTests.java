package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.economy.Money;
import com.orijua.mitofus.feature.economy.Wallet;
import com.orijua.mitofus.feature.fusion.FusionService;
import com.orijua.mitofus.feature.progression.ClassGrade;
import com.orijua.mitofus.feature.progression.ClassService;
import com.orijua.mitofus.feature.progression.ClassSlot;
import com.orijua.mitofus.feature.progression.PlayerClasses;
import com.orijua.mitofus.feature.progression.PlayerStats;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.skill.SkillService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Fusing two classes into a hybrid, and the class + job synergy bonus. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class FusionTests {
    private static final ResourceLocation ARCHER = Mitofus.id("archer");
    private static final ResourceLocation MAGE = Mitofus.id("mage");
    private static final ResourceLocation SWORD = Mitofus.id("swordsman");
    private static final ResourceLocation MARKSMAN = Mitofus.id("arcane_marksman");
    private static final ResourceLocation FUSION = Mitofus.id("arcane_marksman");

    private FusionTests() {
    }

    private static PlayerClasses classes(ServerPlayer player) {
        return player.getData(ModAttachments.PLAYER_CLASSES);
    }

    /** Archer and mage have no talent tree, so their trees count as complete: the fusion works once the coins are there. */
    @GameTest(template = TestSupport.EMPTY)
    public static void twoClassesFuseIntoOneHybridThatTakesOneSlot(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.forceClass(player, 0, ARCHER);
        ClassService.forceClass(player, 1, MAGE);
        ClassService.setClassLevel(player, 1, 12);
        helper.assertFalse(FusionService.fuse(player, FUSION), "no coins yet: nothing happens");
        helper.assertTrue(classes(player).slots().size() == 2, "both classes are still there");
        Wallet.add(player, Money.iron(10));
        helper.assertTrue(FusionService.fuse(player, FUSION), "with 10 gold the fusion works");
        PlayerClasses after = classes(player);
        helper.assertTrue(after.slots().size() == 1, "one slot is freed: " + after.slots());
        ClassSlot hybrid = after.slot(0).orElseThrow();
        helper.assertTrue(hybrid.classId().equals(MARKSMAN), "the hybrid takes the lower slot");
        helper.assertTrue(hybrid.origin().equals(java.util.List.of(ARCHER, MAGE)), "and remembers its parents: " + hybrid.origin());
        helper.assertTrue(hybrid.classLevel() == 12, "the higher level: " + hybrid.classLevel());
        helper.assertTrue(Wallet.balance(player) == 0, "the coins are paid: " + Wallet.balance(player));
        helper.assertTrue(SkillService.get(player).knows(Mitofus.id("flame_bolt")), "the mage's skills are kept");
        helper.assertFalse(FusionService.fuse(player, FUSION), "it cannot be done twice");
        helper.succeed();
    }

    /** A class left on its own can always join a hybrid: the hybrid keeps its identity and gains the class and its forms. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aHybridAbsorbsALoneClass(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ResourceLocation spearman = Mitofus.id("spearman");
        ClassService.forceClass(player, 0, ARCHER);
        ClassService.forceClass(player, 1, MAGE);
        Wallet.add(player, Money.iron(10));
        helper.assertTrue(FusionService.fuse(player, FUSION), "first the fusion");
        ClassService.forceClass(player, 2, spearman);
        helper.assertFalse(FusionService.missingAbsorb(player, 0, spearman).isEmpty(), "no coins left: it cannot absorb yet");
        helper.assertFalse(FusionService.absorb(player, 0, spearman), "and nothing happens");
        helper.assertTrue(classes(player).slots().size() == 2, "both slots are still there");
        Wallet.add(player, Money.iron(10));
        helper.assertTrue(FusionService.absorb(player, 0, spearman), "with the coins it works, missing " + FusionService.missingAbsorb(player, 0, spearman));
        PlayerClasses after = classes(player);
        helper.assertTrue(after.slots().size() == 1, "the lone class's slot is freed: " + after.slots());
        ClassSlot hybrid = after.slot(0).orElseThrow();
        helper.assertTrue(hybrid.classId().equals(MARKSMAN), "the hybrid keeps its identity");
        helper.assertTrue(hybrid.origin().equals(java.util.List.of(ARCHER, MAGE, spearman)), "and gains the class: " + hybrid.origin());
        helper.assertFalse(FusionService.absorb(player, 0, spearman), "a class cannot be absorbed twice");
        helper.succeed();
    }

    /** The hybrid gives more than the two classes did together. */
    @GameTest(template = TestSupport.EMPTY)
    public static void theHybridIsWorthMoreThanItsParents(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.forceClass(player, 0, ARCHER);
        ClassService.forceClass(player, 1, MAGE);
        int before = ProgressionService.effectiveStats(player).dexterity() + ProgressionService.effectiveStats(player).intelligence();
        Wallet.add(player, Money.iron(10));
        FusionService.fuse(player, FUSION);
        int after = ProgressionService.effectiveStats(player).dexterity() + ProgressionService.effectiveStats(player).intelligence();
        helper.assertTrue(after > before, "dexterity + intelligence " + before + " -> " + after);
        helper.succeed();
    }

    /** The swordsman's tree is not complete at the start, so it cannot fuse yet, and nothing is charged. */
    @GameTest(template = TestSupport.EMPTY)
    public static void anIncompleteTreeBlocksTheFusionAndKeepsTheCoins(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.forceClass(player, 0, SWORD);
        ClassService.forceClass(player, 1, MAGE);
        Wallet.add(player, Money.iron(10));
        helper.assertFalse(FusionService.fuse(player, Mitofus.id("spellsword")), "the swordsman's tree is incomplete");
        helper.assertTrue(Wallet.balance(player) == Money.iron(10), "no coins were taken");
        helper.assertTrue(classes(player).slots().size() == 2, "the classes are untouched");
        helper.succeed();
    }

    /** Different grades cannot fuse (unless the fusion allows it). */
    @GameTest(template = TestSupport.EMPTY)
    public static void differentGradesCannotFuse(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.forceClass(player, 0, ARCHER);
        ClassService.forceClass(player, 1, MAGE);
        player.setData(ModAttachments.PLAYER_CLASSES, classes(player).withSlot(classes(player).slot(1).orElseThrow().withGrade(ClassGrade.ADEPT)));
        Wallet.add(player, Money.iron(10));
        helper.assertFalse(FusionService.fuse(player, FUSION), "archer apprentice + mage adept");
        helper.succeed();
    }

    /** Swordsman + blacksmith at job level 10 gives the first step: +1 strength on top of the class's +3. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aSynergyAppliesOnceTheJobLevelIsReached(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.forceClass(player, 0, SWORD);
        ClassService.chooseJob(player, Mitofus.id("blacksmith"));
        helper.assertTrue(ProgressionService.effectiveStats(player).strength() == 6 + 3, "a new smith has no synergy yet");
        ProgressionService.set(player, PlayerStats.DEFAULT.withSmithingXp(1_000_000));
        helper.assertTrue(ClassService.jobLevels(player).getOrDefault(Mitofus.id("blacksmith"), 0) >= 10, "the smithing level reaches 10");
        helper.assertTrue(ProgressionService.effectiveStats(player).strength() == 6 + 3 + 1, "synergy +1 STR: " + ProgressionService.effectiveStats(player).strength());
        helper.succeed();
    }
}
