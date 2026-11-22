package com.orijua.mitofus.core.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The one place that states the factory numbers of {@link Balance}: the other tests read them from the balance they use. */
class BalanceTest {
    private static final Balance D = Balance.DEFAULT;

    @Test
    void theDefaultIsTheDesignedOne() {
        assertAll(
                () -> assertEquals(10, D.statPointsPerLevel()),
                () -> assertEquals(1.0, D.killXpMultiplier()),
                () -> assertEquals(2.0, D.killXpPerAttackDamage()),
                () -> assertEquals(1.0, D.killXpPerArmor()),
                () -> assertEquals(0.25, D.spawnerXpMultiplier()),
                () -> assertEquals(50, D.xpBase()),
                () -> assertEquals(25, D.xpPerLevel()),
                () -> assertEquals(20.0f, D.baseMana()),
                () -> assertEquals(2.5f, D.manaPerIntelligence()),
                () -> assertEquals(1.0f, D.baseManaRegen()),
                () -> assertEquals(0.1f, D.manaRegenPerIntelligence()),
                () -> assertEquals(0.95, D.enhanceBaseChance()),
                () -> assertEquals(0.09, D.enhanceChanceLossPerLevel()),
                () -> assertEquals(0.0025, D.enhanceChancePerDexterity()),
                () -> assertEquals(0.05, D.enhanceMinChance()),
                () -> assertEquals(0.3, D.deathXpLoss(), "dying costs 30 % of the level XP by default"),
                () -> assertEquals(2, D.mobLevelSpread()),
                () -> assertEquals(0.08, D.mobHealthPerLevel()),
                () -> assertEquals(0.05, D.mobDamagePerLevel()),
                () -> assertEquals(0.05, D.eliteChance()),
                () -> assertEquals(0.5, D.eliteStatBonus()),
                () -> assertEquals(3.0, D.eliteXpMultiplier()),
                () -> assertEquals(0.25, D.eliteEquipmentChance()),
                () -> assertEquals(Balance.ContestLoss.AUTO, D.contestLoss()));
    }
}
