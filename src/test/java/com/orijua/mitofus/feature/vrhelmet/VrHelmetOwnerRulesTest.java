package com.orijua.mitofus.feature.vrhelmet;

import com.orijua.mitofus.core.config.VrHelmetBalance;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VrHelmetOwnerRulesTest {
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test
    void aVrHelmetWithoutOwnerBindsToWhoeverUsesIt() {
        assertEquals(VrHelmetOwnerRules.Access.BIND, VrHelmetOwnerRules.access(Optional.empty(), A));
    }

    @Test
    void theOwnerUsesItAndOthersCannot() {
        assertEquals(VrHelmetOwnerRules.Access.OWNER, VrHelmetOwnerRules.access(Optional.of(A), A));
        assertEquals(VrHelmetOwnerRules.Access.STRANGER, VrHelmetOwnerRules.access(Optional.of(A), B));
    }

    @Test
    void unbindingCosts200PercentOfThePriceOfTheTier() {
        VrHelmetBalance balance = VrHelmetBalance.DEFAULT;
        assertEquals(2_000L, balance.unbindPrice(VrHelmetTier.ADVANCED));
        assertEquals(10_000L, balance.unbindPrice(VrHelmetTier.DIAMOND));
        assertEquals(700L, balance.unbindPrice(VrHelmetTier.BASIC), "the basic one uses the reference price of 350");
    }

    @Test
    void unbindingFollowsTheConfiguredPercentAndRoundsUp() {
        VrHelmetBalance free = new VrHelmetBalance(55, 8, 75, 6, 3000, 15000, 10, 10, 0.9, 30, 0, 1000);
        assertEquals(0L, free.unbindPrice(VrHelmetTier.ADVANCED));
        VrHelmetBalance odd = new VrHelmetBalance(55, 8, 75, 6, 3001, 15000, 10, 10, 0.9, 30, 110, 999);
        assertEquals(3_302L, odd.unbindPrice(VrHelmetTier.ADVANCED), "3301.1 rounds up");
        assertEquals(1_099L, odd.unbindPrice(VrHelmetTier.BASIC), "1098.9 rounds up");
        assertThrows(IllegalArgumentException.class, () -> new VrHelmetBalance(55, 8, 75, 6, 3000, 15000, 10, 10, 0.9, 30, -1, 1000));
        assertThrows(IllegalArgumentException.class, () -> new VrHelmetBalance(55, 8, 75, 6, 3000, 15000, 10, 10, 0.9, 30, 1001, 1000));
    }
}
