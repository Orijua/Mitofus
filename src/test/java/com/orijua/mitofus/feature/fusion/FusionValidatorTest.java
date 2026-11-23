package com.orijua.mitofus.feature.fusion;

import com.orijua.mitofus.feature.progression.StatType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FusionValidatorTest {
    private static final ResourceLocation A = id("a");
    private static final ResourceLocation B = id("b");
    private static final ResourceLocation H = id("h");
    private static final ResourceLocation SKILL_A = id("skill_a");
    private static final ResourceLocation SKILL_B = id("skill_b");
    private static final ResourceLocation JOB = id("job");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static FusionValidator.ClassFacts facts(boolean legendary, Map<StatType, Integer> stats, Set<ResourceLocation> skills, int trunk) {
        return new FusionValidator.ClassFacts(legendary, stats, skills, Set.of(), trunk);
    }

    private static Map<ResourceLocation, FusionValidator.ClassFacts> classes(FusionValidator.ClassFacts hybrid) {
        return Map.of(A, facts(false, Map.of(StatType.STRENGTH, 3), Set.of(SKILL_A), 0), B, facts(false, Map.of(StatType.STRENGTH, 1), Set.of(SKILL_B), 0), H, hybrid);
    }

    private static final List<FusionValidator.FusionFacts> FUSION = List.of(new FusionValidator.FusionFacts(id("f"), List.of(A, B), H));

    @Test
    void aHybridWorthMoreThanItsPartsIsValid() {
        assertEquals(List.of(), FusionValidator.validate(FUSION, classes(facts(false, Map.of(StatType.STRENGTH, 5), Set.of(SKILL_A, SKILL_B), 2)), Set.of(), List.of()));
    }

    @Test
    void aHybridWorthLessIsReported() {
        List<String> problems = FusionValidator.validate(FUSION, classes(facts(true, Map.of(StatType.STRENGTH, 3), Set.of(SKILL_A), 0)), Set.of(), List.of());
        assertTrue(problems.stream().anyMatch(p -> p.contains("lacks a skill of parent test:b")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("less strength")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("different legendary")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("no talent nodes of its own")), problems.toString());
    }

    @Test
    void missingClassesAreReported() {
        List<String> problems = FusionValidator.validate(FUSION, Map.of(A, facts(false, Map.of(), Set.of(), 0)), Set.of(), List.of());
        assertTrue(problems.stream().anyMatch(p -> p.contains("result class test:h does not exist")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("parent class test:b does not exist")), problems.toString());
    }

    @Test
    void synergiesNeedAJobAndAClassThatExistAndAreNotRepeated() {
        List<FusionValidator.SynergyFacts> synergies = List.of(new FusionValidator.SynergyFacts(id("s1"), A, JOB),
                new FusionValidator.SynergyFacts(id("s2"), A, JOB), new FusionValidator.SynergyFacts(id("s3"), id("ghost"), id("nojob")));
        List<String> problems = FusionValidator.validate(List.of(), classes(facts(false, Map.of(), Set.of(), 1)), Set.of(JOB), synergies);
        assertEquals(3, problems.size(), problems.toString());
    }
}
