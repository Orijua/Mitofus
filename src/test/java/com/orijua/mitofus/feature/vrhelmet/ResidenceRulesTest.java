package com.orijua.mitofus.feature.vrhelmet;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResidenceRulesTest {
    private static Residence at(String site, long changedAt) {
        return new Residence(Optional.of(new BlockPos(1, 64, 1)), site, changedAt);
    }

    @Test
    void withoutResidenceAnyVillageWorks() {
        assertEquals(ResidenceRules.Result.OK, ResidenceRules.check(Residence.NONE, "covadonga", 0L));
    }

    @Test
    void theSameVillageIsRefused() {
        assertEquals(ResidenceRules.Result.SAME_PLACE, ResidenceRules.check(at("covadonga", 0L), "covadonga", 99_999L));
    }

    @Test
    void anotherVillageWaitsOneGameDay() {
        Residence current = at("covadonga", 1_000L);
        assertEquals(ResidenceRules.Result.COOLDOWN, ResidenceRules.check(current, "eibar", 1_000L + ResidenceRules.cooldownTicks() - 1));
        assertEquals(ResidenceRules.Result.OK, ResidenceRules.check(current, "eibar", 1_000L + ResidenceRules.cooldownTicks()));
    }

    @Test
    void aClockThatWentBackDoesNotBlockForever() {
        assertEquals(ResidenceRules.Result.OK, ResidenceRules.check(at("covadonga", 50_000L), "eibar", 10L));
        assertEquals(0L, ResidenceRules.ticksLeft(at("covadonga", 50_000L), 10L));
    }

    @Test
    void ticksLeftCountsDown() {
        assertEquals(ResidenceRules.cooldownTicks() - 100L, ResidenceRules.ticksLeft(at("covadonga", 0L), 100L));
        assertEquals(0L, ResidenceRules.ticksLeft(Residence.NONE, 100L));
    }
}
