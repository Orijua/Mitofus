package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.progression.ClassService;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.progression.StatModifier;
import com.orijua.mitofus.feature.progression.StatSource;
import com.orijua.mitofus.feature.progression.StatType;
import com.orijua.mitofus.feature.quest.goal.PlayerCounters;
import com.orijua.mitofus.feature.skill.SkillService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class ClassAndTitleTests {
    private ClassAndTitleTests() {
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void choosingClassGrantsSkillsAndBonuses(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        helper.assertTrue(ClassService.chooseClass(player, 0, Mitofus.id("swordsman")), "swordsman should be defined");
        helper.assertFalse(ClassService.chooseClass(player, 0, Mitofus.id("mage")), "a class can only be chosen once");
        helper.assertFalse(SkillService.get(player).knows(Mitofus.id("sancho_link")), "swordsman does not start with Sancho's Swordsmanship (playtest 2026-11-17)");
        helper.assertTrue(SkillService.get(player).knows(Mitofus.id("heavy_strike")), "swordsman starts with Heavy Strike");
        helper.assertTrue(SkillService.get(player).learned().size() == 1, "and only with that one: " + SkillService.get(player).learned());
        helper.assertTrue(ProgressionService.effectiveStats(player).strength() == 6 + 3, "swordsman gives +3 STR over the base 6");
        helper.assertTrue(ProgressionService.get(player).strength() == 0, "bonuses are not allocated points");
        helper.succeed();
    }

    /** A class may declare no skills at all (the archer): choosing it teaches nothing and still works. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aClassWithoutSkillsTeachesNone(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        helper.assertTrue(ClassService.chooseClass(player, 0, Mitofus.id("archer")), "archer should be defined");
        helper.assertTrue(SkillService.get(player).learned().isEmpty(), "no skills: " + SkillService.get(player).learned());
        helper.assertTrue(ProgressionService.effectiveStats(player).agility() > 6, "but its bonus applies");
        helper.succeed();
    }

    /** A job is separate from the class: it has its own once-only choice, skills and smithing bonus. */
    @GameTest(template = TestSupport.EMPTY)
    public static void choosingJobGrantsSkillsAndSmithingBonus(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.chooseClass(player, 0, Mitofus.id("swordsman"));
        int before = ProgressionService.effectiveSmithingLevel(player);
        helper.assertTrue(ClassService.chooseJob(player, Mitofus.id("blacksmith")), "blacksmith job should be defined");
        helper.assertFalse(ClassService.chooseJob(player, Mitofus.id("tailor")), "a job can only be chosen once");
        helper.assertTrue(player.getData(ModAttachments.PLAYER_CLASSES).hasClass(Mitofus.id("swordsman")), "the class is untouched");
        helper.assertTrue(SkillService.get(player).knows(Mitofus.id("iron_guard")), "blacksmith starts with Iron Guard");
        helper.assertTrue(ProgressionService.effectiveSmithingLevel(player) == before + 5, "blacksmith gives +5 smithing levels");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void killsUnlockTitle(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        for (int i = 0; i < 10; i++) {
            TestSupport.killZombie(helper, player);
        }
        helper.assertTrue(player.getData(ModAttachments.PLAYER_IDENTITY).hasTitle(Mitofus.id("zombie_hunter")), "10 zombies earn the title");
        helper.assertTrue(ProgressionService.effectiveStats(player).strength() == 6 + 1, "zombie_hunter gives +1 STR over the base 6");
        helper.succeed();
    }

    /** The value of the modifier of {@code source}, or -1 if there is none. */
    private static double valueFrom(List<StatModifier> modifiers, StatSource source) {
        return modifiers.stream().filter(m -> m.source().equals(source)).mapToDouble(StatModifier::value).findFirst().orElse(-1);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void statModifiersNameEachSource(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ProgressionService.set(player, ProgressionService.get(player).with(StatType.STRENGTH, 2));
        ClassService.chooseClass(player, 0, Mitofus.id("swordsman"));
        for (int i = 0; i < 10; i++) {
            TestSupport.killZombie(helper, player);
        }
        List<StatModifier> strength = ProgressionService.modifiers(player).stream()
                .filter(m -> m.stat() == StatType.STRENGTH && m.value() != 0).toList();
        helper.assertTrue(strength.size() == 4, "base, allocated, swordsman and zombie_hunter: " + strength);
        StatSource classSource = new StatSource(Definitions.get(player.level(), ModDatapackRegistries.PLAYER_CLASS, Mitofus.id("swordsman"))
                .orElseThrow().name());
        StatSource titleSource = new StatSource(Definitions.get(player.level(), ModDatapackRegistries.TITLE, Mitofus.id("zombie_hunter"))
                .orElseThrow().name());
        helper.assertTrue(valueFrom(strength, StatSource.ALLOCATED) == 2, "2 allocated points: " + strength);
        helper.assertTrue(valueFrom(strength, classSource) == 3, "the class bonus is named after the class, swordsman +3: " + strength);
        helper.assertTrue(valueFrom(strength, titleSource) == 1, "the title bonus is named after the title, zombie_hunter +1: " + strength);
        helper.assertTrue(ProgressionService.effectiveStats(player).strength() == 6 + 6, "base 6 + 2 + 3 + 1 STR");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void forgingCountsForLowerTiers(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        TestSupport.forge(player, ItemRating.EPIC);
        PlayerCounters counters = player.getData(ModAttachments.PLAYER_COUNTERS);
        helper.assertTrue(counters.get("forge") == 1 && counters.get("forge/rare") == 1 && counters.get("forge/epic") == 1,
                "an epic forge counts for normal, rare and epic: " + counters);
        helper.assertTrue(counters.get("forge/unique") == 0, "but not for unique");
        helper.assertTrue(player.getData(ModAttachments.PLAYER_IDENTITY).hasTitle(Mitofus.id("epic_forger")), "epic forge earns the title");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void advancedJobCannotBeChosen(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        helper.assertFalse(ClassService.chooseJob(player, Mitofus.id("legendary_blacksmith")), "advanced jobs are quest-only");
        helper.assertTrue(ClassService.currentJob(player).isEmpty(), "no job should be set");
        helper.succeed();
    }
}
