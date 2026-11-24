package com.orijua.mitofus.core.client;

import org.junit.jupiter.api.Test;

import static com.orijua.mitofus.core.client.MitofusCard.State.DIM;
import static com.orijua.mitofus.core.client.MitofusCard.State.HOVER;
import static com.orijua.mitofus.core.client.MitofusCard.State.NORMAL;
import static com.orijua.mitofus.core.client.MitofusCard.State.SELECTED;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MitofusCardTest {
    @Test
    void plainCardIsNormal() {
        assertEquals(NORMAL, MitofusCard.state(false, false, false));
    }

    @Test
    void dimCardIsDim() {
        assertEquals(DIM, MitofusCard.state(false, true, false));
    }

    @Test
    void hoverBeatsDim() {
        assertEquals(HOVER, MitofusCard.state(true, true, false));
        assertEquals(HOVER, MitofusCard.state(true, false, false));
    }

    @Test
    void selectedBeatsEverything() {
        assertEquals(SELECTED, MitofusCard.state(false, false, true));
        assertEquals(SELECTED, MitofusCard.state(true, false, true));
        assertEquals(SELECTED, MitofusCard.state(false, true, true));
        assertEquals(SELECTED, MitofusCard.state(true, true, true));
    }

    @Test
    void fillPerState() {
        assertEquals(0x30FFFFFF, MitofusCard.fill(NORMAL));
        assertEquals(0x50FFFFFF, MitofusCard.fill(HOVER));
        assertEquals(0x18FFFFFF, MitofusCard.fill(DIM));
        assertEquals(0x50F4E2AA, MitofusCard.fill(SELECTED));
    }
}
