package com.orijua.mitofus.feature.skill;

/**
 * Pure rules of the skill slots (own design, see docs Fase5_decisiones_y_plan): pages of 9 slots. The first page starts
 * with 3 open slots and gains one every 10 player levels up to 9; a new page opens every 100 levels (5 pages at most)
 * and its 9 slots are open from the start.
 */
public final class SlotRules {
    public static final int PAGE_SIZE = 9;
    public static final int MAX_PAGES = 5;
    public static final int MAX_SLOTS = PAGE_SIZE * MAX_PAGES;
    public static final int STARTING_SLOTS = 3;
    public static final int LEVELS_PER_EXTRA_SLOT = 10;
    public static final int LEVELS_PER_PAGE = 100;

    private SlotRules() {
    }

    /** Pages a player of {@code level} has (1 to {@link #MAX_PAGES}). */
    public static int pages(int level) {
        return Math.min(MAX_PAGES, 1 + Math.max(0, level) / LEVELS_PER_PAGE);
    }

    /** Open slots of the first page (3 to 9). */
    public static int firstPageSlots(int level) {
        return Math.min(PAGE_SIZE, STARTING_SLOTS + Math.max(0, level) / LEVELS_PER_EXTRA_SLOT);
    }

    /** Whether slot {@code slot} (0 to {@link #MAX_SLOTS} - 1, page after page) is open for a player of {@code level}. */
    public static boolean isOpen(int level, int slot) {
        if (slot < 0 || slot >= MAX_SLOTS) {
            return false;
        }
        int page = slot / PAGE_SIZE;
        if (page >= pages(level)) {
            return false;
        }
        return page > 0 || slot < firstPageSlots(level);
    }

    /** Open slots of {@code page} (0-based), 0 for a page the player doesn't have yet. */
    public static int openOnPage(int level, int page) {
        if (page < 0 || page >= pages(level)) {
            return 0;
        }
        return page == 0 ? firstPageSlots(level) : PAGE_SIZE;
    }

    /** The slot number after {@code slot} that is open, wrapping to -1 ("no slot") after the last one. */
    public static int nextOpen(int level, int slot) {
        for (int next = slot + 1; next < MAX_SLOTS; next++) {
            if (isOpen(level, next)) {
                return next;
            }
        }
        return -1;
    }
}
