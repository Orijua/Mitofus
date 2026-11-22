package com.orijua.mitofus.feature.fatigue.client;

import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.feature.fatigue.ClientFatigue.Display;
import com.orijua.mitofus.feature.fatigue.FatigueRules;
import com.orijua.mitofus.feature.fatigue.FatigueRules.Level;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FatigueHudRulesTest {
    private static final java.util.List<Double> WARN_AT = java.util.List.of(0.15, 0.08, 0.02);

    @Test
    void barShowsByMode() {
        assertFalse(FatigueHudRules.showsBar(Display.AUTO, 1.0f));
        assertFalse(FatigueHudRules.showsBar(Display.AUTO, 0.996f));
        assertTrue(FatigueHudRules.showsBar(Display.AUTO, 0.994f));
        assertTrue(FatigueHudRules.showsBar(Display.AUTO, 0f));
        assertTrue(FatigueHudRules.showsBar(Display.ALWAYS, 1.0f));
        assertFalse(FatigueHudRules.showsBar(Display.HIDDEN, 0.5f));
        assertFalse(FatigueHudRules.showsBar(Display.HIDDEN, 0f));
    }

    @Test
    void barEdgeAt0995() {
        assertTrue(FatigueHudRules.showsBar(Display.AUTO, 0.9949f));
        assertFalse(FatigueHudRules.showsBar(Display.AUTO, 0.9951f));
        // 0.995f is 0.99500000477 as a float, so it reads as 100 % and the bar hides.
        assertEquals(100, FatigueRules.percent(0.995f));
        assertFalse(FatigueHudRules.showsBar(Display.AUTO, 0.995f));
    }

    @Test
    void barOutOfRange() {
        assertTrue(FatigueHudRules.showsBar(Display.AUTO, Float.NaN));
        assertTrue(FatigueHudRules.showsBar(Display.AUTO, -0.1f));
        assertFalse(FatigueHudRules.showsBar(Display.AUTO, 1.5f));
        assertFalse(FatigueHudRules.showsBar(Display.HIDDEN, Float.NaN));
        assertTrue(FatigueHudRules.showsBar(Display.ALWAYS, Float.NaN));
    }

    @Test
    void barMatchesTheShownNumber() {
        float[] values = {0f, 0.5f, 0.9949f, 0.995f, 0.9951f, 1f, Float.NaN, -0.1f, 1.5f};
        for (float e : values) {
            assertEquals(FatigueRules.percent(e) < 100, FatigueHudRules.showsBar(Display.AUTO, e), "energy " + e);
        }
    }

    @Test
    void numberShowsByModeAndLevel() {
        assertFalse(FatigueHudRules.showsNumber(Display.AUTO, Level.NORMAL));
        assertTrue(FatigueHudRules.showsNumber(Display.AUTO, Level.LOW));
        assertTrue(FatigueHudRules.showsNumber(Display.AUTO, Level.CRITICAL));
        assertTrue(FatigueHudRules.showsNumber(Display.ALWAYS, Level.NORMAL));
        for (Level level : Level.values()) {
            assertFalse(FatigueHudRules.showsNumber(Display.HIDDEN, level));
        }
    }

    @Test
    void numberAppearsAtTheWarningThreshold() {
        assertTrue(FatigueHudRules.showsNumber(Display.AUTO, FatigueRules.shownLevel((double) 0.15f, WARN_AT)));
        assertFalse(FatigueHudRules.showsNumber(Display.AUTO, FatigueRules.shownLevel((double) 0.156f, WARN_AT)));
    }

    @Test
    void colors() {
        assertEquals(UiStyle.FATIGUE, FatigueHudRules.colorFor(Level.NORMAL));
        assertEquals(UiStyle.COOLDOWN, FatigueHudRules.colorFor(Level.LOW));
        assertEquals(0xFF000000 | UiStyle.BAD, FatigueHudRules.colorFor(Level.CRITICAL));
        Set<Integer> distinct = new HashSet<>();
        for (Level level : Level.values()) {
            assertEquals(0xFF, FatigueHudRules.colorFor(level) >>> 24);
            distinct.add(FatigueHudRules.colorFor(level));
        }
        assertEquals(3, distinct.size());
    }

    @Test
    void alphaFlashesOnlyWhenCritical() {
        long[] times = {0, 299, 300, 599, 600, 900};
        float[] expected = {0.45f, 0.45f, 1f, 1f, 0.45f, 1f};
        for (int i = 0; i < times.length; i++) {
            assertEquals(expected[i], FatigueHudRules.alpha(Level.CRITICAL, 0.05f, times[i]), 1e-6f, "ms " + times[i]);
            assertEquals(1.0f, FatigueHudRules.alpha(Level.NORMAL, 0.5f, times[i]), 1e-6f);
            assertEquals(1.0f, FatigueHudRules.alpha(Level.LOW, 0.1f, times[i]), 1e-6f);
            assertEquals(1.0f, FatigueHudRules.alpha(Level.CRITICAL, 0f, times[i]), 1e-6f);
        }
    }

    @Test
    void alphaWithNegativeMillisDocumentsCurrentBehavior() {
        // Util.getMillis() is never negative; this only pins what the formula does if it were.
        assertEquals(0.45f, FatigueHudRules.alpha(Level.CRITICAL, 0.05f, -1), 1e-6f);
        assertEquals(1.0f, FatigueHudRules.alpha(Level.CRITICAL, 0.05f, -300), 1e-6f);
    }
}
