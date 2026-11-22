package com.orijua.mitofus.feature.mob;

import com.orijua.mitofus.core.config.StunBalance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StunBalanceTest {
    @Test
    void durationInTicks() {
        assertEquals(40, StunBalance.DEFAULT.durationTicks());
        assertEquals(0, new StunBalance(0.4, 0).durationTicks());
    }

    @Test
    void overrideAppliesOnlyInsideTheAction() {
        StunBalance custom = new StunBalance(0.9, 5);
        StunBalance[] seen = new StunBalance[1];
        StunBalance.withOverride(custom, () -> seen[0] = StunBalance.current());
        assertEquals(custom, seen[0]);
        assertEquals(StunBalance.DEFAULT, StunBalance.current());
    }
}
