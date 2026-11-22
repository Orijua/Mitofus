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

    @Test
    void copperLeftOverStaysUntilItMakesAnIron() {
        Purse purse = Purse.EMPTY.plusCopper(70L);
        assertEquals(0L, purse.iron());
        assertEquals(70, purse.copper());
        Purse more = purse.plusCopper(250L);
        assertEquals(3L, more.iron());
        assertEquals(20, more.copper());
        assertEquals(more, Purse.CODEC.parse(JsonOps.INSTANCE, Purse.CODEC.encodeStart(JsonOps.INSTANCE, more).getOrThrow()).getOrThrow());
    }

    @Test
    void aPurseSavedWithoutCopperStillLoads() {
        var json = new com.google.gson.JsonObject();
        json.addProperty("iron", 40L);
        assertEquals(new Purse(40L, 0), Purse.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }
}
