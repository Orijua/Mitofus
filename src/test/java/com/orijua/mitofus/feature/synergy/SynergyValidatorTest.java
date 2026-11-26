package com.orijua.mitofus.feature.synergy;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SynergyValidatorTest {
    private static final ResourceLocation A = ResourceLocation.parse("mitofus:a");
    private static final ResourceLocation JOB = ResourceLocation.parse("mitofus:job");

    private static ResourceLocation id(String path) {
        return ResourceLocation.parse("mitofus:" + path);
    }

    @Test
    void synergiesNeedAJobAndAClassThatExistAndAreNotRepeated() {
        List<SynergyValidator.SynergyFacts> synergies = List.of(new SynergyValidator.SynergyFacts(id("s1"), A, JOB),
                new SynergyValidator.SynergyFacts(id("s2"), A, JOB), new SynergyValidator.SynergyFacts(id("s3"), id("ghost"), id("nojob")));
        assertEquals(3, SynergyValidator.validate(Set.of(A), Set.of(JOB), synergies).size());
    }

    @Test
    void aJobDefaultNeedsNoClass() {
        assertEquals(List.of(), SynergyValidator.validate(Set.of(A), Set.of(JOB), List.of(new SynergyValidator.SynergyFacts(id("d"), null, JOB))));
    }
}
