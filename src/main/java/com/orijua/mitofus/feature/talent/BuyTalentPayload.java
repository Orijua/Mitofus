package com.orijua.mitofus.feature.talent;

import com.orijua.mitofus.Mitofus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → server: buy a talent node for the class in a slot (the server checks everything). */
public record BuyTalentPayload(int slot, ResourceLocation node) implements CustomPacketPayload {
    public static final Type<BuyTalentPayload> TYPE = new Type<>(Mitofus.id("buy_talent"));
    public static final StreamCodec<ByteBuf, BuyTalentPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BuyTalentPayload::slot,
            ResourceLocation.STREAM_CODEC, BuyTalentPayload::node,
            BuyTalentPayload::new);

    @Override
    public Type<BuyTalentPayload> type() {
        return TYPE;
    }
}
