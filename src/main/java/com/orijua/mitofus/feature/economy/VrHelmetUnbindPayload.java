package com.orijua.mitofus.feature.economy;

import com.orijua.mitofus.Mitofus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: the player confirmed unbinding the bound VR helmet in {@code offHand ? off : main} hand at the terminal in {@code pos}. The server checks everything again. */
public record VrHelmetUnbindPayload(BlockPos pos, boolean offHand) implements CustomPacketPayload {
    public static final Type<VrHelmetUnbindPayload> TYPE = new Type<>(Mitofus.id("vrhelmet_unbind"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VrHelmetUnbindPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, VrHelmetUnbindPayload::pos,
            ByteBufCodecs.BOOL, VrHelmetUnbindPayload::offHand,
            VrHelmetUnbindPayload::new);

    @Override
    public Type<VrHelmetUnbindPayload> type() {
        return TYPE;
    }
}
