package com.orijua.mitofus.core.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** What a list shows when it has nothing to list, or when loading it failed: a centered, wrapped message. */
public final class MitofusEmptyState {
    public enum Kind {
        EMPTY(UiStyle.MUTED),
        ERROR(UiStyle.BAD);

        private final int color;

        Kind(int color) {
            this.color = color;
        }

        public int color() {
            return color;
        }
    }

    private MitofusEmptyState() {
    }

    /** The y of the first line so that {@code lines} lines are centered in a box of the given height. */
    public static int firstLineY(int boxTop, int boxHeight, int lines) {
        return boxTop + Math.max(0, (boxHeight - lines * UiStyle.LINE) / 2);
    }

    public static void draw(GuiGraphics graphics, Font font, int x, int y, int width, int height, Component message, Kind kind) {
        List<FormattedCharSequence> lines = font.split(message, width);
        int lineY = firstLineY(y, height, lines.size());
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x + (width - font.width(line)) / 2, lineY, kind.color(), true);
            lineY += UiStyle.LINE;
        }
    }
}
