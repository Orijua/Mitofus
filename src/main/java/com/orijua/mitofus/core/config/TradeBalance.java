package com.orijua.mitofus.core.config;

import com.orijua.mitofus.feature.equipment.ItemRating;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * The tunable numbers of the trade (selling equipment for gold), apart from {@link Balance}. Rules take it as a
 * parameter so they stay pure; the game passes {@link #current()} (the server config), tests pass {@link #DEFAULT}.
 * Every number is.
 *
 * @param basePrice          reference price in gold of a piece of each rating, before material and enhancement
 * @param materialMultiplier multiplier by material (wood, stone, iron, gold, diamond, netherite, leather, chainmail)
 * @param otherMaterial      multiplier of any material that is not in the map
 * @param enhancementBonus   share of the reference added per enhancement level (every piece)
 * @param levelBonus         share of the reference added per level of the effective required level above 1
 * @param optionBonus        share of the reference added per option the piece carries
 * @param impossibleMargin   a stored required level more than this many levels above the derived one makes the piece "impossible"
 * @param impossibleFactor   price factor of an impossible piece (0 to 1)
 * @param sellShare          share of the reference paid in gold when selling
 * @param saturationStep     price factor for each point of pressure
 * @param saturationFloor    the price factor never goes below this
 * @param recoveryPerDay     points of pressure that go away each whole game day
 * @param pressureCap        pressure never grows beyond this
 * @param maxSellGold    a sale that would pay more gold than this is refused before the piece is touched
 * @param minDurabilityFactor price factor of a piece with no durability left; a new piece pays 1, in between it is linear (0 to 1)
 * @param itemMultiplier     multiplier of single objects by item id (0 to 100); it replaces the one of the material, so a unique
 *                           object does not weigh as iron or netherite just for its tier
 */
public record TradeBalance(
        Map<ItemRating, Integer> basePrice,
        Map<String, Double> materialMultiplier,
        double otherMaterial,
        double enhancementBonus,
        double levelBonus,
        double optionBonus,
        int impossibleMargin,
        double impossibleFactor,
        double sellShare,
        double saturationStep,
        double saturationFloor,
        int recoveryPerDay,
        int pressureCap,
        long maxSellGold,
        double minDurabilityFactor,
        Map<ResourceLocation, Double> itemMultiplier) {

    public static final TradeBalance DEFAULT = new TradeBalance(
            Map.of(ItemRating.NORMAL, 1, ItemRating.RARE, 3, ItemRating.EPIC, 7,
                    ItemRating.UNIQUE, 30, ItemRating.LEGENDARY, 150, ItemRating.ANCESTRAL, 600),
            Map.of("wood", 0.5, "gold", 0.5, "leather", 0.5, "stone", 1.0, "iron", 1.0, "chainmail", 1.0,
                    "diamond", 2.0, "netherite", 3.0),
            1.0, 0.10, 0.02, 0.10, 50, 0.25, 0.40, 0.95, 0.15, 2, 37, 3_333L, 0.25,
            Map.of(ResourceLocation.fromNamespaceAndPath("mitofus", "wolfram_sword"), 2.0,
                    ResourceLocation.fromNamespaceAndPath("mitofus", "wolfram_smithing_hammer"), 2.0,
                    ResourceLocation.fromNamespaceAndPath("mitofus", "mithril_smithing_hammer"), 3.0));

    public TradeBalance {
        basePrice = Map.copyOf(basePrice);
        materialMultiplier = Map.copyOf(materialMultiplier);
        itemMultiplier = Map.copyOf(itemMultiplier);
        for (double multiplier : itemMultiplier.values()) {
            if (!(multiplier >= 0.0 && multiplier <= 100.0)) {
                throw new IllegalArgumentException("an item multiplier must be in [0, 100]: " + multiplier);
            }
        }
        if (!(levelBonus >= 0.0 && optionBonus >= 0.0)) {
            throw new IllegalArgumentException("levelBonus and optionBonus must not be negative: " + levelBonus + ", " + optionBonus);
        }
        if (impossibleMargin < 0) {
            throw new IllegalArgumentException("impossibleMargin must not be negative: " + impossibleMargin);
        }
        if (!(impossibleFactor >= 0.0 && impossibleFactor <= 1.0)) {
            throw new IllegalArgumentException("impossibleFactor must be in [0, 1]: " + impossibleFactor);
        }
        if (!(minDurabilityFactor >= 0.0 && minDurabilityFactor <= 1.0)) {
            throw new IllegalArgumentException("minDurabilityFactor must be in [0, 1]: " + minDurabilityFactor);
        }
    }

    /** Set only inside {@link #withOverride}; read by {@link #current()} before the config. */
    private static TradeBalance override;

    /** The active server config, or the defaults before a world is loaded (e.g. on the title screen). */
    public static TradeBalance current() {
        if (override != null) {
            return override;
        }
        return Config.isLoaded() ? Config.tradeBalance() : DEFAULT;
    }

    /** For game tests: {@link #current()} returns {@code balance} while {@code action} runs, then the config again. */
    public static void withOverride(TradeBalance balance, Runnable action) {
        TradeBalance previous = override;
        override = balance;
        try {
            action.run();
        } finally {
            override = previous;
        }
    }
}
