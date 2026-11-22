package com.orijua.mitofus.feature.progression;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.Balance;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Maps stats onto vanilla attributes (project design). Intelligence has no vanilla attribute:
 * it drives max mana instead, see {@link ProgressionRules#maxMana}.
 */
public final class StatEffects {
    private record Mapping(StatType stat, Holder<Attribute> attribute, double perPoint, AttributeModifier.Operation operation) {
        ResourceLocation id() {
            return Mitofus.id("stat_" + stat.getSerializedName());
        }
    }

    private static final Mapping[] MAPPINGS = {
            new Mapping(StatType.STRENGTH, Attributes.ATTACK_DAMAGE, 0.125, AttributeModifier.Operation.ADD_VALUE),
            new Mapping(StatType.STAMINA, Attributes.MAX_HEALTH, 0.25, AttributeModifier.Operation.ADD_VALUE),
            new Mapping(StatType.AGILITY, Attributes.MOVEMENT_SPEED, 0.002, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
            new Mapping(StatType.DEXTERITY, Attributes.ATTACK_SPEED, 0.005, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
    };

    private StatEffects() {
    }

    /** What one point of {@code stat} adds with the current config (0 if it has none), for UI descriptions. */
    public static double currentPerPoint(StatType stat) {
        return perPoint(stat) * ProgressionRules.pointScale(Balance.current().statPointsPerLevel());
    }

    /** What one point of {@code stat} adds to its vanilla attribute at {@link ProgressionRules#REFERENCE_POINTS_PER_LEVEL} points per level (0 if it has none). */
    public static double perPoint(StatType stat) {
        for (Mapping mapping : MAPPINGS) {
            if (mapping.stat() == stat) {
                return mapping.perPoint();
            }
        }
        return 0;
    }

    /** Idempotent: safe to call on login, respawn and every stat change. */
    public static void apply(Player player, PlayerStats stats) {
        double scale = ProgressionRules.pointScale(Balance.current().statPointsPerLevel());
        for (Mapping mapping : MAPPINGS) {
            AttributeInstance instance = player.getAttribute(mapping.attribute());
            if (instance == null) {
                continue;
            }
            int points = stats.get(mapping.stat());
            if (points <= 0) {
                instance.removeModifier(mapping.id());
            } else {
                instance.addOrReplacePermanentModifier(
                        new AttributeModifier(mapping.id(), points * mapping.perPoint() * scale, mapping.operation()));
            }
        }
        limitHealth(player);
    }

    /** Takes every stat modifier off (the real world has no Mitofus bonuses); current health is capped, never lethal. */
    public static void clear(Player player) {
        for (Mapping mapping : MAPPINGS) {
            AttributeInstance instance = player.getAttribute(mapping.attribute());
            if (instance != null) {
                instance.removeModifier(mapping.id());
            }
        }
        limitHealth(player);
    }

    private static void limitHealth(Player player) {
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }
}
