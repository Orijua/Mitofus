package com.orijua.mitofus.feature.economy.client;

import com.orijua.mitofus.core.client.MitofusButton;
import com.orijua.mitofus.core.client.PanelFit;
import com.orijua.mitofus.core.client.ScrollList;
import com.orijua.mitofus.core.client.UiStyle;
import com.orijua.mitofus.core.config.VrHelmetBalance;
import com.orijua.mitofus.core.config.FatigueBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetText;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetTier;
import com.orijua.mitofus.feature.economy.ClientTerminal;
import com.orijua.mitofus.feature.economy.Money;
import com.orijua.mitofus.feature.economy.MoneyText;
import com.orijua.mitofus.feature.economy.OfferEntry;
import com.orijua.mitofus.feature.economy.TerminalBuyOfferPayload;
import com.orijua.mitofus.feature.economy.TerminalMenu;
import com.orijua.mitofus.feature.economy.TerminalState;
import com.orijua.mitofus.feature.economy.TradeRules;
import com.orijua.mitofus.feature.economy.Wallet;
import com.orijua.mitofus.feature.fatigue.client.FatigueHudLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

/**
 * The terminal's window, in the colors of a real-world device: the energy and the money on the account page, the daily
 * offer on the shop page and the helmet's session on the last page. It only shows what the server last sent
 * ({@link ClientTerminal}); every button sends an order and waits for the new state.
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
    private static final int MARGIN = 12;
    /** "No Mitofus character" blocks buying, so it is red in every page. */
    private static final int NO_CHARACTER_COLOR = UiStyle.BAD;
    private static final int LEFT_WIDTH = 230;
    private static final int PAGE_ACCOUNT = 0;
    private static final int PAGE_SHOP = 1;
    private static final int PAGE_HELMET = 2;
    private static final int OFFER_X = 12;
    private static final int WANTED_WIDTH = 400;
    private static final int WANTED_HEIGHT = 226;
    private static final int MIN_WIDTH = 300;
    private static final int MIN_HEIGHT = 150;
    /** Space under the offer list: the message line. */
    private static final int FOOTER = 20;
    private static final int PAGE_BUTTON_WIDTH = 64;
    private static final int PAGE_BUTTON_STEP = 68;
    private static final int OFFER_TOP = 48;
    private static final int OFFER_ROW = 22;
    private static final int MAX_OFFER_ROWS = 12;

    private int page = PAGE_ACCOUNT;
    private final ScrollList offerScroll = new ScrollList(1);
    private int offerRows = 1;
    private Button pageAccount;
    private Button pageShop;
    private Button pageHelmet;
    private Button[] rowButtons = new Button[0];

    public TerminalScreen(TerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WANTED_WIDTH;
        this.imageHeight = WANTED_HEIGHT;
    }

    @Override
    protected void init() {
        PanelFit.Size size = PanelFit.fit(WANTED_WIDTH, WANTED_HEIGHT, width, height, MIN_WIDTH, MIN_HEIGHT);
        imageWidth = size.width();
        imageHeight = size.height();
        offerRows = Math.min(MAX_OFFER_ROWS, PanelFit.rows(imageHeight - OFFER_TOP - FOOTER, OFFER_ROW));
        offerScroll.setVisibleRows(offerRows);
        rowButtons = new Button[offerRows];
        super.init();
        int pagesX = imageWidth - MARGIN - 2 * PAGE_BUTTON_STEP - PAGE_BUTTON_WIDTH;
        pageAccount = addRenderableWidget(button(Component.translatable("screen.mitofus.terminal.page.account"), pagesX, 6, PAGE_BUTTON_WIDTH, () -> page = PAGE_ACCOUNT));
        pageShop = addRenderableWidget(button(Component.translatable("screen.mitofus.terminal.page.shop"), pagesX + PAGE_BUTTON_STEP, 6, PAGE_BUTTON_WIDTH, () -> page = PAGE_SHOP));
        pageHelmet = addRenderableWidget(button(Component.translatable("screen.mitofus.terminal.page.vrhelmets"), pagesX + 2 * PAGE_BUTTON_STEP, 6, PAGE_BUTTON_WIDTH, () -> page = PAGE_HELMET));
        for (int i = 0; i < offerRows; i++) {
            int row = i;
            rowButtons[i] = addRenderableWidget(button(Component.translatable("screen.mitofus.terminal.shop.buy"),
                    OFFER_X + offerWidth() - 58, OFFER_TOP, 54, () -> sendBuyOffer(offerScroll.first() + row)));
            rowButtons[i].setHeight(16);
        }
    }

    private void sendBuyOffer(int position) {
        state().ifPresent(state -> {
            if (position >= 0 && position < state.offer().size() && !state.boughtToday().contains(position)) {
                OfferEntry entry = state.offer().get(position);
                PacketDistributor.sendToServer(new TerminalBuyOfferPayload(menu.containerId, entry.position(), entry.stack(), entry.price()));
            }
        });
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (page == PAGE_SHOP) {
            Optional<TerminalState> current = state();
            if (current.isPresent() && offerScroll.scroll(scrollY, current.get().offer().size())) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private Button button(Component label, int x, int y, int width, Runnable action) {
        return Button.builder(label, pressed -> action.run()).bounds(leftPos + x, topPos + y, width, 18)
                .build(builder -> new MitofusButton(builder, UiStyle.DEVICE));
    }

    private int offerWidth() {
        return imageWidth - 2 * OFFER_X;
    }

    private Optional<TerminalState> state() {
        return ClientTerminal.stateFor(menu.containerId);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        UiStyle.devicePanel(graphics, leftPos, topPos, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Optional<TerminalState> current = state();
        LocalPlayer player = minecraft.player;
        pageAccount.active = page != PAGE_ACCOUNT;
        pageShop.active = page != PAGE_SHOP;
        pageHelmet.active = page != PAGE_HELMET;
        for (Button rowButton : rowButtons) {
            rowButton.visible = false;
        }
        if (current.isEmpty() || player == null || minecraft.level == null) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        TerminalState state = current.get();
        // The day is the server's; until its new state arrives after the client's clock changed day, buying stays disabled.
        boolean stateIsOld = TradeRules.day(minecraft.level.getGameTime()) != state.today();
        if (page == PAGE_HELMET) {
            super.render(graphics, mouseX, mouseY, partialTick);
            drawHelmet(graphics, state);
            return;
        }
        if (page == PAGE_SHOP) {
            updateShop(state, player, stateIsOld);
            super.render(graphics, mouseX, mouseY, partialTick);
            drawShop(graphics, state, player, mouseX, mouseY);
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        drawAccount(graphics, state, player);
    }

    /** The buy button of each visible row of the offer. */
    private void updateShop(TerminalState state, LocalPlayer player, boolean stateIsOld) {
        long coins = Wallet.balance(player);
        List<OfferEntry> offer = state.offer();
        offerScroll.clamp(offer.size());
        for (int i = 0; i < offerRows; i++) {
            int position = offerScroll.first() + i;
            Button rowButton = rowButtons[i];
            boolean shown = position < offer.size() && !state.boughtToday().contains(position);
            rowButton.visible = shown;
            if (shown) {
                rowButton.setY(topPos + OFFER_TOP + i * OFFER_ROW + 3);
                rowButton.active = !stateIsOld && state.characterAvailable() && state.freeSlots() > 0 && coins >= offer.get(position).price();
            }
        }
    }

    private void drawHeader(GuiGraphics graphics) {
        UiStyle.heading(graphics, font, Component.translatable("screen.mitofus.terminal.title"), leftPos + MARGIN, topPos + 8);
        UiStyle.text(graphics, font, Component.translatable("screen.mitofus.terminal.subtitle"), leftPos + MARGIN, topPos + 20, UiStyle.MUTED);
    }

    private void drawHelmet(GuiGraphics graphics, TerminalState state) {
        int x = leftPos;
        int y = topPos;
        drawHeader(graphics);
        VrHelmetTier tier = state.sessionTier();
        FatigueBalance fatigue = FatigueBalance.current();
        VrHelmetBalance vrhelmet = VrHelmetBalance.current();
        UiStyle.heading(graphics, font, Component.translatable("screen.mitofus.status.vrhelmet_tier", tier.tierName()), x + MARGIN, y + 34);
        UiStyle.text(graphics, font, Component.translatable("screen.mitofus.terminal.vrhelmets.session", VrHelmetText.minutes(vrhelmet.sessionMinutes(tier, fatigue))),
                x + MARGIN, y + 46, UiStyle.TEXT);
        UiStyle.text(graphics, font, Component.translatable("screen.mitofus.terminal.vrhelmets.recovery", VrHelmetText.minutes(vrhelmet.recoveryMinutes(tier, fatigue))),
                x + MARGIN, y + 58, UiStyle.TEXT);
        int noteY = y + 78;
        for (net.minecraft.util.FormattedCharSequence line : font.split(Component.translatable("screen.mitofus.terminal.vrhelmets.note"), imageWidth - 2 * MARGIN)) {
            graphics.drawString(font, line, x + MARGIN, noteY, UiStyle.MUTED);
            noteY += 10;
        }
        drawMessage(graphics, state);
    }

    private void drawShop(GuiGraphics graphics, TerminalState state, LocalPlayer player, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        drawHeader(graphics);
        Component coins = Component.translatable("screen.mitofus.terminal.coins", MoneyText.iron(Wallet.balance(player)));
        UiStyle.heading(graphics, font, Component.translatable("screen.mitofus.terminal.shop.offer"), x + OFFER_X, y + 34);
        UiStyle.text(graphics, font, coins, x + OFFER_X + offerWidth() - 4 - font.width(coins), y + 34, UiStyle.GOLD);
        if (!state.characterAvailable()) {
            UiStyle.text(graphics, font, Component.translatable("screen.mitofus.terminal.no_character"), x + OFFER_X + 100, y + 34, NO_CHARACTER_COLOR);
        } else {
            UiStyle.text(graphics, font, Component.translatable("screen.mitofus.terminal.shop.free_slots", state.freeSlots()),
                    x + OFFER_X + 100, y + 34, state.freeSlots() == 0 ? UiStyle.BAD : UiStyle.MUTED);
        }
        List<OfferEntry> offer = state.offer();
        if (offer.isEmpty()) {
            UiStyle.text(graphics, font, Component.translatable("screen.mitofus.terminal.shop.no_offer"), x + OFFER_X, y + OFFER_TOP + 4, UiStyle.MUTED);
        }
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = offerScroll.first(); i < offerScroll.end(offer.size()); i++) {
            OfferEntry entry = offer.get(i);
            int rowY = y + OFFER_TOP + (i - offerScroll.first()) * OFFER_ROW;
            int rowX = x + OFFER_X;
            boolean hover = mouseX >= rowX && mouseX < rowX + offerWidth() - 62 && mouseY >= rowY && mouseY < rowY + OFFER_ROW - 2;
            graphics.fill(rowX, rowY, rowX + offerWidth() - 4, rowY + OFFER_ROW - 2, hover ? 0x40FFFFFF : 0x20FFFFFF);
            graphics.renderItem(entry.stack(), rowX + 3, rowY + 2);
            int nameColor = UiStyle.ratingNameColor(entry.stack());
            graphics.drawString(font, UiStyle.ellipsize(font, entry.stack().getHoverName().copy().withColor(nameColor), offerWidth() - 24 - 62),
                    rowX + 24, rowY + 2, nameColor);
            UiStyle.text(graphics, font, MoneyText.iron(entry.price()), rowX + 24, rowY + 11, UiStyle.GOLD);
            if (state.boughtToday().contains(entry.position())) {
                Component bought = Component.translatable("screen.mitofus.terminal.shop.bought");
                UiStyle.text(graphics, font, bought, rowX + offerWidth() - 8 - font.width(bought), rowY + 7, UiStyle.GOOD);
            }
            if (hover) {
                hovered = entry.stack();
            }
        }
        offerScroll.renderBar(graphics, x + OFFER_X + offerWidth() - 1, y + OFFER_TOP, offerRows * OFFER_ROW - 2, offer.size());
        drawMessage(graphics, state);
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    private void drawMessage(GuiGraphics graphics, TerminalState state) {
        state.message().ifPresent(message -> UiStyle.text(graphics, font, UiStyle.ellipsize(font, message, imageWidth - 2 * MARGIN),
                leftPos + MARGIN, topPos + imageHeight - FOOTER, UiStyle.COOLDOWN));
    }

    private void drawAccount(GuiGraphics graphics, TerminalState state, LocalPlayer player) {
        int x = leftPos;
        int y = topPos;
        drawHeader(graphics);

        // Energy
        if (FatigueBalance.current().enabled()) {
            float energy = player.getData(ModAttachments.FATIGUE);
            UiStyle.heading(graphics, font, Component.translatable("screen.mitofus.terminal.energy"), x + MARGIN, y + 34);
            Component text = FatigueHudLayer.energyText(energy);
            int color = FatigueHudLayer.colorFor(energy, FatigueBalance.current());
            UiStyle.text(graphics, font, text, x + imageWidth - MARGIN - font.width(text), y + 34, color);
            UiStyle.bar(graphics, x + MARGIN, y + 46, imageWidth - 2 * MARGIN, 5, energy, color);
        }

        // Money
        UiStyle.heading(graphics, font, Component.translatable("screen.mitofus.terminal.account"), x + MARGIN, y + 60);
        UiStyle.text(graphics, font, Component.translatable("screen.mitofus.terminal.coins", MoneyText.iron(Wallet.balance(player))),
                x + MARGIN, y + 72, UiStyle.GOLD);
        Component storedText = state.characterAvailable()
                ? Component.translatable("screen.mitofus.terminal.stored", Money.format(state.storedGold()))
                : Component.translatable("screen.mitofus.terminal.no_character");
        UiStyle.text(graphics, font, UiStyle.ellipsize(font, storedText, LEFT_WIDTH), x + MARGIN, y + 84,
                state.characterAvailable() ? UiStyle.TEXT : NO_CHARACTER_COLOR);
        drawMessage(graphics, state);
    }
}
