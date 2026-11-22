package com.orijua.mitofus.feature.effect;

/** Pure maths of the effects, with no Minecraft types so unit tests can reach it. */
public final class EffectRules {
    private EffectRules() {
    }

    /** Smallest dot product between the look direction and the direction to a target that is still inside the cone. */
    public static double coneMinDot(double coneDegrees) {
        return Math.cos(Math.toRadians(coneDegrees / 2));
    }

    /** Inside the range and the cone; a target closer than one block counts from any angle. */
    public static boolean inCone(double distance, double dot, double range, double minDot) {
        return distance <= range && (distance < 1.0 || dot >= minDot);
    }

    /** Weapon damage times multiplier and the STR/DEX scaling. */
    public static float areaDamage(double attack, double multiplier, double strengthScaling, double dexterityScaling,
                                   int strength, int dexterity) {
        return (float) (attack * multiplier * (1.0 + strengthScaling * strength + dexterityScaling * dexterity));
    }

    /** Sideways offset of projectile {@code index} out of {@code count}, symmetric around the look direction. */
    public static double projectileSpread(int index, int count) {
        return (index - (count - 1) / 2.0) * 0.08;
    }
}
