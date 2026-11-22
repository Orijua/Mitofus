package com.orijua.mitofus.feature.quest;

import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.notification.NotificationEvents;
import com.orijua.mitofus.feature.notification.SystemNotification.Kind;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.orijua.mitofus.feature.progression.ProgressionService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quests with a time limit ({@code time_limit_days}): the clock is the overworld's day time, so sleeping through the
 * night counts as time passing. Once a second each player's deadlines are checked: in the real world the clock is stopped (every deadline is pushed
 * back by the time spent there), in Mitofus with one day left a system window warns them, and when the time runs out
 * the quest fails for good.
 */
public final class QuestDeadlines {
    /** How often deadlines are checked. */
    static final int CHECK_TICKS = 20;
    /** The warning comes with this much time left: one day. */
    public static final long WARNING_TICKS = QuestDefinition.DAY_TICKS;

    /** The overworld time of the last check of each player who is outside Mitofus, to measure how long they stayed out. */
    private static final Map<UUID, Long> OUTSIDE_SINCE = new HashMap<>();

    private QuestDeadlines() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(QuestDeadlines::onTick);
        gameBus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> OUTSIDE_SINCE.remove(event.getEntity().getUUID()));
    }

    /**
     * How long the clock has to wait for {@code player}, who is outside Mitofus: the time since the last check there (one
     * check interval the first time). It is 0 inside Mitofus. Time spent offline doesn't count as outside.
     */
    static long outsideTicks(UUID player, boolean inMitofus, long now) {
        if (inMitofus) {
            OUTSIDE_SINCE.remove(player);
            return 0;
        }
        Long since = OUTSIDE_SINCE.put(player, now);
        return Math.max(0, since == null ? CHECK_TICKS : now - since);
    }

    /** The quest clock: the overworld's day time (also on the client, where only the current level is known). */
    public static long now(Player player) {
        Level level = player.level();
        if (level.getServer() != null) {
            Level overworld = level.getServer().overworld();
            return overworld.getDayTime();
        }
        return level.getDayTime();
    }

    /** Whole days and hours left until {@code deadline}, for the "N d M h left" text; never negative. */
    public static Component timeLeft(long deadline, long now) {
        long left = Math.max(0, deadline - now);
        long days = left / QuestDefinition.DAY_TICKS;
        long hours = left % QuestDefinition.DAY_TICKS / (QuestDefinition.DAY_TICKS / 24);
        return Component.translatable("quest.mitofus.time_left", days, hours);
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % CHECK_TICKS == 0) {
            check(player);
            LoanGuard.purge(player);
        }
    }

    /** Warns about the quests on their last day and fails those whose time ran out. */
    public static void check(ServerPlayer player) {
        long now = now(player);
        boolean inMitofus = MitofusWorld.is(player.level());
        long outside = outsideTicks(player.getUUID(), inMitofus, now);
        PlayerQuests quests = QuestService.get(player);
        if (quests.deadlines().isEmpty()) {
            return;
        }
        if (!inMitofus) {
            // the quests only run in Mitofus: in the real world the clock is stopped, the deadline moves on with the time
            if (outside > 0) {
                player.setData(ModAttachments.PLAYER_QUESTS, quests.shiftDeadlines(outside));
            }
            return;
        }
        for (ResourceLocation id : quests.overdue(now)) {
            fail(player, id);
        }
        quests = QuestService.get(player);
        List<ResourceLocation> soon = quests.dueSoon(now, WARNING_TICKS);
        for (ResourceLocation id : soon) {
            quests = quests.warn(id);
            Definitions.get(player.level(), ModDatapackRegistries.QUEST, id).ifPresent(quest -> NotificationEvents.send(player,
                    Kind.QUEST_WARNING, List.of(quest.name(), Component.translatable("notification.mitofus.quest_warning.body"))));
        }
        if (!soon.isEmpty()) {
            player.setData(ModAttachments.PLAYER_QUESTS, quests);
        }
    }

    /**
     * The quest fails for good (its time ran out, or the player lost it, like Velasco's contest): it closes, it can never
     * be taken again, and what was lent for it goes back.
     */
    public static void fail(ServerPlayer player, ResourceLocation id) {
        player.setData(ModAttachments.PLAYER_QUESTS, QuestService.get(player).fail(id));
        QuestService.reclaimLoans(player, id);
        Definitions.get(player.level(), ModDatapackRegistries.QUEST, id).ifPresent(quest -> {
            NotificationEvents.send(player, Kind.QUEST_FAILED, List.of(quest.name()));
            player.displayClientMessage(Component.translatable("message.mitofus.quest_failed", quest.name()), false);
            if (quest.failLevelPenalty() > 0) {
                int lost = ProgressionService.loseLevels(player, quest.failLevelPenalty());
                if (lost > 0) {
                    player.displayClientMessage(Component.translatable("message.mitofus.quest_failed_levels", lost), false);
                }
            }
        });
        player.level().playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8f, 0.6f);
        NeoForge.EVENT_BUS.post(new QuestFailedEvent(player, id));
    }
}
