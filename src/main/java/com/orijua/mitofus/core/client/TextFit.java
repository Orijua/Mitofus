package com.orijua.mitofus.core.client;

import java.util.function.ToIntFunction;

/** Cuts text to a pixel width; the font is passed as a width function so the logic is testable without Minecraft. */
public final class TextFit {
    public static final String ELLIPSIS = "…";

    private TextFit() {
    }

    /** {@code text} if it fits, else its longest start that fits with "…" after it; empty if not even "…" fits. */
    public static String fit(String text, int maxWidth, ToIntFunction<String> width) {
        if (width.applyAsInt(text) <= maxWidth) {
            return text;
        }
        int room = maxWidth - width.applyAsInt(ELLIPSIS);
        if (room < 0) {
            return "";
        }
        int end = text.length();
        while (end > 0 && width.applyAsInt(text.substring(0, end)) > room) {
            end--;
        }
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + ELLIPSIS;
    }
}
