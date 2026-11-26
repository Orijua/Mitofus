package com.orijua.mitofus.feature.progression;

import com.orijua.mitofus.Mitofus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → server, from the village chief's window: put {@code branch} in {@code slot}. The server checks reach, rules and price. */
public record ChangeBranchPayload(int npc, int slot, ResourceLocation branch) implements CustomPacketPayload {
    public static final Type<ChangeBranchPayload> TYPE = new Type<>(Mitofus.id("change_branch"));
    public static final StreamCodec<ByteBuf, ChangeBranchPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChangeBranchPayload::npc,
            ByteBufCodecs.VAR_INT, ChangeBranchPayload::slot,
            ResourceLocation.STREAM_CODEC, ChangeBranchPayload::branch,
            ChangeBranchPayload::new);

    @Override
    public Type<ChangeBranchPayload> type() {
        return TYPE;
    }
}
