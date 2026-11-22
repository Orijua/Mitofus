package com.orijua.mitofus.feature.eibar;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModEntities;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.notification.NotificationEvents;
import com.orijua.mitofus.feature.notification.SystemNotification.Kind;
import com.orijua.mitofus.feature.sancho.NunoBetrayal;
import com.orijua.mitofus.feature.quest.QuestAcceptedEvent;
import com.orijua.mitofus.feature.quest.QuestCompletedEvent;
import com.orijua.mitofus.feature.quest.QuestDeadlines;
import com.orijua.mitofus.feature.quest.QuestDefinition;
import com.orijua.mitofus.feature.quest.QuestService;
import com.orijua.mitofus.feature.quest.goal.GoalTracker;
import com.orijua.mitofus.feature.quest.goal.MilestoneGoal;
import com.orijua.mitofus.feature.smithing.ItemForgedEvent;
import com.orijua.mitofus.feature.world.Eibar;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Optional;

/**
 * The item production contest in Eibar's square: after "The Blacksmith's Rage", the Compañía de Levante
 * hires Mencia to beat Velasco's side, and Sancho's successor forges for Velasco. Velasco gives the quest
 * ({@link #VELASCOS_CONTEST}); accepting it brings Mencia to her anvil on the stage. Talking to her starts the contest:
 * {@link ContestRules#timeLimitTicks()} to forge one piece by hand at the stage, with materials from Velasco. That piece is
 * scored against her hidden roll (see {@link ContestRules}) and a system window shows both.
 * <p>
 * Winning counts {@link #CONTEST_WON}; claiming the quest at Velasco makes the player his disciple (the
 * {@link #VELASCOS_DISCIPLE} milestone, which earns the title) and rebuilds his smithy for the whole world. Losing, or
 * forging nothing in time, either fails the quest for good (the Levante keeps the smithy and Velasco stops teaching) or allows
 * a rematch the next game day, as the server config's {@code contestLoss} says. In the original story Valmont has Cid arrested
 * before the result and the residents rise up; that comes in phases 10-11.
 */
public final class VelascoContest {
    public static final ResourceLocation VELASCOS_CONTEST = Mitofus.id("velascos_contest");
    /** The player beat Mencia. */
    public static final ResourceLocation CONTEST_WON = Mitofus.id("contest_won");
    /** Velasco took the player as his disciple (claimed the contest quest). */
    public static final ResourceLocation VELASCOS_DISCIPLE = Mitofus.id("velascos_disciple");
    /** Forging this close to Mencia is forging on the stage (the two anvils are 4 blocks apart). */
    public static final double STAGE_REACH = 6.0;
    /** Velasco's materials for the piece: a Wolfram sword (2 ingots and a handle). */
    static final int CONTEST_INGOTS = 2;
    /** How far around a spot an Mencia already there counts. */
    private static final double SPOT_RANGE = 4.0;
    private static final int TICK_EVERY = 20;
    /** Her hammer rings this often while someone competes. */
    private static final int HAMMER_EVERY = 40;

    private VelascoContest() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(VelascoContest::onAccepted);
        gameBus.addListener(VelascoContest::onCompleted);
        gameBus.addListener(VelascoContest::onForged);
        gameBus.addListener(VelascoContest::onTick);
    }

    public static ContestState state(Player player) {
        return player.getData(ModAttachments.VELASCO_CONTEST);
    }

    private static void setState(ServerPlayer player, ContestState state) {
        player.setData(ModAttachments.VELASCO_CONTEST, state);
    }

    /** The quest clock's day (sleeping counts). Both sides. */
    static long today(Player player) {
        return QuestDeadlines.now(player) / QuestDefinition.DAY_TICKS;
    }

    /** Contest won, not yet claimed. From the synced counters: both sides. */
    static boolean hasWon(Player player) {
        return player.getData(ModAttachments.PLAYER_COUNTERS).get(MilestoneGoal.counterKey(CONTEST_WON)) > 0;
    }

    /** On the quest, not competing now, not won yet, and not waiting for tomorrow's rematch. From synced data: both sides. */
    public static boolean canStart(Player player) {
        ContestState state = state(player);
        return QuestService.get(player).isActive(VELASCOS_CONTEST) && !state.running() && !hasWon(player)
                && ContestRules.canRematch(state.lostDay(), today(player));
    }

    /** Whether any player online has the contest quest: Mencia waits on the stage only for them. */
    static boolean anyoneInTheContest(MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream().anyMatch(player -> QuestService.get(player).isActive(VELASCOS_CONTEST));
    }

    private static void onAccepted(QuestAcceptedEvent event) {
        if (event.quest().equals(VELASCOS_CONTEST)) {
            event.giver().ifPresent(velasco -> {
                summonMencia(event.player().serverLevel(), velasco);
                event.player().displayClientMessage(Component.translatable("message.mitofus.velascos_contest_accepted"), false);
            });
        }
    }

    /**
     * Mencia at her anvil on the stage of the Eibar that {@code velasco} lives in (next to him where there is no
     * Eibar, as in a test), unless she is already there.
     */
    static MenciaNpc summonMencia(ServerLevel level, Entity velasco) {
        BlockPos spot = stageSpot(level, velasco.blockPosition())
                .orElseGet(() -> NunoBetrayal.spotsAround(level, velasco.blockPosition(), 1).stream().findFirst().orElse(velasco.blockPosition()));
        List<MenciaNpc> there = level.getEntitiesOfClass(MenciaNpc.class, new AABB(spot).inflate(SPOT_RANGE), Entity::isAlive);
        if (!there.isEmpty()) {
            return there.getFirst();
        }
        MenciaNpc mencia = ModEntities.MENCIA.get().create(level);
        if (mencia != null) {
            mencia.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 180, 0);
            mencia.setCustomName(Component.translatable("entity.mitofus.mencia"));
            level.addFreshEntity(mencia);
        }
        return mencia;
    }

    /** Where Mencia stands on the stage of the Eibar around {@code near}. */
    private static Optional<BlockPos> stageSpot(ServerLevel level, BlockPos near) {
        StructureStart eibar = Eibar.at(level, near);
        if (!eibar.isValid()) {
            return Optional.empty();
        }
        return Eibar.piece(eibar, Eibar.SQUARE)
                .map(square -> square.at(Eibar.RIVAL_SPOT.getX(), Eibar.RIVAL_SPOT.getY(), Eibar.RIVAL_SPOT.getZ()));
    }

    /** What Mencia says when it isn't time to start: the confirmation window comes from the client otherwise. */
    public static void talkToMencia(ServerPlayer player) {
        ContestState state = state(player);
        String line;
        if (!QuestService.get(player).isActive(VELASCOS_CONTEST)) {
            line = QuestService.get(player).isCompleted(VELASCOS_CONTEST) ? "mencia_beaten" : "mencia_ignores";
        } else if (state.running()) {
            player.displayClientMessage(Component.translatable("message.mitofus.contest_running", clock(player, state)), true);
            return;
        } else if (hasWon(player)) {
            line = "contest_go_to_velasco";
        } else if (!ContestRules.canRematch(state.lostDay(), today(player))) {
            line = "mencia_tomorrow";
        } else {
            return;
        }
        player.displayClientMessage(Component.translatable("message.mitofus." + line), true);
    }

    /** The player confirmed: the clock starts, Mencia rolls her score and Velasco hands over the materials. */
    public static boolean start(ServerPlayer player) {
        return start(player, ContestRules.rivalScore(player.getRandom().nextDouble()));
    }

    /** As {@link #start(ServerPlayer)}, with Mencia's score fixed (for tests). */
    public static boolean start(ServerPlayer player, int rivalScore) {
        if (!canStart(player)) {
            return false;
        }
        setState(player, state(player).start(player.level().getGameTime(), rivalScore));
        give(player, new ItemStack(ModItems.WOLFRAM_INGOT.get(), CONTEST_INGOTS));
        give(player, new ItemStack(Items.STICK));
        player.displayClientMessage(Component.translatable("message.mitofus.contest_started", ContestRules.timeLimitTicks() / 20 / 60), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.0f, 1.0f);
        return true;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** A piece taken out of an anvil on the stage during the contest is the one judged; only a manual forge counts. */
    private static void onForged(ItemForgedEvent event) {
        ServerPlayer player = event.player();
        if (!state(player).running() || !onStage(player)) {
            return;
        }
        if (!event.manual()) {
            player.displayClientMessage(Component.translatable("message.mitofus.contest_by_hand"), false);
            return;
        }
        int score = ContestRules.score(event.rating(), event.precision());
        Component you = Component.translatable("notification.mitofus.contest_result.you", event.rating().displayName(),
                Math.round(Math.clamp(event.precision(), 0, 1) * 100), score);
        judge(player, score, you);
    }

    /** Mencia is within {@link #STAGE_REACH}. Both sides: she is a tracked entity on the client too. */
    private static boolean onStage(Player player) {
        return !player.level().getEntitiesOfClass(MenciaNpc.class, player.getBoundingBox().inflate(STAGE_REACH), Entity::isAlive).isEmpty();
    }

    /**
     * Competing on the stage: only a manual forge counts, so the anvil refuses the automatic one (and the screen greys
     * its button out). From the synced state: both sides.
     */
    public static boolean forcesManual(Player player) {
        return state(player).running() && onStage(player);
    }

    /** Both scores side by side in a system window, then the verdict. */
    private static void judge(ServerPlayer player, int score, Component you) {
        int rival = state(player).rivalScore();
        boolean won = ContestRules.wins(score, rival);
        NotificationEvents.send(player, Kind.CONTEST_RESULT, List.of(you,
                Component.translatable("notification.mitofus.contest_result.rival", rival),
                Component.translatable(won ? "notification.mitofus.contest_result.won" : "notification.mitofus.contest_result.lost")));
        if (won) {
            setState(player, ContestState.NONE);
            GoalTracker.milestone(player, CONTEST_WON);
            player.displayClientMessage(Component.translatable("message.mitofus.contest_won"), false);
            player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
        } else {
            lose(player);
        }
    }

    /** Lost: for good or until tomorrow, as the config and the kind of world say. */
    private static void lose(ServerPlayer player) {
        boolean forGood = ContestRules.failsForGood(Balance.current().contestLoss(), EibarStory.isWholeWorld(player.server));
        if (forGood) {
            setState(player, ContestState.NONE);
            QuestDeadlines.fail(player, VELASCOS_CONTEST);
            player.displayClientMessage(Component.translatable("message.mitofus.contest_lost_for_good"), false);
        } else {
            setState(player, state(player).lost(today(player)));
            player.displayClientMessage(Component.translatable("message.mitofus.contest_lost_rematch"), false);
        }
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % TICK_EVERY == 0) {
            tick(player);
        }
    }

    /** Once a second while competing: the clock on the action bar, Mencia's hammer, and the end when time is up. */
    public static void tick(ServerPlayer player) {
        ContestState state = state(player);
        if (!state.running()) {
            return;
        }
        if (!QuestService.get(player).isActive(VELASCOS_CONTEST)) {
            setState(player, ContestState.NONE); // abandoned
            return;
        }
        if (ContestRules.ticksLeft(state.startedAt(), player.level().getGameTime()) <= 0) {
            player.displayClientMessage(Component.translatable("message.mitofus.contest_time_up"), false);
            judge(player, 0, Component.translatable("notification.mitofus.contest_result.no_piece"));
            return;
        }
        player.displayClientMessage(Component.translatable("message.mitofus.contest_clock", clock(player, state)), true);
        if (player.tickCount % HAMMER_EVERY == 0) {
            player.serverLevel().getEntitiesOfClass(MenciaNpc.class, player.getBoundingBox().inflate(16), Entity::isAlive)
                    .forEach(mencia -> player.level().playSound(null, mencia.blockPosition(), SoundEvents.ANVIL_USE,
                            SoundSource.NEUTRAL, 0.6f, 1.1f));
        }
    }

    /** "4:05": the time left, in minutes and seconds. */
    private static String clock(Player player, ContestState state) {
        long seconds = ContestRules.ticksLeft(state.startedAt(), player.level().getGameTime()) / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /** Claimed at Velasco: the player becomes his disciple, and his smithy is rebuilt. */
    private static void onCompleted(QuestCompletedEvent event) {
        if (event.quest().equals(VELASCOS_CONTEST)) {
            ServerPlayer player = event.player();
            GoalTracker.milestone(player, VELASCOS_DISCIPLE);
            restoreSmithy(player.serverLevel(), player.blockPosition());
            player.displayClientMessage(Component.translatable("message.mitofus.velascos_contest_done"), false);
        }
    }

    /** Rebuilds Velasco's smithy in the Eibar around {@code near}, once for the whole world. */
    static void restoreSmithy(ServerLevel level, BlockPos near) {
        EibarData data = EibarData.get(level.getServer().overworld());
        if (data.smithyRestored()) {
            return;
        }
        StructureStart eibar = Eibar.at(level, near);
        if (!eibar.isValid()) {
            return;
        }
        Eibar.piece(eibar, Eibar.VELASCO_SMITHY).ifPresent(smithy -> {
            if (smithy.place(level, Eibar.VELASCO_SMITHY_RESTORED)) {
                data.setSmithyRestored();
                level.playSound(null, near, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
            }
        });
    }
}
