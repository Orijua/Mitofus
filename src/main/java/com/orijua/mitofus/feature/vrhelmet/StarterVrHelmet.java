package com.orijua.mitofus.feature.vrhelmet;

import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The first time a player joins a world they get one basic VR helmet, so the start takes minutes and not a vanilla
 * mining trip. It is a gift: there is no debt. The flag is saved with the player and survives death, so it is given
 * once. The recipe stays for extra VR helmets.
 */
public final class StarterVrHelmet {
    private StarterVrHelmet() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(StarterVrHelmet::onLoggedIn);
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            giveIfFirstTime(player);
        }
    }

    /** Gives the VR helmet and tells the player the first time only; returns whether it was given now. */
    public static boolean giveIfFirstTime(ServerPlayer player) {
        if (player.getData(ModAttachments.STARTER_VR_HELMET_GIVEN.get())) {
            return false;
        }
        player.setData(ModAttachments.STARTER_VR_HELMET_GIVEN.get(), true);
        ItemStack vrhelmet = new ItemStack(ModItems.HELMET.get());
        if (!player.getInventory().add(vrhelmet)) {
            player.drop(vrhelmet, false);
        }
        player.sendSystemMessage(Component.translatable("message.mitofus.vrhelmet.starter_free"));
        return true;
    }
}
