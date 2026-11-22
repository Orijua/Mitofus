package com.orijua.mitofus.feature.npc;

import com.orijua.mitofus.Mitofus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Optional;

/** Server → client: the offers of Smith's shop for the open menu {@code containerId}, and what to tell the player. */
public record SmithShopStatePayload(int containerId, MerchantOffers offers, Optional<Component> message) implements CustomPacketPayload {
    public static final Type<SmithShopStatePayload> TYPE = new Type<>(Mitofus.id("smith_shop_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SmithShopStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SmithShopStatePayload::containerId,
            MerchantOffers.STREAM_CODEC, SmithShopStatePayload::offers,
            ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC), SmithShopStatePayload::message,
            SmithShopStatePayload::new);

    @Override
    public Type<SmithShopStatePayload> type() {
        return TYPE;
    }
}
