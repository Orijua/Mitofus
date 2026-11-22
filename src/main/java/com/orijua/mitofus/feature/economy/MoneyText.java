package com.orijua.mitofus.feature.economy;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * How an amount of iron reads to the player: "3 gold 11 iron", dropping the part that is zero ("5 gold", "40 iron").
 * Zero reads "0 iron". Client and server share it.
 */
public final class MoneyText {
    private MoneyText() {
    }

    public static Component iron(long iron) {
        long amount = Math.max(0L, iron);
        long diamond = Money.diamondPart(amount);
        long gold = Money.goldRest(amount);
        long rest = Money.ironPart(amount);
        MutableComponent text = Component.empty();
        if (diamond > 0L) {
            text.append(Component.translatable("money.mitofus.diamond", Money.format(diamond)));
        }
        if (gold > 0L) {
            text.append(Component.literal(text.getSiblings().isEmpty() ? "" : " "));
            text.append(Component.translatable("money.mitofus.gold", Money.format(gold)));
        }
        if (rest > 0L || text.getSiblings().isEmpty()) {
            text.append(Component.literal(text.getSiblings().isEmpty() ? "" : " "));
            text.append(Component.translatable("money.mitofus.iron", Money.format(rest)));
        }
        return text;
    }

    /** As {@link #iron}, with the copper too: "3 gold 11 iron 40 copper" (the parts that are zero are dropped; zero is "0 copper"). */
    public static Component copper(long copper) {
        long[] coins = Money.coinsOfCopper(copper);
        String[] keys = {"money.mitofus.diamond", "money.mitofus.gold", "money.mitofus.iron", "money.mitofus.copper"};
        MutableComponent text = Component.empty();
        for (int i = 0; i < coins.length; i++) {
            if (coins[i] > 0L) {
                text.append(Component.literal(text.getSiblings().isEmpty() ? "" : " "));
                text.append(Component.translatable(keys[i], Money.format(coins[i])));
            }
        }
        return text.getSiblings().isEmpty() ? Component.translatable(keys[3], "0") : text;
    }
}
