package com.orijua.mitofus.feature.progression;

import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.config.PassiveXpBalance;

import java.util.List;

/**
 * Pure progression formulas (project design numbers).
 * No Minecraft state here; every tunable number comes from {@link Balance}.
 */
public final class ProgressionRules {
    private ProgressionRules() {
    }

    /**
     * What a character has at level 1 before spending points: 6 STR, 7 RES (here
     * Aguante), 4 AGI, 5 INT. Dexterity has no fixed figure, so it starts at 0 (own design).
     */
    public static int baseStat(StatType stat) {
        return switch (stat) {
            case STRENGTH -> 6;
            case STAMINA -> 7;
            case AGILITY -> 4;
            case INTELLIGENCE -> 5;
            case DEXTERITY -> 0;
        };
    }

    /** Players under this level lose no XP when they die. */
    public static final int DEATH_XP_EXEMPT_BELOW_LEVEL = 10;

    /** The points per level the per-point stat values are made for: a level gives the same power with any setting. */
    public static final int REFERENCE_POINTS_PER_LEVEL = 10;

    /**
     * How much a stat point is worth with this {@code statPointsPerLevel} setting: an old config that still gives 5
     * points per level makes each point worth double, so characters keep the power per level they were made for.
     */
    public static double pointScale(int statPointsPerLevel) {
        return (double) REFERENCE_POINTS_PER_LEVEL / Math.max(1, statPointsPerLevel);
    }

    public static int xpToNextLevel(Balance balance, int level) {
        return balance.xpBase() + balance.xpPerLevel() * level;
    }

    /** What a mob was like when it died, as far as XP is concerned. */
    public record Victim(float maxHealth, double attackDamage, int armor, boolean fromSpawner, boolean elite) {
    }

    /** XP for a kill: tougher and harder-hitting mobs (so higher levels) give more, elites even more; spawner mobs give less (anti-farming). */
    public static int killXp(Balance balance, Victim victim) {
        double strength = victim.maxHealth()
                + victim.attackDamage() * balance.killXpPerAttackDamage()
                + victim.armor() * balance.killXpPerArmor();
        double xp = strength * balance.killXpMultiplier() * (victim.fromSpawner() ? balance.spawnerXpMultiplier() : 1.0)
                * (victim.elite() ? balance.eliteXpMultiplier() : 1.0);
        return Math.max(1, (int) Math.round(xp));
    }

    /**
     * The XP of killing a peaceful mob, given the XP {@code xp} a normal kill would give: all of it up to
     * {@code fullXpUntilLevel}, 1 from {@code oneXpFromLevel}, in a straight line in between (never below 1). With the
     * two levels equal or crossed the drop is one step at {@code oneXpFromLevel}. An {@code xp} that is not positive stays.
     */
    public static int passiveKillXp(PassiveXpBalance balance, int xp, int playerLevel) {
        if (xp <= 0) {
            return xp;
        }
        if (playerLevel >= balance.oneXpFromLevel()) {
            return 1;
        }
        if (playerLevel <= balance.fullXpUntilLevel()) {
            return xp;
        }
        double fraction = (balance.oneXpFromLevel() - playerLevel)
                / (double) (balance.oneXpFromLevel() - balance.fullXpUntilLevel());
        return Math.max(1, (int) Math.round(1 + (xp - 1) * fraction));
    }

    /** Level, XP in the level, XP the level needs and XP still missing (never negative). */
    public record XpProgress(int level, int xp, int needed, int remaining) {
    }

    /** The one place that works out how far a player is from the next level (HUD, status tab, command). */
    public static XpProgress progress(Balance balance, PlayerStats stats) {
        int needed = xpToNextLevel(balance, stats.level());
        return new XpProgress(stats.level(), stats.xp(), needed, Math.max(0, needed - stats.xp()));
    }

    public static int smithingLevel(int smithingXp) {
        return (int) Math.floor(Math.sqrt(Math.max(0, smithingXp) / 10.0));
    }

    /**
     * {@code amount} raised by a {@code bonus} fraction (0.1 = +10 %), rounded, never above {@link Integer#MAX_VALUE};
     * a bonus never turns a gain into nothing, and one that is not a positive number changes nothing.
     */
    public static int boostedSmithingXp(int amount, double bonus) {
        if (amount <= 0 || !(bonus > 0)) {
            return amount;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.round(amount * (1 + bonus)));
    }

    /**
     * {@code amount} of level XP raised by {@code percent} while rested, rounded, never above {@link Integer#MAX_VALUE};
     * nothing changes for a gain that is not positive or a percent that is not.
     */
    public static int restedXp(int amount, int percent) {
        if (amount <= 0 || percent <= 0) {
            return amount;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.round(amount * (1 + percent / 100.0)));
    }

    /** The smithing experience at which {@code level} starts (the inverse of {@link #smithingLevel}). */
    public static int smithingXpFor(int level) {
        return Math.max(0, level) * Math.max(0, level) * 10;
    }

    public static float maxMana(Balance balance, int intelligence) {
        return balance.baseMana() + balance.manaPerIntelligence() * intelligence;
    }

    /** Mana regenerated per second. */
    public static float manaRegenPerSecond(Balance balance, int intelligence) {
        return balance.baseManaRegen() + balance.manaRegenPerIntelligence() * intelligence;
    }

    public record XpResult(PlayerStats stats, int levelsGained) {
    }

    /** Adds XP and resolves any number of level ups, each granting the configured stat points. */
    public static XpResult addXp(Balance balance, PlayerStats stats, int amount) {
        int level = stats.level();
        int xp = stats.xp() + Math.max(0, amount);
        int points = stats.freePoints();
        int gained = 0;
        while (xp >= xpToNextLevel(balance, level)) {
            xp -= xpToNextLevel(balance, level);
            level++;
            points += balance.statPointsPerLevel();
            gained++;
        }
        return new XpResult(stats.withProgress(level, xp, points), gained);
    }

    /**
     * Loses {@code levels} levels (never going below level 1): the XP of the level is lost too, and the free points
     * that those levels gave go with them as far as the player still has them; points already spent stay.
     */
    public static PlayerStats loseLevels(Balance balance, PlayerStats stats, int levels) {
        int lost = Math.min(Math.max(0, levels), stats.level() - 1);
        if (lost == 0) {
            return stats;
        }
        int points = Math.max(0, stats.freePoints() - lost * balance.statPointsPerLevel());
        return stats.withProgress(stats.level() - lost, 0, points);
    }

    /** Final value of one stat: every ADD summed, then raised by the sum of every MULTIPLY, rounded down, never below 0. */
    public static int total(StatType stat, List<StatModifier> modifiers) {
        double flat = 0;
        double percent = 0;
        for (StatModifier modifier : modifiers) {
            if (modifier.stat() != stat) {
                continue;
            }
            switch (modifier.operation()) {
                case ADD -> flat += modifier.value();
                case MULTIPLY -> percent += modifier.value();
            }
        }
        // The epsilon keeps 10 * 1.1 = 11.000000000000002 or 10.999999999999998 from flooring to 10.
        return Math.max(0, (int) Math.floor(flat * (1 + percent) + 1e-9));
    }

    /**
     * {@code stats} with each base stat replaced by its {@link #total}. The modifiers must include the
     * allocated points ({@link StatModifier#allocated}); level, XP and the rest are kept from {@code stats}.
     */
    public static PlayerStats effective(PlayerStats stats, List<StatModifier> modifiers) {
        PlayerStats result = stats;
        for (StatType stat : StatType.values()) {
            result = result.with(stat, total(stat, modifiers));
        }
        return result;
    }

    /**
     * Death penalty: loses part of the XP gathered towards the next level, never a whole level, and nothing
     * below {@link #DEATH_XP_EXEMPT_BELOW_LEVEL}.
     */
    public static PlayerStats applyDeathPenalty(Balance balance, PlayerStats stats) {
        if (stats.level() < DEATH_XP_EXEMPT_BELOW_LEVEL) {
            return stats;
        }
        int lost = (int) Math.floor(stats.xp() * balance.deathXpLoss());
        return stats.withProgress(stats.level(), stats.xp() - lost, stats.freePoints());
    }

    /** Takes every allocated point back into free points (bonuses from class and titles are untouched). */
    public static PlayerStats refundAll(PlayerStats stats) {
        int allocated = 0;
        PlayerStats result = stats;
        for (StatType stat : StatType.values()) {
            allocated += stats.get(stat);
            result = result.with(stat, 0);
        }
        return result.withProgress(stats.level(), stats.xp(), stats.freePoints() + allocated);
    }

    /** Spends one free point on {@code stat}; returns the same instance if there are none. */
    public static PlayerStats allocate(PlayerStats stats, StatType stat) {
        return allocate(stats, stat, 1);
    }

    /**
     * Spends up to {@code amount} free points on {@code stat} (all that are left if fewer);
     * returns the same instance if there are none or {@code amount} isn't positive.
     */
    public static PlayerStats allocate(PlayerStats stats, StatType stat, int amount) {
        int spent = Math.min(amount, stats.freePoints());
        if (spent <= 0) {
            return stats;
        }
        return stats.with(stat, stats.get(stat) + spent)
                .withProgress(stats.level(), stats.xp(), stats.freePoints() - spent);
    }
}
