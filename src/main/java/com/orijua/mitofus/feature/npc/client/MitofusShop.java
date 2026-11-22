package com.orijua.mitofus.feature.npc.client;

import com.orijua.mitofus.core.client.HudVisibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Inside Mitofus, the vanilla trading window of a merchant opens as a {@link MitofusMerchantScreen}; in the real
 * world (villagers) the vanilla one stays.
 */
public final class MitofusShop {
    private MitofusShop() {
    }

    /** Whether a screen about to open must be swapped for the Mitofus one: only a vanilla merchant screen, only in Mitofus. */
    static boolean shouldReplace(boolean inMitofus, boolean merchant, boolean alreadyMitofus) {
        return inMitofus && merchant && !alreadyMitofus;
    }

    public static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean merchant = event.getNewScreen() instanceof MerchantScreen;
        boolean already = event.getNewScreen() instanceof MitofusMerchantScreen;
        if (minecraft.player != null && shouldReplace(HudVisibility.inMitofus(minecraft), merchant, already)) {
            MerchantScreen vanilla = (MerchantScreen) event.getNewScreen();
            event.setNewScreen(new MitofusMerchantScreen(vanilla.getMenu(), minecraft.player.getInventory(), vanilla.getTitle()));
        }
    }
}
