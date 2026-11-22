package com.orijua.mitofus.core.validation;

import com.orijua.mitofus.feature.progression.ClassGrade;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cross-checks the JSON content: Minecraft loads each file on its own, so a misspelled id in one file
 * only shows up in play (a class that silently grants no skill). Pure: no Minecraft state here.
 */
public final class ContentValidator {
    private ContentValidator() {
    }

    /** Every problem found, as a readable sentence, sorted; empty when the content is consistent. */
    public static List<String> validate(ContentIndex content) {
        List<String> problems = new ArrayList<>();
        for (ContentIndex.Reference reference : content.references()) {
            Set<ResourceLocation> existing = switch (reference.kind()) {
                case SKILL -> content.skills();
                case CLASS -> content.classes();
                case QUEST -> content.quests();
                case OPTION -> content.itemOptions();
                case PASSIVE -> content.passives();
                case JOB -> content.jobs();
            };
            if (!existing.contains(reference.target())) {
                problems.add(reference.from() + ": " + reference.kind().name().toLowerCase() + " " + reference.target() + " does not exist");
            }
        }
        for (ResourceLocation advanced : content.advancedClasses()) {
            if (!content.rewardedClasses().contains(advanced)) {
                problems.add("class " + advanced + ": can't be chosen and no quest grants it, so nobody can get it");
            }
        }
        for (ResourceLocation advanced : content.advancedJobs()) {
            if (!content.rewardedJobs().contains(advanced)) {
                problems.add("job " + advanced + ": can't be chosen and no quest grants it, so nobody can get it");
            }
        }
        Set<String> seenGrades = new HashSet<>();
        for (ContentIndex.GradeRule rule : content.gradeRules()) {
            if (rule.grade() == ClassGrade.APPRENTICE) {
                problems.add(rule.from() + ": grade apprentice is the starting grade and can't have a requirement");
            }
            if (rule.jobLevel() < 1) {
                problems.add(rule.from() + ": grade " + rule.grade().getSerializedName() + " has job_level " + rule.jobLevel() + ", it must be at least 1");
            }
            if (!seenGrades.add(rule.from() + "|" + rule.grade().getSerializedName())) {
                problems.add(rule.from() + ": grade " + rule.grade().getSerializedName() + " is declared more than once");
            }
        }
        for (ResourceLocation quest : content.visibleClassQuests()) {
            problems.add("quest " + quest + ": grants a class but is visible to everyone; give it a discover_when");
        }
        for (ResourceLocation quest : content.questRequirements().keySet()) {
            if (loopsBack(quest, content.questRequirements())) {
                problems.add("quest " + quest + ": its requires_quest chain loops back to it, so it can never be accepted");
            }
        }
        content.questGivers().forEach((quest, giver) -> {
            if (!content.questGiverNpcs().contains(giver)) {
                problems.add("quest " + quest + ": giver " + giver + " has no quest window, so nobody can accept it");
            }
        });
        for (String owner : content.deliveriesWithoutGiver()) {
            problems.add(owner + ": a deliver goal needs a quest with a giver to hand the items to");
        }
        problems.sort(null);
        return problems;
    }

    /** Follows requires_quest from {@code start}; true if the chain comes back to it. */
    private static boolean loopsBack(ResourceLocation start, Map<ResourceLocation, ResourceLocation> requirements) {
        Set<ResourceLocation> seen = new HashSet<>();
        ResourceLocation current = requirements.get(start);
        while (current != null && seen.add(current)) {
            if (current.equals(start)) {
                return true;
            }
            current = requirements.get(current);
        }
        return false;
    }
}
