package com.orijua.mitofus.feature.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The player's gold, iron and copper in Mitofus as one balance in iron (1 gold = 100 iron) plus the copper left over (less than an iron): money that takes no room
 * in the inventory. It is kept at death and, is the only money of the mod. Never negative.
 */
public record Purse(long iron, int copper) {
    public static final Purse EMPTY = new Purse(0L, 0);

    public static final Codec<Purse> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("iron", 0L).forGetter(Purse::iron),
            Codec.INT.optionalFieldOf("copper", 0).forGetter(Purse::copper)
    ).apply(instance, Purse::new));

    public static final StreamCodec<ByteBuf, Purse> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public Purse {
        iron = Math.max(0L, iron);
        copper = Math.max(0, Math.min(Money.COPPER_PER_IRON - 1, copper));
    }

    /** A purse of whole iron, with no copper left over. */
    public Purse(long iron) {
        this(iron, 0);
    }

    /** Adds {@code amount} copper: every hundred makes an iron, the rest stays as the copper left over. */
    public Purse plusCopper(long amount) {
        long total = copper + Math.max(0L, amount);
        return new Purse(iron + total / Money.COPPER_PER_IRON, (int) (total % Money.COPPER_PER_IRON));
    }

    public Purse plus(long amount) {
        return new Purse(iron + Math.max(0L, amount), copper);
    }

    /** Takes {@code amount} (never more than there is: callers check first). */
    public Purse minus(long amount) {
        return new Purse(iron - Math.max(0L, amount), copper);
    }
}
