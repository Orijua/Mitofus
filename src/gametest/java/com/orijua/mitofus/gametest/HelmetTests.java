package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModBlocks;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetBlock;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetBlockEntity;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetRules;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetService;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetService.Begin;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetTier;
import com.orijua.mitofus.feature.fatigue.FatigueService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.UUID;

/** The connection helmet: putting it on lays the body down where you stand, and the body goes away (returning the helmet) when you leave. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class HelmetTests {
    private HelmetTests() {
    }

    /** The real world is the test level and another level plays Mitofus, so putting the helmet on is possible. */
    private static void lying(GameTestHelper helper, Runnable body) {
        net.minecraft.server.level.ServerLevel other = VrHelmetTests.otherLevel(helper);
        if (other == null) {
            helper.fail("BLOCKED: no second level to play Mitofus");
            return;
        }
        try {
            com.orijua.mitofus.feature.world.MitofusWorld.standIn(other);
            TestSupport.withAllDefaults(body);
        } finally {
            com.orijua.mitofus.feature.world.MitofusWorld.restoreStandIn();
        }
    }

    private static BlockPos bodyPos(GameTestHelper helper, ServerPlayer player) {
        return player.blockPosition();
    }

    private static void onSolidGround(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().setBlock(player.blockPosition().below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(player.blockPosition(), Blocks.AIR.defaultBlockState(), 3);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void puttingTheHelmetOnLaysTheBodyDown(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        onSolidGround(helper, player);
        FatigueService.setEnergy(player, 1.0);
        lying(helper, () -> {
            ItemStack helmet = new ItemStack(ModItems.ADVANCED_HELMET.get());
            BlockPos pos = bodyPos(helper, player);
            helper.assertTrue(VrHelmetService.putOn(player, helmet, VrHelmetTier.ADVANCED) == Begin.STARTED, "the connection starts");
            helper.assertTrue(helper.getLevel().getBlockState(pos).getBlock() == ModBlocks.ADVANCED_BODY.get(), "the body lies where the player stood");
            helper.assertTrue(helmet.isEmpty(), "the helmet went onto the body");
            helper.assertTrue(((VrHelmetBlockEntity) helper.getLevel().getBlockEntity(pos)).owner().equals(Optional.of(player.getUUID())), "and it is bound to the player");
            VrHelmetService.cancel(player, false);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void disconnectingTakesTheBodyAwayAndGivesTheHelmetBack(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlockPos at = new BlockPos(0, 1, 1);
        helper.setBlock(at, ModBlocks.ADVANCED_BODY.get());
        ((VrHelmetBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(at))).bind(player.getUUID());
        FatigueService.setEnergy(player, 1.0);
        BlockPos landing = helper.absolutePos(new BlockPos(1, 1, 1));
        VrHelmetTests.inMitofus(helper, landing, () -> TestSupport.withAllDefaults(() -> {
            helper.assertTrue(VrHelmetService.connect(player, net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(at))), "it connects");
            helper.assertTrue(VrHelmetService.disconnect(player, VrHelmetService.Reason.MANUAL), "disconnecting works");
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(at)).isAir(), "the body is gone");
            helper.assertTrue(player.getInventory().countItem(ModItems.ADVANCED_HELMET.get()) == 1, "the helmet is back in the inventory");
            ItemStack back = ItemStack.EMPTY;
            for (ItemStack stack : player.getInventory().items) {
                if (stack.is(ModItems.ADVANCED_HELMET.get())) {
                    back = stack;
                }
            }
            helper.assertTrue(player.getUUID().equals(back.get(ModDataComponents.VR_HELMET_OWNER.get())), "still bound to its owner");
        }));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void cancellingTheWaitGivesTheHelmetBack(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        onSolidGround(helper, player);
        FatigueService.setEnergy(player, 1.0);
        lying(helper, () -> {
            ItemStack helmet = new ItemStack(ModItems.HELMET.get());
            BlockPos pos = bodyPos(helper, player);
            helper.assertTrue(VrHelmetService.putOn(player, helmet, VrHelmetTier.BASIC) == Begin.STARTED, "the connection starts");
            VrHelmetService.cancel(player, false);
            helper.assertFalse(VrHelmetService.isConnecting(player), "the wait is over");
            helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "the body is gone");
            helper.assertTrue(player.getInventory().countItem(ModItems.HELMET.get()) == 1, "and the helmet is back");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aHelmetOfAnotherPlayerDoesNothing(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        onSolidGround(helper, player);
        FatigueService.setEnergy(player, 1.0);
        lying(helper, () -> {
            ItemStack helmet = new ItemStack(ModItems.HELMET.get());
            helmet.set(ModDataComponents.VR_HELMET_OWNER.get(), UUID.randomUUID());
            helper.assertTrue(VrHelmetService.putOn(player, helmet, VrHelmetTier.BASIC) == Begin.NOT_YOURS, "it is not theirs");
            helper.assertTrue(helmet.getCount() == 1, "the helmet stays in hand");
            helper.assertTrue(helper.getLevel().getBlockState(bodyPos(helper, player)).isAir(), "and no body is left");
            helper.assertFalse(VrHelmetService.isConnecting(player), "nothing starts");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void thereMustBeRoomToLieDown(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        onSolidGround(helper, player);
        FatigueService.setEnergy(player, 1.0);
        lying(helper, () -> {
            helper.getLevel().setBlock(bodyPos(helper, player), Blocks.STONE.defaultBlockState(), 3);
            ItemStack helmet = new ItemStack(ModItems.HELMET.get());
            helper.assertTrue(VrHelmetService.putOn(player, helmet, VrHelmetTier.BASIC) == Begin.NO_ROOM, "no room in a solid block");
            helper.assertTrue(helmet.getCount() == 1, "the helmet stays in hand");
        });
        helper.succeed();
    }
}
