package com.orijua.mitofus.core.registry;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.economy.TerminalMenu;
import com.orijua.mitofus.feature.npc.SmithShopMenu;
import com.orijua.mitofus.feature.smelting.SmelterMenu;
import com.orijua.mitofus.feature.smithing.SmithingAnvilMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Mitofus.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<SmithingAnvilMenu>> SMITHING_ANVIL =
            MENUS.register("smithing_anvil", () -> new MenuType<>(SmithingAnvilMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<SmelterMenu>> SMELTER =
            MENUS.register("smelter", () -> new MenuType<>(SmelterMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<TerminalMenu>> TERMINAL =
            MENUS.register("terminal", () -> IMenuTypeExtension.create(TerminalMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<SmithShopMenu>> SMITH_SHOP =
            MENUS.register("smith_shop", () -> IMenuTypeExtension.create(SmithShopMenu::new));

    private ModMenus() {
    }
}
