package com.orijua.mitofus.feature.progression;

import com.orijua.mitofus.Mitofus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → server: pick the job (only works while the player has none; the server checks it). */
public record ChooseJobPayload(ResourceLocation job) implements CustomPacketPayload {
    public static final Type<ChooseJobPayload> TYPE = new Type<>(Mitofus.id("choose_job"));
    public static final StreamCodec<ByteBuf, ChooseJobPayload> STREAM_CODEC =
            ResourceLocation.STREAM_CODEC.map(ChooseJobPayload::new, ChooseJobPayload::job);

    @Override
    public Type<ChooseJobPayload> type() {
        return TYPE;
    }
}
