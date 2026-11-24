package com.orijua.mitofus.feature.economy.client;

import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.economy.Money;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A balance drawn coin by coin: the icon of each of the four coins (diamond, gold, iron, copper) with how many of
 * it. The hover shows how the coins are worth each other. Client only.
 */
public final class CoinBalanceView {
    private static final int ICON = 16;
    private static final int GAP = 6;

    private CoinBalanceView() {
    }

    /** Draws the balance (in copper) with its left edge at {@code x}; returns the width it took. */
    public static int render(GuiGraphics graphics, Font font, long copper, int x, int y, int color) {
        long[] coins = Money.coinsOfCopper(copper);
        ItemStack[] icons = {new ItemStack(ModItems.DIAMOND_COIN.get()), new ItemStack(ModItems.GOLD_COIN.get()),
                new ItemStack(ModItems.IRON_COIN.get()), new ItemStack(ModItems.COPPER_COIN.get())};
        int cursor = x;
        for (int i = 0; i < coins.length; i++) {
            graphics.renderItem(icons[i], cursor, y - 4);
            String amount = Money.format(coins[i]);
            UiStyle.text(graphics, font, Component.literal(amount), cursor + ICON + 1, y, coins[i] > 0L ? color : UiStyle.MUTED);
            cursor += ICON + 1 + font.width(amount) + GAP;
        }
        return cursor - GAP - x;
    }

    /** The width {@link #render} takes for this balance. */
    public static int width(Font font, long copper) {
        long[] coins = Money.coinsOfCopper(copper);
        int total = 0;
        for (long coin : coins) {
            total += ICON + 1 + font.width(Money.format(coin)) + GAP;
        }
        return total - GAP;
    }

    /** What the hover of a balance says: how the four coins are worth each other. */
    public static List<Component> tooltip() {
        return List.of(
                Component.translatable("screen.mitofus.coins.tip.title").withStyle(net.minecraft.ChatFormatting.GOLD),
                Component.translatable("screen.mitofus.coins.tip.diamond", Money.GOLD_PER_DIAMOND),
                Component.translatable("screen.mitofus.coins.tip.gold", Money.IRON_PER_GOLD),
                Component.translatable("screen.mitofus.coins.tip.iron", Money.COPPER_PER_IRON));
    }
}
