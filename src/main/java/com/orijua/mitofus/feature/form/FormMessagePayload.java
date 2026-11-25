package com.orijua.mitofus.feature.form;

import com.orijua.mitofus.Mitofus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: why a wear or take-off click was refused, to show inside the Forms tab (the action bar is hidden behind the window). */
public record FormMessagePayload(String key) implements CustomPacketPayload {
    public static final Type<FormMessagePayload> TYPE = new Type<>(Mitofus.id("form_message"));
    public static final StreamCodec<ByteBuf, FormMessagePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, FormMessagePayload::key,
            FormMessagePayload::new);

    @Override
    public Type<FormMessagePayload> type() {
        return TYPE;
    }
}
