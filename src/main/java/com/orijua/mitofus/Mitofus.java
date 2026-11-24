/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Orijua
 *
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.orijua.mitofus;

import com.mojang.logging.LogUtils;
import com.orijua.mitofus.core.config.Config;
import com.orijua.mitofus.core.debug.DebugCommands;
import com.orijua.mitofus.core.network.ModNetwork;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModBlockEntities;
import com.orijua.mitofus.core.registry.ModBlocks;
import com.orijua.mitofus.core.registry.ModCreativeTabs;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.core.registry.ModEffects;
import com.orijua.mitofus.core.registry.ModEntities;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.core.registry.ModLoot;
import com.orijua.mitofus.core.registry.ModMenus;
import com.orijua.mitofus.core.registry.ModRecipes;
import com.orijua.mitofus.core.registry.ModWorldgen;
import com.orijua.mitofus.core.validation.ContentValidationEvents;
import com.orijua.mitofus.datagen.ModDataGenerators;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetService;
import com.orijua.mitofus.feature.character.Characters;
import com.orijua.mitofus.feature.economy.TerminalService;
import com.orijua.mitofus.feature.fatigue.FatigueRestraint;
import com.orijua.mitofus.feature.fatigue.FatigueService;
import com.orijua.mitofus.core.save.SaveMigrations;
import com.orijua.mitofus.feature.vrhelmet.StarterVrHelmet;
import com.orijua.mitofus.feature.vrhelmet.LogoutService;
import com.orijua.mitofus.feature.vrhelmet.MitofusDeathService;
import com.orijua.mitofus.feature.vrhelmet.WorldSeparation;
import com.orijua.mitofus.feature.equipment.EquipmentEvents;
import com.orijua.mitofus.feature.equipment.LootEvents;
import com.orijua.mitofus.feature.view.Observation;
import com.orijua.mitofus.feature.mob.MobLevelEvents;
import com.orijua.mitofus.feature.npc.NpcLevels;
import com.orijua.mitofus.feature.mob.StunService;
import com.orijua.mitofus.feature.notification.NotificationEvents;
import com.orijua.mitofus.feature.npc.ApprenticeAssignments;
import com.orijua.mitofus.feature.npc.ApprenticeRoster;
import com.orijua.mitofus.feature.npc.Illumination;
import com.orijua.mitofus.feature.sancho.NunoBetrayal;
import com.orijua.mitofus.feature.sancho.LegendTitle;
import com.orijua.mitofus.feature.progression.ProgressionEvents;
import com.orijua.mitofus.feature.progression.XpRateService;
import com.orijua.mitofus.feature.quest.QuestDeadlines;
import com.orijua.mitofus.feature.equipment.WeightService;
import com.orijua.mitofus.feature.coach.CoachService;
import com.orijua.mitofus.feature.economy.WalletEvents;
import com.orijua.mitofus.feature.quest.goal.GoalTracker;
import com.orijua.mitofus.feature.rumor.MaterialFinds;
import com.orijua.mitofus.feature.effect.TraitEvents;
import com.orijua.mitofus.feature.skill.SkillEvents;
import com.orijua.mitofus.feature.eibar.VelascoContest;
import com.orijua.mitofus.feature.eibar.EibarStory;
import com.orijua.mitofus.feature.world.AltamiraProtection;
import com.orijua.mitofus.feature.world.MitofusOnlyEvents;
import com.orijua.mitofus.feature.world.StartingVillage;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Common entry point (runs on both client and dedicated server).
 * Every registry and event listener is wired here explicitly, so this class is the
 * single place to look to see what the mod plugs into.
 */
@Mod(Mitofus.MODID)
public class Mitofus {
    public static final String MODID = "mitofus";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Mitofus(IEventBus modEventBus, ModContainer modContainer) {
        // Registries (mod bus): content must be registered before the game freezes registries.
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModRecipes.RECIPE_TYPES.register(modEventBus);
        ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);
        ModLoot.LOOT_FUNCTIONS.register(modEventBus);
        ModLoot.LOOT_MODIFIERS.register(modEventBus);
        ModWorldgen.STRUCTURE_PLACEMENTS.register(modEventBus);
        ModWorldgen.BIOME_SOURCES.register(modEventBus);
        ModWorldgen.DENSITY_FUNCTIONS.register(modEventBus);
        ModWorldgen.PLACEMENT_MODIFIERS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        // Mod bus events (JSON registries, networking, creative tabs, entity attributes, datagen).
        modEventBus.addListener(ModDatapackRegistries::register);
        modEventBus.addListener(ModNetwork::register);
        modEventBus.addListener(ModCreativeTabs::addToVanillaTabs);
        modEventBus.addListener(ModEntities::registerAttributes);
        modEventBus.addListener(ModDataGenerators::gatherData);

        // Game bus events (gameplay: kills, ticks, item attributes...).
        // First: it brings old saved data up to date before the other systems read it.
        SaveMigrations.register(NeoForge.EVENT_BUS);
        EquipmentEvents.register(NeoForge.EVENT_BUS);
        ProgressionEvents.register(NeoForge.EVENT_BUS);
        XpRateService.register(NeoForge.EVENT_BUS);
        SkillEvents.register(NeoForge.EVENT_BUS);
        TraitEvents.register(NeoForge.EVENT_BUS);
        MobLevelEvents.register(NeoForge.EVENT_BUS);
        NpcLevels.register(NeoForge.EVENT_BUS);
        StunService.register(NeoForge.EVENT_BUS);
        LootEvents.register(NeoForge.EVENT_BUS);
        // Our own domain events (event/) have their subscribers on the same game bus.
        GoalTracker.register(NeoForge.EVENT_BUS);
        QuestDeadlines.register(NeoForge.EVENT_BUS);
        WalletEvents.register(NeoForge.EVENT_BUS);
        CoachService.register(NeoForge.EVENT_BUS);
        WeightService.register(NeoForge.EVENT_BUS);
        ContentValidationEvents.register(NeoForge.EVENT_BUS);
        NotificationEvents.register(NeoForge.EVENT_BUS);
        DebugCommands.register(NeoForge.EVENT_BUS);
        StartingVillage.register(NeoForge.EVENT_BUS);
        AltamiraProtection.register(NeoForge.EVENT_BUS);
        ApprenticeAssignments.register(NeoForge.EVENT_BUS);
        ApprenticeRoster.register(NeoForge.EVENT_BUS);
        Illumination.register(NeoForge.EVENT_BUS);
        Observation.register(NeoForge.EVENT_BUS);
        NunoBetrayal.register(NeoForge.EVENT_BUS);
        EibarStory.register(NeoForge.EVENT_BUS);
        VelascoContest.register(NeoForge.EVENT_BUS);
        LegendTitle.register(NeoForge.EVENT_BUS);
        MaterialFinds.register(NeoForge.EVENT_BUS);
        VrHelmetService.register(NeoForge.EVENT_BUS);
        StarterVrHelmet.register(NeoForge.EVENT_BUS);
        WorldSeparation.register(NeoForge.EVENT_BUS);
        Characters.register(NeoForge.EVENT_BUS);
        TerminalService.register(NeoForge.EVENT_BUS);
        FatigueService.register(NeoForge.EVENT_BUS);
        FatigueRestraint.register(NeoForge.EVENT_BUS);
        MitofusOnlyEvents.register(NeoForge.EVENT_BUS);
        LogoutService.register(NeoForge.EVENT_BUS);
        MitofusDeathService.register(NeoForge.EVENT_BUS);

        Config.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }

    /** Shorthand for {@code mitofus:<path>}. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
