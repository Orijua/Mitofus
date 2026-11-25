package com.orijua.mitofus.feature.quest.goal;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Counts what a job does besides forging, as story milestones, so a quest can ask for it ({@link MilestoneGoal}): fish caught,
 * ripe crops harvested, logs chopped, items cooked, bread baked, and time spent by the water or in a forest (one count
 * per {@link #TIME_STEP} ticks). The class grade quests use them for fishers, farmers and woodcutters.
 */
public final class JobActivity {
    public static final ResourceLocation FISH_CAUGHT = ResourceLocation.fromNamespaceAndPath("mitofus", "fish_caught");
    public static final ResourceLocation CROPS_HARVESTED = ResourceLocation.fromNamespaceAndPath("mitofus", "crops_harvested");
    public static final ResourceLocation LOGS_CHOPPED = ResourceLocation.fromNamespaceAndPath("mitofus", "logs_chopped");

    public static final ResourceLocation ITEMS_COOKED = ResourceLocation.fromNamespaceAndPath("mitofus", "items_cooked");
    public static final ResourceLocation BREAD_BAKED = ResourceLocation.fromNamespaceAndPath("mitofus", "bread_baked");
    public static final ResourceLocation WATERSIDE_TIME = ResourceLocation.fromNamespaceAndPath("mitofus", "waterside_time");
    public static final ResourceLocation FOREST_TIME = ResourceLocation.fromNamespaceAndPath("mitofus", "forest_time");

    /** Ticks between two counts of "time in a place" (10 seconds). */
    public static final int TIME_STEP = 200;

    private JobActivity() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(JobActivity::onFished);
        gameBus.addListener(JobActivity::onBroken);
        gameBus.addListener(JobActivity::onSmelted);
        gameBus.addListener(JobActivity::onCrafted);
        gameBus.addListener(JobActivity::onTick);
    }

    private static void onSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (int i = 0; i < Math.max(1, event.getSmelting().getCount()); i++) {
                GoalTracker.milestone(player, ITEMS_COOKED);
            }
        }
    }

    private static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getCrafting().is(Items.BREAD)) {
            for (int i = 0; i < event.getCrafting().getCount(); i++) {
                GoalTracker.milestone(player, BREAD_BAKED);
            }
        }
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % TIME_STEP != 0) {
            return;
        }
        var biome = player.level().getBiome(player.blockPosition());
        if (biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_OCEAN)) {
            GoalTracker.milestone(player, WATERSIDE_TIME);
        } else if (biome.is(BiomeTags.IS_FOREST)) {
            GoalTracker.milestone(player, FOREST_TIME);
        }
    }

    private static void onFished(ItemFishedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GoalTracker.milestone(player, FISH_CAUGHT);
        }
    }

    private static void onBroken(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getState().getBlock() instanceof CropBlock crop && crop.isMaxAge(event.getState())) {
            GoalTracker.milestone(player, CROPS_HARVESTED);
        } else if (event.getState().is(BlockTags.LOGS)) {
            GoalTracker.milestone(player, LOGS_CHOPPED);
        }
    }
}
