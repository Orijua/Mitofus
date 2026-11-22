package com.orijua.mitofus.feature.world;

import com.orijua.mitofus.core.registry.ModEffects;
import com.orijua.mitofus.feature.progression.ProgressionService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Keeps the mod's objects inert in the real world (see {@link MitofusOnly}): they can't be placed or used with right
 * click (nor can its entities; the VR helmet, terminal and phone are the exceptions), and the passive bonuses are refreshed (and a stun is
 * lifted) when the player changes dimension. Also tells the game-test server
 * which level plays Mitofus by default.
 */
public final class MitofusOnlyEvents {
    private MitofusOnlyEvents() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(MitofusOnlyEvents::onServerStarting);
        gameBus.addListener(MitofusOnlyEvents::onServerStopped);
        gameBus.addListener(MitofusOnlyEvents::onPlace);
        gameBus.addListener(MitofusOnlyEvents::onRightClickItem);
        gameBus.addListener(MitofusOnlyEvents::onRightClickBlock);
        gameBus.addListener(MitofusOnlyEvents::onEntityInteract);
        gameBus.addListener(MitofusOnlyEvents::onEntityInteractSpecific);
        gameBus.addListener(MitofusOnlyEvents::onChangedDimension);
    }

    /** The game-test server drops the datapack's dimensions: there the overworld plays Mitofus unless a test says otherwise. */
    private static void onServerStarting(ServerStartingEvent event) {
        if (event.getServer() instanceof GameTestServer server) {
            MitofusWorld.testDefault(server.overworld());
            MitofusWorld.restoreStandIn();
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        MitofusWorld.testDefault(null);
        MitofusWorld.standIn(null);
    }

    /** A block of the mod placed by an entity in the real world is cancelled (the player is told). */
    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        Entity placer = event.getEntity();
        if (placer == null || placer.level().isClientSide() || !MitofusOnly.active(placer)
                || !MitofusOnly.blocksPlacement(BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock()))) {
            return;
        }
        event.setCanceled(true);
        if (placer instanceof ServerPlayer player) {
            MitofusOnly.warn(player);
        }
    }

    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (inert(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            warnOnServer(event.getEntity());
        }
    }

    /**
     * Only the item's use is denied (a mod block can't be placed, a mod tool does nothing); the block clicked still
     * reacts, so a chest opens even with a mod item in the hand.
     */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (inert(event.getEntity(), event.getItemStack())) {
            event.setUseItem(TriState.FALSE);
            warnOnServer(event.getEntity());
        }
    }

    /** The mod's entities (NPCs) don't answer to right click in the real world; fires on both sides, so no window opens. */
    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (refuses(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            warnOnServer(event.getEntity());
        }
    }

    private static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (refuses(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            warnOnServer(event.getEntity());
        }
    }

    private static boolean refuses(Player player, Entity target) {
        return MitofusOnly.active(player) && MitofusOnly.isMitofusEntity(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
    }

    private static boolean inert(Player player, ItemStack stack) {
        return !stack.isEmpty() && MitofusOnly.active(player) && MitofusOnly.isMitofusItem(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static void warnOnServer(Player player) {
        if (player instanceof ServerPlayer && !player.level().isClientSide()) {
            MitofusOnly.warn(player);
        }
    }

    /** Going to or from Mitofus turns the passive bonuses off or on. */
    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (MitofusOnly.active(player)) {
                player.removeEffect(ModEffects.STUNNED);
            }
            ProgressionService.refreshEffects(player);
        }
    }
}
