package com.orijua.mitofus.feature.skill;

/** How a skill's cost is shown to the player. Pure rules, no Minecraft types. */
public final class SkillCostRules {
    public static final int TICKS_PER_SECOND = 20;

    private SkillCostRules() {
    }

    /** Mana as a whole number (truncated). */
    public static int manaShown(float manaCost) {
        return (int) manaCost;
    }

    /** Cooldown in whole seconds (truncated). */
    public static int cooldownSeconds(int ticks) {
        return ticks / TICKS_PER_SECOND;
    }

    /** The wear is only shown when the cast actually wears the weapon. */
    public static boolean showsWear(int wear) {
        return wear > 0;
    }
}
