package com.orijua.mitofus.feature.eibar;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModEntities;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.sancho.NunoBetrayal;
import com.orijua.mitofus.feature.sancho.SanchoStory;
import com.orijua.mitofus.feature.quest.QuestAcceptedEvent;
import com.orijua.mitofus.feature.quest.QuestCompletedEvent;
import com.orijua.mitofus.feature.quest.QuestFailedEvent;
import com.orijua.mitofus.feature.quest.QuestService;
import com.orijua.mitofus.feature.quest.goal.GoalTracker;
import com.orijua.mitofus.feature.quest.goal.MilestoneGoal;
import com.orijua.mitofus.feature.world.MitofusWorld;
import com.orijua.mitofus.feature.world.WorldSites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Velasco's story in Eibar. Only Sancho's successor can help him: Cid gets Velasco's quest
 * because he inherited Sancho's humanitarian ideals. Smith points the successor to Eibar with a map; talking to Velasco
 * then counts the {@link #MET_VELASCO} milestone, which reveals his quest. Before that, Velasco is just a drunk who sends
 * everyone away.
 * <p>
 * "The Blacksmith's Rage" [B] ({@link #VELASCOS_RAGE},: accepting it creates the skill of the same name and
 * brings {@link #THUGS} Levante thugs into the smithy; the last one drops the transfer contract, and tearing it up
 * completes the quest. It has 7 days. If the time runs out Velasco dies: in a single-player world for
 * good (a grave is left in his smithy), in a shared world only for the player who failed him (he no longer speaks to
 * them), so nobody else loses him. The production contest that follows is {@link VelascoContest}.
 */
public final class EibarStory {
    /** Smith told Sancho's successor about Velasco and gave them the map to Eibar. */
    public static final ResourceLocation EIBAR_MAP = Mitofus.id("eibar_map");
    /** Sancho's successor talked to Velasco in his smithy. */
    public static final ResourceLocation MET_VELASCO = Mitofus.id("met_velasco");
    /** The smithy transfer contract was torn up. */
    public static final ResourceLocation CONTRACT_DESTROYED = Mitofus.id("contract_destroyed");
    public static final ResourceLocation VELASCOS_RAGE = Mitofus.id("velascos_rage");
    /** the Compañía de Levante's minions in the smithy. */
    public static final int THUGS = 5;
    /** How many lines Velasco has for those he sends away (message.mitofus.velasco_drunk.N). */
    static final int DRUNK_LINES = 3;

    private EibarStory() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(EibarStory::onAccepted);
        gameBus.addListener(EibarStory::onFailed);
        gameBus.addListener(EibarStory::onCompleted);
    }

    /**
     * Whether the player inherited Sancho's legacy, the condition of Velasco's quests in the original design: they read the book or
     * completed "Sancho's Legacy" (which reading completes; a debug command can complete it alone). Not the class, so a
     * later class change doesn't undo it. Read from the synced counters and quests: both sides.
     */
    public static boolean isSanchosSuccessor(Player player) {
        return player.getData(ModAttachments.PLAYER_COUNTERS).get(MilestoneGoal.counterKey(SanchoStory.READ)) > 0
                || QuestService.get(player).isCompleted(SanchoStory.LEGACY);
    }

    /**
     * The player let Velasco's time run out (he is dead for them), or lost his contest for good (the Levante keeps his smithy
     * and he no longer teaches them). From the synced quests: both sides.
     */
    public static boolean hasLostVelasco(Player player) {
        return QuestService.get(player).isFailed(VELASCOS_RAGE) || QuestService.get(player).isFailed(VelascoContest.VELASCOS_CONTEST);
    }

    /** Whether Velasco opens his quest window to this player. */
    public static boolean heardByVelasco(Player player) {
        return isSanchosSuccessor(player) && !hasLostVelasco(player);
    }

    /** Talking to Smith: the first time as Sancho's successor, he tells of his old friend in Eibar and gives the map. */
    public static void talkToSmith(ServerPlayer player) {
        if (!isSanchosSuccessor(player) || GoalTracker.milestones(player, EIBAR_MAP) > 0) {
            return;
        }
        Optional<ItemStack> map = eibarMap(player);
        if (map.isEmpty()) {
            return; // a world without Eibar
        }
        player.displayClientMessage(Component.translatable("message.mitofus.smith_tells_of_velasco"), false);
        GoalTracker.milestone(player, EIBAR_MAP);
        if (!player.getInventory().add(map.get())) {
            player.drop(map.get(), false);
        }
    }

    /**
     * Talking to Velasco: the successor is heard (the milestone, every time), and thanked once his smithy is saved; anyone
     * else is sent away, and whoever failed him gets silence. If the thugs are gone while the quest still runs (and
     * the contract is nowhere), they come back; so does Mencia while the contest quest runs. Once the contest is
     * claimed, his smithy is rebuilt if it wasn't yet (e.g. after a debug command completed the quest elsewhere).
     */
    public static void talkToVelasco(ServerPlayer player, Entity velasco) {
        if (hasLostVelasco(player)) {
            String line = QuestService.get(player).isFailed(VELASCOS_RAGE) ? "velasco_lost" : "velasco_contest_lost";
            player.displayClientMessage(Component.translatable("message.mitofus." + line), true);
        } else if (isSanchosSuccessor(player)) {
            GoalTracker.milestone(player, MET_VELASCO);
            if (QuestService.get(player).isCompleted(VelascoContest.VELASCOS_CONTEST)) {
                VelascoContest.restoreSmithy(player.serverLevel(), velasco.blockPosition());
                player.displayClientMessage(Component.translatable("message.mitofus.velasco_master"), true);
            } else if (QuestService.get(player).isActive(VelascoContest.VELASCOS_CONTEST)) {
                VelascoContest.summonMencia(player.serverLevel(), velasco);
            } else if (QuestService.get(player).isCompleted(VELASCOS_RAGE)) {
                player.displayClientMessage(Component.translatable("message.mitofus.velasco_sober"), true);
            } else if (thugsLost(player, velasco)) {
                summonThugs(player, velasco);
            }
        } else {
            int line = player.getRandom().nextInt(DRUNK_LINES) + 1;
            player.displayClientMessage(Component.translatable("message.mitofus.velasco_drunk." + line), true);
        }
    }

    /** The quest runs, the contract isn't torn, and neither the gang nor the contract is anywhere to be found. */
    private static boolean thugsLost(ServerPlayer player, Entity velasco) {
        if (!QuestService.get(player).isActive(VELASCOS_RAGE) || GoalTracker.milestones(player, CONTRACT_DESTROYED) > 0
                || player.getInventory().hasAnyMatching(stack -> stack.is(ModItems.TRANSFER_CONTRACT.get()))) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        AABB around = velasco.getBoundingBox().inflate(LevanteThug.GANG_RANGE);
        boolean contractOnTheGround = !level.getEntitiesOfClass(ItemEntity.class, around,
                item -> item.getItem().is(ModItems.TRANSFER_CONTRACT.get())).isEmpty();
        return !contractOnTheGround && gang(level, player.getUUID(), velasco.position(), LevanteThug.GANG_RANGE).isEmpty();
    }

    /** The living thugs sent for {@code owner} within {@code range} of {@code center}. */
    static List<LevanteThug> gang(ServerLevel level, UUID owner, Vec3 center, double range) {
        return level.getEntitiesOfClass(LevanteThug.class, AABB.ofSize(center, range * 2, range * 2, range * 2),
                thug -> thug.isAlive() && thug.owner().filter(owner::equals).isPresent());
    }

    private static void onAccepted(QuestAcceptedEvent event) {
        if (event.quest().equals(VELASCOS_RAGE)) {
            event.giver().ifPresent(velasco -> summonThugs(event.player(), velasco));
        }
    }

    /** The Levante's thugs burst into the smithy around Velasco, after this player. */
    static void summonThugs(ServerPlayer player, Entity velasco) {
        ServerLevel level = player.serverLevel();
        List<BlockPos> spots = NunoBetrayal.spotsAround(level, velasco.blockPosition(), THUGS);
        for (BlockPos spot : spots) {
            LevanteThug thug = ModEntities.LEVANTE_THUG.get().create(level);
            if (thug == null) {
                continue;
            }
            thug.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360, 0);
            thug.sendFor(player, velasco.blockPosition());
            level.addFreshEntity(thug);
        }
        level.playSound(null, velasco.blockPosition(), SoundEvents.VINDICATOR_CELEBRATE, SoundSource.HOSTILE, 1.5f, 0.8f);
        player.displayClientMessage(Component.translatable("message.mitofus.levante_thugs_arrive"), false);
    }

    private static void onCompleted(QuestCompletedEvent event) {
        if (event.quest().equals(VELASCOS_RAGE)) {
            event.player().displayClientMessage(Component.translatable("message.mitofus.velascos_rage_done"), false);
        }
    }

    /** Velasco's time ran out: he dies for the whole world when it is a single-player one, else only for this player. */
    private static void onFailed(QuestFailedEvent event) {
        if (!event.quest().equals(VELASCOS_RAGE)) {
            return;
        }
        MinecraftServer server = event.player().server;
        if (isWholeWorld(server)) {
            killVelasco(server.overworld());
            event.player().displayClientMessage(Component.translatable("message.mitofus.velasco_died"), false);
        } else {
            event.player().displayClientMessage(Component.translatable("message.mitofus.velasco_died_for_you"), false);
        }
    }

    /** One player alone in a world nobody else can join: what happens to Velasco happens to the world. */
    static boolean isWholeWorld(MinecraftServer server) {
        return server.isSingleplayer() && !server.isPublished();
    }

    /** Velasco dies for the whole world: those loaded now are buried at once, the rest when their chunk loads. */
    public static void killVelasco(ServerLevel overworld) {
        EibarData.get(overworld).setVelascoDead();
        for (ServerLevel level : overworld.getServer().getAllLevels()) {
            for (VelascoNpc velasco : level.getEntities(ModEntities.VELASCO.get(), Entity::isAlive)) {
                bury(velasco);
            }
        }
    }

    public static boolean isVelascoDead(ServerLevel level) {
        return EibarData.get(level.getServer().overworld()).velascoDead();
    }

    /** Velasco is gone; where he stood, a grave sign. */
    public static void bury(VelascoNpc velasco) {
        ServerLevel level = (ServerLevel) velasco.level();
        BlockPos at = velasco.blockPosition();
        if (level.getBlockState(at).canBeReplaced()) {
            int rotation = Math.floorMod(Math.round(velasco.getYRot() * 16 / 360f), 16);
            level.setBlockAndUpdate(at, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation));
            if (level.getBlockEntity(at) instanceof SignBlockEntity sign) {
                SignText text = new SignText()
                        .setMessage(1, Component.translatable("sign.mitofus.velasco_grave.1"))
                        .setMessage(2, Component.translatable("sign.mitofus.velasco_grave.2"));
                sign.setText(text, true);
                sign.setWaxed(true);
                sign.setChanged();
                level.sendBlockUpdated(at, sign.getBlockState(), sign.getBlockState(), 3);
            }
        }
        level.playSound(null, at, SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 1.0f, 0.5f);
        velasco.discard();
    }

    /** An explorer map centered on the world's Eibar, with the city marked; empty if the world has none. */
    static Optional<ItemStack> eibarMap(ServerPlayer player) {
        // The map shows the Mitofus dimension, wherever the player is when they get it.
        ServerLevel level = MitofusWorld.of(player.server).orElse(null);
        if (level == null) {
            return Optional.empty();
        }
        return WorldSites.siteFor(level.getSeed(), WorldSites.EIBAR).map(chunk -> {
            BlockPos eibar = chunk.getMiddleBlockPosition(64);
            ItemStack map = MapItem.create(level, eibar.getX(), eibar.getZ(), (byte) 2, true, true);
            MapItem.renderBiomePreviewMap(level, map);
            MapItemSavedData.addTargetDecoration(map, eibar, "+", MapDecorationTypes.RED_X);
            map.set(DataComponents.ITEM_NAME, Component.translatable("item.mitofus.eibar_map"));
            return map;
        });
    }
}
