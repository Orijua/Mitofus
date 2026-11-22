package com.orijua.mitofus.core.config;

/**
 * Prices (in gold) and stocks of Smith's shop, apart from {@link Balance}. The enhancement stones follow the reference prices
 * (180 and 2.500, halved); the rest is the mod's own design. A smith already in the world takes the new numbers the
 * next time his shop opens (BlacksmithNpc), keeping what was already bought.
 *
 * @param stonePrice         weapon or armor enhancement stone
 * @param blessedStonePrice  blessed weapon or armor enhancement stone
 * @param blessedScrollPrice blessed enhancement scroll
 * @param stoneStock         normal stones of each kind until he restocks
 * @param blessedStock       blessed stones of each kind and blessed scrolls until he restocks
 * @param respecScrollPrice  stat reset scroll
 * @param mithrilSaleGold    gold he pays for 4 mithril ingots
 * @param skillBookPrice     each skill book
 * @param eyePrice           Appraiser Eye (it also asks for 2 diamonds)
 * @param eyeDailyStock      Appraiser Eyes on sale each Minecraft day
 */
public record SmithShopBalance(int stonePrice, int blessedStonePrice, int blessedScrollPrice, int stoneStock, int blessedStock,
                               int respecScrollPrice, int mithrilSaleGold, int skillBookPrice, int eyePrice, int eyeDailyStock) {
    /** Smith's shop takes the price from the whole inventory (SmithShop), so it may be more than a stack. */
    public static final int MAX_PRICE = 100_000;
    public static final int MAX_STOCK = 999;
    /** The gold he pays comes out as one stack in the result slot. */
    public static final int MAX_MITHRIL_SALE = 64;

    public static final SmithShopBalance DEFAULT = new SmithShopBalance(90, 1_250, 1_250, 16, 3, 4, 1, 5, 10, 4);

    public SmithShopBalance {
        check("stonePrice", stonePrice, 1, MAX_PRICE);
        check("blessedStonePrice", blessedStonePrice, 1, MAX_PRICE);
        check("blessedScrollPrice", blessedScrollPrice, 1, MAX_PRICE);
        check("stoneStock", stoneStock, 1, MAX_STOCK);
        check("blessedStock", blessedStock, 1, MAX_STOCK);
        check("respecScrollPrice", respecScrollPrice, 1, MAX_PRICE);
        check("mithrilSaleGold", mithrilSaleGold, 1, MAX_MITHRIL_SALE);
        check("skillBookPrice", skillBookPrice, 1, MAX_PRICE);
        check("eyePrice", eyePrice, 1, MAX_PRICE);
        check("eyeDailyStock", eyeDailyStock, 1, MAX_STOCK);
    }

    private static void check(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ": " + value);
        }
    }

    /** Set only inside {@link #withOverride}; read by {@link #current()} before the config. */
    private static SmithShopBalance override;

    /** The active server config, or the defaults before a world is loaded. */
    public static SmithShopBalance current() {
        if (override != null) {
            return override;
        }
        return Config.isLoaded() ? Config.smithShopBalance() : DEFAULT;
    }

    /** For game tests: {@link #current()} returns {@code balance} while {@code action} runs, then the config again. */
    public static void withOverride(SmithShopBalance balance, Runnable action) {
        SmithShopBalance previous = override;
        override = balance;
        try {
            action.run();
        } finally {
            override = previous;
        }
    }
}
