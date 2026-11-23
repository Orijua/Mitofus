package com.orijua.mitofus.core.validation;

import com.orijua.mitofus.feature.progression.ClassGrade;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
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
        validateTalents(content, problems);
        for (String owner : content.deliveriesWithoutGiver()) {
            problems.add(owner + ": a deliver goal needs a quest with a giver to hand the items to");
        }
        problems.sort(null);
        return problems;
    }

    /** The talent trees: every node's prerequisites exist in its own class, each tree has one root and no loops, boosts point at something the class has. */
    private static void validateTalents(ContentIndex content, List<String> problems) {
        Map<ResourceLocation, ContentIndex.TalentRule> byId = new HashMap<>();
        content.talentRules().forEach(rule -> byId.put(rule.id(), rule));
        Map<ResourceLocation, Integer> roots = new HashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> requirements = new HashMap<>();
        for (ContentIndex.TalentRule rule : content.talentRules()) {
            String from = "talent " + rule.id();
            if (!content.classes().contains(rule.playerClass())) {
                problems.add(from + ": class " + rule.playerClass() + " does not exist");
            }
            if (rule.requires().isEmpty()) {
                roots.merge(rule.playerClass(), 1, Integer::sum);
            }
            if (rule.emptyEffects()) {
                problems.add(from + ": has no effects, so owning it gives nothing");
            }
            if (rule.idleSkillBoost()) {
                problems.add(from + ": a skill_boost changes no multiplier, so it gives nothing");
            }
            for (ResourceLocation required : rule.requires()) {
                ContentIndex.TalentRule other = byId.get(required);
                if (other == null) {
                    problems.add(from + ": requires talent " + required + " that does not exist");
                } else if (!other.playerClass().equals(rule.playerClass())) {
                    problems.add(from + ": requires talent " + required + " of another class");
                }
            }
            requirements.put(rule.id(), rule.requires());
            for (ResourceLocation skill : rule.boostedSkills()) {
                boolean granted = content.talentRules().stream()
                        .anyMatch(other -> other.playerClass().equals(rule.playerClass()) && other.grantedSkills().contains(skill));
                if (!rule.classSkills().contains(skill) && !granted) {
                    problems.add(from + ": boosts skill " + skill + " that the class neither has nor learns from a talent");
                }
                if (!content.skills().contains(skill)) {
                    problems.add(from + ": skill " + skill + " does not exist");
                }
            }
            for (ResourceLocation skill : rule.grantedSkills()) {
                if (!content.skills().contains(skill)) {
                    problems.add(from + ": skill " + skill + " does not exist");
                }
            }
            for (ResourceLocation passive : rule.boostedPassives()) {
                if (!rule.classPassives().contains(passive)) {
                    problems.add(from + ": boosts passive " + passive + " that the class does not have");
                }
            }
        }
        roots.forEach((playerClass, count) -> {
            if (count > 1) {
                problems.add("class " + playerClass + ": its talent tree has " + count + " roots, it needs exactly one");
            }
        });
        Set<ResourceLocation> withoutRoot = new HashSet<>();
        for (ContentIndex.TalentRule rule : content.talentRules()) {
            if (!roots.containsKey(rule.playerClass()) && withoutRoot.add(rule.playerClass())) {
                problems.add("class " + rule.playerClass() + ": its talent tree has no root (a node with no requires)");
            }
        }
        for (ContentIndex.TalentRule rule : content.talentRules()) {
            if (talentLoops(rule.id(), requirements)) {
                problems.add("talent " + rule.id() + ": its requires chain loops back to it, so it can never be bought");
            }
        }
    }

    private static boolean talentLoops(ResourceLocation start, Map<ResourceLocation, List<ResourceLocation>> requirements) {
        Set<ResourceLocation> seen = new HashSet<>();
        ArrayDeque<ResourceLocation> pending = new ArrayDeque<>(requirements.getOrDefault(start, List.of()));
        while (!pending.isEmpty()) {
            ResourceLocation current = pending.poll();
            if (current.equals(start)) {
                return true;
            }
            if (seen.add(current)) {
                pending.addAll(requirements.getOrDefault(current, List.of()));
            }
        }
        return false;
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
