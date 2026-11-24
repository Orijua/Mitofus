package com.orijua.mitofus.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The window colors keep a readable contrast (WCAG): 4.5:1 for text, 3:1 for bars and lines. */
class UiContrastTest {
    private static final double TEXT_MIN = 4.5;
    private static final double GRAPHIC_MIN = 3.0;

    private static double channel(int value) {
        double v = value / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    static double luminance(int rgb) {
        return 0.2126 * channel(rgb >> 16 & 0xFF) + 0.7152 * channel(rgb >> 8 & 0xFF) + 0.0722 * channel(rgb & 0xFF);
    }

    static double ratio(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static void assertContrast(String name, int color, double minimum) {
        // The lighter top of the window is the hardest background for light text.
        for (int background : new int[]{UiStyle.WINDOW_TOP & 0xFFFFFF, UiStyle.WINDOW_BOTTOM & 0xFFFFFF}) {
            double ratio = ratio(color & 0xFFFFFF, background);
            assertTrue(ratio >= minimum, name + " has contrast " + ratio + " on " + Integer.toHexString(background));
        }
    }

    @Test
    void textColorsAreReadable() {
        assertContrast("TEXT", UiStyle.TEXT, TEXT_MIN);
        assertContrast("MUTED", UiStyle.MUTED, TEXT_MIN);
        assertContrast("HEADING", UiStyle.HEADING, TEXT_MIN);
        assertContrast("GOLD", UiStyle.GOLD, TEXT_MIN);
        assertContrast("GOOD", UiStyle.GOOD, TEXT_MIN);
        assertContrast("BAD", UiStyle.BAD, TEXT_MIN);
        assertContrast("COOLDOWN", UiStyle.COOLDOWN, TEXT_MIN);
    }

    @Test
    void barsAndLinesStandOut() {
        assertContrast("MANA", UiStyle.MANA, GRAPHIC_MIN);
        assertContrast("XP", UiStyle.XP, GRAPHIC_MIN);
        assertContrast("FATIGUE", UiStyle.FATIGUE, GRAPHIC_MIN);
        assertContrast("LINE_BRIGHT", UiStyle.LINE_BRIGHT, GRAPHIC_MIN);
        assertContrast("LINE_SOFT", UiStyle.LINE_SOFT, GRAPHIC_MIN);
    }
}
