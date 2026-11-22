package com.orijua.mitofus.feature.vrhelmet;

import com.orijua.mitofus.core.config.RuleBalance;
import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModBlocks;
import net.minecraft.world.item.ItemStack;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.config.FatigueBalance;
import com.orijua.mitofus.feature.character.Characters;
import com.orijua.mitofus.feature.fatigue.FatigueRules;
import com.orijua.mitofus.feature.fatigue.FatigueService;
import com.orijua.mitofus.feature.character.WorldSide;
import com.orijua.mitofus.feature.world.MitofusWorld;
import com.orijua.mitofus.feature.world.WorldSites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entering and leaving Mitofus through the vrhelmet. Lying down starts a short "Connecting…" wait (cancelled by taking
 * damage, moving or walking away); when it ends the player appears in Mitofus, where they last disconnected or, the first time,
 * on Covadonga's plaza. Disconnecting puts them back beside their vrhelmet. Both trips look for a free spot near the wanted
 * one ({@link SafeSpot}) and are the only way across between the real world and Mitofus ({@link WorldSeparation}).
 * The {@code /mitofus logout} countdown ({@link LogoutService}), fatigue, the sleeping body and death all end up
 * calling {@link #disconnect} with their own {@link Reason} (dying in Mitofus does not take you out: you reappear there
 * with your things, and only a second death in the window disconnects you and locks the vrhelmet for a while: {@link #begin}
 * and {@link #connect} refuse while the lock lasts; see {@link MitofusDeathService}). A connection
 * in progress is forgotten when the player dies, respawns, changes dimension, logs out or the server stops, and on login the saved state is
 * reconciled with where the player really is.
 */
public final class VrHelmetService {
    /** The connections in progress, by player. Normally only the server thread touches it; it is concurrent just in case. */
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    /**
     * The helmet that was put on: it is held until the connection is done, then the body is placed (after the player has
     * arrived in Mitofus, never at the same time: a block appearing where the player stands moves them and the trip fails).
     */
    private record HelmetOn(VrHelmetTier tier, Optional<UUID> owner, net.minecraft.core.Direction facing) {
    }

    /** {@code helmet}: set when the connection was started by putting a helmet on; the body does not exist yet. */
    private record Pending(GlobalPos vrhelmet, int ticksLeft, Vec3 anchor, @Nullable HelmetOn helmet) {
        Pending tick() {
            return new Pending(vrhelmet, ticksLeft - 1, anchor, helmet);
        }
    }

    /** Why a player was taken out of Mitofus. */
    public enum Reason { MANUAL, DEBUG, LOGOUT, FATIGUE, DEATH }

    /** What lying down in a vrhelmet did. */
    public enum Begin { STARTED, ALREADY_CONNECTING, IN_MITOFUS, ALREADY_CONNECTED, NOT_OVERWORLD, NO_MITOFUS, TOO_TIRED, LOCKED, NOT_YOURS, NO_ROOM }

    /** The "still locked after your death" line, with the time left as mm:ss. */
    public static Component lockedMessage(ServerPlayer player) {
        return Component.translatable("message.mitofus.vrhelmet.locked",
                MitofusDeathRules.formatRemaining(MitofusDeathService.lockRemaining(player)));
    }

    private VrHelmetService() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(VrHelmetService::onTick);
        gameBus.addListener(VrHelmetService::onDamaged);
        gameBus.addListener(VrHelmetService::onLoggedOut);
        // LOWEST and not cancelled: a death something else prevented (the legend title) must not free the body.
        gameBus.addListener(EventPriority.LOWEST, VrHelmetService::onDeath);
        gameBus.addListener(VrHelmetService::onRespawn);
        gameBus.addListener(VrHelmetService::onChangedDimension);
        gameBus.addListener(VrHelmetService::onLoggedIn);
        gameBus.addListener(VrHelmetService::onServerStopping);
        gameBus.addListener(VrHelmetService::onServerStarting);
    }

    public static VrHelmetState state(ServerPlayer player) {
        return player.getData(ModAttachments.VR_HELMET.get());
    }

    /**
     * Putting a helmet on: the body lies down where the player stands (a body block of the helmet's tier, bound like the
     * helmet) and the connection starts. If it cannot start, nothing is placed and the helmet stays in hand; if it starts,
     * the helmet goes onto the body and comes back when the player disconnects or the wait is cancelled.
     */
    public static Begin putOn(ServerPlayer player, ItemStack helmet, VrHelmetTier tier) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        Optional<UUID> owner = Optional.ofNullable(helmet.get(ModDataComponents.VR_HELMET_OWNER.get()));
        Begin result;
        if (state(player).isConnected() || MitofusWorld.is(level) || isConnecting(player)) {
            result = begin(player, pos);
        } else if (!bodyFits(level, pos)) {
            result = Begin.NO_ROOM;
        } else if (VrHelmetOwnerRules.access(owner, player.getUUID()) == VrHelmetOwnerRules.Access.STRANGER) {
            result = Begin.NOT_YOURS;
        } else {
            // Nothing is placed yet: the body appears once the player has connected.
            result = begin(player, pos);
            if (result == Begin.STARTED) {
                HelmetOn on = new HelmetOn(tier, owner, player.getDirection().getOpposite());
                PENDING.computeIfPresent(player.getUUID(), (id, pending) -> new Pending(pending.vrhelmet(), pending.ticksLeft(), pending.anchor(), on));
                helmet.shrink(1);
            }
        }
        reportBegin(player, result);
        return result;
    }

    private static boolean bodyFits(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).canBeReplaced() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
    }

    /** The body lies down at {@code pos}, bound like the helmet (or to the player if the helmet was not bound yet). */
    private static void placeBody(ServerPlayer player, ServerLevel level, BlockPos pos, HelmetOn on) {
        level.setBlock(pos, ModBlocks.bodyOf(on.tier()).defaultBlockState().setValue(VrHelmetBlock.FACING, on.facing()), 3);
        if (level.getBlockEntity(pos) instanceof VrHelmetBlockEntity body) {
            body.bind(on.owner().orElse(player.getUUID()));
        }
        if (on.owner().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.bound"), true);
        }
    }

    /** The wait ended without a connection: the helmet that was held comes back to the player. */
    private static void giveBackHelmet(ServerPlayer player, HelmetOn on) {
        ItemStack helmet = HelmetItem.stackFor(on.tier(), on.owner());
        if (!player.getInventory().add(helmet)) {
            player.drop(helmet, false);
        }
    }

    /** The message of what putting the helmet on did (nothing when it started). */
    public static void reportBegin(ServerPlayer player, Begin result) {
        switch (result) {
            case IN_MITOFUS, ALREADY_CONNECTED -> player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.in_mitofus"), true);
            case NOT_OVERWORLD -> player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.only_overworld"), true);
            case NO_MITOFUS -> player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.no_mitofus"), true);
            case TOO_TIRED -> player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.too_tired",
                    Math.round(FatigueService.energy(player) * 100), Math.round(FatigueBalance.current().minEnergyToConnect() * 100)), true);
            case NOT_YOURS -> player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.not_yours"), true);
            case LOCKED -> player.displayClientMessage(lockedMessage(player), true);
            case NO_ROOM -> player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.no_room"), true);
            case ALREADY_CONNECTING, STARTED -> {
            }
        }
    }

    /**
     * The body is no longer needed (the player disconnected, or the wait was cancelled): the body block goes away and the
     * helmet it was holding is handed back to the player, bound to its owner.
     */
    public static void takeBodyAway(ServerPlayer player, GlobalPos body) {
        ServerLevel level = player.server.getLevel(body.dimension());
        if (level == null || !(level.getBlockState(body.pos()).getBlock() instanceof VrHelmetBlock block)) {
            return;
        }
        Optional<UUID> owner = level.getBlockEntity(body.pos()) instanceof VrHelmetBlockEntity entity ? entity.owner() : Optional.empty();
        level.removeBlock(body.pos(), false);
        ItemStack helmet = HelmetItem.stackFor(block.tier(), owner);
        if (!player.getInventory().add(helmet)) {
            player.drop(helmet, false);
        }
    }

    /** Lying down in the vrhelmet at {@code vrhelmet}: the connection starts. */
    public static Begin begin(ServerPlayer player, BlockPos vrhelmet) {
        MinecraftServer server = player.server;
        if (MitofusWorld.is(player.level())) {
            return Begin.IN_MITOFUS;
        }
        if (state(player).isConnected()) {
            return Begin.ALREADY_CONNECTED;
        }
        // Own design: the vrhelmet connects only from the overworld; the Nether and the End are the real world too, but
        // nobody lies down there.
        if (player.level().dimension() != Level.OVERWORLD) {
            return Begin.NOT_OVERWORLD;
        }
        if (MitofusWorld.of(server).isEmpty()) {
            return Begin.NO_MITOFUS;
        }
        // Own design: a vrhelmet is bound to the first player who uses it; for anyone else it does nothing.
        VrHelmetBlockEntity bound = player.level().getBlockEntity(vrhelmet) instanceof VrHelmetBlockEntity entity ? entity : null;
        if (bound != null && VrHelmetOwnerRules.access(bound.owner(), player.getUUID()) == VrHelmetOwnerRules.Access.STRANGER) {
            return Begin.NOT_YOURS;
        }
        if (PENDING.containsKey(player.getUUID())) {
            return Begin.ALREADY_CONNECTING;
        }
        // Own design: after the disconnection of a double death in Mitofus the vrhelmet stays locked for a while.
        if (MitofusDeathService.lockRemaining(player) > 0) {
            return Begin.LOCKED;
        }
        // Own design: below the minimum energy the vrhelmet refuses; the real world refills it.
        if (!FatigueRules.canConnect(FatigueBalance.current(), FatigueService.energy(player))) {
            return Begin.TOO_TIRED;
        }
        if (bound != null && bound.owner().isEmpty()) {
            bound.bind(player.getUUID());
            player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.bound"), true);
        }
        start(player, GlobalPos.of(player.level().dimension(), vrhelmet));
        return Begin.STARTED;
    }

    /** The wait begins (the checks of {@link #begin} are done): the player is lying in {@code vrhelmet}. */
    public static void start(ServerPlayer player, GlobalPos vrhelmet) {
        PENDING.put(player.getUUID(), new Pending(vrhelmet, RuleBalance.current().connectTicks(), player.position(), null));
        PacketDistributor.sendToPlayer(player, new VrHelmetConnectingPayload(RuleBalance.current().connectTicks()));
        player.level().playSound(null, vrhelmet.pos(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.4f);
    }

    public static boolean isConnecting(ServerPlayer player) {
        return PENDING.containsKey(player.getUUID());
    }

    /**
     * Stops a connection in progress (damage, moving, getting out of reach, the vrhelmet gone, death...). The "Connecting…"
     * screen is always closed; the "cancelled" message only if {@code tellPlayer}.
     */
    public static void cancel(ServerPlayer player, boolean tellPlayer) {
        cancel(player, tellPlayer ? "message.mitofus.vrhelmet.cancelled" : null);
    }

    private static void cancel(ServerPlayer player, @Nullable String messageKey) {
        Pending cancelled = PENDING.remove(player.getUUID());
        if (cancelled != null) {
            if (cancelled.helmet() != null) {
                giveBackHelmet(player, cancelled.helmet());
            }
            PacketDistributor.sendToPlayer(player, new VrHelmetConnectingPayload(0));
            if (messageKey != null) {
                player.displayClientMessage(Component.translatable(messageKey), true);
            }
        }
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tick(player);
        }
    }

    /** One tick of the player's connection, if any. */
    public static void tick(ServerPlayer player) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return;
        }
        if (!player.isAlive()) {
            cancel(player, false);
            return;
        }
        BlockPos pos = pending.vrhelmet().pos();
        boolean here = player.level().dimension() == pending.vrhelmet().dimension();
        boolean vrHelmetStillThere = here && (pending.helmet() != null || player.level().getBlockState(pos).getBlock() instanceof VrHelmetBlock);
        if (!vrHelmetStillThere) {
            cancel(player, true);
            return;
        }
        if (!VrHelmetRules.stayedStill(player.position().distanceToSqr(pending.anchor()))) {
            cancel(player, "message.mitofus.vrhelmet.cancelled_moved");
            return;
        }
        if (!VrHelmetRules.inReach(player.distanceToSqr(pos.getCenter()))) {
            cancel(player, true);
            return;
        }
        Pending next = pending.tick();
        if (next.ticksLeft() > 0) {
            PENDING.put(player.getUUID(), next);
            return;
        }
        PENDING.remove(player.getUUID());
        HelmetOn on = pending.helmet();
        if (on == null) {
            connect(player, pending.vrhelmet());
        } else if (!bodyFits(player.serverLevel(), pos)) {
            // Something took the place while waiting: the helmet comes back.
            PacketDistributor.sendToPlayer(player, new VrHelmetConnectingPayload(0));
            reportBegin(player, Begin.NO_ROOM);
            giveBackHelmet(player, on);
        } else {
            ServerLevel real = player.serverLevel();
            if (connect(player, pending.vrhelmet(), on.tier())) {
                // Connected: now the body lies down where the player was, in the real world.
                placeBody(player, real, pos, on);
            } else {
                giveBackHelmet(player, on);
            }
        }
    }

    /** Taking damage wakes you up before you are connected. */
    private static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getNewDamage() > 0 && event.getEntity() instanceof ServerPlayer player && isConnecting(player)) {
            cancel(player, true);
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // The player is leaving: nobody is left to close a screen for.
            PENDING.remove(player.getUUID());
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            died(player, event.getSource());
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            respawned(player);
        }
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cancel(player, true);
        }
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reconcile(player);
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        forgetAllConnections();
    }

    /** In case the previous server went down without stopping cleanly. */
    private static void onServerStarting(ServerStartingEvent event) {
        forgetAllConnections();
    }

    /** No connection in progress survives the server stopping. */
    public static void forgetAllConnections() {
        PENDING.clear();
    }

    /**
     * The player died: any connection in progress ends. If they died in Mitofus they reappear in Mitofus at their last
     * save point without losing anything, and the body stays in the vrhelmet ({@link MitofusDeathService}); a second
     * death in the window also disconnects them.
     */
    public static void died(ServerPlayer player) {
        died(player, null);
    }

    /** Same, knowing what killed them: a death at the hands of another player with PvP on can drop equipped pieces. */
    public static void died(ServerPlayer player, DamageSource source) {
        cancel(player, false);
        if (MitofusWorld.is(player.level())) {
            // The wait for the next rested bonus (RESTED_COOLDOWN) is kept on purpose: dying is not a way around it.
            player.setData(ModAttachments.RESTED_SECONDS.get(), 0);
            Mitofus.LOGGER.info("{} died in Mitofus: the body stays in the vrhelmet", player.getGameProfile().getName());
            MitofusDeathService.died(player, source);
        }
    }

    /**
     * The new entity has the same UUID: a connection of the old one must not resume. If it reappeared in the real world
     * while still connected (the reserve case: there was no Mitofus or no spot to reappear in), the body is released.
     */
    public static void respawned(ServerPlayer player) {
        cancel(player, false);
        reconcile(player);
    }

    /** On login, the saved state is brought in line with where the player is. The last save is never touched. */
    public static void reconcile(ServerPlayer player) {
        VrHelmetState state = state(player);
        switch (VrHelmetRules.coherence(state, MitofusWorld.is(player.level()))) {
            case CONNECTED_OUTSIDE -> {
                player.setData(ModAttachments.VR_HELMET.get(), state.withoutBody());
                // RESTED_COOLDOWN is kept: logging in outside Mitofus is not a way around the wait either.
                player.setData(ModAttachments.RESTED_SECONDS.get(), 0);
                Mitofus.LOGGER.warn("{} logged in outside Mitofus ({}) still connected to {}: the body is released",
                        player.getGameProfile().getName(), player.level().dimension().location(), state.vrhelmet().orElse(null));
            }
            case DISCONNECTED_INSIDE -> Mitofus.LOGGER.warn("{} logged in inside Mitofus ({}) without a body in a vrhelmet",
                    player.getGameProfile().getName(), player.level().dimension().location());
            case OK -> {
            }
        }
    }

    /** Where the player would appear in Mitofus now. */
    public static Optional<VrHelmetRules.Spawn> spawnFor(ServerPlayer player, ServerLevel mitofus) {
        return VrHelmetRules.spawn(state(player).lastSave(), WorldSites.landingFor(mitofus.getSeed()));
    }

    /** Where the player reappears in Mitofus after dying there: their residence, else as {@link #spawnFor}. */
    public static Optional<VrHelmetRules.Spawn> deathSpawnFor(ServerPlayer player, ServerLevel mitofus) {
        return VrHelmetRules.deathSpawn(residenceOf(player), state(player).lastSave(), WorldSites.landingFor(mitofus.getSeed()));
    }

    /** The place the player is resident of in Mitofus (a village chief assigns it), where they come back to life. */
    static Optional<BlockPos> residenceOf(ServerPlayer player) {
        return ResidenceService.posOf(player);
    }

    /** The tier of the vrhelmet block at {@code vrhelmet}; the basic one if there is no vrhelmet there. */
    public static VrHelmetTier tierAt(MinecraftServer server, GlobalPos vrhelmet) {
        ServerLevel level = server.getLevel(vrhelmet.dimension());
        if (level != null && level.getBlockState(vrhelmet.pos()).getBlock() instanceof VrHelmetBlock block) {
            return block.tier();
        }
        return VrHelmetTier.BASIC;
    }

    /** The body stays in {@code vrhelmet}; the player appears in Mitofus. Returns false if already connected or there is nowhere to appear. */
    public static boolean connect(ServerPlayer player, GlobalPos vrhelmet) {
        return connect(player, vrhelmet, tierAt(player.server, vrhelmet));
    }

    /** As {@link #connect(ServerPlayer, GlobalPos)}, with the tier given (a helmet's body does not exist yet). */
    private static boolean connect(ServerPlayer player, GlobalPos vrhelmet, VrHelmetTier tier) {
        if (state(player).isConnected()) {
            PacketDistributor.sendToPlayer(player, new VrHelmetConnectingPayload(0));
            Mitofus.LOGGER.warn("{} tried to connect while already connected", player.getGameProfile().getName());
            return false;
        }
        // The lock is checked again here: the wait of "Connecting…" can end after it started (or begin was skipped).
        if (MitofusDeathService.lockRemaining(player) > 0) {
            PacketDistributor.sendToPlayer(player, new VrHelmetConnectingPayload(0));
            player.displayClientMessage(lockedMessage(player), true);
            return false;
        }
        Optional<ServerLevel> mitofus = MitofusWorld.of(player.server);
        if (mitofus.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new VrHelmetConnectingPayload(0));
            return false;
        }
        Optional<VrHelmetRules.Spawn> spawn = spawnFor(player, mitofus.get());
        if (spawn.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.nowhere"), true);
            PacketDistributor.sendToPlayer(player, new VrHelmetConnectingPayload(0));
            return false;
        }
        if (!Characters.canCross(player, WorldSide.MITOFUS)) {
            player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.character_unreadable"), true);
            PacketDistributor.sendToPlayer(player, new VrHelmetConnectingPayload(0));
            Mitofus.LOGGER.error("{} cannot connect: the stored character cannot be read", player.getGameProfile().getName());
            return false;
        }
        GlobalPos exit = GlobalPos.of(player.level().dimension(), player.blockPosition());
        // The state is saved before the trip (the arrival events may need it) and put back if the trip does not happen.
        VrHelmetState before = state(player);
        player.setData(ModAttachments.VR_HELMET.get(), before.connected(vrhelmet, exit));
        BlockPos at = spawn.get().pos();
        Mitofus.LOGGER.info("{} connects to Mitofus at {} ({})", player.getGameProfile().getName(), at, spawn.get().source());
        BlockPos free = freeSpot(mitofus.get(), at, "connection", player);
        ServerLevel from = player.serverLevel();
        Vec3 fromPos = player.position();
        float fromYaw = player.getYRot();
        Characters.Result swap = WorldSeparation.cross(player, () ->
                player.teleportTo(mitofus.get(), free.getX() + 0.5, free.getY(), free.getZ() + 0.5, player.getYRot(), 0.0f));
        if (MitofusWorld.is(player.level()) && swap == Characters.Result.FAILED) {
            // Arrived, but still carrying the real world's things: back where they were; nothing moved in the vault.
            goBack(player, from, fromPos, fromYaw);
            player.setData(ModAttachments.VR_HELMET.get(), before);
            PacketDistributor.sendToPlayer(player, new VrHelmetConnectingPayload(0));
            player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.character_unreadable"), true);
            Mitofus.LOGGER.error("{} could not swap characters on arriving in Mitofus: the connection is undone",
                    player.getGameProfile().getName());
            return false;
        }
        if (!MitofusWorld.is(player.level())) {
            // Something (another mod) cancelled the trip: the player is still in the real world.
            player.setData(ModAttachments.VR_HELMET.get(), before);
            PacketDistributor.sendToPlayer(player, new VrHelmetConnectingPayload(0));
            player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.nowhere"), true);
            Mitofus.LOGGER.warn("{} did not arrive in Mitofus: the connection is undone", player.getGameProfile().getName());
            return false;
        }
        player.resetFallDistance();
        player.setData(ModAttachments.VR_HELMET_TIER.get(), tier);
        FatigueService.onConnected(player);
        player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.connected"), true);
        return true;
    }

    /**
     * Takes the player out of Mitofus and back beside their vrhelmet (or, without a known one, to their respawn point).
     * Where they were becomes where the next connection starts. Returns false if they were not in Mitofus, or if the trip did not happen (the state is then put back).
     */
    public static boolean disconnect(ServerPlayer player, Reason reason) {
        if (!MitofusWorld.is(player.level())) {
            return false;
        }
        // Fatigue forces the disconnection; every other reason is the player's own choice and can be refused.
        boolean forced = reason == Reason.FATIGUE;
        if (!forced && !Characters.canCross(player, WorldSide.REAL)) {
            player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.character_unreadable"), true);
            Mitofus.LOGGER.error("{} cannot disconnect: the stored real character cannot be read", player.getGameProfile().getName());
            return false;
        }
        VrHelmetState before = state(player);
        player.setData(ModAttachments.VR_HELMET.get(), before.disconnected(player.blockPosition()));
        Mitofus.LOGGER.info("{} disconnects from Mitofus ({})", player.getGameProfile().getName(), reason);

        Destination destination = destination(player.server, before.exit(), player.getRespawnDimension(), player.getRespawnPosition());
        ServerLevel target = destination.level();
        BlockPos free = freeSpot(target, destination.pos(), "disconnection", player);
        ServerLevel from = player.serverLevel();
        Vec3 fromPos = player.position();
        float fromYaw = player.getYRot();
        Characters.Result swap = WorldSeparation.cross(player, () ->
                player.teleportTo(target, free.getX() + 0.5, free.getY(), free.getZ() + 0.5, player.getYRot(), 0.0f));
        if (player.level() == target && swap == Characters.Result.FAILED) {
            if (!forced) {
                // A voluntary disconnection is undone: the player stays in Mitofus with their Mitofus things.
                goBack(player, from, fromPos, fromYaw);
                player.setData(ModAttachments.VR_HELMET.get(), before);
                player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.character_unreadable"), true);
                Mitofus.LOGGER.error("{} could not swap characters on leaving Mitofus: the disconnection is undone",
                        player.getGameProfile().getName());
                return false;
            }
            // A forced one (fatigue) goes through anyway; the swap is tried again at the next dimension change, respawn or login.
            player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.character_unreadable"), true);
            Mitofus.LOGGER.error("{} left Mitofus still carrying its things (forced by {}): the swap is retried later",
                    player.getGameProfile().getName(), reason);
        }
        if (player.level() != target) {
            // Something (another mod) cancelled the trip: the player is still in Mitofus, so the body stays where it was.
            player.setData(ModAttachments.VR_HELMET.get(), before);
            Mitofus.LOGGER.warn("{} did not leave Mitofus: the disconnection is undone", player.getGameProfile().getName());
            return false;
        }
        player.resetFallDistance();
        before.vrhelmet().ifPresent(body -> takeBodyAway(player, body));
        // The tier stays (the recovery keeps using it); the rested bonus does not outlive the connection, but the wait
        // for the next one (RESTED_COOLDOWN) does: disconnecting and connecting again must not restart the bonus.
        player.setData(ModAttachments.RESTED_SECONDS.get(), 0);
        String message = switch (reason) {
            case FATIGUE -> "message.mitofus.vrhelmet.disconnected_tired";
            case DEATH -> "message.mitofus.vrhelmet.disconnected_death";
            case MANUAL, DEBUG, LOGOUT -> "message.mitofus.vrhelmet.disconnected";
        };
        player.displayClientMessage(Component.translatable(message), true);
        return true;
    }

    /** Takes the player back to where a crossing started (the swap of characters failed, so they are undoing the trip). */
    private static void goBack(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw) {
        WorldSeparation.cross(player, () -> player.teleportTo(level, pos.x, pos.y, pos.z, yaw, 0.0f));
    }

    /** Where a disconnection leads: a level and the spot wanted in it (before looking for a free one nearby). */
    public record Destination(ServerLevel level, BlockPos pos) {
    }

    /**
     * Where a player leaving Mitofus goes. Beside their vrhelmet if the exit point is known and its dimension exists;
     * otherwise to their respawn point, or the world's spawn. A respawn point in Mitofus (a bed slept in there) is
     * never used: that would be a trip from Mitofus to Mitofus, so the overworld's spawn is used instead.
     */
    public static Destination destination(MinecraftServer server, Optional<GlobalPos> exit,
                                          ResourceKey<Level> respawnDimension, @Nullable BlockPos respawnPosition) {
        ServerLevel exitLevel = exit.map(GlobalPos::dimension).map(server::getLevel).orElse(null);
        if (exit.isPresent() && exitLevel != null) {
            return new Destination(exitLevel, exit.get().pos());
        }
        ServerLevel level = Optional.ofNullable(server.getLevel(respawnDimension)).orElse(server.overworld());
        if (MitofusWorld.is(level)) {
            level = server.overworld();
        }
        BlockPos pos = respawnPosition != null && level.dimension() == respawnDimension ? respawnPosition : level.getSharedSpawnPos();
        return new Destination(level, pos);
    }

    /** The nearest free spot to {@code wanted} (see {@link SafeSpot}); if there is none, {@code wanted} itself. */
    public static BlockPos freeSpot(ServerLevel level, BlockPos wanted, String what, ServerPlayer player) {
        Optional<BlockPos> found = SafeSpot.find(new LevelProbe(level), wanted);
        if (found.isEmpty()) {
            Mitofus.LOGGER.warn("No free spot near {} in {} for the {} of {}: using it as it is", wanted,
                    level.dimension().location(), what, player.getGameProfile().getName());
            return wanted;
        }
        return found.get();
    }
}
