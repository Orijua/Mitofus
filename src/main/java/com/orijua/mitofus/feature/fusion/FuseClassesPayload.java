package com.orijua.mitofus.feature.fusion;

import com.orijua.mitofus.Mitofus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → server: fuse the two classes of this fusion (the server checks every requirement and takes the coins). */
public record FuseClassesPayload(ResourceLocation fusion) implements CustomPacketPayload {
    public static final Type<FuseClassesPayload> TYPE = new Type<>(Mitofus.id("fuse_classes"));
    public static final StreamCodec<ByteBuf, FuseClassesPayload> STREAM_CODEC =
            ResourceLocation.STREAM_CODEC.map(FuseClassesPayload::new, FuseClassesPayload::fusion);

    @Override
    public Type<FuseClassesPayload> type() {
        return TYPE;
    }
}
