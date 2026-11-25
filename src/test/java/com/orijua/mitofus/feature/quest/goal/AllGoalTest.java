package com.orijua.mitofus.feature.quest.goal;

import com.orijua.mitofus.feature.progression.PlayerStats;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllGoalTest {
    private static final ResourceLocation A = ResourceLocation.fromNamespaceAndPath("mitofus", "a");
    private static final ResourceLocation B = ResourceLocation.fromNamespaceAndPath("mitofus", "b");
    private static final AllGoal GOAL = new AllGoal(List.of(new MilestoneGoal(A, 5), new MilestoneGoal(B, 3)));

    private static PlayerCounters with(PlayerCounters base, ResourceLocation key, int times) {
        for (int i = 0; i < times; i++) {
            base = base.increment(MilestoneGoal.counterKey(key));
        }
        return base;
    }

    @Test
    void targetIsTheSumAndEachPartIsCappedOnItsOwn() {
        assertEquals(8, GOAL.target());
        PlayerCounters counters = with(PlayerCounters.EMPTY, A, 20);
        assertEquals(5, GOAL.questProgress(GOAL.startingValue(PlayerCounters.EMPTY, PlayerStats.DEFAULT), counters, PlayerStats.DEFAULT),
                "plenty of one part doesn't cover the other");
        counters = with(counters, B, 3);
        assertEquals(8, GOAL.questProgress(0, counters, PlayerStats.DEFAULT));
    }

    @Test
    void onlyWhatHappensAfterAcceptingCounts() {
        PlayerCounters before = with(with(PlayerCounters.EMPTY, A, 7), B, 40000);
        int stored = GOAL.startingValue(before, PlayerStats.DEFAULT);
        assertEquals(0, GOAL.questProgress(stored, before, PlayerStats.DEFAULT));
        PlayerCounters after = with(with(before, A, 2), B, 1);
        assertEquals(3, GOAL.questProgress(stored, after, PlayerStats.DEFAULT));
    }

    @Test
    void itIsCountedByEvents() {
        assertTrue(GOAL.cumulative());
        assertEquals(GoalType.ALL, GOAL.type());
    }

    @Test
    void onlyTwoCountedGoalsAreValid() {
        assertTrue(AllGoal.validate(GOAL).result().isPresent());
        assertTrue(AllGoal.validate(new AllGoal(List.of(new MilestoneGoal(A, 1)))).result().isEmpty(), "one part is not a double quest");
        assertTrue(AllGoal.validate(new AllGoal(List.of(GOAL, new MilestoneGoal(A, 1)))).result().isEmpty(), "no nesting");
    }
}
