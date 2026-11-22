package com.orijua.mitofus.feature.npc.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MitofusShopTest {
    @Test
    void replacesVanillaMerchantInMitofus() {
        assertTrue(MitofusShop.shouldReplace(true, true, false));
    }

    @Test
    void keepsVanillaOutsideMitofus() {
        assertFalse(MitofusShop.shouldReplace(false, true, false));
    }

    @Test
    void ignoresOtherScreens() {
        assertFalse(MitofusShop.shouldReplace(true, false, false));
    }

    @Test
    void doesNotReplaceTheMitofusScreenAgain() {
        assertFalse(MitofusShop.shouldReplace(true, true, true));
    }
}
