package com.orijua.mitofus.feature.rumor;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModBlocks;
import com.orijua.mitofus.feature.quest.goal.GoalTracker;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Map;
import java.util.function.Supplier;

/** The first time a player mines a rare ore it counts as a milestone, so the rumors about it are put to rest. */
public final class MaterialFinds {
    public static final ResourceLocation MITHRIL = Mitofus.id("found_mithril");
    public static final ResourceLocation AZURITE = Mitofus.id("found_azurite");

    private static final Map<Supplier<? extends Block>, ResourceLocation> ORES = Map.of(
            ModBlocks.MITHRIL_ORE, MITHRIL,
            ModBlocks.DEEPSLATE_MITHRIL_ORE, MITHRIL,
            ModBlocks.AZURITE_ORE, AZURITE,
            ModBlocks.DEEPSLATE_AZURITE_ORE, AZURITE);

    private MaterialFinds() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(MaterialFinds::onBreak);
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !MitofusWorld.is(player.level())) {
            return;
        }
        ORES.forEach((ore, milestone) -> {
            if (event.getState().is(ore.get()) && GoalTracker.milestones(player, milestone) == 0) {
                GoalTracker.milestone(player, milestone);
            }
        });
    }
}
