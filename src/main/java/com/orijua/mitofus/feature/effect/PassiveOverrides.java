package com.orijua.mitofus.feature.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Passives that a debug command gave or took away on top of what the class gives ({@code /mitofus passive}).
 * Empty in normal play. {@code granted} count at any level and with any class; {@code revoked} hide a class passive.
 */
public record PassiveOverrides(List<ResourceLocation> granted, List<ResourceLocation> revoked) {
    public static final PassiveOverrides EMPTY = new PassiveOverrides(List.of(), List.of());

    public static final Codec<PassiveOverrides> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("granted", List.of()).forGetter(PassiveOverrides::granted),
            ResourceLocation.CODEC.listOf().optionalFieldOf("revoked", List.of()).forGetter(PassiveOverrides::revoked)
    ).apply(instance, PassiveOverrides::new));

    public static final StreamCodec<ByteBuf, PassiveOverrides> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public boolean isEmpty() {
        return granted.isEmpty() && revoked.isEmpty();
    }

    public PassiveOverrides give(ResourceLocation id) {
        List<ResourceLocation> newGranted = new ArrayList<>(granted);
        if (!newGranted.contains(id)) {
            newGranted.add(id);
        }
        List<ResourceLocation> newRevoked = new ArrayList<>(revoked);
        newRevoked.remove(id);
        return new PassiveOverrides(List.copyOf(newGranted), List.copyOf(newRevoked));
    }

    public PassiveOverrides remove(ResourceLocation id) {
        List<ResourceLocation> newGranted = new ArrayList<>(granted);
        newGranted.remove(id);
        List<ResourceLocation> newRevoked = new ArrayList<>(revoked);
        if (!newRevoked.contains(id)) {
            newRevoked.add(id);
        }
        return new PassiveOverrides(List.copyOf(newGranted), List.copyOf(newRevoked));
    }

    /** The ids the player ends up with: the class ones without the revoked, then the granted ones not already there. */
    public List<ResourceLocation> resolve(List<ResourceLocation> fromClass) {
        List<ResourceLocation> result = new ArrayList<>();
        for (ResourceLocation id : fromClass) {
            if (!revoked.contains(id) && !result.contains(id)) {
                result.add(id);
            }
        }
        for (ResourceLocation id : granted) {
            if (!result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }
}
