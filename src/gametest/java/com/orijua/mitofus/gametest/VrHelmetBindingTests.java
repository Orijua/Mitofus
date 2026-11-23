package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.VrHelmetBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.character.CharacterVault;
import com.orijua.mitofus.feature.character.WorldSide;
import com.orijua.mitofus.feature.economy.VrHelmetUnbind;
import com.orijua.mitofus.feature.economy.VrHelmetUnbind.Result;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/** VrHelmets bound to their owner: bound on the first use, closed to everybody else, kept in the item, unbound at the terminal. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class VrHelmetBindingTests {
    private VrHelmetBindingTests() {
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
