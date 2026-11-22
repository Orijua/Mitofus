package com.orijua.mitofus.feature.vrhelmet;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RespawnStashTest {
    @Test
    void aPointSurvivesTheRoundTrip() {
        CompoundTag tag = new CompoundTag();
        RespawnStash.Point point = new RespawnStash.Point(Level.NETHER, new BlockPos(-12, 70, 3456), 90.0f, true);
        RespawnStash.write(tag, point);
        assertEquals(point, RespawnStash.read(tag).orElseThrow());
    }

    @Test
    void withoutAPointOrWithAMalformedOneThereIsNothing() {
        assertTrue(RespawnStash.read(new CompoundTag()).isEmpty());
        CompoundTag bad = new CompoundTag();
        CompoundTag inner = new CompoundTag();
        inner.putString("dimension", "not a valid:::id");
        bad.put("respawn", inner);
        assertTrue(RespawnStash.read(bad).isEmpty());
    }
}
