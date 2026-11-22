package com.orijua.mitofus.feature.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * The appraisal state of a piece that has not been appraised in full: the seed that fixes the order its data are revealed
 * in, and the keys already revealed (see {@link AppraisalRules}). A piece without this component is appraised: it is the
 * default for everything forged by hand and for every old piece. Keys are stored, not indexes, so a piece whose
 * options change in a later version still reads right.
 */
public record Appraisal(long seed, List<String> revealed) {
    public static final Codec<Appraisal> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("seed").forGetter(Appraisal::seed),
            Codec.STRING.listOf().optionalFieldOf("revealed", List.of()).forGetter(Appraisal::revealed)
    ).apply(instance, Appraisal::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Appraisal> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Appraisal::seed,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Appraisal::revealed,
            Appraisal::new);

    public Appraisal {
        revealed = List.copyOf(revealed);
    }
}
