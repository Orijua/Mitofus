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

import com.mojang.blaze3d.platform.InputConstants;
import com.orijua.mitofus.core.client.HudVisibility;
import com.orijua.mitofus.core.client.MitofusTabs;
import com.orijua.mitofus.core.client.MitofusTooltips;
import com.orijua.mitofus.feature.npc.client.MitofusShop;
import com.orijua.mitofus.core.registry.ModEntities;
import com.orijua.mitofus.core.registry.ModMenus;
import com.orijua.mitofus.feature.vrhelmet.ClientVrHelmet;
import com.orijua.mitofus.feature.vrhelmet.client.VrHelmetConnectingLayer;
import com.orijua.mitofus.feature.mob.client.BoneLordRenderer;
import com.orijua.mitofus.feature.mob.client.MobLevelNameTags;
import com.orijua.mitofus.feature.npc.client.NpcNameColors;
import com.orijua.mitofus.feature.notification.ClientNotifications;
import com.orijua.mitofus.feature.notification.client.SystemNotificationHudLayer;
import com.orijua.mitofus.feature.npc.client.NpcRenderer;
import com.orijua.mitofus.feature.sancho.client.NunoRenderer;
import com.orijua.mitofus.feature.progression.client.XpHudLayer;
import com.orijua.mitofus.feature.quest.client.QuestTrackerHudLayer;
import com.orijua.mitofus.feature.skill.PlayerSkills;
import com.orijua.mitofus.feature.skill.SkillService;
import com.orijua.mitofus.feature.skill.UseSkillPayload;
import com.orijua.mitofus.feature.skill.client.ManaHudLayer;
import com.orijua.mitofus.feature.skill.client.SkillSlotsHudLayer;
import com.orijua.mitofus.feature.smelting.client.SmelterScreen;
import com.orijua.mitofus.feature.smithing.client.SmithingAnvilScreen;
import com.orijua.mitofus.feature.eibar.client.MenciaRenderer;
import com.orijua.mitofus.feature.eibar.client.LevanteThugRenderer;
import com.orijua.mitofus.core.config.ClientConfig;
import com.orijua.mitofus.feature.fatigue.ClientFatigue;
import com.orijua.mitofus.feature.fatigue.FatigueRestraint;
import com.orijua.mitofus.feature.fatigue.client.FatigueDebugLayer;
import com.orijua.mitofus.feature.fatigue.client.FatigueGraceLayer;
import com.orijua.mitofus.feature.fatigue.client.FatigueHudLayer;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.LayeredDraw;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Client-only entry point: never loaded on a dedicated server, so it may use client classes freely. */
@Mod(value = Mitofus.MODID, dist = Dist.CLIENT)
public class MitofusClient {
    private static final String KEY_CATEGORY = "key.categories.mitofus";

    public static final KeyMapping OPEN_STATUS = key("status", GLFW.GLFW_KEY_K);
    /** One key per skill slot; the HUD shows their bound letters. */
    public static final List<KeyMapping> SKILL_KEYS = List.of(
            key("skill_1", GLFW.GLFW_KEY_Z),
            key("skill_2", GLFW.GLFW_KEY_X),
            key("skill_3", GLFW.GLFW_KEY_C));
    /** Casts the held weapon's own skill (Bisection on a Legendary Guadalete); its HUD slot only shows while one is held. */
    public static final KeyMapping WEAPON_SKILL = key("weapon_skill", GLFW.GLFW_KEY_V);
    /** Toggles the combat mode: the number keys cast the 9 slots of the current page instead of choosing the hotbar. */
    public static final KeyMapping COMBAT_MODE = key("combat_mode", GLFW.GLFW_KEY_R);
    /** Changes the page of skill slots while in combat mode (Tab by default, as decided; it shares the key with the player list). */
    public static final KeyMapping NEXT_PAGE = key("next_page", GLFW.GLFW_KEY_TAB);
    /** Cycles how the energy bar shows (auto, always, hidden). Unbound by default so it does not take anyone's key; works in both worlds. */
    public static final KeyMapping FATIGUE_DISPLAY = key("fatigue_display", GLFW.GLFW_KEY_UNKNOWN);

    public MitofusClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, com.orijua.mitofus.core.config.ClientConfig.SPEC);
        // The "Config" button of the mod list: NeoForge's own screen, with the names and help from the lang files
        // (mitofus.configuration.<option> and .tooltip). Server options can be changed there inside a world.
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(MitofusClient::onRegisterKeys);
        modEventBus.addListener(MitofusClient::onRegisterScreens);
        modEventBus.addListener(MitofusClient::onRegisterGuiLayers);
        modEventBus.addListener(MitofusClient::onRegisterRenderers);
        NeoForge.EVENT_BUS.addListener(MitofusClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(MitofusClient::onClientTickPre);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> com.orijua.mitofus.feature.skill.client.ClientCombat.reset());
        // Combat mode swaps the hotbar for the skill row, drawn in the same place (SkillSlotsHudLayer).
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) -> {
            if (event.getName().equals(VanillaGuiLayers.HOTBAR) && com.orijua.mitofus.feature.skill.client.ClientCombat.active()
                    && HudVisibility.inMitofus(Minecraft.getInstance())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(com.orijua.mitofus.feature.fatigue.client.FatigueRestraintClient::onMovementInput);
        NeoForge.EVENT_BUS.addListener(com.orijua.mitofus.feature.fatigue.client.FatigueRestraintClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(MobLevelNameTags::onRenderNameTag);
        NeoForge.EVENT_BUS.addListener(NpcNameColors::onRenderNameTag);
        NeoForge.EVENT_BUS.addListener(MitofusTooltips::onTooltipColor);
        NeoForge.EVENT_BUS.addListener(MitofusShop::onScreenOpening);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientNotifications.QUEUE.clear());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> com.orijua.mitofus.feature.economy.ClientTerminal.clear());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientFatigue.debug(com.orijua.mitofus.feature.fatigue.FatigueDebugPayload.HIDDEN));
        // A "Connecting…" wait must not outlive the world it started in.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientVrHelmet.connecting(0, 0));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> ClientVrHelmet.connecting(0, 0));
    }

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key.mitofus." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, defaultKey, KEY_CATEGORY);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_STATUS);
        SKILL_KEYS.forEach(event::register);
        event.register(WEAPON_SKILL);
        event.register(COMBAT_MODE);
        event.register(NEXT_PAGE);
        event.register(FATIGUE_DISPLAY);
    }

    private static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SMITHING_ANVIL.get(), SmithingAnvilScreen::new);
        event.register(ModMenus.SMELTER.get(), SmelterScreen::new);
        event.register(ModMenus.TERMINAL.get(), com.orijua.mitofus.feature.economy.client.TerminalScreen::new);
        event.register(ModMenus.SMITH_SHOP.get(), com.orijua.mitofus.feature.npc.client.SmithShopScreen::new);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BLACKSMITH.get(), context -> new NpcRenderer<>(context, NpcRenderer.BLACKSMITH));
        event.registerEntityRenderer(ModEntities.VILLAGE_CHIEF.get(), context -> new NpcRenderer<>(context, NpcRenderer.VILLAGE_CHIEF));
        event.registerEntityRenderer(ModEntities.COACHMAN.get(), context -> new NpcRenderer<>(context, NpcRenderer.COACHMAN));
        event.registerEntityRenderer(ModEntities.STOREKEEPER.get(), context -> new NpcRenderer<>(context, NpcRenderer.STOREKEEPER));
        event.registerEntityRenderer(ModEntities.BONE_LORD.get(), BoneLordRenderer::new);
        event.registerEntityRenderer(ModEntities.APPRENTICE_SMITH.get(), context -> new NpcRenderer<>(context, NpcRenderer.BLACKSMITH));
        event.registerEntityRenderer(ModEntities.CONDE_NUNO.get(), context -> new NunoRenderer<>(context, NunoRenderer.NUNO));
        event.registerEntityRenderer(ModEntities.MESNADERO.get(), context -> new NunoRenderer<>(context, NunoRenderer.KNIGHT));
        event.registerEntityRenderer(ModEntities.VELASCO.get(), context -> new NpcRenderer<>(context, NpcRenderer.VELASCO));
        event.registerEntityRenderer(ModEntities.LEVANTE_THUG.get(), LevanteThugRenderer::new);
        event.registerEntityRenderer(ModEntities.MENCIA.get(), MenciaRenderer::new);
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL, Mitofus.id("mana"), mitofusOnly(new ManaHudLayer()));
        // The energy bar also shows in the real world, where it refills.
        event.registerAbove(Mitofus.id("mana"), Mitofus.id("fatigue"), new FatigueHudLayer());
        event.registerAbove(Mitofus.id("fatigue"), Mitofus.id("xp"), mitofusOnly(new XpHudLayer()));
        event.registerAbove(Mitofus.id("xp"), Mitofus.id("fatigue_rested"), FatigueHudLayer::renderRestedLayer);
        event.registerAboveAll(Mitofus.id("fatigue_debug"), new FatigueDebugLayer());
        event.registerAbove(VanillaGuiLayers.HOTBAR, Mitofus.id("skill_slots"), mitofusOnly(new SkillSlotsHudLayer()));
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, Mitofus.id("quest_tracker"), mitofusOnly(new QuestTrackerHudLayer()));
        event.registerAbove(Mitofus.id("quest_tracker"), Mitofus.id("system_notification"), mitofusOnly(new SystemNotificationHudLayer()));
        // A layer can only be ordered against one already registered: this one must come after system_notification.
        event.registerAbove(Mitofus.id("system_notification"), Mitofus.id("fatigue_grace"), new FatigueGraceLayer());
        event.registerAboveAll(Mitofus.id("vrhelmet_connecting"), new VrHelmetConnectingLayer());
    }

    /** Draws the layer only while the player is in Mitofus. */
    private static LayeredDraw.Layer mitofusOnly(LayeredDraw.Layer layer) {
        return (graphics, deltaTracker) -> {
            if (HudVisibility.inMitofus(Minecraft.getInstance())) {
                layer.render(graphics, deltaTracker);
            }
        };
    }

    /**
     * Before the game reads the keys: in combat mode the number keys are the skill slots, so their clicks are taken here
     * and the hotbar never sees them.
     */
    private static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !com.orijua.mitofus.feature.skill.client.ClientCombat.active()) {
            return;
        }
        if (!HudVisibility.inMitofus(minecraft)) {
            com.orijua.mitofus.feature.skill.client.ClientCombat.reset();
            return;
        }
        int page = com.orijua.mitofus.feature.skill.client.ClientCombat.pageFor(
                com.orijua.mitofus.feature.progression.ProgressionService.get(minecraft.player).level());
        for (int i = 0; i < com.orijua.mitofus.feature.skill.SlotRules.PAGE_SIZE; i++) {
            while (minecraft.options.keyHotbarSlots[i].consumeClick()) {
                PacketDistributor.sendToServer(new UseSkillPayload(page * com.orijua.mitofus.feature.skill.SlotRules.PAGE_SIZE + i));
            }
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        // Works in both worlds: the energy also shows in the real world.
        while (FATIGUE_DISPLAY.consumeClick()) {
            ClientFatigue.Display next = ClientConfig.fatigueDisplay().next();
            ClientConfig.setFatigueDisplay(next);
            minecraft.player.displayClientMessage(Component.translatable("message.mitofus.fatigue_display." + next.name().toLowerCase(java.util.Locale.ROOT)), true);
        }
        if (!HudVisibility.inMitofus(minecraft)) {
            // In the real world the keys do nothing and say nothing: empty their clicks.
            while (OPEN_STATUS.consumeClick()) {
                // nothing
            }
            for (KeyMapping skillKey : SKILL_KEYS) {
                while (skillKey.consumeClick()) {
                    // nothing
                }
            }
            while (WEAPON_SKILL.consumeClick()) {
                // nothing
            }
            while (COMBAT_MODE.consumeClick()) {
                // nothing
            }
            while (NEXT_PAGE.consumeClick()) {
                // nothing
            }
            return;
        }
        while (OPEN_STATUS.consumeClick()) {
            // Held still, the screen would be closed in the same tick: do not open it.
            if (!FatigueRestraint.isRestrained(minecraft.player)) {
                minecraft.setScreen(MitofusTabs.createScreen());
            }
        }
        for (int slot = 0; slot < PlayerSkills.SLOT_COUNT; slot++) {
            while (SKILL_KEYS.get(slot).consumeClick()) {
                PacketDistributor.sendToServer(new UseSkillPayload(slot));
            }
        }
        while (WEAPON_SKILL.consumeClick()) {
            PacketDistributor.sendToServer(new UseSkillPayload(SkillService.WEAPON_SLOT));
        }
        while (COMBAT_MODE.consumeClick()) {
            com.orijua.mitofus.feature.skill.client.ClientCombat.toggle();
        }
        while (NEXT_PAGE.consumeClick()) {
            if (com.orijua.mitofus.feature.skill.client.ClientCombat.active()) {
                int level = com.orijua.mitofus.feature.progression.ProgressionService.get(minecraft.player).level();
                // Shift + the page key goes back instead of forward.
                if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                    com.orijua.mitofus.feature.skill.client.ClientCombat.previousPage(level);
                } else {
                    com.orijua.mitofus.feature.skill.client.ClientCombat.nextPage(level);
                }
            }
        }
    }
}
