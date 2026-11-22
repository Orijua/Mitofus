package com.orijua.mitofus.feature.equipment;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;

import java.util.Locale;
import java.util.Optional;

/** The material key of a piece, shared by its price and its level: the vanilla tier name of a tool or the path of an armor material. */
public final class EquipMaterial {
    private EquipMaterial() {
    }

    /** E.g. {@code iron}, {@code netherite}, {@code leather}; empty for anything else (the "other material"). */
    public static Optional<String> key(ItemStack stack) {
        if (stack.getItem() instanceof TieredItem tiered && tiered.getTier() instanceof Tiers tier) {
            return Optional.of(tier.name().toLowerCase(Locale.ROOT));
        }
        if (stack.getItem() instanceof ArmorItem armor) {
            return armor.getMaterial().unwrapKey().map(key -> key.location().getPath());
        }
        return Optional.empty();
    }
}
