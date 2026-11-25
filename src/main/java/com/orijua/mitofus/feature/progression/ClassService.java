package com.orijua.mitofus.feature.progression;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.economy.Money;
import com.orijua.mitofus.feature.economy.Wallet;
import com.orijua.mitofus.feature.skill.SkillService;
import com.orijua.mitofus.feature.talent.TalentEffects;
import com.orijua.mitofus.feature.talent.TalentService;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * How a player gets classes and a job. Classes go in slots that open with the player's level
 * ({@link ClassSlotRules}): chosen from the Profile tab, or granted by a quest to the first free slot. Either way the
 * class skills are learned and bonuses applied. The only place that writes {@link PlayerClasses}.
 */
public final class ClassService {
    /** The blacksmith job: its level for grades is the player's smithing level. */
    public static final ResourceLocation BLACKSMITH = Mitofus.id("blacksmith");

    private ClassService() {
    }

    public static PlayerClasses classes(ServerPlayer player) {
        return player.getData(ModAttachments.PLAYER_CLASSES);
    }

    public static Optional<ResourceLocation> currentJob(ServerPlayer player) {
        return classes(player).job();
    }

    /**
     * Places a basic class in {@code slot}. @return false in the real world, if the class doesn't exist or isn't a selectable
     * basic class, or if the slot is closed, taken (a choice is never changed), out of range, or the class is already in another slot.
     */
    public static boolean chooseClass(ServerPlayer player, int slot, ResourceLocation classId) {
        if (!MitofusWorld.is(player.level())) {
            return false;
        }
        Optional<PlayerClassDefinition> definition = Definitions.get(player.level(), ModDatapackRegistries.PLAYER_CLASS, classId);
        if (definition.isEmpty() || !definition.get().selectable()) {
            return false;
        }
        ClassSlotRules.Choice choice = ClassSlotRules.check(classes(player), slot, classId, ProgressionService.get(player).level());
        if (choice != ClassSlotRules.Choice.OK) {
            player.displayClientMessage(Component.translatable("message.mitofus.class_" + switch (choice) {
                case LOCKED -> "slot_locked";
                case OCCUPIED -> "slot_taken";
                case DUPLICATE -> "duplicate";
                default -> "bad_slot";
            }), false);
            return false;
        }
        place(player, slot, classId, definition.get(), false);
        return true;
    }

    /**
     * Changes the branch of {@code slot} to {@code branch} (at the village chief). The branch that leaves sleeps with all its progress and a
     * dormant one comes back as it was. The first change of each slot is free; after that it costs the new branch's {@code change_cost} in gold.
     * @return false if the rules refuse it or the purse cannot pay it (the player is told why); nothing changes then.
     */
    public static boolean changeBranch(ServerPlayer player, int slot, ResourceLocation branch) {
        if (!MitofusWorld.is(player.level())) {
            return false;
        }
        Optional<PlayerClassDefinition> definition = Definitions.get(player.level(), ModDatapackRegistries.PLAYER_CLASS, branch);
        if (definition.isEmpty() || !definition.get().selectable()) {
            return false;
        }
        PlayerClasses before = classes(player);
        ClassSlotRules.Swap check = ClassSlotRules.checkSwap(before, slot, branch, ProgressionService.get(player).level());
        if (check != ClassSlotRules.Swap.OK) {
            player.displayClientMessage(Component.translatable("message.mitofus.branch_" + check.name().toLowerCase()), false);
            return false;
        }
        int gold = ClassSlotRules.swapCost(before, slot, definition.get().changeCost());
        long cost = gold == 0 ? 0L : Money.iron(gold); // Money.iron never gives less than 1 iron
        if (cost > 0 && !Wallet.pay(player, cost)) {
            player.displayClientMessage(Component.translatable("message.mitofus.branch_poor"), false);
            return false;
        }
        ClassSlot leaving = before.slot(slot).orElseThrow();
        PlayerClasses after = ClassSlotRules.swap(before, slot, branch);
        player.setData(ModAttachments.PLAYER_CLASSES, after);
        syncBranchSkills(player, leaving, after);
        ProgressionService.refreshEffects(player);
        player.displayClientMessage(Component.translatable("message.mitofus.branch_changed", definition.get().name()), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 0.8f);
        NeoForge.EVENT_BUS.post(new ClassChangedEvent(player, slot, branch, false));
        return true;
    }

    /** The skills a placed branch gives: its class skills plus those its bought talent nodes teach. */
    private static java.util.Set<ResourceLocation> branchSkills(ServerPlayer player, ClassSlot placed) {
        java.util.Set<ResourceLocation> skills = new java.util.HashSet<>();
        Definitions.get(player.level(), ModDatapackRegistries.PLAYER_CLASS, placed.classId()).ifPresent(d -> skills.addAll(d.skills()));
        skills.addAll(TalentEffects.grantedSkills(TalentService.all(player.level()), placed));
        return skills;
    }

    /** After a swap: forgets what only the sleeping branch taught and learns what the held branches teach. */
    private static void syncBranchSkills(ServerPlayer player, ClassSlot leaving, PlayerClasses after) {
        java.util.Set<ResourceLocation> keep = new java.util.HashSet<>();
        after.slots().forEach(held -> keep.addAll(branchSkills(player, held)));
        branchSkills(player, leaving).stream().filter(skill -> !keep.contains(skill)).forEach(skill -> SkillService.forget(player, skill));
        keep.forEach(skill -> SkillService.learn(player, skill));
    }

    /**
     * A quest's class_reward: the class goes to the first open and free slot, whatever its kind. @return false (and the player is told
     * why) if there is no such slot, the class is already held or it doesn't exist; the quest itself is not blocked by this.
     */
    public static boolean grantClass(ServerPlayer player, ResourceLocation classId) {
        if (!MitofusWorld.is(player.level())) {
            return false;
        }
        Optional<PlayerClassDefinition> definition = Definitions.get(player.level(), ModDatapackRegistries.PLAYER_CLASS, classId);
        PlayerClasses classes = classes(player);
        if (definition.isEmpty() || classes.hasClass(classId)) {
            return false;
        }
        int slot = ClassSlotRules.firstFreeSlot(classes, ProgressionService.get(player).level());
        if (slot < 0) {
            player.displayClientMessage(Component.translatable("message.mitofus.class_reward_no_slot", definition.get().name()), false);
            return false;
        }
        place(player, slot, classId, definition.get(), true);
        return true;
    }

    /** Debug: puts any class in any slot (0 to 2) regardless of level, replacing what was there; the class cannot be in another slot already. */
    public static boolean forceClass(ServerPlayer player, int slot, ResourceLocation classId) {
        if (!MitofusWorld.is(player.level()) || slot < 0 || slot >= ClassSlotRules.MAX_SLOTS) {
            return false;
        }
        Optional<PlayerClassDefinition> definition = Definitions.get(player.level(), ModDatapackRegistries.PLAYER_CLASS, classId);
        if (definition.isEmpty() || classes(player).slots().stream().anyMatch(s -> s.slot() != slot && s.classId().equals(classId))) {
            return false;
        }
        place(player, slot, classId, definition.get(), true);
        return true;
    }

    /** Debug: sets the level of the class in {@code slot} (XP back to 0). @return false if the slot is empty or the level is below 1. */
    public static boolean setClassLevel(ServerPlayer player, int slot, int level) {
        Optional<ClassSlot> placed = classes(player).slot(slot);
        if (placed.isEmpty() || level < 1) {
            return false;
        }
        player.setData(ModAttachments.PLAYER_CLASSES, classes(player).withSlot(placed.get().withProgress(level, 0)));
        return true;
    }

    /** Debug: the slot (0 to 2) that holds {@code classId}, or empty. Slots are numbered from 0 here but shown from 1 in the Profile. */
    public static Optional<Integer> slotOf(ServerPlayer player, ResourceLocation classId) {
        return classes(player).slots().stream().filter(s -> s.classId().equals(classId)).map(ClassSlot::slot).findFirst();
    }

    /** Like {@link #chooseClass} for the job: once, only selectable ones, only in Mitofus. */
    public static boolean chooseJob(ServerPlayer player, ResourceLocation jobId) {
        if (!MitofusWorld.is(player.level())) {
            return false;
        }
        Optional<JobDefinition> definition = Definitions.get(player.level(), ModDatapackRegistries.JOB, jobId);
        if (!ClassSlotRules.canChooseJob(classes(player)) || definition.isEmpty() || !definition.get().selectable()) {
            return false;
        }
        applyJob(player, jobId, definition.get());
        return true;
    }

    /** Sets the job regardless of how it is obtained (a quest's job_reward), replacing the current one. Skills already learned are kept. */
    public static boolean grantJob(ServerPlayer player, ResourceLocation jobId) {
        if (!MitofusWorld.is(player.level())) {
            return false;
        }
        Optional<JobDefinition> definition = Definitions.get(player.level(), ModDatapackRegistries.JOB, jobId);
        if (definition.isEmpty()) {
            return false;
        }
        applyJob(player, jobId, definition.get());
        return true;
    }

    /**
     * The player's job levels for the grade requirements: the blacksmith's is their smithing level (as for any job that adds to it,
     * like Sancho's successor); the other jobs have no level yet (0).
     */
    public static Map<ResourceLocation, Integer> jobLevels(Player player) {
        Map<ResourceLocation, Integer> levels = new HashMap<>();
        player.getData(ModAttachments.PLAYER_CLASSES).job().ifPresent(jobId -> {
            boolean smith = jobId.equals(BLACKSMITH) || Definitions.get(player.level(), ModDatapackRegistries.JOB, jobId)
                    .map(job -> job.smithingBonus() > 0).orElse(false);
            int level = smith ? ProgressionService.get(player).smithingLevel() : 0;
            levels.put(jobId, level);
            if (smith) {
                levels.put(BLACKSMITH, level);
            }
        });
        return levels;
    }

    /** Raises the grade of the class in {@code slot} by one if the rules allow it ({@link ClassSlotRules#advance}). */
    public static ClassSlotRules.Advance advanceGrade(ServerPlayer player, int slot) {
        Optional<ClassSlot> placed = classes(player).slot(slot);
        if (placed.isEmpty() || !MitofusWorld.is(player.level())) {
            return ClassSlotRules.Advance.NO_CLASS;
        }
        Optional<PlayerClassDefinition> definition = Definitions.get(player.level(), ModDatapackRegistries.PLAYER_CLASS, placed.get().classId());
        List<GradeRequirement> requirements = definition.map(PlayerClassDefinition::gradeRequirements).orElse(List.of());
        ClassSlotRules.Advance result = ClassSlotRules.advance(placed.get(), requirements, jobLevels(player));
        if (result == ClassSlotRules.Advance.OK) {
            ClassGrade next = placed.get().grade().next().orElseThrow();
            player.setData(ModAttachments.PLAYER_CLASSES, classes(player).withSlot(placed.get().withGrade(next)));
            player.displayClientMessage(Component.translatable("message.mitofus.class_grade_up",
                    definition.map(PlayerClassDefinition::name).orElse(Component.literal(placed.get().classId().toString())), next.displayName()), false);
            ProgressionService.refreshEffects(player);
        }
        return result;
    }

    /** Gives the level XP the player just earned to every class in a slot (they level on the same curve as the player). */
    public static void awardClassXp(ServerPlayer player, int amount) {
        PlayerClasses before = classes(player);
        if (before.slots().isEmpty() || amount <= 0) {
            return;
        }
        PlayerClasses after = before;
        int playerLevel = ProgressionService.get(player).level();
        for (ClassSlot placed : before.slots()) {
            PlayerClassDefinition.CatchUp catchUp = Definitions.get(player.level(), ModDatapackRegistries.PLAYER_CLASS, placed.classId())
                    .map(PlayerClassDefinition::catchUp).orElse(PlayerClassDefinition.CatchUp.DEFAULT);
            int boosted = (int) Math.round(amount * ClassSlotRules.catchUpMultiplier(placed.classLevel(), playerLevel, catchUp));
            after = after.withSlot(ClassSlotRules.addClassXp(Balance.current(), placed, boosted));
        }
        player.setData(ModAttachments.PLAYER_CLASSES, after);
    }

    private static void applyJob(ServerPlayer player, ResourceLocation jobId, JobDefinition definition) {
        player.setData(ModAttachments.PLAYER_CLASSES, classes(player).withJob(jobId));
        definition.skills().forEach(skill -> SkillService.learn(player, skill));
        ProgressionService.refreshEffects(player);
        player.displayClientMessage(Component.translatable("message.mitofus.job_granted", definition.name()), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    /** @param granted true when a quest (or a command) gives the class, false when the player picks it */
    private static void place(ServerPlayer player, int slot, ResourceLocation classId, PlayerClassDefinition definition, boolean granted) {
        String messageKey = granted ? "message.mitofus.class_granted" : "message.mitofus.class_chosen";
        PlayerClasses before = classes(player);
        ClassSlot entering = before.dormant(classId).map(sleeping -> sleeping.inSlot(slot)).orElseGet(() -> ClassSlot.fresh(slot, classId));
        player.setData(ModAttachments.PLAYER_CLASSES, before.withoutDormant(classId).withSlot(entering));
        definition.skills().forEach(skill -> SkillService.learn(player, skill));
        ProgressionService.refreshEffects(player);

        player.displayClientMessage(Component.translatable(messageKey, definition.name()), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 0.8f);
        NeoForge.EVENT_BUS.post(new ClassChangedEvent(player, slot, classId, granted));
    }
}
