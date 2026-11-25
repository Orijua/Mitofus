package com.orijua.mitofus.feature.form;

import com.orijua.mitofus.Mitofus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** Client → server: wear a form, or take the worn one off when empty. */
public record EquipFormPayload(Optional<ResourceLocation> form) implements CustomPacketPayload {
    public static final Type<EquipFormPayload> TYPE = new Type<>(Mitofus.id("equip_form"));
    public static final StreamCodec<ByteBuf, EquipFormPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), EquipFormPayload::form,
            EquipFormPayload::new);

    @Override
    public Type<EquipFormPayload> type() {
        return TYPE;
    }
}
