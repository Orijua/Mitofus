package com.orijua.mitofus.feature.vrhelmet;

import com.orijua.mitofus.Mitofus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: the "Connecting…" screen starts for this many ticks, or ({@code ticks} = 0) is cancelled. */
public record VrHelmetConnectingPayload(int ticks) implements CustomPacketPayload {
    public static final Type<VrHelmetConnectingPayload> TYPE = new Type<>(Mitofus.id("vrhelmet_connecting"));
    public static final StreamCodec<ByteBuf, VrHelmetConnectingPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(VrHelmetConnectingPayload::new, VrHelmetConnectingPayload::ticks);

    @Override
    public Type<VrHelmetConnectingPayload> type() {
        return TYPE;
    }
}
