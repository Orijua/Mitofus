package com.orijua.mitofus.feature.synergy.client;

import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.synergy.SynergyDefinition;
import com.orijua.mitofus.feature.synergy.SynergyService;
import com.orijua.mitofus.feature.progression.PlayerClassDefinition;
import com.orijua.mitofus.feature.progression.StatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Map;

/** Texts of classes and synergies for the screens (client side, but only reads data both sides have). */
public final class SynergyText {
    private SynergyText() {
    }

    public static Component className(Level level, ResourceLocation classId) {
        return Definitions.get(level, ModDatapackRegistries.PLAYER_CLASS, classId)
                .map(PlayerClassDefinition::name).orElse(Component.literal(classId.toString()));
    }

    public static Component statsText(Map<StatType, Integer> stats) {
        MutableComponent text = Component.empty();
        stats.forEach((stat, value) -> text.append(Component.literal("+" + value + " ")).append(stat.displayName()).append("  "));
        return text;
    }

    /** What the class gives with the job at each step, shown before choosing; empty text when there is no synergy. */
    public static Component synergyLines(Level level, ResourceLocation classId, ResourceLocation job) {
        MutableComponent text = Component.empty();
        var steps = SynergyService.stepsOf(level, classId, job);
        if (steps.isEmpty()) {
            return text;
        }
        Component jobName = Definitions.get(level, ModDatapackRegistries.JOB, job)
                .map(definition -> definition.name()).orElse(Component.literal(job.toString()));
        // One short line: the best step's bonus, which is what the pair ends up giving.
        text.append("\n").append(Component.translatable("screen.mitofus.synergy_header", jobName))
                .append(" ").append(statsText(steps.get(steps.size() - 1).stats()));
        return text;
    }
}
