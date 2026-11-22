package com.orijua.mitofus.feature.npc;

import com.orijua.mitofus.core.registry.ModMenus;
import com.orijua.mitofus.feature.quest.QuestService;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Optional;

/**
 * Smith's shop window: no slots (so no payment slots that would cap a price at one stack), only the offers the server
 * sends. The server counts and takes the payment from the whole inventory ({@link SmithShop}). It stays open while Smith
 * trades with this player, in Mitofus and within reach.
 */
public class SmithShopMenu extends AbstractContainerMenu {
    /** The smith, on the server; null on the client. */
    private final BlacksmithNpc smith;
    private MerchantOffers offers;
    private Optional<Component> message = Optional.empty();

    /** Server-side. */
    public SmithShopMenu(int containerId, Inventory inventory, BlacksmithNpc smith) {
        super(ModMenus.SMITH_SHOP.get(), containerId);
        this.smith = smith;
        this.offers = smith.getOffers();
    }

    /** Client-side constructor, called by the MenuType when the server opens the menu: reads the first offers. */
    public SmithShopMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(ModMenus.SMITH_SHOP.get(), containerId);
        this.smith = null;
        this.offers = MerchantOffers.STREAM_CODEC.decode(buf);
    }

    /** The smith of this window; null on the client. */
    public BlacksmithNpc smith() {
        return smith;
    }

    /** What the window shows: the last offers and message the server sent. */
    public void receive(MerchantOffers offers, Optional<Component> message) {
        this.offers = offers;
        this.message = message;
    }

    public MerchantOffers offers() {
        return offers;
    }

    public Optional<Component> message() {
        return message;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (smith == null) {
            return true;
        }
        return MitofusWorld.is(player.level()) && smith.isAlive() && smith.getTradingPlayer() == player
                && smith.distanceToSqr(player) <= QuestService.GIVER_REACH * QuestService.GIVER_REACH;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (smith != null && smith.getTradingPlayer() == player) {
            smith.setTradingPlayer(null);
        }
    }
}
