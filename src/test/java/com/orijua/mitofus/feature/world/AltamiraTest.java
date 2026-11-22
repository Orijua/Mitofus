package com.orijua.mitofus.feature.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AltamiraTest {
    private static final Map<String, TemplateBuilder> PIECES = Altamira.pieces();

    @Test
    void eachPieceLeadsToTheNextAndTheSanctumEndsIt() {
        assertEquals(List.of(Altamira.ENTRANCE, Altamira.DESCENT, Altamira.HALL, Altamira.SANCTUM), List.copyOf(PIECES.keySet()));
        assertEquals(List.of("mitofus:altamira/descent"), pools(Altamira.ENTRANCE));
        assertEquals(List.of("mitofus:altamira/hall", "minecraft:empty"), pools(Altamira.DESCENT));
        assertEquals(List.of("mitofus:altamira/sanctum", "minecraft:empty"), pools(Altamira.HALL));
        assertEquals(List.of("minecraft:empty"), pools(Altamira.SANCTUM));
        assertTrue(StructurePieces.all().keySet().containsAll(PIECES.keySet()), "datagen writes them");
    }

    @Test
    void theDescentGoesDownOneStepPerBlock() {
        assertEquals(1, Altamira.stepFloor(0), "flat at the bottom");
        assertEquals(1, Altamira.stepFloor(4));
        assertEquals(2, Altamira.stepFloor(5));
        assertEquals(Altamira.DROP + 1, Altamira.stepFloor(4 + Altamira.DROP), "and flat at the top");
        assertEquals(Altamira.DROP + 1, Altamira.stepFloor(99));
        TemplateBuilder descent = Altamira.descent();
        for (int z = 1; z < 4 + Altamira.DROP + 2; z++) {
            int floor = Altamira.stepFloor(z);
            for (int y = floor + 1; y <= floor + 3; y++) {
                String block = descent.blockAt(2, y, z);
                assertTrue(block.equals("minecraft:air") || block.startsWith("minecraft:soul_lantern"), "headroom at z " + z + ", y " + y + ": " + block);
            }
        }
        int pit = 4 + Altamira.DROP / 2;
        assertEquals("minecraft:powder_snow", descent.blockAt(2, Altamira.stepFloor(pit), pit), "the trap");
        assertTrue(descent.blockAt(2, Altamira.stepFloor(pit) - 1, pit).startsWith("minecraft:"), "only one block deep");
        assertEquals("minecraft:spawner", descent.blockAt(0, 2, 2));
    }

    @Test
    void theHallIsFullOfLockedArmoredStands() {
        TemplateBuilder hall = Altamira.hall();
        List<String> ids = hall.entityIds();
        assertEquals(10, ids.size(), "one on each of the four piles and six along the aisle");
        assertTrue(ids.stream().allMatch("minecraft:armor_stand"::equals));
        ListTag entities = hall.build(1).getList("entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag stand = entities.getCompound(i).getCompound("nbt");
            assertTrue(stand.getBoolean("Invulnerable"), "unbreakable");
            assertEquals(Altamira.ALL_SLOTS_DISABLED, stand.getInt("DisabledSlots"), "nothing can be taken or swapped");
            assertEquals(Altamira.RELIC_TAG, stand.getList("Tags", Tag.TAG_STRING).getString(0));
            assertEquals(4, stand.getList("ArmorItems", Tag.TAG_COMPOUND).size());
            assertTrue(stand.getList("HandItems", Tag.TAG_COMPOUND).getCompound(0).getString("id").endsWith("_sword"));
        }
        assertEquals("minecraft:air", hall.blockAt(11, 3, 11), "the aisle is clear");
    }

    @Test
    void theBookWaitsOnTheGoldenTable() {
        assertEquals("mitofus:golden_table[book=true]", Altamira.sanctum().blockAt(6, 2, 6));
    }

    private static List<String> pools(String piece) {
        return Jigsaws.of(PIECES.get(piece)).stream().map(jigsaw -> jigsaw.getString("pool")).toList();
    }
}
