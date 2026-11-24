package com.orijua.mitofus.feature.fatigue;

import com.orijua.mitofus.core.config.FatigueBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModEffects;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The safety protocol holds the player still: from the moment the energy runs out until the forced disconnection they cannot
 * walk, jump, swim, glide, ride, hit, shoot, throw, mine or place blocks (the client part, which zeroes the movement input, is in
 * {@code FatigueRestraintClient}). Nothing is applied or removed: it is deduced from the grace attachment, so it cannot be left
 * stuck on. The protection after reconnecting does not restrain.
 *
 * <p>The movement is also held on the server ({@link #holdStill}): horizontal motion zeroed each tick and a player who drifts
 * past {@code FatigueBalance.restraintLeeway} blocks is put back where the hold began. Releasing a bow or a charged trident is
 * refused too, so the first tick of the hold has no window. Any right click with an item stays refused, food
 * included (final decision, BUG-087). Mining, placing and interacting with entities follow the same rules as
 * {@link #isRestrained}.
 *
 * <p>Interacting with entities (trading, item frames, armor stands, shears, saddles, leads) is refused too (BUG-089, option A).
 * That includes the screens of the mod's own entities (NPC, apprentices, trading) while the 5 seconds last: they open from
 * those same events. Left click actions on blocks are all cancelled as well; whether cancelling the abort of a mining already
 * in progress leaves a stale crack on the client is pending a manual test (BUG-089, point 2).
 *
 * <p>A window already open when the grace period starts (chest, trading, furnace...) is closed by {@link #enforce} every tick
 * (BUG-091, option A); the item on the cursor goes back to the inventory. Creative, spectator, the real world and the
 * protection after reconnecting close nothing (they are not restrained). {@code closeContainer} only closes server-side menus
 * (smelter, smithing anvil, account terminal, vanilla containers). The client-only screens (NPC, apprentices, trading, Mitofus
 * tabs) and the player's own inventory are closed by the client as if Escape was pressed ({@code FatigueRestraintClient},
 * BUG-092); the server does not filter their payloads. Chat, the pause menu and system screens stay open.
 */
public final class FatigueRestraint {
    /** Where each restrained player was when the hold began; server-only, forgotten as soon as they are free or leave. */
    private static final java.util.Map<java.util.UUID, Vec3> ANCHORS = new java.util.concurrent.ConcurrentHashMap<>();

    private FatigueRestraint() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(FatigueRestraint::onTick);
        gameBus.addListener(FatigueRestraint::onAttack);
        gameBus.addListener(FatigueRestraint::onUseItem);
        gameBus.addListener(FatigueRestraint::onStartUsing);
        gameBus.addListener(FatigueRestraint::onStopUsing);
        gameBus.addListener(FatigueRestraint::onLogout);
        gameBus.addListener(FatigueRestraint::onMount);
        gameBus.addListener(FatigueRestraint::onDamageDealt);
        gameBus.addListener(FatigueRestraint::onBreak);
        gameBus.addListener(FatigueRestraint::onLeftClickBlock);
        gameBus.addListener(FatigueRestraint::onBreakSpeed);
        gameBus.addListener(FatigueRestraint::onPlace);
        gameBus.addListener(FatigueRestraint::onRightClickBlock);
        gameBus.addListener(FatigueRestraint::onInteractEntity);
        gameBus.addListener(FatigueRestraint::onInteractEntitySpecific);
    }

    /** Whether the player is held still now: in the grace period (the attachment is synced to its owner) or stunned. */
    public static boolean isRestrained(Player player) {
        // A stunned player is held the same way (the effect is synced to the client): no attacks, skills, items, blocks or movement.
        if (player.hasEffect(ModEffects.STUNNED) && MitofusWorld.is(player.level()) && !player.isCreative() && !player.isSpectator() && player.isAlive()) {
            return true;
        }
        int grace = player.getData(ModAttachments.FATIGUE_GRACE);
        if (grace < 0) {
            return false;
        }
        return FatigueRules.isRestrained(grace, MitofusWorld.is(player.level()),
                !player.isCreative() && !player.isSpectator(), player.isAlive());
    }

    /** What the server does every tick to a restrained player; harmless to repeat. */
    public static void enforce(ServerPlayer player) {
        if (!isRestrained(player)) {
            ANCHORS.remove(player.getUUID());
            return;
        }
        holdStill(player);
        if (player.isPassenger()) {
            player.stopRiding();
        }
        if (player.isFallFlying()) {
            player.stopFallFlying();
        }
        if (player.isUsingItem()) {
            player.stopUsingItem();
        }
        player.setSprinting(false);
        if (player.containerMenu != player.inventoryMenu) {
            // Server menus (chest, furnace, smelter, smithing anvil, terminal) opened before the grace period: closed. Client-only
            // screens (NPC, apprentices, trading, Mitofus tabs) are not menus: the client closes them. The menu hands the cursor back to the
            // inventory (it only drops it when there is no room), so nothing is lost. Idempotent: the next tick finds it closed.
            player.closeContainer();
        }
    }

    /**
     * Server-side hold (the client already zeroes its own input, but a modified client could ignore it): the horizontal motion is
     * zeroed every tick, and a player who still ends up farther than the leeway from where the hold began is put back there.
     * Falling is left alone so a player held in the air still lands.
     */
    private static void holdStill(ServerPlayer player) {
        Vec3 anchor = ANCHORS.computeIfAbsent(player.getUUID(), id -> player.position());
        Vec3 now = player.position();
        if (FatigueRules.driftedTooFar(now.x - anchor.x, now.y - anchor.y, now.z - anchor.z,
                FatigueBalance.current().restraintLeeway())) {
            // The connection's own teleport also tells the client where it is, so it cannot keep walking from the old spot.
            player.moveTo(anchor.x, anchor.y, anchor.z, player.getYRot(), player.getXRot());
            player.connection.teleport(anchor.x, anchor.y, anchor.z, player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        if (motion.x != 0.0 || motion.z != 0.0) {
            player.setDeltaMovement(0.0, motion.y, 0.0);
            player.hurtMarked = true;
        }
    }

    /** Releasing a bow or a charged trident in the tick the hold begins goes nowhere (#32). */
    private static void onStopUsing(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof Player player && isRestrained(player)) {
            event.setCanceled(true);
        }
    }

    private static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        ANCHORS.remove(event.getEntity().getUUID());
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            enforce(player);
        }
    }

    private static void onAttack(AttackEntityEvent event) {
        if (isRestrained(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Every use of an item in the hand is refused (bows, tridents, throwables and food too: final decision). */
    private static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (isRestrained(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static void onStartUsing(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player && isRestrained(player)) {
            event.setCanceled(true);
        }
    }

    private static void onMount(EntityMountEvent event) {
        if (event.isMounting() && event.getEntityMounting() instanceof Player player && isRestrained(player)) {
            event.setCanceled(true);
        }
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (isRestrained(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    /** Starting to dig (fired on both sides). */
    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isRestrained(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** No progress is shown while the player keeps the button pressed. */
    private static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (isRestrained(event.getEntity())) {
            event.setNewSpeed(0.0f);
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && isRestrained(player)) {
            event.setCanceled(true);
        }
    }

    /** Placing and any use of a block (chests, doors): cancelling also denies the block use and the item use. */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isRestrained(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** Any use of an entity (trade, item frame, armor stand, shears, saddle, lead, mod screens): refused like the clicks above. */
    private static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (isRestrained(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** Same as {@link #onInteractEntity} for the interaction at a point of the entity (armor stands, item frames). */
    private static void onInteractEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isRestrained(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** Safety net on the server: whatever slipped through (a skill, a projectile already in the air) does no damage. */
    private static void onDamageDealt(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof Player player && player != event.getEntity() && isRestrained(player)) {
            event.setCanceled(true);
        }
    }
}
