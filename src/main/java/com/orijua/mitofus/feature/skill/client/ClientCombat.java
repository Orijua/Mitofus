package com.orijua.mitofus.feature.skill.client;

import com.orijua.mitofus.feature.skill.SlotRules;

/** Client-only state of the combat mode: while on, the number keys 1-9 cast the slots of the current page. */
public final class ClientCombat {
    private static boolean active;
    private static int page;

    private ClientCombat() {
    }

    public static boolean active() {
        return active;
    }

    /** The current page, 0-based. */
    public static int page() {
        return page;
    }

    public static void toggle() {
        active = !active;
    }

    public static void reset() {
        active = false;
        page = 0;
    }

    /** Moves to the next page the player has, wrapping to the first; keeps the page valid if the level dropped. */
    public static void nextPage(int level) {
        page = (page + 1) % SlotRules.pages(level);
    }

    /** Moves to the previous page, wrapping to the last (Shift + the page key). */
    public static void previousPage(int level) {
        int pages = SlotRules.pages(level);
        page = (pageFor(level) + pages - 1) % pages;
    }

    /** The page clamped to the pages the player has now (a death can lower the level). */
    public static int pageFor(int level) {
        return Math.min(page, SlotRules.pages(level) - 1);
    }
}
