package com.orijua.mitofus.feature.economy;

import com.orijua.mitofus.core.config.TerminalBalance;
import com.orijua.mitofus.core.config.TradeBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.character.CharacterSnapshot;
import com.orijua.mitofus.feature.character.CharacterVault;
import com.orijua.mitofus.feature.character.WorldSide;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

/**
 * The account terminal and the phone: opens the window (never in Mitofus: there they show "No signal") and builds its state.
 * Everything is decided here, in the server: the client says what it saw and the checks come before anything moves. The
 * only thing that touches the stored character is {@link TerminalShop}, an exception to "nothing else moves items between
 * the characters": it only ADDS entries (the pieces of the offer).
 */
public final class TerminalService {
    private TerminalService() {
    }

    public enum Result { OK, NO_TERMINAL, IN_MITOFUS }

    public static void register(IEventBus gameBus) {
        // After the characters are settled (normal priority): the world the player is in is known.
        gameBus.addListener(EventPriority.LOWEST, TerminalService::onLoggedIn);
        gameBus.addListener(EventPriority.LOWEST, TerminalService::onChangedDimension);
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            giveIfMissing(player);
        }
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            giveIfMissing(player);
        }
    }

    /**
     * Gives the phone once, to a player who already got their starter VR helmet, while they carry the real world's things
     * (so it lands in the real inventory, never in Mitofus's). Someone who dies in Mitofus gets it at the next login or
     * change of dimension. Returns whether it was given now.
     */
    public static boolean giveIfMissing(ServerPlayer player) {
        if (!player.getData(ModAttachments.STARTER_VR_HELMET_GIVEN.get()) || player.getData(ModAttachments.PHONE_GIVEN.get())
                || !carriesRealWorld(player)) {
            return false;
        }
        player.setData(ModAttachments.PHONE_GIVEN.get(), true);
        ItemStack phone = new ItemStack(ModItems.PHONE.get());
        if (!player.getInventory().add(phone)) {
            player.drop(phone, false);
        }
        player.sendSystemMessage(Component.translatable("message.mitofus.phone.given"));
        return true;
    }

    // ---- Opening ----

    /** Opens the terminal's window; in Mitofus nothing opens. {@code terminal} is the block, empty for the phone. */
    public static Result open(ServerPlayer player, Optional<BlockPos> terminal) {
        if (!carriesRealWorld(player)) {
            return Result.IN_MITOFUS;
        }
        TerminalState state = state(player, Optional.empty());
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> {
                    TerminalMenu menu = new TerminalMenu(id, inventory, terminal);
                    menu.markSent(state.today());
                    return menu;
                },
                        Component.translatable("screen.mitofus.terminal.title")),
                buf -> TerminalState.STREAM_CODEC.encode(buf, state));
        return Result.OK;
    }

    // ---- State ----

    private static CharacterVault vault(ServerPlayer player) {
        return player.getData(ModAttachments.CHARACTER_VAULT.get());
    }

    /**
     * Whether the player is outside Mitofus AND carries the real world's things (vault live REAL). After a failed
     * settle they can be in the real world still carrying Mitofus's inventory: there the terminal must not touch it.
     */
    static boolean carriesRealWorld(ServerPlayer player) {
        return !MitofusWorld.is(player.level()) && vault(player).live().equals(Optional.of(WorldSide.REAL));
    }

    /** The stored Mitofus character, only while the player carries the real world's things and the snapshot can be read. */
    static Optional<CompoundTag> storedCharacter(ServerPlayer player) {
        if (!carriesRealWorld(player)) {
            return Optional.empty();
        }
        return vault(player).stored().filter(tag -> CharacterSnapshot.isReadable(tag, player.registryAccess()));
    }

    public static long today(ServerPlayer player) {
        return TradeRules.day(player.serverLevel().getGameTime());
    }

    public static TerminalState state(ServerPlayer player, Optional<Component> message) {
        TerminalBalance balance = TerminalBalance.current();
        Optional<CompoundTag> stored = storedCharacter(player);
        int gold = stored.map(tag -> TerminalRules.countGold(CharacterSnapshot.inventory(tag))).orElse(0);
        long today = today(player);
        int free = stored.map(tag -> TerminalRules.freeSlots(CharacterSnapshot.inventory(tag))).orElse(0);
        TradeBalance trade = TradeBalance.current();
        return new TerminalState(stored.isPresent(), gold, today, free,
                TerminalShop.offer(player.serverLevel(), today, balance, trade),
                player.getData(ModAttachments.OFFER_BOUGHT.get()).positionsOn(today),
                player.getData(ModAttachments.VR_HELMET_TIER.get()), message);
    }

    /** Sends the open menu a fresh state with the message of the last action. */
    public static void sendState(ServerPlayer player, TerminalMenu menu, Optional<Component> message) {
        TerminalState state = state(player, message);
        menu.markSent(state.today());
        PacketDistributor.sendToPlayer(player, new TerminalStatePayload(menu.containerId, state));
    }

    static boolean menuOpen(ServerPlayer player, int containerId) {
        return player.containerMenu instanceof TerminalMenu menu && menu.containerId == containerId && menu.stillValid(player);
    }
}
