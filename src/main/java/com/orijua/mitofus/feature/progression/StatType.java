package com.orijua.mitofus.feature.progression;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

import java.util.Arrays;
import java.util.Optional;

/**
 * The five base stats. Mitofus has strength, stamina, agility, intelligence and dexterity
 * among many others (dignity, view, courage...). What each one does here is project design.
 */
public enum StatType implements StringRepresentable {
    STRENGTH("strength"),
    STAMINA("stamina"),
    AGILITY("agility"),
    INTELLIGENCE("intelligence"),
    DEXTERITY("dexterity");

    public static final Codec<StatType> CODEC = StringRepresentable.fromEnum(StatType::values);
    // CLAMP: a bogus id from a client can never index outside the enum.
    public static final StreamCodec<ByteBuf, StatType> STREAM_CODEC = ByteBufCodecs.idMapper(
            ByIdMap.continuous(StatType::ordinal, values(), ByIdMap.OutOfBoundsStrategy.CLAMP),
            StatType::ordinal);

    private final String name;

    StatType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** The stat with that serialized name (case sensitive), if any. */
    public static Optional<StatType> byName(String name) {
        return Arrays.stream(values()).filter(stat -> stat.name.equals(name)).findFirst();
    }

    public Component displayName() {
        return Component.translatable("stat.mitofus." + name);
    }
}
