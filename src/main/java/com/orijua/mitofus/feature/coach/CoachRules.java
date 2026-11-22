package com.orijua.mitofus.feature.coach;

import com.orijua.mitofus.core.config.CoachBalance;
import com.orijua.mitofus.feature.world.WorldSites;

import java.util.List;

/**
 * The coachman's tariff (own design on a base of 5 gold for a safe trip). On top of the base goes a surcharge for
 * risk, taken from the distance: the farther the road, the more dangerous it is. Prices are in iron.
 */
public final class CoachRules {
    /** The villages a coachman can take you to. */
    public static final List<String> DESTINATIONS = List.of(WorldSites.COVADONGA, WorldSites.EIBAR);

    private CoachRules() {
    }

    /** What the trip costs, in iron, for a road of {@code distance} blocks. */
    public static long fareIron(double distance) {
        return fareIron(CoachBalance.current(), distance);
    }

    /** The fare with the given tariff: the base plus the surcharge by distance, which has a top. */
    public static long fareIron(CoachBalance balance, double distance) {
        long surcharge = (long) Math.floor(Math.max(0.0, distance) / 100.0) * balance.surchargePer100Blocks();
        return balance.baseIron() + Math.min(surcharge, balance.maxSurchargeIron());
    }

    /** True if a coachman takes you to {@code destination} from {@code origin}: a known village, and not the one you are in. */
    public static boolean canGo(String origin, String destination) {
        return DESTINATIONS.contains(destination) && !destination.equals(origin);
    }
}
