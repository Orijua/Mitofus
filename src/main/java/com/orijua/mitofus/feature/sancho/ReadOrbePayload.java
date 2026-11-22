package com.orijua.mitofus.feature.sancho;

import com.orijua.mitofus.Mitofus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: read Sancho's rare book (confirmed in its window); the server checks the player carries it. */
public record ReadOrbePayload() implements CustomPacketPayload {
    public static final ReadOrbePayload INSTANCE = new ReadOrbePayload();
    public static final Type<ReadOrbePayload> TYPE = new Type<>(Mitofus.id("read_orbe"));
    public static final StreamCodec<ByteBuf, ReadOrbePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<ReadOrbePayload> type() {
        return TYPE;
    }
}
