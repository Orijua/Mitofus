package com.orijua.mitofus.feature.form.client;

import com.orijua.mitofus.core.client.HudVisibility;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.form.ElementTypeDefinition;
import com.orijua.mitofus.feature.form.FormRules;
import com.orijua.mitofus.feature.form.PlayerForms;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The worn form on the right of the bars: its name and form level in the color of its type, and a thin bar with the
 * progress to its next level. Nothing is drawn while no form is worn. Takes the next right-side row ({@link Gui#rightHeight}).
 */
public final class FormHudLayer implements LayeredDraw.Layer {
    private static final int WIDTH = 81;
    private static final int BAR_HEIGHT = 2;
    private static final int ROW_HEIGHT = 17;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!HudVisibility.showSurvivalBars(minecraft)) {
            return;
        }
        PlayerForms forms = player.getData(ModAttachments.PLAYER_FORMS);
        if (forms.equipped().isEmpty()) {
            return;
        }
        var id = forms.equipped().get();
        var form = Definitions.get(player.level(), ModDatapackRegistries.FORM, id);
        if (form.isEmpty()) {
            return;
        }
        int xp = forms.xpOf(id);
        int level = FormRules.level(form.get().curve(), xp);
        float fraction = level >= FormRules.MAX_LEVEL ? 1f
                : (xp - FormRules.usesToReach(form.get().curve(), level)) / (float) FormRules.usesToAdvance(form.get().curve(), level);
        int color = 0xFF000000 | Definitions.get(player.level(), ModDatapackRegistries.ELEMENT_TYPE, form.get().type())
                .map(ElementTypeDefinition::color).orElse(0xFFFFFF);

        Gui gui = minecraft.gui;
        int left = graphics.guiWidth() / 2 + 91 - WIDTH;
        int top = graphics.guiHeight() - gui.rightHeight;
        gui.rightHeight += ROW_HEIGHT;

        Component label = Component.translatable("hud.mitofus.form", form.get().name(), level);
        UiStyle.text(graphics, minecraft.font, UiStyle.ellipsize(minecraft.font, label, WIDTH - ElementIcons.SIZE - 2), left, top - 8, color);
        ElementIcons.draw(graphics, form.get().type(), left + WIDTH - ElementIcons.SIZE, top - ElementIcons.SIZE + 3);
        UiStyle.bar(graphics, left, top + 1, WIDTH, BAR_HEIGHT, fraction, color);
    }
}
