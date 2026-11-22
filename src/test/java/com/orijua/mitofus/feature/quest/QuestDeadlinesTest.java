package com.orijua.mitofus.feature.quest;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestDeadlinesTest {
    @Test
    void theClockWaitsForAsLongAsThePlayerIsOutsideMitofus() {
        UUID player = UUID.randomUUID();
        assertEquals(0, QuestDeadlines.outsideTicks(player, true, 1000), "inside Mitofus the clock runs");
        assertEquals(QuestDeadlines.CHECK_TICKS, QuestDeadlines.outsideTicks(player, false, 1020), "the first check outside counts one interval");
        assertEquals(20, QuestDeadlines.outsideTicks(player, false, 1040));
        assertEquals(6000, QuestDeadlines.outsideTicks(player, false, 7040), "sleeping through the night outside counts too");
        assertEquals(0, QuestDeadlines.outsideTicks(player, true, 7060), "back inside: it runs again");
        assertEquals(QuestDeadlines.CHECK_TICKS, QuestDeadlines.outsideTicks(player, false, 9000), "leaving again starts a new count");
    }
}
