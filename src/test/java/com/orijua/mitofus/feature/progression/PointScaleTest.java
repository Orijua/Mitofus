package com.orijua.mitofus.feature.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PointScaleTest {
    @Test
    void tenPointsPerLevelUsesTheValuesAsWritten() {
        assertEquals(1.0, ProgressionRules.pointScale(10), 1e-9);
    }

    @Test
    void aLevelGivesTheSamePowerWhateverThePointsPerLevel() {
        for (int points : new int[]{1, 4, 5, 10, 20}) {
            assertEquals(ProgressionRules.REFERENCE_POINTS_PER_LEVEL, ProgressionRules.pointScale(points) * points, 1e-9);
        }
    }

    @Test
    void zeroPointsPerLevelDoesNotDivideByZero() {
        assertEquals(10.0, ProgressionRules.pointScale(0), 1e-9);
    }
}
