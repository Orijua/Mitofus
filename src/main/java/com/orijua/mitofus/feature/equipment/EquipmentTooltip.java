package com.orijua.mitofus.feature.equipment;

import com.orijua.mitofus.core.config.EnhanceBalance;
import com.orijua.mitofus.core.config.EquipLevelBalance;
import com.orijua.mitofus.core.config.TradeBalance;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.economy.MoneyText;
import com.orijua.mitofus.feature.economy.TradeGoods;
import com.orijua.mitofus.feature.economy.TradeRules;
import com.orijua.mitofus.feature.equipment.option.ItemOptionDefinition;
import com.orijua.mitofus.feature.equipment.option.OptionEffect;
import com.orijua.mitofus.feature.equipment.option.RolledOption;
import com.orijua.mitofus.feature.progression.PlayerStats;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.progression.StatType;
import com.orijua.mitofus.feature.smithing.HammerRules;
import com.orijua.mitofus.feature.smithing.SmithingHammers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The lines the mod adds to the tooltip of equipment: state of the appraisal, rating, enhancement, options, requirements and
 * value. A datum an unappraised piece has not revealed is left out, not marked; what it has revealed is shown dimmed, as
 * information and not as an effect in force. It touches no client class, so a test can
 * build the lines on the server. It reads the economy for the value line (see {@link AppraisalService}).
 */
public final class EquipmentTooltip {
    private EquipmentTooltip() {
    }

    /** The lines to insert after the item's name. {@code player} is null when there is none (then no requirements are shown). */
    public static List<Component> lines(ItemStack stack, @Nullable HolderLookup.Provider registries, @Nullable Player player) {
        List<Component> lines = new ArrayList<>();
        boolean unappraised = AppraisalService.isUnappraised(stack);

        if (unappraised) {
            boolean nothingRevealed = stack.get(ModDataComponents.APPRAISAL).revealed().isEmpty();
            lines.add(Component.translatable(nothingRevealed ? "tooltip.mitofus.unappraised" : "tooltip.mitofus.partly_appraised")
                    .withStyle(ChatFormatting.GRAY));
        }

        ItemRating rating = stack.get(ModDataComponents.ITEM_RATING);
        if (rating != null && AppraisalService.isRevealed(stack, AppraisalRules.RATING)) {
            if (unappraised) {
                // The damage bonus is not applied yet, so only the rank is shown, dimmed.
                lines.add(pending(Component.translatable("tooltip.mitofus.rating", rating.displayName())));
            } else {
                if (EquipmentBonuses.isWeapon(stack) && rating.attackDamageBonus() > 0) {
                    lines.add(Component.translatable("tooltip.mitofus.rating_bonus", rating.displayName(), rating.attackDamageBonus()));
                } else if (SmithingHammers.isHammer(stack) && HammerRules.ratingBonus(rating) > 0) {
                    // A hammer adds forging skill, not damage.
                    lines.add(Component.translatable("tooltip.mitofus.rating_forge", rating.displayName(),
                            HammerRules.format(HammerRules.ratingBonus(rating))));
                } else {
                    lines.add(Component.translatable("tooltip.mitofus.rating", rating.displayName()));
                }
            }
        }

        int enhancement = stack.getOrDefault(ModDataComponents.ENHANCEMENT, 0);
        if (enhancement > 0) {
            Component line = EquipmentBonuses.enhancementBonus(stack, EnhanceBalance.current())
                    .map(bonus -> Component.translatable(
                            bonus.attribute().equals(Attributes.ARMOR) ? "tooltip.mitofus.enhancement_armor" : "tooltip.mitofus.enhancement",
                            enhancement, EquipmentRules.formatBonus(bonus.amount()), EquipmentRules.formatPercent(bonus.factor() - 1)))
                    .orElseGet(() -> Component.translatable("tooltip.mitofus.enhancement_plain", enhancement));
            lines.add(line.copy().withStyle(ChatFormatting.AQUA));
        }

        if (registries != null) {
            List<RolledOption> options = stack.getOrDefault(ModDataComponents.ITEM_OPTIONS, List.<RolledOption>of());
            for (int i = 0; i < options.size(); i++) {
                if (!AppraisalService.isRevealed(stack, AppraisalRules.optionKey(i))) {
                    continue;
                }
                RolledOption rolled = options.get(i);
                Optional<ItemOptionDefinition> def =
                        Definitions.get(registries, ModDatapackRegistries.ITEM_OPTION, rolled.option());
                def.ifPresent(d -> {
                    Component line = Component.literal("  ").append(d.name()).append(" ").append(d.effect().formatValue(rolled.value()));
                    lines.add(unappraised ? pending(line) : line.copy().withStyle(ChatFormatting.GREEN));
                });
                def.flatMap(d -> weaponSkillCostLine(registries, d.effect(), unappraised)).ifPresent(lines::add);
            }
        }

        if (player != null) {
            Optional<EquipRequirements> truth = EquipLevels.trueRequirements(stack);
            if (truth.isPresent()) {
                PlayerStats stats = ProgressionService.statsForRequirements(player);
                if (unappraised) {
                    lines.addAll(unappraisedRequirementLines(stack, truth.get(), stats));
                } else {
                    lines.addAll(requirementLines(truth.get(), stats));
                    if (!truth.get().metBy(stats)) {
                        lines.add(Component.translatable("tooltip.mitofus.bonuses_inactive").withStyle(ChatFormatting.RED));
                    }
                }
            }
        }

        if (rating != null && TradeGoods.refusal(stack).isEmpty() && AppraisalService.isRevealed(stack, AppraisalRules.VALUE)) {
            TradeBalance trade = TradeBalance.current();
            long value = TradeRules.sellPriceIron(TradeGoods.trueReference(stack, trade), 0, trade);
            lines.add(Component.translatable(unappraised ? "tooltip.mitofus.value_unappraised" : "tooltip.mitofus.value",
                    MoneyText.iron(value)).withStyle(ChatFormatting.GOLD));
        }
        return lines;
    }

    /** A line of something revealed but not yet in force: dimmed, never green or red. */
    private static Component pending(Component line) {
        return line.copy().withStyle(ChatFormatting.DARK_GRAY);
    }

    /** Cost of the skill an option grants, as an indented grey line (dimmed if unappraised); empty if the option grants none. */
    private static Optional<Component> weaponSkillCostLine(HolderLookup.Provider registries, OptionEffect effect, boolean unappraised) {
        return effect.grantedSkill()
                .flatMap(id -> Definitions.get(registries, ModDatapackRegistries.SKILL, id))
                .map(skill -> {
                    Component line = Component.literal("    ").append(skill.costLine(true));
                    return unappraised ? pending(line) : line.copy().withStyle(ChatFormatting.GRAY);
                });
    }

    private static List<Component> requirementLines(EquipRequirements requirements, PlayerStats stats) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tooltip.mitofus.requires").withStyle(ChatFormatting.GRAY));
        lines.add(requirementLine(Component.translatable("tooltip.mitofus.requires_level", requirements.level()),
                stats.level() >= requirements.level()));
        for (StatType stat : StatType.values()) {
            Integer needed = requirements.stats().get(stat);
            if (needed != null) {
                lines.add(statLine(stat, needed, stats));
            }
        }
        return lines;
    }

    /**
     * What an unappraised piece asks for: the level of a Normal piece (the requirement in force), plus the real level and the
     * real stat requirements that have been revealed. The bonuses are inactive if the requirement in force is not met.
     */
    private static List<Component> unappraisedRequirementLines(ItemStack stack, EquipRequirements truth, PlayerStats stats) {
        List<Component> lines = new ArrayList<>();
        int inForce = EquipLevels.normalLevel(stack.getItem(), EquipLevelBalance.current());
        lines.add(Component.translatable("tooltip.mitofus.requires").withStyle(ChatFormatting.GRAY));
        boolean met = stats.level() >= inForce;
        lines.add(requirementLine(Component.translatable("tooltip.mitofus.requires_level_unappraised", inForce), met));
        if (AppraisalService.isRevealed(stack, AppraisalRules.LEVEL)) {
            lines.add(pending(Component.literal("  ").append(
                    Component.translatable("tooltip.mitofus.requires_level_appraised", truth.level()))));
        }
        for (StatType stat : StatType.values()) {
            Integer needed = truth.stats().get(stat);
            if (needed != null && AppraisalService.isRevealed(stack, AppraisalRules.statKey(stat.getSerializedName()))) {
                lines.add(pending(Component.literal("  ").append(
                        Component.translatable("tooltip.mitofus.requires_stat_appraised", needed, stat.displayName()))));
            }
        }
        if (!met) {
            lines.add(Component.translatable("tooltip.mitofus.bonuses_inactive").withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    private static Component statLine(StatType stat, int needed, PlayerStats stats) {
        return requirementLine(Component.literal(needed + " ").append(stat.displayName()), stats.get(stat) >= needed);
    }

    private static Component requirementLine(Component text, boolean met) {
        return Component.literal("  ").append(text).withStyle(met ? ChatFormatting.GRAY : ChatFormatting.RED);
    }
}
