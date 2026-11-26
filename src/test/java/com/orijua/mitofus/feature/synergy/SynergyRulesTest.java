package com.orijua.mitofus.feature.synergy;

import com.orijua.mitofus.feature.progression.ClassGrade;
import com.orijua.mitofus.feature.progression.StatType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SynergyRulesTest {
    private static final ResourceLocation SWORD = id("swordsman");
    private static final ResourceLocation MAGE = id("mage");
    private static final ResourceLocation SMITH = id("blacksmith");
    private static final ResourceLocation TAILOR = id("tailor");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static SynergyDefinition synergy(ResourceLocation playerClass, ResourceLocation job, Map<StatType, Integer>... steps) {
        ClassGrade[] grades = {ClassGrade.APPRENTICE, ClassGrade.EXPERT, ClassGrade.GRAND_MASTER};
        int[] levels = {10, 30, 60};
        List<SynergyDefinition.Step> list = new java.util.ArrayList<>();
        for (int i = 0; i < steps.length; i++) {
            list.add(new SynergyDefinition.Step(grades[i], levels[i], steps[i]));
        }
        return new SynergyDefinition(Optional.ofNullable(playerClass), job, list);
    }

    private static final SynergyDefinition SWORD_SMITH = synergy(SWORD, SMITH,
            Map.of(StatType.STRENGTH, 4), Map.of(StatType.STRENGTH, 8), Map.of(StatType.STRENGTH, 12));
    private static final SynergyDefinition MAGE_SMITH = synergy(MAGE, SMITH, Map.of(StatType.INTELLIGENCE, 2), Map.of(StatType.INTELLIGENCE, 4));
    private static final SynergyDefinition DEFAULT_SMITH = synergy(null, SMITH, Map.of(StatType.STAMINA, 1), Map.of(StatType.STAMINA, 2));

    private static final SynergyRules.Index INDEX = new SynergyRules.Index(
            Map.of(SWORD, Map.of(SMITH, SWORD_SMITH), MAGE, Map.of(SMITH, MAGE_SMITH)), Map.of(SMITH, DEFAULT_SMITH));

    @Test
    void nothingBelowTheFirstStepAndTheBestReachedStepApplies() {
        assertTrue(SynergyRules.reached(SWORD_SMITH, ClassGrade.APPRENTICE, 9).isEmpty(), "job level 10 is the minimum");
        assertEquals(Map.of(StatType.STRENGTH, 4), SynergyRules.reached(SWORD_SMITH, ClassGrade.APPRENTICE, 10));
        assertEquals(Map.of(StatType.STRENGTH, 4), SynergyRules.reached(SWORD_SMITH, ClassGrade.EXPERT, 29), "needs both the grade and the job level");
        assertEquals(Map.of(StatType.STRENGTH, 8), SynergyRules.reached(SWORD_SMITH, ClassGrade.MASTER, 59), "a higher grade also counts");
        assertEquals(Map.of(StatType.STRENGTH, 12), SynergyRules.reached(SWORD_SMITH, ClassGrade.GRAND_MASTER, 99));
        assertEquals(2, SynergyRules.stepsReached(SWORD_SMITH, ClassGrade.EXPERT, 40));
    }

    @Test
    void aPairWithNoEntryFallsBackToTheJobsDefault() {
        assertEquals(Map.of(StatType.STAMINA, 1), SynergyRules.bonus(INDEX, id("archer"), SMITH, ClassGrade.APPRENTICE, 10));
        assertTrue(SynergyRules.bonus(INDEX, SWORD, TAILOR, ClassGrade.APPRENTICE, 99).isEmpty(), "no default for that job: nothing");
    }
}
