package com.orijua.mitofus.feature.quest.client;

import com.orijua.mitofus.core.client.UiStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestUiTest {
    @Test
    void notActiveIsMuted() {
        assertEquals(UiStyle.MUTED, QuestUi.accent(false, false));
    }

    @Test
    void activeIsGold() {
        assertEquals(UiStyle.GOLD, QuestUi.accent(true, false));
    }

    @Test
    void doneIsGood() {
        assertEquals(UiStyle.GOOD, QuestUi.accent(true, true));
    }

    @Test
    void barIsAmberWhileAdvancingAndXpGreenWhenDone() {
        assertEquals(UiStyle.COOLDOWN, QuestUi.barColor(false));
        assertEquals(UiStyle.XP, QuestUi.barColor(true));
    }
}
