package com.orijua.mitofus.feature.npc;

import com.orijua.mitofus.feature.mob.BossMob;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.Entity;

/**
 * Name colors that tell characters apart: gold for named NPCs and bosses, green for common NPCs,
 * white for players (already the default, so players are left alone). Iron (quasi-named) has no one to use it yet.
 */
public final class NameColors {
    private NameColors() {
    }

    /** The color of this entity's name, or null to leave it as it is. */
    public static ChatFormatting of(Entity entity) {
        return of(entity.getClass());
    }

    /** The same, by class (so it can be tested without creating entities). */
    static ChatFormatting of(Class<? extends Entity> type) {
        if (ApprenticeSmith.class.isAssignableFrom(type)) {
            return ChatFormatting.GREEN;
        }
        if (PeacefulNpc.class.isAssignableFrom(type) || BossMob.class.isAssignableFrom(type)) {
            return ChatFormatting.GOLD;
        }
        return null;
    }
}
