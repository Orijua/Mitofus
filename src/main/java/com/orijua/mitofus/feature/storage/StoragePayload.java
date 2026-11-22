package com.orijua.mitofus.feature.storage;

import com.orijua.mitofus.Mitofus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: a button of the storekeeper's window; {@code npc} is his entity id, {@code expand} asks for one more row instead of opening. */
public record StoragePayload(int npc, boolean expand) implements CustomPacketPayload {
    public static final Type<StoragePayload> TYPE = new Type<>(Mitofus.id("storage"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StoragePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StoragePayload::npc,
            ByteBufCodecs.BOOL, StoragePayload::expand,
            StoragePayload::new);

    @Override
    public Type<StoragePayload> type() {
        return TYPE;
    }
}
