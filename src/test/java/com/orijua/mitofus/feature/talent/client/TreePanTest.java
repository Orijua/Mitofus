package com.orijua.mitofus.feature.talent.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreePanTest {
    @Test
    void aShortPressIsAClickAndALongOneIsADrag() {
        TreePan pan = new TreePan();
        pan.press(10, 10);
        pan.drag(11, 10, 1, 0);
        assertEquals(0, pan.x(), "a tiny wobble does not move the tree");
        assertTrue(pan.release(), "and it is still a click");

        pan.press(10, 10);
        pan.drag(30, 25, 20, 15);
        assertEquals(20, pan.x());
        assertEquals(15, pan.y());
        assertFalse(pan.release(), "a drag is not a click");
    }

    @Test
    void movingWithoutPressingDoesNothing() {
        TreePan pan = new TreePan();
        pan.drag(30, 30, 20, 20);
        assertEquals(0, pan.x());
        assertFalse(pan.release());
    }

    @Test
    void aTreeBiggerThanTheWindowCanReachItsEdgesAndNoFurther() {
        // Tree drawn from y=0 to y=300 in a window from y=0 to y=100.
        assertEquals(-(300 - 100 + TreePan.MARGIN), TreePan.limit(-1000, 0, 300, 0, 100), "bottom edge reaches the bottom of the window");
        assertEquals(TreePan.MARGIN, TreePan.limit(1000, 0, 300, 0, 100), "top edge stops at the top margin");
        assertEquals(-50, TreePan.limit(-50, 0, 300, 0, 100), "in between it is left alone");
    }

    @Test
    void aTreeOutsideTheWindowThatFitsIsMovedInside() {
        // Tree from x=-40 to x=60 drawn left of a window starting at x=0 to 200: it fits, so it is pulled in.
        TreePan pan = new TreePan();
        pan.clamp(-40, 60, 0, 50, 0, 200, 0, 100);
        assertEquals(40 + TreePan.MARGIN, pan.x());
    }

    @Test
    void resetCentresTheTreeAgain() {
        TreePan pan = new TreePan();
        pan.press(0, 0);
        pan.drag(50, 50, 50, 50);
        pan.reset();
        assertEquals(0, pan.x());
        assertEquals(0, pan.y());
        assertFalse(pan.release());
    }
}
