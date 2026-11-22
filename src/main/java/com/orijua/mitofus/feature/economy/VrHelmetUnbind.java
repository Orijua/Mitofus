package com.orijua.mitofus.feature.economy;

import com.orijua.mitofus.core.config.VrHelmetBalance;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.feature.vrhelmet.HelmetItem;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetTier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Unbinding a bound VR helmet at the terminal: it costs a percent of its tier's price and leaves it without an owner. */
public final class VrHelmetUnbind {
    private VrHelmetUnbind() {
    }

    public enum Result { UNBOUND, NOT_BOUND, IN_MITOFUS, NOT_ENOUGH }

    /** The tier of a VR helmet stack (empty-handed: BASIC is never returned for a non-VR helmet; the caller checked). */
    static VrHelmetTier tierOf(ItemStack stack) {
        if (stack.getItem() instanceof HelmetItem item) {
            return item.tier();
        }
        throw new IllegalArgumentException("Not a vrhelmet: " + stack);
    }

    /** Iron it costs to unbind {@code stack}, a VR helmet (what the confirmation window shows). */
    public static long price(ItemStack stack, VrHelmetBalance balance) {
        return balance.unbindPrice(tierOf(stack));
    }

    public static Result unbind(ServerPlayer player, ItemStack stack, VrHelmetBalance balance) {
        if (!stack.has(ModDataComponents.VR_HELMET_OWNER.get())) {
            return Result.NOT_BOUND;
        }
        if (!TerminalService.carriesRealWorld(player)) {
            player.displayClientMessage(Component.translatable("message.mitofus.terminal.no_signal"), true);
            return Result.IN_MITOFUS;
        }
        long price = balance.unbindPrice(tierOf(stack));
        if (!Wallet.canAfford(player, price)) {
            player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.unbind_no_money", MoneyText.iron(price)), true);
            return Result.NOT_ENOUGH;
        }
        Wallet.pay(player, price);
        stack.remove(ModDataComponents.VR_HELMET_OWNER.get());
        player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.unbound", MoneyText.iron(price)), true);
        return Result.UNBOUND;
    }
}
