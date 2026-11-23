package com.orijua.mitofus.feature.talent;

import com.orijua.mitofus.feature.progression.ClassSlot;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rules of the talent tree, with no Minecraft state. A class earns 1 talent point per class level; a node costs what
 * its JSON says; roots are free and always owned. Points are never stored: they are the class level minus what the bought
 * nodes cost now, so a datapack that changes a cost or removes a node can never leave a player with negative points.
 */
public final class TalentRules {
    public static final int POINTS_PER_LEVEL = 1;

    private TalentRules() {
    }

    public enum Buy {
        OK,
        UNKNOWN,
        WRONG_CLASS,
        OWNED,
        LOCKED,
        GRADE,
        POINTS
    }

    /** The talent points a class level gives in total. */
    public static int pointsEarned(int classLevel) {
        return Math.max(0, classLevel) * POINTS_PER_LEVEL;
    }

    /** The nodes the slot owns: what it bought (only nodes of its class that still exist) plus the root(s) of its tree. */
    public static Set<ResourceLocation> owned(Map<ResourceLocation, ? extends TalentNode> all, ClassSlot slot) {
        Set<ResourceLocation> owned = new HashSet<>();
        all.forEach((id, node) -> {
            if (node.playerClass().equals(slot.classId()) && (node.isRoot() || slot.talents().contains(id))) {
                owned.add(id);
            }
        });
        return owned;
    }

    public static int pointsSpent(Map<ResourceLocation, ? extends TalentNode> all, ClassSlot slot) {
        int spent = 0;
        for (ResourceLocation id : owned(all, slot)) {
            spent += all.get(id).cost();
        }
        return spent;
    }

    public static int pointsAvailable(Map<ResourceLocation, ? extends TalentNode> all, ClassSlot slot) {
        return Math.max(0, pointsEarned(slot.classLevel()) - pointsSpent(all, slot));
    }

    /** Whether {@code slot} may buy {@code node} now, and if not, the first thing that stops it. */
    public static Buy check(Map<ResourceLocation, ? extends TalentNode> all, ClassSlot slot, ResourceLocation node) {
        TalentNode definition = all.get(node);
        if (definition == null) {
            return Buy.UNKNOWN;
        }
        if (!definition.playerClass().equals(slot.classId())) {
            return Buy.WRONG_CLASS;
        }
        Set<ResourceLocation> owned = owned(all, slot);
        if (owned.contains(node)) {
            return Buy.OWNED;
        }
        if (!owned.containsAll(definition.requires())) {
            return Buy.LOCKED;
        }
        if (slot.grade().ordinal() < definition.minGrade().ordinal()) {
            return Buy.GRADE;
        }
        if (pointsAvailable(all, slot) < definition.cost()) {
            return Buy.POINTS;
        }
        return Buy.OK;
    }

    /** The slot with {@code node} added to its bought nodes; call only after {@link #check} said OK. */
    public static ClassSlot buy(ClassSlot slot, ResourceLocation node) {
        List<ResourceLocation> bought = new ArrayList<>(slot.talents());
        bought.add(node);
        return slot.withTalents(bought);
    }
}
