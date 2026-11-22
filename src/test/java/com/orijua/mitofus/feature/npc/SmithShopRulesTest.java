package com.orijua.mitofus.feature.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmithShopRulesTest {
    @Test
    void anExactPaymentIsEnough() {
        assertEquals(SmithShopRules.Check.OK, SmithShopRules.check(false, 150, 150, 0, 0));
        assertEquals(SmithShopRules.Check.OK, SmithShopRules.check(false, 151, 150, 0, 0));
    }

    @Test
    void oneGoldLessIsNotEnough() {
        assertEquals(SmithShopRules.Check.NOT_ENOUGH, SmithShopRules.check(false, 149, 150, 0, 0));
        assertEquals(SmithShopRules.Check.NOT_ENOUGH, SmithShopRules.check(false, 0, 1, 0, 0));
    }

    @Test
    void theSecondCostCountsToo() {
        assertEquals(SmithShopRules.Check.OK, SmithShopRules.check(false, 10, 10, 2, 2));
        assertEquals(SmithShopRules.Check.NOT_ENOUGH, SmithShopRules.check(false, 10, 10, 1, 2), "a diamond short");
        assertEquals(SmithShopRules.Check.NOT_ENOUGH, SmithShopRules.check(false, 10, 10, 0, 2));
        assertEquals(SmithShopRules.Check.NOT_ENOUGH, SmithShopRules.check(false, 9, 10, 2, 2));
    }

    @Test
    void soldOutComesBeforeNotEnough() {
        assertEquals(SmithShopRules.Check.SOLD_OUT, SmithShopRules.check(true, 0, 10, 0, 2));
        assertEquals(SmithShopRules.Check.SOLD_OUT, SmithShopRules.check(true, 100, 10, 5, 2));
    }

    @Test
    void aPriceAboveOneStackWorks() {
        assertEquals(SmithShopRules.Check.OK, SmithShopRules.check(false, 1250, 1250, 0, 0));
        assertEquals(SmithShopRules.Check.NOT_ENOUGH, SmithShopRules.check(false, 1249, 1250, 0, 0));
    }

    @Test
    void theSecondCostIsIgnoredWhenThereIsNone() {
        assertEquals(SmithShopRules.Check.OK, SmithShopRules.check(false, 5, 5, 0, 0));
    }
}
