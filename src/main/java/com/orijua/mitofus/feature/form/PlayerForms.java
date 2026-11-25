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
 * the one form they have equipped (only one for the whole player) and the bar slot each form skill had when it left the bar. Level-forms are not stored: they follow the class
 * level. Only {@link FormService} writes it.
 */
public record PlayerForms(Map<ResourceLocation, Integer> xp, List<ResourceLocation> granted, Optional<ResourceLocation> equipped,
                          Map<ResourceLocation, Integer> bar) {
    public static final PlayerForms EMPTY = new PlayerForms(Map.of(), List.of(), Optional.empty(), Map.of());

    public static final Codec<PlayerForms> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("xp", Map.of()).forGetter(PlayerForms::xp),
            ResourceLocation.CODEC.listOf().optionalFieldOf("granted", List.of()).forGetter(PlayerForms::granted),
            ResourceLocation.CODEC.optionalFieldOf("equipped").forGetter(PlayerForms::equipped),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("bar", Map.of()).forGetter(PlayerForms::bar)
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
        return new PlayerForms(Map.copyOf(copy), granted, equipped, bar);
    }

    public PlayerForms withGranted(ResourceLocation form) {
        if (granted.contains(form)) {
            return this;
        }
        List<ResourceLocation> copy = new ArrayList<>(granted);
        copy.add(form);
        return new PlayerForms(xp, List.copyOf(copy), equipped, bar);
    }

    public PlayerForms withEquipped(Optional<ResourceLocation> form) {
        return new PlayerForms(xp, granted, form, bar);
    }

    /** The slot the skill sat in when its form was taken off, if it was in one. */
    public Optional<Integer> barSlot(ResourceLocation skill) {
        return Optional.ofNullable(bar.get(skill));
    }

    public PlayerForms withBar(ResourceLocation skill, Optional<Integer> slot) {
        Map<ResourceLocation, Integer> copy = new HashMap<>(bar);
        slot.ifPresentOrElse(value -> copy.put(skill, value), () -> copy.remove(skill));
        return new PlayerForms(xp, granted, equipped, Map.copyOf(copy));
    }
}
