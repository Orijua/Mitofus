package com.orijua.mitofus.feature.world;

import com.orijua.mitofus.Mitofus;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Optional;

/** The Mitofus dimension: the game world, where Covadonga and the places are. The overworld is the real world. */
public final class MitofusWorld {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, Mitofus.id("mitofus"));

    /** For tests: the game-test server drops the datapack's dimensions, so a test lends the level that plays Mitofus. */
    private static volatile ServerLevel standIn;

    /** What {@link #restoreStandIn()} gives back: in the game-test server, the overworld plays Mitofus by default. */
    private static volatile ServerLevel testDefault;

    private MitofusWorld() {
    }

    public static boolean is(Level level) {
        return level.dimension() == LEVEL || level == standIn;
    }

    /** Same as {@link #is(Level)}, by dimension key (for events that only carry the destination). */
    public static boolean is(ResourceKey<Level> key) {
        return key == LEVEL || (standIn != null && standIn.dimension() == key);
    }

    public static Optional<ServerLevel> of(MinecraftServer server) {
        ServerLevel level = server.getLevel(LEVEL);
        return Optional.ofNullable(level != null ? level : standIn);
    }

    /** Also for tests: {@code null} means that no level is Mitofus. */
    public static void standIn(ServerLevel level) {
        standIn = level;
    }

    /** The level that plays Mitofus by default in the game-test server ({@code null} outside it). */
    public static void testDefault(ServerLevel level) {
        testDefault = level;
    }

    /** Puts the standIn back to the default of the test server. */
    public static void restoreStandIn() {
        standIn = testDefault;
    }
}
