package com.orijua.mitofus.feature.effect;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.progression.ClassSlot;
import com.orijua.mitofus.feature.skill.SkillDefinition;
import com.orijua.mitofus.feature.talent.TalentEffects;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The passives a player has right now: the ones the classes in their slots give. Works on both sides (the class and the
 * passive definitions are synced). The real world has none: Mitofus-only, like every class bonus.
 */
public final class ActiveTraits {
    private record AttributePassive(BonusType type, Holder<Attribute> attribute) {
        ResourceLocation id() {
            return Mitofus.id("passive_" + type.getSerializedName());
        }
    }

    private static final List<AttributePassive> ATTRIBUTES = List.of(
            new AttributePassive(BonusType.ARMOR, Attributes.ARMOR),
            new AttributePassive(BonusType.KNOCKBACK_RESISTANCE, Attributes.KNOCKBACK_RESISTANCE));

    /** The index of the third class slot, where a class's {@code third_slot_passive} works. */
    private static final int THIRD_SLOT_INDEX = 2;

    private ActiveTraits() {
    }

    /** The passives the player has, in the order the class lists them, then the ones a debug command gave. Empty outside Mitofus. */
    public static List<PassiveDefinition> active(Player player) {
        List<PassiveDefinition> result = new ArrayList<>();
        activeIds(player).forEach(id -> Definitions.get(player.level(), ModDatapackRegistries.PASSIVE, id).ifPresent(result::add));
        return result;
    }

    /** The ids of {@link #active}, which is what a talent's passive boost is matched against. */
    private static List<ResourceLocation> activeIds(Player player) {
        if (!MitofusWorld.is(player.level())) {
            return List.of();
        }
        List<ResourceLocation> fromClass = new ArrayList<>();
        for (ClassSlot slot : player.getData(ModAttachments.PLAYER_CLASSES).slots()) {
            Definitions.get(player.level(), ModDatapackRegistries.PLAYER_CLASS, slot.classId()).ifPresent(playerClass -> {
                fromClass.addAll(playerClass.passives());
                // The extra passive only works when the class sits in the third slot.
                if (slot.slot() == THIRD_SLOT_INDEX) {
                    playerClass.thirdSlotPassive().ifPresent(fromClass::add);
                }
            });
        }
        com.orijua.mitofus.feature.form.FormService.equippedPassive(player).ifPresent(fromClass::add);
        return player.getData(ModAttachments.PASSIVE_OVERRIDES).resolve(fromClass);
    }

    /** The bonuses of the learned skills that carry a passive. Empty outside Mitofus, like every class bonus. */
    private static List<Bonus> skillBonuses(Player player) {
        List<Bonus> result = new ArrayList<>();
        if (!MitofusWorld.is(player.level())) {
            return result;
        }
        for (ResourceLocation id : player.getData(ModAttachments.PLAYER_SKILLS).learned()) {
            Definitions.get(player.level(), ModDatapackRegistries.SKILL, id)
                    .flatMap(SkillDefinition::passive)
                    .ifPresent(trait -> result.addAll(trait.bonuses()));
        }
        return result;
    }

    private static List<Bonus> bonuses(Player player) {
        List<Bonus> result = new ArrayList<>();
        active(player).forEach(passive -> result.addAll(passive.trait().bonuses()));
        result.addAll(TalentEffects.passiveBonuses(player, Set.copyOf(activeIds(player))));
        result.addAll(skillBonuses(player));
        return result;
    }

    /** The unconditional total of {@code type}, e.g. the extra maximum mana. */
    public static double total(Player player, BonusType type) {
        return TraitRules.sum(bonuses(player), type, ProgressionService.get(player).level());
    }

    /** Fraction of extra damage the player deals with {@code weapon} in hand right now (0.1 = +10 %). */
    public static double damageBonus(Player player) {
        return com.orijua.mitofus.feature.form.FormService.typeBonus(player) + TraitRules.damage(bonuses(player), ProgressionService.get(player).level(),
                bonus -> bonus.withItem().map(player.getMainHandItem()::is).orElse(true), player.level().isNight());
    }

    /** Idempotent: puts the attribute passives on the player, or takes them off when there are none. */
    public static void apply(Player player) {
        List<Bonus> bonuses = bonuses(player);
        int level = ProgressionService.get(player).level();
        for (AttributePassive passive : ATTRIBUTES) {
            AttributeInstance instance = player.getAttribute(passive.attribute());
            if (instance == null) {
                continue;
            }
            double value = TraitRules.sum(bonuses, passive.type(), level);
            if (value <= 0) {
                instance.removeModifier(passive.id());
            } else {
                instance.addOrReplacePermanentModifier(new AttributeModifier(passive.id(), value, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }
}
