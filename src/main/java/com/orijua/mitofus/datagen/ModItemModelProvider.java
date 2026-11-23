package com.orijua.mitofus.datagen;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Item models for plain items (block items come from ModBlockStateProvider). */
final class ModItemModelProvider extends ItemModelProvider {
    ModItemModelProvider(PackOutput output, ExistingFileHelper files) {
        super(output, Mitofus.MODID, files);
    }

    @Override
    protected void registerModels() {
        // Own textures: textures/item/<id>.png.
        basicItem(ModItems.RAW_MITHRIL.get());
        basicItem(ModItems.MITHRIL_INGOT.get());
        handheldItem(ModItems.WOLFRAM_SWORD.get());
        handheldItem(ModItems.DURANDARTE.get());
        handheldItem(ModItems.GUADALETE_SWORD.get()); // the user's 32x32 PNG
        // Batch 1 (draft textures from the art sources, to be repainted by hand): plain item/generated or handheld.
        basicItem(ModItems.RAW_WOLFRAM.get());
        basicItem(ModItems.WOLFRAM_INGOT.get());
        basicItem(ModItems.AZURITE_INGOT.get());
        basicItem(ModItems.RAW_AZURITE.get());
        basicItem(ModItems.HELMET.get());
        basicItem(ModItems.ADVANCED_HELMET.get());
        basicItem(ModItems.DIAMOND_HELMET.get());
        handheldItem(ModItems.IRON_SMITHING_HAMMER.get());
        handheldItem(ModItems.WOLFRAM_SMITHING_HAMMER.get());
        handheldItem(ModItems.MITHRIL_SMITHING_HAMMER.get());
        handheldItem(ModItems.COMMANDERS_SWORD.get());
        handheldItem(ModItems.RULERS_SWORD.get());
        basicItem(ModItems.ENHANCEMENT_STONE.get());
        basicItem(ModItems.ARMOR_ENHANCEMENT_STONE.get());
        basicItem(ModItems.BLESSED_WEAPON_ENHANCEMENT_STONE.get());
        basicItem(ModItems.BLESSED_ARMOR_ENHANCEMENT_STONE.get());
        basicItem(ModItems.BLESSED_ENHANCEMENT_SCROLL.get());
        basicItem(ModItems.ANCIENT_WEAPON_ENHANCEMENT_SCROLL.get());
        basicItem(ModItems.ANCIENT_ARMOR_ENHANCEMENT_SCROLL.get());

        // Placeholders that reuse a vanilla texture until there is an own PNG.
        basicItem(ModItems.DIAMOND_COIN.get());
        basicItem(ModItems.GOLD_COIN.get());
        basicItem(ModItems.IRON_COIN.get());
        basicItem(ModItems.COPPER_COIN.get());
        vanillaTexture(ModItems.SKILL_BOOK.get(), "writable_book");
        vanillaTexture(ModItems.GUADALETE_BLUEPRINT.get(), "map");
        vanillaTexture(ModItems.UNAPPRAISED_BOOK.get(), "book");
        vanillaTexture(ModItems.APPRAISER_EYE.get(), "ender_eye");
        vanillaTexture(ModItems.APPRENTICE_CONTRACT.get(), "paper");
        vanillaTexture(ModItems.TRANSFER_CONTRACT.get(), "paper");
        vanillaTexture(ModItems.WORKSHOP_LEDGER.get(), "writable_book");
        vanillaTexture(ModItems.RESPEC_SCROLL.get(), "paper");
        vanillaTexture(ModItems.TALENT_SCROLL.get(), "paper");
        vanillaTexture(ModItems.ORBE_AURIFERO.get(), "enchanted_book");

        // The phone borrows a dark glass block as its texture until it has its own art.
        withExistingParent("phone", mcLoc("item/generated")).texture("layer0", mcLoc("block/black_stained_glass"));

        withExistingParent(ModItems.BLACKSMITH_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
        withExistingParent(ModItems.VILLAGE_CHIEF_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
        withExistingParent(ModItems.COACHMAN_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
        withExistingParent(ModItems.STOREKEEPER_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
        withExistingParent(ModItems.BONE_LORD_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
        withExistingParent(ModItems.VELASCO_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
    }

    private void vanillaTexture(Item item, String vanillaTexture) {
        withExistingParent(BuiltInRegistries.ITEM.getKey(item).getPath(), mcLoc("item/generated"))
                .texture("layer0", mcLoc("item/" + vanillaTexture));
    }
}
