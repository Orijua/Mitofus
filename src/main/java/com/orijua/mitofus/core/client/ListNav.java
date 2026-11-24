package com.orijua.mitofus.core.client;

/** Moving a selection through a list with the keyboard. */
public final class ListNav {
    private ListNav() {
    }

    /**
     * The index after moving {@code delta} steps from {@code current} (-1 for no selection) in a list of {@code size}:
     * it stops at the ends; with nothing selected the first step lands on the first row (down) or the last (up).
     * -1 when the list is empty.
     */
    public static int move(int current, int delta, int size) {
        if (size <= 0) {
            return -1;
        }
        if (current < 0) {
            return delta >= 0 ? 0 : size - 1;
        }
        return Math.clamp((long) current + delta, 0, size - 1);
    }
}
