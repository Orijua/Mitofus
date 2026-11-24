package com.orijua.mitofus.datagen;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.loaders.SeparateTransformsModelBuilder;
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
        hammer3d(ModItems.WOLFRAM_SMITHING_HAMMER.get());
        hammer3d(ModItems.MITHRIL_SMITHING_HAMMER.get());
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

        // Batch 2: own 32x32 textures. Held objects have a 3D model in hands and on the ground.
        held3d(ModItems.SKILL_BOOK.get());
        held3d(ModItems.PHONE.get());
        held3d(ModItems.ORBE_AURIFERO.get());
        basicItem(ModItems.GUADALETE_BLUEPRINT.get());
        basicItem(ModItems.UNAPPRAISED_BOOK.get());
        basicItem(ModItems.APPRAISER_EYE.get());
        basicItem(ModItems.APPRENTICE_CONTRACT.get());
        basicItem(ModItems.TRANSFER_CONTRACT.get());
        basicItem(ModItems.WORKSHOP_LEDGER.get());
        basicItem(ModItems.RESPEC_SCROLL.get());
        basicItem(ModItems.TALENT_SCROLL.get());

        withExistingParent(ModItems.BLACKSMITH_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
        withExistingParent(ModItems.VILLAGE_CHIEF_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
        withExistingParent(ModItems.COACHMAN_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
        withExistingParent(ModItems.STOREKEEPER_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
        withExistingParent(ModItems.BONE_LORD_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
        withExistingParent(ModItems.VELASCO_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
    }

    /**
     * Flat 32x32 icon in the inventory and in frames, hand-made 3D model (models/item/<id>_3d.json) in hands and on
     * the ground.
     */
    private void hammer3d(Item item) {
        solid3d(item, "minecraft:item/handheld");
    }

    /** Same as {@link #hammer3d} for objects that are held, not swung: books, the phone, the orb. */
    private void held3d(Item item) {
        solid3d(item, "minecraft:item/generated");
    }

    private void solid3d(Item item, String flatParent) {
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        ModelFile solid = new ModelFile.UncheckedModelFile(modLoc("item/" + id + "_3d"));
        ItemModelBuilder builder = getBuilder(id).parent(new ModelFile.UncheckedModelFile("minecraft:item/generated"));
        SeparateTransformsModelBuilder<ItemModelBuilder> loader = builder.customLoader(SeparateTransformsModelBuilder::begin);
        loader.base(nested().parent(solid));
        for (ItemDisplayContext context : new ItemDisplayContext[] {ItemDisplayContext.GUI, ItemDisplayContext.FIXED}) {
            loader.perspective(context, nested().parent(new ModelFile.UncheckedModelFile(flatParent))
                    .texture("layer0", modLoc("item/" + id)));
        }
    }
}
