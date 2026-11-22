package com.orijua.mitofus.feature.mob;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobArmorRulesTest {
    @Test
    void armorIsShownInMitofus() {
        assertTrue(MobArmorRules.showArmor(true, 8));
    }

    @Test
    void theRealWorldIsVanillaAndShowsNoArmor() {
        assertFalse(MobArmorRules.showArmor(false, 8));
    }

    @Test
    void noArmorShowsNothing() {
        assertFalse(MobArmorRules.showArmor(true, 0));
    }
}
