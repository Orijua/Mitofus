package com.orijua.mitofus.feature.progression.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusLayoutTest {
    @Test
    void roomyTabKeepsTheFullRowHeight() {
        assertEquals(18, StatusLayout.statRow(172, 5));
    }

    @Test
    void smallTabTightensTheRows() {
        assertEquals(13, StatusLayout.statRow(134, 5));
        assertEquals(12, StatusLayout.statRow(100, 5));
    }

    @Test
    void thinBarsSitBetweenTheTextAndTheNextLine() {
        assertTrue(StatusLayout.RESOURCE_BAR_Y + StatusLayout.RESOURCE_BAR_HEIGHT < StatusLayout.POINTS_Y);
        assertTrue(StatusLayout.POINTS_Y + 10 <= StatusLayout.STATS_TOP);
    }
}
