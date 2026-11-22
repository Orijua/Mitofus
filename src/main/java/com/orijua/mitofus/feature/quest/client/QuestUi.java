package com.orijua.mitofus.feature.quest.client;

import com.orijua.mitofus.core.client.UiStyle;

/** Colors of a quest shared by the NPC quest window and the Quests tab, kept apart from the screens so the rules can be tested. */
final class QuestUi {
    private QuestUi() {
    }

    /** The stripe of a quest card: grey when not taken, gold while active, green once it can be claimed. */
    static int accent(boolean active, boolean done) {
        if (!active) {
            return UiStyle.MUTED;
        }
        return done ? UiStyle.GOOD : UiStyle.GOLD;
    }

    /** The progress bar of an active quest: amber while it advances, XP green once it is done. */
    static int barColor(boolean done) {
        return done ? UiStyle.XP : UiStyle.COOLDOWN;
    }
}
