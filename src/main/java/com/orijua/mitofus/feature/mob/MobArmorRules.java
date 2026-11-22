package com.orijua.mitofus.feature.mob;

/** When a mob's armor is shown to the player: only inside Mitofus; the real world is vanilla and shows nothing. */
public final class MobArmorRules {
    private MobArmorRules() {
    }

    public static boolean showArmor(boolean inMitofus, int armor) {
        return inMitofus && armor > 0;
    }
}
