package com.orijua.mitofus.compat.jei;

/**
 * Colours for text on JEI's pages. JEI draws on vanilla's light grey panel, where the mod's own colours (UiStyle, made
 * for the dark Mitofus windows) would not read, so these are vanilla's dark grey and a dark gold.
 */
final class JeiStyle {
    /** Vanilla's container label grey. */
    static final int TEXT = 0xFF404040;
    /** A gold that reads on light grey, for what the player must not miss. */
    static final int HIGHLIGHT = 0xFF9A6A00;

    private JeiStyle() {
    }
}
