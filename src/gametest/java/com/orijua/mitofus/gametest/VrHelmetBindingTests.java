package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.VrHelmetBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModBlocks;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetBlockEntity;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetService;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetService.Begin;
import com.orijua.mitofus.feature.character.CharacterVault;
import com.orijua.mitofus.feature.character.WorldSide;
import com.orijua.mitofus.feature.economy.VrHelmetUnbind;
import com.orijua.mitofus.feature.economy.VrHelmetUnbind.Result;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/** VrHelmets bound to their owner: bound on the first use, closed to everybody else, kept in the item, unbound at the terminal. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class VrHelmetBindingTests {
    private VrHelmetBindingTests() {
    }

    private static final BlockPos VR_HELMET = new BlockPos(0, 1, 1);

    /** The real world is the test level and another level plays Mitofus, so lying down is possible; then the body runs. */
    private static void lying(GameTestHelper helper, Runnable body) {
        ServerLevel other = VrHelmetTests.otherLevel(helper);
        if (other == null) {
            helper.fail("BLOCKED: no second level to play Mitofus");
            return;
        }
        try {
            MitofusWorld.standIn(other);
            body.run();
        } finally {
            MitofusWorld.restoreStandIn();
        }
    }

    private static Optional<java.util.UUID> ownerOf(GameTestHelper helper) {
        return ((VrHelmetBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(VR_HELMET))).owner();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theVrHelmetBindsOnFirstUseAndRejectsOthers(GameTestHelper helper) {
        helper.setBlock(VR_HELMET, ModBlocks.BODY.get());
        ServerPlayer owner = TestSupport.fakePlayer(helper);
        ServerPlayer stranger = TestSupport.fakePlayer(helper);
        lying(helper, () -> {
            BlockPos pos = helper.absolutePos(VR_HELMET);
            helper.assertTrue(ownerOf(helper).isEmpty(), "nobody owns a new vrhelmet");
            helper.assertTrue(VrHelmetService.begin(owner, pos) == Begin.STARTED, "the first player lies down");
            helper.assertTrue(ownerOf(helper).equals(Optional.of(owner.getUUID())), "and becomes the owner");
            VrHelmetService.cancel(owner, false);
            helper.assertTrue(VrHelmetService.begin(stranger, pos) == Begin.NOT_YOURS, "another player is rejected");
            helper.assertFalse(VrHelmetService.isConnecting(stranger), "and nothing starts");
            helper.assertTrue(VrHelmetService.begin(owner, pos) == Begin.STARTED, "the owner connects again");
            VrHelmetService.cancel(owner, false);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aBrokenBoundVrHelmetKeepsItsOwnerInTheItem(GameTestHelper helper) {
        helper.setBlock(VR_HELMET, ModBlocks.ADVANCED_BODY.get());
        BlockPos pos = helper.absolutePos(VR_HELMET);
        java.util.UUID owner = java.util.UUID.randomUUID();
        ((VrHelmetBlockEntity) helper.getLevel().getBlockEntity(pos)).bind(owner);
        LootParams.Builder params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .withParameter(LootContextParams.BLOCK_STATE, helper.getLevel().getBlockState(pos))
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, helper.getLevel().getBlockEntity(pos));
        List<ItemStack> drops = helper.getLevel().getBlockState(pos).getDrops(params);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(ModItems.ADVANCED_HELMET.get()), "the vrhelmet drops: " + drops);
        helper.assertTrue(owner.equals(drops.get(0).get(ModDataComponents.VR_HELMET_OWNER.get())), "with its owner");
        // Placing it again keeps the owner.
        helper.setBlock(new BlockPos(1, 1, 1), ModBlocks.ADVANCED_BODY.get());
        VrHelmetBlockEntity placed = (VrHelmetBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)));
        helper.assertTrue(placed.owner().isEmpty(), "a fresh block has none");
        helper.succeed();
    }

    private static ServerPlayer terminalUser(GameTestHelper helper, long iron) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setData(ModAttachments.CHARACTER_VAULT.get(), new CharacterVault(Optional.of(WorldSide.REAL), Optional.empty()));
        TestSupport.setIron(player, iron);
        return player;
    }

    private static ItemStack bound(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.VR_HELMET_OWNER.get(), java.util.UUID.randomUUID());
        return stack;
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void unbindingChargesTheTierPriceAndLeavesItWithoutOwner(GameTestHelper helper) {
        VrHelmetTests.outsideMitofus(helper, () -> {
            ServerPlayer player = terminalUser(helper, 10_000L);
            ItemStack vrhelmet = bound(ModItems.ADVANCED_HELMET.get());
            helper.assertTrue(VrHelmetUnbind.unbind(player, vrhelmet, VrHelmetBalance.DEFAULT) == Result.UNBOUND, "unbound");
            helper.assertTrue(com.orijua.mitofus.feature.economy.Wallet.purse(player).iron() == 10_000L - 2_000L, "advanced costs 200 % of 1000");
            helper.assertFalse(vrhelmet.has(ModDataComponents.VR_HELMET_OWNER.get()), "no owner left");
            ItemStack basic = bound(ModItems.HELMET.get());
            helper.assertTrue(VrHelmetUnbind.unbind(player, basic, VrHelmetBalance.DEFAULT) == Result.UNBOUND, "the basic one too");
            helper.assertTrue(com.orijua.mitofus.feature.economy.Wallet.purse(player).iron() == 10_000L - 2_000L - 700L, "basic uses the reference price of 350");
            helper.assertTrue(VrHelmetUnbind.unbind(player, basic, VrHelmetBalance.DEFAULT) == Result.NOT_BOUND, "an unbound one is not charged");
            helper.assertTrue(com.orijua.mitofus.feature.economy.Wallet.purse(player).iron() == 10_000L - 2_000L - 700L, "nothing more charged");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void unbindingNeedsTheMoneyAndIsFreeAtZeroPercent(GameTestHelper helper) {
        VrHelmetTests.outsideMitofus(helper, () -> {
            ServerPlayer poor = terminalUser(helper, 1_999L);
            ItemStack vrhelmet = bound(ModItems.ADVANCED_HELMET.get());
            helper.assertTrue(VrHelmetUnbind.unbind(poor, vrhelmet, VrHelmetBalance.DEFAULT) == Result.NOT_ENOUGH, "one iron short");
            helper.assertTrue(vrhelmet.has(ModDataComponents.VR_HELMET_OWNER.get()) && com.orijua.mitofus.feature.economy.Wallet.purse(poor).iron() == 1_999L, "nothing changed");
            VrHelmetBalance free = new VrHelmetBalance(55, 8, 75, 6, 3000, 15000, 10, 10, 0.9, 30, 0, 1000);
            helper.assertTrue(VrHelmetUnbind.unbind(poor, vrhelmet, free) == Result.UNBOUND && com.orijua.mitofus.feature.economy.Wallet.purse(poor).iron() == 1_999L, "free at 0 %");
        });
        helper.succeed();
    }
}
