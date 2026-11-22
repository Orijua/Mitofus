package com.orijua.mitofus.feature.quest;

import com.orijua.mitofus.core.event.MitofusPlayerEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Optional;
import javax.annotation.Nullable;

/** A player accepted a quest, at the NPC that gives it ({@code giver}) or from the Quests tab. Posted after any loans. */
public final class QuestAcceptedEvent extends MitofusPlayerEvent {
    private final ResourceLocation quest;
    @Nullable
    private final Entity giver;

    public QuestAcceptedEvent(ServerPlayer player, ResourceLocation quest, @Nullable Entity giver) {
        super(player);
        this.quest = quest;
        this.giver = giver;
    }

    public ResourceLocation quest() {
        return quest;
    }

    public Optional<Entity> giver() {
        return Optional.ofNullable(giver);
    }
}
