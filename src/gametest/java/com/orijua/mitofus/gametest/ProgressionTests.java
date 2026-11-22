package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.progression.PlayerStats;
import com.orijua.mitofus.feature.progression.ProgressionRules;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.progression.StatEffects;
import com.orijua.mitofus.feature.progression.StatType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Stats applied to a real player. The XP and point formulas are unit tests (ProgressionRulesTest). */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class ProgressionTests {
    private ProgressionTests() {
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void staminaRaisesMaxHealth(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        StatEffects.apply(player, PlayerStats.DEFAULT.with(StatType.STAMINA, 4));
        TestSupport.assertClose(helper, 21.0, player.getMaxHealth(), "max health with 4 STA (+0.25 each)");
        StatEffects.apply(player, PlayerStats.DEFAULT);
        TestSupport.assertClose(helper, 20.0, player.getMaxHealth(), "max health after reset");
        helper.succeed();
    }

    /** Playtest 2026-11-17: gaining a level heals fully and refills mana; XP that does not level up changes neither. */
    @GameTest(template = TestSupport.EMPTY)
    public static void levelingUpHealsAndRefillsMana(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        float maxMana = ProgressionRules.maxMana(Balance.current(), ProgressionService.effectiveStats(player).intelligence());
        player.setHealth(3.0f);
        player.setData(ModAttachments.MANA, 1.0f);
        ProgressionService.awardXp(player, 1);
        helper.assertTrue(ProgressionService.get(player).level() == 1, "one XP is not a level");
        helper.assertTrue(player.getHealth() == 3.0f && player.getData(ModAttachments.MANA) == 1.0f, "no level, no refill");
        ProgressionService.awardXp(player, 100_000);
        helper.assertTrue(ProgressionService.get(player).level() > 1, "the player leveled up");
        helper.assertTrue(player.getHealth() == player.getMaxHealth(), "full health: " + player.getHealth() + " of " + player.getMaxHealth());
        maxMana = ProgressionRules.maxMana(Balance.current(), ProgressionService.effectiveStats(player).intelligence());
        TestSupport.assertClose(helper, maxMana, player.getData(ModAttachments.MANA), "full mana");
        helper.succeed();
    }

    /** A mob the player hit that then dies of something else (fire, a fall) still counts as his kill (playtest). */
    @GameTest(template = TestSupport.EMPTY)
    public static void aKillByFireAfterYourHitIsYours(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
        int before = ProgressionService.get(player).xp();
        zombie.hurt(player.damageSources().playerAttack(player), 1.0f);
        zombie.hurt(helper.getLevel().damageSources().onFire(), 1000.0f);
        helper.assertTrue(zombie.isDeadOrDying(), "it burned");
        helper.assertTrue(ProgressionService.get(player).xp() > before, "and the XP is his");

        Zombie untouched = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
        int after = ProgressionService.get(player).xp();
        untouched.hurt(helper.getLevel().damageSources().onFire(), 1000.0f);
        helper.assertTrue(ProgressionService.get(player).xp() == after, "one he never hit is nobody's");
        helper.succeed();
    }

    /** The smithing XP saturates at Integer.MAX_VALUE instead of overflowing into a negative number. */
    @GameTest(template = TestSupport.EMPTY)
    public static void smithingXpSaturatesInsteadOfOverflowing(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ProgressionService.set(player, ProgressionService.get(player).withSmithingXp(Integer.MAX_VALUE - 5));
        ProgressionService.addSmithingXp(player, 100);
        helper.assertTrue(ProgressionService.get(player).smithingXp() == Integer.MAX_VALUE,
                "MAX-5 plus 100 stays at MAX_VALUE: " + ProgressionService.get(player).smithingXp());
        ProgressionService.addSmithingXp(player, 100);
        helper.assertTrue(ProgressionService.get(player).smithingXp() == Integer.MAX_VALUE, "and keeps there");
        helper.succeed();
    }
}
