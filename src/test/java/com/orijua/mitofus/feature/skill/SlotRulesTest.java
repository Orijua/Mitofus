package com.orijua.mitofus.feature.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotRulesTest {
    @Test
    void theFirstPageStartsWithThreeAndGainsOneEveryTenLevels() {
        assertEquals(3, SlotRules.firstPageSlots(1));
        assertEquals(3, SlotRules.firstPageSlots(9));
        assertEquals(4, SlotRules.firstPageSlots(10));
        assertEquals(9, SlotRules.firstPageSlots(60));
        assertEquals(9, SlotRules.firstPageSlots(500), "never more than nine");
    }

    @Test
    void aNewPageOpensEveryHundredLevelsUpToFive() {
        assertEquals(1, SlotRules.pages(99));
        assertEquals(2, SlotRules.pages(100));
        assertEquals(5, SlotRules.pages(400));
        assertEquals(5, SlotRules.pages(5000));
    }

    @Test
    void openSlotsFollowTheLevel() {
        assertTrue(SlotRules.isOpen(1, 2));
        assertFalse(SlotRules.isOpen(1, 3));
        assertTrue(SlotRules.isOpen(10, 3));
        assertFalse(SlotRules.isOpen(99, 9), "page two is not there yet");
        assertTrue(SlotRules.isOpen(100, 9));
        assertTrue(SlotRules.isOpen(100, 17), "a new page has its nine slots open");
        assertFalse(SlotRules.isOpen(100, 18));
        assertFalse(SlotRules.isOpen(1000, SlotRules.MAX_SLOTS), "past the last slot");
        assertFalse(SlotRules.isOpen(1000, -1));
    }

    @Test
    void openOnPageAndNextOpen() {
        assertEquals(3, SlotRules.openOnPage(1, 0));
        assertEquals(0, SlotRules.openOnPage(1, 1));
        assertEquals(9, SlotRules.openOnPage(100, 1));
        assertEquals(0, SlotRules.nextOpen(1, -1));
        assertEquals(2, SlotRules.nextOpen(1, 1));
        assertEquals(-1, SlotRules.nextOpen(1, 2), "after the last open slot: no slot");
        assertEquals(9, SlotRules.nextOpen(100, 8), "jumps to the next page");
    }
}
