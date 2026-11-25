package com.orijua.mitofus.feature.fusion;

import com.orijua.mitofus.Mitofus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → server: the hybrid in {@code hybridSlot} absorbs the lone class {@code classId} (the server checks everything and takes the coins). */
public record AbsorbClassPayload(int hybridSlot, ResourceLocation classId) implements CustomPacketPayload {
    public static final Type<AbsorbClassPayload> TYPE = new Type<>(Mitofus.id("absorb_class"));
    public static final StreamCodec<ByteBuf, AbsorbClassPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AbsorbClassPayload::hybridSlot,
            ResourceLocation.STREAM_CODEC, AbsorbClassPayload::classId,
            AbsorbClassPayload::new);

    @Override
    public Type<AbsorbClassPayload> type() {
        return TYPE;
    }
}
