package com.orijua.mitofus.feature.economy;

import com.orijua.mitofus.core.config.TerminalBalance;
import com.orijua.mitofus.core.config.TradeBalance;
import com.orijua.mitofus.feature.equipment.ItemRating;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeBalanceTest {
    @Test
    void defaultsAreTheApprovedNumbers() {
        TradeBalance b = TradeBalance.DEFAULT;
        assertEquals(1, b.basePrice().get(ItemRating.NORMAL));
        assertEquals(3, b.basePrice().get(ItemRating.RARE));
        assertEquals(7, b.basePrice().get(ItemRating.EPIC));
        assertEquals(30, b.basePrice().get(ItemRating.UNIQUE));
        assertEquals(150, b.basePrice().get(ItemRating.LEGENDARY));
        assertEquals(600, b.basePrice().get(ItemRating.ANCESTRAL));
        assertEquals(Map.of("wood", 0.5, "gold", 0.5, "leather", 0.5, "stone", 1.0, "iron", 1.0, "chainmail", 1.0,
                "diamond", 2.0, "netherite", 3.0), b.materialMultiplier());
        assertEquals(1.0, b.otherMaterial());
        assertEquals(0.10, b.enhancementBonus());
        assertEquals(0.02, b.levelBonus());
        assertEquals(0.10, b.optionBonus());
        assertEquals(50, b.impossibleMargin());
        assertEquals(0.25, b.impossibleFactor());
        assertEquals(0.40, b.sellShare());
        assertEquals(0.95, b.saturationStep());
        assertEquals(0.15, b.saturationFloor());
        assertEquals(2, b.recoveryPerDay());
        assertEquals(37, b.pressureCap());
        assertEquals(3_333L, b.maxSellGold());
        assertEquals(0.25, b.minDurabilityFactor());
    }

    @Test
    void everyBasePriceTimesThreeIsTheOldPriceInEmeralds() {
        TradeBalance b = TradeBalance.DEFAULT;
        assertEquals(3, b.basePrice().get(ItemRating.NORMAL) * Money.EMERALDS_PER_GOLD);
        assertEquals(9, b.basePrice().get(ItemRating.RARE) * Money.EMERALDS_PER_GOLD);
        assertEquals(21, b.basePrice().get(ItemRating.EPIC) * Money.EMERALDS_PER_GOLD);
        assertEquals(90, b.basePrice().get(ItemRating.UNIQUE) * Money.EMERALDS_PER_GOLD);
        assertEquals(450, b.basePrice().get(ItemRating.LEGENDARY) * Money.EMERALDS_PER_GOLD);
        assertEquals(1800, b.basePrice().get(ItemRating.ANCESTRAL) * Money.EMERALDS_PER_GOLD);
        assertEquals(10_000L, TradeBalance.DEFAULT.maxSellGold() * Money.EMERALDS_PER_GOLD + 1, "3.333 gold is the old 10.000 cap, rounded down");
    }

    private static TradeBalance withMinDurability(double value) {
        return TradeBalancesForTests.withMinDurabilityFactor(value);
    }

    private static TradeBalance withItemMultiplier(double value) {
        return TradeBalancesForTests.withItemMultipliers(Map.of(ResourceLocation.withDefaultNamespace("iron_sword"), value));
    }

    private static TradeBalance with(double levelBonus, double optionBonus, int margin, double factor) {
        TradeBalance d = TradeBalance.DEFAULT;
        return new TradeBalance(d.basePrice(), d.materialMultiplier(), d.otherMaterial(), d.enhancementBonus(), levelBonus, optionBonus,
                margin, factor, d.sellShare(), d.saturationStep(), d.saturationFloor(), d.recoveryPerDay(), d.pressureCap(),
                d.maxSellGold(), d.minDurabilityFactor(), d.itemMultiplier());
    }

    @Test
    void theLevelAndImpossibleNumbersAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> with(-0.01, 0.1, 50, 0.25));
        assertThrows(IllegalArgumentException.class, () -> with(0.02, -0.01, 50, 0.25));
        assertThrows(IllegalArgumentException.class, () -> with(Double.NaN, 0.1, 50, 0.25));
        assertThrows(IllegalArgumentException.class, () -> with(0.02, 0.1, -1, 0.25));
        assertThrows(IllegalArgumentException.class, () -> with(0.02, 0.1, 50, -0.1));
        assertThrows(IllegalArgumentException.class, () -> with(0.02, 0.1, 50, 1.1));
        assertEquals(0, with(0.0, 0.0, 0, 0.0).impossibleMargin());
        assertEquals(1.0, with(0.02, 0.1, 50, 1.0).impossibleFactor());
    }

    @Test
    void theMinimumDurabilityFactorMustBeInZeroToOne() {
        assertThrows(IllegalArgumentException.class, () -> withMinDurability(-0.01));
        assertThrows(IllegalArgumentException.class, () -> withMinDurability(1.01));
        assertThrows(IllegalArgumentException.class, () -> withMinDurability(Double.NaN));
        assertEquals(0.0, withMinDurability(0.0).minDurabilityFactor());
        assertEquals(1.0, withMinDurability(1.0).minDurabilityFactor());
    }

    @Test
    void theDefaultObjectMultipliersAreTheThreeUniqueObjects() {
        Map<ResourceLocation, Double> m = TradeBalance.DEFAULT.itemMultiplier();
        assertEquals(3, m.size());
        assertEquals(2.0, m.get(ResourceLocation.fromNamespaceAndPath("mitofus", "wolfram_sword")));
        assertEquals(2.0, m.get(ResourceLocation.fromNamespaceAndPath("mitofus", "wolfram_smithing_hammer")));
        assertEquals(3.0, m.get(ResourceLocation.fromNamespaceAndPath("mitofus", "mithril_smithing_hammer")));
    }

    @Test
    void anObjectMultiplierMustBeAFiniteNumberFromZeroToOneHundred() {
        assertThrows(IllegalArgumentException.class, () -> withItemMultiplier(-0.5));
        assertThrows(IllegalArgumentException.class, () -> withItemMultiplier(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> withItemMultiplier(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> withItemMultiplier(100.5));
        assertEquals(0.0, withItemMultiplier(0.0).itemMultiplier().values().iterator().next());
        assertEquals(100.0, withItemMultiplier(100.0).itemMultiplier().values().iterator().next());
    }

    @Test
    void theObjectMultipliersNeverShareAnItemWithTheTerminalOffer() {
        assertFalse(TerminalBalance.DEFAULT.offerItems().isEmpty(), "the offer has items, so the check below checks something");
        for (ResourceLocation id : TradeBalance.DEFAULT.itemMultiplier().keySet()) {
            assertFalse(TerminalBalance.DEFAULT.offerItems().contains(id), id + " is in the daily offer");
        }
    }

    @Test
    void currentIsTheDefaultsBeforeTheConfigLoads() {
        assertEquals(TradeBalance.DEFAULT, TradeBalance.current());
    }

    @Test
    void anOverrideAppliesOnlyWhileTheActionRuns() {
        TradeBalance changed = TradeBalancesForTests.withSellShare(0.5);
        TradeBalance[] seen = new TradeBalance[1];
        TradeBalance.withOverride(changed, () -> seen[0] = TradeBalance.current());
        assertEquals(changed, seen[0]);
        assertEquals(TradeBalance.DEFAULT, TradeBalance.current());
    }

    @Test
    void anOverrideIsRestoredEvenWhenTheActionThrows() {
        TradeBalance changed = TradeBalancesForTests.withSellShare(0.5);
        assertThrows(IllegalStateException.class, () -> TradeBalance.withOverride(changed, () -> {
            assertEquals(changed, TradeBalance.current());
            throw new IllegalStateException("boom");
        }));
        assertEquals(TradeBalance.DEFAULT, TradeBalance.current(), "the override did not leak");
    }

    @Test
    void nestedOverridesRestoreTheOuterOne() {
        TradeBalance outer = TradeBalancesForTests.withSellShare(0.5);
        TradeBalance inner = TradeBalancesForTests.withSellShare(0.6);
        TradeBalance.withOverride(outer, () -> {
            TradeBalance.withOverride(inner, () -> assertEquals(inner, TradeBalance.current()));
            assertEquals(outer, TradeBalance.current());
        });
        assertEquals(TradeBalance.DEFAULT, TradeBalance.current());
    }

    @Test
    void theMapsAreImmutableAndCopiedFromTheOnesGiven() {
        TradeBalance defaults = TradeBalance.DEFAULT;
        ResourceLocation sword = ResourceLocation.withDefaultNamespace("iron_sword");
        assertThrows(UnsupportedOperationException.class, () -> defaults.itemMultiplier().put(sword, 2.0));
        assertThrows(UnsupportedOperationException.class, () -> defaults.itemMultiplier().clear());
        assertThrows(UnsupportedOperationException.class, () -> defaults.materialMultiplier().put("wood", 9.0));
        assertThrows(UnsupportedOperationException.class, () -> defaults.basePrice().put(ItemRating.NORMAL, 9));

        Map<ResourceLocation, Double> source = new java.util.HashMap<>();
        source.put(sword, 2.0);
        TradeBalance balance = TradeBalancesForTests.withItemMultipliers(source);
        source.put(ResourceLocation.withDefaultNamespace("bow"), 3.0);
        source.put(sword, 50.0);
        assertEquals(Map.of(sword, 2.0), balance.itemMultiplier(), "changing the source map afterwards does not change the balance");
    }

    /**
     * The defaults above are numbers someone approved. These are the relations between them that must hold if one is tuned, so
     * a change fails here with a reason instead of only "expected 37".
     */
    @Test
    void theDefaultNumbersKeepTheirRelations() {
        TradeBalance b = TradeBalance.DEFAULT;
        // The pressure cap is the first one where the curve reaches the floor.
        assertTrue(Math.pow(b.saturationStep(), b.pressureCap()) <= b.saturationFloor());
        assertTrue(Math.pow(b.saturationStep(), b.pressureCap() - 1) > b.saturationFloor());
        // Every rating is worth more than the one below.
        long previous = 0;
        for (ItemRating rating : ItemRating.values()) {
            long price = b.basePrice().get(rating);
            assertTrue(price > previous, rating + " is worth more than the rating below");
            previous = price;
        }
        assertTrue(b.sellShare() > 0 && b.sellShare() < 1, "a piece sells for less than its reference");
        assertTrue(b.saturationFloor() > 0 && b.saturationFloor() < 1);
    }
}
