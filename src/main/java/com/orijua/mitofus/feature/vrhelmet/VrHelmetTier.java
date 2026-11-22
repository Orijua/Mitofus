package com.orijua.mitofus.feature.vrhelmet;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

import java.util.Optional;

/** The tier of a VR helmet: basic, advanced or diamond. Each one is bought on top of the previous one. */
public enum VrHelmetTier implements StringRepresentable {
    BASIC("basic"),
    ADVANCED("advanced"),
    DIAMOND("diamond");

    public static final Codec<VrHelmetTier> CODEC = StringRepresentable.fromEnum(VrHelmetTier::values);
    public static final StreamCodec<ByteBuf, VrHelmetTier> STREAM_CODEC = ByteBufCodecs.idMapper(
            ByIdMap.continuous(VrHelmetTier::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO), VrHelmetTier::ordinal);

    private final String id;

    VrHelmetTier(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** The tier this one is built from; empty for the basic one. */
    public Optional<VrHelmetTier> previous() {
        return ordinal() == 0 ? Optional.empty() : Optional.of(values()[ordinal() - 1]);
    }

    /** Whether this tier is {@code other} or a better one. */
    public boolean atLeast(VrHelmetTier other) {
        return compareTo(other) >= 0;
    }

    /** The name of the tier, translated. */
    public Component tierName() {
        return Component.translatable("vrhelmet_tier.mitofus." + id);
    }
}
