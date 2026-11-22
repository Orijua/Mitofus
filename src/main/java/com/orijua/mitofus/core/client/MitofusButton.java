package com.orijua.mitofus.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.util.Mth;

/**
 * A button drawn as a small Mitofus window instead of vanilla's grey one.
 * Use it with {@code Button.builder(...).build(MitofusButton::new)}; for another palette (the terminal's device
 * colors) {@code build(builder -> new MitofusButton(builder, UiStyle.DEVICE))}.
 */
public final class MitofusButton extends Button {
    private static final int HOVER = 0x40FFFFFF;

    private final UiStyle.Palette palette;

    public MitofusButton(Builder builder) {
        this(builder, UiStyle.MITOFUS);
    }

    public MitofusButton(Builder builder, UiStyle.Palette palette) {
        super(builder);
        this.palette = palette;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiStyle.frame(graphics, getX(), getY(), getWidth(), getHeight(), active ? alpha : alpha * 0.55f, palette);
        if (active && isHoveredOrFocused()) {
            graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, UiStyle.withAlpha(HOVER, alpha));
        }
        int color = active ? UiStyle.TEXT : UiStyle.MUTED;
        renderString(graphics, Minecraft.getInstance().font, color | Mth.ceil(alpha * 255.0f) << 24);
    }
}
