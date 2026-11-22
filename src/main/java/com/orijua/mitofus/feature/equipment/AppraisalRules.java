package com.orijua.mitofus.feature.equipment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * The pure rules of the appraisal: which data a piece has, in which order the Eye reveals them and how many it reveals per
 * use. Everything is a function of the seed stored on the piece, so the same piece always reveals the same way. A piece's
 * data are keys: {@code rating}, {@code option:<i>}, {@code level}, {@code stat:<name>} and {@code value}, in tooltip order.
 */
public final class AppraisalRules {
    public static final String RATING = "rating";
    public static final String LEVEL = "level";
    public static final String VALUE = "value";

    private AppraisalRules() {
    }

    public static String optionKey(int index) {
        return "option:" + index;
    }

    public static String statKey(String stat) {
        return "stat:" + stat;
    }

    /** The data of a piece in tooltip order; {@code value} only if the piece can be priced. */
    public static List<String> keys(int optionCount, List<String> statNames, boolean hasValue) {
        List<String> keys = new ArrayList<>();
        keys.add(RATING);
        for (int i = 0; i < optionCount; i++) {
            keys.add(optionKey(i));
        }
        keys.add(LEVEL);
        for (String stat : statNames) {
            keys.add(statKey(stat));
        }
        if (hasValue) {
            keys.add(VALUE);
        }
        return keys;
    }

    /** The order the data are revealed in: the rating first, the rest shuffled by the seed. */
    public static List<String> revealOrder(List<String> keys, long seed) {
        List<String> rest = new ArrayList<>(keys);
        boolean hasRating = rest.remove(RATING);
        SplittableRandom random = new SplittableRandom(seed);
        for (int i = rest.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            String swap = rest.get(i);
            rest.set(i, rest.get(j));
            rest.set(j, swap);
        }
        List<String> order = new ArrayList<>(keys.size());
        if (hasRating) {
            order.add(RATING);
        }
        order.addAll(rest);
        return order;
    }

    /** A number fixed by the seed and how many data are revealed already: the same use of the same piece gives the same number. */
    public static long mix(long seed, int revealedCount) {
        return new SplittableRandom(seed ^ (0x9E3779B97F4A7C15L * (revealedCount + 1L))).nextLong();
    }

    /** How many data the next use reveals: from {@code min} to {@code max}, both included. */
    public static int amount(long seed, int revealedCount, int min, int max) {
        if (min < 0 || min > max) {
            throw new IllegalArgumentException("amount needs 0 <= min <= max: min=" + min + ", max=" + max);
        }
        return min + (int) Math.floorMod(mix(seed, revealedCount), (long) (max - min + 1));
    }

    /** The next {@code amount} data to reveal, in reveal order, leaving out the ones already revealed. */
    public static List<String> next(List<String> keys, long seed, Collection<String> revealed, int amount) {
        List<String> next = new ArrayList<>();
        for (String key : revealOrder(keys, seed)) {
            if (next.size() >= amount) {
                break;
            }
            if (!revealed.contains(key)) {
                next.add(key);
            }
        }
        return next;
    }

    /** Whether every datum is revealed; keys in {@code revealed} that the piece does not have are ignored. */
    public static boolean complete(List<String> keys, Collection<String> revealed) {
        return revealed.containsAll(keys);
    }

    /** What one use did: the data it revealed, all the revealed ones and whether the piece is now appraised in full. */
    public record Step(List<String> newlyRevealed, List<String> revealed, boolean complete) {
    }

    /** One use of the Eye: reveals between {@code min} and {@code max} data (the number is fixed by the seed). */
    public static Step step(List<String> keys, long seed, List<String> revealed, int min, int max) {
        return stepExactly(keys, seed, revealed, amount(seed, revealed.size(), min, max));
    }

    /** As {@link #step}, with the number of data given (for debugging). */
    public static Step stepExactly(List<String> keys, long seed, List<String> revealed, int n) {
        List<String> added = next(keys, seed, revealed, n);
        Set<String> all = new LinkedHashSet<>(revealed);
        all.addAll(added);
        return new Step(List.copyOf(added), List.copyOf(all), complete(keys, all));
    }
}
