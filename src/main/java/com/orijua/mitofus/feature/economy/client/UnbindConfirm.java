package com.orijua.mitofus.feature.economy.client;

import com.orijua.mitofus.core.client.MitofusConfirmScreen;
import com.orijua.mitofus.core.config.VrHelmetBalance;
import com.orijua.mitofus.feature.economy.VrHelmetUnbind;
import com.orijua.mitofus.feature.economy.VrHelmetUnbindPayload;
import com.orijua.mitofus.feature.economy.MoneyText;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client side of unbinding a VR helmet at the terminal: shows the price and asks before the server charges it. */
public final class UnbindConfirm {
    private UnbindConfirm() {
    }

    public static void open(BlockPos pos, InteractionHand hand, ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        long price = VrHelmetUnbind.price(stack, VrHelmetBalance.current());
        boolean offHand = hand == InteractionHand.OFF_HAND;
        minecraft.setScreen(new MitofusConfirmScreen(minecraft.screen,
                Component.translatable("screen.mitofus.terminal.unbind_title"),
                Component.translatable("screen.mitofus.terminal.unbind_confirm", MoneyText.iron(price)),
                () -> PacketDistributor.sendToServer(new VrHelmetUnbindPayload(pos, offHand))));
    }
}
