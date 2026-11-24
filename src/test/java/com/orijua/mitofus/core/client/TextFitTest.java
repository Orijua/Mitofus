package com.orijua.mitofus.core.client;

import org.junit.jupiter.api.Test;

import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextFitTest {
    /** Every character is 6 wide, like a monospaced font. */
    private static final ToIntFunction<String> WIDTH = text -> text.length() * 6;

    @Test
    void textThatFitsIsKept() {
        assertEquals("Hello", TextFit.fit("Hello", 30, WIDTH));
    }

    @Test
    void longTextIsCutWithAnEllipsisThatFits() {
        String fitted = TextFit.fit("A very long title", 60, WIDTH);
        assertTrue(fitted.endsWith(TextFit.ELLIPSIS));
        assertTrue(WIDTH.applyAsInt(fitted) <= 60);
        assertEquals("A very lo…", fitted);
    }

    @Test
    void nothingFitsWhenEvenTheEllipsisDoesNot() {
        assertEquals("", TextFit.fit("Hello", 5, WIDTH));
        assertEquals("", TextFit.fit("Hello", 0, WIDTH));
    }

    @Test
    void neverSplitsASurrogatePair() {
        String fitted = TextFit.fit("ab😀cd", 24, WIDTH); // room for 3 chars + ellipsis
        assertEquals("ab…", fitted);
    }

    @Test
    void emptyTextStaysEmpty() {
        assertEquals("", TextFit.fit("", 10, WIDTH));
    }
}
