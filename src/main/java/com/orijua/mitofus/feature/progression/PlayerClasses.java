package com.orijua.mitofus.feature.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The classes a player has placed in their slots (see {@link ClassSlotRules}) and their one job. Only
 * {@link ClassService} writes it.
 */
public record PlayerClasses(List<ClassSlot> slots, Optional<ResourceLocation> job) {
    public static final PlayerClasses DEFAULT = new PlayerClasses(List.of(), Optional.empty());

    public static final Codec<PlayerClasses> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ClassSlot.CODEC.listOf().optionalFieldOf("slots", List.of()).forGetter(PlayerClasses::slots),
            ResourceLocation.CODEC.optionalFieldOf("job").forGetter(PlayerClasses::job)
    ).apply(instance, PlayerClasses::new));

    public static final StreamCodec<ByteBuf, PlayerClasses> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public Optional<ClassSlot> slot(int index) {
        return slots.stream().filter(s -> s.slot() == index).findFirst();
    }

    public boolean hasClass(ResourceLocation classId) {
        return slots.stream().anyMatch(s -> s.classId().equals(classId));
    }

    /** The class ids in slot order. */
    public List<ResourceLocation> classIds() {
        return slots.stream().map(ClassSlot::classId).toList();
    }

    /** Puts {@code placed} in its slot (replacing what was there), keeping the slots in order. */
    public PlayerClasses withSlot(ClassSlot placed) {
        List<ClassSlot> copy = new ArrayList<>(slots);
        copy.removeIf(s -> s.slot() == placed.slot());
        copy.add(placed);
        copy.sort(Comparator.comparingInt(ClassSlot::slot));
        return new PlayerClasses(List.copyOf(copy), job);
    }

    public PlayerClasses withJob(ResourceLocation id) {
        return new PlayerClasses(slots, Optional.of(id));
    }
}
