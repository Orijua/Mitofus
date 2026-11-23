package com.orijua.mitofus.datagen;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

final class ModBlockTagsProvider extends BlockTagsProvider {
    ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper files) {
        super(output, lookup, Mitofus.MODID, files);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.SMITHING_ANVIL.get(), ModBlocks.SMELTER.get(), ModBlocks.ACCOUNT_TERMINAL.get(),
                ModBlocks.MITHRIL_ORE.get(), ModBlocks.DEEPSLATE_MITHRIL_ORE.get(),
                ModBlocks.WOLFRAM_ORE.get(), ModBlocks.DEEPSLATE_WOLFRAM_ORE.get(), ModBlocks.AZURITE_ORE.get(),
                ModBlocks.DEEPSLATE_AZURITE_ORE.get());
        tag(BlockTags.NEEDS_DIAMOND_TOOL).add(ModBlocks.AZURITE_ORE.get(), ModBlocks.DEEPSLATE_AZURITE_ORE.get());
        tag(BlockTags.NEEDS_STONE_TOOL).add(ModBlocks.WOLFRAM_ORE.get(), ModBlocks.DEEPSLATE_WOLFRAM_ORE.get());
        tag(BlockTags.NEEDS_IRON_TOOL).add(ModBlocks.MITHRIL_ORE.get(), ModBlocks.DEEPSLATE_MITHRIL_ORE.get());
        // c:ores, shared with other mods.
        tag(Tags.Blocks.ORES).add(ModBlocks.MITHRIL_ORE.get(), ModBlocks.DEEPSLATE_MITHRIL_ORE.get(), ModBlocks.WOLFRAM_ORE.get(),
                ModBlocks.DEEPSLATE_WOLFRAM_ORE.get(), ModBlocks.AZURITE_ORE.get(),
                ModBlocks.DEEPSLATE_AZURITE_ORE.get());
    }
}
