package com.orijua.mitofus.feature.eibar;

import com.orijua.mitofus.Mitofus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: the player confirmed starting the contest against Mencia (entity id {@code mencia}). */
public record StartContestPayload(int mencia) implements CustomPacketPayload {
    public static final Type<StartContestPayload> TYPE = new Type<>(Mitofus.id("start_contest"));
    public static final StreamCodec<ByteBuf, StartContestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StartContestPayload::mencia,
            StartContestPayload::new);

    @Override
    public Type<StartContestPayload> type() {
        return TYPE;
    }
}
