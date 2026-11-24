package com.orijua.mitofus.feature.quest.goal;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Counts what a job does besides forging, as story milestones, so a quest can ask for it ({@link MilestoneGoal}): fish caught,
 * ripe crops harvested and logs chopped. The class grade quests use them for fishers, farmers and woodcutters.
 */
public final class JobActivity {
    public static final ResourceLocation FISH_CAUGHT = ResourceLocation.fromNamespaceAndPath("mitofus", "fish_caught");
    public static final ResourceLocation CROPS_HARVESTED = ResourceLocation.fromNamespaceAndPath("mitofus", "crops_harvested");
    public static final ResourceLocation LOGS_CHOPPED = ResourceLocation.fromNamespaceAndPath("mitofus", "logs_chopped");

    private JobActivity() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(JobActivity::onFished);
        gameBus.addListener(JobActivity::onBroken);
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
