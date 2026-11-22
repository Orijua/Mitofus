package com.orijua.mitofus.core.registry;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetBlockEntity;
import com.orijua.mitofus.feature.smelting.SmelterBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Blocks that keep state of their own (items, heat) between ticks and saves. */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Mitofus.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmelterBlockEntity>> SMELTER =
            BLOCK_ENTITIES.register("smelter", () -> BlockEntityType.Builder.of(SmelterBlockEntity::new, ModBlocks.SMELTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VrHelmetBlockEntity>> VR_HELMET =
            BLOCK_ENTITIES.register("vrhelmet", () -> BlockEntityType.Builder.of(VrHelmetBlockEntity::new,
                    ModBlocks.BODY.get(), ModBlocks.ADVANCED_BODY.get(), ModBlocks.DIAMOND_BODY.get()).build(null));

    private ModBlockEntities() {
    }
}
