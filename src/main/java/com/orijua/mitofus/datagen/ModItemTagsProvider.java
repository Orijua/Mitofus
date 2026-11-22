package com.orijua.mitofus.datagen;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.equipment.LootEvents;
import com.orijua.mitofus.feature.npc.ApprenticeMaterials;
import com.orijua.mitofus.feature.smithing.SmithingHammers;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

final class ModItemTagsProvider extends ItemTagsProvider {
    /** Items that can roll weapon options (item_option "applies_to"). */
    static final TagKey<Item> WEAPONS = ItemTags.create(Mitofus.id("weapons"));
    /** Items that can roll armor options. */
    static final TagKey<Item> ARMOR = ItemTags.create(Mitofus.id("armor"));
    /** What Smith takes for his iron quest (deliver goal "#mitofus:smith_iron"): raw iron or the ores. */
    static final TagKey<Item> SMITH_IRON = ItemTags.create(Mitofus.id("smith_iron"));

    ModItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
                        CompletableFuture<TagLookup<Block>> blockTags, ExistingFileHelper files) {
        super(output, lookup, blockTags, Mitofus.MODID, files);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(WEAPONS).addTag(ItemTags.SWORDS).addTag(ItemTags.AXES);
        tag(ARMOR).addTag(ItemTags.HEAD_ARMOR).addTag(ItemTags.CHEST_ARMOR).addTag(ItemTags.LEG_ARMOR).addTag(ItemTags.FOOT_ARMOR);

        // What elites can drop: vanilla swords, axes and armor from wood and leather to netherite, never the mod's unique weapons.
        // The material (and so how likely each piece is) comes from the item id, see EliteDropRules.
        tag(LootEvents.ELITE_DROPS).add(Items.WOODEN_SWORD, Items.WOODEN_AXE,
                Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
                Items.STONE_SWORD, Items.STONE_AXE,
                Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS,
                Items.IRON_SWORD, Items.IRON_AXE,
                Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
                Items.GOLDEN_SWORD, Items.GOLDEN_AXE,
                Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS,
                Items.DIAMOND_SWORD, Items.DIAMOND_AXE,
                Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
                Items.NETHERITE_SWORD, Items.NETHERITE_AXE,
                Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);

        // Being in minecraft:swords also makes them enchantable as swords.
        tag(ItemTags.SWORDS).add(ModItems.WOLFRAM_SWORD.get(), ModItems.DURANDARTE.get(), ModItems.GUADALETE_SWORD.get());

        copy(Tags.Blocks.ORES, Tags.Items.ORES);
        tag(Tags.Items.INGOTS).add(ModItems.MITHRIL_INGOT.get(), ModItems.WOLFRAM_INGOT.get(), ModItems.AZURITE_INGOT.get());
        tag(Tags.Items.RAW_MATERIALS).add(ModItems.RAW_MITHRIL.get(), ModItems.RAW_WOLFRAM.get(), ModItems.RAW_AZURITE.get());
        tag(SMITH_IRON).add(Items.RAW_IRON, Items.IRON_ORE, Items.DEEPSLATE_IRON_ORE);

        tag(SmithingHammers.TAG).add(ModItems.IRON_SMITHING_HAMMER.get(), ModItems.WOLFRAM_SMITHING_HAMMER.get(), ModItems.MITHRIL_SMITHING_HAMMER.get());

        // What an apprentice needs to have mastered to work each material; anything else is a beginner's.
        tag(ApprenticeMaterials.INTERMEDIATE).add(Items.GOLD_INGOT);
        tag(ApprenticeMaterials.ADVANCED).add(Items.DIAMOND);
        tag(ApprenticeMaterials.MASTER).add(Items.NETHERITE_INGOT, ModItems.MITHRIL_INGOT.get(), ModItems.AZURITE_INGOT.get());
    }
}
