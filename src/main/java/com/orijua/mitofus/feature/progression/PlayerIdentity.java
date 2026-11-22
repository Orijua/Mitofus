package com.orijua.mitofus.feature.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Who the player is in Mitofus: the titles they have earned. Classes and job are in {@link PlayerClasses}. */
public record PlayerIdentity(List<ResourceLocation> titles) {
    public static final PlayerIdentity DEFAULT = new PlayerIdentity(List.of());

    public static final Codec<PlayerIdentity> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("titles", List.of()).forGetter(PlayerIdentity::titles)
    ).apply(instance, PlayerIdentity::new));

    public static final StreamCodec<ByteBuf, PlayerIdentity> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public PlayerIdentity withTitle(ResourceLocation id) {
        List<ResourceLocation> copy = new ArrayList<>(titles);
        copy.add(id);
        return new PlayerIdentity(List.copyOf(copy));
    }

    public boolean hasTitle(ResourceLocation id) {
        return titles.contains(id);
    }
}
