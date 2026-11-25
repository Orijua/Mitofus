package com.orijua.mitofus.feature.form;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Pure rules of forms: their level by use, their skills and their type bonus. Numbers come from the form's JSON. */
public final class FormRules {
    public static final int MAX_LEVEL = 100;
    /** A step is never larger than this, so a steep curve cannot overflow the uses counters. */
    public static final int MAX_STEP = 10_000_000;
    /** The form levels that give the attack, the passive and the ultimate. */
    public static final List<Integer> SKILL_LEVELS = List.of(1, 30, 60);
    public static final String SKILL_LEVELS_TEXT = "1, 30 and 60";

    private FormRules() {
    }

    /** Uses needed to go from {@code level} to the next one. */
    public static int usesToAdvance(FormDefinition.Curve curve, int level) {
        double uses = Math.round(curve.firstStep() * Math.pow(curve.growth(), Math.max(1, level) - 1));
        return (int) Math.min(MAX_STEP, Math.max(1, uses));
    }

    /** Total uses at which a form reaches {@code level} (level 1 needs none). */
    public static int usesToReach(FormDefinition.Curve curve, int level) {
        long total = 0;
        for (int l = 1; l < Math.min(level, MAX_LEVEL); l++) {
            total += usesToAdvance(curve, l);
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /** The level (1 to {@link #MAX_LEVEL}) of a form with {@code xp} uses. */
    public static int level(FormDefinition.Curve curve, int xp) {
        int level = 1;
        long remaining = Math.max(0, xp);
        while (level < MAX_LEVEL && remaining >= usesToAdvance(curve, level)) {
            remaining -= usesToAdvance(curve, level);
            level++;
        }
        return level;
    }

    /** What one use is worth for a form at {@code level}: the boost below {@code boost_below}, 1 otherwise. */
    public static double useValue(FormDefinition.Curve curve, int level) {
        return level < curve.boostBelow() ? curve.boost() : 1.0;
    }

    /** The damage bonus of the form's type at {@code level}: 0 at none, {@code max_type_bonus} at level 100, in a straight line. */
    public static double typeBonus(FormDefinition form, int level) {
        return form.maxTypeBonus() * Math.min(Math.max(level, 0), MAX_LEVEL) / MAX_LEVEL;
    }

    /** The form's skills (and passive) the player has at {@code level}: attack at 1, passive at 30, ultimate at 60. */
    public static List<ResourceLocation> unlockedSkills(FormDefinition form, int level) {
        List<ResourceLocation> result = new ArrayList<>();
        for (int i = 0; i < form.skills().size() && i < SKILL_LEVELS.size(); i++) {
            if (level >= SKILL_LEVELS.get(i)) {
                result.add(form.skills().get(i));
            }
        }
        return result;
    }

    /**
     * Whether a form is unlocked: the class level reached {@code unlock_level} and, for a grade-quest form that the class
     * owns, the quest was finished ({@code granted}). A form a fusion inherits needs only the level.
     */
    public static boolean unlocked(FormDefinition form, int classLevel, boolean granted, boolean inherited) {
        if (classLevel < form.unlockLevel()) {
            return false;
        }
        return inherited || form.source() == FormSource.LEVEL || granted;
    }

    /** The skills to learn (attack and ultimate, not the passive) at {@code level}. */
    public static List<ResourceLocation> unlockedActiveSkills(FormDefinition form, int level) {
        List<ResourceLocation> result = new ArrayList<>();
        for (int i = 0; i < form.skills().size() && i < SKILL_LEVELS.size(); i++) {
            if (i != FormDefinition.PASSIVE_INDEX && level >= SKILL_LEVELS.get(i)) {
                result.add(form.skills().get(i));
            }
        }
        return result;
    }

    /** The passive of the form at {@code level}, once it reaches the passive's level. */
    public static java.util.Optional<ResourceLocation> unlockedPassive(FormDefinition form, int level) {
        return level >= SKILL_LEVELS.get(FormDefinition.PASSIVE_INDEX) ? form.passive() : java.util.Optional.empty();
    }
}
