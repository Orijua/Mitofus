package com.orijua.mitofus.feature.skill;

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
 * Skills a player has learned, which ones sit in the slots (3 quick keys; up to 5 pages of 9 in combat mode, see
 * {@link SlotRules}), and when each comes off cooldown (game time).
 */
public record PlayerSkills(List<ResourceLocation> learned, List<Optional<ResourceLocation>> slots, Map<ResourceLocation, Long> cooldownUntil) {
    /** The slots that have their own quick key outside combat mode. */
    public static final int SLOT_COUNT = 3;
    public static final PlayerSkills EMPTY = new PlayerSkills(List.of(),
            normalize(List.of()), Map.of());

    // An empty slot is stored as "". Saved data of another length (the 3 slots of older saves, or hand-edited) is padded or cut to SlotRules.MAX_SLOTS, so the old slots stay where they were.
    private static final Codec<List<Optional<ResourceLocation>>> SLOTS_CODEC = Codec.STRING.listOf().xmap(
            list -> normalize(list.stream().map(s -> s.isEmpty() ? Optional.<ResourceLocation>empty() : Optional.ofNullable(ResourceLocation.tryParse(s))).toList()),
            list -> list.stream().map(slot -> slot.map(ResourceLocation::toString).orElse("")).toList());

    private static List<Optional<ResourceLocation>> normalize(List<Optional<ResourceLocation>> slots) {
        List<Optional<ResourceLocation>> result = new ArrayList<>(slots.subList(0, Math.min(slots.size(), SlotRules.MAX_SLOTS)));
        while (result.size() < SlotRules.MAX_SLOTS) {
            result.add(Optional.empty());
        }
        return List.copyOf(result);
    }

    public static final Codec<PlayerSkills> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.listOf().fieldOf("learned").forGetter(PlayerSkills::learned),
            SLOTS_CODEC.fieldOf("slots").forGetter(PlayerSkills::slots),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.LONG).fieldOf("cooldown_until").forGetter(PlayerSkills::cooldownUntil)
    ).apply(instance, PlayerSkills::new));

    public static final StreamCodec<ByteBuf, PlayerSkills> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public boolean knows(ResourceLocation skill) {
        return learned.contains(skill);
    }

    public Optional<ResourceLocation> slot(int index) {
        return index >= 0 && index < slots.size() ? slots.get(index) : Optional.empty();
    }

    public long cooldownRemaining(ResourceLocation skill, long gameTime) {
        return Math.max(0, cooldownUntil.getOrDefault(skill, 0L) - gameTime);
    }

    /** Learns the skill and puts it in the first free slot of a level-0 player (the 3 starting ones), if any. */
    public PlayerSkills learn(ResourceLocation skill) {
        return learn(skill, 0);
    }

    /** Learns the skill and puts it in the first free slot that is open for a player of {@code level}, if any. */
    public PlayerSkills learn(ResourceLocation skill, int level) {
        List<ResourceLocation> newLearned = new ArrayList<>(learned);
        newLearned.add(skill);
        PlayerSkills result = new PlayerSkills(List.copyOf(newLearned), slots, cooldownUntil);
        for (int slot = 0; slot < slots.size(); slot++) {
            if (slots.get(slot).isEmpty() && SlotRules.isOpen(level, slot)) {
                return result.withSlot(slot, Optional.of(skill));
            }
        }
        return result;
    }

    public PlayerSkills withSlot(int index, Optional<ResourceLocation> skill) {
        List<Optional<ResourceLocation>> newSlots = new ArrayList<>(slots);
        skill.ifPresent(s -> newSlots.replaceAll(existing -> existing.equals(Optional.of(s)) ? Optional.empty() : existing));
        newSlots.set(index, skill);
        return new PlayerSkills(learned, List.copyOf(newSlots), cooldownUntil);
    }

    public PlayerSkills withCooldown(ResourceLocation skill, long until) {
        Map<ResourceLocation, Long> newCooldowns = new HashMap<>(cooldownUntil);
        newCooldowns.put(skill, until);
        return new PlayerSkills(learned, slots, Map.copyOf(newCooldowns));
    }
}
