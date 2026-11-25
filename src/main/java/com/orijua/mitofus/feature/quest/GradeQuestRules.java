package com.orijua.mitofus.feature.quest;

import com.orijua.mitofus.feature.progression.ClassSlot;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Pure rules of when a class grade quest can be taken: the class is in a slot at the quest's grade, the class level reaches the next
 * grade, the whole talent tree that can be bought at this grade is owned, and the quest is the variant for the player's job.
 */
public final class GradeQuestRules {
    private GradeQuestRules() {
    }

    /** Why a grade quest can or cannot be taken now; the first thing that stops it. */
    public enum Check { OK, NO_CLASS, WRONG_GRADE, WRONG_JOB, LEVEL_TOO_LOW, TREE_INCOMPLETE }

    /**
     * @param slot            the slot that holds the quest's class, if any
     * @param treeComplete    the slot owns every node it can buy at its grade
     * @param playerJob       the player's job, if any
     * @param jobVariantExists some grade quest of the same class and grade is the variant of {@code playerJob}
     */
    public static Check check(GradeQuest quest, Optional<ClassSlot> slot, boolean treeComplete, Optional<ResourceLocation> playerJob,
                              boolean jobVariantExists) {
        if (slot.isEmpty()) {
            return Check.NO_CLASS;
        }
        if (slot.get().grade() != quest.from()) {
            return Check.WRONG_GRADE;
        }
        boolean forThisPlayer = quest.job().isPresent() ? quest.job().equals(playerJob) : !jobVariantExists;
        if (!forThisPlayer) {
            return Check.WRONG_JOB;
        }
        // The last grade has no next one to rank up to, but its own quest opens at the level the grade ends.
        boolean levelReached = quest.from().next().isPresent() ? quest.from().canRankUp(slot.get().classLevel())
                : slot.get().classLevel() >= quest.from().levelToRankUp();
        if (!levelReached) {
            return Check.LEVEL_TOO_LOW;
        }
        return treeComplete ? Check.OK : Check.TREE_INCOMPLETE;
    }

    /** Whether the quest is the one the player is meant to see for their class, grade and job, whatever their level or tree. */
    public static boolean isForPlayer(Check check) {
        return check != Check.NO_CLASS && check != Check.WRONG_GRADE && check != Check.WRONG_JOB;
    }
}
