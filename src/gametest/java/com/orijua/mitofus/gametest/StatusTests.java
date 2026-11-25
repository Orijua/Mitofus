package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.core.registry.ModEffects;
import com.orijua.mitofus.feature.effect.ActiveTraits;
import com.orijua.mitofus.feature.effect.BonusLimits;
import com.orijua.mitofus.feature.effect.BonusType;
import com.orijua.mitofus.feature.effect.status.StatusService;
import com.orijua.mitofus.feature.form.FormService;
import com.orijua.mitofus.feature.progression.ClassService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/** The central status system (chance, intensity, duration, renewal, immunity), the global caps and the passives that use them. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class StatusTests {
    private static final ResourceLocation BURN = Mitofus.id("burn");
    private static final ResourceLocation SHOCK = Mitofus.id("shock");
    private static final ResourceLocation SWORD = Mitofus.id("swordsman");

    private StatusTests() {
    }

    private static Zombie zombie(GameTestHelper helper) {
        return helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void everyKindOfBonusHasAGlobalCapAndTheStatusesLoad(GameTestHelper helper) {
        for (BonusType type : BonusType.values()) {
            helper.assertTrue(BonusLimits.cap(type).isPresent(), "a cap in data for " + type.getSerializedName());
        }
        helper.assertTrue(Definitions.all(helper.getLevel(), ModDatapackRegistries.STATUS).size() == 6, "six statuses");
        helper.assertTrue(BonusLimits.cap(BonusType.CRIT_CHANCE).orElseThrow() == 0.5, "crit chance cap");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aStatusNeverComesWithZeroChanceAndAlwaysWithTheMaximumRolled(GameTestHelper helper) {
        Zombie target = zombie(helper);
        helper.assertFalse(StatusService.apply(null, target, BURN, 0.0, Optional.empty(), Optional.empty()), "chance 0 never lands");
        helper.assertFalse(target.hasEffect(ModEffects.BURNING), "nothing given");
        boolean landed = false;
        for (int i = 0; i < 200 && !landed; i++) {
            landed = StatusService.apply(null, target, BURN, 5.0, Optional.empty(), Optional.empty());
        }
        helper.assertTrue(landed, "a high chance lands (kept at the status's maximum, 75%)");
        helper.assertTrue(target.hasEffect(ModEffects.BURNING), "burning");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void givingTheSameStatusAgainRenewsItAndNeverStacksIntensity(GameTestHelper helper) {
        Zombie target = zombie(helper);
        target.addEffect(new MobEffectInstance(ModEffects.BURNING, 20, 1));
        boolean landed = false;
        for (int i = 0; i < 200 && !landed; i++) {
            landed = StatusService.apply(null, target, BURN, 1.0, Optional.of(1), Optional.of(300));
        }
        MobEffectInstance burning = target.getEffect(ModEffects.BURNING);
        helper.assertTrue(burning.getDuration() > 200 && burning.getAmplifier() == 0, "renewed to the new duration and intensity: " + burning);
        landed = false;
        for (int i = 0; i < 200 && !landed; i++) {
            landed = StatusService.apply(null, target, BURN, 1.0, Optional.of(10), Optional.of(100000));
        }
        burning = target.getEffect(ModEffects.BURNING);
        helper.assertTrue(burning.getAmplifier() == 2 && burning.getDuration() <= 400, "kept under the maximums: " + burning);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aShockHasAnImmunityWindowAfterIt(GameTestHelper helper) {
        Zombie target = zombie(helper);
        boolean landed = false;
        for (int i = 0; i < 400 && !landed; i++) {
            landed = StatusService.apply(null, target, SHOCK, 1.0, Optional.empty(), Optional.empty());
        }
        helper.assertTrue(landed && target.hasEffect(ModEffects.STUNNED), "stunned");
        target.removeEffect(ModEffects.STUNNED);
        boolean again = false;
        for (int i = 0; i < 400 && !again; i++) {
            again = StatusService.apply(null, target, SHOCK, 1.0, Optional.empty(), Optional.empty());
        }
        helper.assertFalse(again, "inside the window it cannot be stunned again");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theFormPassivesNowUseTheirRealEffects(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.chooseClass(player, 0, SWORD);
        ClassService.setClassLevel(player, 0, 500);
        FormService.forceEquip(player, Mitofus.id("tidebreaker"));
        FormService.setLevel(player, Mitofus.id("tidebreaker"), 30);
        helper.assertTrue(ActiveTraits.total(player, BonusType.HEALTH_REGEN) == 0.5, "health regeneration of Flowing Guard");
        FormService.forceEquip(player, Mitofus.id("thunder_edge"));
        FormService.setLevel(player, Mitofus.id("thunder_edge"), 30);
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_SPEED) - 4.4) < 1e-6, "attack speed of Static Charge: " + player.getAttributeValue(Attributes.ATTACK_SPEED));
        FormService.forceUnequip(player);
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_SPEED) - 4.0) < 1e-6, "back to normal without the form");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void searingEdgeBurnsWhatYouHit(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.chooseClass(player, 0, SWORD);
        ClassService.setClassLevel(player, 0, 50);
        FormService.forceEquip(player, Mitofus.id("ember_blade"));
        FormService.setLevel(player, Mitofus.id("ember_blade"), 30);
        Zombie target = zombie(helper);
        target.setHealth(target.getMaxHealth());
        boolean burning = false;
        for (int i = 0; i < 300 && !burning; i++) {
            target.setHealth(target.getMaxHealth());
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), 1.0f);
            burning = target.hasEffect(ModEffects.BURNING);
        }
        helper.assertTrue(burning, "a hit with the form's passive burns the target");
        helper.succeed();
    }
}
