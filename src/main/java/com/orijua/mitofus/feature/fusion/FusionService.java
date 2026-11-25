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

    /** What an absorption costs: the cheapest fusion the absorbed class takes part in. Empty when it takes part in none. */
    public static Optional<Long> absorbCost(Player player, ResourceLocation classId) {
        return Definitions.all(player.level(), ModDatapackRegistries.FUSION).stream()
                .filter(entry -> entry.value().parents().contains(classId))
                .map(entry -> costInIron(entry.value())).min(Long::compare);
    }

    private static FusionRules.Parent parentOf(Player player, Map<ResourceLocation, TalentDefinition> talents, Optional<ClassSlot> slot) {
        boolean legendary = slot.flatMap(s -> Definitions.get(player.level(), ModDatapackRegistries.PLAYER_CLASS, s.classId()))
                .map(PlayerClassDefinition::legendary).orElse(false);
        return new FusionRules.Parent(slot, legendary, slot.map(s -> FusionRules.treeComplete(talents, s)).orElse(false));
    }

    private static Optional<ClassSlot> slotOf(PlayerClasses classes, int slot) {
        return classes.slots().stream().filter(s -> s.slot() == slot).findFirst();
    }

    private static Optional<ClassSlot> classSlot(PlayerClasses classes, ResourceLocation classId) {
        return classes.slots().stream().filter(s -> s.classId().equals(classId) && !s.isHybrid()).findFirst();
    }

    /** What the player still needs for the hybrid in {@code hybridSlot} to absorb {@code classId}; empty when it can be done now. */
    public static Set<FusionRules.Missing> missingAbsorb(Player player, int hybridSlot, ResourceLocation classId) {
        PlayerClasses classes = player.getData(ModAttachments.PLAYER_CLASSES);
        Map<ResourceLocation, TalentDefinition> talents = TalentService.all(player.level());
        Optional<Long> cost = absorbCost(player, classId);
        Set<FusionRules.Missing> missing = EnumSet.noneOf(FusionRules.Missing.class);
        missing.addAll(FusionRules.missingAbsorb(parentOf(player, talents, slotOf(classes, hybridSlot)),
                parentOf(player, talents, classSlot(classes, classId)), cost.isPresent() && Wallet.canAfford(player, cost.get())));
        if (cost.isEmpty()) {
            missing.add(FusionRules.Missing.CLASS);
        }
        return missing;
    }

    /**
     * A hybrid absorbs a normal class it was not made of, so no class is ever left without a fusion. The hybrid keeps its identity and
     * gains the class's tree and forms. @return true if it happened; otherwise nothing changed and the player is told what is missing.
     */
    public static boolean absorb(ServerPlayer player, int hybridSlot, ResourceLocation classId) {
        if (!MitofusWorld.is(player.level())) {
            return false;
        }
        Set<FusionRules.Missing> missing = missingAbsorb(player, hybridSlot, classId);
        if (!missing.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.mitofus.fusion_missing",
                    Component.translatable("screen.mitofus.fusion_missing." + missing.iterator().next().name().toLowerCase())), false);
            return false;
        }
        PlayerClasses classes = ClassService.classes(player);
        ClassSlot hybrid = slotOf(classes, hybridSlot).orElseThrow();
        ClassSlot lone = classSlot(classes, classId).orElseThrow();
        if (hybrid.origin().contains(classId) || !Wallet.pay(player, absorbCost(player, classId).orElseThrow())) {
            return false;
        }
        Map<ResourceLocation, TalentDefinition> talents = TalentService.all(player.level());
        ClassSlot merged = FusionRules.absorb(hybrid, lone, hybrid.paidPoints() + TalentRules.pointsSpent(talents, lone));
        List<ClassSlot> slots = new ArrayList<>(classes.slots());
        slots.removeIf(s -> s.slot() == hybrid.slot() || s.slot() == lone.slot());
        slots.add(merged);
        slots.sort(java.util.Comparator.comparingInt(ClassSlot::slot));
        player.setData(ModAttachments.PLAYER_CLASSES, new PlayerClasses(List.copyOf(slots), classes.job()));
        ProgressionService.refreshEffects(player);
        com.orijua.mitofus.feature.form.FormService.sync(player);
        player.displayClientMessage(Component.translatable("message.mitofus.absorb_done",
                Definitions.get(player.level(), ModDatapackRegistries.PLAYER_CLASS, classId).map(PlayerClassDefinition::name).orElse(Component.empty())), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 0.6f);
        return true;
    }
}
