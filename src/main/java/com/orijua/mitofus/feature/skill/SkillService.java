package com.orijua.mitofus.feature.skill;

import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.effect.ActiveTraits;
import com.orijua.mitofus.feature.effect.EffectContext;
import com.orijua.mitofus.feature.effect.EffectEngine;
import com.orijua.mitofus.feature.effect.EffectSource;
import com.orijua.mitofus.feature.effect.EffectTargets;
import com.orijua.mitofus.feature.equipment.AppraisalService;
import com.orijua.mitofus.feature.equipment.EquipmentEvents;
import com.orijua.mitofus.feature.fatigue.FatigueRestraint;
import com.orijua.mitofus.feature.equipment.option.RolledOption;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.Optional;

/** Server-side skill operations: learning, slotting and casting. Every rule is checked here. */
public final class SkillService {
    /** The fourth skill key: casts the held weapon's own skill instead of a learned one. */
    public static final int WEAPON_SLOT = SlotRules.MAX_SLOTS;

    public enum Result { SUCCESS, EMPTY_SLOT, UNKNOWN_SKILL, WRONG_ITEM, REQUIREMENTS_UNMET, ON_COOLDOWN, NO_MANA, REAL_WORLD, RESTRAINED, CONDITION_UNMET }

    /** How far a skill that needs a target looks for it. */
    public static final double TARGET_RANGE = 16.0;

    private SkillService() {
    }

    public static PlayerSkills get(ServerPlayer player) {
        return player.getData(ModAttachments.PLAYER_SKILLS);
    }

    public static Optional<SkillDefinition> definition(ServerPlayer player, ResourceLocation id) {
        return Definitions.get(player.level(), ModDatapackRegistries.SKILL, id);
    }

    /** @return false if the skill doesn't exist or is already known. */
    public static boolean learn(ServerPlayer player, ResourceLocation skill) {
        PlayerSkills skills = get(player);
        if (skills.knows(skill) || definition(player, skill).isEmpty()) {
            return false;
        }
        player.setData(ModAttachments.PLAYER_SKILLS, skills.learn(skill, ProgressionService.get(player).level()));
        ActiveTraits.apply(player); // a skill's passive counts from the moment it is learned
        return true;
    }

    public static void assign(ServerPlayer player, int slot, Optional<ResourceLocation> skill) {
        PlayerSkills skills = get(player);
        if (!SlotRules.isOpen(ProgressionService.get(player).level(), slot) || skill.filter(s -> !skills.knows(s)).isPresent()) {
            return;
        }
        player.setData(ModAttachments.PLAYER_SKILLS, skills.withSlot(slot, skill));
    }

    /** Casts the skill in slot {@code slot} (0-44, page after page), or the held weapon's skill for {@link #WEAPON_SLOT}. */
    public static Result use(ServerPlayer player, int slot) {
        if (!MitofusWorld.is(player.level())) {
            return Result.REAL_WORLD;
        }
        if (slot != WEAPON_SLOT && !SlotRules.isOpen(ProgressionService.get(player).level(), slot)) {
            return Result.EMPTY_SLOT;
        }
        Optional<ResourceLocation> id = slot == WEAPON_SLOT ? weaponSkill(player.getMainHandItem()) : get(player).slot(slot);
        if (id.isEmpty()) {
            return Result.EMPTY_SLOT;
        }
        return cast(player, id.get(), slot == WEAPON_SLOT);
    }

    /**
     * The skill a weapon lets its holder cast (a skill option, e.g. Bisection on a Legendary Guadalete), if any.
     * Works on both sides, so the HUD can show it.
     */
    public static Optional<ResourceLocation> weaponSkill(ItemStack weapon) {
        for (RolledOption rolled : AppraisalService.activeOptions(weapon)) {
            Optional<ResourceLocation> skill = Definitions.get(ModDatapackRegistries.ITEM_OPTION, rolled.option())
                    .flatMap(def -> def.effect().grantedSkill());
            if (skill.isPresent()) {
                return skill;
            }
        }
        return Optional.empty();
    }

    /**
     * Checks the item, requirements, cooldown and mana, then casts and wears the held weapon (see {@link WeaponWearRules}).
     * The skill need not be learned (weapon skills).
     */
    private static Result cast(ServerPlayer player, ResourceLocation id, boolean fromWeaponSlot) {
        PlayerSkills skills = get(player);
        Optional<SkillDefinition> found = definition(player, id);
        if (found.isEmpty()) {
            return Result.UNKNOWN_SKILL;
        }
        SkillDefinition skill = found.get();
        long now = player.level().getGameTime();

        if (skill.dealsDamage() && FatigueRestraint.isRestrained(player)) {
            return fail(player, Result.RESTRAINED, Component.translatable("message.mitofus.fatigue.skill_refused"));
        }
        if (!skill.canUseWith(player.getMainHandItem())) {
            return fail(player, Result.WRONG_ITEM, Component.translatable("message.mitofus.wrong_item", skill.name()));
        }
        if (!EquipmentEvents.meetsRequirements(player, player.getMainHandItem())) {
            return fail(player, Result.REQUIREMENTS_UNMET, Component.translatable("message.mitofus.requirements_unmet"));
        }
        long remaining = skills.cooldownRemaining(id, now);
        if (remaining > 0) {
            return fail(player, Result.ON_COOLDOWN, Component.translatable("message.mitofus.cooldown",
                    skill.name(), String.format("%.1f", remaining / 20.0)));
        }
        float mana = player.getData(ModAttachments.MANA);
        if (mana < skill.manaCost()) {
            return fail(player, Result.NO_MANA, Component.translatable("message.mitofus.no_mana", skill.name()));
        }

        EffectContext context = EffectContext.of(player, EffectSource.skill(id, skill.name()), ProgressionService.effectiveStats(player));
        if (skill.needsTarget()) {
            Optional<LivingEntity> target = EffectTargets.lookTarget(player, TARGET_RANGE);
            if (target.isPresent()) {
                context = context.withTarget(target.get());
            }
        }
        // Conditions are checked before anything is spent: a refused cast costs nothing.
        if (!EffectEngine.canApply(skill.conditions(), context)) {
            return fail(player, Result.CONDITION_UNMET, Component.translatable("message.mitofus.condition_unmet", skill.name()));
        }
        // Item skills (Bisection) and skills not learned don't grow.
        boolean grows = !fromWeaponSlot && skills.knows(id);
        SkillMastery mastery = player.getData(ModAttachments.SKILL_MASTERY);
        int level = grows ? mastery.level(id) : 1;
        EffectEngine.Outcome outcome = EffectEngine.run(skill.effects(), skill.conditions(), context.withSkillLevel(level));
        if (grows && outcome == EffectEngine.Outcome.ACTED) {
            recordUse(player, id, skill, mastery, level);
        }
        int wear = WeaponWearRules.wear(skill.requiredItem().isPresent(), fromWeaponSlot, skill.weaponWear(),
                player.hasInfiniteMaterials());
        if (wear > 0) {
            player.getMainHandItem().hurtAndBreak(wear, player, EquipmentSlot.MAINHAND);
        }
        player.setData(ModAttachments.MANA, mana - skill.manaCost());
        player.setData(ModAttachments.PLAYER_SKILLS, skills.withCooldown(id, now + skill.cooldownTicks()));
        player.displayClientMessage(skill.name(), true);
        NeoForge.EVENT_BUS.post(new SkillUsedEvent(player, id));
        return Result.SUCCESS;
    }

    /** One successful use more; tells the player when the skill reaches a new level. */
    private static void recordUse(ServerPlayer player, ResourceLocation id, SkillDefinition skill, SkillMastery before, int levelBefore) {
        SkillMastery after = before.withUse(id);
        player.setData(ModAttachments.SKILL_MASTERY, after);
        int levelAfter = after.level(id);
        if (levelAfter > levelBefore) {
            player.sendSystemMessage(Component.translatable("message.mitofus.skill_level_up", skill.name(),
                    MasteryText.rank(levelAfter), MasteryRules.levelInRank(levelAfter)));
        }
    }

    private static Result fail(ServerPlayer player, Result result, Component message) {
        player.displayClientMessage(message, true);
        return result;
    }
}
