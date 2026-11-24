package com.orijua.mitofus.feature.storage;

import com.orijua.mitofus.Mitofus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: what the storekeeper's window shows. {@code rows} is 0 until the storage is bought, {@code usedSlots} the
 * slots with something in them, {@code openCost} what buying it costs and {@code nextRowCost} what one more row costs
 * (-1 at the most). The costs come from the server, so they are the ones that will be charged.
 */
public record StorageInfoPayload(int npc, int rows, int usedSlots, long openCost, long nextRowCost) implements CustomPacketPayload {
    public static final Type<StorageInfoPayload> TYPE = new Type<>(Mitofus.id("storage_info"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageInfoPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StorageInfoPayload::npc,
            ByteBufCodecs.VAR_INT, StorageInfoPayload::rows,
            ByteBufCodecs.VAR_INT, StorageInfoPayload::usedSlots,
            ByteBufCodecs.VAR_LONG, StorageInfoPayload::openCost,
            ByteBufCodecs.VAR_LONG, StorageInfoPayload::nextRowCost,
            StorageInfoPayload::new);

    public int slots() {
        return rows * StorageRules.SLOTS_PER_ROW;
    }

    public int freeSlots() {
        return Math.max(0, slots() - usedSlots);
    }

    @Override
    public Type<StorageInfoPayload> type() {
        return TYPE;
    }
}
