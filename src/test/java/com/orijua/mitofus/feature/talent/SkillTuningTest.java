package com.orijua.mitofus.feature.talent;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillTuningTest {
    private static final ResourceLocation STRIKE = ResourceLocation.fromNamespaceAndPath("test", "strike");
    private static final ResourceLocation CRY = ResourceLocation.fromNamespaceAndPath("test", "cry");

    @Test
    void noBoostsChangeNothing() {
        assertEquals(TalentEffects.SkillTuning.NONE, TalentEffects.SkillTuning.of(List.of(), STRIKE));
    }

    @Test
    void boostsOfTheSameSkillMultiplyAndOthersAreIgnored() {
        TalentEffects.SkillTuning tuning = TalentEffects.SkillTuning.of(List.of(
                new SkillBoostTalentEffect(STRIKE, 1.1, 1.0, 0.9),
                new SkillBoostTalentEffect(STRIKE, 1.1, 0.5, 1.0),
                new SkillBoostTalentEffect(CRY, 5.0, 5.0, 5.0)), STRIKE);
        assertEquals(1.21, tuning.damage(), 1e-9);
        assertEquals(0.5, tuning.manaCost(), 1e-9);
        assertEquals(0.9, tuning.cooldown(), 1e-9);
    }

    @Test
    void costAndCooldownAreScaledAndRounded() {
        TalentEffects.SkillTuning tuning = new TalentEffects.SkillTuning(1.0, 0.85, 0.9);
        assertEquals(6.8f, tuning.mana(8f), 1e-4);
        assertEquals(54, tuning.cooldownTicks(60));
    }
}
