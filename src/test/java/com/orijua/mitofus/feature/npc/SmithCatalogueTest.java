package com.orijua.mitofus.feature.npc;

import com.orijua.mitofus.core.config.ApprenticeBalance;
import com.orijua.mitofus.core.config.SmithShopBalance;
import com.orijua.mitofus.feature.economy.Money;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every price of Smith's shop in gold: the enhancement materials at half the reference price, the rest converted from the
 * old emerald prices. Fixing the version and stocks is on purpose: changing them is a restock that needs a decision.
 */
class SmithCatalogueTest {
    @Test
    void everyPriceIsTheOldPriceInGold() {
        assertEquals(4, BlacksmithNpc.SHOP_VERSION);
        assertEquals(4, SmithShopBalance.DEFAULT.eyeDailyStock());
        assertEquals(16, SmithShopBalance.DEFAULT.stoneStock());
        assertEquals(3, SmithShopBalance.DEFAULT.blessedStock());
        assertEquals(90, SmithShopBalance.DEFAULT.stonePrice()); // 180 / 2
        assertEquals(1_250, SmithShopBalance.DEFAULT.blessedStonePrice()); // 2.500 / 2
        assertEquals(1_250, SmithShopBalance.DEFAULT.blessedScrollPrice());
        assertTrue(SmithShopBalance.DEFAULT.blessedStonePrice() >= 10 * SmithShopBalance.DEFAULT.stonePrice()
                && SmithShopBalance.DEFAULT.blessedStonePrice() <= 14 * SmithShopBalance.DEFAULT.stonePrice(), "a blessed stone costs 10 to 14 times a normal one");
        assertEquals(Money.goldFromEmeralds(12), SmithShopBalance.DEFAULT.respecScrollPrice());
        assertEquals(Money.goldFromEmeralds(3), SmithShopBalance.DEFAULT.mithrilSaleGold());
        assertEquals(Money.goldFromEmeralds(16), SmithShopBalance.DEFAULT.skillBookPrice());
        assertEquals(Money.goldFromEmeralds(32), SmithShopBalance.DEFAULT.eyePrice());
        assertEquals(Money.goldFromEmeralds(64), ApprenticeBalance.DEFAULT.contractPrice());
        assertEquals(Money.goldFromEmeralds(3), ApprenticeBalance.DEFAULT.dailyWage());
    }
}
