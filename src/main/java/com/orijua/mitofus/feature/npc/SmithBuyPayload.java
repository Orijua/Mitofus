package com.orijua.mitofus.feature.npc;

import com.orijua.mitofus.Mitofus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Client → server, from Smith's shop: close the deal {@code index} of the open menu {@code containerId}, having seen it give
 * {@code seenResult} for {@code seenCostA} of the first cost and {@code seenCostB} of the second (0 if the offer has none). The server checks all of it.
 */
public record SmithBuyPayload(int containerId, int index, ItemStack seenResult, int seenCostA, int seenCostB) implements CustomPacketPayload {
    public static final Type<SmithBuyPayload> TYPE = new Type<>(Mitofus.id("smith_buy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SmithBuyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SmithBuyPayload::containerId,
            ByteBufCodecs.VAR_INT, SmithBuyPayload::index,
            ItemStack.OPTIONAL_STREAM_CODEC, SmithBuyPayload::seenResult,
            ByteBufCodecs.VAR_INT, SmithBuyPayload::seenCostA,
            ByteBufCodecs.VAR_INT, SmithBuyPayload::seenCostB,
            SmithBuyPayload::new);

    @Override
    public Type<SmithBuyPayload> type() {
        return TYPE;
    }
}
