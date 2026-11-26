package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.effect.status.StatusService;
import com.orijua.mitofus.feature.progression.ClassService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/** Synergies through statuses: each branch applies one status and takes advantage of another. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class BranchStatusTests {
    private static final ResourceLocation SWORD = Mitofus.id("swordsman");

    private BranchStatusTests() {
    }

    private static float hit(ServerPlayer attacker, Zombie target) {
        var event = new LivingIncomingDamageEvent(target, new DamageContainer(attacker.damageSources().playerAttack(attacker), 10.0f));
        NeoForge.EVENT_BUS.post(event);
        return event.getAmount();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aBranchHitsHarderAgainstTheStatusItTakesAdvantageOf(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.chooseClass(player, 0, SWORD);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(0.5, 1, 2.5));
        float plain = hit(player, zombie);
        helper.assertTrue(StatusService.apply(null, zombie, Mitofus.id("weaken"), 1.0, Optional.empty(), Optional.empty()), "the zombie is weakened");
        float weakened = hit(player, zombie);
        helper.assertTrue(Math.abs(weakened - plain) < 1e-4, "the swordsman takes advantage of slow, not weaken: " + plain + " vs " + weakened);
        zombie.removeAllEffects();
        helper.assertTrue(StatusService.apply(null, zombie, Mitofus.id("slow"), 1.0, Optional.empty(), Optional.empty()), "the zombie is slowed");
        float slowed = hit(player, zombie);
        helper.assertTrue(Math.abs(slowed - plain * 1.15f) < 1e-3, "+15 % against a slowed target: " + plain + " vs " + slowed);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void everyBranchHasItsApplierAndItsExploiter(GameTestHelper helper) {
        var level = helper.getLevel();
        for (String branch : new String[]{"swordsman", "spearman", "squire", "mage", "archer", "necromancer"}) {
            var definition = com.orijua.mitofus.core.registry.Definitions.get(level, com.orijua.mitofus.core.registry.ModDatapackRegistries.PLAYER_CLASS,
                    Mitofus.id(branch)).orElseThrow();
            long appliers = definition.passives().stream().filter(id -> id.getPath().startsWith("apply_")).count();
            long exploiters = definition.passives().stream().filter(id -> id.getPath().startsWith("exploit_")).count();
            helper.assertTrue(appliers == 1 && exploiters == 1, branch + " has one applier and one exploiter: " + definition.passives());
        }
        helper.assertTrue(com.orijua.mitofus.feature.effect.status.StatusValidator.problems(level.registryAccess()).isEmpty(), "statuses and passives are consistent");
        helper.succeed();
    }
}
