package com.orijua.mitofus.feature.economy;

import com.orijua.mitofus.core.config.TerminalBalance;
import com.orijua.mitofus.feature.equipment.ItemRating;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TerminalBalanceTest {
    @Test
    void defaultsAreTheApprovedNumbers() {
        TerminalBalance b = TerminalBalance.DEFAULT;
        assertEquals(4.0, b.buyMultiplier());
        assertEquals(6, b.offerPerRank());
        assertEquals(ItemRating.RARE, b.offerMaxRating());
        assertEquals(32, b.offerItems().size());
        assertEquals(32, new java.util.HashSet<>(b.offerItems()).size(), "no repeats");
        for (ResourceLocation id : b.offerItems()) {
            assertFalse(id.getPath().contains("diamond") || id.getPath().contains("netherite"), id.toString());
        }
    }

    @Test
    void badNumbersAreRejected() {
        TerminalBalance d = TerminalBalance.DEFAULT;
        assertEquals(1.0, new TerminalBalance(1.0, 6, ItemRating.RARE, d.offerItems()).buyMultiplier());
        assertEquals(OfferBought.MAX_POSITIONS, TerminalBalance.MAX_OFFER_PER_RANK * ItemRating.values().length);
        for (double bad : new double[]{0.5, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new TerminalBalance(bad, 6, ItemRating.RARE, d.offerItems()));
        }
        assertThrows(IllegalArgumentException.class, () -> d.withOffer(-1, ItemRating.RARE, d.offerItems()));
        assertThrows(IllegalArgumentException.class, () -> d.withOffer(13, ItemRating.RARE, d.offerItems()));
        assertThrows(NullPointerException.class, () -> d.withOffer(6, null, d.offerItems()));
        assertEquals(0, d.withOffer(0, ItemRating.RARE, List.of()).offerItems().size());
        assertEquals(12, d.withOffer(12, ItemRating.RARE, d.offerItems()).offerPerRank());
    }
}
