package com.orijua.mitofus.core.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.orijua.mitofus.core.config.TradeBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.feature.economy.Money;
import com.orijua.mitofus.feature.economy.MoneyText;
import com.orijua.mitofus.feature.economy.Wallet;
import com.orijua.mitofus.feature.economy.TerminalService;
import com.orijua.mitofus.feature.economy.TerminalState;
import com.orijua.mitofus.feature.economy.TradeKey;
import com.orijua.mitofus.feature.economy.TradeRules;
import com.orijua.mitofus.feature.economy.TradeSaturation;
import com.orijua.mitofus.feature.economy.TradeService;
import com.orijua.mitofus.feature.equipment.ItemRating;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The terminal, the account, debt and trade pressure: subcommands of {@code /mitofus} for operators ({@link DebugCommands} lists them all). */
public final class EconomyDebugCommands {
    private EconomyDebugCommands() {
    }

    /** The {@code /mitofus} subcommands of this topic (The terminal, the account, debt and trade pressure); the caller makes them operator-only. */
    static List<LiteralArgumentBuilder<CommandSourceStack>> nodes(CommandBuildContext context) {
        return List.of(
                Commands.literal("terminal")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    TerminalState state = TerminalService.state(player, java.util.Optional.empty());
                                    Component stored = state.characterAvailable() ? Component.literal(Money.format(state.storedGold()))
                                            : Component.translatable("message.mitofus.debug_terminal_no_character");
                                    ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_terminal",
                                            stored, state.today()), false);
                                    return 1;
                                })
                                .then(Commands.literal("offer")
                                        .executes(EconomyDebugCommands::terminalOffer)
                                        .then(Commands.literal("reset")
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    player.removeData(ModAttachments.OFFER_BOUGHT.get());
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_terminal_offer_reset"), false);
                                                    return 1;
                                                }))),
                Commands.literal("purse")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_purse",
                                            MoneyText.iron(Wallet.balance(player))), false);
                                    return 1;
                                }),
                Commands.literal("trade")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    TradeBalance balance = TradeBalance.current();
                                    long today = TradeRules.day(player.serverLevel().getGameTime());
                                    TradeSaturation saturation = TradeService.saturation(player);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_trade"), false);
                                    saturation.entries().keySet().stream().sorted(Comparator.comparing(TradeKey::asString)).forEach(key -> {
                                        int pressure = saturation.pressure(key, today, balance);
                                        long percent = TradeRules.saturationPercent(pressure, balance);
                                        ctx.getSource().sendSuccess(() -> Component.translatable(
                                                "message.mitofus.debug_trade_entry", key.asString(), pressure, percent), false);
                                    });
                                    return 1;
                                })
                                .then(Commands.literal("reset")
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            player.removeData(ModAttachments.TRADE_SATURATION.get());
                                            ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_trade_reset"), false);
                                            return 1;
                                        })));
    }

    /** Lists today's offer of the terminal: position, item, rating, price and whether this player bought it. */
    private static int terminalOffer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TerminalState state = TerminalService.state(player, java.util.Optional.empty());
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_terminal_offer", state.today(), state.offer().size()), false);
        for (com.orijua.mitofus.feature.economy.OfferEntry entry : state.offer()) {
            ItemRating rating = entry.stack().get(com.orijua.mitofus.core.registry.ModDataComponents.ITEM_RATING);
            Component bought = Component.translatable(state.boughtToday().contains(entry.position())
                    ? "message.mitofus.debug_terminal_offer_yes" : "message.mitofus.debug_terminal_offer_no");
            ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_terminal_offer_entry", entry.position(),
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entry.stack().getItem()).toString(),
                    rating == null ? "?" : rating.getSerializedName(), Money.format(entry.price()), bought), false);
        }
        return 1;
    }
}
