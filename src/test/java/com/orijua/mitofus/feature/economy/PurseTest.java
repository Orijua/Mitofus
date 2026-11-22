package com.orijua.mitofus.feature.economy;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PurseTest {
    @Test
    void anAmountIsAddedAndTaken() {
        Purse purse = Purse.EMPTY.plus(250L).minus(100L);
        assertEquals(150L, purse.iron());
    }

    @Test
    void theBalanceIsNeverNegative() {
        assertEquals(0L, new Purse(-5L).iron());
        assertEquals(0L, new Purse(10L).minus(50L).iron());
        assertEquals(10L, new Purse(10L).plus(-5L).iron());
    }

    @Test
    void itRoundTripsThroughItsCodec() {
        Purse purse = new Purse(1234L);
        var json = Purse.CODEC.encodeStart(JsonOps.INSTANCE, purse).getOrThrow();
        assertEquals(purse, Purse.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }
}
