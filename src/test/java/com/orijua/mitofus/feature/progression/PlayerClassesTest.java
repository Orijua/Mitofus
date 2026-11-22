package com.orijua.mitofus.feature.progression;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerClassesTest {
    private static final ResourceLocation SWORD = ResourceLocation.parse("mitofus:swordsman");
    private static final ResourceLocation MAGE = ResourceLocation.parse("mitofus:mage");

    @Test
    void codecRoundTrips() {
        PlayerClasses classes = PlayerClasses.DEFAULT
                .withSlot(new ClassSlot(1, MAGE, 120, 33, ClassGrade.ADEPT))
                .withSlot(ClassSlot.fresh(0, SWORD))
                .withJob(ResourceLocation.parse("mitofus:blacksmith"));
        var json = PlayerClasses.CODEC.encodeStart(JsonOps.INSTANCE, classes).getOrThrow();
        assertEquals(classes, PlayerClasses.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(List.of(SWORD, MAGE), classes.classIds(), "slots are kept in order");
    }

    @Test
    void anEmptyObjectReadsAsTheDefault() {
        assertEquals(PlayerClasses.DEFAULT, PlayerClasses.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow());
        assertEquals(Optional.empty(), PlayerClasses.DEFAULT.job());
        assertTrue(PlayerClasses.DEFAULT.slots().isEmpty());
    }

    @Test
    void aSlotWithOnlyItsClassGetsLevelOneAndApprenticeBack() {
        JsonObject slot = new JsonObject();
        slot.addProperty("slot", 2);
        slot.addProperty("class", "mitofus:mage");
        assertEquals(new ClassSlot(2, MAGE, 1, 0, ClassGrade.APPRENTICE), ClassSlot.CODEC.parse(JsonOps.INSTANCE, slot).getOrThrow());
    }

    @Test
    void placingInAnOccupiedSlotReplaces() {
        PlayerClasses classes = PlayerClasses.DEFAULT.withSlot(ClassSlot.fresh(0, SWORD)).withSlot(ClassSlot.fresh(0, MAGE));
        assertEquals(1, classes.slots().size());
        assertTrue(classes.hasClass(MAGE));
        assertFalse(classes.hasClass(SWORD));
        assertEquals(Optional.empty(), classes.slot(1));
    }
}
