package com.orijua.mitofus.feature.npc;

import com.orijua.mitofus.core.config.ApprenticeBalance;
import com.orijua.mitofus.core.config.SmithShopBalance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every price of Smith's shop in gold: the enhancement materials at half the reference price, the rest in whole gold.
 * Fixing the version and stocks is on purpose: changing them is a restock that needs a decision.
 */
class SmithCatalogueTest {
    @Test
    void theDefaultsAreTheDesignedNumbers() {
        assertEquals(4, BlacksmithNpc.SHOP_VERSION);
        assertEquals(4, SmithShopBalance.DEFAULT.eyeDailyStock());
        assertEquals(16, SmithShopBalance.DEFAULT.stoneStock());
        assertEquals(3, SmithShopBalance.DEFAULT.blessedStock());
        assertEquals(90, SmithShopBalance.DEFAULT.stonePrice()); // 180 / 2
        assertEquals(1_250, SmithShopBalance.DEFAULT.blessedStonePrice()); // 2.500 / 2
        assertEquals(1_250, SmithShopBalance.DEFAULT.blessedScrollPrice());
        assertTrue(SmithShopBalance.DEFAULT.blessedStonePrice() >= 10 * SmithShopBalance.DEFAULT.stonePrice()
                && SmithShopBalance.DEFAULT.blessedStonePrice() <= 14 * SmithShopBalance.DEFAULT.stonePrice(), "a blessed stone costs 10 to 14 times a normal one");
        assertEquals(4L, SmithShopBalance.DEFAULT.respecScrollPrice());
        assertEquals(1L, SmithShopBalance.DEFAULT.mithrilSaleGold());
        assertEquals(5L, SmithShopBalance.DEFAULT.skillBookPrice());
        assertEquals(10L, SmithShopBalance.DEFAULT.eyePrice());
        assertEquals(21L, ApprenticeBalance.DEFAULT.contractPrice());
        assertEquals(1L, ApprenticeBalance.DEFAULT.dailyWage());
    }
}
