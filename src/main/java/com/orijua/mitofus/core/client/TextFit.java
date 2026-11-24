package com.orijua.mitofus.core.client;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * The first {@code maxLines} of already wrapped {@code lines}; when some are left out the last shown one ends with "…"
     * (cut if needed to stay within {@code maxWidth}). The same list when it already fits.
     */
    public static List<String> clampLines(List<String> lines, int maxLines, int maxWidth, ToIntFunction<String> width) {
        if (maxLines <= 0) {
            return List.of();
        }
        if (lines.size() <= maxLines) {
            return lines;
        }
        List<String> shown = new ArrayList<>(lines.subList(0, maxLines));
        String last = shown.get(maxLines - 1);
        int end = last.length();
        while (end > 0 && width.applyAsInt(last.substring(0, end) + ELLIPSIS) > maxWidth) {
            end--;
        }
        if (end > 0 && Character.isHighSurrogate(last.charAt(end - 1))) {
            end--;
        }
        shown.set(maxLines - 1, last.substring(0, end).stripTrailing() + ELLIPSIS);
        return shown;
    }
}
