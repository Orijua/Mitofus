package com.orijua.mitofus.feature.coach;

import com.orijua.mitofus.Mitofus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: a "Travel to ..." button of the coachman's window; {@code npc} is his entity id, {@code destination} a village. */
public record CoachTripPayload(int npc, String destination) implements CustomPacketPayload {
    public static final Type<CoachTripPayload> TYPE = new Type<>(Mitofus.id("coach_trip"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CoachTripPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CoachTripPayload::npc,
            ByteBufCodecs.stringUtf8(32), CoachTripPayload::destination,
            CoachTripPayload::new);

    @Override
    public Type<CoachTripPayload> type() {
        return TYPE;
    }
}
