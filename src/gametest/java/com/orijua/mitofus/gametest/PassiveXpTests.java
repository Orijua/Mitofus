package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.config.PassiveXpBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModEntities;
import com.orijua.mitofus.feature.progression.PlayerStats;
import com.orijua.mitofus.feature.progression.ProgressionEvents;
import com.orijua.mitofus.feature.progression.ProgressionEvents.KillKind;
import com.orijua.mitofus.feature.progression.ProgressionRules;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.quest.goal.KillGoal;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/** What a kill is worth: peaceful mobs give less XP with the level, villagers and the mod's NPCs none, the rest all of it. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class PassiveXpTests {
    private PassiveXpTests() {
    }

    private static void assertKind(GameTestHelper helper, EntityType<? extends LivingEntity> type, KillKind expected) {
        LivingEntity entity = type.create(helper.getLevel());
        helper.assertTrue(entity != null, type + " can be created");
        KillKind kind = ProgressionEvents.kindOf(entity);
        helper.assertTrue(kind == expected, type + ": expected " + expected + ", got " + kind);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void peacefulMobsAreClassifiedPassive(GameTestHelper helper) {
        for (EntityType<? extends LivingEntity> type : List.of(
                EntityType.COW, EntityType.BAT, EntityType.COD, EntityType.SQUID, EntityType.SNOW_GOLEM)) {
            assertKind(helper, type, KillKind.PASSIVE);
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void monstersNeutralsAndBossesAreFull(GameTestHelper helper) {
        for (EntityType<? extends LivingEntity> type : List.of(
                EntityType.ZOMBIE, EntityType.WOLF, EntityType.IRON_GOLEM, EntityType.BEE,
                ModEntities.LEVANTE_THUG.get(), ModEntities.CONDE_NUNO.get(), ModEntities.BONE_LORD.get())) {
            assertKind(helper, type, KillKind.FULL);
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void villagersAndTheModsNpcsGiveNoXp(GameTestHelper helper) {
        for (EntityType<? extends LivingEntity> type : List.of(
                EntityType.VILLAGER, EntityType.WANDERING_TRADER, ModEntities.BLACKSMITH.get(), ModEntities.VILLAGE_CHIEF.get(),
                ModEntities.APPRENTICE_SMITH.get(), ModEntities.VELASCO.get(), ModEntities.MENCIA.get())) {
            assertKind(helper, type, KillKind.NO_XP);
        }
        helper.succeed();
    }

    private static ServerPlayer atLevel(GameTestHelper helper, int level) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ProgressionService.set(player, PlayerStats.DEFAULT.withProgress(level, 0, 0));
        return player;
    }

    /** Kills a mob for real (a player hit that kills it) and returns the XP it gave. */
    private static int killXp(GameTestHelper helper, ServerPlayer player, EntityType<? extends LivingEntity> type) {
        LivingEntity mob = helper.spawn(type, 1, 2, 1);
        int before = ProgressionService.get(player).xp();
        mob.hurt(player.damageSources().playerAttack(player), 10_000.0f);
        helper.assertTrue(mob.isDeadOrDying(), type + " died");
        return ProgressionService.get(player).xp() - before;
    }

/** The XP a normal kill of this mob gives, from the real entity (its health, attack, armor, spawner and elite flags). */
private static int baseXp(LivingEntity mob) {
    return ProgressionRules.killXp(Balance.current(), new ProgressionRules.Victim(
            mob.getMaxHealth(),
            mob.getAttribute(Attributes.ATTACK_DAMAGE) != null ? mob.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0.0,
            mob.getArmorValue(),
            mob.getData(ModAttachments.FROM_SPAWNER),
            mob.getData(ModAttachments.MOB_LEVEL).elite()));
}

private static int cowBaseXp(GameTestHelper helper) {
    LivingEntity cow = EntityType.COW.create(helper.getLevel());
    helper.assertTrue(cow != null, "a cow can be created");
    return baseXp(cow);
}

private static int cowXp(GameTestHelper helper, ServerPlayer player) {
        return killXp(helper, player, EntityType.COW);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aCowGivesLessAsYouLevel(GameTestHelper helper) {
        int base = cowBaseXp(helper);
        helper.assertTrue(base > 2, "a normal kill of a cow gives more than 2 XP: " + base);
        int atTen = cowXp(helper, atLevel(helper, 10));
        helper.assertTrue(atTen == base, "level 10: the base XP " + base + ", got " + atTen);
        int atTwenty = cowXp(helper, atLevel(helper, 20));
        int expectedTwenty = ProgressionRules.passiveKillXp(PassiveXpBalance.DEFAULT, base, 20);
        helper.assertTrue(atTwenty == expectedTwenty && atTwenty < base && atTwenty > 1,
                "level 20: halfway, " + expectedTwenty + ", got " + atTwenty);
        int atThirty = cowXp(helper, atLevel(helper, 30));
        helper.assertTrue(atThirty == 1, "level 30: 1, got " + atThirty);
        int atFifty = cowXp(helper, atLevel(helper, 50));
        helper.assertTrue(atFifty == 1, "level 50: 1, got " + atFifty);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theRestedBonusNeverTurnsOneIntoMore(GameTestHelper helper) {
        ServerPlayer player = atLevel(helper, 30);
        player.setData(ModAttachments.RESTED_SECONDS.get(), 300);
        int gained = cowXp(helper, player);
        helper.assertTrue(gained == 1, "level 30 rested: " + gained);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void monstersAndNeutralsKeepTheirFullXpAtAnyLevel(GameTestHelper helper) {
        for (EntityType<? extends LivingEntity> type : List.of(EntityType.ZOMBIE, EntityType.WOLF)) {
            ServerPlayer player = atLevel(helper, 30);
            LivingEntity mob = helper.spawn(type, 1, 2, 1);
            int expected = baseXp(mob);
            int before = ProgressionService.get(player).xp();
            mob.hurt(player.damageSources().playerAttack(player), 10_000.0f);
            int gained = ProgressionService.get(player).xp() - before;
            helper.assertTrue(expected > 1 && gained == expected, type + ": expected " + expected + ", got " + gained);
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aVillagerGivesNoXpAndDoesNotCountAsAKill(GameTestHelper helper) {
        ServerPlayer player = atLevel(helper, 1);
        String key = KillGoal.counterKey(Optional.empty());
        int before = player.getData(ModAttachments.PLAYER_COUNTERS).get(key);
        int gained = killXp(helper, player, EntityType.VILLAGER);
        helper.assertTrue(gained == 0, "no XP: " + gained);
        helper.assertTrue(player.getData(ModAttachments.PLAYER_COUNTERS).get(key) == before, "and no kill counted");
        // Control: a cow does count.
        cowXp(helper, player);
        helper.assertTrue(player.getData(ModAttachments.PLAYER_COUNTERS).get(key) == before + 1, "a cow counts as a kill");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theCurveFollowsTheConfig(GameTestHelper helper) {
        PassiveXpBalance.withOverride(new PassiveXpBalance(0, 5), () -> {
            int base = cowBaseXp(helper);
            helper.assertTrue(base > 2, "base " + base);
            int atTwo = cowXp(helper, atLevel(helper, 2));
            int expected = ProgressionRules.passiveKillXp(new PassiveXpBalance(0, 5), base, 2);
            helper.assertTrue(atTwo == expected && atTwo > 1 && atTwo < base, "(0,5) at level 2: expected " + expected + ", got " + atTwo);
            int gained = cowXp(helper, atLevel(helper, 5));
            helper.assertTrue(gained == 1, "(0,5) at level 5: " + gained);
        });
        helper.succeed();
    }
}
