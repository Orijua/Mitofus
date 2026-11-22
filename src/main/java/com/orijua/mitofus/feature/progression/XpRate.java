package com.orijua.mitofus.feature.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * The level XP a player earned in their last minutes played in Mitofus (see {@link XpRateRules}). Immutable and small.
 *
 * @param minutes        the XP of each full minute played, the newest first, at most {@link #MAX_MINUTES}
 * @param currentXp      the XP earned in the minute in progress
 * @param currentSeconds the seconds played of the minute in progress, 0 to 59
 */
public record XpRate(List<Integer> minutes, int currentXp, int currentSeconds) {
    /** Full minutes kept; with the minute in progress the window is one more. */
    public static final int MAX_MINUTES = 14;

    public static final XpRate EMPTY = new XpRate(List.of(), 0, 0);

    public static final Codec<XpRate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.listOf().optionalFieldOf("minutes", List.of()).forGetter(XpRate::minutes),
            Codec.INT.optionalFieldOf("current_xp", 0).forGetter(XpRate::currentXp),
            Codec.INT.optionalFieldOf("current_seconds", 0).forGetter(XpRate::currentSeconds)
    ).apply(instance, XpRate::new));

    public static final StreamCodec<ByteBuf, XpRate> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), XpRate::minutes,
            ByteBufCodecs.VAR_INT, XpRate::currentXp,
            ByteBufCodecs.VAR_INT, XpRate::currentSeconds,
            XpRate::new);

    public XpRate {
        List<Integer> kept = minutes.size() > MAX_MINUTES ? minutes.subList(0, MAX_MINUTES) : minutes;
        minutes = kept.stream().map(xp -> Math.max(0, xp)).toList();
        currentXp = Math.max(0, currentXp);
        currentSeconds = Math.max(0, Math.min(59, currentSeconds));
    }
}
