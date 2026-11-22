package com.orijua.mitofus.feature.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeaponWearRulesTest {
    @Test
    void requiredItemWearsEvenFromALearnedSlot() {
        assertEquals(2, WeaponWearRules.wear(true, false, 2));
    }

    @Test
    void weaponSlotWearsEvenWithoutRequiredItem() {
        assertEquals(4, WeaponWearRules.wear(false, true, 4));
    }

    @Test
    void skillWithoutItemFromALearnedSlotNeverWears() {
        assertEquals(0, WeaponWearRules.wear(false, false, 5));
    }

    @Test
    void zeroConfiguredWearsNothingEvenInWeaponSlot() {
        assertEquals(0, WeaponWearRules.wear(false, true, 0));
        assertEquals(0, WeaponWearRules.wear(true, true, 0));
    }

    @Test
    void creativeNeverWears() {
        for (boolean item : new boolean[]{false, true}) {
            for (boolean slot : new boolean[]{false, true}) {
                assertEquals(0, WeaponWearRules.wear(item, slot, 3, true));
            }
        }
    }

    @Test
    void creativeNeverWearsEvenWithoutWeapon() {
        assertEquals(0, WeaponWearRules.wear(false, false, 0, true));
    }

    @Test
    void survivalWithoutWeaponNeverWears() {
        assertEquals(0, WeaponWearRules.wear(false, false, 3, false));
    }

    @Test
    void survivalKeepsTheConfiguredWear() {
        assertEquals(3, WeaponWearRules.wear(true, false, 3, false));
    }

    @Test
    void negativeConfiguredWearsNothing() {
        assertEquals(0, WeaponWearRules.wear(true, true, -3));
    }

    @Test
    void defaultWearIsOne() {
        assertEquals(1, WeaponWearRules.DEFAULT_WEAR);
    }
}
