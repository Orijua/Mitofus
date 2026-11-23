package com.orijua.mitofus.feature.progression;

import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.config.VrHelmetBalance;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.feature.effect.BonusType;
import com.orijua.mitofus.feature.effect.ActiveTraits;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.fusion.SynergyService;
import com.orijua.mitofus.feature.talent.TalentEffects;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.equipment.AppraisalService;
import com.orijua.mitofus.feature.equipment.EquipLevels;
import com.orijua.mitofus.feature.equipment.EquipRequirements;
import com.orijua.mitofus.feature.fatigue.FatigueService;
import com.orijua.mitofus.feature.equipment.EquipmentBonuses;
import com.orijua.mitofus.feature.equipment.option.RolledOption;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Progression of a player: the only place that writes PlayerStats, and the one place that
 * gathers the stat sources (allocated points, class, titles) into the effective stats.
 */
public final class ProgressionService {
    private ProgressionService() {
    }

    /** Allocated stats only, as shown on the "+" rows. */
    public static PlayerStats get(Player player) {
        return player.getData(ModAttachments.PLAYER_STATS);
    }

    /** Allocated stats plus every other source in {@link #modifiers}. Use this for every gameplay check; works on both sides. */
    public static PlayerStats effectiveStats(Player player) {
        return ProgressionRules.effective(get(player), modifiers(player));
    }

    public static int effectiveSmithingLevel(Player player) {
        return get(player).smithingLevel() + job(player).map(JobDefinition::smithingBonus).orElse(0);
    }

    public static Optional<JobDefinition> job(Player player) {
        return player.getData(ModAttachments.PLAYER_CLASSES).job()
                .flatMap(id -> Definitions.get(player.level(), ModDatapackRegistries.JOB, id));
    }

    /** The definitions of the classes the player has placed in their slots, in slot order. */
    public static List<PlayerClassDefinition> classes(Player player) {
        List<PlayerClassDefinition> result = new ArrayList<>();
        for (ClassSlot slot : player.getData(ModAttachments.PLAYER_CLASSES).slots()) {
            Definitions.get(player.level(), ModDatapackRegistries.PLAYER_CLASS, slot.classId()).ifPresent(result::add);
        }
        return result;
    }

    /**
     * Every contribution to the base stats, each tagged with where it comes from (the stat tooltip lists them).
     * A new source (equipment, talents, sets, buffs, guild...) only needs to add its modifiers here.
     */
    public static List<StatModifier> modifiers(Player player) {
        return withActiveEquipment(player, baseModifiers(player));
    }

    /** Stats without what the worn equipment gives (allocation, class, titles). */
    public static PlayerStats statsWithoutEquipment(Player player) {
        return ProgressionRules.effective(get(player), baseModifiers(player));
    }

    /**
     * The stats item requirements are checked against: allocation, class and titles plus what the other worn
     * equipment gives (the user's decision of 2026-11-17: gear stats count as the character's own). An item can
     * never meet its own requirements with the stats it gives, see {@link #withActiveEquipment}.
     */
    public static PlayerStats statsForRequirements(Player player) {
        return effectiveStats(player);
    }

    private static List<StatModifier> baseModifiers(Player player) {
        List<StatModifier> modifiers = new ArrayList<>(StatModifier.base());
        modifiers.addAll(StatModifier.allocated(get(player)));
        // The bonuses of every class in a slot and of the job add up.
        classes(player).forEach(c -> modifiers.addAll(StatModifier.fromBonuses(new StatSource(c.name()), c.bonusStats())));
        job(player).ifPresent(j -> modifiers.addAll(StatModifier.fromBonuses(new StatSource(j.name()), j.bonusStats())));
        modifiers.addAll(SynergyService.modifiers(player));
        modifiers.addAll(TalentEffects.statModifiers(player));
        for (var titleId : player.getData(ModAttachments.PLAYER_IDENTITY).titles()) {
            Definitions.get(player.level(), ModDatapackRegistries.TITLE, titleId)
                    .ifPresent(t -> modifiers.addAll(StatModifier.fromBonuses(new StatSource(t.name()), t.bonusStats())));
        }
        return modifiers;
    }

    /**
     * The base modifiers plus those of the worn items whose requirements are met, and so whose stat options count. It grows from the base stats
     * one round at a time: an item joins when the base stats plus what the already active items give meet its
     * requirements, so no item (or pair of items) can lift itself over its own requirements.
     */
    private static List<StatModifier> withActiveEquipment(Player player, List<StatModifier> base) {
        List<StatModifier> modifiers = new ArrayList<>(base);
        if (!MitofusWorld.is(player.level())) {
            return modifiers;
        }
        List<ItemStack> pending = new ArrayList<>(EquipmentBonuses.wornEquipment(player));
        boolean changed = true;
        while (changed) {
            changed = false;
            PlayerStats stats = ProgressionRules.effective(get(player), modifiers);
            for (var it = pending.iterator(); it.hasNext(); ) {
                ItemStack stack = it.next();
                EquipRequirements requirements = EquipLevels.requirements(stack).orElse(null);
                if (requirements == null || requirements.metBy(stats)) {
                    it.remove();
                    changed = true;
                    StatSource source = new StatSource(stack.getHoverName());
                    for (RolledOption rolled : AppraisalService.activeOptions(stack)) {
                        Definitions.get(player.level(), ModDatapackRegistries.ITEM_OPTION, rolled.option())
                                .ifPresent(def -> def.effect().statModifiers(source, rolled.value(), modifiers::add));
                    }
                }
            }
        }
        return modifiers;
    }

    public static void set(ServerPlayer player, PlayerStats stats) {
        player.setData(ModAttachments.PLAYER_STATS, stats);
        refreshEffects(player);
    }

    /** Re-applies attribute modifiers; call after anything that changes effective stats (requirements may change too). */
    public static void refreshEffects(Player player) {
        if (!MitofusWorld.is(player.level())) {
            // The real world has no Mitofus bonuses; they come back when the player returns.
            StatEffects.clear(player);
            ActiveTraits.apply(player);
            EquipmentBonuses.apply(player);
            return;
        }
        StatEffects.apply(player, effectiveStats(player));
        ActiveTraits.apply(player);
        EquipmentBonuses.apply(player);
    }

    /** Takes {@code levels} levels off the player (see {@link ProgressionRules#loseLevels}); returns how many they lost. */
    public static int loseLevels(ServerPlayer player, int levels) {
        PlayerStats before = get(player);
        PlayerStats after = ProgressionRules.loseLevels(Balance.current(), before, levels);
        if (after.level() != before.level()) {
            set(player, after);
        }
        return before.level() - after.level();
    }

    public static void awardXp(ServerPlayer player, int amount) {
        if (!MitofusWorld.is(player.level())) {
            return;
        }
        // Rested (diamond VR helmet): level XP only, not smithing XP.
        int gained = FatigueService.restedSeconds(player) > 0
                ? ProgressionRules.restedXp(amount, VrHelmetBalance.current().restedXpPercent()) : amount;
        ProgressionRules.XpResult result = ProgressionRules.addXp(Balance.current(), get(player), gained);
        XpRateService.record(player, gained);
        ClassService.awardClassXp(player, gained);
        set(player, result.stats());
        if (result.levelsGained() > 0) {
            player.displayClientMessage(Component.translatable("message.mitofus.level_up", result.stats().level()), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);
            int newLevel = result.stats().level();
            NeoForge.EVENT_BUS.post(new LevelUpEvent(player, newLevel - result.levelsGained(), newLevel));
            // After the event: what it grants (titles, class) can raise the maximums, and the refill goes to the final ones.
            restoreOnLevelUp(player);
        }
    }

    /** Maximum mana: from intelligence, plus what the player's passives add. */
    public static float maxMana(Player player) {
        return ProgressionRules.maxMana(Balance.current(), effectiveStats(player).intelligence())
                + (float) ActiveTraits.total(player, BonusType.MAX_MANA);
    }

    /** Mana regenerated per second: from intelligence, plus the passives' extra percentage. */
    public static float manaRegenPerSecond(Player player) {
        return ProgressionRules.manaRegenPerSecond(Balance.current(), effectiveStats(player).intelligence())
                * (1 + (float) ActiveTraits.total(player, BonusType.MANA_REGEN));
    }

    /** Gaining a level fully heals the player and refills their mana (to the maximum their stats give now). */
    public static void restoreOnLevelUp(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        player.setData(ModAttachments.MANA, maxMana(player));
    }

    /** Spends up to {@code amount} free points; the rule caps it at what's left and ignores bad amounts from the client. */
    public static void allocatePoints(ServerPlayer player, StatType stat, int amount) {
        PlayerStats before = get(player);
        PlayerStats after = ProgressionRules.allocate(before, stat, amount);
        if (after != before) {
            set(player, after);
        }
    }

    /** Adds smithing experience, raised by the titles that boost it (Velasco's Disciple). */
    public static void addSmithingXp(ServerPlayer player, int amount) {
        if (!MitofusWorld.is(player.level())) {
            return;
        }
        PlayerStats stats = get(player);
        long total = (long) stats.smithingXp() + ProgressionRules.boostedSmithingXp(amount, titleSmithingXpBonus(player));
        set(player, stats.withSmithingXp((int) Math.min(Integer.MAX_VALUE, total)));
    }

    /** The sum of the {@code smithing_xp_bonus} of every title the player holds. */
    public static double titleSmithingXpBonus(Player player) {
        double bonus = 0;
        for (var titleId : player.getData(ModAttachments.PLAYER_IDENTITY).titles()) {
            bonus += Definitions.get(player.level(), ModDatapackRegistries.TITLE, titleId).map(TitleDefinition::smithingXpBonus).orElse(0.0);
        }
        return bonus;
    }
}
