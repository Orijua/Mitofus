package com.orijua.mitofus.feature.storage;

import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.economy.CoinPurse;
import com.orijua.mitofus.feature.economy.MoneyText;
import com.orijua.mitofus.feature.quest.QuestService;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;


/** Opening, expanding and using a character's storage; the storekeeper's window asks for it. */
public final class StorageService {
    private StorageService() {
    }

    public static Storage of(ServerPlayer player) {
        return player.getData(ModAttachments.STORAGE.get());
    }

    /** Opens the storage, buying it first if the player has none. Returns true if the window opened. */
    public static boolean open(ServerPlayer player, StorekeeperNpc keeper) {
        if (!inReach(player, keeper)) {
            return false;
        }
        Storage storage = of(player);
        if (!storage.opened()) {
            if (!CoinPurse.pay(player, StorageRules.openCost())) {
                player.displayClientMessage(Component.translatable("message.mitofus.storage.no_money",
                        MoneyText.iron(StorageRules.openCost())), true);
                return false;
            }
            storage = new Storage(StorageRules.FIRST_ROWS, player.getRandom().nextInt(900_000) + 100_000, new CompoundTag());
            player.setData(ModAttachments.STORAGE.get(), storage);
            player.displayClientMessage(Component.translatable("message.mitofus.storage.opened",
                    storage.number(), MoneyText.iron(StorageRules.openCost())), false);
        }
        show(player, storage, keeper);
        return true;
    }

    /** Buys one more row. Returns true if it was bought. */
    public static boolean expand(ServerPlayer player, StorekeeperNpc keeper) {
        if (!inReach(player, keeper)) {
            return false;
        }
        Storage storage = of(player);
        if (!storage.opened()) {
            player.displayClientMessage(Component.translatable("message.mitofus.storage.not_opened"), true);
            return false;
        }
        long cost = StorageRules.nextRowCost(storage.rows());
        if (cost < 0L) {
            player.displayClientMessage(Component.translatable("message.mitofus.storage.full"), true);
            return false;
        }
        if (!CoinPurse.pay(player, cost)) {
            player.displayClientMessage(Component.translatable("message.mitofus.storage.no_money", MoneyText.iron(cost)), true);
            return false;
        }
        player.setData(ModAttachments.STORAGE.get(), storage.withRows(storage.rows() + 1));
        player.displayClientMessage(Component.translatable("message.mitofus.storage.expanded",
                storage.rows() + 1, MoneyText.iron(cost)), false);
        return true;
    }

    private static boolean inReach(ServerPlayer player, StorekeeperNpc keeper) {
        return MitofusWorld.is(player.level()) && withinReach(keeper, player);
    }

    /** Whether the player is close enough to the storekeeper to keep using the storage. */
    public static boolean withinReach(StorekeeperNpc keeper, Player player) {
        return keeper.isAlive() && keeper.distanceToSqr(player) <= QuestService.GIVER_REACH * QuestService.GIVER_REACH;
    }

    /** The storage window closes by itself when the player walks away from the storekeeper (or it dies). */
    static final class KeeperChestMenu extends ChestMenu {
        private final StorekeeperNpc keeper;

        KeeperChestMenu(MenuType<?> type, int id, Inventory inventory, Container container, int rows, StorekeeperNpc keeper) {
            super(type, id, inventory, container, rows);
            this.keeper = keeper;
        }

        @Override
        public boolean stillValid(Player player) {
            return super.stillValid(player) && withinReach(keeper, player);
        }
    }

    /** The chest window over the saved items; every change is saved back at once. */
    private static void show(ServerPlayer player, Storage storage, StorekeeperNpc keeper) {
        SimpleContainer container = new SimpleContainer(storage.slots());
        NonNullList<ItemStack> saved = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(storage.items(), saved, player.registryAccess());
        for (int slot = 0; slot < saved.size(); slot++) {
            container.setItem(slot, saved.get(slot));
        }
        int rows = storage.rows();
        int number = storage.number();
        container.addListener(changed -> {
            NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                items.set(slot, container.getItem(slot).copy());
            }
            player.setData(ModAttachments.STORAGE.get(),
                    new Storage(rows, number, ContainerHelper.saveAllItems(new CompoundTag(), items, player.registryAccess())));
        });
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> new KeeperChestMenu(menuType(rows), id, inventory, container, rows, keeper),
                Component.translatable("container.mitofus.storage", number)));
    }

    static MenuType<ChestMenu> menuType(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }
}
