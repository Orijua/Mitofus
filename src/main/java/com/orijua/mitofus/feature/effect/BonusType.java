package com.orijua.mitofus.feature.effect;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** What a passive bonus changes. Each kind is applied in one place, see {@link ActiveTraits}. */
public enum BonusType implements StringRepresentable {
    /** Flat vanilla armor. */
    ARMOR("armor"),
    /** Flat vanilla knockback resistance (0 to 1). */
    KNOCKBACK_RESISTANCE("knockback_resistance"),
    /** Extra damage dealt, as a fraction (0.1 = +10 %), optionally only with a tagged weapon or at night. */
    DAMAGE("damage"),
    /** Flat extra maximum mana. */
    MAX_MANA("max_mana"),
    /** Extra mana regeneration, as a fraction of the normal rate (0.25 = +25 %). */
    MANA_REGEN("mana_regen"),
    /** Extra attack speed, as a fraction (0.1 = +10 %). */
    ATTACK_SPEED("attack_speed"),
    /** Flat extra maximum health (2 = one heart). */
    MAX_HEALTH("max_health"),
    /** Health recovered per second, flat (1 = half a heart per second). */
    HEALTH_REGEN("health_regen"),
    /** Fraction of the damage dealt that comes back as health (0.05 = 5 %). */
    LIFE_STEAL("life_steal"),
    /** Flat health recovered with every kill. */
    HEAL_ON_KILL("heal_on_kill"),
    /** Extra movement speed, as a fraction (0.1 = +10 %). */
    MOVE_SPEED("move_speed"),
    /** Chance (0 to 1) that a hit is critical; a critical hit deals {@code 1.5 + crit_damage} times the damage. */
    CRIT_CHANCE("crit_chance"),
    /** Extra critical damage, as a fraction added to the base 1.5 times (0.25 = 1.75 times). */
    CRIT_DAMAGE("crit_damage"),
    /** Fraction of the damage taken that is ignored (0.1 = 10 % less). */
    DAMAGE_REDUCTION("damage_reduction"),
    /** Flat mana recovered with every kill. */
    MANA_ON_KILL("mana_on_kill"),
    /** Fraction of the cooldown of skills that is saved (0.1 = 10 % shorter). */
    COOLDOWN_REDUCTION("cooldown_reduction"),
    /** Extra damage, as a fraction, against a creature that is not targeting the player. */
    DAMAGE_VS_UNAWARE("damage_vs_unaware"),
    /**
     * A chance (the {@code amount}) to give the target of a hit the {@code status} (see status.StatusService), with the
     * status's own intensity and duration unless the bonus writes its own.
     */
    APPLY_STATUS("apply_status");

    public static final Codec<BonusType> CODEC = StringRepresentable.fromEnum(BonusType::values);

    private final String serializedName;

    BonusType(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
