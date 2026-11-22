package com.orijua.mitofus.feature.npc;

import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.smithing.ItemForgedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;

/**
 * Illumination: when a smith forges a Legendary
 * piece, his Master apprentices within {@link ApprenticeRules#ILLUMINATION_REACH} blocks become Craftsmen.
 */
public final class Illumination {
    private Illumination() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(ItemForgedEvent.class, event -> enlighten(event.player(), event.rating()));
    }

    /** Lets every apprentice of {@code master} nearby judge the piece he just forged. Returns how many were enlightened. */
    public static int enlighten(ServerPlayer master, ItemRating rating) {
        int enlightened = 0;
        for (ApprenticeSmith apprentice : master.serverLevel().getEntitiesOfClass(ApprenticeSmith.class,
                master.getBoundingBox().inflate(ApprenticeRules.ILLUMINATION_REACH), apprentice -> apprentice.isOwnedBy(master))) {
            if (apprentice.judgePiece(rating, false)) {
                enlightened++;
            }
        }
        return enlightened;
    }
}
