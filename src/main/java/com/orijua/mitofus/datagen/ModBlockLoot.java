package com.orijua.mitofus.datagen;

import com.orijua.mitofus.core.registry.ModBlocks;
import com.orijua.mitofus.core.registry.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

import java.util.Set;

/** What each block drops. Every block in ModBlocks needs an entry, or datagen fails. */
final class ModBlockLoot extends BlockLootSubProvider {
    ModBlockLoot(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        dropSelf(ModBlocks.SMITHING_ANVIL.get());
        dropSelf(ModBlocks.SMELTER.get());
        // The body drops the helmet through VrHelmetBlock#getDrops (the helmet keeps its owner), not through a table.
        add(ModBlocks.BODY.get(), noDrop());
        add(ModBlocks.ADVANCED_BODY.get(), noDrop());
        add(ModBlocks.DIAMOND_BODY.get(), noDrop());
        dropSelf(ModBlocks.ACCOUNT_TERMINAL.get());
        // Like iron ore: the raw material (more with Fortune), the ore itself with Silk Touch.
        add(ModBlocks.WOLFRAM_ORE.get(), block -> createOreDrop(block, ModItems.RAW_WOLFRAM.get()));
        add(ModBlocks.DEEPSLATE_WOLFRAM_ORE.get(), block -> createOreDrop(block, ModItems.RAW_WOLFRAM.get()));
        add(ModBlocks.AZURITE_ORE.get(), block -> createOreDrop(block, ModItems.RAW_AZURITE.get()));
        add(ModBlocks.DEEPSLATE_AZURITE_ORE.get(), block -> createOreDrop(block, ModItems.RAW_AZURITE.get()));
        add(ModBlocks.MITHRIL_ORE.get(), block -> createOreDrop(block, ModItems.RAW_MITHRIL.get()));
        add(ModBlocks.DEEPSLATE_MITHRIL_ORE.get(), block -> createOreDrop(block, ModItems.RAW_MITHRIL.get()));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(Holder::value).map(Block.class::cast)::iterator;
    }
}
