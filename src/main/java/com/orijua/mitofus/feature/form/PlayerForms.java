package com.orijua.mitofus.feature.form;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a player has of forms: the uses (XP) of each form they have worn, the grade-quest forms they were granted and
 * the one form they have equipped (only one for the whole player). Level-forms are not stored: they follow the class
 * level. Only {@link FormService} writes it.
 */
public record PlayerForms(Map<ResourceLocation, Integer> xp, List<ResourceLocation> granted, Optional<ResourceLocation> equipped) {
    public static final PlayerForms EMPTY = new PlayerForms(Map.of(), List.of(), Optional.empty());

    public static final Codec<PlayerForms> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("xp", Map.of()).forGetter(PlayerForms::xp),
            ResourceLocation.CODEC.listOf().optionalFieldOf("granted", List.of()).forGetter(PlayerForms::granted),
            ResourceLocation.CODEC.optionalFieldOf("equipped").forGetter(PlayerForms::equipped)
    ).apply(instance, PlayerForms::new));

    public static final StreamCodec<ByteBuf, PlayerForms> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public int xpOf(ResourceLocation form) {
        return xp.getOrDefault(form, 0);
    }

    public boolean isGranted(ResourceLocation form) {
        return granted.contains(form);
    }

    public PlayerForms withXp(ResourceLocation form, int value) {
        Map<ResourceLocation, Integer> copy = new HashMap<>(xp);
        copy.put(form, Math.max(0, value));
        return new PlayerForms(Map.copyOf(copy), granted, equipped);
    }

    public PlayerForms withGranted(ResourceLocation form) {
        if (granted.contains(form)) {
            return this;
        }
        List<ResourceLocation> copy = new ArrayList<>(granted);
        copy.add(form);
        return new PlayerForms(xp, List.copyOf(copy), equipped);
    }

    public PlayerForms withEquipped(Optional<ResourceLocation> form) {
        return new PlayerForms(xp, granted, form);
    }
}
