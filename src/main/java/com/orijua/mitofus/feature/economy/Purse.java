package com.orijua.mitofus.feature.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The player's gold, iron and copper in Mitofus as one balance in iron (1 gold = 100 iron): money that takes no room
 * in the inventory. It is kept at death and, is the only money of the mod. Never negative.
 */
public record Purse(long iron) {
    public static final Purse EMPTY = new Purse(0L);

    public static final Codec<Purse> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("iron", 0L).forGetter(Purse::iron)
    ).apply(instance, Purse::new));

    public static final StreamCodec<ByteBuf, Purse> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public Purse {
        iron = Math.max(0L, iron);
    }

    public Purse plus(long amount) {
        return new Purse(iron + Math.max(0L, amount));
    }

    /** Takes {@code amount} (never more than there is: callers check first). */
    public Purse minus(long amount) {
        return new Purse(iron - Math.max(0L, amount));
    }
}
