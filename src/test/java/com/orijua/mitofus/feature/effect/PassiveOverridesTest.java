package com.orijua.mitofus.feature.effect;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassiveOverridesTest {
    private static final ResourceLocation A = ResourceLocation.parse("mitofus:a");
    private static final ResourceLocation B = ResourceLocation.parse("mitofus:b");

    @Test
    void emptyChangesNothing() {
        assertTrue(PassiveOverrides.EMPTY.isEmpty());
        assertEquals(List.of(A), PassiveOverrides.EMPTY.resolve(List.of(A)));
    }

    @Test
    void grantedAreAddedOnceAfterTheClassOnes() {
        PassiveOverrides overrides = PassiveOverrides.EMPTY.give(B).give(B).give(A);
        assertEquals(List.of(A, B), overrides.resolve(List.of(A)));
    }

    @Test
    void removingHidesAClassPassiveAndGivingBringsItBack() {
        PassiveOverrides hidden = PassiveOverrides.EMPTY.remove(A);
        assertEquals(List.of(), hidden.resolve(List.of(A)));
        assertEquals(List.of(A), hidden.give(A).resolve(List.of(A)));
    }

    @Test
    void removingAGrantedPassiveDropsIt() {
        assertEquals(List.of(), PassiveOverrides.EMPTY.give(B).remove(B).resolve(List.of()));
    }
}
