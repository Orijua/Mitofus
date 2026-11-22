package com.orijua.mitofus.feature.npc.client;

import com.orijua.mitofus.core.client.MitofusButton;
import com.orijua.mitofus.core.client.MitofusCard;
import com.orijua.mitofus.core.client.MitofusStyledScreen;
import com.orijua.mitofus.core.client.ScrollList;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.economy.Money;
import com.orijua.mitofus.feature.npc.SmithBuyPayload;
import com.orijua.mitofus.feature.npc.SmithShop;
import com.orijua.mitofus.feature.npc.SmithShopMenu;
import com.orijua.mitofus.feature.npc.SmithShopRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

/**
 * Smith's shop in Mitofus's style: a scrolling list of deals (what it costs, an arrow, what you get), the gold you carry and a
 * "Make the deal" button. It has no slots: the payment is counted and taken by the server over the whole inventory, so a
 * price above 64 works. The costs are written as numbers (the stack decoration stops at 99). The button follows the same
 * rules as the server ({@link SmithShopRules}), which checks everything again.
 */
public class SmithShopScreen extends AbstractContainerScreen<SmithShopMenu> implements MitofusStyledScreen {
    private static final int MARGIN = 10;
    private static final int ROWS = 6;
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 24;
    private static final int ICON = 18;

    private final ScrollList scroll = new ScrollList(ROWS);
    private int selected = -1;
    private Button deal;

    public SmithShopScreen(SmithShopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 260;
        this.imageHeight = 214;
    }

    @Override
    protected void init() {
        super.init();
        deal = addRenderableWidget(Button.builder(Component.translatable("screen.mitofus.smith_shop.trade"), pressed -> sendDeal())
                .bounds(leftPos + imageWidth - MARGIN - 110, topPos + imageHeight - 28, 110, 18)
                .build(builder -> new MitofusButton(builder)));
    }

    private MerchantOffers offers() {
        return menu.offers();
    }

    private Optional<MerchantOffer> selectedOffer() {
        return selected >= 0 && selected < offers().size() ? Optional.of(offers().get(selected)) : Optional.empty();
    }

    private static long carried(ItemCost cost) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? 0L : SmithShop.count(minecraft.player.getInventory(), cost);
    }

    private static long goldCarried() {
        return carried(new ItemCost(ModItems.GOLD_COIN.get(), 1));
    }

    private SmithShopRules.Check check(MerchantOffer offer) {
        Optional<ItemCost> costB = offer.getItemCostB();
        return SmithShopRules.check(offer.isOutOfStock(), carried(offer.getItemCostA()), offer.getItemCostA().count(),
                costB.map(SmithShopScreen::carried).orElse(0L), costB.map(ItemCost::count).orElse(0));
    }

    private void sendDeal() {
        selectedOffer().ifPresent(offer -> {
            if (check(offer) == SmithShopRules.Check.OK) {
                PacketDistributor.sendToServer(new SmithBuyPayload(menu.containerId, selected, offer.getResult().copy(),
                        offer.getItemCostA().count(), offer.getItemCostB().map(ItemCost::count).orElse(0)));
            }
        });
    }

    private int rowX() {
        return leftPos + MARGIN;
    }

    private int rowWidth() {
        return imageWidth - 2 * MARGIN - 6;
    }

    private int rowY(int visibleIndex) {
        return topPos + LIST_TOP + visibleIndex * ROW_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            scroll.clamp(offers().size());
            for (int i = scroll.first(); i < scroll.end(offers().size()); i++) {
                int y = rowY(i - scroll.first());
                if (mouseX >= rowX() && mouseX < rowX() + rowWidth() && mouseY >= y && mouseY < y + ROW_HEIGHT - 2) {
                    selected = i;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scroll.scroll(scrollY, offers().size())) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        UiStyle.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        scroll.clamp(offers().size());
        deal.active = selectedOffer().map(offer -> check(offer) == SmithShopRules.Check.OK).orElse(false);
        super.render(graphics, mouseX, mouseY, partialTick);
        UiStyle.heading(graphics, font, title, leftPos + MARGIN, topPos + 8);
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = scroll.first(); i < scroll.end(offers().size()); i++) {
            hovered = drawRow(graphics, offers().get(i), i, rowY(i - scroll.first()), mouseX, mouseY, hovered);
        }
        scroll.renderBar(graphics, leftPos + imageWidth - MARGIN - 3, topPos + LIST_TOP, ROWS * ROW_HEIGHT - 2, offers().size());
        int bottom = topPos + imageHeight - 28;
        UiStyle.text(graphics, font, Component.translatable("screen.mitofus.smith_shop.have", Money.format(goldCarried())),
                leftPos + MARGIN, bottom + 5, UiStyle.TEXT);
        menu.message().ifPresent(message -> UiStyle.text(graphics, font, UiStyle.ellipsize(font, message, imageWidth - 2 * MARGIN),
                leftPos + MARGIN, bottom - 14, UiStyle.MUTED));
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    /** One deal; returns the stack under the mouse in it, if any (else {@code hovered}, what was already found). */
    private ItemStack drawRow(GuiGraphics graphics, MerchantOffer offer, int index, int y, int mouseX, int mouseY, ItemStack hovered) {
        boolean soldOut = offer.isOutOfStock();
        boolean over = mouseX >= rowX() && mouseX < rowX() + rowWidth() && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
        MitofusCard.draw(graphics, rowX(), y, rowWidth(), ROW_HEIGHT - 2, UiStyle.LINE_SOFT & 0xFFFFFF,
                MitofusCard.state(over, soldOut, index == selected));
        int x = rowX() + 6;
        int iconY = y + 2;
        ItemStack result = offer.getResult();
        ItemStack found = hovered;
        ItemCost costA = offer.getItemCostA();
        found = drawCost(graphics, costA, x, iconY, mouseX, mouseY, found);
        x += ICON + font.width(Money.format(costA.count())) + 6;
        Optional<ItemCost> costB = offer.getItemCostB();
        if (costB.isPresent()) {
            graphics.drawString(font, "+", x, iconY + 5, UiStyle.MUTED, false);
            x += font.width("+") + 4;
            found = drawCost(graphics, costB.get(), x, iconY, mouseX, mouseY, found);
            x += ICON + font.width(Money.format(costB.get().count())) + 6;
        }
        graphics.drawString(font, "->", x, iconY + 5, UiStyle.LINE_BRIGHT, false);
        x += font.width("->") + 6;
        MitofusCard.icon(graphics, result, x, iconY);
        if (mouseX >= x && mouseX < x + ICON && mouseY >= iconY && mouseY < iconY + ICON) {
            found = result;
        }
        if (result.getCount() > 1) {
            graphics.drawString(font, Money.format(result.getCount()), x + ICON + 2, iconY + 5, UiStyle.TEXT, false);
        }
        if (soldOut) {
            Component label = Component.translatable("screen.mitofus.smith_shop.sold_out");
            graphics.drawString(font, label, rowX() + rowWidth() - font.width(label) - 4, iconY + 5, UiStyle.BAD, false);
        }
        return found;
    }

    /** An icon with its number beside it (the stack decoration would stop at 99). */
    private ItemStack drawCost(GuiGraphics graphics, ItemCost cost, int x, int y, int mouseX, int mouseY, ItemStack hovered) {
        ItemStack icon = cost.itemStack().copyWithCount(1);
        MitofusCard.icon(graphics, icon, x, y);
        graphics.drawString(font, Money.format(cost.count()), x + ICON + 2, y + 5, UiStyle.TEXT, false);
        if (mouseX >= x && mouseX < x + ICON && mouseY >= y && mouseY < y + ICON) {
            return icon;
        }
        return hovered;
    }
}
