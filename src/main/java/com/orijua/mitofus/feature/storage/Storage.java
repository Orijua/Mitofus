package com.orijua.mitofus.feature.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;

/**
 * A character's storage. {@code rows} of 9 slots, {@code 0} if never opened; {@code number} is
 * its registration number (no password until phase 9). Saved with the character, never synced.
 */
public record Storage(int rows, int number, CompoundTag items) {
    public static final Storage NONE = new Storage(0, 0, new CompoundTag());

    public static final Codec<Storage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("rows", 0).forGetter(Storage::rows),
            Codec.INT.optionalFieldOf("number", 0).forGetter(Storage::number),
            CompoundTag.CODEC.optionalFieldOf("items", new CompoundTag()).forGetter(Storage::items)
    ).apply(instance, Storage::new));

    public boolean opened() {
        return rows > 0;
    }

    public int slots() {
        return rows * StorageRules.SLOTS_PER_ROW;
    }

    /** The same storage with {@code newRows} rows; the items stay. */
    public Storage withRows(int newRows) {
        return new Storage(newRows, number, items.copy());
    }
}
