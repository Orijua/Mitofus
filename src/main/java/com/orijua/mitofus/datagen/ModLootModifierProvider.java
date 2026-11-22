package com.orijua.mitofus.datagen;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.equipment.RateEquipmentModifier;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.data.GlobalLootModifierProvider;

import java.util.concurrent.CompletableFuture;

/** Global loot modifiers: changes to the loot of tables that already exist (vanilla's and other mods'). */
final class ModLootModifierProvider extends GlobalLootModifierProvider {
    ModLootModifierProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, Mitofus.MODID);
    }

    @Override
    protected void start() {
        // Weapons and armor in any chest come rated, at base odds (the same as a novice smith).
        add("rate_chest_equipment", new RateEquipmentModifier(new LootItemCondition[0], "chests/", 0, ItemRating.NORMAL));
    }
}
