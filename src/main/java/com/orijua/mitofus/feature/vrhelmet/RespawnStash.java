package com.orijua.mitofus.feature.vrhelmet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * The player's real-world respawn point (bed or respawn anchor), put aside while they die in Mitofus. Vanilla looks
 * for the respawn point when it respawns the player, wherever they died: it would use up a charge of the respawn
 * anchor or report a missing bed. So the point is cleared at the death and given back to the new player.
 */
public final class RespawnStash {
    private static final String KEY = "respawn";

    private RespawnStash() {
    }

    /** A respawn point as vanilla keeps it. */
    public record Point(ResourceKey<Level> dimension, BlockPos pos, float angle, boolean forced) {
    }

    /** Writes the point under {@code tag}. */
    public static void write(CompoundTag tag, Point point) {
        CompoundTag saved = new CompoundTag();
        saved.putString("dimension", point.dimension().location().toString());
        saved.putLong("pos", point.pos().asLong());
        saved.putFloat("angle", point.angle());
        saved.putBoolean("forced", point.forced());
        tag.put(KEY, saved);
    }

    /** The point written by {@link #write}, if any (and well formed). */
    public static Optional<Point> read(CompoundTag tag) {
        if (!tag.contains(KEY, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag saved = tag.getCompound(KEY);
        ResourceLocation dimension = ResourceLocation.tryParse(saved.getString("dimension"));
        if (dimension == null || !saved.contains("pos", Tag.TAG_LONG)) {
            return Optional.empty();
        }
        return Optional.of(new Point(ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(saved.getLong("pos")),
                saved.getFloat("angle"), saved.getBoolean("forced")));
    }
}
