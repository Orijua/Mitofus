package com.orijua.mitofus.feature.progression;

import com.orijua.mitofus.Mitofus;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

/**
 * Where a defense value comes from, for the Profile: the pieces worn, the forge enhancements of those pieces, their rolled
 * options, and everything else (stat points, passives, effects). The total is always the real value of the attribute: what no
 * source explains (multipliers) goes to {@code other}, so the parts always add up.
 */
public final class DefenseRules {
    private DefenseRules() {
    }

    public enum Source { PIECES, ENHANCEMENT, OPTIONS, OTHER }

    /** The total and its parts. */
    public record Breakdown(double total, double pieces, double enhancement, double options, double other) {
        public double of(Source source) {
            return switch (source) {
                case PIECES -> pieces;
                case ENHANCEMENT -> enhancement;
                case OPTIONS -> options;
                case OTHER -> other;
            };
        }
    }

    /** Which source a modifier of the attribute belongs to, by its id. */
    public static Source sourceOf(ResourceLocation id) {
        String path = id.getPath();
        if (id.getNamespace().equals(Mitofus.MODID) && path.startsWith("equip_")) {
            if (path.endsWith("_enhancement")) {
                return Source.ENHANCEMENT;
            }
            if (path.contains("_option_")) {
                return Source.OPTIONS;
            }
        }
        if (path.startsWith("armor.")) {
            return Source.PIECES;
        }
        return Source.OTHER;
    }

    /** The parts of the player's {@code attribute} (armor or armor toughness). Flat modifiers are sorted by source. */
    public static Breakdown of(Player player, Holder<Attribute> attribute) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return new Breakdown(0, 0, 0, 0, 0);
        }
        double[] parts = new double[Source.values().length];
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                parts[sourceOf(modifier.id()).ordinal()] += modifier.amount();
            }
        }
        double total = instance.getValue();
        double base = instance.getBaseValue();
        return of(total, base, parts[Source.PIECES.ordinal()], parts[Source.ENHANCEMENT.ordinal()], parts[Source.OPTIONS.ordinal()]);
    }

    /** The breakdown from a total and the known flat parts: the rest (base value, multipliers, other flat modifiers) is {@code other}. */
    public static Breakdown of(double total, double base, double pieces, double enhancement, double options) {
        double other = total - pieces - enhancement - options;
        // The base value belongs with the rest; a rounding crumb is not shown.
        if (Math.abs(other) < 1.0E-6 && base == 0.0) {
            other = 0.0;
        }
        return new Breakdown(total, pieces, enhancement, options, other);
    }
}
