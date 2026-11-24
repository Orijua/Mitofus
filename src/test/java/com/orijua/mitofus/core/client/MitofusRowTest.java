package com.orijua.mitofus.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MitofusRowTest {
    @Test
    void rowFitsTwoLines() {
        assertTrue(MitofusRow.height() >= 2 * 9);
    }

    @Test
    void containsIsHalfOpen() {
        int h = MitofusRow.height();
        assertTrue(MitofusRow.contains(10, 20, 100, 10, 20));
        assertTrue(MitofusRow.contains(10, 20, 100, 109, 20 + h - 1));
        assertFalse(MitofusRow.contains(10, 20, 100, 110, 20));
        assertFalse(MitofusRow.contains(10, 20, 100, 10, 20 + h));
        assertFalse(MitofusRow.contains(10, 20, 100, 9, 20));
    }

    @Test
    void emptyStateCentersItsLines() {
        assertEquals(20 + (100 - 3 * UiStyle.LINE) / 2, MitofusEmptyState.firstLineY(20, 100, 3));
        assertEquals(20, MitofusEmptyState.firstLineY(20, 10, 5));
    }
}
