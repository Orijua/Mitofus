package com.orijua.mitofus.feature.progression.client;

import com.orijua.mitofus.core.client.PanelFit;

/** Where the pieces of the Status tab sit, in pixels from the top of the tab. Kept apart so it can be tested. */
final class StatusLayout {
    /** The line of mana and fatigue text, and the thin bars right under it. */
    static final int RESOURCE_TEXT_Y = 14;
    static final int RESOURCE_BAR_Y = 25;
    static final int RESOURCE_BAR_HEIGHT = 3;
    static final int POINTS_Y = 34;
    static final int STATS_TOP = 52;
    static final int MAX_STAT_ROW = 18;
    static final int MIN_STAT_ROW = 12;
    /** Space kept under the stats for the logout row. */
    static final int BOTTOM = 16;

    private StatusLayout() {
    }

    /** Height of each of the {@code count} stat rows: 18 when the tab has room, tighter (down to 12) when it does not. */
    static int statRow(int tabHeight, int count) {
        return PanelFit.spread(tabHeight - STATS_TOP - BOTTOM, count, MIN_STAT_ROW, MAX_STAT_ROW);
    }
}
