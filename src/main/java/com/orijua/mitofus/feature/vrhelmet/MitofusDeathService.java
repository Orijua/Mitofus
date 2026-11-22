package com.orijua.mitofus.feature.vrhelmet;

import com.mojang.serialization.DataResult;
import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.DeathBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.notification.NotificationEvents;
import com.orijua.mitofus.feature.notification.SystemNotification.Kind;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToLongFunction;

/**
 * Dying in Mitofus (own design): you reappear in Mitofus at your last save point (or Covadonga's plaza) without losing
 * anything, and a second death within the window ({@link DeathBalance}) also disconnects you to your VR helmet and locks
 * the VR helmet: you cannot connect again until {@code reconnectLockMinutes} have passed. Dying in the real world is
 * vanilla and does not count.
 *
 * <p>The lock: when the disconnection with {@link VrHelmetService.Reason#DEATH} actually happens, the game time at which
 * it ends is stored in {@link MitofusDeaths#unlockAt()} (only if the lock is above 0). {@link VrHelmetService#begin} and
 * {@link VrHelmetService#connect} refuse with {@code Begin.LOCKED} / false while {@link #lockRemaining} is above 0.
 * Fatigue, the normal logout, the manual disconnection and the damage to the body never lock; the death counter going
 * back to 0 at the disconnection does not clear the lock; the lock survives death and logging out, and
 * {@code /mitofus death reset} and {@code resetAll} clear it. Operator debug commands ({@code /mitofus goto})
 * are not blocked: they are tools to move around for testing and do not go through the VR helmet.
 *
 * <p>How it works: when the player dies in Mitofus ({@link VrHelmetService#died}), the inventory is put aside in the
 * {@link ModAttachments#MITOFUS_DEATH_KEPT} attachment and emptied (vanilla drops the inventory right after the death
 * event, and cancelling the drops would destroy it). The respawn position event sends the new player back into
 * Mitofus, and the clone event gives them the inventory and the experience of the old one. A pending disconnection
 * runs on the tick after the respawn.
 *
 * <p>The clock is the game time of the overworld, in ticks, so neither the window nor the lock pass while the server is
 * closed (it stops with it); both use the same (injectable) clock. There is no tomb system in the mod: what is avoided is vanilla's drop of the items.
 */
public final class MitofusDeathService {
    private static final ToLongFunction<MinecraftServer> GAME_TIME = server -> server.overworld().getGameTime();
    private static ToLongFunction<MinecraftServer> clock = GAME_TIME;

    private static final String INVENTORY = "inventory";
    private static final String DEATHS_BEFORE = "deaths_before";

    /** Players whose items went through the clone event of a death in Mitofus and who have not been told yet. */
    private static final Set<UUID> JUST_DIED = ConcurrentHashMap.newKeySet();

    private MitofusDeathService() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(MitofusDeathService::onExperienceDrop);
        gameBus.addListener(MitofusDeathService::onRespawnPosition);
        gameBus.addListener(MitofusDeathService::onClone);
        gameBus.addListener(MitofusDeathService::onRespawn);
        gameBus.addListener(MitofusDeathService::onTick);
        gameBus.addListener(MitofusDeathService::onLoggedOut);
        gameBus.addListener(MitofusDeathService::onServerStopping);
        gameBus.addListener(MitofusDeathService::onServerStarting);
    }

    /** For tests: the game time the window is measured with. */
    public static void setClock(ToLongFunction<MinecraftServer> replacement) {
        clock = replacement;
    }

    public static void restoreClock() {
        clock = GAME_TIME;
    }

    /** The game time (ticks) the window and the lock are measured with: the single clock of this service (injectable in tests). */
    public static long now(MinecraftServer server) {
        return clock.applyAsLong(server);
    }

    public static MitofusDeaths deaths(ServerPlayer player) {
        return player.getData(ModAttachments.MITOFUS_DEATHS.get());
    }

    /** Whether the player has items put aside by a death that has not been given back yet. */
    public static boolean hasKept(ServerPlayer player) {
        return player.hasData(ModAttachments.MITOFUS_DEATH_KEPT.get());
    }

    // ---- the death ----

    /**
     * The player died in Mitofus (called by {@link VrHelmetService#died}, which only hears deaths nothing cancelled):
     * the inventory is put aside and the death is counted.
     */
    static void died(ServerPlayer player) {
        died(player, null);
    }

    static void died(ServerPlayer player, DamageSource source) {
        // A previous death that never reached its respawn (a repeated event): give that back and count from before it.
        if (hasKept(player)) {
            undo(player);
        }
        MitofusDeaths before = deaths(player);
        returnMenuItems(player);
        List<ItemStack> pvpDrops = takePvpDrops(player, source);
        CompoundTag kept = new CompoundTag();
        kept.put(INVENTORY, player.getInventory().save(new ListTag()));
        kept.put(DEATHS_BEFORE, MitofusDeaths.CODEC.encodeStart(NbtOps.INSTANCE, before).result().orElseGet(CompoundTag::new));
        stashRespawn(player, kept);
        player.setData(ModAttachments.MITOFUS_DEATH_KEPT.get(), kept);
        player.getInventory().clearContent();
        pvpDrops.forEach(stack -> player.spawnAtLocation(stack));

        long now = now(player.server);
        MitofusDeathRules.Outcome outcome = MitofusDeathRules.classify(before.lastDeathAt(), now, DeathBalance.current().windowTicks());
        player.setData(ModAttachments.MITOFUS_DEATHS.get(), MitofusDeathRules.after(before, outcome, now));
        Mitofus.LOGGER.info("{} died in Mitofus ({}): their things are kept and they reappear in Mitofus",
                player.getGameProfile().getName(), outcome);
    }

    /**
     * Killed by another player with PvP on: with the configured chance, some equipped pieces (main hand and armor) are
     * taken out of the inventory so they are not put aside, and the caller drops them where the victim died.
     */
    private static List<ItemStack> takePvpDrops(ServerPlayer victim, DamageSource source) {
        if (source == null || !(source.getEntity() instanceof ServerPlayer killer) || killer == victim
                || !victim.server.isPvpAllowed()) {
            return List.of();
        }
        DeathBalance balance = DeathBalance.current();
        if (!MitofusDeathRules.pvpDrops(balance.pvpDropChance(), victim.getRandom().nextDouble())) {
            return List.of();
        }
        Inventory inventory = victim.getInventory();
        // Slot 0 is the main hand; 1 to 4 are the armor pieces.
        List<Integer> filled = new java.util.ArrayList<>();
        if (!inventory.getItem(inventory.selected).isEmpty()) {
            filled.add(0);
        }
        for (int piece = 0; piece < inventory.armor.size(); piece++) {
            if (!inventory.armor.get(piece).isEmpty()) {
                filled.add(1 + piece);
            }
        }
        List<ItemStack> taken = new java.util.ArrayList<>();
        for (int slot : MitofusDeathRules.pickPvpDrops(filled, balance.pvpDropCount(), victim.getRandom()::nextInt)) {
            if (slot == 0) {
                taken.add(inventory.items.set(inventory.selected, ItemStack.EMPTY));
            } else {
                taken.add(inventory.armor.set(slot - 1, ItemStack.EMPTY));
            }
        }
        if (!taken.isEmpty()) {
            Mitofus.LOGGER.info("{} was killed by {} in Mitofus: {} equipped piece(s) fall", victim.getGameProfile().getName(),
                    killer.getGameProfile().getName(), taken.size());
        }
        return taken;
    }

    /**
     * The cursor, the 2x2 crafting grid and the temporary slots of an open menu go back to the inventory.
     *
     * <p>We are inside the death event, so the health is already 0. Vanilla (checked in 1.21.1) decides with
     * {@code isAlive()}: {@code AbstractContainerMenu.removed} (cursor) and {@code clearContainer} (grids) drop the items
     * on the ground for a dead player and put them back in the inventory for a living one. So the health is raised for
     * a moment and restored in {@code finally}; {@code setHealth} only changes the synched entity data, nothing is sent
     * until the tick, and by then the health is 0 again. {@code ServerPlayer.doCloseContainer} only calls {@code removed}
     * of the open menu, not of the inventory menu, so with another menu open the 2x2 grid is emptied apart.
     * With a full inventory what does not fit falls at their feet (recoverable in Mitofus).
     */
    private static void returnMenuItems(ServerPlayer player) {
        float health = player.getHealth();
        player.setHealth(1.0f);
        try {
            boolean otherMenu = player.containerMenu != player.inventoryMenu;
            player.closeContainer();
            if (otherMenu) {
                player.inventoryMenu.removed(player);
            }
        } finally {
            player.setHealth(health);
        }
    }

    /** Puts the real-world respawn point aside and clears it, so the vanilla respawn cannot use up an anchor charge or report a missing bed. */
    private static void stashRespawn(ServerPlayer player, CompoundTag kept) {
        BlockPos pos = player.getRespawnPosition();
        if (pos == null) {
            return;
        }
        RespawnStash.write(kept, new RespawnStash.Point(player.getRespawnDimension(), pos, player.getRespawnAngle(), player.isRespawnForced()));
        player.setRespawnPosition(Level.OVERWORLD, null, 0.0f, false, false);
    }

    private static void restoreRespawn(ServerPlayer player, CompoundTag kept) {
        RespawnStash.read(kept).ifPresent(point -> player.setRespawnPosition(point.dimension(), point.pos(), point.angle(), point.forced(), false));
    }

    /** Gives back what was put aside and puts the counter as it was before that death. Nothing is ever lost. */
    private static void undo(ServerPlayer player) {
        CompoundTag kept = player.getData(ModAttachments.MITOFUS_DEATH_KEPT.get());
        giveBack(player, kept.getList(INVENTORY, Tag.TAG_COMPOUND));
        restoreRespawn(player, kept);
        DataResult<MitofusDeaths> before = MitofusDeaths.CODEC.parse(NbtOps.INSTANCE, kept.get(DEATHS_BEFORE));
        player.setData(ModAttachments.MITOFUS_DEATHS.get(), before.result().orElse(MitofusDeaths.EMPTY));
        player.removeData(ModAttachments.MITOFUS_DEATH_KEPT.get());
    }

    /** Into an empty inventory as it was (same slots); otherwise piece by piece into whatever fits, and what does not fit falls at their feet. */
    private static void giveBack(ServerPlayer player, ListTag saved) {
        Inventory inventory = player.getInventory();
        if (inventory.isEmpty()) {
            inventory.load(saved);
            return;
        }
        Inventory temp = new Inventory(player);
        temp.load(saved);
        for (int i = 0; i < temp.getContainerSize(); i++) {
            ItemStack stack = temp.getItem(i);
            if (!stack.isEmpty()) {
                inventory.placeItemBackInInventory(stack);
            }
        }
    }

    /** What the dead player drops as experience: nothing, it is kept. */
    private static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && hasKept(player)) {
            event.setDroppedExperience(0);
            event.setCanceled(true);
        }
    }

    // ---- the respawn ----

    private static void onRespawnPosition(PlayerRespawnPositionEvent event) {
        if (event.isFromEndFight() || !(event.getEntity() instanceof ServerPlayer dead)) {
            return;
        }
        // Vanilla has already looked for the respawn point (finding none, as it was cleared) and copies it from this player next.
        if (hasKept(dead)) {
            restoreRespawn(dead, dead.getData(ModAttachments.MITOFUS_DEATH_KEPT.get()));
        }
        respawnTransition(dead).ifPresent(event::setDimensionTransition);
    }

    /**
     * Where a player who just died appears: in Mitofus at their last save point (or Covadonga's plaza). Empty (vanilla
     * respawn) if they did not die in Mitofus, or there is no Mitofus or nowhere to appear in it.
     */
    public static Optional<DimensionTransition> respawnTransition(ServerPlayer dead) {
        if (!MitofusWorld.is(dead.level())) {
            return Optional.empty();
        }
        Optional<ServerLevel> mitofus = MitofusWorld.of(dead.server);
        if (mitofus.isEmpty()) {
            Mitofus.LOGGER.warn("{} died in Mitofus but there is no Mitofus level: vanilla respawn", dead.getGameProfile().getName());
            return Optional.empty();
        }
        Optional<VrHelmetRules.Spawn> spawn = VrHelmetService.deathSpawnFor(dead, mitofus.get());
        if (spawn.isEmpty()) {
            Mitofus.LOGGER.warn("{} died in Mitofus and there is nowhere to reappear: vanilla respawn", dead.getGameProfile().getName());
            return Optional.empty();
        }
        BlockPos free = VrHelmetService.freeSpot(mitofus.get(), spawn.get().pos(), "respawn", dead);
        Vec3 center = new Vec3(free.getX() + 0.5, free.getY(), free.getZ() + 0.5);
        return Optional.of(new DimensionTransition(mitofus.get(), center, Vec3.ZERO, dead.getYRot(), 0.0f, DimensionTransition.DO_NOTHING));
    }

    /** The new player gets back what the old one had put aside (their inventory and experience). */
    private static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer fresh)
                || !(event.getOriginal() instanceof ServerPlayer original) || !hasKept(original)) {
            return;
        }
        CompoundTag kept = original.getData(ModAttachments.MITOFUS_DEATH_KEPT.get());
        giveBack(fresh, kept.getList(INVENTORY, Tag.TAG_COMPOUND));
        // Same as the keepInventory branch of ServerPlayer#restoreFrom.
        fresh.experienceLevel = original.experienceLevel;
        fresh.totalExperience = original.totalExperience;
        fresh.experienceProgress = original.experienceProgress;
        fresh.setScore(original.getScore());
        JUST_DIED.add(fresh.getUUID());
    }

    /**
     * Tells the player what the death was. Note: the {@code mitofus_death.second} warning is sent now, before the
     * disconnection, which happens on the next {@link #tick}; if {@code disconnect} fails (a warn is logged there)
     * there is neither disconnection nor lock, and the warning is not fulfilled.
     */
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !JUST_DIED.remove(player.getUUID())) {
            return;
        }
        if (!MitofusWorld.is(player.level())) {
            return;
        }
        DeathBalance balance = DeathBalance.current();
        Component line = !deaths(player).pendingDisconnect()
                ? Component.translatable("notification.mitofus.mitofus_death.first")
                : balance.reconnectLockMinutes() > 0
                ? Component.translatable("notification.mitofus.mitofus_death.second",
                        balance.doubleDeathWindowMinutes(), balance.reconnectLockMinutes())
                : Component.translatable("notification.mitofus.mitofus_death.second_unlocked", balance.doubleDeathWindowMinutes());
        NotificationEvents.send(player, Kind.MITOFUS_DEATH, List.of(line));
    }

    // ---- the tick after ----

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tick(player);
        }
    }

    /** One tick: the safety net for a death something else cancelled after us, and the pending disconnection. */
    public static void tick(ServerPlayer player) {
        if (!player.isAlive()) {
            return;
        }
        if (hasKept(player)) {
            // Alive with their things put aside: another mod cancelled the death after we heard it.
            Mitofus.LOGGER.warn("{} is alive but had their things put aside by a death: giving them back", player.getGameProfile().getName());
            undo(player);
        }
        MitofusDeaths state = deaths(player);
        if (!state.pendingDisconnect()) {
            return;
        }
        // The flag goes first: whatever happens next, this runs once.
        player.setData(ModAttachments.MITOFUS_DEATHS.get(), state.withoutPending());
        if (!MitofusWorld.is(player.level())) {
            return;
        }
        LogoutService.cancel(player, false);
        VrHelmetService.cancel(player, false);
        if (!VrHelmetService.disconnect(player, VrHelmetService.Reason.DEATH)) {
            Mitofus.LOGGER.warn("{} died twice in Mitofus but the disconnection did not happen", player.getGameProfile().getName());
            return;
        }
        // The disconnection happened: the reconnection lock starts now (only for this reason, and only if configured).
        long unlockAt = MitofusDeathRules.lockUntil(now(player.server),DeathBalance.current().lockTicks());
        if (unlockAt > 0) {
            player.setData(ModAttachments.MITOFUS_DEATHS.get(), deaths(player).withUnlockAt(unlockAt));
            Mitofus.LOGGER.info("{} is locked out of Mitofus until game time {}", player.getGameProfile().getName(), unlockAt);
        }
    }

    /**
     * Ticks the player still has to wait before connecting to Mitofus (0 = free to connect). Same clock as the
     * double-death window. A lock longer than the configured one (the config was lowered, or the clock went
     * backwards) is cut down to the configured one.
     */
    public static long lockRemaining(ServerPlayer player) {
        return MitofusDeathRules.lockRemaining(deaths(player).unlockAt(), now(player.server),
                DeathBalance.current().lockTicks());
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        JUST_DIED.remove(event.getEntity().getUUID());
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        JUST_DIED.clear();
    }

    private static void onServerStarting(ServerStartingEvent event) {
        JUST_DIED.clear();
    }
}
