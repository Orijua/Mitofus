package com.orijua.mitofus.feature.vrhelmet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientVrHelmetFadeTest {
    @Test
    void theDarkFadesOutAfterTheScreenEndsInsteadOfVanishing() {
        ClientVrHelmet.connecting(40, 10_000);
        assertEquals(ClientVrHelmet.MAX_DARK, ClientVrHelmet.dark(ClientVrHelmet.fraction(11_900)), 0.001f);
        ClientVrHelmet.connecting(0, 12_000);
        assertEquals(-1.0f, ClientVrHelmet.fraction(12_000), 0.0f);
        float start = ClientVrHelmet.fadeOutDark(12_000);
        float middle = ClientVrHelmet.fadeOutDark(12_000 + ClientVrHelmet.FADE_OUT_MILLIS / 2);
        assertTrue(start > middle && middle > 0.0f, "it gets lighter step by step");
        assertEquals(0.0f, ClientVrHelmet.fadeOutDark(12_000 + ClientVrHelmet.FADE_OUT_MILLIS), 0.0f);
    }

    @Test
    void cancellingWhenNothingWasConnectingFadesNothing() {
        ClientVrHelmet.connecting(0, 50_000);
        ClientVrHelmet.connecting(0, 90_000);
        assertEquals(0.0f, ClientVrHelmet.fadeOutDark(90_000), 0.0f);
    }
}
