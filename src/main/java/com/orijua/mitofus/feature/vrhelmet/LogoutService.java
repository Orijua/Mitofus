package com.orijua.mitofus.feature.vrhelmet;

import com.orijua.mitofus.core.config.RuleBalance;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.quest.QuestDeadlines;
import com.orijua.mitofus.feature.quest.QuestService;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /mitofus logout} (or the "Disconnect" button of the Mitofus window): a 3-second countdown inside Mitofus that ends in {@link VrHelmetService#disconnect}. It is
 * refused after recent combat (damage dealt or taken) and cancelled by moving, damage taken, death, respawn, leaving
 * the server or changing dimension (moving is measured from where the countdown began; see
 * {@link VrHelmetRules#stayedStill}). Damage dealt during the countdown does not cancel it.
 */
public final class LogoutService {
    /** Ticks left of the countdown and where the player stood when it began, by player. */
    private static final Map<UUID, Countdown> COUNTDOWN = new ConcurrentHashMap<>();

    private record Countdown(int ticksLeft, Vec3 anchor, boolean force) {
    }

    /** What asking to log out did. */
    public enum Start { STARTED, ALREADY_RUNNING, NOT_IN_MITOFUS, IN_DANGER, QUEST_DEADLINE }

    private LogoutService() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(LogoutService::onTick);
        gameBus.addListener(LogoutService::onDamaged);
        gameBus.addListener(LogoutService::onDeath);
        gameBus.addListener(LogoutService::onRespawn);
        gameBus.addListener(LogoutService::onChangedDimension);
        gameBus.addListener(LogoutService::onLoggedOut);
        gameBus.addListener(LogoutService::onServerStopping);
        gameBus.addListener(LogoutService::onServerStarting);
        gameBus.addListener(LogoutService::onRegisterCommands);
    }

    public static Start start(ServerPlayer player) {
        return start(player, false);
    }

    /**
     * Starts the countdown. A quest with a deadline running refuses it unless {@code force}: forcing the exit fails those
     * quests (with their penalty) when the player really disconnects. Fatigue, a double death or a moved body never go
     * through here, so they stay free.
     */
    public static Start start(ServerPlayer player, boolean force) {
        if (!MitofusWorld.is(player.level())) {
            return Start.NOT_IN_MITOFUS;
        }
        if (isCountingDown(player)) {
            return Start.ALREADY_RUNNING;
        }
        if (LogoutRules.inDanger(player.server.getTickCount(), CombatClock.last(player))) {
            return Start.IN_DANGER;
        }
        if (!force && !QuestService.get(player).deadlines().isEmpty()) {
            return Start.QUEST_DEADLINE;
        }
        COUNTDOWN.put(player.getUUID(), new Countdown(RuleBalance.current().logoutTicks(), player.position(), force));
        announce(player, RuleBalance.current().logoutTicks());
        return Start.STARTED;
    }

    /** Starts the countdown and tells the player why not when it can't start (command and window button). */
    public static Start request(ServerPlayer player) {
        return request(player, false);
    }

    public static Start request(ServerPlayer player, boolean force) {
        Start result = start(player, force);
        switch (result) {
            case NOT_IN_MITOFUS -> player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.in_real_world"), true);
            case ALREADY_RUNNING -> player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.logout_in_progress"), true);
            case IN_DANGER -> player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.logout_in_danger"), true);
            case QUEST_DEADLINE -> player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.logout_quest_deadline",
                    deadlineQuestName(player)), true);
            case STARTED -> {
            }
        }
        return result;
    }

    /** The name of the first quest (by id) that has a deadline running, for the refusal message. */
    private static Component deadlineQuestName(ServerPlayer player) {
        return QuestService.get(player).deadlines().keySet().stream().sorted().findFirst()
                .map(id -> Definitions.get(player.level(), ModDatapackRegistries.QUEST, id)
                        .map(quest -> quest.name()).orElse(Component.literal(id.toString())))
                .orElse(Component.empty());
    }

    public static boolean isCountingDown(ServerPlayer player) {
        return COUNTDOWN.containsKey(player.getUUID());
    }

    /** One tick of the player's countdown, if any. */
    public static void tick(ServerPlayer player) {
        Countdown countdown = COUNTDOWN.get(player.getUUID());
        if (countdown == null) {
            return;
        }
        if (!player.isAlive() || !MitofusWorld.is(player.level())) {
            cancel(player, false);
            return;
        }
        if (!VrHelmetRules.stayedStill(player.position().distanceToSqr(countdown.anchor()))) {
            cancel(player, "message.mitofus.vrhelmet.logout_cancelled_moved");
            return;
        }
        int next = countdown.ticksLeft() - 1;
        if (next == 0) {
            // Remove first: disconnect changes dimension, and that would otherwise cancel us half way.
            COUNTDOWN.remove(player.getUUID());
            if (countdown.force()) {
                // The player chose to leave in the middle of a timed quest: it fails, with its penalty.
                for (var id : QuestService.get(player).deadlines().keySet().stream().sorted().toList()) {
                    QuestDeadlines.fail(player, id);
                }
            }
            VrHelmetService.disconnect(player, VrHelmetService.Reason.LOGOUT);
            return;
        }
        COUNTDOWN.put(player.getUUID(), new Countdown(next, countdown.anchor(), countdown.force()));
        announce(player, next);
    }

    private static void announce(ServerPlayer player, int ticksLeft) {
        OptionalInt seconds = LogoutRules.announcement(ticksLeft);
        if (seconds.isPresent()) {
            player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.logout_countdown", seconds.getAsInt()), true);
        }
    }

    /** Stops the countdown; the "cancelled" message only if {@code tell}. */
    public static void cancel(ServerPlayer player, boolean tell) {
        cancel(player, tell ? "message.mitofus.vrhelmet.logout_cancelled" : null);
    }

    private static void cancel(ServerPlayer player, @Nullable String messageKey) {
        if (COUNTDOWN.remove(player.getUUID()) != null && messageKey != null) {
            player.displayClientMessage(Component.translatable(messageKey), true);
        }
    }

    public static void markCombat(ServerPlayer player, long tick) {
        CombatClock.mark(player, tick);
    }

    /** Forgets everything about this player. */
    public static void forget(ServerPlayer player) {
        COUNTDOWN.remove(player.getUUID());
        CombatClock.forget(player);
    }

    public static void forgetAll() {
        COUNTDOWN.clear();
        CombatClock.forgetAll();
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tick(player);
        }
    }

    private static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer victim) {
            markCombat(victim, victim.server.getTickCount());
            if (isCountingDown(victim)) {
                cancel(victim, true);
            }
        }
        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer attacker && source != event.getEntity()) {
            markCombat(attacker, attacker.server.getTickCount());
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cancel(player, false);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cancel(player, false);
        }
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cancel(player, true);
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            forget(player);
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        forgetAll();
    }

    private static void onServerStarting(ServerStartingEvent event) {
        forgetAll();
    }

    /** /mitofus logout: for everyone (the op-only parts of /mitofus are their own literals). */
    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mitofus")
                .then(Commands.literal("logout")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            Start result = request(player);
                            return result == Start.STARTED ? 1 : 0;
                        })
                        .then(Commands.literal("force")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    Start result = request(player, true);
                                    return result == Start.STARTED ? 1 : 0;
                                }))));
    }
}
