package com.orijua.mitofus.core.config;

import com.orijua.mitofus.feature.vrhelmet.VrHelmetRules;
import com.orijua.mitofus.feature.vrhelmet.LogoutRules;
import com.orijua.mitofus.feature.fatigue.FatigueRules;
import com.orijua.mitofus.feature.npc.NpcGrade;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBalanceTest {
    private static RuleBalance with(double connect, double named, double semi, double danger, double combatWindow) {
        RuleBalance d = RuleBalance.DEFAULT;
        return new RuleBalance(connect, d.logoutSeconds(), danger, d.maxDistance(), d.stillTolerance(), named, semi,
                d.boneLordMinions(), d.boneLordRegenPercent(), d.boneLordOutOfCombatSeconds(), combatWindow,
                d.protectionSeconds(), d.restMaxMove(), d.workWindowSeconds(), d.terminalRange(), d.eliteDropMinChance(), d.eliteDropDecay(),
                d.smithLevel(), d.chiefLevel(), d.velascoLevel(), d.menciaLevel());
    }

    @Test
    void defaultsAreTheOldFixedValues() {
        RuleBalance d = RuleBalance.DEFAULT;
        assertEquals(60, d.connectTicks());
        assertEquals(60, d.logoutTicks());
        assertEquals(60, d.dangerTicks());
        assertEquals(3.0, d.maxDistance());
        assertEquals(0.5, d.stillTolerance());
        assertEquals(0.01, d.namedChance());
        assertEquals(0.09, d.semiNamedChance());
        assertEquals(2, d.boneLordMinions());
        assertEquals(200, d.boneLordOutOfCombatTicks());
        assertEquals(200, d.combatWindowTicks());
        assertEquals(200, d.protectionTicks());
        assertEquals(60, d.workWindowTicks());
        assertEquals(0.25, d.restMaxMove() * d.restMaxMove(), 1e-12);
        assertEquals(64.0, d.terminalRange() * d.terminalRange(), 1e-12);
        assertEquals(0.00005, d.eliteDropMinChance());
        assertEquals(0.92, d.eliteDropDecay());
        assertEquals(15, d.smithLevel());
        assertEquals(30, d.chiefLevel());
        assertEquals(25, d.velascoLevel());
        assertEquals(50, d.menciaLevel());
    }

    @Test
    void theOldConstantsStillMatchTheDefaults() {
        RuleBalance d = RuleBalance.DEFAULT;
        assertEquals(VrHelmetRules.CONNECT_TICKS, d.connectTicks());
        assertEquals(LogoutRules.COUNTDOWN_TICKS, d.logoutTicks());
        assertEquals(LogoutRules.DANGER_TICKS, d.dangerTicks());
        assertEquals(VrHelmetRules.MAX_DISTANCE, d.maxDistance());
        assertEquals(VrHelmetRules.STILL_TOLERANCE, d.stillTolerance());
        assertEquals(NpcGrade.NAMED_CHANCE, d.namedChance());
        assertEquals(NpcGrade.SEMI_NAMED_CHANCE, d.semiNamedChance());
        assertEquals(FatigueRules.COMBAT_WINDOW_TICKS, d.combatWindowTicks());
        assertEquals(FatigueRules.PROTECTION_TICKS, d.protectionTicks());
        assertEquals(FatigueRules.WORK_WINDOW_TICKS, d.workWindowTicks());
    }

    @Test
    void chancesThatPassOneAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> with(3.0, 0.6, 0.5, 3.0, 10.0));
        assertThrows(IllegalArgumentException.class, () -> with(3.0, Double.NaN, 0.1, 3.0, 10.0));
        with(3.0, 0.5, 0.5, 3.0, 10.0);
    }

    @Test
    void waitsHaveAtLeastOneTick() {
        assertThrows(IllegalArgumentException.class, () -> with(0.0, 0.01, 0.09, 3.0, 10.0));
        assertEquals(1, with(0.05, 0.01, 0.09, 3.0, 10.0).connectTicks());
    }

    @Test
    void theRulesFollowTheOverride() {
        RuleBalance custom = with(3.0, 0.5, 0.5, 0.0, 0.0);
        RuleBalance.withOverride(custom, () -> {
            assertEquals(NpcGrade.NAMED, NpcGrade.roll(0.4));
            assertEquals(NpcGrade.SEMI_NAMED, NpcGrade.roll(0.6));
            assertFalse(LogoutRules.inDanger(100, java.util.OptionalLong.of(100)));
            assertFalse(FatigueRules.inCombat(100, java.util.OptionalLong.of(100)));
        });
        assertTrue(LogoutRules.inDanger(100, java.util.OptionalLong.of(100)));
        assertEquals(NpcGrade.NORMAL, NpcGrade.roll(0.4));
    }
}
