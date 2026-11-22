package com.orijua.mitofus.feature.economy;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyTest {
    @Test
    void floorAbsorbsFloatingPointNoise() {
        assertEquals(95L, Money.floor(100 * (1.0 - 0.05)));
        assertEquals(94L, Money.floor(94.9));
    }

    @Test
    void goldRoundsDownAndIsNeverBelowOne() {
        assertEquals(1L, Money.gold(0.4));
        assertEquals(1L, Money.gold(0.0));
        assertEquals(1L, Money.gold(-5.0));
        assertEquals(1L, Money.gold(Double.NaN));
        assertEquals(3L, Money.gold(2.9999999999));
        assertEquals(5L, Money.gold(5.999));
        assertEquals(Long.MAX_VALUE, Money.gold(1e30));
        assertEquals(1L, Money.gold(Double.NEGATIVE_INFINITY));
        assertEquals(Long.MAX_VALUE, Money.gold(Double.POSITIVE_INFINITY));
    }

    @Test
    void goldFromEmeraldsIsAThirdRoundedDownWithAMinimumOfOne() {
        for (int emeralds = 1; emeralds <= 5; emeralds++) {
            assertEquals(1L, Money.goldFromEmeralds(emeralds), "emeralds " + emeralds);
        }
        assertEquals(2L, Money.goldFromEmeralds(6));
        assertEquals(4L, Money.goldFromEmeralds(12));
        assertEquals(5L, Money.goldFromEmeralds(16));
        assertEquals(10L, Money.goldFromEmeralds(32));
        assertEquals(16L, Money.goldFromEmeralds(48));
        assertEquals(21L, Money.goldFromEmeralds(64));
        assertEquals(3333L, Money.goldFromEmeralds(10_000));
    }

    @Test
    void goldBordersRoundDownWithAMinimumOfOne() {
        assertEquals(1L, Money.goldFromEmeralds(0));
        assertEquals(1L, Money.goldFromEmeralds(-3));
        assertEquals(1L, Money.gold(1.0));
        assertEquals(1L, Money.gold(0.9999999999), "below 1 counts as 1 either way");
        assertEquals(2L, Money.gold(1.9999999999), "the 1e-9 tolerance absorbs the rounding error of a double");
    }

    @Test
    void formatGroupsThousandsWithADot() {
        assertEquals("0", Money.format(0));
        assertEquals("99", Money.format(99));
        assertEquals("100", Money.format(100));
        assertEquals("999", Money.format(999));
        assertEquals("1.000", Money.format(1000));
        assertEquals("6.400", Money.format(6400));
        assertEquals("999.999", Money.format(999_999));
        assertEquals("1.000.000", Money.format(1_000_000));
        assertEquals("-6.400", Money.format(-6400));
        assertEquals("-999", Money.format(-999));
        assertEquals("9.223.372.036.854.775.807", Money.format(Long.MAX_VALUE));
        assertEquals("-9.223.372.036.854.775.808", Money.format(Long.MIN_VALUE));
    }

    @Test
    void formatIgnoresTheDefaultLocale() {
        Locale before = Locale.getDefault();
        try {
            for (Locale locale : new Locale[]{Locale.US, Locale.GERMANY, Locale.FRANCE, Locale.forLanguageTag("ar-EG")}) {
                Locale.setDefault(locale);
                assertEquals("6.400", Money.format(6400), locale.toString());
                assertEquals("-1.234.567", Money.format(-1_234_567), locale.toString());
            }
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    void ironIsAHundredthOfGoldRoundedDownWithAMinimumOfOneIron() {
        assertEquals(1L, Money.iron(0.0));
        assertEquals(1L, Money.iron(0.001));
        assertEquals(1L, Money.iron(-4.0));
        assertEquals(1L, Money.iron(Double.NaN));
        assertEquals(33L, Money.iron(0.33));
        assertEquals(280L, Money.iron(2.8));
        assertEquals(100L, Money.iron(1.0));
        assertEquals(Long.MAX_VALUE, Money.iron(1e30));
    }

    @Test
    void goldAndIronPartsAddUp() {
        assertEquals(3L, Money.goldPart(311));
        assertEquals(11L, Money.ironPart(311));
        assertEquals(0L, Money.goldPart(99));
        assertEquals(99L, Money.ironPart(99));
    }

    @Test
    void copperCashesInAsIronAtAHundredToOne() {
        assertEquals(0L, Money.ironFromCopper(20));
        assertEquals(1L, Money.ironFromCopper(100));
        assertEquals(2L, Money.ironFromCopper(250));
        assertEquals(0L, Money.ironFromCopper(-5));
    }

    @Test
    void theFourTiersAreWorthOneHundredOfTheOneBelow() {
        assertEquals(100, Money.COPPER_PER_IRON);
        assertEquals(100, Money.IRON_PER_GOLD);
        assertEquals(100, Money.GOLD_PER_DIAMOND);
        assertEquals(10_000L, Money.IRON_PER_DIAMOND);
        assertEquals(123L, Money.diamondPart(1_234_545L));
        assertEquals(45L, Money.goldRest(1_234_545L));
        assertEquals(45L, Money.ironPart(1_234_545L));
        assertEquals(0L, Money.diamondPart(9_999L));
    }
}
