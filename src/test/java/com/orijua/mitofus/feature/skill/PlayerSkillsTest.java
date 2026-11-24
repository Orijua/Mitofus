package com.orijua.mitofus.feature.skill;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerSkillsTest {
    private static final ResourceLocation A = ResourceLocation.fromNamespaceAndPath("a", "b");
    private static final ResourceLocation B = ResourceLocation.fromNamespaceAndPath("a", "c");

    private static PlayerSkills decode(String json) {
        return PlayerSkills.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    void aShortSlotListIsPaddedWithEmptySlots() {
        PlayerSkills skills = decode("{\"learned\": [], \"slots\": [\"a:b\"], \"cooldown_until\": {}}");
        assertEquals(SlotRules.MAX_SLOTS, skills.slots().size());
        assertEquals(Optional.of(A), skills.slot(0));
        assertEquals(Optional.empty(), skills.slot(1));
        assertEquals(Optional.empty(), skills.slot(2));
        assertDoesNotThrow(() -> skills.withSlot(2, Optional.of(B)));
    }

    @Test
    void aLongSlotListIsCutAtTheLastPage() {
        String many = String.join(",", java.util.Collections.nCopies(60, "\"a:c\""));
        PlayerSkills skills = decode("{\"learned\": [], \"slots\": [" + many + "], \"cooldown_until\": {}}");
        assertEquals(SlotRules.MAX_SLOTS, skills.slots().size());
        assertEquals(Optional.of(B), skills.slot(SlotRules.MAX_SLOTS - 1));
    }

    @Test
    void aNewSkillOnlyFillsAnOpenSlot() {
        PlayerSkills full = PlayerSkills.EMPTY.learn(A, 0).learn(B, 0).learn(ResourceLocation.fromNamespaceAndPath("a", "d"), 0);
        PlayerSkills fourth = full.learn(ResourceLocation.fromNamespaceAndPath("a", "e"), 0);
        assertEquals(Optional.empty(), fourth.slot(3), "level 0 has only 3 open slots: the fourth skill stays unassigned");
        assertEquals(Optional.of(ResourceLocation.fromNamespaceAndPath("a", "e")),
                full.learn(ResourceLocation.fromNamespaceAndPath("a", "e"), 10).slot(3), "level 10 opens a fourth slot");
    }

    @Test
    void emptyAndLearnedSkillsSurviveARoundTrip() {
        assertEquals(PlayerSkills.EMPTY, roundTrip(PlayerSkills.EMPTY));
        PlayerSkills learned = PlayerSkills.EMPTY.learn(A).learn(B).withCooldown(A, 500L);
        assertEquals(learned, roundTrip(learned));
    }

    @Test
    void cooldownRunsOutAtItsGameTime() {
        PlayerSkills skills = PlayerSkills.EMPTY.withCooldown(A, 500L);
        assertEquals(100, skills.cooldownRemaining(A, 400));
        assertEquals(0, skills.cooldownRemaining(A, 500));
        assertEquals(0, skills.cooldownRemaining(A, 900));
        assertEquals(0, skills.cooldownRemaining(B, 0));
    }

    private static PlayerSkills roundTrip(PlayerSkills skills) {
        return PlayerSkills.CODEC.parse(JsonOps.INSTANCE, PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, skills).getOrThrow()).getOrThrow();
    }
}
