package com.orijua.mitofus.feature.effect.status;

import com.orijua.mitofus.feature.effect.Bonus;
import com.orijua.mitofus.feature.effect.BonusType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusValidatorTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mitofus", path);
    }

    private static Bonus applies(Optional<ResourceLocation> status) {
        return new Bonus(BonusType.APPLY_STATUS, 0.2, 0, 0, Optional.empty(), false, status, Optional.empty(), Optional.empty());
    }

    @Test
    void goodDataHasNoProblems() {
        var statuses = Map.of(id("burn"), new StatusRules.Limits(100, 300, 1, 3, 0.5, 0));
        var bonuses = Map.of(id("searing_edge"), List.of(applies(Optional.of(id("burn")))));
        assertTrue(StatusValidator.validate(statuses, bonuses, Set.of(id("damage"), id("crit_chance"))).isEmpty());
    }

    @Test
    void reportsUnknownStatusesMissingStatusesBadCapNamesAndDefaultsAboveMaximums() {
        var statuses = Map.of(id("burn"), new StatusRules.Limits(400, 300, 5, 3, 0.5, 0));
        var bonuses = Map.of(id("a"), List.of(applies(Optional.of(id("mitofus_burning")))), id("b"), List.of(applies(Optional.empty())));
        var problems = StatusValidator.validate(statuses, bonuses, Set.of(id("nothing")));
        assertEquals(5, problems.size(), problems.toString());
    }
}
