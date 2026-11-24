package com.orijua.mitofus.core.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** The inner card shared by every Mitofus list: a translucent fill with a colored stripe on the left. */
public final class MitofusCard {
    /** How a card looks: normal, under the mouse, dimmed (stale or hidden) or selected. */
    public enum State { NORMAL, HOVER, DIM, SELECTED }

    /** Width of the colored stripe on the left of a card. */
    public static final int STRIPE = 2;

    private MitofusCard() {
    }

    /** The state of a card; selected wins over hover, hover over dim, so the mouse over a dim card still lights it. */
    public static State state(boolean hovered, boolean dim, boolean selected) {
        if (selected) {
            return State.SELECTED;
        }
        if (hovered) {
            return State.HOVER;
        }
        return dim ? State.DIM : State.NORMAL;
    }

    public static int fill(State state) {
        return switch (state) {
            case NORMAL -> 0x30FFFFFF;
            case HOVER -> 0x50FFFFFF;
            case DIM -> 0x18FFFFFF;
            case SELECTED -> 0x50F4E2AA;
        };
    }

    /** A card of the given size with a stripe in {@code accent} (RGB; it is drawn opaque). */
    public static void draw(GuiGraphics graphics, int x, int y, int width, int height, int accent, State state) {
        graphics.fill(x, y, x + width, y + height, fill(state));
        graphics.fill(x, y, x + STRIPE, y + height, 0xFF000000 | accent);
    }

    /** An item in a slot frame from ({@code x}, {@code y}); just the frame when the stack is empty. */
    public static void icon(GuiGraphics graphics, ItemStack stack, int x, int y) {
        UiStyle.slot(graphics, x, y);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 1, y + 1);
        }
    }
}
