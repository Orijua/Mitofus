package com.orijua.mitofus.core.client;

/**
 * How big a window may be on the current screen. Windows are designed for a wanted size and shrink (their lists
 * scroll) when the GUI scale leaves less room, instead of being cut off by the screen edge.
 */
public final class PanelFit {
    /** Free space kept between a window and the screen edge, on each side. */
    public static final int MARGIN = 4;
    public static final int MIN_WIDTH = 120;
    public static final int MIN_HEIGHT = 90;

    public record Size(int width, int height) {
    }

    private PanelFit() {
    }

    /** The wanted size, shrunk to the screen minus the margins, but never under the minimum. */
    public static Size fit(int wantedWidth, int wantedHeight, int screenWidth, int screenHeight) {
        int width = Math.max(MIN_WIDTH, Math.min(wantedWidth, screenWidth - 2 * MARGIN));
        int height = Math.max(MIN_HEIGHT, Math.min(wantedHeight, screenHeight - 2 * MARGIN));
        return new Size(width, height);
    }

    public static boolean needsScroll(int contentHeight, int availableHeight) {
        return contentHeight > availableHeight;
    }
}
