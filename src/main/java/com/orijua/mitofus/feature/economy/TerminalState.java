package com.orijua.mitofus.feature.economy;

import com.orijua.mitofus.feature.vrhelmet.VrHelmetTier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Everything the terminal window needs, decided by the server and sent whenever it changes: whether there is a
 * character to buy into, how many gold coins it holds, what can be bought and the last message.
 *
 * @param characterAvailable whether the Mitofus character is stored and readable (the real world's player carries the other one)
 * @param storedGold     gold coins in the stored character's inventory
 * @param today              the server's game day
 * @param freeSlots          free main slots of the stored character (0 without character)
 * @param offer              today's offer, in position order (empty: no offer)
 * @param boughtToday        the positions of the offer this player already bought today
 * @param sessionTier        the tier of the helmet of the player's last connection
 * @param message            the result of the last action, if any
 */
public record TerminalState(boolean characterAvailable, int storedGold, long today, int freeSlots, List<OfferEntry> offer,
                            Set<Integer> boughtToday, VrHelmetTier sessionTier, Optional<Component> message) {
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalState> STREAM_CODEC = StreamCodec.of(TerminalState::write, TerminalState::read);

    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> MESSAGE = ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC);

    private static void write(RegistryFriendlyByteBuf buf, TerminalState state) {
        buf.writeBoolean(state.characterAvailable);
        buf.writeVarInt(state.storedGold);
        buf.writeLong(state.today);
        buf.writeVarInt(state.freeSlots);
        buf.writeVarInt(state.offer.size());
        for (OfferEntry entry : state.offer) {
            OfferEntry.STREAM_CODEC.encode(buf, entry);
        }
        buf.writeVarInt(state.boughtToday.size());
        for (int position : state.boughtToday) {
            buf.writeVarInt(position);
        }
        VrHelmetTier.STREAM_CODEC.encode(buf, state.sessionTier);
        MESSAGE.encode(buf, state.message);
    }

    private static TerminalState read(RegistryFriendlyByteBuf buf) {
        boolean available = buf.readBoolean();
        int stored = buf.readVarInt();
        long today = buf.readLong();
        int free = buf.readVarInt();
        int offerSize = buf.readVarInt();
        if (offerSize < 0 || offerSize > OfferBought.MAX_POSITIONS) {
            throw new IllegalArgumentException("the offer has " + offerSize + " pieces");
        }
        List<OfferEntry> offer = new ArrayList<>(offerSize);
        for (int i = 0; i < offerSize; i++) {
            offer.add(OfferEntry.STREAM_CODEC.decode(buf));
        }
        int boughtSize = buf.readVarInt();
        if (boughtSize < 0 || boughtSize > OfferBought.MAX_POSITIONS) {
            throw new IllegalArgumentException("the bought set has " + boughtSize + " positions");
        }
        Set<Integer> bought = new TreeSet<>();
        for (int i = 0; i < boughtSize; i++) {
            bought.add(buf.readVarInt());
        }
        VrHelmetTier sessionTier = VrHelmetTier.STREAM_CODEC.decode(buf);
        Optional<Component> message = MESSAGE.decode(buf);
        return new TerminalState(available, stored, today, free, List.copyOf(offer), Set.copyOf(bought), sessionTier, message);
    }

    public TerminalState withMessage(Optional<Component> message) {
        return new TerminalState(characterAvailable, storedGold, today, freeSlots, offer, boughtToday, sessionTier, message);
    }
}
