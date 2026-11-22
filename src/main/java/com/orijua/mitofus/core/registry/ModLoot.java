package com.orijua.mitofus.core.registry;

import com.mojang.serialization.MapCodec;
import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.equipment.RateEquipmentFunction;
import com.orijua.mitofus.feature.equipment.RateEquipmentModifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Loot: a function loot tables can call ("function": "mitofus:rate_equipment") and a global
 * modifier type that changes the loot of existing tables (its JSON is generated in datagen).
 */
public final class ModLoot {
    public static final DeferredRegister<LootItemFunctionType<?>> LOOT_FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, Mitofus.MODID);
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Mitofus.MODID);

    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<RateEquipmentFunction>> RATE_EQUIPMENT =
            LOOT_FUNCTIONS.register("rate_equipment", () -> new LootItemFunctionType<>(RateEquipmentFunction.CODEC));

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<RateEquipmentModifier>> RATE_EQUIPMENT_MODIFIER =
            LOOT_MODIFIERS.register("rate_equipment", () -> RateEquipmentModifier.CODEC);

    private ModLoot() {
    }
}
