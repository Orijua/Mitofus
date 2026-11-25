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
 * The branches (classes) a player holds in their slots (see {@link ClassSlotRules}), the branches they swapped out and that
 * sleep with everything they had ({@code dormant}), the slots whose free change is already used ({@code freeUsed}, slot
 * indexes) and their one job. Only {@link ClassService} writes it.
 */
public record PlayerClasses(List<ClassSlot> slots, Optional<ResourceLocation> job, List<ClassSlot> dormant, List<Integer> freeUsed) {
    public static final PlayerClasses DEFAULT = new PlayerClasses(List.of(), Optional.empty(), List.of(), List.of());

    public static final Codec<PlayerClasses> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ClassSlot.CODEC.listOf().optionalFieldOf("slots", List.of()).forGetter(PlayerClasses::slots),
            ResourceLocation.CODEC.optionalFieldOf("job").forGetter(PlayerClasses::job),
            ClassSlot.CODEC.listOf().optionalFieldOf("dormant", List.of()).forGetter(PlayerClasses::dormant),
            Codec.INT.listOf().optionalFieldOf("free_used", List.of()).forGetter(PlayerClasses::freeUsed)
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
        return new PlayerClasses(List.copyOf(copy), job, dormant, freeUsed);
    }

    /** The swapped-out branch {@code classId}, if it sleeps. */
    public Optional<ClassSlot> dormant(ResourceLocation classId) {
        return dormant.stream().filter(s -> s.classId().equals(classId)).findFirst();
    }

    /** The list of dormant branches without {@code classId}. */
    public PlayerClasses withoutDormant(ResourceLocation classId) {
        return withDormant(dormant.stream().filter(s -> !s.classId().equals(classId)).toList());
    }

    public List<ClassSlot> dormantBranches() {
        return dormant;
    }

    public PlayerClasses withDormant(List<ClassSlot> sleeping) {
        return new PlayerClasses(slots, job, List.copyOf(sleeping), freeUsed);
    }

    /** Whether the slot's one free change was already used. */
    public boolean freeChangeUsed(int slot) {
        return freeUsed.contains(slot);
    }

    public PlayerClasses withFreeUsed(int slot) {
        if (freeUsed.contains(slot)) {
            return this;
        }
        List<Integer> copy = new ArrayList<>(freeUsed);
        copy.add(slot);
        return new PlayerClasses(slots, job, dormant, List.copyOf(copy));
    }

    public PlayerClasses withJob(ResourceLocation id) {
        return new PlayerClasses(slots, Optional.of(id), dormant, freeUsed);
    }
}
