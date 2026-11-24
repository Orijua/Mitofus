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

    @Test
    void ownMinimumSizeIsRespected() {
        assertEquals(new PanelFit.Size(300, 150), PanelFit.fit(400, 226, 200, 100, 300, 150));
        assertEquals(new PanelFit.Size(312, 172), PanelFit.fit(400, 226, 320, 180, 300, 150));
    }

    @Test
    void rowsFillTheSpaceButNeverDropToZero() {
        assertEquals(7, PanelFit.rows(158, 22));
        assertEquals(6, PanelFit.rows(146, 24));
        assertEquals(1, PanelFit.rows(5, 22));
        assertEquals(1, PanelFit.rows(-10, 22));
    }

    @Test
    void shopAndTerminalSizesFitEveryCommonResolutionAndScale() {
        int[][] resolutions = {{854, 480}, {1280, 720}, {1920, 1080}};
        for (int[] resolution : resolutions) {
            for (int scale = 1; scale <= 4; scale++) {
                int screenWidth = resolution[0] / scale;
                int screenHeight = resolution[1] / scale;
                PanelFit.Size terminal = PanelFit.fit(400, 226, screenWidth, screenHeight, 300, 150);
                PanelFit.Size shop = PanelFit.fit(260, 214, screenWidth, screenHeight, 240, 120);
                // The windows only exceed the screen where the screen is smaller than their readable minimum.
                assertTrue(terminal.width() <= Math.max(300, screenWidth - 2 * PanelFit.MARGIN));
                assertTrue(terminal.height() <= Math.max(150, screenHeight - 2 * PanelFit.MARGIN));
                assertTrue(shop.width() <= Math.max(240, screenWidth - 2 * PanelFit.MARGIN));
                assertTrue(shop.height() <= Math.max(120, screenHeight - 2 * PanelFit.MARGIN));
                assertTrue(PanelFit.rows(terminal.height() - 48 - 20, 22) >= 1);
            }
        }
    }
}
