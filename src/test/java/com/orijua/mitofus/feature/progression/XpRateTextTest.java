package com.orijua.mitofus.feature.progression;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XpRateTextTest {
    @Test
    void noEtaWithoutAMeasuredPace() {
        XpRate rate = XpRateRules.record(XpRate.EMPTY, 10);
        assertEquals(OptionalLong.empty(), XpRateText.etaMinutes(rate, 30));
    }

    @Test
    void etaFromTheMeasuredPace() {
        // 10 XP in one minute = 600 XP/hour; 30 missing = 3 minutes.
        XpRate rate = new XpRate(java.util.List.of(), 10, 60 - 1);
        rate = XpRateRules.tick(rate);
        assertEquals(OptionalLong.of(3), XpRateText.etaMinutes(rate, 30));
        assertEquals(OptionalLong.empty(), XpRateText.etaMinutes(rate, 0));
    }

    @Test
    void durationUsesMinutesOrHours() {
        TranslatableContents minutes = (TranslatableContents) XpRateText.duration(5).getContents();
        assertEquals("screen.mitofus.duration.minutes", minutes.getKey());
        assertEquals(5L, minutes.getArgs()[0]);
        TranslatableContents hours = (TranslatableContents) XpRateText.duration(125).getContents();
        assertEquals("screen.mitofus.duration.hours", hours.getKey());
        assertEquals(2L, hours.getArgs()[0]);
        assertEquals(5L, hours.getArgs()[1]);
    }
}
