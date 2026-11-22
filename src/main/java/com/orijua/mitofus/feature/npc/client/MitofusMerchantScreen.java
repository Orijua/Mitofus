package com.orijua.mitofus.feature.npc.client;

import com.orijua.mitofus.core.client.MitofusCard;
import com.orijua.mitofus.core.client.MitofusStyledScreen;
import com.orijua.mitofus.core.client.UiStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * The vanilla trading window dressed as a Mitofus window: only the look changes. The menu, the packets and the buying
 * are vanilla's. The offer buttons stay (invisible, alpha 0) and each is painted as a {@link MitofusCard}; the items,
 * the discount strike, the red arrow of a sold-out offer, the scroller, the progress bar and the tooltips are drawn by
 * vanilla's {@code render}. What is lost: the cross over the result slot when the selected offer is sold out, because
 * it is drawn in vanilla's background from a private field.
 */
public class MitofusMerchantScreen extends MerchantScreen implements MitofusStyledScreen {
    /** Where vanilla puts the right part (payment and result slots), and the arrow zone inside it. */
    private static final int RIGHT_PART_X = 99;
    private static final int ARROW_X = RIGHT_PART_X + 83;
    private static final int ARROW_Y = 35;
    /** Vanilla language keys (they live in the game, not in the mod's lang files). */
    private static final String TITLE_KEY = "merchant.title";
    private static final String LEVEL_KEY = "merchant.level.";
    private static final String TRADES_KEY = "merchant.trades";

    private final List<Button> offerButtons = new ArrayList<>();

    public MitofusMerchantScreen(MerchantMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        offerButtons.clear();
        for (GuiEventListener child : children()) {
            if (child instanceof Button button) {
                // Vanilla paints a grey button; the card below replaces it.
                button.setAlpha(0f);
                offerButtons.add(button);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        UiStyle.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        graphics.fill(leftPos + RIGHT_PART_X, topPos + 16, leftPos + RIGHT_PART_X + 1, topPos + imageHeight - 6, UiStyle.LINE_SOFT);
        for (Button button : offerButtons) {
            if (button.visible) {
                MitofusCard.draw(graphics, button.getX(), button.getY(), button.getWidth(), button.getHeight(),
                        UiStyle.LINE_SOFT & 0xFFFFFF, MitofusCard.state(button.isHoveredOrFocused(), false, false));
            }
        }
        for (Slot slot : menu.slots) {
            UiStyle.slot(graphics, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
        renderArrow(graphics, leftPos + ARROW_X, topPos + ARROW_Y);
    }

    /** A plain arrow pointing right inside vanilla's 28x21 zone between the payment slots and the result. */
    private static void renderArrow(GuiGraphics graphics, int x, int y) {
        int middle = y + 10;
        graphics.fill(x + 4, middle - 1, x + 18, middle + 2, UiStyle.LINE_BRIGHT);
        for (int i = 0; i < 7; i++) {
            graphics.fill(x + 18 + i, middle - 6 + i, x + 19 + i, middle + 7 - i, UiStyle.LINE_BRIGHT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int level = menu.getTraderLevel();
        Component heading = title;
        if (level > 0 && level <= 5 && menu.showProgressBar()) {
            heading = Component.translatable(TITLE_KEY, title, Component.translatable(LEVEL_KEY + level));
        }
        graphics.drawString(font, heading, 49 + imageWidth / 2 - font.width(heading) / 2, 6, UiStyle.TEXT, true);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, UiStyle.MUTED, true);
        Component trades = Component.translatable(TRADES_KEY);
        graphics.drawString(font, trades, 5 - font.width(trades) / 2 + 48, 6, UiStyle.HEADING, true);
    }
}
