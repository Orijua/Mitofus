package com.orijua.mitofus.feature.vrhelmet;

import com.orijua.mitofus.feature.character.Characters;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;

/**
 * The real world (overworld, Nether, End, any other dimension) and Mitofus are separate worlds: whatever tries to go
 * from one to the other is cancelled, except while the VR helmet connects/disconnects or the debug goto teleports.
 * Inside the same category nothing changes.
 */
public final class WorldSeparation {
    private static final ThreadLocal<Boolean> ALLOWED = ThreadLocal.withInitial(() -> false);

    private WorldSeparation() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(WorldSeparation::onTravel);
        gameBus.addListener(WorldSeparation::onSetSpawn);
    }

    /**
     * The way a player changes world: closes their menu in the world they leave, teleports with crossing allowed, and
     * then makes them carry the character of the world they are in ({@link Characters#settle}). If the trip did not
     * happen (another mod cancelled it) the world is the same and nothing is swapped. Returns what the swap did: on
     * {@code FAILED} the player is in the new world still carrying the other one's things, and the caller decides.
     */
    public static Characters.Result cross(ServerPlayer player, Runnable teleport) {
        Characters.prepareCrossing(player);
        crossing(teleport);
        return Characters.settle(player);
    }

    /** Runs {@code teleport} with crossing between the two worlds allowed; always restored afterwards. */
    public static void crossing(Runnable teleport) {
        boolean before = ALLOWED.get();
        ALLOWED.set(true);
        try {
            teleport.run();
        } finally {
            ALLOWED.set(before);
        }
    }

    public static boolean crossingAllowed() {
        return ALLOWED.get();
    }

    private static void onTravel(EntityTravelToDimensionEvent event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (VrHelmetRules.crossingBlocked(MitofusWorld.is(entity.level()), MitofusWorld.is(event.getDimension()), crossingAllowed())) {
            event.setCanceled(true);
            ServerPlayer toTell = entity instanceof ServerPlayer player ? player
                    : entity instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer owner ? owner : null;
            if (toTell != null) {
                toTell.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.crossing_blocked"), true);
            }
        }
    }

    /**
     * A bed (or anchor) in Mitofus must not set the respawn point of the real world: dying in Mitofus no longer sends you
     * to the real world (you reappear in Mitofus, see {@link MitofusDeathService}), but the real respawn must stay the
     * real one (it is also the reserve case when there is nowhere to reappear). Resets (no new position) carry the
     * overworld, so they pass.
     */
    private static void onSetSpawn(PlayerSetSpawnEvent event) {
        if (event.getNewSpawn() != null && MitofusWorld.is(event.getSpawnLevel())) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.bed_ignored"), true);
            }
        }
    }
}
