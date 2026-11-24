package com.orijua.mitofus.core.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The common list row of every Mitofus window: a card with a short title and one muted summary line. Anything
 * longer goes in the hover (the caller draws it with {@code renderComponentTooltip} when {@link #contains}).
 */
public final class MitofusRow {
    private static final int PADDING = 3;
    private static final int TEXT_OFFSET = MitofusCard.STRIPE + 4;

    private MitofusRow() {
    }

    /** Height of a row: a title line, a summary line and the padding. */
    public static int height() {
        return 2 * UiStyle.LINE + 2 * PADDING - 4;
    }

    public static boolean contains(int x, int y, int width, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height();
    }

    /** Draws the row and returns whether the mouse is over it. */
    public static boolean draw(GuiGraphics graphics, Font font, int x, int y, int width, int accent, Component title,
                               Component summary, boolean dim, boolean selected, int mouseX, int mouseY) {
        boolean hovered = contains(x, y, width, mouseX, mouseY);
        MitofusCard.draw(graphics, x, y, width, height(), accent, MitofusCard.state(hovered, dim, selected));
        int textWidth = width - TEXT_OFFSET - PADDING;
        UiStyle.text(graphics, font, UiStyle.ellipsize(font, title, textWidth), x + TEXT_OFFSET, y + PADDING, UiStyle.TEXT);
        UiStyle.text(graphics, font, UiStyle.ellipsize(font, summary, textWidth), x + TEXT_OFFSET,
                y + PADDING + UiStyle.LINE - 2, UiStyle.MUTED);
        return hovered;
    }
}
