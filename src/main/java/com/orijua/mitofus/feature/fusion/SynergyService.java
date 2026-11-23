package com.orijua.mitofus.feature.fusion;

import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.progression.ClassService;
import com.orijua.mitofus.feature.progression.ClassSlot;
import com.orijua.mitofus.feature.progression.PlayerClassDefinition;
import com.orijua.mitofus.feature.progression.PlayerClasses;
import com.orijua.mitofus.feature.progression.StatModifier;
import com.orijua.mitofus.feature.progression.StatSource;
import com.orijua.mitofus.feature.progression.StatType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads the loaded synergies and gives the stat modifiers of the player's class + job pairs. Works on both sides. */
public final class SynergyService {
    private SynergyService() {
    }

    public static SynergyRules.Index index(Level level) {
        Map<ResourceLocation, Map<ResourceLocation, SynergyDefinition>> specific = new HashMap<>();
        Map<ResourceLocation, SynergyDefinition> defaults = new HashMap<>();
        for (Definitions.Entry<SynergyDefinition> entry : Definitions.all(level, ModDatapackRegistries.SYNERGY)) {
            SynergyDefinition synergy = entry.value();
            synergy.playerClass().ifPresentOrElse(
                    classId -> specific.computeIfAbsent(classId, id -> new HashMap<>()).put(synergy.job(), synergy),
                    () -> defaults.put(synergy.job(), synergy));
        }
        return new SynergyRules.Index(specific, defaults);
    }

    /** The multiplier of the fusion that made {@code hybrid} (the default if no fusion names it). */
    public static double multiplierOf(Level level, ResourceLocation hybrid) {
        return Definitions.all(level, ModDatapackRegistries.FUSION).stream()
                .filter(entry -> entry.value().resultClass().equals(hybrid))
                .map(entry -> entry.value().synergyMultiplier()).findFirst().orElse(FusionDefinition.DEFAULT_SYNERGY_MULTIPLIER);
    }

    /** What {@code placed} gives together with {@code job} at the job level {@code jobLevel}. */
    public static Map<StatType, Integer> bonus(Level level, SynergyRules.Index index, ClassSlot placed, ResourceLocation job, int jobLevel) {
        return SynergyRules.bonus(index, placed.classId(), placed.origin(), multiplierOf(level, placed.classId()), job, placed.grade(), jobLevel);
    }

    /** Every synergy bonus of the player's classes with their job, each tagged for the stat tooltip. */
    public static List<StatModifier> modifiers(Player player) {
        PlayerClasses classes = player.getData(ModAttachments.PLAYER_CLASSES);
        List<StatModifier> result = new ArrayList<>();
        Optional<ResourceLocation> job = classes.job();
        if (job.isEmpty() || classes.slots().isEmpty()) {
            return result;
        }
        int jobLevel = ClassService.jobLevels(player).getOrDefault(job.get(), 0);
        SynergyRules.Index index = index(player.level());
        for (ClassSlot placed : classes.slots()) {
            Map<StatType, Integer> bonus = bonus(player.level(), index, placed, job.get(), jobLevel);
            if (!bonus.isEmpty()) {
                Component className = Definitions.get(player.level(), ModDatapackRegistries.PLAYER_CLASS, placed.classId())
                        .map(PlayerClassDefinition::name).orElse(Component.literal(placed.classId().toString()));
                result.addAll(StatModifier.fromBonuses(new StatSource(Component.translatable("stat_source.mitofus.synergy", className)), bonus));
            }
        }
        return result;
    }

    /** What a class would give with a job at their best step, for the screen shown before choosing (e.g. at the top grade and level). */
    public static List<SynergyDefinition.Step> stepsOf(Level level, ResourceLocation classId, ResourceLocation job) {
        return index(level).of(classId, job).map(SynergyDefinition::steps).orElse(List.of());
    }
}
