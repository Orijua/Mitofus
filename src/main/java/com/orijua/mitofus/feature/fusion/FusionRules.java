package com.orijua.mitofus.feature.fusion;

import com.orijua.mitofus.feature.progression.ClassSlot;
import com.orijua.mitofus.feature.talent.TalentNode;
import com.orijua.mitofus.feature.talent.TalentRules;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The pure rules of a class fusion, with no Minecraft state: what a fusion still needs ({@link #missing}) and what the hybrid is
 * made of ({@link #fuse}). Two classes fuse when both trees are complete, both are normal or both legendary, both have the same grade
 * (unless the fusion allows mixed grades) and the coins are there. Nothing is ever lost: every bought node and spent point carries over.
 */
public final class FusionRules {
    private FusionRules() {
    }

    /** What stops a fusion, shown greyed in the list. */
    public enum Missing {
        /** The player has not placed one of the two classes (or it is already part of a hybrid). */
        CLASS,
        /** A parent's talent tree is not complete yet. */
        TREE,
        /** One parent is legendary and the other is not. */
        RANK,
        /** The parents are not at the same grade. */
        GRADE,
        /** Not enough coins. */
        COINS
    }

    /** A parent as the rules see it: the slot it sits in (empty if not placed), whether it is legendary and its tree is complete. */
    public record Parent(Optional<ClassSlot> slot, boolean legendary, boolean treeComplete) {
    }

    public static Set<Missing> missing(Parent first, Parent second, boolean allowMixedGrades, boolean canPay) {
        Set<Missing> missing = EnumSet.noneOf(Missing.class);
        if (first.slot().isEmpty() || second.slot().isEmpty() || first.slot().get().isHybrid() || second.slot().get().isHybrid()) {
            missing.add(Missing.CLASS);
        } else {
            if (first.legendary() != second.legendary()) {
                missing.add(Missing.RANK);
            }
            if (!allowMixedGrades && first.slot().get().grade() != second.slot().get().grade()) {
                missing.add(Missing.GRADE);
            }
            if (!first.treeComplete() || !second.treeComplete()) {
                missing.add(Missing.TREE);
            }
        }
        if (!canPay) {
            missing.add(Missing.COINS);
        }
        return missing;
    }

    /**
     * What stops a hybrid from absorbing a lone class: the hybrid must be a hybrid and the other a normal placed class, both
     * of the same rank, the lone class's tree complete (the hybrid's own tree is not asked, so it is never stuck), and the coins there. The grade does not matter: the higher one stays.
     */
    public static Set<Missing> missingAbsorb(Parent hybrid, Parent lone, boolean canPay) {
        Set<Missing> missing = EnumSet.noneOf(Missing.class);
        if (hybrid.slot().isEmpty() || !hybrid.slot().get().isHybrid() || lone.slot().isEmpty() || lone.slot().get().isHybrid()) {
            missing.add(Missing.CLASS);
        } else {
            if (hybrid.legendary() != lone.legendary()) {
                missing.add(Missing.RANK);
            }
            if (!lone.treeComplete()) {
                missing.add(Missing.TREE);
            }
        }
        if (!canPay) {
            missing.add(Missing.COINS);
        }
        return missing;
    }

    /**
     * The hybrid after absorbing {@code lone}: it keeps its slot, class and identity, takes the higher level (and that XP) and
     * grade, every node either had bought, and adds the absorbed class to its origin, so it gets that class's tree and forms.
     */
    public static ClassSlot absorb(ClassSlot hybrid, ClassSlot lone, int paidPoints) {
        boolean loneHigher = lone.classLevel() > hybrid.classLevel() || (lone.classLevel() == hybrid.classLevel() && lone.classXp() > hybrid.classXp());
        ClassSlot higher = loneHigher ? lone : hybrid;
        List<ResourceLocation> talents = new ArrayList<>(hybrid.talents());
        lone.talents().stream().filter(node -> !talents.contains(node)).forEach(talents::add);
        List<ResourceLocation> origin = new ArrayList<>(hybrid.origin());
        origin.add(lone.classId());
        return new ClassSlot(hybrid.slot(), hybrid.classId(), higher.classLevel(), higher.classXp(),
                hybrid.grade().ordinal() >= lone.grade().ordinal() ? hybrid.grade() : lone.grade(), List.copyOf(talents), List.copyOf(origin), paidPoints);
    }

    /** Whether every node of the slot's own class tree is owned. A class with no tree has nothing left to buy, so it counts as complete. */
    public static boolean treeComplete(Map<ResourceLocation, ? extends TalentNode> all, ClassSlot slot) {
        Set<ResourceLocation> owned = TalentRules.owned(all, slot);
        for (Map.Entry<ResourceLocation, ? extends TalentNode> entry : all.entrySet()) {
            if (entry.getValue().playerClass().equals(slot.classId()) && !owned.contains(entry.getKey())) {
                return false;
            }
        }
        return true;
    }

    /**
     * The hybrid that replaces the two parents: it takes the lower slot, the higher class level (and that class's XP), the higher grade,
     * every node either parent had bought, and remembers the parents. {@code paidPoints} are the talent points both trees had cost,
     * which stay paid (see {@link TalentRules#pointsAvailable}). The other slot is simply left empty by the caller.
     */
    public static ClassSlot fuse(ClassSlot first, ClassSlot second, ResourceLocation resultClass, int paidPoints) {
        ClassSlot higher = second.classLevel() > first.classLevel()
                || (second.classLevel() == first.classLevel() && second.classXp() > first.classXp()) ? second : first;
        List<ResourceLocation> talents = new ArrayList<>(first.talents());
        second.talents().stream().filter(node -> !talents.contains(node)).forEach(talents::add);
        return new ClassSlot(Math.min(first.slot(), second.slot()), resultClass, higher.classLevel(), higher.classXp(),
                first.grade().ordinal() >= second.grade().ordinal() ? first.grade() : second.grade(),
                List.copyOf(talents), List.of(first.classId(), second.classId()), paidPoints);
    }
}
