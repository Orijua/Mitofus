package com.orijua.mitofus.core.client;

import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.feature.equipment.AppraisalService;
import com.orijua.mitofus.feature.equipment.ItemRating;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Shared look of every Mitofus screen and HUD element, modeled on RPG system windows: a translucent
 * steel-blue panel, light-blue double lines on top and bottom, a crest above and white text with a shadow.
 * Change colors here, not in each screen.
 */
public final class UiStyle {
    public static final int TEXT = 0xFFFFFF;
    public static final int MUTED = 0xC4D3E6;
    public static final int HEADING = 0xD6EEFF;
    public static final int GOLD = 0xFFD700;
    public static final int GOOD = 0x6CFF7A;
    public static final int BAD = 0xFF6B6B;
    public static final int MANA = 0xFF3F8CFF;
    public static final int XP = 0xFF55CC55;
    public static final int COOLDOWN = 0xFFFFC23F;
    /** Energy (fatigue): lavender, distinct from mana, XP and warnings. */
    public static final int FATIGUE = 0xFFB48CFF;

    /**
     * The color of the name of a rated piece (its rating color, opaque); Normal's while its rating is hidden (unappraised);
     * {@link #MUTED} when it has no rating.
     */
    public static int ratingNameColor(ItemStack stack) {
        ItemRating rating = AppraisalService.visibleRating(stack).orElse(null);
        if (rating == null) {
            return stack.has(ModDataComponents.ITEM_RATING) ? 0xFF000000 | ItemRating.NORMAL.color() : MUTED;
        }
        return 0xFF000000 | rating.color();
    }

    /** Window background, lighter at the top like the reference window. */
    public static final int WINDOW_TOP = 0xD27D97B8;
    public static final int WINDOW_BOTTOM = 0xD23C5679;
    /** The bright outer line and the softer inner one of the top and bottom edges. */
    public static final int LINE_BRIGHT = 0xFFE6F4FF;
    public static final int LINE_SOFT = 0xFF9DC4EA;
    /** The system-sign panel: darker at the top and bottom edges, lit in the middle. */
    public static final int WINDOW_EDGE = 0xD25A7698;
    public static final int WINDOW_CENTER = 0xD27E9ABC;
    /** The rods above and below the panel: how far they stick out on each side, their height and their color. */
    public static final int ROD_OVERHANG = 3;
    public static final int ROD_HEIGHT = 3;
    public static final int ROD = 0xFF607DAC;
    /** Height of the small label above a title and of a title line in {@link #titleBlock}. */
    public static final int KICKER_HEIGHT = 7;
    public static final int TITLE_HEIGHT = 10;
    private static final int SIDE_LINE = 0x90D6ECFF;
    private static final int CREST_FILL = 0xFFCFE9FF;
    private static final int CREST_OUTLINE = 0xFF4F7CB0;

    /** The colors of a window: background (top and bottom), the bright and soft lines of the top and bottom edges, and the side lines. */
    public record Palette(int top, int bottom, int bright, int soft, int side) {
    }

    /** The Mitofus windows: steel blue with light-blue lines. */
    public static final Palette MITOFUS = new Palette(WINDOW_TOP, WINDOW_BOTTOM, LINE_BRIGHT, LINE_SOFT, SIDE_LINE);
    /** The real-world devices (terminal, phone): dark grey with cyan lines. */
    public static final Palette DEVICE = new Palette(0xE02A2F35, 0xE015181C, 0xFF4FE3EC, 0xFF2A9BA3, 0x904FE3EC);

    private static final int BAR_BORDER = 0xC09DC4EA;
    private static final int BAR_EMPTY = 0xC01B2A40;

    public static final int LINE = 12;

    /**
     * Half of the crest drawn above windows, the last column being the middle one; it is mirrored for the
     * right half. '#' is filled; the outline is added around it. A placeholder until a PNG replaces it.
     */
    private static final String[] CREST_HALF = {
            "................#",
            "...............##",
            "..###.........###",
            ".#...#.......####",
            "#..#..#.....#####",
            "#...#..#...##.###",
            ".#.....##.##..###",
            "..##....###...###",
            "....####..#..####",
            ".........#.######",
            "..........##..###",
            "............####.",
    };
    public static final int CREST_HEIGHT = CREST_HALF.length;

    private UiStyle() {
    }

    /** A Mitofus window with its crest on top. */
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        panel(graphics, x, y, width, height, 1.0f);
    }

    /**
     * A Mitofus window faded to {@code alpha} (0-1), for windows that fade in and out: a "system sign" with no side
     * lines, lit in the middle, a rod above and below and the crest on the top rod.
     */
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        int edge = withAlpha(WINDOW_EDGE, alpha);
        int center = withAlpha(WINDOW_CENTER, alpha);
        int middle = y + height / 2;
        graphics.fillGradient(x, y, x + width, middle, edge, center);
        graphics.fillGradient(x, middle, x + width, y + height, center, edge);
        int centerX = x + width / 2;
        int gap = CREST_HALF[0].length() - 1 + 2;
        rod(graphics, x, y - 1, width, alpha, centerX - gap, centerX + gap + 1);
        rod(graphics, x, y + height - 2, width, alpha, 0, 0);
        // Sits on the top rod, overlapping the panel by 2 pixels so it looks attached but clears the content below.
        crest(graphics, centerX, y - CREST_HEIGHT + 2, alpha);
    }

    /**
     * A rod of {@link #ROD_HEIGHT} pixels from {@code top}, wider than the panel by the overhang; the span from
     * {@code gapFrom} to {@code gapTo} stays empty (no gap when {@code gapTo <= gapFrom}).
     */
    private static void rod(GuiGraphics graphics, int panelX, int top, int panelWidth, float alpha, int gapFrom, int gapTo) {
        int from = panelX - ROD_OVERHANG;
        int to = panelX + panelWidth + ROD_OVERHANG;
        int bright = withAlpha(LINE_BRIGHT, alpha);
        int body = withAlpha(ROD, alpha);
        for (int row = 0; row < ROD_HEIGHT; row++) {
            int color = row == 0 ? bright : body;
            if (gapTo > gapFrom) {
                graphics.fill(from, top + row, gapFrom, top + row + 1, color);
                graphics.fill(gapTo, top + row, to, top + row + 1, color);
            } else {
                graphics.fill(from, top + row, to, top + row + 1, color);
            }
        }
    }

    /** The window without the crest, for small boxes such as buttons. */
    public static void frame(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        frame(graphics, x, y, width, height, alpha, MITOFUS);
    }

    /** A window of a device (no crest), in the {@link #DEVICE} colors. */
    public static void devicePanel(GuiGraphics graphics, int x, int y, int width, int height) {
        frame(graphics, x, y, width, height, 1.0f, DEVICE);
    }

    /** A window without the crest in the colors of {@code palette}. */
    public static void frame(GuiGraphics graphics, int x, int y, int width, int height, float alpha, Palette palette) {
        graphics.fillGradient(x, y, x + width, y + height, withAlpha(palette.top(), alpha), withAlpha(palette.bottom(), alpha));
        graphics.fill(x, y, x + 1, y + height, withAlpha(palette.side(), alpha));
        graphics.fill(x + width - 1, y, x + width, y + height, withAlpha(palette.side(), alpha));
        int bright = withAlpha(palette.bright(), alpha);
        int soft = withAlpha(palette.soft(), alpha);
        graphics.fill(x, y, x + width, y + 1, bright);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 3, soft);
        graphics.fill(x, y + height - 1, x + width, y + height, bright);
        graphics.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, soft);
    }

    /** The ornament centered on {@code centerX}, its top at {@code top}. */
    public static void crest(GuiGraphics graphics, int centerX, int top, float alpha) {
        int half = CREST_HALF[0].length() - 1;
        int fill = withAlpha(CREST_FILL, alpha);
        int outline = withAlpha(CREST_OUTLINE, alpha);
        for (int row = -1; row <= CREST_HEIGHT; row++) {
            for (int column = -half - 1; column <= half + 1; column++) {
                int x = centerX + column;
                int y = top + row;
                if (crestAt(row, column)) {
                    graphics.fill(x, y, x + 1, y + 1, fill);
                } else if (crestAt(row - 1, column) || crestAt(row + 1, column) || crestAt(row, column - 1) || crestAt(row, column + 1)) {
                    graphics.fill(x, y, x + 1, y + 1, outline);
                }
            }
        }
    }

    /** Whether the crest is filled at {@code row} and {@code column} (0 = the middle column). */
    private static boolean crestAt(int row, int column) {
        int half = CREST_HALF[0].length() - 1;
        if (row < 0 || row >= CREST_HEIGHT || Math.abs(column) > half) {
            return false;
        }
        return CREST_HALF[row].charAt(half - Math.abs(column)) == '#';
    }

    /**
     * {@code color} with its alpha scaled by {@code alpha}; RGB colors without alpha count as opaque.
     * Don't draw text below ~0.05: the font treats an alpha under 4 as fully opaque.
     */
    public static int withAlpha(int color, float alpha) {
        int base = (color >>> 24) == 0 ? 0xFF : color >>> 24;
        return (Math.round(base * Math.clamp(alpha, 0, 1)) << 24) | (color & 0xFFFFFF);
    }

    /** A Mitofus bar: light-blue 1px border, dark translucent track, filled left to right with a lit top edge. */
    public static void bar(GuiGraphics graphics, int x, int y, int width, int height, float fraction, int color) {
        bar(graphics, x, y, width, height, fraction, color, 1);
    }

    /** The same bar faded to {@code alpha} (0 to 1), for HUD parts that appear and disappear. */
    public static void bar(GuiGraphics graphics, int x, int y, int width, int height, float fraction, int color, float alpha) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, withAlpha(BAR_BORDER, alpha));
        graphics.fill(x, y, x + width, y + height, withAlpha(BAR_EMPTY, alpha));
        int filled = Math.round(width * Math.clamp(fraction, 0, 1));
        graphics.fillGradient(x, y, x + filled, y + height, withAlpha(lighter(color), alpha), withAlpha(color, alpha));
    }

    /** A 9x9 check box in the Mitofus style: soft border, dark translucent inside, a bright square when checked. */
    public static void checkbox(GuiGraphics graphics, int x, int y, boolean checked) {
        graphics.fill(x, y, x + 9, y + 9, LINE_SOFT);
        graphics.fill(x + 1, y + 1, x + 8, y + 8, withAlpha(0xFF1B2A40, 0.75f));
        if (checked) {
            graphics.fill(x + 2, y + 2, x + 7, y + 7, LINE_BRIGHT);
        }
    }

    /** {@code color} blended a third of the way to white, keeping its alpha. */
    private static int lighter(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (color & 0xFF000000) | ((r + (255 - r) / 3) << 16) | ((g + (255 - g) / 3) << 8) | (b + (255 - b) / 3);
    }

    public static void text(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color);
    }

    /** {@code text} cut to {@code maxWidth} with "…" at the end, or whole if it fits (the style is lost when cut). */
    public static Component ellipsize(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return Component.literal(font.substrByWidth(text, maxWidth - font.width("…")).getString() + "…");
    }

    /**
     * The head of a window: a small label (optional), the title in bold and a subtitle (optional), all centered on
     * {@code centerX} from {@code y}. Returns the y where the content can start.
     */
    public static int titleBlock(GuiGraphics graphics, Font font, @Nullable Component kicker, Component title,
                                 @Nullable Component subtitle, int subtitleColor, int centerX, int y) {
        int next = y;
        if (kicker != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(centerX, next, 0);
            graphics.pose().scale(0.75f, 0.75f, 1f);
            graphics.drawCenteredString(font, kicker, 0, 0, MUTED);
            graphics.pose().popPose();
            next += KICKER_HEIGHT;
        }
        graphics.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD), centerX, next, TEXT);
        next += TITLE_HEIGHT;
        if (subtitle != null) {
            graphics.drawCenteredString(font, subtitle, centerX, next, subtitleColor);
            next += TITLE_HEIGHT;
        }
        return next + 2;
    }

    /** A section heading: {@code text} on the left in {@code color} with a soft line under it across {@code width}. Returns the y below it. */
    public static int sectionHeading(GuiGraphics graphics, Font font, Component text, int x, int y, int width, int color) {
        graphics.drawString(font, text, x, y, color);
        graphics.fill(x, y + 10, x + width, y + 11, LINE_SOFT);
        return y + LINE + 2;
    }

    /** An 18x18 item slot frame from ({@code x}, {@code y}): soft 1px border, dark translucent inside. */
    public static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, LINE_SOFT);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, withAlpha(0xFF1B2A40, 0.55f));
    }

    public static void heading(GuiGraphics graphics, Font font, Component text, int x, int y) {
        graphics.drawString(font, text, x, y, HEADING);
    }
}
