package com.orijua.mitofus.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanelFitTest {
    @Test
    void windowThatFitsIsUnchanged() {
        assertEquals(new PanelFit.Size(320, 210), PanelFit.fit(320, 210, 640, 360));
    }

    @Test
    void windowShrinksToTheScreenLeavingAMargin() {
        // GUI scale 4 on 1080p: 480x270 is fine, but scale 4 on 720p is 320x180.
        assertEquals(new PanelFit.Size(312, 172), PanelFit.fit(320, 210, 320, 180));
    }

    @Test
    void eachAxisShrinksOnItsOwn() {
        assertEquals(new PanelFit.Size(320, 172), PanelFit.fit(320, 210, 640, 180));
    }

    @Test
    void tinyScreenNeverGoesBelowTheMinimum() {
        assertEquals(new PanelFit.Size(PanelFit.MIN_WIDTH, PanelFit.MIN_HEIGHT), PanelFit.fit(320, 210, 50, 40));
    }

    @Test
    void scrollOnlyWhenTheContentIsTaller() {
        assertFalse(PanelFit.needsScroll(100, 100));
        assertTrue(PanelFit.needsScroll(101, 100));
        assertFalse(PanelFit.needsScroll(0, 0));
    }
}
