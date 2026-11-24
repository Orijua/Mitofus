package com.orijua.mitofus.feature.progression;

import com.orijua.mitofus.Mitofus;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefenseRulesTest {
    @Test
    void theWornPiecesAreRecognisedByTheirVanillaModifier() {
        assertEquals(DefenseRules.Source.PIECES, DefenseRules.sourceOf(ResourceLocation.withDefaultNamespace("armor.chestplate")));
    }

    @Test
    void enhancementAndOptionsOfAPieceAreTheirOwnSources() {
        assertEquals(DefenseRules.Source.ENHANCEMENT, DefenseRules.sourceOf(Mitofus.id("equip_head_enhancement")));
        assertEquals(DefenseRules.Source.OPTIONS, DefenseRules.sourceOf(Mitofus.id("equip_chest_option_2")));
    }

    @Test
    void anythingElseIsOther() {
        assertEquals(DefenseRules.Source.OTHER, DefenseRules.sourceOf(Mitofus.id("stat_vitality")));
    }

    @Test
    void thePartsAlwaysAddUpToTheTotal() {
        DefenseRules.Breakdown breakdown = DefenseRules.of(14.0, 0.0, 8.0, 3.0, 1.0);
        assertEquals(14.0, breakdown.total());
        assertEquals(2.0, breakdown.other());
        assertEquals(breakdown.total(), breakdown.pieces() + breakdown.enhancement() + breakdown.options() + breakdown.other(), 1.0E-9);
    }

    @Test
    void aMultiplierShowsUpInOther() {
        assertEquals(1.5, DefenseRules.of(11.5, 0.0, 8.0, 2.0, 0.0).other(), 1.0E-9);
    }
}
