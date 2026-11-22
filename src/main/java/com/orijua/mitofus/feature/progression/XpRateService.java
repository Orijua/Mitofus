package com.orijua.mitofus.feature.progression;

import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Measures the level XP a player earns per hour (see {@link XpRateRules}): the clock runs once per second while they
 * are alive in Mitofus, and {@link ProgressionService#awardXp} records every gain. The numbers are an attachment.
 */
public final class XpRateService {
    private static final int TICKS_PER_SECOND = 20;

    private XpRateService() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(XpRateService::onTick);
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % TICKS_PER_SECOND == 0) {
            tickSecond(player);
        }
    }

    /** One second of play: counted only for a living player in Mitofus (the clock stops in the real world). */
    public static void tickSecond(ServerPlayer player) {
        if (!MitofusWorld.is(player.level()) || !player.isAlive()) {
            return;
        }
        player.setData(ModAttachments.XP_RATE.get(), XpRateRules.tick(get(player)));
    }

    /** Level XP that was just gained (rested bonus included). */
    public static void record(ServerPlayer player, int amount) {
        if (amount <= 0) {
            return;
        }
        player.setData(ModAttachments.XP_RATE.get(), XpRateRules.record(get(player), amount));
    }

    public static XpRate get(Player player) {
        return player.getData(ModAttachments.XP_RATE.get());
    }

    /** Forgets the measurement. */
    public static void reset(ServerPlayer player) {
        player.removeData(ModAttachments.XP_RATE.get());
    }
}
