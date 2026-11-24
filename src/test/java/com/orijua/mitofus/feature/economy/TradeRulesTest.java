package com.orijua.mitofus.feature.economy;

import com.orijua.mitofus.core.config.TradeBalance;
import com.orijua.mitofus.feature.economy.TradeRules.Refusal;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.google.gson.JsonObject;
import com.orijua.mitofus.lang.LangFiles;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeRulesTest {
    private static final TradeBalance B = TradeBalance.DEFAULT;

    @Test
    void anEpicIronSwordOfLevel1IsWorth7() {
        long ref = TradeRules.reference(ItemRating.EPIC, 1.0, 1, 0, 0, false, B);
        assertEquals(7, ref);
        assertEquals(2, TradeRules.sellPrice(ref, 0, B), "7 x 0,4 = 2,8 rounded down");
    }

    @Test
    void aDiamondLegendaryPlusFiveOfLevel1IsWorth450() {
        long ref = TradeRules.reference(ItemRating.LEGENDARY, 2.0, 1, 0, 5, false, B);
        assertEquals(450, ref);
        assertEquals(180, TradeRules.sellPrice(ref, 0, B));
    }

    @Test
    void aDiamondEpicWithNoBonusesIsWorth14AndSellsFor5() {
        long ref = TradeRules.reference(ItemRating.EPIC, 2.0, 1, 0, 0, false, B);
        assertEquals(14, ref);
        assertEquals(5, TradeRules.sellPrice(ref, 0, B), "14 x 0,4 = 5,6 rounded down");
    }

    @Test
    void theReferenceIsTheRoundedDownValueAndTheValueKeepsTheDecimals() {
        assertEquals(0.5, TradeRules.referenceValue(ItemRating.NORMAL, 0.5, 1, 0, 0, false, 1.0, B), 1e-12);
        assertEquals(46.08, TradeRules.referenceValue(ItemRating.UNIQUE, 1.0, 15, 2, 0, false, 1.0, B), 1e-9);
        assertEquals(11.52, TradeRules.referenceValue(ItemRating.UNIQUE, 1.0, 15, 2, 0, true, 1.0, B), 1e-9);
        assertEquals(7 * 2.0 * 1.38 * 1.1 * 1.0, TradeRules.referenceValue(ItemRating.EPIC, 2.0, 20, 1, 0, false, 1.0, B), 1e-9);
        assertEquals(Money.gold(0.5), TradeRules.reference(ItemRating.NORMAL, 0.5, 1, 0, 0, false, 1.0, B));
        for (ItemRating rating : ItemRating.values()) {
            for (int level = 0; level < 40; level += 3) {
                double value = TradeRules.referenceValue(rating, 1.0, level, 1, 2, false, 0.6, B);
                assertEquals(Money.gold(value), TradeRules.reference(rating, 1.0, level, 1, 2, false, 0.6, B));
            }
        }
    }

    @Test
    void aSaleOfAReferenceOfOneIsOneGold() {
        assertEquals(1, TradeRules.sellPrice(1, 0, B));
        assertEquals(1, TradeRules.sellPrice(1, 36, B), "never below 1");
    }

    @Test
    void theSellCapIs3333Gold() {
        assertTrue(TradeRules.payable(3333, B));
        assertFalse(TradeRules.payable(3334, B));
    }

    @Test
    void aWoodenNormalPieceIsWorthAtLeastOne() {
        long ref = TradeRules.reference(ItemRating.NORMAL, 0.5, 1, 0, 0, false, B);
        assertEquals(1, ref);
        assertEquals(1, TradeRules.sellPrice(ref, 0, B));
    }

    @Test
    void enhancementCountsForEveryPiece() {
        assertEquals(10, TradeRules.reference(ItemRating.EPIC, 1.0, 1, 0, 5, false, B), "7 x 1,5 = 10,5");
        assertEquals(7, TradeRules.reference(ItemRating.EPIC, 1.0, 1, 0, 0, false, B));
    }

    @Test
    void everyBasePrice() {
        assertEquals(1, TradeRules.reference(ItemRating.NORMAL, 1.0, 1, 0, 0, false, B));
        assertEquals(3, TradeRules.reference(ItemRating.RARE, 1.0, 1, 0, 0, false, B));
        assertEquals(7, TradeRules.reference(ItemRating.EPIC, 1.0, 1, 0, 0, false, B));
        assertEquals(30, TradeRules.reference(ItemRating.UNIQUE, 1.0, 1, 0, 0, false, B));
        assertEquals(150, TradeRules.reference(ItemRating.LEGENDARY, 1.0, 1, 0, 0, false, B));
        assertEquals(600, TradeRules.reference(ItemRating.ANCESTRAL, 1.0, 1, 0, 0, false, B));
    }

    /** The approved table: rating, material, level, options. */
    @Test
    void theApprovedReferenceTable() {
        assertEquals(1, TradeRules.reference(ItemRating.NORMAL, 1.0, 5, 0, 0, false, B), "iron normal level 5: 1 x 1,08 = 1,08");
        assertEquals(3, TradeRules.reference(ItemRating.RARE, 1.0, 7, 1, 0, false, B), "iron rare level 7, 1 option: 3 x 1,12 x 1,1 = 3,696");
        assertEquals(9, TradeRules.reference(ItemRating.EPIC, 1.0, 10, 1, 0, false, B), "iron epic level 10, 1 option: 7 x 1,18 x 1,1 = 9,086");
        assertEquals(21, TradeRules.reference(ItemRating.EPIC, 2.0, 20, 1, 0, false, B), "diamond epic level 20, 1 option: 7 x 2 x 1,38 x 1,1 = 21,252");
        assertEquals(46, TradeRules.reference(ItemRating.UNIQUE, 1.0, 15, 2, 0, false, B), "iron unique level 15, 2 options: 30 x 1,28 x 1,2 = 46,08");
        assertEquals(7, TradeRules.reference(ItemRating.RARE, 2.0, 10, 1, 0, false, B), "wolfram rare level 10, 1 option: 3 x 2 x 1,18 x 1,1 = 7,788");
        assertEquals(655, TradeRules.reference(ItemRating.LEGENDARY, 2.0, 35, 3, 0, false, B), "diamond legendary level 35, 3 options: 150 x 2 x 1,68 x 1,3 = 655,2");
    }

    @Test
    void theLevelAndTheOptionsRaiseThePriceInSteps() {
        // 150 x (1 + 0,02 x (level - 1)) x (1 + 0,1 x options)
        assertEquals(150, TradeRules.reference(ItemRating.LEGENDARY, 1.0, 1, 0, 0, false, B));
        assertEquals(153, TradeRules.reference(ItemRating.LEGENDARY, 1.0, 2, 0, 0, false, B));
        assertEquals(165, TradeRules.reference(ItemRating.LEGENDARY, 1.0, 1, 1, 0, false, B));
        assertEquals(150, TradeRules.reference(ItemRating.LEGENDARY, 1.0, 0, 0, 0, false, B), "a level below 1 adds nothing");
        assertEquals(150, TradeRules.reference(ItemRating.LEGENDARY, 1.0, 1, -3, 0, false, B), "a negative number of options adds nothing");
    }

    @Test
    void theEnhancementCountsInAnArmorToo() {
        // an armor is just a piece: iron epic level 10 with +4 is 7 x 1,18 x 1,4 = 11,564
        assertEquals(11, TradeRules.reference(ItemRating.EPIC, 1.0, 10, 0, 4, false, B));
        assertEquals(8, TradeRules.reference(ItemRating.EPIC, 1.0, 10, 0, 0, false, B), "7 x 1,18 = 8,26");
    }

    @Test
    void anImpossibleLevelIsOnlyAboveTheMarginNotOnIt() {
        assertFalse(TradeRules.impossible(0, 15, B), "no stored level is never impossible");
        assertFalse(TradeRules.impossible(15, 15, B));
        assertFalse(TradeRules.impossible(65, 15, B), "exactly the margin (50) is still fine");
        assertTrue(TradeRules.impossible(66, 15, B), "one above the margin is impossible");
        assertTrue(TradeRules.impossible(300, 15, B));
        assertFalse(TradeRules.impossible(5, 15, B), "a stored level below the derived one is not impossible");
    }

    @Test
    void theImpossibleFactorCutsThePriceOnceBeforeTheRounding() {
        assertEquals(46, TradeRules.reference(ItemRating.UNIQUE, 1.0, 15, 2, 0, false, B));
        assertEquals(11, TradeRules.reference(ItemRating.UNIQUE, 1.0, 15, 2, 0, true, B), "46,08 x 0,25 = 11,52");
    }

    @Test
    void theGuadaleteCase() {
        // 62 is the table case with material 1 (NOT the real Guadalete, which is netherite): unique, level 300, 2 options, impossible: 30 x 6,98 x 1,2 x 0,25 = 62,82
        assertEquals(62, TradeRules.reference(ItemRating.UNIQUE, 1.0, 300, 2, 0, true, B));
        // the real Guadalete sword is netherite (material 3): 188,46
        assertEquals(188, TradeRules.reference(ItemRating.UNIQUE, 3.0, 300, 2, 0, true, B));
    }

    @Test
    void theSaturationFollowsThePressure() {
        assertEquals(1.0, TradeRules.saturation(0, B));
        assertEquals(0.95, TradeRules.saturation(1, B), 1e-12);
        assertEquals(Math.pow(0.95, 36), TradeRules.saturation(36, B), 1e-12);
        assertEquals(0.15, TradeRules.saturation(37, B));
        assertEquals(0.15, TradeRules.saturation(1000, B));
        assertEquals(1.0, TradeRules.saturation(-3, B));
    }

    @Test
    void oneSaleLowersThePriceOfAnEpicIronSword() {
        assertEquals(15, TradeRules.sellPrice(40, 1, B));
    }

    @Test
    void thePressureRecoversTwoPerDay() {
        assertEquals(3, TradeRules.recovered(5, 0, 1, B));
        assertEquals(0, TradeRules.recovered(5, 0, 3, B));
        assertEquals(0, TradeRules.recovered(5, 0, 100, B));
        assertEquals(5, TradeRules.recovered(5, 4, 4, B));
    }

    @Test
    void daysThatGoBackCountAsNone() {
        assertEquals(5, TradeRules.recovered(5, 10, 3, B));
    }

    @Test
    void aNegativePressureIsNone() {
        assertEquals(0, TradeRules.recovered(-4, 0, 0, B));
    }

    @Test
    void recoveredWithHugeDays() {
        assertEquals(0, TradeRules.recovered(5, Long.MIN_VALUE, Long.MAX_VALUE, B));
        assertEquals(5, TradeRules.recovered(5, Long.MAX_VALUE, Long.MIN_VALUE, B));
        assertEquals(0, TradeRules.recovered(Integer.MAX_VALUE, 0, Long.MAX_VALUE, B));
    }

    @Test
    void noRecoveryWhenPerDayIsZeroOrNegative() {
        assertEquals(5, TradeRules.recovered(5, 0, 10, TradeBalancesForTests.withRecoveryPerDay(0)));
        assertEquals(5, TradeRules.recovered(5, 0, 10, TradeBalancesForTests.withRecoveryPerDay(-3)));
    }

    @Test
    void payableNeedsAtLeastOne() {
        assertFalse(TradeRules.payable(0, B));
        assertFalse(TradeRules.payable(-5, B));
        assertTrue(TradeRules.payable(1, B));
    }

    @Test
    void payableStopsAtTheSellCap() {
        long cap = B.maxSellGold();
        assertTrue(TradeRules.payable(cap, B));
        assertFalse(TradeRules.payable(cap + 1, B));
    }

    @Test
    void paymentsSplitInStacks() {
        long[] sale = TradeRules.payments(720, 64);
        assertEquals(12, sale.length);
        for (int i = 0; i < 11; i++) {
            assertEquals(64, sale[i]);
        }
        assertEquals(16, sale[11]);
        assertArrayEquals(new long[]{64}, TradeRules.payments(64, 64));
        assertArrayEquals(new long[]{1}, TradeRules.payments(1, 64));
        long total = 0;
        for (long part : TradeRules.payments(B.maxSellGold(), 64)) {
            total += part;
        }
        assertEquals(B.maxSellGold(), total);
    }

    private static TradeRules.Piece piece(TradeCategory category, ItemRating rating, long reference) {
        return new TradeRules.Piece(new TradeKey(category, rating), reference);
    }

    @Test
    void threeEpicSwordsOfTheSameKeyPayLessEach() {
        TradeRules.Piece sword = piece(TradeCategory.SWORD, ItemRating.EPIC, 40);
        long[] prices = TradeRules.sellPrices(List.of(sword, sword, sword), TradeSaturation.EMPTY, 0, B);
        assertArrayEquals(new long[]{16, 15, 14}, prices);
        assertEquals(45, TradeRules.total(prices));
    }

    @Test
    void interleavedKeysOnlyPressTheirOwn() {
        TradeRules.Piece sword = piece(TradeCategory.SWORD, ItemRating.EPIC, 40);
        TradeRules.Piece helmet = piece(TradeCategory.HELMET, ItemRating.EPIC, 40);
        assertArrayEquals(new long[]{16, 16, 15}, TradeRules.sellPrices(List.of(sword, helmet, sword), TradeSaturation.EMPTY, 0, B));
    }

    @Test
    void earlierPressureCountsInABatch() {
        TradeSaturation pressed = TradeSaturation.EMPTY;
        TradeKey key = new TradeKey(TradeCategory.SWORD, ItemRating.EPIC);
        for (int i = 0; i < 36; i++) {
            pressed = pressed.recordSale(key, 0, B);
        }
        TradeRules.Piece sword = piece(TradeCategory.SWORD, ItemRating.EPIC, 40);
        assertArrayEquals(new long[]{2, 2}, TradeRules.sellPrices(List.of(sword, sword), pressed, 0, B));
    }

    @Test
    void anEmptyBatchPricesNothingAndKeepsTheSaturation() {
        TradeSaturation before = TradeSaturation.EMPTY.recordSale(new TradeKey(TradeCategory.AXE, ItemRating.RARE), 0, B);
        assertEquals(0, TradeRules.sellPrices(List.of(), before, 0, B).length);
        assertEquals(TradeSaturation.EMPTY.recordSale(new TradeKey(TradeCategory.AXE, ItemRating.RARE), 0, B), before);
        assertEquals(0, TradeRules.total(new long[0]));
    }

    @Test
    void sellPricesEqualApplyingSellPriceAndRecordSaleByHand() {
        TradeKey[] keys = {new TradeKey(TradeCategory.SWORD, ItemRating.EPIC), new TradeKey(TradeCategory.HELMET, ItemRating.RARE),
                new TradeKey(TradeCategory.BOW, ItemRating.LEGENDARY)};
        long[] references = {40, 12, 600};
        List<TradeRules.Piece> pieces = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            pieces.add(new TradeRules.Piece(keys[(i * 7) % 3], references[(i * 7) % 3]));
        }
        TradeSaturation start = TradeSaturation.EMPTY.recordSale(keys[0], 0, B);
        TradeSaturation saturation = start;
        long[] expected = new long[pieces.size()];
        for (int i = 0; i < pieces.size(); i++) {
            TradeRules.Piece piece = pieces.get(i);
            expected[i] = TradeRules.sellPrice(piece.reference(), saturation.pressure(piece.key(), 0, B), B);
            saturation = saturation.recordSale(piece.key(), 0, B);
        }
        assertArrayEquals(expected, TradeRules.sellPrices(pieces, start, 0, B));
    }

    @Test
    void sellableSlotsSkipEmptyAndRefusedOnesInOrder() {
        Refusal[] refusals = new Refusal[36];
        boolean[] empty = new boolean[36];
        java.util.Arrays.fill(empty, true);
        empty[2] = false;
        empty[5] = false;
        refusals[5] = Refusal.LOANED;
        empty[7] = false;
        refusals[7] = Refusal.STACKED;
        empty[9] = false;
        refusals[9] = Refusal.NO_VALUE;
        empty[11] = false;
        refusals[11] = Refusal.UNRATED;
        empty[20] = false;
        empty[35] = false;
        assertEquals(List.of(2, 20, 35), TradeRules.sellableSlots(36, slot -> empty[slot], slot -> Optional.ofNullable(refusals[slot])));
        assertEquals(List.of(), TradeRules.sellableSlots(36, slot -> true, slot -> Optional.empty()));
        assertEquals(List.of(), TradeRules.sellableSlots(36, slot -> false, slot -> Optional.of(Refusal.UNRATED)));
    }

    @Test
    void validBatchChecksSizeRangeAndRepeats() {
        assertFalse(TradeRules.validBatch(List.of(), 36));
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            all.add(i);
        }
        assertTrue(TradeRules.validBatch(all, 36));
        List<Integer> tooMany = new ArrayList<>(all);
        tooMany.add(0);
        assertFalse(TradeRules.validBatch(tooMany, 36));
        assertFalse(TradeRules.validBatch(List.of(3, 3), 36));
        assertFalse(TradeRules.validBatch(List.of(-1), 36));
        assertFalse(TradeRules.validBatch(List.of(36), 36));
        assertFalse(TradeRules.validBatch(List.of(0, 36), 36));
        assertTrue(TradeRules.validBatch(List.of(0), 36));
        assertTrue(TradeRules.validBatch(List.of(35), 36));
        assertTrue(TradeRules.validBatch(List.of(35, 0), 36));
        assertTrue(TradeRules.validBatch(List.of(40), 41));
        assertFalse(TradeRules.validBatch(List.of(10), 10));
        assertEquals(36, TradeRules.MAX_BATCH);
    }

    @Test
    void saturationPercentRounds() {
        assertEquals(100, TradeRules.saturationPercent(0, B));
        assertEquals(95, TradeRules.saturationPercent(1, B));
        assertEquals(15, TradeRules.saturationPercent(37, B));
        assertEquals(100, TradeRules.saturationPercent(1, TradeBalancesForTests.withSaturationStep(0.999)));
    }

    @Test
    void floorBeatsTheCurveBeforeTheCap() {
        TradeBalance floor = TradeBalancesForTests.withSaturationFloor(0.9);
        assertEquals(0.95, TradeRules.saturation(1, floor), 1e-12);
        assertEquals(0.9, TradeRules.saturation(3, floor), 1e-12);
        assertEquals(0.9, TradeRules.saturation(37, floor), 1e-12);
    }

    @Test
    void priceAtTheCap() {
        assertEquals(2, TradeRules.sellPrice(40, 37, B));
    }

    @Test
    void negativeEnhancementCountsAsZero() {
        assertEquals(TradeRules.reference(ItemRating.EPIC, 1.0, 1, 0, 0, false, B), TradeRules.reference(ItemRating.EPIC, 1.0, 1, 0, -5, false, B));
    }

    @Test
    void parseItemMultiplierReadsAnIdAndANumber() {
        TradeRules.ItemMultiplier guadalete = TradeRules.parseItemMultiplier("mitofus:guadalete_sword=0.25").orElseThrow();
        assertEquals("mitofus:guadalete_sword", guadalete.id().toString());
        assertEquals(0.25, guadalete.multiplier());
        assertEquals(2.0, TradeRules.parseItemMultiplier("  minecraft:iron_sword = 2  ").orElseThrow().multiplier());
        assertEquals(0.0, TradeRules.parseItemMultiplier("minecraft:iron_sword=0").orElseThrow().multiplier());
        assertEquals(100.0, TradeRules.parseItemMultiplier("minecraft:iron_sword=100").orElseThrow().multiplier());
        assertEquals("mitofus:wolfram_sword=2.0", TradeRules.parseItemMultiplier("mitofus:wolfram_sword=2").orElseThrow().asString());
    }

    @Test
    void parseItemMultiplierRejectsWhatIsNotAnObjectAndANumber() {
        for (String bad : new String[]{"minecraft:iron_sword", "#minecraft:swords=2", "minecraft:iron_sword=NaN",
                "minecraft:iron_sword=Infinity", "minecraft:iron_sword=-1", "minecraft:iron_sword=100.1", "minecraft:iron_sword=",
                "minecraft:iron_sword=two", "=2", "minecraft:=2", "Not An Id=2", ""}) {
            assertTrue(TradeRules.parseItemMultiplier(bad).isEmpty(), bad);
        }
        assertTrue(TradeRules.parseItemMultiplier(null).isEmpty());
    }

    /** Java's number syntax is what the config accepts: a suffix "d" or an exponent are numbers, hex floats too. */
    @Test
    void parseItemMultiplierAcceptsWhatJavaReadsAsANumber() {
        assertEquals(2.0, TradeRules.parseItemMultiplier("minecraft:iron_sword=2d").orElseThrow().multiplier());
        assertEquals(10.0, TradeRules.parseItemMultiplier("minecraft:iron_sword=1e1").orElseThrow().multiplier());
        assertEquals(2.0, TradeRules.parseItemMultiplier("minecraft:iron_sword=0x1p1").orElseThrow().multiplier());
    }

    @Test
    void parseItemMultiplierTakesTheLastEqualsAndSoRejectsAnEqualsInTheId() {
        assertTrue(TradeRules.parseItemMultiplier("a=b=2").isEmpty(), "the id would be a=b, which is not an id");
        assertTrue(TradeRules.parseItemMultiplier("minecraft:iron_sword==2").isEmpty(), "the id would end in =");
        assertTrue(TradeRules.parseItemMultiplier("minecraft:iron_sword=2=").isEmpty(), "nothing after the last =");
    }

    @Test
    void parseItemMultiplierReadsMinusZeroAsPlainZero() {
        TradeRules.ItemMultiplier zero = TradeRules.parseItemMultiplier("minecraft:iron_sword=-0").orElseThrow();
        assertEquals(0.0, zero.multiplier(), "assertEquals on doubles tells -0.0 from 0.0");
        assertEquals("minecraft:iron_sword=0.0", zero.asString());
        assertEquals(0.0, TradeRules.parseItemMultiplier("minecraft:iron_sword=-0.0").orElseThrow().multiplier());
    }

    @Test
    void parseItemMultiplierDoesNotAcceptUppercaseIds() {
        assertTrue(TradeRules.parseItemMultiplier("Minecraft:Iron_Sword=2").isEmpty());
        assertTrue(TradeRules.parseItemMultiplier("minecraft:Iron_Sword=2").isEmpty());
    }

    @Test
    void asStringAndParseGoRoundTripEvenForTinyAndHugeNumbers() {
        for (double value : new double[]{0.0, 1.0E-7, 0.25, 1.0, 2.5, 99.999999, 100.0}) {
            TradeRules.ItemMultiplier original = new TradeRules.ItemMultiplier(net.minecraft.resources.ResourceLocation.withDefaultNamespace("iron_sword"), value);
            assertEquals(original, TradeRules.parseItemMultiplier(original.asString()).orElseThrow(), original.asString());
        }
        assertEquals("minecraft:iron_sword=1.0E-7", new TradeRules.ItemMultiplier(
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("iron_sword"), 1.0E-7).asString());
    }

    @Test
    void itemMultipliersToleratesNullElementsAndKeepsTheFirstOfEachId() {
        List<String> lines = new ArrayList<>();
        lines.add(null);
        lines.add("minecraft:iron_sword=2");
        lines.add(null);
        lines.add("minecraft:iron_sword=3");
        lines.add("minecraft:iron_sword=2");
        lines.add("minecraft:bow=0");
        lines.add("minecraft:bow=7");
        Map<net.minecraft.resources.ResourceLocation, Double> m = TradeRules.itemMultipliers(lines);
        assertEquals(2, m.size());
        assertEquals(2.0, m.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace("iron_sword")));
        assertEquals(0.0, m.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace("bow")));
        assertTrue(TradeRules.itemMultipliers(java.util.Collections.singletonList(null)).isEmpty());
    }

    @Test
    void itemMultipliersSkipTheInvalidAndTheFirstRepeatedWins() {
        Map<net.minecraft.resources.ResourceLocation, Double> m = TradeRules.itemMultipliers(
                List.of("minecraft:iron_sword=2", "broken", "minecraft:iron_sword=5", "minecraft:bow=0.5", "minecraft:axe=NaN"));
        assertEquals(2, m.size());
        assertEquals(2.0, m.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace("iron_sword")));
        assertEquals(0.5, m.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace("bow")));
        assertTrue(TradeRules.itemMultipliers(List.of()).isEmpty());
    }

    @Test
    void durabilityPercentIsTheRoundedFactor() {
        assertEquals(100, TradeRules.durabilityPercent(1.0));
        assertEquals(63, TradeRules.durabilityPercent(0.625));
        assertEquals(25, TradeRules.durabilityPercent(0.25));
        assertEquals(0, TradeRules.durabilityPercent(0.0));
    }

    @Test
    void durabilityFactorIsLinearBetweenTheMinimumAndOne() {
        assertEquals(1.0, TradeRules.durabilityFactor(0, 100, 0.25));
        assertEquals(0.25, TradeRules.durabilityFactor(100, 100, 0.25));
        assertEquals(0.625, TradeRules.durabilityFactor(50, 100, 0.25));
        assertEquals(0.0, TradeRules.durabilityFactor(100, 100, 0.0));
    }

    @Test
    void durabilityFactorEdgeCases() {
        assertEquals(1.0, TradeRules.durabilityFactor(5, 0, 0.25), "no durability counts as new");
        assertEquals(1.0, TradeRules.durabilityFactor(5, -3, 0.25));
        assertEquals(1.0, TradeRules.durabilityFactor(-1, 100, 0.25), "negative damage counts as new");
        assertEquals(0.25, TradeRules.durabilityFactor(500, 100, 0.25), "more damage than the maximum is broken");
        assertEquals(1.0, TradeRules.durabilityFactor(77, 100, 1.0), "a minimum of 1 means wear never matters");
    }

    /** The rules do not clamp the minimum: TradeBalance is what keeps it in [0, 1]. Here only what comes out is pinned down. */
    @Test
    void durabilityFactorDoesNotClampAnOutOfRangeMinimum() {
        assertTrue(Double.isNaN(TradeRules.durabilityFactor(50, 100, Double.NaN)));
        assertTrue(Double.isNaN(TradeRules.durabilityFactor(0, 100, Double.NaN)), "even an untouched piece: the minimum takes part in the formula");
        assertEquals(1.0, TradeRules.durabilityFactor(0, 0, Double.NaN), "only a piece with no durability skips it");
        assertEquals(0.0, TradeRules.durabilityFactor(50, 100, -1.0), 1e-12, "-1 + 2 x 0,5");
        assertEquals(1.5, TradeRules.durabilityFactor(50, 100, 2.0), 1e-12, "2 - 1 x 0,5");
        assertEquals(-1.0, TradeRules.durabilityFactor(100, 100, -1.0), "broken gives the minimum as it comes");
    }

    @Test
    void durabilityFactorWithTheBiggestMaxDamage() {
        assertEquals(1.0, TradeRules.durabilityFactor(0, Integer.MAX_VALUE, 0.25));
        assertEquals(0.25, TradeRules.durabilityFactor(Integer.MAX_VALUE, Integer.MAX_VALUE, 0.25));
        assertEquals(0.625, TradeRules.durabilityFactor(Integer.MAX_VALUE / 2, Integer.MAX_VALUE, 0.25), 1e-9);
        assertEquals(1.0, TradeRules.durabilityFactor(Integer.MIN_VALUE, Integer.MAX_VALUE, 0.25), "negative damage is new");
        double almostNew = TradeRules.durabilityFactor(1, Integer.MAX_VALUE, 0.25);
        assertTrue(almostNew < 1.0 && almostNew > 0.999999, "one point of damage: " + almostNew);
    }

    @Test
    void theBalanceRefusesAMinimumOutOfRange() {
        for (double bad : new double[]{Double.NaN, -0.1, 1.1, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> TradeBalancesForTests.withMinDurabilityFactor(bad), String.valueOf(bad));
        }
    }

    /** The wear is a whole percent, rounded to the nearest (half up): 0,995 reads 100 %, so a piece that lost only 0,5 % shows no wear tooltip. */
    @Test
    void durabilityPercentRoundsToTheNearestAndTakesWhatItIsGiven() {
        assertEquals(150, TradeRules.durabilityPercent(1.5), "a factor above 1 is not clamped here");
        assertEquals(-50, TradeRules.durabilityPercent(-0.5), "nor a negative one");
        assertEquals(0, TradeRules.durabilityPercent(Double.NaN), "NaN reads 0");
        assertEquals(100, TradeRules.durabilityPercent(0.995), "decision: 0,995 shows as 100 %, so no wear is announced");
        assertEquals(99, TradeRules.durabilityPercent(0.994));
        assertEquals(99, TradeRules.durabilityPercent(0.99));
        assertEquals(1, TradeRules.durabilityPercent(0.005), "0,005 rounds up to 1 %");
        assertEquals(0, TradeRules.durabilityPercent(0.004));
    }

    /** Damage 0 to 250 on a 250-point piece: the reference never goes up with the damage, never below 1 nor above the new piece's. */
    @Test
    void theReferenceFallsWithTheDamageButNeverBelowOneNorAboveTheNewPiece() {
        long newPiece = TradeRules.reference(ItemRating.EPIC, 1.0, 1, 0, 0, false, TradeRules.durabilityFactor(0, 250, B.minDurabilityFactor()), B);
        assertEquals(7, newPiece);
        long previous = newPiece;
        for (int damage = 0; damage <= 250; damage++) {
            double factor = TradeRules.durabilityFactor(damage, 250, B.minDurabilityFactor());
            long reference = TradeRules.reference(ItemRating.EPIC, 1.0, 1, 0, 0, false, factor, B);
            assertTrue(reference <= previous, "damage " + damage + ": " + reference + " after " + previous);
            assertTrue(reference >= 1, "damage " + damage);
            assertTrue(reference <= newPiece, "damage " + damage);
            previous = reference;
        }
        assertEquals(1, previous, "a broken epic piece is worth 7 x 0,25 = 1,75, rounded down");
        long sellPrevious = TradeRules.sellPrice(newPiece, 0, B);
        for (int damage = 0; damage <= 250; damage++) {
            long reference = TradeRules.reference(ItemRating.EPIC, 1.0, 1, 0, 0, false, TradeRules.durabilityFactor(damage, 250, B.minDurabilityFactor()), B);
            long price = TradeRules.sellPrice(reference, 0, B);
            assertTrue(price <= sellPrevious && price >= 1, "sale price at damage " + damage + ": " + price);
            sellPrevious = price;
        }
    }

    @Test
    void durabilityEntersTheReferenceBeforeTheSingleRounding() {
        assertEquals(3, TradeRules.reference(ItemRating.RARE, 1.0, 1, 0, 0, false, 1.0, B));
        assertEquals(1, TradeRules.reference(ItemRating.RARE, 1.0, 1, 0, 0, false, 0.625, B));
        assertEquals(1, TradeRules.reference(ItemRating.RARE, 1.0, 1, 0, 0, false, 0.25, B));
        assertEquals(150, TradeRules.reference(ItemRating.LEGENDARY, 1.0, 1, 0, 0, false, 1.0, B));
        assertEquals(93, TradeRules.reference(ItemRating.LEGENDARY, 1.0, 1, 0, 0, false, 0.625, B), "93,75 rounded down once");
        assertEquals(37, TradeRules.reference(ItemRating.LEGENDARY, 1.0, 1, 0, 0, false, 0.25, B), "37,5 rounded down once");
        assertEquals(1, TradeRules.reference(ItemRating.NORMAL, 0.5, 1, 0, 0, false, 0.0, B), "a broken piece still pays at least 1");
        assertEquals(TradeRules.reference(ItemRating.EPIC, 2.0, 7, 2, 3, false, B), TradeRules.reference(ItemRating.EPIC, 2.0, 7, 2, 3, false, 1.0, B));
    }

    @Test
    void referenceMatchesExactArithmetic() {
        for (ItemRating rating : ItemRating.values()) {
            for (Map.Entry<String, Double> material : B.materialMultiplier().entrySet()) {
                for (int enhancement = 0; enhancement <= 10; enhancement += 5) {
                    for (int level = 1; level <= 65; level += 8) {
                        for (int options = 0; options <= 3; options++) {
                            BigDecimal value = BigDecimal.valueOf(B.basePrice().get(rating))
                                    .multiply(BigDecimal.valueOf(material.getValue()))
                                    .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(B.levelBonus()).multiply(BigDecimal.valueOf(level - 1))))
                                    .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(B.optionBonus()).multiply(BigDecimal.valueOf(options))))
                                    .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(B.enhancementBonus()).multiply(BigDecimal.valueOf(enhancement))));
                            long expected = Math.max(1L, value.setScale(0, RoundingMode.FLOOR).longValueExact());
                            assertEquals(expected, TradeRules.reference(rating, material.getValue(), level, options, enhancement, false, B),
                                    rating + " " + material.getKey() + " level " + level + " options " + options + " +" + enhancement);
                        }
                    }
                }
            }
        }
    }

    @Test
    void messagesExistForEveryResult() {
        for (TradeService.Result result : TradeService.Result.values()) {
            Optional<net.minecraft.network.chat.Component> message = TradeService.message(new TradeService.Outcome(
                    result, result == TradeService.Result.REFUSED ? Optional.of(Refusal.LOANED) : Optional.empty(), 0L));
            assertEquals(result == TradeService.Result.SOLD, message.isEmpty(), result.toString());
        }
        assertEquals("message.mitofus.trade_stale", key(TradeService.Result.STALE));
        assertEquals("message.mitofus.trade_stale", key(TradeService.Result.EMPTY));
        assertEquals("message.mitofus.trade_stale", key(TradeService.Result.INVALID_SLOT));
        assertEquals("message.mitofus.trade_failed", key(TradeService.Result.FAILED));
        assertEquals("message.mitofus.trade_outside", key(TradeService.Result.OUTSIDE_MITOFUS));
        assertEquals("message.mitofus.trade_refused", key(TradeService.Result.REFUSED));
    }

    private static String key(TradeService.Result result) {
        var message = TradeService.message(new TradeService.Outcome(
                result, result == TradeService.Result.REFUSED ? Optional.of(Refusal.LOANED) : Optional.empty(), 0L)).orElseThrow();
        return ((net.minecraft.network.chat.contents.TranslatableContents) message.getContents()).getKey();
    }

    @Test
    void aDayIs24000Ticks() {
        assertEquals(0, TradeRules.day(23999));
        assertEquals(1, TradeRules.day(24000));
        assertEquals(5, TradeRules.day(5 * 24000L + 7));
    }

    @Test
    void refusalsComeInOrder() {
        assertEquals(Optional.of(Refusal.LOANED), TradeRules.refusal(5, false, false, true));
        assertEquals(Optional.of(Refusal.STACKED), TradeRules.refusal(2, false, false, false));
        assertEquals(Optional.of(Refusal.NO_VALUE), TradeRules.refusal(1, false, false, false));
        assertEquals(Optional.of(Refusal.UNRATED), TradeRules.refusal(1, true, false, false));
        assertTrue(TradeRules.refusal(1, true, true, false).isEmpty());
        assertEquals(Optional.of(Refusal.LOANED), TradeRules.refusal(5, true, true, true));
        assertEquals(Optional.of(Refusal.STACKED), TradeRules.refusal(2, true, false, false));
        assertEquals(Optional.of(Refusal.NO_VALUE), TradeRules.refusal(1, false, true, false));
        // The service cuts before with EMPTY: counts of 0 or less are not a refusal here.
        assertTrue(TradeRules.refusal(0, true, true, false).isEmpty());
        assertTrue(TradeRules.refusal(-1, true, true, false).isEmpty());
    }

    private static String batchKey(TradeService.Batch batch) {
        var message = TradeService.message(batch).orElseThrow();
        return ((net.minecraft.network.chat.contents.TranslatableContents) message.getContents()).getKey();
    }

    @Test
    void messagesForABatch() {
        assertEquals("message.mitofus.trade_outside", batchKey(new TradeService.Batch(0, 0, 2, 0, TradeService.Result.OUTSIDE_MITOFUS)));
        assertEquals("message.mitofus.trade_stale", batchKey(new TradeService.Batch(0, 0, 2, 0, TradeService.Result.INVALID_SLOT)));
        assertEquals("message.mitofus.trade_skipped", batchKey(new TradeService.Batch(2, 30, 1, 0, TradeService.Result.STALE)));
        assertEquals("message.mitofus.trade_failed", batchKey(new TradeService.Batch(0, 0, 0, 3, TradeService.Result.FAILED)));
        assertEquals("message.mitofus.trade_failed", batchKey(new TradeService.Batch(1, 5, 2, 1, TradeService.Result.FAILED)));
        assertEquals("message.mitofus.trade_failed", batchKey(new TradeService.Batch(1, 64, 0, 1, TradeService.Result.FAILED)));
        assertTrue(TradeService.message(new TradeService.Batch(3, 45, 0, 0, TradeService.Result.SOLD)).isEmpty());
    }

    private static int placeholders(String text) {
        return text.split("%s", -1).length - 1;
    }

    @Test
    void exactMultiplesRoundDownToTheExactValueWithAMinimumOfOne() {
        for (long r : new long[]{5, 10, 15, 20, 35, 100, 1000}) {
            long expected = Math.max(1L, BigDecimal.valueOf(r).multiply(BigDecimal.valueOf(B.sellShare())).setScale(0, RoundingMode.FLOOR).longValueExact());
            assertEquals(expected, TradeRules.sellPrice(r, 0, B), "reference " + r);
        }
        assertEquals(1, TradeRules.sellPrice(2, 0, B), "2 x 0,4 = 0,8 counts as 1");
    }

    @Test
    void sellPriceIronKeepsTheFractionOfGoldAndNeverGoesBelowOneIron() {
        assertEquals(280, TradeRules.sellPriceIron(7, 0, B), "7 x 0,4 = 2,8 gold = 280 iron");
        assertEquals(1, TradeRules.sellPriceIron(0, 0, B), "the minimum is 1 iron, not 1 gold");
        assertTrue(TradeRules.sellPriceIron(1, 36, B) >= 1);
    }

    @Test
    void ironPaymentsGiveWholeGoldStacksThenTheIronLeft() {
        assertArrayEquals(new long[]{1100}, TradeRules.ironPayments(1100, 64));
        assertArrayEquals(new long[]{311 - 11, 11}, TradeRules.ironPayments(311, 64));
        assertArrayEquals(new long[]{40}, TradeRules.ironPayments(40, 64));
        long total = 0;
        for (long part : TradeRules.ironPayments(64 * 100 * 2 + 5, 64)) {
            total += part;
        }
        assertEquals(64 * 100 * 2 + 5, total);
    }

    @Test
    void payableIronUsesTheGoldCap() {
        long cap = B.maxSellGold();
        assertTrue(TradeRules.payableIron(cap * 100 + 99, B));
        assertFalse(TradeRules.payableIron((cap + 1) * 100, B));
        assertFalse(TradeRules.payableIron(0, B));
    }

    @Test
    void hugeReferencesSaturateWithoutOverflowOrExceptions() {
        long huge = Long.MAX_VALUE;
        assertEquals(Long.MAX_VALUE, TradeRules.sellPriceIron(huge, 0, B));
        assertFalse(TradeRules.payableIron(Long.MAX_VALUE, B), "far above the sell cap");
        assertFalse(TradeRules.payable(Long.MAX_VALUE, B));
        assertTrue(TradeRules.sellPrice(huge, 0, B) > B.maxSellGold());
        assertEquals(Long.MAX_VALUE, TradeRules.reference(ItemRating.ANCESTRAL, 1e300, 1_000_000, 1_000_000, 1_000_000, false, B));
        assertEquals(1, TradeRules.reference(ItemRating.NORMAL, Double.NaN, 1, 0, 0, false, B), "a NaN material still prices at 1");
    }

    @Test
    void aFullBatchAtTheCapTotalsWithoutOverflow() {
        long capIron = B.maxSellGold() * Money.IRON_PER_GOLD;
        long[] prices = new long[TradeRules.MAX_BATCH];
        java.util.Arrays.fill(prices, capIron);
        assertEquals(capIron * TradeRules.MAX_BATCH, TradeRules.total(prices));
        assertTrue(TradeRules.total(prices) > 0);
    }

    @Test
    void twoSalesOfTheSameKeyInTheSameTickAddTwoPoints() {
        TradeKey key = new TradeKey(TradeCategory.SWORD, ItemRating.EPIC);
        TradeSaturation once = TradeSaturation.EMPTY.recordSale(key, 100, B);
        TradeSaturation twice = once.recordSale(key, 100, B);
        assertEquals(1, once.pressure(key, 100, B));
        assertEquals(2, twice.pressure(key, 100, B));
        assertEquals(0, TradeSaturation.EMPTY.pressure(key, 100, B), "the earlier value is not changed");
        // The second one is paid less than the first, as in a batch.
        assertTrue(TradeRules.sellPrice(40, 2, B) <= TradeRules.sellPrice(40, 1, B));
    }

    @Test
    void salesOfDifferentKeysInTheSameTickDoNotShareTheirPressure() {
        TradeKey sword = new TradeKey(TradeCategory.SWORD, ItemRating.EPIC);
        TradeKey axe = new TradeKey(TradeCategory.AXE, ItemRating.EPIC);
        TradeSaturation both = TradeSaturation.EMPTY.recordSale(sword, 7, B).recordSale(axe, 7, B);
        assertEquals(1, both.pressure(sword, 7, B));
        assertEquals(1, both.pressure(axe, 7, B));
    }
}
