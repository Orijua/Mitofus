package com.orijua.mitofus.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListNavTest {
    @Test
    void moveStaysInsideTheList() {
        assertEquals(1, ListNav.move(0, 1, 3));
        assertEquals(0, ListNav.move(0, -1, 3));
        assertEquals(2, ListNav.move(2, 1, 3));
    }

    @Test
    void moveOnAnEmptyListFindsNothing() {
        assertEquals(-1, ListNav.move(-1, 1, 0));
        assertEquals(-1, ListNav.move(0, -1, 0));
    }

    @Test
    void moveFromNoSelectionPicksTheEdge() {
        assertEquals(0, ListNav.move(-1, 1, 4));
        assertEquals(3, ListNav.move(-1, -1, 4));
    }

    @Test
    void singleElementListStaysPut() {
        assertEquals(0, ListNav.move(0, 1, 1));
        assertEquals(0, ListNav.move(0, -1, 1));
    }

    @Test
    void pageScrollMovesByTheVisibleRowsAndClamps() {
        ScrollList list = new ScrollList(3);
        assertTrue(list.page(1, 10));
        assertEquals(3, list.first());
        assertTrue(list.page(1, 10));
        assertTrue(list.page(1, 10));
        assertEquals(7, list.first());
        assertFalse(list.page(1, 10));
        assertTrue(list.page(-1, 10));
        assertEquals(4, list.first());
    }

    @Test
    void revealBringsARowIntoView() {
        ScrollList list = new ScrollList(3);
        list.reveal(5, 10);
        assertEquals(3, list.first());
        list.reveal(1, 10);
        assertEquals(1, list.first());
        list.reveal(2, 10);
        assertEquals(1, list.first());
    }
}
