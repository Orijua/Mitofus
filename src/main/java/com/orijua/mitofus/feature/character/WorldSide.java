package com.orijua.mitofus.feature.character;

import com.mojang.serialization.Codec;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;

/** Which of the two characters a level belongs to: the real world (overworld, Nether, End) or Mitofus. */
public enum WorldSide implements StringRepresentable {
    REAL("real"),
    MITOFUS("mitofus");

    public static final Codec<WorldSide> CODEC = StringRepresentable.fromEnum(WorldSide::values);

    private final String name;

    WorldSide(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** The single definition of "which world is this level": Mitofus, or else the real world. */
    public static WorldSide of(Level level) {
        return MitofusWorld.is(level) ? MITOFUS : REAL;
    }
}
