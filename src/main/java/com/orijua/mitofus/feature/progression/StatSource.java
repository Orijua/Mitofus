package com.orijua.mitofus.feature.progression;

import net.minecraft.network.chat.Component;

/** Where a {@link StatModifier} comes from, as shown in the stat tooltip ("Warrior", "Zombie Hunter"...). */
public record StatSource(Component name) {
    /** The stats a character starts with, before any point is spent. */
    public static final StatSource BASE = new StatSource(Component.translatable("screen.mitofus.stat_source.base"));
    public static final StatSource ALLOCATED = new StatSource(Component.translatable("screen.mitofus.stat_source.allocated"));
}
