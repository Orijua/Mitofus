package com.orijua.mitofus.feature.npc;

import com.orijua.mitofus.feature.economy.CarriedItems;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.economy.Money;
import com.orijua.mitofus.feature.economy.Wallet;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

/**
 * Smith's shop, on the server: opens the window and closes deals. The payment is counted over the whole inventory (slots 0
 * to 35 and the offhand, not armor, like the terminal's work order), so a price is not limited to one stack. It always reads
 * the real cost from {@code getItemCostA()} / {@code getItemCostB()}: {@code getCostA()} and {@code satisfiedBy()} cap a cost at
 * the stack size, which would let a price above 64 be paid with less. Nothing moves before everything is checked.
 */
public final class SmithShop {
    private SmithShop() {
    }

    /** What happened to an order. */
    public enum Result {
        OK, OUTSIDE_MITOFUS, NOT_TRADING, STALE, SOLD_OUT, NOT_ENOUGH
    }

    /** Opens the shop window for {@code player}: the offers go with the opening packet. */
    public static void open(ServerPlayer player, BlacksmithNpc smith) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new SmithShopMenu(id, inventory, smith), smith.getDisplayName()),
                buf -> MerchantOffers.STREAM_CODEC.encode(buf, smith.getOffers()));
    }

    /** Sends the current offers and a message to the window of {@code menu}. */
    public static void sendState(ServerPlayer player, SmithShopMenu menu, Optional<Component> message) {
        MerchantOffers offers = menu.offers();
        PacketDistributor.sendToPlayer(player, new SmithShopStatePayload(menu.containerId, offers, message));
    }

    /**
     * Closes deal {@code index} for {@code player}, who saw it give {@code seenResult} for {@code seenCostA} of the first cost
     * and {@code seenCostB} of the second (0 if it has none).
     * Checked in this order: Mitofus, that Smith trades with this player, the index, what was seen, and the money.
     */
    public static Result buy(ServerPlayer player, BlacksmithNpc smith, int index, ItemStack seenResult, int seenCostA, int seenCostB) {
        if (!MitofusWorld.is(player.level())) {
            return Result.OUTSIDE_MITOFUS;
        }
        if (smith.getTradingPlayer() != player) {
            return Result.NOT_TRADING;
        }
        MerchantOffers offers = smith.getOffers();
        if (index < 0 || index >= offers.size()) {
            return Result.STALE;
        }
        MerchantOffer offer = offers.get(index);
        ItemCost costA = offer.getItemCostA();
        Optional<ItemCost> costB = offer.getItemCostB();
        if (!ItemStack.matches(offer.getResult(), seenResult) || costA.count() != seenCostA
                || costB.map(ItemCost::count).orElse(0) != seenCostB) {
            return Result.STALE;
        }
        Inventory inventory = player.getInventory();
        SmithShopRules.Check check = SmithShopRules.check(offer.isOutOfStock(),
                count(inventory, costA), costA.count(),
                costB.map(cost -> count(inventory, cost)).orElse(0L), costB.map(ItemCost::count).orElse(0));
        switch (check) {
            case SOLD_OUT -> {
                return Result.SOLD_OUT;
            }
            case NOT_ENOUGH -> {
                return Result.NOT_ENOUGH;
            }
            case OK -> {
            }
        }
        take(inventory, costA, costA.count());
        costB.ifPresent(cost -> take(inventory, cost, cost.count()));
        give(player, offer.getResult().copy());
        smith.notifyTrade(offer);
        player.inventoryMenu.sendAllDataToRemote();
        return Result.OK;
    }

    /** The message for the player after an order; empty when the window is closing anyway. */
    public static Optional<Component> message(Result result, ItemStack got) {
        return switch (result) {
            case OK -> Optional.of(got.getCount() > 1
                    ? Component.translatable("message.mitofus.smith_shop.bought_many", Money.format(got.getCount()), got.getHoverName())
                    : Component.translatable("message.mitofus.smith_shop.bought", got.getHoverName()));
            case NOT_ENOUGH -> Optional.of(Component.translatable("message.mitofus.smith_shop.not_enough"));
            case SOLD_OUT -> Optional.of(Component.translatable("message.mitofus.smith_shop.sold_out"));
            case STALE -> Optional.of(Component.translatable("message.mitofus.smith_shop.stale"));
            case OUTSIDE_MITOFUS, NOT_TRADING -> Optional.empty();
        };
    }

    /** How many items matching {@code cost} the inventory holds: slots 0 to 35 and the offhand, not armor. */
    public static long count(Inventory inventory, ItemCost cost) {
        if (isGold(cost)) {
            // The gold is the purse (a balance), not coin items: in whole gold, for the shop's prices.
            return Wallet.balance(inventory.player) / Money.IRON_PER_GOLD;
        }
        return CarriedItems.count(inventory, cost::test);
    }

    private static boolean isGold(ItemCost cost) {
        return cost.test(new ItemStack(ModItems.GOLD_COIN.get()));
    }

    /** Takes exactly {@code amount}: slots 0 to 35 in order, then the offhand. Only called when there is enough. */
    private static void take(Inventory inventory, ItemCost cost, int amount) {
        if (isGold(cost) && inventory.player instanceof ServerPlayer player) {
            Wallet.pay(player, (long) amount * Money.IRON_PER_GOLD);
            return;
        }
        CarriedItems.take(inventory, cost::test, amount);
    }

    /** Puts the result in the inventory; what does not fit falls on the ground. */
    private static void give(ServerPlayer player, ItemStack result) {
        if (result.is(ModItems.GOLD_COIN.get())) {
            // Smith pays in gold (the mithril): into the purse.
            Wallet.add(player, (long) result.getCount() * Money.IRON_PER_GOLD);
            return;
        }
        player.getInventory().add(result);
        if (!result.isEmpty()) {
            player.drop(result, false);
        }
    }
}
