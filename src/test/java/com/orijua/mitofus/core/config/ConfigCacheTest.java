package com.orijua.mitofus.core.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The cached balances are built once per load, not once per call or per frame, and are rebuilt after a reload. */
class ConfigCacheTest {
    @Test
    void buildsOnceUntilInvalidated() {
        AtomicInteger builds = new AtomicInteger();
        Config.Cached<Object> cached = new Config.Cached<>(() -> {
            builds.incrementAndGet();
            return new Object();
        });
        Object first = cached.get();
        for (int frame = 0; frame < 1000; frame++) {
            assertSame(first, cached.get());
        }
        assertEquals(1, builds.get());
        cached.invalidate();
        Object second = cached.get();
        assertEquals(2, builds.get());
        assertSame(second, cached.get());
    }
}
