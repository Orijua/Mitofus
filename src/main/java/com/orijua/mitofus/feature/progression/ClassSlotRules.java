package com.orijua.mitofus.feature.progression;

import com.orijua.mitofus.core.config.Balance;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Pure rules of the class slots: which are open at a player level, what may be placed in them and when a class goes up
 * a grade. No Minecraft state here. The numbers are kind on purpose (the mod is meant to be generous, not prohibitive).
 */
public final class ClassSlotRules {
    /** The player level at which each slot (index 0, 1, 2) opens. */
    public static final List<Integer> SLOT_UNLOCK_LEVELS = List.of(1, 20, 50);

    public static final int MAX_SLOTS = SLOT_UNLOCK_LEVELS.size();

    private ClassSlotRules() {
    }

    /** How many slots are open at {@code playerLevel}: they open in order, so slots {@code 0 .. result - 1}. */
    public static int unlockedSlots(int playerLevel) {
        int open = 0;
        for (int level : SLOT_UNLOCK_LEVELS) {
            if (playerLevel >= level) {
                open++;
            }
        }
        return open;
    }

    /** Why a class can or cannot be placed in a slot. */
    public enum Choice { OK, BAD_SLOT, LOCKED, OCCUPIED, DUPLICATE }

    public static Choice check(PlayerClasses classes, int slot, ResourceLocation classId, int playerLevel) {
        if (slot < 0 || slot >= MAX_SLOTS) {
            return Choice.BAD_SLOT;
        }
        if (slot >= unlockedSlots(playerLevel)) {
            return Choice.LOCKED;
        }
        if (classes.slot(slot).isPresent()) {
            return Choice.OCCUPIED;
        }
        if (classes.hasClass(classId)) {
            return Choice.DUPLICATE;
        }
        return Choice.OK;
    }

    /** The slot is open at {@code playerLevel} and free, and the class is not already in another slot. What was chosen is never changed. */
    public static boolean canChoose(PlayerClasses classes, int slot, ResourceLocation classId, int playerLevel) {
        return check(classes, slot, classId, playerLevel) == Choice.OK;
    }

    /** Why a branch can or cannot replace the one in a slot. */
    public enum Swap { OK, BAD_SLOT, LOCKED, EMPTY, SAME, DUPLICATE }

    /** The branch in {@code slot} may be replaced by {@code branch}: the slot is open and occupied, and {@code branch} sits in no slot. */
    public static Swap checkSwap(PlayerClasses classes, int slot, ResourceLocation branch, int playerLevel) {
        if (slot < 0 || slot >= MAX_SLOTS) {
            return Swap.BAD_SLOT;
        }
        if (slot >= unlockedSlots(playerLevel)) {
            return Swap.LOCKED;
        }
        java.util.Optional<ClassSlot> current = classes.slot(slot);
        if (current.isEmpty()) {
            return Swap.EMPTY;
        }
        if (current.get().classId().equals(branch)) {
            return Swap.SAME;
        }
        return classes.hasClass(branch) ? Swap.DUPLICATE : Swap.OK;
    }

    /** What changing the branch of {@code slot} costs in gold coins: nothing the first time for each slot, {@code cost} after that. */
    public static int swapCost(PlayerClasses classes, int slot, int cost) {
        return classes.freeChangeUsed(slot) ? cost : 0;
    }

    /**
     * Puts {@code branch} in {@code slot} and the branch that was there to sleep, keeping all its progress. A branch that was dormant comes
     * back exactly as it was; a new one starts at level 1. The slot's free change is marked used. Check with {@link #checkSwap} first.
     */
    public static PlayerClasses swap(PlayerClasses classes, int slot, ResourceLocation branch) {
        ClassSlot leaving = classes.slot(slot).orElseThrow();
        List<ClassSlot> sleeping = new java.util.ArrayList<>(classes.dormantBranches());
        sleeping.removeIf(s -> s.classId().equals(leaving.classId()) || s.classId().equals(branch));
        sleeping.add(leaving);
        ClassSlot entering = classes.dormant(branch).map(s -> s.inSlot(slot)).orElseGet(() -> ClassSlot.fresh(slot, branch));
        return classes.withSlot(entering).withDormant(sleeping).withFreeUsed(slot);
    }

    /** The job is chosen once: only while there is none. */
    public static boolean canChooseJob(PlayerClasses classes) {
        return classes.job().isEmpty();
    }

    /** The first open and free slot at {@code playerLevel}, or -1 if there is none. */
    public static int firstFreeSlot(PlayerClasses classes, int playerLevel) {
        int open = unlockedSlots(playerLevel);
        for (int slot = 0; slot < open; slot++) {
            if (classes.slot(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /** The highest grade a class of {@code classLevel} can have: grade number g (0 for Apprentice) needs level 100 times g. */
    public static ClassGrade eligibleGrade(int classLevel) {
        ClassGrade[] grades = ClassGrade.values();
        int index = Math.min(grades.length - 1, Math.max(0, classLevel) / ClassGrade.LEVELS_PER_GRADE);
        return grades[index];
    }

    /** {@code NO_CLASS} is for the service: the slot is empty. */
    public enum Advance { OK, NO_CLASS, MAX_GRADE, LEVEL_TOO_LOW, JOB_TOO_LOW }

    /**
     * Whether the class in {@code slot} may go up to the next grade: one grade at a time, its class level must reach
     * that grade's and every requirement of that grade must be met by the job levels ({@code jobLevels}: job id to level, a missing job is 0).
     */
    public static Advance advance(ClassSlot slot, List<GradeRequirement> requirements, Map<ResourceLocation, Integer> jobLevels) {
        ClassGrade next = slot.grade().next().orElse(null);
        if (next == null) {
            return Advance.MAX_GRADE;
        }
        if (eligibleGrade(slot.classLevel()).ordinal() < next.ordinal()) {
            return Advance.LEVEL_TOO_LOW;
        }
        for (GradeRequirement requirement : requirements) {
            if (requirement.grade() == next && jobLevels.getOrDefault(requirement.job(), 0) < requirement.jobLevel()) {
                return Advance.JOB_TOO_LOW;
            }
        }
        return Advance.OK;
    }

    public static boolean canAdvance(ClassSlot slot, List<GradeRequirement> requirements, Map<ResourceLocation, Integer> jobLevels) {
        return advance(slot, requirements, jobLevels) == Advance.OK;
    }

    /** The XP multiplier of a late starter: {@code catchUp.multiplier()} while the class is at least {@code minGap} levels below the player, else 1. */
    public static double catchUpMultiplier(int classLevel, int playerLevel, PlayerClassDefinition.CatchUp catchUp) {
        return catchUp.enabled() && playerLevel - classLevel >= catchUp.minGap() ? catchUp.multiplier() : 1.0;
    }

    /** Adds level XP to a placed class with the same curve as the player's levels ({@link ProgressionRules#addXp}). */
    public static ClassSlot addClassXp(Balance balance, ClassSlot slot, int amount) {
        PlayerStats result = ProgressionRules.addXp(balance, PlayerStats.DEFAULT.withProgress(slot.classLevel(), slot.classXp(), 0), amount).stats();
        return slot.withProgress(result.level(), result.xp());
    }
}
