package com.orijua.mitofus.feature.fatigue.client;

import com.orijua.mitofus.feature.fatigue.FatigueRestraint;
import com.orijua.mitofus.feature.fatigue.FatigueRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.Input;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * The client half of the safety protocol: while the player is held still, no key moves them (sneaking stays free), and any
 * window they have open is closed as if they pressed Escape (the mod's own screens, containers and the inventory). Chat, the
 * pause menu and system screens (death, connecting, disconnected, options...) are left alone; the protocol notice is a HUD
 * layer, not a screen. Closing every tick also stops a new screen from staying open during the 5 seconds (BUG-092). The movement
 * is held on the client only, accepted for now: pending to be enforced on the server if it turns out to be needed. The
 * server's {@code enforce} stays as a safety net for the menus.
 *
 * <p>Whitelist rule ({@link FatigueRules#isProtectedScreen}): a screen closes if it is an {@code AbstractContainerScreen} or its
 * class lives under {@link FatigueRules#MOD_PACKAGE}; everything else is protected. Risk: a future screen of the mod that must stay
 * open during the margin (VR helmet/logout, disconnection) would be closed every tick. If one is created, add an exception
 * (e.g. a marker interface {@code KeepOpenWhileRestrained}) to the rule.
 */
public final class FatigueRestraintClient {
    private FatigueRestraintClient() {
    }

    /** Only containers (the inventory included) and the mod's own screens close; anything else (chat, pause, system) is protected. */
    static boolean isProtectedScreen(Screen screen) {
        return FatigueRules.isProtectedScreen(screen instanceof AbstractContainerScreen<?>, screen.getClass().getName());
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (minecraft.player == null || screen == null) {
            return;
        }
        boolean restrained = FatigueRestraint.isRestrained(minecraft.player);
        if (FatigueRules.shouldCloseScreen(restrained, true, isProtectedScreen(screen))) {
            // What Escape does: a container tells the server to close its menu, the rest just pop. Once per tick at most.
            screen.onClose();
        }
    }

    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!FatigueRestraint.isRestrained(event.getEntity())) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0.0f;
        input.leftImpulse = 0.0f;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        // Momentum (a push, a fall in progress) must not carry them: the horizontal speed goes, gravity stays.
        Vec3 motion = event.getEntity().getDeltaMovement();
        event.getEntity().setDeltaMovement(0.0, motion.y, 0.0);
    }
}
