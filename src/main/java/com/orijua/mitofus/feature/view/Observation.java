package com.orijua.mitofus.feature.view;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.notification.NotificationEvents;
import com.orijua.mitofus.feature.notification.SystemNotification;
import com.orijua.mitofus.feature.quest.goal.GoalTracker;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Observing to grow View: while a player sneaks looking at an NPC or a monster, a bar fills on the action bar; after
 * {@link ViewRules#OBSERVE_TICKS} of steady looking he gains a point. Each target teaches once per Minecraft day.
 * Crossing a detail threshold opens a system window saying what can be seen now.
 * <p>
 * Targets are aimed at with a margin around their hitbox that grows with their speed, and a short look grace
 * ({@link ViewRules#lookGraceTicks}) keeps the progress when the target slips out of sight; the grace adds no study
 * time. Only the progress of the bar is kept in memory (stopping sneaking, logging out, another target or a long look
 * away starts it again, and so does leaving Mitofus); what was already learned
 * today is saved with the player ({@link ModAttachments#VIEW_LEARNED}), so logging out doesn't reset the day.
 */
public final class Observation {
    /** Milestone of every View point gained by observing. */
    public static final ResourceLocation OBSERVED = Mitofus.id("observed");
    private static final int BAR_SEGMENTS = 10;

    /** Who a player is studying, for how long, ticks in a row without seeing it, and the grace allowed (from its speed). */
    private record Study(UUID target, int ticks, int missed, int grace) {
    }

    private static final Map<UUID, Study> STUDYING = new HashMap<>();

    private Observation() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(Observation::onTick);
        gameBus.addListener(Observation::onLogout);
    }

    public static int view(Player player) {
        return player.getData(ModAttachments.VIEW);
    }

    /** What NPC windows reveal by: View plus what a sword of observation in the main hand adds. */
    public static int effectiveView(Player player) {
        return view(player) + (player.getMainHandItem().getItem() instanceof ViewSwordItem sword ? sword.viewBonus() : 0);
    }

    /** Ticks of steady looking for a point: shorter with a sword of observation in the main hand, and with more View. */
    public static int observeTicks(Player player) {
        int base = player.getMainHandItem().getItem() instanceof ViewSwordItem sword ? sword.observeTicks() : ViewRules.OBSERVE_TICKS;
        return ViewRules.observeTicks(base, view(player));
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!MitofusWorld.is(player.level()) || player.isSpectator() || !player.isShiftKeyDown()) {
                stop(player);
            } else {
                observe(player, lookedAt(player));
            }
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STUDYING.remove(event.getEntity().getUUID());
    }

    /** Who the player is observing now: the creature in sight while sneaking; null when spectating or not sneaking. */
    public static Entity target(ServerPlayer player) {
        return !player.isSpectator() && player.isShiftKeyDown() ? lookedAt(player) : null;
    }

    /**
     * The living creature (not a player) the player looks at, within reach and in sight, or null. Each candidate's hitbox
     * is widened by {@link #aimMargin(Entity)}; the nearest cut of the view ray wins among the candidates that are in
     * line of sight (distances are squared). With the eye inside the real hitbox the distance is 0; inside only the
     * widened one, the real hitbox must be looked at.
     */
    public static LivingEntity lookedAt(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 reach = player.getViewVector(1.0f).scale(ViewRules.REACH);
        Vec3 end = eye.add(reach);
        AABB area = player.getBoundingBox().expandTowards(reach).inflate(Math.max(1.0, ViewRules.AIM_MARGIN_MAX));
        LivingEntity best = null;
        double bestDistance = ViewRules.REACH * ViewRules.REACH;
        for (Entity entity : player.level().getEntities(player, area, Observation::observable)) {
            AABB real = entity.getBoundingBox();
            AABB box = real.inflate(aimMargin(entity));
            double distance;
            if (real.contains(eye)) {
                distance = 0.0;
            } else {
                Optional<Vec3> cut = box.clip(eye, end);
                if (box.contains(eye) || cut.isEmpty()) {
                    cut = real.clip(eye, end);
                }
                if (cut.isEmpty()) {
                    continue;
                }
                distance = eye.distanceToSqr(cut.get());
            }
            if (ViewRules.inReach(distance) && distance <= bestDistance && player.hasLineOfSight(entity)) {
                bestDistance = distance;
                best = (LivingEntity) entity;
            }
        }
        return best;
    }

    /** Whether an entity can be observed: a living creature that is not a player and is alive. */
    public static boolean observable(Entity entity) {
        return entity instanceof LivingEntity && !(entity instanceof Player) && entity.isAlive();
    }

    /** Blocks the target moved horizontally in the last tick. */
    public static double horizontalSpeed(Entity entity) {
        return Math.hypot(entity.getX() - entity.xo, entity.getZ() - entity.zo);
    }

    /** Extra blocks around the target's hitbox when aiming at it: more for fast ones. */
    public static double aimMargin(Entity entity) {
        return ViewRules.aimMargin(horizontalSpeed(entity));
    }

    /** Forgets the bar of a player at once (stopped sneaking, logged out). */
    public static void stop(ServerPlayer player) {
        STUDYING.remove(player.getUUID());
    }

    /**
     * One tick of observing {@code target} (null: not observing). Returns whether this tick gave a point. Public so tests
     * can drive it without a real client.
     */
    public static boolean observe(ServerPlayer player, Entity target) {
        return observe(player, target, player.level().getGameTime());
    }

    /** As {@link #observe(ServerPlayer, Entity)} at a given game time (tests use it to cross days without touching the world's clock). */
    public static boolean observe(ServerPlayer player, Entity target, long gameTime) {
        Study study = STUDYING.get(player.getUUID());
        if (target == null) {
            if (study != null) {
                int missed = study.missed() + 1;
                if (ViewRules.keepsProgress(missed, study.grace())) {
                    STUDYING.put(player.getUUID(), new Study(study.target(), study.ticks(), missed, study.grace()));
                } else {
                    stop(player);
                }
            }
            return false;
        }
        long today = ViewRules.day(gameTime);
        int ticks = study != null && study.target().equals(target.getUUID()) ? study.ticks() + 1 : 1;
        int grace = ViewRules.lookGraceTicks(horizontalSpeed(target));
        STUDYING.put(player.getUUID(), new Study(target.getUUID(), ticks, 0, grace));
        if (player.getData(ModAttachments.VIEW_LEARNED).has(target.getUUID(), today)) {
            if (ticks == 1) {
                player.displayClientMessage(Component.translatable("message.mitofus.observed_today", target.getDisplayName()), true);
            }
            return false;
        }
        int needed = observeTicks(player);
        if (ticks < needed) {
            if (ticks % 10 == 1) {
                double fraction = ticks / (double) needed;
                player.displayClientMessage(Component.translatable("message.mitofus.observing", target.getDisplayName(),
                        bar(fraction)), true);
                // A soft tick that rises as the bar fills.
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.4f, 0.8f + (float) fraction);
            }
            return false;
        }
        player.setData(ModAttachments.VIEW_LEARNED, player.getData(ModAttachments.VIEW_LEARNED).with(target.getUUID(), today));
        // Still studying the same target: looking on doesn't replace the "gained" message with "already observed today".
        STUDYING.put(player.getUUID(), new Study(target.getUUID(), ticks, 0, grace));
        gain(player, target);
        return true;
    }

    private static void gain(ServerPlayer player, Entity target) {
        int after = raise(player, 1);
        player.displayClientMessage(Component.translatable("message.mitofus.view_gained", target.getDisplayName(), after), true);
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.4f);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.2f);
        GoalTracker.milestone(player, OBSERVED);
    }

    /** Adds {@code amount} View points (notifying a change of detail level); returns the new total. */
    public static int raise(ServerPlayer player, int amount) {
        int before = view(player);
        int after = before + amount;
        player.setData(ModAttachments.VIEW, after);
        ViewRules.Detail detail = ViewRules.detail(after);
        if (detail != ViewRules.detail(before)) {
            NotificationEvents.send(player, SystemNotification.Kind.VIEW, List.of(
                    Component.translatable("notification.mitofus.view.body", after),
                    Component.translatable("notification.mitofus.view." + detail.name().toLowerCase())));
        }
        return after;
    }

    private static String bar(double fraction) {
        int filled = (int) Math.round(fraction * BAR_SEGMENTS);
        return "▮".repeat(filled) + "▯".repeat(BAR_SEGMENTS - filled);
    }

    /** For tests: forget what the player learned today. */
    public static void forget(Player player) {
        STUDYING.remove(player.getUUID());
        player.removeData(ModAttachments.VIEW_LEARNED);
    }
}
