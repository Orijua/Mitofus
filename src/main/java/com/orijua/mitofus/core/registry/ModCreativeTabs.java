package com.orijua.mitofus.core.registry;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.skill.SkillBookItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Mitofus.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MITOFUS_TAB = CREATIVE_MODE_TABS.register("mitofus",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mitofus"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    // Lambda: the stack is built lazily, never during class init.
                    .icon(() -> ModItems.SMITHING_ANVIL.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.SMITHING_ANVIL);
                        output.accept(ModItems.SMELTER);
                        output.accept(ModItems.HELMET);
                        output.accept(ModItems.ADVANCED_HELMET);
                        output.accept(ModItems.DIAMOND_HELMET);
                        output.accept(ModItems.ACCOUNT_TERMINAL);
                        output.accept(ModItems.PHONE);
                        output.accept(ModItems.WOLFRAM_ORE);
                        output.accept(ModItems.DEEPSLATE_WOLFRAM_ORE);
                        output.accept(ModItems.RAW_WOLFRAM);
                        output.accept(ModItems.WOLFRAM_INGOT);
                        output.accept(ModItems.AZURITE_ORE);
                        output.accept(ModItems.DEEPSLATE_AZURITE_ORE);
                        output.accept(ModItems.RAW_AZURITE);
                        output.accept(ModItems.AZURITE_INGOT);
                        output.accept(ModItems.MITHRIL_ORE);
                        output.accept(ModItems.DEEPSLATE_MITHRIL_ORE);
                        output.accept(ModItems.RAW_MITHRIL);
                        output.accept(ModItems.MITHRIL_INGOT);
                        output.accept(ModItems.WOLFRAM_SWORD);
                        output.accept(ModItems.DURANDARTE);
                        output.accept(ModItems.GUADALETE_SWORD);
                        output.accept(ModItems.GUADALETE_BLUEPRINT);
                        output.accept(ModItems.UNAPPRAISED_BOOK);
                        output.accept(ModItems.APPRAISER_EYE);
                        output.accept(ModItems.ORBE_AURIFERO);
                        output.accept(ModItems.TRANSFER_CONTRACT);
                        output.accept(ModItems.APPRENTICE_CONTRACT);
                        output.accept(ModItems.WORKSHOP_LEDGER);
                        output.accept(ModItems.IRON_SMITHING_HAMMER);
                        output.accept(ModItems.WOLFRAM_SMITHING_HAMMER);
                        output.accept(ModItems.MITHRIL_SMITHING_HAMMER);
                        output.accept(ModItems.COMMANDERS_SWORD);
                        output.accept(ModItems.RULERS_SWORD);
                        output.accept(ModItems.ENHANCEMENT_STONE);
                        output.accept(ModItems.ARMOR_ENHANCEMENT_STONE);
                        output.accept(ModItems.BLESSED_WEAPON_ENHANCEMENT_STONE);
                        output.accept(ModItems.BLESSED_ARMOR_ENHANCEMENT_STONE);
                        output.accept(ModItems.BLESSED_ENHANCEMENT_SCROLL);
                        output.accept(ModItems.ANCIENT_WEAPON_ENHANCEMENT_SCROLL);
                        output.accept(ModItems.ANCIENT_ARMOR_ENHANCEMENT_SCROLL);
                        output.accept(ModItems.DIAMOND_COIN);
                        output.accept(ModItems.GOLD_COIN);
                        output.accept(ModItems.IRON_COIN);
                        output.accept(ModItems.COPPER_COIN);
                        output.accept(ModItems.RESPEC_SCROLL);
                        output.accept(ModItems.BLACKSMITH_SPAWN_EGG);
                        output.accept(ModItems.VILLAGE_CHIEF_SPAWN_EGG);
                        output.accept(ModItems.COACHMAN_SPAWN_EGG);
                        output.accept(ModItems.STOREKEEPER_SPAWN_EGG);
                        output.accept(ModItems.BONE_LORD_SPAWN_EGG);
                        output.accept(ModItems.VELASCO_SPAWN_EGG);
                        // One book per skill defined in the loaded datapacks.
                        parameters.holders().lookup(ModDatapackRegistries.SKILL).ifPresent(skills -> skills.listElementIds()
                                .forEach(key -> output.accept(SkillBookItem.forSkill(ModItems.SKILL_BOOK.get(), key.location()))));
                    })
                    .build());

    /** Also list our items in the matching vanilla tabs. */
    public static void addToVanillaTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.WOLFRAM_SWORD);
            event.accept(ModItems.DURANDARTE);
            event.accept(ModItems.GUADALETE_SWORD);
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.RAW_WOLFRAM);
            event.accept(ModItems.WOLFRAM_INGOT);
            event.accept(ModItems.RAW_AZURITE);
            event.accept(ModItems.AZURITE_INGOT);
            event.accept(ModItems.RAW_MITHRIL);
            event.accept(ModItems.MITHRIL_INGOT);
        }
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(ModItems.WOLFRAM_ORE);
            event.accept(ModItems.DEEPSLATE_WOLFRAM_ORE);
            event.accept(ModItems.AZURITE_ORE);
            event.accept(ModItems.DEEPSLATE_AZURITE_ORE);
            event.accept(ModItems.MITHRIL_ORE);
            event.accept(ModItems.DEEPSLATE_MITHRIL_ORE);
        }
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.BLACKSMITH_SPAWN_EGG);
            event.accept(ModItems.VILLAGE_CHIEF_SPAWN_EGG);
            event.accept(ModItems.COACHMAN_SPAWN_EGG);
            event.accept(ModItems.STOREKEEPER_SPAWN_EGG);
            event.accept(ModItems.BONE_LORD_SPAWN_EGG);
            event.accept(ModItems.VELASCO_SPAWN_EGG);
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.SMITHING_ANVIL);
            event.accept(ModItems.SMELTER);
            event.accept(ModItems.HELMET);
            event.accept(ModItems.ADVANCED_HELMET);
            event.accept(ModItems.DIAMOND_HELMET);
            event.accept(ModItems.ACCOUNT_TERMINAL);
            event.accept(ModItems.PHONE);
            event.accept(ModItems.GOLDEN_TABLE);
        }
    }

    private ModCreativeTabs() {
    }
}
