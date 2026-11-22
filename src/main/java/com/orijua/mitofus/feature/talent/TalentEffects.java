package com.orijua.mitofus.feature.talent;

import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.effect.Bonus;
import com.orijua.mitofus.feature.progression.ClassSlot;
import com.orijua.mitofus.feature.progression.StatModifier;
import com.orijua.mitofus.feature.progression.StatSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What the talent nodes a player owns do. Works on both sides (the nodes and the slots are synced). */
public final class TalentEffects {
    private TalentEffects() {
    }

    /** How much a player's talents change one skill: each field is applied on top of the skill's own value. */
    public record SkillTuning(double damage, double manaCost, double cooldown) {
        public static final SkillTuning NONE = new SkillTuning(1.0, 1.0, 1.0);

        /** The product of every boost of {@code skill} in {@code effects}, so two boosts of 10% each give 21%, never more than they say. */
        public static SkillTuning of(List<? extends TalentEffect> effects, ResourceLocation skill) {
            double damage = 1.0;
            double mana = 1.0;
            double cooldown = 1.0;
            for (TalentEffect effect : effects) {
                if (effect instanceof SkillBoostTalentEffect boost && boost.skill().equals(skill)) {
                    damage *= boost.damageMultiplier();
                    mana *= boost.manaCostMultiplier();
                    cooldown *= boost.cooldownMultiplier();
                }
            }
            return new SkillTuning(damage, mana, cooldown);
        }

        public float mana(float base) {
            return (float) (base * manaCost);
        }

        public int cooldownTicks(int base) {
            return (int) Math.round(base * cooldown);
        }
    }

    /** Every node the player owns across their classes, roots included, in slot order. */
    public static List<TalentDefinition> owned(Player player) {
        List<TalentDefinition> result = new ArrayList<>();
        Map<ResourceLocation, TalentDefinition> all = TalentService.all(player.level());
        for (ClassSlot slot : player.getData(ModAttachments.PLAYER_CLASSES).slots()) {
            TalentRules.owned(all, slot).stream().sorted().forEach(id -> result.add(all.get(id)));
        }
        return result;
    }

    private static List<TalentEffect> effects(Player player) {
        List<TalentEffect> result = new ArrayList<>();
        owned(player).forEach(node -> result.addAll(node.effects()));
        return result;
    }

    /** The stat points the owned nodes give, each tagged with its node for the stat tooltip. */
    public static List<StatModifier> statModifiers(Player player) {
        List<StatModifier> result = new ArrayList<>();
        for (TalentDefinition node : owned(player)) {
            for (TalentEffect effect : node.effects()) {
                if (effect instanceof StatTalentEffect stat) {
                    result.add(StatModifier.add(new StatSource(node.name()), stat.stat(), stat.amount()));
                }
            }
        }
        return result;
    }

    /** The extra bonuses for passives the player has right now ({@code activePassives}); a boost of a passive they do not have gives nothing. */
    public static List<Bonus> passiveBonuses(Player player, Set<ResourceLocation> activePassives) {
        List<Bonus> result = new ArrayList<>();
        for (TalentEffect effect : effects(player)) {
            if (effect instanceof PassiveBoostTalentEffect boost && activePassives.contains(boost.passive())) {
                result.addAll(boost.bonuses());
            }
        }
        return result;
    }

    public static SkillTuning tuning(Player player, ResourceLocation skill) {
        return SkillTuning.of(effects(player), skill);
    }

    /** The skills the owned nodes of {@code slot} teach. */
    public static List<ResourceLocation> grantedSkills(Map<ResourceLocation, TalentDefinition> all, ClassSlot slot) {
        List<ResourceLocation> result = new ArrayList<>();
        for (ResourceLocation id : TalentRules.owned(all, slot)) {
            for (TalentEffect effect : all.get(id).effects()) {
                if (effect instanceof GrantSkillTalentEffect grant) {
                    result.add(grant.skill());
                }
            }
        }
        return result;
    }
}
