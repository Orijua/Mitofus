package com.orijua.mitofus.core.validation;

import com.orijua.mitofus.feature.progression.ClassGrade;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What {@link ContentValidator} needs to know about the loaded content: which ids exist and which ids
 * each definition points to. Built from the real registries by {@link ContentScanner}, or by hand in tests.
 *
 * @param advancedClasses   classes with {@code "selectable": false}: only a quest's {@code class_reward} can grant them
 * @param rewardedClasses   every class some quest grants with {@code class_reward}
 * @param questRequirements quest → the quest its {@code requires_quest} points to, to find chains that loop
 * @param visibleClassQuests quests with a {@code class_reward} but no {@code discover_when}: in Mitofus every
 *                           class quest is found by the player, never listed for everyone
 * @param questGivers       quest → the NPC (entity type) in its {@code giver}
 * @param questGiverNpcs    the NPCs with a quest window, the only ones a {@code giver} can name
 * @param deliveriesWithoutGiver where a deliver goal sits with no giver to hand the items to ("quest x", "title y")
 * @param itemOptions       the item options that exist, for recipes' fixed options
 * @param passives          the passives that exist, for classes' {@code passives} and {@code third_slot_passive}
 * @param jobs              the jobs that exist
 * @param advancedJobs      jobs with {@code "selectable": false}: only a quest's {@code job_reward} can grant them
 * @param rewardedJobs      every job some quest grants with {@code job_reward}
 * @param gradeRules        every entry of every class's {@code grade_requirements}
 * @param talents           the talent nodes that exist
 * @param talentRules       the facts of every talent node the tree checks need
 */
public record ContentIndex(
        Set<ResourceLocation> skills,
        Set<ResourceLocation> classes,
        Set<ResourceLocation> quests,
        Set<ResourceLocation> advancedClasses,
        Set<ResourceLocation> rewardedClasses,
        Map<ResourceLocation, ResourceLocation> questRequirements,
        Set<ResourceLocation> visibleClassQuests,
        List<Reference> references,
        Map<ResourceLocation, ResourceLocation> questGivers,
        Set<ResourceLocation> questGiverNpcs,
        Set<String> deliveriesWithoutGiver,
        Set<ResourceLocation> itemOptions,
        Set<ResourceLocation> passives,
        Set<ResourceLocation> jobs,
        Set<ResourceLocation> advancedJobs,
        Set<ResourceLocation> rewardedJobs,
        List<GradeRule> gradeRules,
        Set<ResourceLocation> talents,
        List<TalentRule> talentRules) {

    /** Content without talent nodes. */
    public ContentIndex(Set<ResourceLocation> skills, Set<ResourceLocation> classes, Set<ResourceLocation> quests,
                        Set<ResourceLocation> advancedClasses, Set<ResourceLocation> rewardedClasses,
                        Map<ResourceLocation, ResourceLocation> questRequirements, Set<ResourceLocation> visibleClassQuests,
                        List<Reference> references, Map<ResourceLocation, ResourceLocation> questGivers,
                        Set<ResourceLocation> questGiverNpcs, Set<String> deliveriesWithoutGiver, Set<ResourceLocation> itemOptions,
                        Set<ResourceLocation> passives, Set<ResourceLocation> jobs, Set<ResourceLocation> advancedJobs,
                        Set<ResourceLocation> rewardedJobs, List<GradeRule> gradeRules) {
        this(skills, classes, quests, advancedClasses, rewardedClasses, questRequirements, visibleClassQuests, references,
                questGivers, questGiverNpcs, deliveriesWithoutGiver, itemOptions, passives, jobs, advancedJobs, rewardedJobs,
                gradeRules, Set.of(), List.of());
    }

    /** Content without jobs, passives or grades. */
    public ContentIndex(Set<ResourceLocation> skills, Set<ResourceLocation> classes, Set<ResourceLocation> quests,
                        Set<ResourceLocation> advancedClasses, Set<ResourceLocation> rewardedClasses,
                        Map<ResourceLocation, ResourceLocation> questRequirements, Set<ResourceLocation> visibleClassQuests,
                        List<Reference> references, Map<ResourceLocation, ResourceLocation> questGivers,
                        Set<ResourceLocation> questGiverNpcs, Set<String> deliveriesWithoutGiver, Set<ResourceLocation> itemOptions) {
        this(skills, classes, quests, advancedClasses, rewardedClasses, questRequirements, visibleClassQuests, references,
                questGivers, questGiverNpcs, deliveriesWithoutGiver, itemOptions, Set.of(), Set.of(), Set.of(), Set.of(), List.of(), Set.of(), List.of());
    }

    /** Content without NPC quests, as most tests build it. */
    public ContentIndex(Set<ResourceLocation> skills, Set<ResourceLocation> classes, Set<ResourceLocation> quests,
                        Set<ResourceLocation> advancedClasses, Set<ResourceLocation> rewardedClasses,
                        Map<ResourceLocation, ResourceLocation> questRequirements, Set<ResourceLocation> visibleClassQuests,
                        List<Reference> references) {
        this(skills, classes, quests, advancedClasses, rewardedClasses, questRequirements, visibleClassQuests, references,
                Map.of(), Set.of(), Set.of());
    }

    /** Content without recipes' fixed options. */
    public ContentIndex(Set<ResourceLocation> skills, Set<ResourceLocation> classes, Set<ResourceLocation> quests,
                        Set<ResourceLocation> advancedClasses, Set<ResourceLocation> rewardedClasses,
                        Map<ResourceLocation, ResourceLocation> questRequirements, Set<ResourceLocation> visibleClassQuests,
                        List<Reference> references, Map<ResourceLocation, ResourceLocation> questGivers,
                        Set<ResourceLocation> questGiverNpcs, Set<String> deliveriesWithoutGiver) {
        this(skills, classes, quests, advancedClasses, rewardedClasses, questRequirements, visibleClassQuests, references,
                questGivers, questGiverNpcs, deliveriesWithoutGiver, Set.of());
    }

    public enum Kind {
        SKILL,
        CLASS,
        QUEST,
        OPTION,
        PASSIVE,
        JOB
    }

    /** One entry of a class's {@code grade_requirements}: {@code from} (e.g. "class test:swordsman") needs {@code jobLevel} of {@code job} for {@code grade}. */
    public record GradeRule(String from, ClassGrade grade, ResourceLocation job, int jobLevel) {
    }

    /**
     * What the tree checks need to know about one talent node. {@code classSkills} and {@code classPassives} are what the node's class
     * gives on its own, so a boost can be matched to something the class really has.
     */
    public record TalentRule(ResourceLocation id, ResourceLocation playerClass, List<ResourceLocation> requires,
                             List<ResourceLocation> boostedSkills, List<ResourceLocation> boostedPassives,
                             List<ResourceLocation> grantedSkills, Set<ResourceLocation> classSkills,
                             Set<ResourceLocation> classPassives, boolean emptyEffects, boolean idleSkillBoost) {
    }

    /** "{@code from} needs the {@code kind} {@code target} to exist", e.g. class warrior → skill sancho_link. */
    public record Reference(String from, Kind kind, ResourceLocation target) {
    }
}
