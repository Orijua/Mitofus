package com.orijua.mitofus.core.validation;

import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.core.registry.ModEntities;
import com.orijua.mitofus.core.registry.ModRecipes;
import com.orijua.mitofus.core.validation.ContentIndex.Kind;
import com.orijua.mitofus.core.validation.ContentIndex.Reference;
import com.orijua.mitofus.feature.equipment.option.ItemOptionDefinition;
import com.orijua.mitofus.feature.npc.BlacksmithNpc;
import com.orijua.mitofus.feature.progression.GradeRequirement;
import com.orijua.mitofus.feature.progression.JobDefinition;
import com.orijua.mitofus.feature.progression.PlayerClassDefinition;
import com.orijua.mitofus.feature.fusion.FusionScanner;
import com.orijua.mitofus.feature.talent.GrantSkillTalentEffect;
import com.orijua.mitofus.feature.talent.PassiveBoostTalentEffect;
import com.orijua.mitofus.feature.talent.SkillBoostTalentEffect;
import com.orijua.mitofus.feature.talent.TalentDefinition;
import com.orijua.mitofus.feature.talent.TalentEffect;
import com.orijua.mitofus.feature.progression.TitleDefinition;
import com.orijua.mitofus.feature.quest.QuestDefinition;
import com.orijua.mitofus.feature.quest.goal.DeliverGoal;
import com.orijua.mitofus.feature.smithing.ForgingExtras;
import com.orijua.mitofus.feature.smithing.ForgingRecipe;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads the loaded registries into a {@link ContentIndex}: the Minecraft side of the content validator. */
public final class ContentScanner {
    private ContentScanner() {
    }

    public static ContentIndex index(RegistryAccess access, RecipeManager recipes) {
        Set<ResourceLocation> advancedClasses = new HashSet<>();
        Set<ResourceLocation> rewardedClasses = new HashSet<>();
        Map<ResourceLocation, ResourceLocation> questRequirements = new HashMap<>();
        Set<ResourceLocation> visibleClassQuests = new HashSet<>();
        List<Reference> references = new ArrayList<>();
        Map<ResourceLocation, ResourceLocation> questGivers = new HashMap<>();
        Set<String> deliveriesWithoutGiver = new HashSet<>();
        Set<ResourceLocation> advancedJobs = new HashSet<>();
        Set<ResourceLocation> rewardedJobs = new HashSet<>();
        List<ContentIndex.GradeRule> gradeRules = new ArrayList<>();

        for (Map.Entry<ResourceLocation, PlayerClassDefinition> entry : entries(access, ModDatapackRegistries.PLAYER_CLASS)) {
            String from = "class " + entry.getKey();
            if (!entry.getValue().selectable()) {
                advancedClasses.add(entry.getKey());
            }
            PlayerClassDefinition definition = entry.getValue();
            definition.skills().forEach(skill -> references.add(new Reference(from, Kind.SKILL, skill)));
            definition.passives().forEach(passive -> references.add(new Reference(from, Kind.PASSIVE, passive)));
            definition.thirdSlotPassive().ifPresent(passive -> references.add(new Reference(from, Kind.PASSIVE, passive)));
            for (GradeRequirement requirement : definition.gradeRequirements()) {
                references.add(new Reference(from, Kind.JOB, requirement.job()));
                gradeRules.add(new ContentIndex.GradeRule(from, requirement.grade(), requirement.job(), requirement.jobLevel()));
            }
        }
        List<ContentIndex.TalentRule> talentRules = new ArrayList<>();
        for (Map.Entry<ResourceLocation, TalentDefinition> entry : entries(access, ModDatapackRegistries.TALENT)) {
            TalentDefinition talent = entry.getValue();
            PlayerClassDefinition owner = access.registry(ModDatapackRegistries.PLAYER_CLASS)
                    .map(registry -> registry.get(talent.playerClass())).orElse(null);
            List<ResourceLocation> boostedSkills = new ArrayList<>();
            List<ResourceLocation> boostedPassives = new ArrayList<>();
            List<ResourceLocation> grantedSkills = new ArrayList<>();
            boolean idleBoost = false;
            for (TalentEffect effect : talent.effects()) {
                if (effect instanceof SkillBoostTalentEffect boost) {
                    boostedSkills.add(boost.skill());
                    idleBoost |= !boost.changesAnything();
                } else if (effect instanceof PassiveBoostTalentEffect boost) {
                    boostedPassives.add(boost.passive());
                } else if (effect instanceof GrantSkillTalentEffect grant) {
                    grantedSkills.add(grant.skill());
                }
            }
            Set<ResourceLocation> classSkills = owner == null ? Set.of() : Set.copyOf(owner.skills());
            Set<ResourceLocation> classPassives = new HashSet<>();
            if (owner != null) {
                classPassives.addAll(owner.passives());
                owner.thirdSlotPassive().ifPresent(classPassives::add);
            }
            talentRules.add(new ContentIndex.TalentRule(entry.getKey(), talent.playerClass(), talent.requires(), boostedSkills,
                    boostedPassives, grantedSkills, classSkills, Set.copyOf(classPassives), talent.effects().isEmpty() && !talent.isRoot(), idleBoost));
        }
        for (Map.Entry<ResourceLocation, JobDefinition> entry : entries(access, ModDatapackRegistries.JOB)) {
            String from = "job " + entry.getKey();
            if (!entry.getValue().selectable()) {
                advancedJobs.add(entry.getKey());
            }
            entry.getValue().skills().forEach(skill -> references.add(new Reference(from, Kind.SKILL, skill)));
        }
        for (Map.Entry<ResourceLocation, QuestDefinition> entry : entries(access, ModDatapackRegistries.QUEST)) {
            String from = "quest " + entry.getKey();
            QuestDefinition quest = entry.getValue();
            quest.requiredClass().ifPresent(id -> references.add(new Reference(from, Kind.CLASS, id)));
            quest.gradeQuest().flatMap(grade -> grade.job()).ifPresent(id -> references.add(new Reference(from, Kind.JOB, id)));
            quest.requiresQuest().ifPresent(id -> {
                references.add(new Reference(from, Kind.QUEST, id));
                questRequirements.put(entry.getKey(), id);
            });
            quest.classReward().ifPresent(id -> {
                references.add(new Reference(from, Kind.CLASS, id));
                rewardedClasses.add(id);
                if (!quest.isHidden()) {
                    visibleClassQuests.add(entry.getKey());
                }
            });
            quest.jobReward().ifPresent(id -> {
                references.add(new Reference(from, Kind.JOB, id));
                rewardedJobs.add(id);
            });
            for (ItemStack reward : quest.itemRewards()) {
                ResourceLocation skill = reward.get(ModDataComponents.SKILL_BOOK);
                if (skill != null) {
                    references.add(new Reference(from, Kind.SKILL, skill));
                }
            }
            quest.acceptSkill().ifPresent(skill -> references.add(new Reference(from, Kind.SKILL, skill)));
            quest.giver().ifPresent(giver -> questGivers.put(entry.getKey(), BuiltInRegistries.ENTITY_TYPE.getKey(giver)));
            if (quest.goal() instanceof DeliverGoal && quest.giver().isEmpty()) {
                deliveriesWithoutGiver.add(from);
            }
            if (quest.discoverWhen().filter(DeliverGoal.class::isInstance).isPresent()) {
                deliveriesWithoutGiver.add(from + " (discover_when)");
            }
        }
        for (Map.Entry<ResourceLocation, TitleDefinition> entry : entries(access, ModDatapackRegistries.TITLE)) {
            if (entry.getValue().goal() instanceof DeliverGoal) {
                deliveriesWithoutGiver.add("title " + entry.getKey());
            }
        }
        for (Map.Entry<ResourceLocation, ItemOptionDefinition> entry : entries(access, ModDatapackRegistries.ITEM_OPTION)) {
            String from = "item option " + entry.getKey();
            entry.getValue().effect().grantedSkill().ifPresent(skill -> references.add(new Reference(from, Kind.SKILL, skill)));
        }
        for (RecipeHolder<ForgingRecipe> holder : recipes.getAllRecipesFor(ModRecipes.FORGING.get())) {
            String from = "forging recipe " + holder.id();
            ForgingExtras extras = holder.value().extras();
            extras.requiredJob().ifPresent(id -> references.add(new Reference(from, Kind.JOB, id)));
            extras.fixedOptions().forEach(option -> references.add(new Reference(from, Kind.OPTION, option.option())));
        }
        BlacksmithNpc.SOLD_SKILLS.forEach(skill -> references.add(new Reference("blacksmith trades", Kind.SKILL, skill)));
        Set<ResourceLocation> questGiverNpcs = new HashSet<>();
        ModEntities.questGivers().forEach(type -> questGiverNpcs.add(BuiltInRegistries.ENTITY_TYPE.getKey(type)));

        rewardedClasses.addAll(FusionScanner.resultClasses(access));
        return new ContentIndex(ids(access, ModDatapackRegistries.SKILL), ids(access, ModDatapackRegistries.PLAYER_CLASS),
                ids(access, ModDatapackRegistries.QUEST), advancedClasses, rewardedClasses, questRequirements, visibleClassQuests, references,
                questGivers, questGiverNpcs, deliveriesWithoutGiver, ids(access, ModDatapackRegistries.ITEM_OPTION),
                ids(access, ModDatapackRegistries.PASSIVE), ids(access, ModDatapackRegistries.JOB), advancedJobs, rewardedJobs, gradeRules,
                ids(access, ModDatapackRegistries.TALENT), talentRules);
    }

    private static <T> Set<ResourceLocation> ids(RegistryAccess access, ResourceKey<Registry<T>> key) {
        return access.registry(key).map(registry -> Set.copyOf(registry.keySet())).orElse(Set.of());
    }

    private static <T> List<Map.Entry<ResourceLocation, T>> entries(RegistryAccess access, ResourceKey<Registry<T>> key) {
        return access.registry(key)
                .map(registry -> registry.keySet().stream().map(id -> Map.entry(id, registry.get(id))).toList())
                .orElse(List.of());
    }
}
