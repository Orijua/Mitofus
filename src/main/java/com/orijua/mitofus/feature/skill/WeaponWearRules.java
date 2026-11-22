package com.orijua.mitofus.feature.skill;

/** How much durability a skill cast costs the held weapon. Pure rules, no Minecraft types. */
public final class WeaponWearRules {
    /** Wear of a skill that doesn't set {@code weapon_wear}. */
    public static final int DEFAULT_WEAR = 1;

    private WeaponWearRules() {
    }

    /** Wear applies only to skills that need a weapon or come from the weapon slot. */
    public static int wear(boolean requiresItem, boolean fromWeaponSlot, int configured) {
        return requiresItem || fromWeaponSlot ? Math.max(0, configured) : 0;
    }

    /** Same as above, but creative players never wear their weapon. */
    public static int wear(boolean requiresItem, boolean fromWeaponSlot, int configured, boolean creative) {
        return creative ? 0 : wear(requiresItem, fromWeaponSlot, configured);
    }
}
