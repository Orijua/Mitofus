package com.orijua.mitofus.feature.economy;

import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * The player's money in Mitofus, in iron: the {@link Purse} (a balance, no room in the inventory) plus any coin item still
 * carried (old saves, the terminal's gold, drops), which is {@link #absorb absorbed} into the purse as soon as the server
 * looks. Everything that is paid or earned in Mitofus goes through here, on both sides: the client reads the synced purse.
 */
public final class Wallet {
    private Wallet() {
    }

    public static Purse purse(Player player) {
        return player.getData(ModAttachments.PURSE.get());
    }

    /** Everything the player has: the purse and the coins still carried. */
    public static long balance(Player player) {
        return purse(player).iron() + CarriedItems.totalIron(player.getInventory());
    }

    /** Everything the player has in copper, the smallest coin: the iron balance plus every copper coin and the purse's copper. */
    public static long totalCopper(Player player) {
        return purse(player).iron() * Money.COPPER_PER_IRON + purse(player).copper()
                + CarriedItems.totalIron(player.getInventory()) * Money.COPPER_PER_IRON
                + CarriedItems.copper(player.getInventory()) % Money.COPPER_PER_IRON;
    }

    public static boolean canAfford(Player player, long iron) {
        return balance(player) >= iron;
    }

    /** Puts {@code iron} in the purse. */
    public static void add(ServerPlayer player, long iron) {
        if (iron > 0L) {
            player.setData(ModAttachments.PURSE.get(), purse(player).plus(iron));
        }
    }

    /**
     * Takes {@code iron} from the purse (after absorbing the coins carried). Returns false and touches nothing if the
     * balance is short.
     */
    public static boolean pay(ServerPlayer player, long iron) {
        absorb(player);
        Purse purse = purse(player);
        if (purse.iron() < iron) {
            return false;
        }
        if (iron > 0L) {
            player.setData(ModAttachments.PURSE.get(), purse.minus(iron));
        }
        return true;
    }

    /**
     * Moves every coin item carried (diamond, gold, iron, copper) into the purse. The copper that is less than an iron stays in
     * the purse as copper until it makes one. Returns the iron absorbed (0 if there was none).
     */
    public static long absorb(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        long diamond = CarriedItems.diamond(inventory);
        long gold = CarriedItems.gold(inventory);
        long iron = CarriedItems.iron(inventory);
        long copper = CarriedItems.copper(inventory);
        if (diamond == 0L && gold == 0L && iron == 0L && copper == 0L) {
            return 0L;
        }
        takeAll(inventory, ModItems.DIAMOND_COIN.get(), diamond);
        takeAll(inventory, ModItems.GOLD_COIN.get(), gold);
        takeAll(inventory, ModItems.IRON_COIN.get(), iron);
        takeAll(inventory, ModItems.COPPER_COIN.get(), copper);
        long total = diamond * Money.IRON_PER_DIAMOND + gold * Money.IRON_PER_GOLD + iron;
        // Every copper coin goes in: the purse keeps the copper that does not make an iron yet.
        Purse before = purse(player);
        Purse after = before.plus(total).plusCopper(copper);
        player.setData(ModAttachments.PURSE.get(), after);
        total = after.iron() - before.iron();
        player.inventoryMenu.sendAllDataToRemote();
        return total;
    }

    private static void takeAll(Inventory inventory, Item coin, long amount) {
        if (amount > 0L) {
            CarriedItems.take(inventory, stack -> stack.is(coin), (int) Math.min(Integer.MAX_VALUE, amount));
        }
    }

    /** "Purse: 3 gold 11 iron", for the action bar. */
    public static Component text(Player player) {
        return Component.translatable("message.mitofus.purse", MoneyText.iron(balance(player)));
    }
}
