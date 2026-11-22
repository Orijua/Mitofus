package com.orijua.mitofus.feature.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovadongaTest {
    private static final Map<String, TemplateBuilder> PIECES = Covadonga.pieces();

    @Test
    void theSquarePointsEachSideAtItsPool() {
        List<CompoundTag> exits = Jigsaws.of(Covadonga.plaza());
        assertEquals(6, exits.size());
        Set<String> pools = exits.stream().map(jigsaw -> jigsaw.getString("pool")).collect(Collectors.toSet());
        assertEquals(Set.of("mitofus:covadonga/smithy", "mitofus:covadonga/houses", "mitofus:covadonga/chief_house",
                "mitofus:covadonga/mine"), pools);
        assertEquals(1, exits.stream().filter(jigsaw -> jigsaw.getString("pool").endsWith("/smithy")).count(), "one smithy");
        assertEquals(3, exits.stream().filter(jigsaw -> jigsaw.getString("pool").endsWith("/houses")).count(), "three houses");
    }

    @Test
    void everyBuildingJoinsTheSquareOnce() {
        String wanted = Jigsaws.of(Covadonga.plaza()).getFirst().getString("target");
        PIECES.forEach((path, piece) -> {
            if (path.equals(Covadonga.PLAZA)) {
                return;
            }
            List<CompoundTag> ends = Jigsaws.of(piece);
            assertEquals(1, ends.size(), path + ": one jigsaw, back to the square");
            assertEquals(wanted, ends.getFirst().getString("name"), path);
            assertEquals("minecraft:empty", ends.getFirst().getString("pool"), path + " doesn't grow the village further");
        });
    }

    @Test
    void smithWorksInTheSmithy() {
        TemplateBuilder smithy = Covadonga.smithy();
        assertEquals(List.of("mitofus:blacksmith"), smithy.entityIds());
        CompoundTag smith = smithy.build(1).getList("entities", Tag.TAG_COMPOUND).getCompound(0).getCompound("nbt");
        assertTrue(smith.getString("CustomName").contains("\"" + Covadonga.SMITH_NAME + "\""));
        assertEquals("mitofus:smithing_anvil", smithy.blockAt(3, 1, 3));
    }

    @Test
    void theChiefIsInTheChiefsHouse() {
        assertEquals(List.of("mitofus:village_chief"), Covadonga.chiefHouse().entityIds());
    }

    @Test
    void theMineHasIronAndWolframToDig() {
        TemplateBuilder mine = Covadonga.mine();
        List<String> palette = mine.build(1).getList("palette", Tag.TAG_COMPOUND).stream()
                .map(state -> ((CompoundTag) state).getString("Name")).toList();
        assertTrue(palette.contains("minecraft:iron_ore"), "iron for Smith's second quest");
        assertTrue(palette.contains(Covadonga.WOLFRAM_ORE), "wolfram for his third");
        assertEquals("minecraft:iron_ore", mine.blockAt(5, 1, 0), "on the back wall of the room");
        assertEquals(Covadonga.WOLFRAM_ORE, mine.blockAt(2, 1, 0), "next to it");
        assertEquals("minecraft:air", mine.blockAt(5, 2, 5), "and the tunnel to it is open");
    }

    @Test
    void piecesAreListedByTemplatePath() {
        assertEquals(Set.of(Covadonga.PLAZA, Covadonga.SMITHY, Covadonga.CHIEF_HOUSE, Covadonga.HOUSE, Covadonga.GARDEN_HOUSE, Covadonga.MINE),
                Set.copyOf(PIECES.keySet()));
        assertTrue(StructurePieces.all().keySet().containsAll(PIECES.keySet()), "datagen writes them");
    }
}
