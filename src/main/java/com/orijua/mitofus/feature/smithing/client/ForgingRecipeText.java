package com.orijua.mitofus.feature.smithing.client;

import com.orijua.mitofus.core.config.EquipLevelBalance;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.equipment.EquipLevelRules;
import com.orijua.mitofus.feature.equipment.EquipLevels;
import com.orijua.mitofus.feature.equipment.EquipRequirements;
import com.orijua.mitofus.feature.progression.JobDefinition;
import com.orijua.mitofus.feature.progression.StatType;
import com.orijua.mitofus.feature.smithing.ForgingRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * How a forging recipe's requirements read, shared by the smithing anvil's side panel and the JEI page so the two
 * always say the same: change the wording or the level rule here, never in one of them only.
 */
public final class ForgingRecipeText {
    private ForgingRecipeText() {
    }

    /** "Only: <class>" when the recipe is for one job (only the Legendary Blacksmith can use Guadalete's blueprint). */
    public static Optional<Component> requiredJob(Level level, ForgingRecipe recipe) {
        return recipe.extras().requiredJob().map(id -> {
            Component name = Definitions.get(level, ModDatapackRegistries.JOB, id)
                    .map(JobDefinition::name).orElse(Component.literal(id.toString()));
            return Component.translatable("screen.mitofus.anvil_class", name);
        });
    }

    /**
     * The levels the result may ask for. The piece comes out rated, so it asks for the effective level (the recipe's,
     * or the one of its material and rating), which depends on the rating it rolls.
     */
    public static EquipLevelRules.Range levelRange(ForgingRecipe recipe) {
        return EquipLevels.levelRange(recipe.result().getItem(), recipe.extras().minRating(), recipe.extras().maxRating(),
                recipe.requirements().map(EquipRequirements::level).orElse(0), EquipLevelBalance.current());
    }

    /** "Level 5", or "Level 5-12" when the rating it rolls changes it. */
    public static Component levelText(EquipLevelRules.Range levels) {
        return levels.low() == levels.high()
                ? Component.translatable("tooltip.mitofus.requires_level", levels.low())
                : Component.translatable("screen.mitofus.anvil_requires_level_range", levels.low(), levels.high());
    }

    /** "Strength (STR) 10": one stat the result asks for. */
    public static MutableComponent statText(StatType stat, int value) {
        return stat.displayName().copy().append(" " + value);
    }

    /** Every stat the result asks for, in the recipe's order. */
    public static List<Component> statTexts(ForgingRecipe recipe) {
        List<Component> texts = new ArrayList<>();
        for (Map.Entry<StatType, Integer> stat : recipe.requirements().map(EquipRequirements::stats).orElse(Map.of()).entrySet()) {
            texts.add(statText(stat.getKey(), stat.getValue()));
        }
        return texts;
    }
}
