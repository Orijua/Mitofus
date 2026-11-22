package com.orijua.mitofus.feature.progression;

import com.orijua.mitofus.Mitofus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → server: place a class in a slot (only works if the slot is open and free; the server checks it). */
public record ChooseClassPayload(int slot, ResourceLocation playerClass) implements CustomPacketPayload {
    public static final Type<ChooseClassPayload> TYPE = new Type<>(Mitofus.id("choose_class"));
    public static final StreamCodec<ByteBuf, ChooseClassPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChooseClassPayload::slot,
            ResourceLocation.STREAM_CODEC, ChooseClassPayload::playerClass,
            ChooseClassPayload::new);

    @Override
    public Type<ChooseClassPayload> type() {
        return TYPE;
    }
}
