package com.orijua.mitofus.feature.progression;

import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.quest.goal.PlayerCounters;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

public final class TitleService {
    private TitleService() {
    }

    /** Grants every title whose goal is now met. Called by GoalTracker after any countable event. */
    public static void checkUnlocks(ServerPlayer player) {
        if (!MitofusWorld.is(player.level())) {
            return;
        }
        PlayerIdentity identity = player.getData(ModAttachments.PLAYER_IDENTITY);
        PlayerCounters counters = player.getData(ModAttachments.PLAYER_COUNTERS);
        PlayerStats stats = ProgressionService.get(player);
        boolean changed = false;
        List<ResourceLocation> earned = new ArrayList<>();

        for (Definitions.Entry<TitleDefinition> title : Definitions.all(player.level(), ModDatapackRegistries.TITLE)) {
            if (identity.hasTitle(title.id()) || title.value().goal().progress(counters, stats) < title.value().goal().target()) {
                continue;
            }
            identity = identity.withTitle(title.id());
            changed = true;
            player.displayClientMessage(Component.translatable("message.mitofus.title_earned", title.value().name()), false);
            player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7f, 1.0f);
            earned.add(title.id());
        }
        if (changed) {
            player.setData(ModAttachments.PLAYER_IDENTITY, identity);
            ProgressionService.refreshEffects(player);
            earned.forEach(id -> NeoForge.EVENT_BUS.post(new TitleEarnedEvent(player, id)));
        }
    }
}
