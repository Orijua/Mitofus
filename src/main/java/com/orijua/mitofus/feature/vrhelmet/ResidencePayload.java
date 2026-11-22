package com.orijua.mitofus.feature.vrhelmet;

import com.orijua.mitofus.Mitofus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: the "Become a resident" button of the village chief's window; {@code npc} is the chief's entity id. */
public record ResidencePayload(int npc) implements CustomPacketPayload {
    public static final Type<ResidencePayload> TYPE = new Type<>(Mitofus.id("residence"));
    public static final StreamCodec<ByteBuf, ResidencePayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(ResidencePayload::new, ResidencePayload::npc);

    @Override
    public Type<ResidencePayload> type() {
        return TYPE;
    }
}
