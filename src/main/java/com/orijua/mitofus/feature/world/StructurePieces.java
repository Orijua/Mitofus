package com.orijua.mitofus.feature.world;

import java.util.LinkedHashMap;
import java.util.Map;

/** Every structure piece built in code, by template path. Datagen writes them; a new structure adds its pieces() here. */
public final class StructurePieces {
    private StructurePieces() {
    }

    public static Map<String, TemplateBuilder> all() {
        Map<String, TemplateBuilder> pieces = new LinkedHashMap<>(AdventurerCamp.pieces());
        pieces.putAll(Covadonga.pieces());
        pieces.putAll(Altamira.pieces());
        pieces.putAll(TorreDelOro.pieces());
        pieces.putAll(Eibar.pieces());
        return pieces;
    }
}
