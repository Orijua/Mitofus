package com.orijua.mitofus.feature.form.client;

import com.orijua.mitofus.core.client.HudVisibility;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.form.ElementTypeDefinition;
import com.orijua.mitofus.feature.form.FormDefinition;
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
    private static final int ROW_HEIGHT = 13;

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
        Shown shown = shown(forms, id, form.get(), player);
        int color = shown.color();
        float fraction = shown.fraction();

        Gui gui = minecraft.gui;
        int left = graphics.guiWidth() / 2 + 91 - WIDTH;
        int top = graphics.guiHeight() - gui.rightHeight;
        gui.rightHeight += ROW_HEIGHT;

        UiStyle.text(graphics, minecraft.font, shown.label(minecraft.font), left, top - 8, color);
        UiStyle.bar(graphics, left, top + 1, WIDTH, BAR_HEIGHT, fraction, color);
    }

    /** What is drawn, worked out once per change of the player's forms (they are replaced, not changed in place). */
    private static final class Shown {
        private final PlayerForms source;
        private final Component text;
        private final int color;
        private final float fraction;
        private Component label;

        private Shown(PlayerForms source, Component text, int color, float fraction) {
            this.source = source;
            this.text = text;
            this.color = color;
            this.fraction = fraction;
        }

        int color() {
            return color;
        }

        float fraction() {
            return fraction;
        }

        Component label(net.minecraft.client.gui.Font font) {
            if (label == null) {
                label = UiStyle.ellipsize(font, text, WIDTH);
            }
            return label;
        }
    }

    private static Shown cache;

    private static Shown shown(PlayerForms forms, net.minecraft.resources.ResourceLocation id, FormDefinition form, LocalPlayer player) {
        Shown current = cache;
        if (current != null && current.source == forms) {
            return current;
        }
        int xp = forms.xpOf(id);
        int level = FormRules.level(form.curve(), xp);
        int color = 0xFF000000 | Definitions.get(player.level(), ModDatapackRegistries.ELEMENT_TYPE, form.type())
                .map(ElementTypeDefinition::color).orElse(0xFFFFFF);
        current = new Shown(forms, Component.translatable("hud.mitofus.form", form.name(), level), color, FormRules.progress(form.curve(), xp));
        cache = current;
        return current;
    }
}
