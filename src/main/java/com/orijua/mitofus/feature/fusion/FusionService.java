package com.orijua.mitofus.feature.fusion;

import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.economy.Money;
import com.orijua.mitofus.feature.economy.Wallet;
import com.orijua.mitofus.feature.progression.ClassService;
import com.orijua.mitofus.feature.progression.ClassSlot;
import com.orijua.mitofus.feature.progression.PlayerClassDefinition;
import com.orijua.mitofus.feature.progression.PlayerClasses;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.skill.SkillService;
import com.orijua.mitofus.feature.talent.TalentDefinition;
import com.orijua.mitofus.feature.talent.TalentRules;
import com.orijua.mitofus.feature.talent.TalentService;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Fusing two classes of a player into a hybrid. The server is the only place that changes anything; the client uses
 * {@link #missing} to grey out what is not available yet. The only writer of {@link PlayerClasses} for a fusion.
 */
public final class FusionService {
    private FusionService() {
    }

    public static long costInIron(FusionDefinition fusion) {
        return Money.iron(fusion.cost());
    }

    private static FusionRules.Parent parent(Player player, PlayerClasses classes, Map<ResourceLocation, TalentDefinition> talents, ResourceLocation classId) {
        Optional<ClassSlot> slot = classes.slots().stream().filter(s -> s.classId().equals(classId)).findFirst();
        boolean legendary = Definitions.get(player.level(), ModDatapackRegistries.PLAYER_CLASS, classId).map(PlayerClassDefinition::legendary).orElse(false);
        return new FusionRules.Parent(slot, legendary, slot.map(s -> FusionRules.treeComplete(talents, s)).orElse(false));
    }

    /** What the player still needs for {@code fusion}; empty when it can be done now. Works on both sides. */
    public static Set<FusionRules.Missing> missing(Player player, FusionDefinition fusion) {
        PlayerClasses classes = player.getData(ModAttachments.PLAYER_CLASSES);
        Map<ResourceLocation, TalentDefinition> talents = TalentService.all(player.level());
        Set<FusionRules.Missing> missing = EnumSet.noneOf(FusionRules.Missing.class);
        missing.addAll(FusionRules.missing(parent(player, classes, talents, fusion.parents().get(0)), parent(player, classes, talents, fusion.parents().get(1)),
                fusion.allowMixedGrades(), Wallet.canAfford(player, costInIron(fusion))));
        if (Definitions.get(player.level(), ModDatapackRegistries.PLAYER_CLASS, fusion.resultClass()).isEmpty()) {
            missing.add(FusionRules.Missing.CLASS);
        }
        return missing;
    }

    /** Fuses the two classes of {@code fusionId}. @return true if it happened; otherwise nothing changed and the player is told what is missing. */
    public static boolean fuse(ServerPlayer player, ResourceLocation fusionId) {
        if (!MitofusWorld.is(player.level())) {
            return false;
        }
        Optional<FusionDefinition> found = Definitions.get(player.level(), ModDatapackRegistries.FUSION, fusionId);
        if (found.isEmpty()) {
            return false;
        }
        FusionDefinition fusion = found.get();
        Set<FusionRules.Missing> missing = missing(player, fusion);
        if (!missing.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.mitofus.fusion_missing",
                    Component.translatable("screen.mitofus.fusion_missing." + missing.iterator().next().name().toLowerCase())), false);
            return false;
        }
        PlayerClasses classes = ClassService.classes(player);
        ClassSlot first = classes.slots().stream().filter(s -> s.classId().equals(fusion.parents().get(0))).findFirst().orElseThrow();
        ClassSlot second = classes.slots().stream().filter(s -> s.classId().equals(fusion.parents().get(1))).findFirst().orElseThrow();
        if (!Wallet.pay(player, costInIron(fusion))) {
            return false;
        }
        Map<ResourceLocation, TalentDefinition> talents = TalentService.all(player.level());
        ClassSlot hybrid = FusionRules.fuse(first, second, fusion.resultClass(),
                TalentRules.pointsSpent(talents, first) + TalentRules.pointsSpent(talents, second));
        List<ClassSlot> slots = new ArrayList<>(classes.slots());
        slots.removeIf(s -> s.slot() == first.slot() || s.slot() == second.slot());
        slots.add(hybrid);
        slots.sort(java.util.Comparator.comparingInt(ClassSlot::slot));
        player.setData(ModAttachments.PLAYER_CLASSES, new PlayerClasses(List.copyOf(slots), classes.job()));

        PlayerClassDefinition result = Definitions.get(player.level(), ModDatapackRegistries.PLAYER_CLASS, fusion.resultClass()).orElseThrow();
        result.skills().forEach(skill -> SkillService.learn(player, skill));
        ProgressionService.refreshEffects(player);
        player.displayClientMessage(Component.translatable("message.mitofus.fusion_done", result.name()), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 0.6f);
        return true;
    }
}
