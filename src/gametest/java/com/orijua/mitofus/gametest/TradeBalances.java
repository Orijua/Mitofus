package com.orijua.mitofus.gametest;

import com.orijua.mitofus.core.config.TradeBalance;

/** Copies of {@link TradeBalance#DEFAULT} with one number changed; a new field of the record only breaks this class and its unit-test twin. */
final class TradeBalances {
    private static final TradeBalance D = TradeBalance.DEFAULT;

    private TradeBalances() {
    }

    private static TradeBalance copy(double other, double step, double floor, int recovery, int cap, long maxSell) {
        return new TradeBalance(D.basePrice(), D.materialMultiplier(), other, D.enhancementBonus(), D.levelBonus(), D.optionBonus(), D.impossibleMargin(), D.impossibleFactor(), D.sellShare(),
                step, floor, recovery, cap, maxSell, D.minDurabilityFactor(), D.itemMultiplier());
    }

    static TradeBalance withOtherMaterial(double value) {
        return copy(value, D.saturationStep(), D.saturationFloor(), D.recoveryPerDay(), D.pressureCap(), D.maxSellGold());
    }


    /** The defaults with the multipliers of single objects replaced. */
    static TradeBalance withItemMultipliers(java.util.Map<net.minecraft.resources.ResourceLocation, Double> multipliers) {
        return new TradeBalance(D.basePrice(), D.materialMultiplier(), D.otherMaterial(), D.enhancementBonus(), D.levelBonus(), D.optionBonus(), D.impossibleMargin(), D.impossibleFactor(), D.sellShare(),
                D.saturationStep(), D.saturationFloor(), D.recoveryPerDay(), D.pressureCap(), D.maxSellGold(),
                D.minDurabilityFactor(), multipliers);
    }

    static TradeBalance withMaxSellGold(long value) {
        return copy(D.otherMaterial(), D.saturationStep(), D.saturationFloor(), D.recoveryPerDay(), D.pressureCap(), value);
    }

    /**
     * The price scale of before the gold coin (base prices x3, cap 10.000): the sale mechanics tests (saturation, stale prices, piles,
     * batches) read whole-gold prices that fall one by one with the pressure, which the small gold prices hide behind the rounding.
     * The real table is checked by the unit tests and by TradeValueTests.
     */
    static TradeBalance preGold() {
        return preGoldWith(D.otherMaterial(), 10_000L);
    }

    static TradeBalance preGoldWithMaxSell(long value) {
        return preGoldWith(D.otherMaterial(), value);
    }

    static TradeBalance preGoldWithOtherMaterial(double value) {
        return preGoldWith(value, 10_000L);
    }

    private static TradeBalance preGoldWith(double other, long maxSell) {
        return new TradeBalance(java.util.Map.of(com.orijua.mitofus.feature.equipment.ItemRating.NORMAL, 3, com.orijua.mitofus.feature.equipment.ItemRating.RARE, 9,
                com.orijua.mitofus.feature.equipment.ItemRating.EPIC, 21, com.orijua.mitofus.feature.equipment.ItemRating.UNIQUE, 90,
                com.orijua.mitofus.feature.equipment.ItemRating.LEGENDARY, 450, com.orijua.mitofus.feature.equipment.ItemRating.ANCESTRAL, 1800),
                D.materialMultiplier(), other, D.enhancementBonus(), D.levelBonus(), D.optionBonus(), D.impossibleMargin(), D.impossibleFactor(), D.sellShare(),
                D.saturationStep(), D.saturationFloor(), D.recoveryPerDay(), D.pressureCap(), maxSell, D.minDurabilityFactor(), D.itemMultiplier());
    }
}
