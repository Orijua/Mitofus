package com.orijua.mitofus.feature.economy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;

/**
 * Paying in gold, iron and copper (gold = 100 iron, iron = 100 copper): the price comes out of the {@link Wallet}, a
 * balance, so there is no change to give. The pure arithmetic in copper, the smallest coin, is for the old coin items.
 */
public final class CoinPurse {
    public static final long COPPER_PER_GOLD = (long) Money.IRON_PER_GOLD * Money.COPPER_PER_IRON;

    private CoinPurse() {
    }

    /** What a price in iron is in copper. */
    public static long copperOf(long iron) {
        return iron * Money.COPPER_PER_IRON;
    }

    /** The purse as copper: the value of every coin carried. */
    public static long carriedCopper(Inventory inventory) {
        return CarriedItems.gold(inventory) * COPPER_PER_GOLD
                + CarriedItems.iron(inventory) * Money.COPPER_PER_IRON
                + CarriedItems.copper(inventory);
    }

    /** The fewest coins worth {@code copper}: [gold, iron, copper]. */
    public static long[] coins(long copper) {
        long left = Math.max(0L, copper);
        long gold = left / COPPER_PER_GOLD;
        left %= COPPER_PER_GOLD;
        return new long[]{gold, left / Money.COPPER_PER_IRON, left % Money.COPPER_PER_IRON};
    }

    /** True if the player has {@code iron} (the purse and the coins carried: see {@link Wallet}). */
    public static boolean canAfford(Inventory inventory, long iron) {
        return Wallet.canAfford(inventory.player, iron);
    }

    /**
     * Takes {@code iron} from the player's purse (after absorbing the coins carried): no change to give, because the money
     * is a balance. Returns false and touches nothing if it is short.
     */
    public static boolean pay(ServerPlayer player, long iron) {
        return Wallet.pay(player, iron);
    }
}
