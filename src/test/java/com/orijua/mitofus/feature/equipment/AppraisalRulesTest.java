package com.orijua.mitofus.feature.equipment;

import com.orijua.mitofus.core.config.AppraisalBalance;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppraisalRulesTest {
    /** 14 data: rating, 6 options, level, 5 stats, value. */
    private static final List<String> BIG = AppraisalRules.keys(6, List.of("strength", "agility", "vitality", "intelligence", "sense"), true);

    @Test
    void keysFollowTheTooltipOrder() {
        assertEquals(List.of("rating", "option:0", "option:1", "level", "stat:strength", "value"),
                AppraisalRules.keys(2, List.of("strength"), true));
        assertEquals(List.of("rating", "level", "value"), AppraisalRules.keys(0, List.of(), true));
        assertEquals(List.of("rating", "level"), AppraisalRules.keys(0, List.of(), false));
        assertEquals(14, BIG.size());
    }

    @Test
    void revealOrderStartsWithTheRatingAndIsAPermutation() {
        for (long seed = 0; seed <= 20; seed++) {
            List<String> order = AppraisalRules.revealOrder(BIG, seed);
            assertEquals("rating", order.get(0));
            assertEquals(new HashSet<>(BIG), new HashSet<>(order));
            assertEquals(BIG.size(), order.size());
            assertEquals(order, AppraisalRules.revealOrder(BIG, seed));
        }
    }

    @Test
    void revealOrderChangesWithTheSeed() {
        Set<List<String>> orders = new HashSet<>();
        for (long seed = 0; seed <= 20; seed++) {
            orders.add(AppraisalRules.revealOrder(BIG, seed));
        }
        assertNotEquals(AppraisalRules.revealOrder(BIG, 0L), AppraisalRules.revealOrder(BIG, 1L));
        assertEquals(21, orders.size(), "every one of the 21 seeds gives its own order");
    }

    @Test
    void amountStaysInRangeAndIsDeterministic() {
        for (long seed = -1000; seed <= 1000; seed++) {
            for (int count = 0; count <= 5; count++) {
                int amount = AppraisalRules.amount(seed, count, 6, 10);
                assertTrue(amount >= 6 && amount <= 10, "amount " + amount);
                assertEquals(amount, AppraisalRules.amount(seed, count, 6, 10));
            }
        }
    }

    @Test
    void aSingleValueRangeAlwaysGivesThatValue() {
        for (long seed = -50; seed <= 50; seed++) {
            for (int count = 0; count <= 5; count++) {
                assertEquals(7, AppraisalRules.amount(seed, count, 7, 7));
            }
        }
    }

    @Test
    void amountRejectsAnInvalidRange() {
        assertThrows(IllegalArgumentException.class, () -> AppraisalRules.amount(1L, 0, 10, 6));
        assertThrows(IllegalArgumentException.class, () -> AppraisalRules.amount(1L, 0, -1, 6));
    }

    @Test
    void amountReachesEveryValueOfTheRange() {
        Set<Integer> seen = new HashSet<>();
        for (long seed = 0; seed < 1000; seed++) {
            seen.add(AppraisalRules.amount(seed, 0, 6, 10));
        }
        assertEquals(Set.of(6, 7, 8, 9, 10), seen);
    }

    @Test
    void nextLeavesOutWhatIsRevealedAndStopsWhenThereIsNoMore() {
        List<String> order = AppraisalRules.revealOrder(BIG, 5);
        List<String> first = AppraisalRules.next(BIG, 5, List.of(), 4);
        assertEquals(order.subList(0, 4), first);
        List<String> second = AppraisalRules.next(BIG, 5, first, 4);
        assertEquals(order.subList(4, 8), second);
        List<String> rest = AppraisalRules.next(BIG, 5, order.subList(0, BIG.size() - 2), 10);
        assertEquals(order.subList(BIG.size() - 2, BIG.size()), rest);
        assertTrue(AppraisalRules.next(BIG, 5, order, 10).isEmpty());
    }

    @Test
    void aNormalPieceWithoutOptionsIsDoneInOneUse() {
        List<String> keys = AppraisalRules.keys(0, List.of(), true);
        assertEquals(3, keys.size());
        AppraisalRules.Step step = AppraisalRules.step(keys, 42L, List.of(), 6, 10);
        assertTrue(step.complete());
        assertEquals(3, step.newlyRevealed().size());
    }

    @Test
    void aBigPieceNeedsThreeUsesWithSixAndSix() {
        long seed = 99L;
        AppraisalRules.Step one = AppraisalRules.step(BIG, seed, List.of(), 6, 6);
        assertEquals(6, one.newlyRevealed().size());
        assertFalse(one.complete());
        assertEquals("rating", one.newlyRevealed().get(0));
        AppraisalRules.Step two = AppraisalRules.step(BIG, seed, one.revealed(), 6, 6);
        assertEquals(6, two.newlyRevealed().size());
        assertEquals(12, two.revealed().size());
        assertFalse(two.complete());
        AppraisalRules.Step three = AppraisalRules.step(BIG, seed, two.revealed(), 6, 6);
        assertEquals(2, three.newlyRevealed().size());
        assertTrue(three.complete());
        assertEquals(new HashSet<>(BIG), new HashSet<>(three.revealed()));
    }

    @Test
    void stepsNeverRepeatADatum() {
        for (long seed = 0; seed < 50; seed++) {
            List<String> revealed = new ArrayList<>();
            boolean complete = false;
            int uses = 0;
            while (!complete) {
                AppraisalRules.Step step = AppraisalRules.step(BIG, seed, revealed, 6, 10);
                for (String key : step.newlyRevealed()) {
                    assertFalse(revealed.contains(key), key + " repeated");
                }
                revealed = new ArrayList<>(step.revealed());
                complete = step.complete();
                assertTrue(++uses <= 3, "more than 3 uses");
            }
            assertEquals(BIG.size(), revealed.size());
        }
    }

    @Test
    void stepExactlyRevealsTheGivenNumber() {
        AppraisalRules.Step step = AppraisalRules.stepExactly(BIG, 1L, List.of(), 3);
        assertEquals(3, step.newlyRevealed().size());
        assertFalse(step.complete());
    }

    @Test
    void completeIgnoresUnknownKeys() {
        List<String> keys = List.of("rating", "level");
        assertTrue(AppraisalRules.complete(keys, List.of("rating", "level", "stat:gone")));
        assertFalse(AppraisalRules.complete(keys, List.of("rating", "stat:gone")));
    }

    @Test
    void theBalanceDefaultsAndLimits() {
        assertEquals(new AppraisalBalance(6, 10, 20), AppraisalBalance.DEFAULT);
        assertThrows(IllegalArgumentException.class, () -> new AppraisalBalance(0, 10, 20));
        assertThrows(IllegalArgumentException.class, () -> new AppraisalBalance(6, 5, 20));
        assertThrows(IllegalArgumentException.class, () -> new AppraisalBalance(6, 65, 20));
        assertThrows(IllegalArgumentException.class, () -> new AppraisalBalance(6, 10, -1));
        assertThrows(IllegalArgumentException.class, () -> new AppraisalBalance(6, 10, 1201));
        assertDoesNotThrow(() -> new AppraisalBalance(1, 1, 0));
        assertDoesNotThrow(() -> new AppraisalBalance(64, 64, 1200));
    }

    @Test
    void stepOverAnAlreadyCompleteRevealedSetAddsNothing() {
        AppraisalRules.Step step = AppraisalRules.step(BIG, 3L, BIG, 6, 10);
        assertTrue(step.newlyRevealed().isEmpty());
        assertTrue(step.complete());
        assertEquals(new HashSet<>(BIG), new HashSet<>(step.revealed()));
    }

    @Test
    void unknownRevealedKeysDoNotCountNorBreakAStep() {
        List<String> revealed = List.of("stat:gone", "option:99");
        List<String> expected = AppraisalRules.next(BIG, 8L, List.of(), 4);
        assertEquals(expected, AppraisalRules.next(BIG, 8L, revealed, 4));
        AppraisalRules.Step step = AppraisalRules.stepExactly(BIG, 8L, revealed, 4);
        assertEquals(expected, step.newlyRevealed());
        assertFalse(step.complete());
    }

    @Test
    void revealOrderWithoutARatingIsAPermutationAndAddsNone() {
        List<String> keys = List.of("level", "stat:strength", "value", "option:0");
        for (long seed = 0; seed <= 20; seed++) {
            List<String> order = AppraisalRules.revealOrder(keys, seed);
            assertEquals(new HashSet<>(keys), new HashSet<>(order));
            assertEquals(keys.size(), order.size());
            assertFalse(order.contains("rating"));
        }
    }

    @Test
    void anEmptyKeyListHasNoOrderAndNothingToReveal() {
        assertTrue(AppraisalRules.revealOrder(List.of(), 4L).isEmpty());
        assertTrue(AppraisalRules.next(List.of(), 4L, List.of(), 5).isEmpty());
    }

    @Test
    void nextWithANonPositiveAmountIsEmpty() {
        assertTrue(AppraisalRules.next(BIG, 5L, List.of(), 0).isEmpty());
        assertTrue(AppraisalRules.next(BIG, 5L, List.of(), -1).isEmpty());
    }
}
