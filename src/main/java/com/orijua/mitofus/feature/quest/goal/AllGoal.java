package com.orijua.mitofus.feature.quest.goal;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orijua.mitofus.feature.progression.PlayerStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * A double quest, two counted goals that must both be reached: {"type": "all", "goals": [{...}, {...}]}. Each part
 * keeps its own progress (capped at its own target), and the quest's target is the sum of both targets. Parts must be
 * counted by events (no deliveries, no "reach level N"), because a quest stores a single number: the starting point of
 * each part, 15 bits apiece.
 */
public record AllGoal(List<Goal> goals) implements Goal {
    private static final int BITS = 15;
    private static final int MASK = (1 << BITS) - 1;

    public static final MapCodec<AllGoal> CODEC = RecordCodecBuilder.<AllGoal>mapCodec(instance -> instance.group(
            Goal.CODEC.listOf().fieldOf("goals").forGetter(AllGoal::goals)
    ).apply(instance, AllGoal::new)).validate(AllGoal::validate);

    static DataResult<AllGoal> validate(AllGoal goal) {
        if (goal.goals.size() != 2) {
            return DataResult.error(() -> "an \"all\" goal needs exactly 2 goals");
        }
        for (Goal part : goal.goals) {
            if (!part.cumulative() || part instanceof DeliverGoal || part instanceof AllGoal) {
                return DataResult.error(() -> "the goals of an \"all\" goal must be counted by events (no deliver, level or nested all)");
            }
            if (part.target() > MASK) {
                return DataResult.error(() -> "a part of an \"all\" goal can ask for " + MASK + " at most");
            }
        }
        return DataResult.success(goal);
    }

    @Override
    public GoalType type() {
        return GoalType.ALL;
    }

    @Override
    public int target() {
        return goals.stream().mapToInt(Goal::target).sum();
    }

    /** Lifetime progress, each part capped at its own target. */
    @Override
    public int progress(PlayerCounters counters, PlayerStats stats) {
        int total = 0;
        for (Goal part : goals) {
            total += Math.min(part.target(), part.progress(counters, stats));
        }
        return total;
    }

    @Override
    public int startingValue(PlayerCounters counters, PlayerStats stats) {
        int packed = 0;
        for (int i = 0; i < goals.size(); i++) {
            packed |= (goals.get(i).progress(counters, stats) & MASK) << (BITS * i);
        }
        return packed;
    }

    @Override
    public int questProgress(int stored, PlayerCounters counters, PlayerStats stats) {
        int total = 0;
        for (int i = 0; i < goals.size(); i++) {
            Goal part = goals.get(i);
            int start = (stored >> (BITS * i)) & MASK;
            total += Math.min(part.target(), (part.progress(counters, stats) - start) & MASK);
        }
        return total;
    }

    @Override
    public Component describe() {
        MutableComponent text = Component.empty();
        for (int i = 0; i < goals.size(); i++) {
            if (i > 0) {
                text.append(Component.translatable("goal.mitofus.all.and"));
            }
            text.append(goals.get(i).describe());
        }
        return text;
    }
}
