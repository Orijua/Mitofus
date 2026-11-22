package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.economy.TerminalShop;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/**
 * The message of the terminal's shop writes the price as coins ("123 gold 45 iron") with the thousands separator in each
 * part. Money.format and MoneyText have their own unit tests; this checks the service really uses them in its argument
 * (the sale notification is checked in {@link TradeTests}).
 */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class AmountFormatTests {
    private AmountFormatTests() {
    }

    private static TranslatableContents translatable(GameTestHelper helper, Optional<Component> message, String key) {
        helper.assertTrue(message.isPresent() && message.get().getContents() instanceof TranslatableContents contents && contents.getKey().equals(key),
                "the message is " + key + ": " + message);
        return (TranslatableContents) message.get().getContents();
    }

    private static TranslatableContents part(Component price, int index) {
        return (TranslatableContents) price.getSiblings().stream().filter(c -> c.getContents() instanceof TranslatableContents)
                .toList().get(index).getContents();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theTerminalShopMessageWritesThePriceInCoins(GameTestHelper helper) {
        TranslatableContents piece = translatable(helper, TerminalShop.message(
                new TerminalShop.Outcome(TerminalShop.Result.OK, 1_234_545L, new ItemStack(Items.DIAMOND_SWORD))), "message.mitofus.terminal.bought_piece");
        helper.assertTrue(piece.getArgs()[1] instanceof Component price && price.getSiblings().stream().filter(c -> c.getContents() instanceof TranslatableContents).count() == 3
                        && part(price, 0).getKey().equals("money.mitofus.diamond") && "123".equals(part(price, 0).getArgs()[0])
                        && part(price, 1).getKey().equals("money.mitofus.gold") && "45".equals(part(price, 1).getArgs()[0])
                        && part(price, 2).getKey().equals("money.mitofus.iron") && "45".equals(part(price, 2).getArgs()[0]),
                "the price reads 123 diamond 45 gold 45 iron: " + java.util.Arrays.toString(piece.getArgs()));
        helper.succeed();
    }
}
