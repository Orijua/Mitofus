package com.orijua.mitofus.feature.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orijua.mitofus.feature.quest.goal.Goal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A quest from data/<namespace>/mitofus/quest/<id>.json: accept it, meet its goal, claim the rewards.
 * {@code form_reward} gives the player that form (see form.FormService) when claimed.
 * {@code required_class} limits who can accept it; {@code class_reward} changes the player's class and {@code job_reward} their job when claimed.
 * {@code requires_quest} chains quests: this one unlocks once that one has been completed.
 * {@code discover_when} makes it a hidden quest: invisible until the player meets that secret goal
 * (counted over their whole life, like titles), then it shows up and can be accepted.
 * {@code giver} is the NPC (entity type) that gives it: such a quest is only accepted, delivered to and claimed at an NPC
 * of that type, from its quest window; the Quests tab lists it with a hint to go and talk to them.
 * {@code loaned_items} are lent on accepting it and taken back (from the inventory) when it is claimed or abandoned.
 * {@code time_limit_days} gives it a deadline in game days (sleeping counts) from accepting it: such a quest can't be
 * abandoned, warns when one day is left, and when the time runs out it fails for good and can never be taken again.
 * {@code fail_level_penalty} is how many levels failing it costs; never below level 1.
 * {@code grade_quest} makes it a class grade quest: it needs {@code required_class} and raises that class's grade when claimed.
 * {@code accept_skill} is a skill learned on accepting it.
 */
public record QuestDefinition(
        Component name,
        Component description,
        Goal goal,
        Optional<ResourceLocation> requiredClass,
        Optional<ResourceLocation> requiresQuest,
        Optional<Goal> discoverWhen,
        int xpReward,
        List<ItemStack> itemRewards,
        Optional<ResourceLocation> classReward,
        Optional<ResourceLocation> jobReward,
        boolean repeatable,
        Optional<EntityType<?>> giver,
        List<ItemStack> loanedItems,
        Optional<Integer> timeLimitDays,
        Optional<ResourceLocation> acceptSkill,
        int failLevelPenalty,
        Optional<GradeQuest> gradeQuest,
        Optional<ResourceLocation> formReward) {

    /** One Minecraft day, in ticks of day time. */
    public static final long DAY_TICKS = 24_000L;

    public static final Codec<QuestDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ComponentSerialization.CODEC.fieldOf("name").forGetter(QuestDefinition::name),
            ComponentSerialization.CODEC.optionalFieldOf("description", Component.empty()).forGetter(QuestDefinition::description),
            Goal.CODEC.fieldOf("goal").forGetter(QuestDefinition::goal),
            ResourceLocation.CODEC.optionalFieldOf("required_class").forGetter(QuestDefinition::requiredClass),
            ResourceLocation.CODEC.optionalFieldOf("requires_quest").forGetter(QuestDefinition::requiresQuest),
            Goal.CODEC.optionalFieldOf("discover_when").forGetter(QuestDefinition::discoverWhen),
            Codec.intRange(0, 1_000_000).optionalFieldOf("xp_reward", 0).forGetter(QuestDefinition::xpReward),
            ItemStack.CODEC.listOf().optionalFieldOf("item_rewards", List.of()).forGetter(QuestDefinition::itemRewards),
            ResourceLocation.CODEC.optionalFieldOf("class_reward").forGetter(QuestDefinition::classReward),
            ResourceLocation.CODEC.optionalFieldOf("job_reward").forGetter(QuestDefinition::jobReward),
            Codec.BOOL.optionalFieldOf("repeatable", false).forGetter(QuestDefinition::repeatable),
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().optionalFieldOf("giver").forGetter(QuestDefinition::giver),
            ItemStack.CODEC.listOf().optionalFieldOf("loaned_items", List.of()).forGetter(QuestDefinition::loanedItems),
            Codec.intRange(1, 365).optionalFieldOf("time_limit_days").forGetter(QuestDefinition::timeLimitDays),
            Extras.CODEC.forGetter(quest -> new Extras(quest.acceptSkill(), quest.failLevelPenalty(), quest.gradeQuest(), quest.formReward()))
    ).apply(instance, (name, description, goal, requiredClass, requiresQuest, discoverWhen, xpReward, itemRewards, classReward,
                       jobReward, repeatable, giver, loanedItems, timeLimitDays, extras) -> new QuestDefinition(name, description, goal,
            requiredClass, requiresQuest, discoverWhen, xpReward, itemRewards, classReward, jobReward, repeatable, giver, loanedItems,
            timeLimitDays, extras.acceptSkill(), extras.failLevelPenalty(), extras.gradeQuest(), extras.formReward())));

    /** The last fields of the JSON, grouped only because a codec takes at most 16 fields; they stay flat in the file. */
    private record Extras(Optional<ResourceLocation> acceptSkill, int failLevelPenalty, Optional<GradeQuest> gradeQuest,
                          Optional<ResourceLocation> formReward) {
        static final MapCodec<Extras> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("accept_skill").forGetter(Extras::acceptSkill),
                Codec.intRange(0, 100).optionalFieldOf("fail_level_penalty", 0).forGetter(Extras::failLevelPenalty),
                GradeQuest.CODEC.optionalFieldOf("grade_quest").forGetter(Extras::gradeQuest),
                ResourceLocation.CODEC.optionalFieldOf("form_reward").forGetter(Extras::formReward)
        ).apply(instance, Extras::new));
    }

    /** No class required, or the player has it in any of their slots ({@code playerClasses}). */
    public boolean allowsClass(Set<ResourceLocation> playerClasses) {
        return requiredClass.isEmpty() || playerClasses.contains(requiredClass.get());
    }

    /** No previous quest in the chain, or the player has completed it. */
    public boolean isUnlockedBy(PlayerQuests quests) {
        return requiresQuest.map(quests::isCompleted).orElse(true);
    }

    public boolean isGivenBy(EntityType<?> type) {
        return giver.isPresent() && giver.get() == type;
    }

    /** Where to go for it, for the Quests tab: "quest.&lt;namespace&gt;.giver.&lt;npc&gt;" ("Talk to Smith in Covadonga"). */
    public Optional<Component> giverHint() {
        return giver.map(BuiltInRegistries.ENTITY_TYPE::getKey)
                .map(id -> Component.translatable("quest." + id.getNamespace() + ".giver." + id.getPath()));
    }

    public boolean isHidden() {
        return discoverWhen.isPresent();
    }

    /** Visible quests are listed and can be accepted; a hidden one only once {@code quests} has discovered it. */
    public boolean isVisibleTo(ResourceLocation id, PlayerQuests quests) {
        return !isHidden() || quests.isDiscovered(id);
    }
}
