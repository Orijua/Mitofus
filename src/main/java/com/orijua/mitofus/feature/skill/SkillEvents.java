package com.orijua.mitofus.feature.skill;

import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class SkillEvents {
    private SkillEvents() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(SkillEvents::regenerateMana);
    }

    /** Once per second; only writes (and syncs) when the value changes. */
    private static void regenerateMana(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0 || !MitofusWorld.is(player.level())) {
            return;
        }
        float current = player.getData(ModAttachments.MANA);
        float regenerated = Math.min(ProgressionService.maxMana(player),
                current + ProgressionService.manaRegenPerSecond(player));
        if (regenerated != current) {
            player.setData(ModAttachments.MANA, regenerated);
        }
    }
}
