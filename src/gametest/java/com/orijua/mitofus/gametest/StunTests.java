package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.StunBalance;
import com.orijua.mitofus.core.registry.ModEffects;
import com.orijua.mitofus.feature.fatigue.FatigueRestraint;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A blow worth 40% of the victim's life stuns it. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class StunTests {
    private StunTests() {
    }

    private static Zombie zombie(GameTestHelper helper, double x) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(x, 1, 2.5));
        zombie.setNoAi(true);
        return zombie;
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aBigBlowStunsAndASmallOneDoesNot(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Zombie big = zombie(helper, 0.5);
        Zombie small = zombie(helper, 2.5);
        big.hurt(player.damageSources().playerAttack(player), 10f);
        small.hurt(player.damageSources().playerAttack(player), 2f);
        helper.assertTrue(big.hasEffect(ModEffects.STUNNED), "10 of 20 life: stunned");
        helper.assertFalse(small.hasEffect(ModEffects.STUNNED), "2 of 20 life: not stunned");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void onlyABlowFromACreatureStuns(GameTestHelper helper) {
        Zombie fall = zombie(helper, 0.5);
        Zombie thorns = zombie(helper, 2.5);
        Zombie attacker = zombie(helper, 4.5);
        fall.hurt(fall.damageSources().fall(), 12f);
        thorns.hurt(thorns.damageSources().thorns(attacker), 12f);
        helper.assertFalse(fall.hasEffect(ModEffects.STUNNED), "a fall is not a blow");
        helper.assertFalse(thorns.hasEffect(ModEffects.STUNNED), "thorns are not a blow");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aStunnedMobCannotMoveOrTarget(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Zombie zombie = zombie(helper, 0.5);
        helper.assertTrue(zombie.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0, "the zombie moves before");
        zombie.setTarget(player);
        zombie.hurt(player.damageSources().playerAttack(player), 10f);
        helper.assertTrue(zombie.getAttributeValue(Attributes.MOVEMENT_SPEED) <= 0.0001, "no speed while stunned");
        helper.assertTrue(zombie.getTarget() == null, "the stun drops its target");
        zombie.setTarget(player);
        helper.assertTrue(zombie.getTarget() == null, "and it cannot pick another while stunned");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aStunnedPlayerIsHeldStill(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        helper.assertFalse(FatigueRestraint.isRestrained(player), "free before");
        // A fake player is invulnerable, so the effect is given directly; the rule that gives it is covered with mobs above.
        player.addEffect(new MobEffectInstance(ModEffects.STUNNED, 40, 0));
        helper.assertTrue(FatigueRestraint.isRestrained(player), "a stunned player is held like in the grace period");
        player.removeEffect(ModEffects.STUNNED);
        helper.assertFalse(FatigueRestraint.isRestrained(player), "free again when it ends");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aSecondBlowDoesNotExtendTheStun(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Zombie zombie = zombie(helper, 0.5);
        zombie.hurt(player.damageSources().playerAttack(player), 10f);
        zombie.removeEffect(ModEffects.STUNNED);
        zombie.addEffect(new MobEffectInstance(ModEffects.STUNNED, 10, 0));
        zombie.setHealth(zombie.getMaxHealth());
        zombie.invulnerableTime = 0;
        zombie.hurt(player.damageSources().playerAttack(player), 10f);
        helper.assertTrue(zombie.getEffect(ModEffects.STUNNED).getDuration() <= 10, "the second blow leaves the timer alone");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void afterAStunTheVictimIsImmuneForAWhile(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Zombie zombie = zombie(helper, 0.5);
        zombie.hurt(player.damageSources().playerAttack(player), 10f);
        helper.assertTrue(zombie.hasEffect(ModEffects.STUNNED), "the first blow stuns");
        zombie.removeEffect(ModEffects.STUNNED);
        zombie.setHealth(zombie.getMaxHealth());
        zombie.invulnerableTime = 0;
        zombie.hurt(player.damageSources().playerAttack(player), 10f);
        helper.assertFalse(zombie.hasEffect(ModEffects.STUNNED), "a second blow while immune does not stun");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void zeroSecondsTurnsStunOff(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Zombie zombie = zombie(helper, 0.5);
        StunBalance.withOverride(new StunBalance(0.4, 0), () -> zombie.hurt(player.damageSources().playerAttack(player), 10f));
        helper.assertFalse(zombie.hasEffect(ModEffects.STUNNED), "duration 0: no stun");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theThresholdFollowsTheOverride(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Zombie zombie = zombie(helper, 0.5);
        StunBalance.withOverride(new StunBalance(0.8, 3), () -> zombie.hurt(player.damageSources().playerAttack(player), 10f));
        helper.assertFalse(zombie.hasEffect(ModEffects.STUNNED), "10 of 20 is under an 80% threshold");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void nothingIsStunnedInTheRealWorld(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Zombie zombie = zombie(helper, 0.5);
        VrHelmetTests.outsideMitofus(helper, () -> {
            zombie.hurt(player.damageSources().playerAttack(player), 10f);
            helper.assertFalse(zombie.hasEffect(ModEffects.STUNNED), "10 of 20 life in the real world: no stun");
            helper.assertTrue(zombie.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0, "and it keeps moving");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aStunnedPlayerIsNotHeldInTheRealWorldAndLosesTheStunWhenLeaving(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.addEffect(new MobEffectInstance(ModEffects.STUNNED, 200, 0));
        helper.assertTrue(FatigueRestraint.isRestrained(player), "held in Mitofus");
        VrHelmetTests.outsideMitofus(helper, () -> {
            helper.assertFalse(FatigueRestraint.isRestrained(player), "not held in the real world");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.NETHER, Level.OVERWORLD));
            helper.assertFalse(player.hasEffect(ModEffects.STUNNED), "arriving in the real world lifts the stun");
        });
        helper.succeed();
    }
}
