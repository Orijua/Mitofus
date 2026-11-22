package com.orijua.mitofus.feature.view;

import com.orijua.mitofus.Mitofus;
import net.minecraft.world.entity.npc.Villager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hidden talents of villagers, for Talent Search. Own design: the six kinds, that Covadonga's residents always hide one, and that elsewhere
 * one villager in {@link #ONE_IN} does. Which talent is fixed by the villager's UUID, so it never changes.
 */
public final class Talents {
    /** Tag carried by the villagers Covadonga places. */
    public static final String COVADONGA_RESIDENT = Mitofus.MODID + ".covadonga_resident";
    static final int ONE_IN = 4;
    static final List<String> KINDS = List.of("mining", "smithing", "carpentry", "cooking", "swordsmanship", "magic");

    private Talents() {
    }

    public static Optional<String> of(Villager villager) {
        return of(villager.getUUID(), villager.getTags().contains(COVADONGA_RESIDENT));
    }

    /** Pure part: the talent a villager with this UUID hides, if any. */
    static Optional<String> of(UUID uuid, boolean covadongaResident) {
        if (!covadongaResident && Math.floorMod(uuid.getMostSignificantBits(), ONE_IN) != 0) {
            return Optional.empty();
        }
        return Optional.of(KINDS.get(Math.floorMod(uuid.getLeastSignificantBits(), KINDS.size())));
    }
}
