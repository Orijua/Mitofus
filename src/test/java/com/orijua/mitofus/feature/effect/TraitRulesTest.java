package com.orijua.mitofus.feature.effect;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TraitRulesTest {
    private static final TagKey<Item> SWORDS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("minecraft", "swords"));

    private static Bonus flat(BonusType kind, double amount) {
        return new Bonus(kind, amount, 0, 0, Optional.empty(), false);
    }

    @Test
    void aFlatBonusIgnoresTheLevel() {
        assertEquals(20.0, flat(BonusType.MAX_MANA, 20).valueAt(1));
        assertEquals(20.0, flat(BonusType.MAX_MANA, 20).valueAt(500));
    }

    @Test
    void aGrowingBonusAddsOneStepPerCompletedBlockOfLevels() {
        Bonus armor = new Bonus(BonusType.ARMOR, 2, 10, 0, Optional.empty(), false);
        assertEquals(0.0, armor.valueAt(9));
        assertEquals(2.0, armor.valueAt(10));
        assertEquals(2.0, armor.valueAt(19));
        assertEquals(6.0, armor.valueAt(30));
    }

    @Test
    void theCapStopsAGrowingBonus() {
        Bonus armor = new Bonus(BonusType.ARMOR, 2, 10, 10, Optional.empty(), false);
        assertEquals(10.0, armor.valueAt(50));
        assertEquals(10.0, armor.valueAt(500));
    }

    @Test
    void sumOnlyCountsTheRequestedKindAndSkipsConditionalOnes() {
        List<Bonus> bonuses = List.of(
                flat(BonusType.MAX_MANA, 20),
                flat(BonusType.MAX_MANA, 5),
                flat(BonusType.MANA_REGEN, 0.25),
                new Bonus(BonusType.DAMAGE, 0.1, 0, 0, Optional.of(SWORDS), false));
        assertEquals(25.0, TraitRules.sum(bonuses, BonusType.MAX_MANA, 1));
        assertEquals(0.25, TraitRules.sum(bonuses, BonusType.MANA_REGEN, 1));
        assertEquals(0.0, TraitRules.sum(bonuses, BonusType.DAMAGE, 1));
    }

    @Test
    void damageNeedsTheTaggedItemAndTheNightWhenAsked() {
        List<Bonus> bonuses = List.of(
                new Bonus(BonusType.DAMAGE, 0.1, 0, 0, Optional.of(SWORDS), false),
                new Bonus(BonusType.DAMAGE, 0.1, 0, 0, Optional.empty(), true));
        assertEquals(0.0, TraitRules.damage(bonuses, 1, bonus -> false, false), 1e-9);
        assertEquals(0.1, TraitRules.damage(bonuses, 1, bonus -> true, false), 1e-9);
        assertEquals(0.1, TraitRules.damage(bonuses, 1, bonus -> false, true), 1e-9);
        assertEquals(0.2, TraitRules.damage(bonuses, 1, bonus -> true, true), 1e-9);
    }
}
