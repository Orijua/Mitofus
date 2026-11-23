package com.orijua.mitofus.feature.skill.client;

import com.orijua.mitofus.MitofusClient;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.talent.TalentEffects;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.skill.PlayerSkills;
import com.orijua.mitofus.feature.skill.SkillDefinition;
import com.orijua.mitofus.feature.skill.SkillService;
import com.orijua.mitofus.feature.skill.SlotRules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * Three skill slots right of the hotbar: bound key, skill icon (or initial), and a dark overlay while on cooldown.
 * A fourth slot, a little apart, shows while the held weapon has its own skill (Bisection on a Legendary Guadalete).
 */
public final class SkillSlotsHudLayer implements LayeredDraw.Layer {
    private static final int SIZE = 20;
    private static final int GAP = 2;
    /** Extra space before the weapon-skill slot, so it reads as a separate thing. */
    private static final int WEAPON_GAP = 6;
    /** Empty slots are drawn as a fainter Mitofus frame. */
    private static final float EMPTY_ALPHA = 0.45f;
    private static final int COOLDOWN = 0xB0101828;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()) {
            return;
        }
        PlayerSkills skills = player.getData(ModAttachments.PLAYER_SKILLS);
        int x = graphics.guiWidth() / 2 + 91 + 8;
        int y = graphics.guiHeight() - SIZE - 1;
        if (ClientCombat.active()) {
            renderCombatPage(graphics, minecraft, player, skills);
            return;
        }

        for (int slot = 0; slot < PlayerSkills.SLOT_COUNT; slot++) {
            drawSlot(graphics, minecraft, player, skills, skills.slot(slot),
                    MitofusClient.SKILL_KEYS.get(slot).getTranslatedKeyMessage(), x + slot * (SIZE + GAP), y);
        }
        Optional<ResourceLocation> weaponSkill = SkillService.weaponSkill(player.getMainHandItem());
        if (weaponSkill.isPresent()) {
            int left = x + PlayerSkills.SLOT_COUNT * (SIZE + GAP) + WEAPON_GAP;
            drawSlot(graphics, minecraft, player, skills, weaponSkill, MitofusClient.WEAPON_SKILL.getTranslatedKeyMessage(), left, y);
        }
    }

    /**
     * The 9 slots of the current page where the hotbar normally is (the hotbar is hidden meanwhile, see
     * {@code MitofusClient}): numbered like the keys that cast them, an iron sword to the left marks the mode,
     * the page is above and locked slots show empty.
     */
    private static void renderCombatPage(GuiGraphics graphics, Minecraft minecraft, LocalPlayer player, PlayerSkills skills) {
        int level = ProgressionService.get(player).level();
        int page = ClientCombat.pageFor(level);
        int totalWidth = SlotRules.PAGE_SIZE * SIZE;
        int left = (graphics.guiWidth() - totalWidth) / 2;
        int y = graphics.guiHeight() - SIZE - 1;
        graphics.renderItem(new ItemStack(Items.IRON_SWORD), left - 20, y + 2);
        for (int i = 0; i < SlotRules.PAGE_SIZE; i++) {
            int slot = page * SlotRules.PAGE_SIZE + i;
            boolean open = SlotRules.isOpen(level, slot);
            Optional<ResourceLocation> id = open ? skills.slot(slot) : Optional.empty();
            drawSlot(graphics, minecraft, player, skills, id, Component.literal(Integer.toString(i + 1)), left + i * SIZE, y, true);
        }
        if (SlotRules.pages(level) > 1) {
            Component label = Component.translatable("screen.mitofus.skill_page", page + 1, SlotRules.pages(level));
            graphics.drawCenteredString(minecraft.font, label, graphics.guiWidth() / 2, y - 12, UiStyle.HEADING);
        }
    }

    private static void drawSlot(GuiGraphics graphics, Minecraft minecraft, LocalPlayer player, PlayerSkills skills,
                                 Optional<ResourceLocation> id, Component key, int left, int y) {
        drawSlot(graphics, minecraft, player, skills, id, key, left, y, false);
    }

    /** {@code keyInside}: the key label goes in the slot's corner (combat mode, where there is no room above) instead of above it. */
    private static void drawSlot(GuiGraphics graphics, Minecraft minecraft, LocalPlayer player, PlayerSkills skills,
                                 Optional<ResourceLocation> id, Component key, int left, int y, boolean keyInside) {
        Optional<SkillDefinition> skill = id.flatMap(s -> Definitions.get(player.level(), ModDatapackRegistries.SKILL, s));

        UiStyle.frame(graphics, left, y, SIZE, SIZE, skill.isPresent() ? 1.0f : EMPTY_ALPHA);
        skill.ifPresent(def -> {
            ItemStack icon = def.iconStack();
            if (icon.isEmpty()) {
                String name = def.name().getString();
                graphics.drawCenteredString(minecraft.font, name.isEmpty() ? "?" : name.substring(0, 1), left + SIZE / 2, y + 6, UiStyle.TEXT);
            } else {
                graphics.renderItem(icon, left + 2, y + 2);
            }
            long remaining = skills.cooldownRemaining(id.get(), player.level().getGameTime());
            int cooldown = TalentEffects.tuning(player, id.get()).cooldownTicks(def.cooldownTicks());
            if (remaining > 0 && cooldown > 0) {
                int height = Math.min(SIZE, Math.round(SIZE * remaining / (float) cooldown));
                // Items are drawn at a higher depth; lift the overlay above them.
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 200);
                graphics.fill(left, y + SIZE - height, left + SIZE, y + SIZE, COOLDOWN);
                graphics.pose().popPose();
            }
        });
        if (keyInside) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 250);
            graphics.drawString(minecraft.font, key, left + 3, y + 2, UiStyle.HEADING, true);
            graphics.pose().popPose();
        } else {
            graphics.drawCenteredString(minecraft.font, key, left + SIZE / 2, y - 9, UiStyle.HEADING);
        }
    }
}
