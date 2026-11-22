package com.orijua.mitofus.feature.vrhelmet;

import com.orijua.mitofus.Mitofus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: the player pressed "Disconnect" in the Mitofus window; the server starts the logout countdown.
 * {@code force} = the player confirmed leaving with a quest deadline running, which fails that quest.
 */
public record LogoutRequestPayload(boolean force) implements CustomPacketPayload {
    public static final LogoutRequestPayload INSTANCE = new LogoutRequestPayload(false);
    public static final LogoutRequestPayload FORCED = new LogoutRequestPayload(true);
    public static final Type<LogoutRequestPayload> TYPE = new Type<>(Mitofus.id("logout_request"));
    public static final StreamCodec<ByteBuf, LogoutRequestPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(LogoutRequestPayload::new, LogoutRequestPayload::force);

    @Override
    public Type<LogoutRequestPayload> type() {
        return TYPE;
    }
}
