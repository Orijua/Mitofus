package com.orijua.mitofus.core.debug;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.feature.equipment.EquipRequirements;
import com.orijua.mitofus.feature.equipment.EquipmentService;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.equipment.option.RolledOption;
import com.orijua.mitofus.feature.progression.StatType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Test equipment for {@code /mitofus give gear}: a sword that requires points of one stat and a chestplate that
 * gives a bonus in it through the real option path (the {@code debug_<stat>} options, weight 0: they never roll).
 */
public final class DebugGear {
    private DebugGear() {
    }

    /** The item option that gives the bonus of that stat. */
    public static ResourceLocation optionId(StatType stat) {
        return ResourceLocation.fromNamespaceAndPath(Mitofus.MODID, "debug_" + stat.getSerializedName());
    }

    /** An iron sword (level 1) that requires {@code requirement} points of {@code stat}. */
    public static ItemStack weapon(StatType stat, int requirement) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(ModDataComponents.REQUIREMENTS.get(), new EquipRequirements(1, Map.of(stat, requirement)));
        sword.set(ModDataComponents.ITEM_RATING.get(), ItemRating.UNIQUE);
        EquipmentService.refreshName(sword);
        return sword;
    }

    /**
     * An iron chestplate that gives {@code bonus} points of {@code stat} while worn, rated unique like the sword so both
     * look alike (the rating only adds attack damage in the main hand, so it changes nothing on armor).
     */
    public static ItemStack armor(StatType stat, int bonus) {
        ItemStack chestplate = new ItemStack(Items.IRON_CHESTPLATE);
        chestplate.set(ModDataComponents.ITEM_OPTIONS.get(), List.of(new RolledOption(optionId(stat), bonus)));
        chestplate.set(ModDataComponents.ITEM_RATING.get(), ItemRating.UNIQUE);
        EquipmentService.refreshName(chestplate);
        return chestplate;
    }

    /** Gives the sword and, if there is a bonus, the chestplate (dropped if the inventory is full); returns how many. */
    public static int give(ServerPlayer player, StatType stat, int requirement, OptionalInt bonus) {
        int given = 0;
        give(player, weapon(stat, requirement));
        given++;
        if (bonus.isPresent()) {
            give(player, armor(stat, bonus.getAsInt()));
            given++;
        }
        return given;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
