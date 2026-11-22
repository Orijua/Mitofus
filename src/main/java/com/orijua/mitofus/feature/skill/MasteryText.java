package com.orijua.mitofus.feature.skill;

import net.minecraft.network.chat.Component;

/** Names of the mastery ranks, shared by the level-up message and the Skills tab. */
public final class MasteryText {
    private MasteryText() {
    }

    public static Component rank(int level) {
        return Component.translatable("mastery.mitofus.rank." + MasteryRules.rank(level));
    }

    /** "Intermediate 3". */
    public static Component full(int level) {
        return Component.translatable("mastery.mitofus.level", rank(level), MasteryRules.levelInRank(level));
    }
}
