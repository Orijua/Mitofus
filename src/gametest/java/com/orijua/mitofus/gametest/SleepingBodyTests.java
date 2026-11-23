package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.vrhelmet.SleepingBody;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetService;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** The body that lies in the real world while a player is connected: an entity, not a block. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class SleepingBodyTests {
    private static final BlockPos AT = new BlockPos(0, 1, 1);

    private SleepingBodyTests() {
    }

    private static GlobalPos at(GameTestHelper helper) {
        return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(AT));
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theBodyIsAnEntityNotABlock(GameTestHelper helper) {
        TestSupport.lieBody(helper, AT, VrHelmetTier.DIAMOND);
        helper.assertTrue(helper.getBlockState(AT).isAir(), "no block is placed");
        List<SleepingBody> bodies = SleepingBody.at(helper.getLevel(), helper.absolutePos(AT));
        helper.assertTrue(bodies.size() == 1 && bodies.get(0).tier() == VrHelmetTier.DIAMOND, "one diamond body lies there");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void onlyACreativePlayerBreaksABodyAndTheHelmetDrops(GameTestHelper helper) {
        java.util.UUID owner = java.util.UUID.randomUUID();
        SleepingBody body = SleepingBody.spawn(helper.getLevel(), helper.absolutePos(AT), VrHelmetTier.ADVANCED, owner, 0.0f);
        Player survival = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertFalse(body.hurt(helper.getLevel().damageSources().playerAttack(survival), 100.0f), "a survival player cannot hurt it");
        helper.assertFalse(body.hurt(helper.getLevel().damageSources().generic(), 100.0f), "nor can the world");
        helper.assertTrue(body.isAlive(), "the body is still there");
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        helper.assertTrue(body.hurt(helper.getLevel().damageSources().playerAttack(creative), 1.0f), "a creative player breaks it");
        helper.assertFalse(body.isAlive(), "the body is gone");
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(3));
        helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().is(ModItems.ADVANCED_HELMET.get())
                && owner.equals(drops.get(0).getItem().get(ModDataComponents.VR_HELMET_OWNER.get())), "the helmet drops, bound to its owner");
        helper.succeed();
    }

    /** Logging out while connected takes the body away; logging in lies it down again, still connected. */
    @GameTest(template = TestSupport.EMPTY)
    public static void theBodyGoesAwayOnLogoutAndComesBackOnLogin(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        TestSupport.lieBody(helper, AT, VrHelmetTier.ADVANCED);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> TestSupport.withAllDefaults(() -> {
            com.orijua.mitofus.feature.fatigue.FatigueService.setEnergy(player, 1.0);
            helper.assertTrue(VrHelmetService.connect(player, at(helper)), "it connects");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
            helper.assertFalse(TestSupport.hasBody(helper, helper.absolutePos(AT)), "the body is gone with the player");
            helper.assertTrue(VrHelmetService.state(player).isConnected(), "but they are still connected");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
            List<SleepingBody> bodies = SleepingBody.at(helper.getLevel(), helper.absolutePos(AT));
            helper.assertTrue(bodies.size() == 1 && bodies.get(0).owner().equals(java.util.Optional.of(player.getUUID()))
                    && bodies.get(0).tier() == VrHelmetTier.ADVANCED, "the body lies down again, theirs");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
            helper.assertTrue(SleepingBody.at(helper.getLevel(), helper.absolutePos(AT)).size() == 1, "never twice");
            helper.assertTrue(VrHelmetService.disconnect(player, VrHelmetService.Reason.MANUAL), "disconnecting works");
            helper.assertFalse(TestSupport.hasBody(helper, helper.absolutePos(AT)), "the body is gone");
            helper.assertTrue(player.getInventory().countItem(ModItems.ADVANCED_HELMET.get()) == 1, "and the helmet is back");
        }));
        helper.succeed();
    }

    /** A body whose owner is online but not connected through it (a missed removal) removes itself. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aBodyNobodyIsConnectedThroughIsNotKept(GameTestHelper helper) {
        SleepingBody body = SleepingBody.spawn(helper.getLevel(), helper.absolutePos(AT), VrHelmetTier.BASIC, java.util.UUID.randomUUID(), 0.0f);
        // An owner that is not online is left alone (the server may still be starting).
        for (int i = 0; i < 25; i++) {
            body.tick();
        }
        helper.assertTrue(body.isAlive(), "an offline owner's body stays");
        helper.succeed();
    }
}
