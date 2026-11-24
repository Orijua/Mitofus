package com.orijua.mitofus.core.registry;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.vrhelmet.HelmetItem;
import com.orijua.mitofus.feature.vrhelmet.HelmetItem;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetTier;
import com.orijua.mitofus.feature.economy.PhoneItem;
import com.orijua.mitofus.feature.equipment.EnhanceMaterial;
import com.orijua.mitofus.feature.equipment.EnhanceMaterialItem;
import com.orijua.mitofus.feature.view.ViewSwordItem;
import com.orijua.mitofus.feature.npc.ApprenticeContractItem;
import com.orijua.mitofus.feature.npc.WorkshopLedgerItem;
import com.orijua.mitofus.feature.sancho.AppraiserEyeItem;
import com.orijua.mitofus.feature.sancho.SanchoRareBookItem;
import com.orijua.mitofus.feature.sancho.UnappraisedItem;
import com.orijua.mitofus.feature.progression.RespecScrollItem;
import com.orijua.mitofus.feature.skill.SkillBookItem;
import com.orijua.mitofus.feature.talent.TalentScrollItem;
import com.orijua.mitofus.feature.smithing.BlueprintItem;
import com.orijua.mitofus.feature.smithing.SmithingHammerItem;
import com.orijua.mitofus.feature.eibar.TransferContractItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mitofus.MODID);

    // Wolfram: ore -> raw wolfram -> (smelt) ingot -> the Wolfram sword. Textures borrowed from vanilla copper for now.
    public static final DeferredItem<BlockItem> WOLFRAM_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.WOLFRAM_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_WOLFRAM_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_WOLFRAM_ORE);
    public static final DeferredItem<Item> RAW_WOLFRAM = ITEMS.registerSimpleItem("raw_wolfram");
    public static final DeferredItem<Item> WOLFRAM_INGOT = ITEMS.registerSimpleItem("wolfram_ingot");

    // Blue azurite: ore -> raw -> (smelter only) ingot -> Guadalete. Textures borrowed from vanilla for now.
    public static final DeferredItem<BlockItem> AZURITE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.AZURITE_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_AZURITE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_AZURITE_ORE);
    public static final DeferredItem<Item> RAW_AZURITE = ITEMS.registerSimpleItem("raw_azurite");
    public static final DeferredItem<Item> AZURITE_INGOT = ITEMS.registerSimpleItem("azurite_ingot");

    // Mithril: ore -> raw mithril -> (smelter only) ingot -> higher-tier material (enhancement stones, respec scrolls, Cid's sword).
    public static final DeferredItem<BlockItem> MITHRIL_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.MITHRIL_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_MITHRIL_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_MITHRIL_ORE);
    public static final DeferredItem<Item> RAW_MITHRIL = ITEMS.registerSimpleItem("raw_mithril");
    public static final DeferredItem<Item> MITHRIL_INGOT = ITEMS.registerSimpleItem("mithril_ingot");

    // Forged at the smithing anvil from 2 wolfram ingots and a stick, so it usually carries a rating.
    public static final DeferredItem<SwordItem> WOLFRAM_SWORD = ITEMS.register("wolfram_sword",
            () -> new SwordItem(Tiers.IRON, new Item.Properties()
                    .attributes(SwordItem.createAttributes(Tiers.IRON, 3, -2.4f))));

    // The old id is kept; the stats and the recipe are own
    // design. Forged from diamonds; stronger than a diamond sword but needs level 10 and 8 STR.
    public static final DeferredItem<SwordItem> DURANDARTE = ITEMS.register("durandarte",
            () -> new SwordItem(Tiers.DIAMOND, new Item.Properties()
                    .attributes(SwordItem.createAttributes(Tiers.DIAMOND, 5, -2.4f))));

    // Guadalete: Cid's first legendary work, a blue azurite greatsword whose requirements nobody meets.
    // Forged only from its blueprint, which the Sancho's Successor quest gives and the forge uses up.
    public static final DeferredItem<SwordItem> GUADALETE_SWORD = ITEMS.register("guadalete_sword",
            () -> new SwordItem(Tiers.NETHERITE, new Item.Properties()
                    .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 7, -2.8f))));
    public static final DeferredItem<BlueprintItem> GUADALETE_BLUEPRINT = ITEMS.registerItem("guadalete_blueprint",
            BlueprintItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    // The book on the golden table of the Altamira Cave: unknown until it is appraised.
    public static final DeferredItem<UnappraisedItem> UNAPPRAISED_BOOK = ITEMS.registerItem("unappraised_book",
            UnappraisedItem::new, new Item.Properties().stacksTo(1).fireResistant());
    // The Appraiser Eye appraises it (sold by Smith, expensive): it becomes Sancho's rare book, and Conde Nuno comes for it.
    public static final DeferredItem<AppraiserEyeItem> APPRAISER_EYE = ITEMS.registerItem("appraiser_eye",
            AppraiserEyeItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
    public static final DeferredItem<SanchoRareBookItem> ORBE_AURIFERO = ITEMS.registerItem("orbe_aurifero",
            SanchoRareBookItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    public static final DeferredItem<BlockItem> GOLDEN_TABLE = ITEMS.registerSimpleBlockItem(ModBlocks.GOLDEN_TABLE);

    // An apprentice blacksmith's contract (mass production, phase 2): Smith gives the first at smithing 50 and sells the rest.
    public static final DeferredItem<ApprenticeContractItem> APPRENTICE_CONTRACT = ITEMS.registerItem("apprentice_contract",
            ApprenticeContractItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    // Smithing hammers (forged at the anvil, rated): in the off hand they improve forging; an apprentice can carry one.
    public static final DeferredItem<SmithingHammerItem> IRON_SMITHING_HAMMER = ITEMS.registerItem("iron_smithing_hammer",
            properties -> new SmithingHammerItem(5, properties), new Item.Properties().durability(250));
    public static final DeferredItem<SmithingHammerItem> WOLFRAM_SMITHING_HAMMER = ITEMS.registerItem("wolfram_smithing_hammer",
            properties -> new SmithingHammerItem(10, properties), new Item.Properties().durability(500));
    public static final DeferredItem<SmithingHammerItem> MITHRIL_SMITHING_HAMMER = ITEMS.registerItem("mithril_smithing_hammer",
            properties -> new SmithingHammerItem(20, properties), new Item.Properties().durability(1000).rarity(Rarity.UNCOMMON));
    // Swords of observation: the Commander's (the chief's watch quest) and the Ruler's (lent by the chief).
    public static final DeferredItem<ViewSwordItem> COMMANDERS_SWORD = ITEMS.registerItem("commanders_sword",
            properties -> new ViewSwordItem(Tiers.IRON, 100, 25, false, properties.attributes(SwordItem.createAttributes(Tiers.IRON, 3, -2.4f))),
            new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<ViewSwordItem> RULERS_SWORD = ITEMS.registerItem("rulers_sword",
            properties -> new ViewSwordItem(Tiers.DIAMOND, 140, 10, true, properties.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4f))),
            new Item.Properties().rarity(Rarity.RARE));
    // Opens the Workshop tab: Smith gives it with the first contract; also crafted (book + iron ingot).
    public static final DeferredItem<WorkshopLedgerItem> WORKSHOP_LEDGER = ITEMS.registerItem("workshop_ledger",
            WorkshopLedgerItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredItem<BlockItem> SMITHING_ANVIL = ITEMS.registerSimpleBlockItem(ModBlocks.SMITHING_ANVIL);
    public static final DeferredItem<BlockItem> SMELTER = ITEMS.registerSimpleBlockItem(ModBlocks.SMELTER);
    // The connection helmets (feature/VR helmet): put one on (right-click) to connect; the body lies down where you are.
    public static final DeferredItem<HelmetItem> HELMET = ITEMS.registerItem("helmet",
            properties -> new HelmetItem(VrHelmetTier.BASIC, properties), new Item.Properties().stacksTo(1));
    public static final DeferredItem<HelmetItem> ADVANCED_HELMET = ITEMS.registerItem("advanced_helmet",
            properties -> new HelmetItem(VrHelmetTier.ADVANCED, properties), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<HelmetItem> DIAMOND_HELMET = ITEMS.registerItem("diamond_helmet",
            properties -> new HelmetItem(VrHelmetTier.DIAMOND, properties), new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> ACCOUNT_TERMINAL = ITEMS.registerSimpleBlockItem(ModBlocks.ACCOUNT_TERMINAL);
    // The phone: reaches the account and the work from anywhere in the real world (feature/economy).
    public static final DeferredItem<PhoneItem> PHONE = ITEMS.registerItem("phone", PhoneItem::new, new Item.Properties().stacksTo(1));

    // The weapon stone keeps the old id so saved items and quests still work; the others are new (feature/equipment).
    public static final DeferredItem<EnhanceMaterialItem> ENHANCEMENT_STONE = ITEMS.registerItem("enhancement_stone",
            p -> new EnhanceMaterialItem(EnhanceMaterial.WEAPON_STONE, p), new Item.Properties());
    public static final DeferredItem<EnhanceMaterialItem> ARMOR_ENHANCEMENT_STONE = ITEMS.registerItem("armor_enhancement_stone",
            p -> new EnhanceMaterialItem(EnhanceMaterial.ARMOR_STONE, p), new Item.Properties());
    public static final DeferredItem<EnhanceMaterialItem> BLESSED_WEAPON_ENHANCEMENT_STONE = ITEMS.registerItem("blessed_weapon_enhancement_stone",
            p -> new EnhanceMaterialItem(EnhanceMaterial.BLESSED_WEAPON_STONE, p),
            new Item.Properties().rarity(Rarity.RARE).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
    public static final DeferredItem<EnhanceMaterialItem> BLESSED_ARMOR_ENHANCEMENT_STONE = ITEMS.registerItem("blessed_armor_enhancement_stone",
            p -> new EnhanceMaterialItem(EnhanceMaterial.BLESSED_ARMOR_STONE, p),
            new Item.Properties().rarity(Rarity.RARE).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
    public static final DeferredItem<EnhanceMaterialItem> BLESSED_ENHANCEMENT_SCROLL = ITEMS.registerItem("blessed_enhancement_scroll",
            p -> new EnhanceMaterialItem(EnhanceMaterial.BLESSED_SCROLL, p),
            new Item.Properties().rarity(Rarity.RARE).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
    public static final DeferredItem<EnhanceMaterialItem> ANCIENT_WEAPON_ENHANCEMENT_SCROLL = ITEMS.registerItem("ancient_weapon_enhancement_scroll",
            p -> new EnhanceMaterialItem(EnhanceMaterial.ANCIENT_WEAPON_SCROLL, p),
            new Item.Properties().rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
    public static final DeferredItem<EnhanceMaterialItem> ANCIENT_ARMOR_ENHANCEMENT_SCROLL = ITEMS.registerItem("ancient_armor_enhancement_scroll",
            p -> new EnhanceMaterialItem(EnhanceMaterial.ANCIENT_ARMOR_SCROLL, p),
            new Item.Properties().rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

    // Mitofus's money (gold); not vanilla.
    public static final DeferredItem<Item> GOLD_COIN = ITEMS.registerSimpleItem("gold_coin",
            new Item.Properties().rarity(Rarity.UNCOMMON));

    /** 1 diamond = 100 gold: the top coin (the mod's own design). */
    public static final DeferredItem<Item> DIAMOND_COIN = ITEMS.registerSimpleItem("diamond_coin",
            new Item.Properties().rarity(Rarity.RARE));

    /** 1 gold = 100 iron. */
    public static final DeferredItem<Item> IRON_COIN = ITEMS.registerSimpleItem("iron_coin",
            new Item.Properties());
    /** 1 iron = 100 copper (the mod's own design). Rewards of minor quests are paid in it. */
    public static final DeferredItem<Item> COPPER_COIN = ITEMS.registerSimpleItem("copper_coin",
            new Item.Properties());

    public static final DeferredItem<SkillBookItem> SKILL_BOOK = ITEMS.registerItem("skill_book",
            SkillBookItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredItem<DeferredSpawnEggItem> BLACKSMITH_SPAWN_EGG = ITEMS.register("blacksmith_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BLACKSMITH, 0x4A3222, 0x9EE6F5, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> VILLAGE_CHIEF_SPAWN_EGG = ITEMS.register("village_chief_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.VILLAGE_CHIEF, 0x6B4A2B, 0xC9A64B, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> COACHMAN_SPAWN_EGG = ITEMS.register("coachman_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.COACHMAN, 0x5A3E24, 0x3E6B8A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> STOREKEEPER_SPAWN_EGG = ITEMS.register("storekeeper_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.STOREKEEPER, 0x5A3E24, 0x7A7A7A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> BONE_LORD_SPAWN_EGG = ITEMS.register("bone_lord_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BONE_LORD, 0x1E1E1E, 0xD9D3C1, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> VELASCO_SPAWN_EGG = ITEMS.register("velasco_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.VELASCO, 0x4A3A2A, 0x8C8C8C, new Item.Properties()));

    public static final DeferredItem<TransferContractItem> TRANSFER_CONTRACT = ITEMS.registerItem("transfer_contract",
            TransferContractItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredItem<RespecScrollItem> RESPEC_SCROLL = ITEMS.registerItem("respec_scroll",
            RespecScrollItem::new, new Item.Properties().stacksTo(16));

    public static final DeferredItem<TalentScrollItem> TALENT_SCROLL = ITEMS.registerItem("talent_scroll",
            TalentScrollItem::new, new Item.Properties().stacksTo(16));

    private ModItems() {
    }
}
