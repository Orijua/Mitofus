package com.orijua.mitofus.feature.coach;

import com.orijua.mitofus.Mitofus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Server → client: what the coachman's window shows. One place per destination he can take you to from here, with the
 * total fare in iron that will be charged (the server works it out from the real road).
 */
public record CoachInfoPayload(int npc, List<String> destinations, List<Long> fares) implements CustomPacketPayload {
    public static final Type<CoachInfoPayload> TYPE = new Type<>(Mitofus.id("coach_info"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CoachInfoPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CoachInfoPayload::npc,
            ByteBufCodecs.stringUtf8(32).apply(ByteBufCodecs.list()), CoachInfoPayload::destinations,
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), CoachInfoPayload::fares,
            CoachInfoPayload::new);

    @Override
    public Type<CoachInfoPayload> type() {
        return TYPE;
    }
}
