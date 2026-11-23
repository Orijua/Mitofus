package com.orijua.mitofus.core.validation;

import com.orijua.mitofus.core.validation.ContentIndex.Kind;
import com.orijua.mitofus.core.validation.ContentIndex.GradeRule;
import com.orijua.mitofus.core.validation.ContentIndex.Reference;
import com.orijua.mitofus.feature.progression.ClassGrade;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentValidatorTest {
    private static final ResourceLocation SLASH = id("slash");
    private static final ResourceLocation WARRIOR = id("warrior");
    private static final ResourceLocation HERO = id("hero");
    private static final ResourceLocation TRIAL = id("trial");
    private static final ResourceLocation HUNT = id("hunt");
    private static final ResourceLocation FINALE = id("finale");

    @Test
    void consistentContentHasNoProblems() {
        ContentIndex content = new ContentIndex(Set.of(SLASH), Set.of(WARRIOR, HERO), Set.of(TRIAL, HUNT), Set.of(HERO), Set.of(HERO),
                Map.of(HUNT, TRIAL), Set.of(), List.of(
                new Reference("class test:warrior", Kind.SKILL, SLASH),
                new Reference("quest test:trial", Kind.CLASS, WARRIOR),
                new Reference("quest test:trial", Kind.CLASS, HERO),
                new Reference("quest test:hunt", Kind.QUEST, TRIAL)));
        assertEquals(List.of(), ContentValidator.validate(content));
    }

    @Test
    void reportsAMissingSkill() {
        ContentIndex content = references(new Reference("class test:warrior", Kind.SKILL, id("slsh")));
        assertEquals(List.of("class test:warrior: skill test:slsh does not exist"), ContentValidator.validate(content));
    }

    @Test
    void reportsAMissingClass() {
        ContentIndex content = references(new Reference("quest test:trial", Kind.CLASS, id("mage")));
        assertEquals(List.of("quest test:trial: class test:mage does not exist"), ContentValidator.validate(content));
    }

    @Test
    void reportsAMissingPreviousQuest() {
        ContentIndex content = references(new Reference("quest test:hunt", Kind.QUEST, id("trail")));
        assertEquals(List.of("quest test:hunt: quest test:trail does not exist"), ContentValidator.validate(content));
    }

    @Test
    void reportsAnAdvancedClassNoQuestGrants() {
        ContentIndex content = new ContentIndex(Set.of(), Set.of(WARRIOR, HERO), Set.of(), Set.of(HERO), Set.of(), Map.of(), Set.of(), List.of());
        assertEquals(List.of("class test:hero: can't be chosen and no quest grants it, so nobody can get it"),
                ContentValidator.validate(content));
    }

    @Test
    void reportsAClassQuestVisibleToEveryone() {
        ContentIndex content = new ContentIndex(Set.of(), Set.of(HERO), Set.of(TRIAL), Set.of(), Set.of(HERO), Map.of(), Set.of(TRIAL), List.of());
        assertEquals(List.of("quest test:trial: grants a class but is visible to everyone; give it a discover_when"),
                ContentValidator.validate(content));
    }

    @Test
    void reportsEveryQuestOfAChainThatLoops() {
        // trial → finale → hunt → trial loops; the extra quest only leads into the loop and is fine on its own.
        ResourceLocation extra = id("extra");
        Map<ResourceLocation, ResourceLocation> requirements = Map.of(TRIAL, FINALE, FINALE, HUNT, HUNT, TRIAL, extra, TRIAL);
        ContentIndex content = new ContentIndex(Set.of(), Set.of(), Set.of(TRIAL, HUNT, FINALE, extra), Set.of(), Set.of(),
                requirements, Set.of(), List.of());
        assertEquals(List.of(
                "quest test:finale: its requires_quest chain loops back to it, so it can never be accepted",
                "quest test:hunt: its requires_quest chain loops back to it, so it can never be accepted",
                "quest test:trial: its requires_quest chain loops back to it, so it can never be accepted"),
                ContentValidator.validate(content));
    }

    @Test
    void reportsEveryProblemSorted() {
        ContentIndex content = references(
                new Reference("quest test:b", Kind.SKILL, SLASH),
                new Reference("class test:a", Kind.SKILL, SLASH));
        assertEquals(List.of(
                "class test:a: skill test:slash does not exist",
                "quest test:b: skill test:slash does not exist"), ContentValidator.validate(content));
    }

    @Test
    void npcQuestsNeedAGiverWithAQuestWindow() {
        ResourceLocation smith = id("smith");
        ResourceLocation cow = ResourceLocation.withDefaultNamespace("cow");
        ContentIndex content = new ContentIndex(Set.of(), Set.of(), Set.of(TRIAL, HUNT), Set.of(), Set.of(), Map.of(), Set.of(), List.of(),
                Map.of(TRIAL, smith, HUNT, cow), Set.of(smith), Set.of("quest test:finale", "title test:hoarder"));
        assertEquals(List.of(
                "quest test:finale: a deliver goal needs a quest with a giver to hand the items to",
                "quest test:hunt: giver minecraft:cow has no quest window, so nobody can accept it",
                "title test:hoarder: a deliver goal needs a quest with a giver to hand the items to"), ContentValidator.validate(content));
    }

    @Test
    void aRecipeWithAMissingFixedOptionIsReported() {
        ResourceLocation agility = id("agility");
        ContentIndex content = new ContentIndex(Set.of(), Set.of(HERO), Set.of(), Set.of(), Set.of(), Map.of(), Set.of(), List.of(
                new Reference("forging recipe test:guadalete", Kind.CLASS, HERO),
                new Reference("forging recipe test:guadalete", Kind.OPTION, agility),
                new Reference("forging recipe test:guadalete", Kind.OPTION, id("agilty"))),
                Map.of(), Set.of(), Set.of(), Set.of(agility));
        assertEquals(List.of("forging recipe test:guadalete: option test:agilty does not exist"), ContentValidator.validate(content));
    }

    private static final ResourceLocation SMITH = id("smith");
    private static final ResourceLocation HEIR = id("heir");
    private static final ResourceLocation TOUGH = id("tough");

    private static ContentIndex withJobs(Set<ResourceLocation> advancedJobs, Set<ResourceLocation> rewardedJobs,
                                         Set<ResourceLocation> passives, List<Reference> references, List<GradeRule> rules) {
        return new ContentIndex(Set.of(), Set.of(WARRIOR), Set.of(), Set.of(), Set.of(), Map.of(), Set.of(), List.copyOf(references),
                Map.of(), Set.of(), Set.of(), Set.of(), passives, Set.of(SMITH, HEIR), advancedJobs, rewardedJobs, rules);
    }

    @Test
    void reportsAMissingPassiveAndJob() {
        ContentIndex content = withJobs(Set.of(), Set.of(), Set.of(TOUGH), List.of(
                new Reference("class test:warrior", Kind.PASSIVE, TOUGH),
                new Reference("class test:warrior", Kind.PASSIVE, id("tuff")),
                new Reference("class test:warrior", Kind.JOB, SMITH),
                new Reference("class test:warrior", Kind.JOB, id("smth")),
                new Reference("quest test:trial", Kind.JOB, id("heirr"))), List.of());
        assertEquals(List.of(
                "class test:warrior: job test:smth does not exist",
                "class test:warrior: passive test:tuff does not exist",
                "quest test:trial: job test:heirr does not exist"), ContentValidator.validate(content));
    }

    @Test
    void reportsAnAdvancedJobNoQuestGrants() {
        ContentIndex content = withJobs(Set.of(HEIR), Set.of(), Set.of(), List.of(), List.of());
        assertEquals(List.of("job test:heir: can't be chosen and no quest grants it, so nobody can get it"), ContentValidator.validate(content));
        assertEquals(List.of(), ContentValidator.validate(withJobs(Set.of(HEIR), Set.of(HEIR), Set.of(), List.of(), List.of())));
    }

    @Test
    void reportsGradeRulesThatMakeNoSense() {
        ContentIndex content = withJobs(Set.of(), Set.of(), Set.of(), List.of(), List.of(
                new GradeRule("class test:warrior", ClassGrade.APPRENTICE, SMITH, 3),
                new GradeRule("class test:warrior", ClassGrade.MASTER, SMITH, 0),
                new GradeRule("class test:warrior", ClassGrade.ADEPT, SMITH, 5),
                new GradeRule("class test:warrior", ClassGrade.ADEPT, SMITH, 8)));
        assertEquals(List.of(
                "class test:warrior: grade adept is declared more than once",
                "class test:warrior: grade apprentice is the starting grade and can't have a requirement",
                "class test:warrior: grade master has job_level 0, it must be at least 1"), ContentValidator.validate(content));
    }

    @Test
    void validGradeRulesAndClassesWithoutSkillsOrPassivesAreFine() {
        ContentIndex content = withJobs(Set.of(), Set.of(), Set.of(), List.of(), List.of(
                new GradeRule("class test:warrior", ClassGrade.ADEPT, SMITH, 5),
                new GradeRule("class test:warrior", ClassGrade.MASTER, SMITH, 10),
                new GradeRule("class test:other", ClassGrade.ADEPT, SMITH, 5)));
        assertEquals(List.of(), ContentValidator.validate(content));
    }

    private static final ResourceLocation ROOT = id("root");
    private static final ResourceLocation LEAF = id("leaf");
    private static final ResourceLocation OTHER_ROOT = id("other_root");

    private static ContentIndex.TalentRule talent(ResourceLocation id, ResourceLocation playerClass, List<ResourceLocation> requires) {
        return new ContentIndex.TalentRule(id, playerClass, requires, List.of(), List.of(), List.of(), Set.of(), Set.of(), false, false);
    }

    private static ContentIndex withTalents(Set<ResourceLocation> skills, ContentIndex.TalentRule... rules) {
        return new ContentIndex(skills, Set.of(WARRIOR, HERO), Set.of(), Set.of(), Set.of(), Map.of(), Set.of(), List.of(), Map.of(), Set.of(),
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), List.of(), Set.of(), List.of(rules));
    }

    @Test
    void aCleanTalentTreeHasNoProblems() {
        assertEquals(List.of(), ContentValidator.validate(withTalents(Set.of(),
                talent(ROOT, WARRIOR, List.of()), talent(LEAF, WARRIOR, List.of(ROOT)), talent(OTHER_ROOT, HERO, List.of()))));
    }

    @Test
    void reportsTalentRequirementsThatAreMissingOrOfAnotherClass() {
        assertEquals(List.of(
                "talent test:leaf: requires talent test:gone that does not exist",
                "talent test:other_leaf: requires talent test:root of another class"),
                ContentValidator.validate(withTalents(Set.of(), talent(ROOT, WARRIOR, List.of()), talent(OTHER_ROOT, HERO, List.of()),
                        talent(LEAF, WARRIOR, List.of(id("gone"))), talent(id("other_leaf"), HERO, List.of(ROOT)))));
    }

    @Test
    void reportsATreeWithTwoRootsOrNone() {
        assertEquals(List.of("class test:warrior: its talent tree has 2 roots, it needs exactly one"),
                ContentValidator.validate(withTalents(Set.of(), talent(ROOT, WARRIOR, List.of()), talent(LEAF, WARRIOR, List.of()))));
        assertEquals(List.of(
                "class test:warrior: its talent tree has no root (a node with no requires)",
                "talent test:leaf: requires talent test:gone that does not exist"),
                ContentValidator.validate(withTalents(Set.of(), talent(LEAF, WARRIOR, List.of(id("gone"))))));
    }

    @Test
    void reportsTalentsThatRequireEachOther() {
        assertEquals(List.of(
                "talent test:a: its requires chain loops back to it, so it can never be bought",
                "talent test:b: its requires chain loops back to it, so it can never be bought"),
                ContentValidator.validate(withTalents(Set.of(), talent(ROOT, WARRIOR, List.of()),
                        talent(id("a"), WARRIOR, List.of(id("b"))), talent(id("b"), WARRIOR, List.of(id("a"))))));
    }

    @Test
    void reportsBoostsOfThingsTheClassDoesNotHave() {
        ResourceLocation slash = id("slash");
        ResourceLocation learned = id("learned");
        ResourceLocation guard = id("guard");
        ContentIndex.TalentRule boosts = new ContentIndex.TalentRule(LEAF, WARRIOR, List.of(ROOT), List.of(slash, learned), List.of(guard), List.of(),
                Set.of(), Set.of(), false, false);
        ContentIndex.TalentRule teaches = new ContentIndex.TalentRule(id("teacher"), WARRIOR, List.of(ROOT), List.of(), List.of(), List.of(learned),
                Set.of(), Set.of(), false, false);
        assertEquals(List.of(
                "talent test:leaf: boosts passive test:guard that the class does not have",
                "talent test:leaf: boosts skill test:slash that the class neither has nor learns from a talent",
                "talent test:leaf: skill test:slash does not exist"),
                ContentValidator.validate(withTalents(Set.of(learned), talent(ROOT, WARRIOR, List.of()), boosts, teaches)));
    }

    @Test
    void reportsTalentsThatGiveNothing() {
        ContentIndex.TalentRule empty = new ContentIndex.TalentRule(LEAF, WARRIOR, List.of(ROOT), List.of(), List.of(), List.of(), Set.of(), Set.of(), true, false);
        ContentIndex.TalentRule idle = new ContentIndex.TalentRule(id("idle"), WARRIOR, List.of(ROOT), List.of(), List.of(), List.of(), Set.of(), Set.of(), false, true);
        assertEquals(List.of(
                "talent test:idle: a skill_boost changes no multiplier, so it gives nothing",
                "talent test:leaf: has no effects, so owning it gives nothing"),
                ContentValidator.validate(withTalents(Set.of(), talent(ROOT, WARRIOR, List.of()), empty, idle)));
    }

    /** Content with warrior and the trial quest but no skills, plus the given references. */
    private static ContentIndex references(Reference... references) {
        return new ContentIndex(Set.of(), Set.of(WARRIOR), Set.of(TRIAL), Set.of(), Set.of(), Map.of(), Set.of(), List.of(references));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
