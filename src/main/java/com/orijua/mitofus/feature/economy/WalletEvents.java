package com.orijua.mitofus.feature.economy;

import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Once a second in Mitofus, the coin items a player carries go into their purse (see {@link Wallet#absorb}). */
public final class WalletEvents {
    /** Ticks between two looks at the inventory. */
    private static final int PERIOD = 20;

    private WalletEvents() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(WalletEvents::onTick);
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % PERIOD == 0 && MitofusWorld.is(player.level())) {
            Wallet.absorb(player);
        }
    }
}
