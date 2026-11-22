package com.orijua.mitofus.feature.vrhelmet;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VrHelmetTierTest {
    @Test
    void theCodecRoundTripsEveryTier() {
        for (VrHelmetTier tier : VrHelmetTier.values()) {
            var json = VrHelmetTier.CODEC.encodeStart(JsonOps.INSTANCE, tier).getOrThrow();
            assertEquals(tier, VrHelmetTier.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
        assertEquals("basic", VrHelmetTier.BASIC.getSerializedName());
    }

    @Test
    void anUnknownNameIsRejected() {
        var json = JsonOps.INSTANCE.createString("platinum");
        assertTrue(VrHelmetTier.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test
    void previousIsTheTierItIsBuiltFrom() {
        assertEquals(Optional.empty(), VrHelmetTier.BASIC.previous());
        assertEquals(Optional.of(VrHelmetTier.BASIC), VrHelmetTier.ADVANCED.previous());
        assertEquals(Optional.of(VrHelmetTier.ADVANCED), VrHelmetTier.DIAMOND.previous());
    }

    @Test
    void atLeastFollowsTheOrder() {
        assertTrue(VrHelmetTier.DIAMOND.atLeast(VrHelmetTier.ADVANCED));
        assertTrue(VrHelmetTier.ADVANCED.atLeast(VrHelmetTier.ADVANCED));
        assertFalse(VrHelmetTier.BASIC.atLeast(VrHelmetTier.ADVANCED));
        assertTrue(VrHelmetTier.BASIC.atLeast(VrHelmetTier.BASIC));
    }

    @Test
    void theSerializedNamesAreTheStableIds() {
        assertEquals("basic", VrHelmetTier.BASIC.getSerializedName());
        assertEquals("advanced", VrHelmetTier.ADVANCED.getSerializedName());
        assertEquals("diamond", VrHelmetTier.DIAMOND.getSerializedName());
    }

    @Test
    void theStreamCodecRoundTripsEveryTier() {
        for (VrHelmetTier tier : VrHelmetTier.values()) {
            ByteBuf buf = Unpooled.buffer();
            VrHelmetTier.STREAM_CODEC.encode(buf, tier);
            assertEquals(tier, VrHelmetTier.STREAM_CODEC.decode(buf), tier.toString());
        }
    }

    @Test
    void anIdOutOfRangeDecodesAsBasicInsteadOfThrowing() {
        for (int id : new int[]{VrHelmetTier.values().length, 99, -1, Integer.MAX_VALUE}) {
            ByteBuf buf = Unpooled.buffer();
            net.minecraft.network.VarInt.write(buf, id);
            assertEquals(VrHelmetTier.BASIC, VrHelmetTier.STREAM_CODEC.decode(buf), "id " + id);
        }
    }

    @Test
    void theNameKeyFollowsTheSerializedName() {
        for (VrHelmetTier tier : VrHelmetTier.values()) {
            TranslatableContents contents = (TranslatableContents) tier.tierName().getContents();
            assertEquals("vrhelmet_tier.mitofus." + tier.getSerializedName(), contents.getKey());
        }
    }
}
