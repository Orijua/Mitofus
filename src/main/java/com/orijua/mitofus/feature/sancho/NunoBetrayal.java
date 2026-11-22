package com.orijua.mitofus.feature.sancho;

import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModEntities;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.quest.PlayerQuests;
import com.orijua.mitofus.feature.quest.QuestDeadlines;
import com.orijua.mitofus.feature.quest.QuestService;
import com.orijua.mitofus.feature.quest.goal.GoalTracker;
import com.orijua.mitofus.feature.world.MitofusWorld;
import com.orijua.mitofus.feature.world.WorldSites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Conde Nuno's side of the story. Once the player has finished Smith's chain, Nuno tells them of his
 * secret S-rank request and gives them a map to the Altamira Cave. Bringing him the book unappraised completes it
 * (and Nuno keeps the book). Appraising it instead reveals what it is, and Nuno comes for it with his knights: the
 * request becomes "Conde Nuno's Anger" (SS). Whoever dies at his hands with the book loses it to him.
 */
public final class NunoBetrayal {
    /** Nuno arrives with dozens of knights; here four, which is already too many. */
    public static final int KNIGHTS = 4;

    private NunoBetrayal() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(EventPriority.LOW, NunoBetrayal::onDeath);
        gameBus.addListener(NunoBetrayal::onDrops);
    }

    /**
     * Talking to Nuno in his manor: before Smith's chain is done he has no time for a nobody; after it, the first time,
     * he tells of his secret request (the hidden quest is discovered) and hands over a map to the cave.
     */
    public static void talk(ServerPlayer player) {
        if (!QuestService.get(player).isCompleted(SanchoStory.SMITH_CHAIN_END)) {
            player.displayClientMessage(Component.translatable("message.mitofus.nuno_nobody"), true);
            return;
        }
        if (GoalTracker.milestones(player, SanchoStory.NUNO_SECRET) == 0) {
            player.displayClientMessage(Component.translatable("message.mitofus.nuno_secret"), false);
            GoalTracker.milestone(player, SanchoStory.NUNO_SECRET);
        }
        // The map has its own milestone: if the world has no cave yet he tells the secret and the map comes in a later talk.
        // Players who got the map before this milestone existed receive it once more (a duplicate map, harmless).
        if (GoalTracker.milestones(player, SanchoStory.NUNO_MAP_GIVEN) == 0 && !QuestService.get(player).isCompleted(SanchoStory.SECRET_REQUEST)) {
            caveMap(player).ifPresent(map -> {
                GoalTracker.milestone(player, SanchoStory.NUNO_MAP_GIVEN);
                if (!player.getInventory().add(map)) {
                    player.drop(map, false);
                }
            });
        }
    }

    /** An explorer map centered on the world's Altamira Cave, with the cave marked; empty if the world has none. */
    static Optional<ItemStack> caveMap(ServerPlayer player) {
        // The map shows the Mitofus dimension, wherever the player is when they get it.
        ServerLevel level = MitofusWorld.of(player.server).orElse(null);
        if (level == null) {
            return Optional.empty();
        }
        return WorldSites.siteFor(level.getSeed(), WorldSites.ALTAMIRA).map(chunk -> {
            BlockPos cave = chunk.getMiddleBlockPosition(64);
            ItemStack map = MapItem.create(level, cave.getX(), cave.getZ(), (byte) 2, true, true);
            MapItem.renderBiomePreviewMap(level, map);
            MapItemSavedData.addTargetDecoration(map, cave, "+", MapDecorationTypes.RED_X);
            map.set(DataComponents.ITEM_NAME, Component.translatable("item.mitofus.nuno_map"));
            return map;
        });
    }

    /**
     * The book was appraised: Sancho's legacy is revealed, and Nuno arrives with {@link #KNIGHTS} knights around the
     * player. His secret request is dropped and "Conde Nuno's Anger" (kill him) starts.
     */
    public static void betray(ServerPlayer player) {
        GoalTracker.milestone(player, SanchoStory.APPRAISED);
        if (SanchoStory.killedNuno(player)) {
            return; // he is already dead for this player: nobody comes
        }
        PlayerQuests quests = QuestService.get(player);
        if (quests.isActive(SanchoStory.SECRET_REQUEST)) {
            player.setData(ModAttachments.PLAYER_QUESTS, quests.abandon(SanchoStory.SECRET_REQUEST));
        }
        boolean first = GoalTracker.milestones(player, SanchoStory.BETRAYAL) == 0;
        GoalTracker.milestone(player, SanchoStory.BETRAYAL);
        if (first || !QuestService.get(player).isActive(SanchoStory.ANGER)) {
            QuestService.accept(player, SanchoStory.ANGER);
        }
        ServerLevel level = player.serverLevel();
        List<BlockPos> spots = spotsAround(level, player.blockPosition(), KNIGHTS + 1);
        summon(level, ModEntities.CONDE_NUNO.get(), spots.getFirst(), player);
        for (int i = 1; i < spots.size(); i++) {
            summon(level, ModEntities.MESNADERO.get(), spots.get(i), player);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0f, 1.0f);
        player.displayClientMessage(Component.translatable("message.mitofus.nuno_arrives"), false);
    }

    private static void summon(ServerLevel level, EntityType<? extends Mesnada> type, BlockPos at, ServerPlayer target) {
        Mesnada member = type.create(level);
        if (member == null) {
            return;
        }
        member.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        member.setSummoned(true);
        member.setTarget(target);
        level.addFreshEntity(member);
    }

    /**
     * {@code count} places to stand 3 to 7 blocks around {@code center} (free for two blocks, on solid ground), nearest
     * first; where there aren't enough, the center itself fills the rest.
     */
    public static List<BlockPos> spotsAround(ServerLevel level, BlockPos center, int count) {
        List<BlockPos> spots = new ArrayList<>();
        for (int radius = 3; radius <= 7 && spots.size() < count; radius++) {
            for (int step = 0; step < 16 && spots.size() < count; step++) {
                double angle = Math.PI * 2 * step / 16 + radius;
                BlockPos column = center.offset((int) Math.round(Math.cos(angle) * radius), 0, (int) Math.round(Math.sin(angle) * radius));
                for (int dy = 2; dy >= -2; dy--) {
                    BlockPos feet = column.above(dy);
                    if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                            && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                            && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                            && !spots.contains(feet)) {
                        spots.add(feet);
                        break;
                    }
                }
            }
        }
        while (spots.size() < count) {
            spots.add(center);
        }
        return spots;
    }

    /** dying to Nuno with the book means he takes it. The chance to read it is lost; the quest fails too. */
    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !SanchoStory.isNunosEnemy(player)) {
            return;
        }
        Inventory inventory = player.getInventory();
        boolean taken = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(ModItems.ORBE_AURIFERO.get())) {
                inventory.setItem(slot, ItemStack.EMPTY);
                taken = true;
            }
        }
        if (taken) {
            player.displayClientMessage(Component.translatable("message.mitofus.nuno_took_book"), false);
        }
        // Dying while he hunts you also fails his quest (and costs its levels), like running out of time.
        if (QuestService.get(player).isActive(SanchoStory.ANGER)) {
            QuestDeadlines.fail(player, SanchoStory.ANGER);
        }
    }

    /** In case the book was already among the drops (another mod dropping the inventory first). */
    private static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SanchoStory.isNunosEnemy(player)) {
            event.getDrops().removeIf(drop -> drop.getItem().is(ModItems.ORBE_AURIFERO.get()));
        }
    }

}
