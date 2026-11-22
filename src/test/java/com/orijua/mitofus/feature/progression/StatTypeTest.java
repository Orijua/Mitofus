package com.orijua.mitofus.feature.progression;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatTypeTest {
    @Test
    void byNameFindsEveryStatBySerializedName() {
        for (StatType stat : StatType.values()) {
            assertEquals(Optional.of(stat), StatType.byName(stat.getSerializedName()), stat.name());
        }
    }

    @Test
    void byNameIsCaseSensitiveAndExact() {
        for (String name : new String[]{"Strength", "vit", "", "str"}) {
            assertEquals(Optional.empty(), StatType.byName(name), "'" + name + "'");
        }
    }
}
