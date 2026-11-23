package com.orijua.mitofus.feature.talent.client;

/**
 * Where a talent tree is dragged to inside its window, with no Minecraft state. The tree is drawn clipped to the window and can be
 * moved with the mouse so every node can be brought into view; a short press without moving still counts as a click on a node.
 */
public final class TreePan {
    /** The distance in pixels a press may move and still be a click, not a drag. */
    public static final int CLICK_SLOP = 3;
    /** Pixels of margin kept between the tree's edge and the window's edge at the limits. */
    public static final int MARGIN = 6;

    private int panX;
    private int panY;
    private boolean pressed;
    private boolean dragged;
    private double pressX;
    private double pressY;

    public int x() {
        return panX;
    }

    public int y() {
        return panY;
    }

    public void reset() {
        panX = 0;
        panY = 0;
        pressed = false;
        dragged = false;
    }

    /** The mouse went down at ({@code x}, {@code y}) inside the window. */
    public void press(double x, double y) {
        pressed = true;
        dragged = false;
        pressX = x;
        pressY = y;
    }

    /** The mouse moved by ({@code dx}, {@code dy}) with the button held. */
    public void drag(double x, double y, double dx, double dy) {
        if (!pressed) {
            return;
        }
        if (!dragged && Math.abs(x - pressX) < CLICK_SLOP && Math.abs(y - pressY) < CLICK_SLOP) {
            return;
        }
        dragged = true;
        panX += (int) Math.round(dx);
        panY += (int) Math.round(dy);
    }

    /** The button went up. @return true when it was a click (the press never became a drag). */
    public boolean release() {
        boolean click = pressed && !dragged;
        pressed = false;
        dragged = false;
        return click;
    }

    /**
     * Keeps the tree reachable: whatever its size, its edges can be brought to the window's edges (less the margin) and no further, and a tree
     * that fits is moved inside. The bounds are where the tree is drawn with no pan.
     */
    public void clamp(int contentMinX, int contentMaxX, int contentMinY, int contentMaxY, int viewMinX, int viewMaxX, int viewMinY, int viewMaxY) {
        panX = limit(panX, contentMinX, contentMaxX, viewMinX, viewMaxX);
        panY = limit(panY, contentMinY, contentMaxY, viewMinY, viewMaxY);
    }

    static int limit(int pan, int contentMin, int contentMax, int viewMin, int viewMax) {
        int a = viewMax - MARGIN - contentMax;
        int b = viewMin + MARGIN - contentMin;
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), pan));
    }
}
