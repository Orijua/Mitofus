package com.orijua.mitofus.feature.quest;

import com.orijua.mitofus.core.event.MitofusPlayerEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** A player claimed a finished quest. Posted after its rewards are given. */
public final class QuestCompletedEvent extends MitofusPlayerEvent {
    private final ResourceLocation quest;

    public QuestCompletedEvent(ServerPlayer player, ResourceLocation quest) {
        super(player);
        this.quest = quest;
    }

    public ResourceLocation quest() {
        return quest;
    }
}
