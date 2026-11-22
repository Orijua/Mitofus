package com.orijua.mitofus.feature.equipment;

import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.equipment.option.OptionEffect;
import com.orijua.mitofus.feature.equipment.option.RolledOption;
import com.orijua.mitofus.feature.progression.PlayerStats;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.world.MitofusWorld;
import com.orijua.mitofus.core.config.WeightBalance;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Optional;
import java.util.function.BiConsumer;

/** Connects the equipment components (rating, enhancement, options, requirements) to the game and the tooltip. */
public final class EquipmentEvents {
    private EquipmentEvents() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(EquipmentEvents::onEquipmentChange);
        gameBus.addListener(EquipmentEvents::onTooltip);
        gameBus.addListener(EquipmentEvents::onDamageDealt);
        gameBus.addListener(EquipmentEvents::onAttack);
        gameBus.addListener(EquipmentEvents::onIncomingDamage);
        gameBus.addListener(EquipmentEvents::onPlayerTick);
    }

    public static boolean meetsRequirements(Player player, ItemStack stack) {
        Optional<EquipRequirements> requirements = EquipLevels.requirements(stack);
        return requirements.isEmpty() || requirements.get().metBy(ProgressionService.statsForRequirements(player));
    }

    /** Same rule with stats already computed, to reuse one calculation across several items. */
    public static boolean meetsRequirements(PlayerStats stats, ItemStack stack) {
        Optional<EquipRequirements> requirements = EquipLevels.requirements(stack);
        return requirements.isEmpty() || requirements.get().metBy(stats);
    }

    /** Equipment can give stats too (stat options), so both the stat effects and the bonuses are re-applied. */
    private static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            ProgressionService.refreshEffects(player);
        }
    }

    /** Every option of the equipment the player wears where it counts and whose requirements they meet. */
    public static void forEachActiveOption(Player player, BiConsumer<OptionEffect, Float> action) {
        if (!MitofusWorld.is(player.level())) {
            return;
        }
        PlayerStats stats = ProgressionService.statsForRequirements(player);
        for (ItemStack stack : EquipmentBonuses.wornEquipment(player)) {
            if (!meetsRequirements(stats, stack)) {
                continue;
            }
            for (RolledOption rolled : AppraisalService.activeOptions(stack)) {
                Definitions.get(player.level(), ModDatapackRegistries.ITEM_OPTION, rolled.option())
                        .ifPresent(def -> action.accept(def.effect(), rolled.value()));
            }
        }
    }

    /** Options that react to damage: blocking it as the wearer, or making the wearer's attacks hit harder. */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer wearer) {
            forEachActiveOption(wearer, (effect, value) -> {
                if (!event.isCanceled()) {
                    effect.onWearerHurt(wearer, event, value);
                }
            });
        }
        if (!event.isCanceled() && event.getSource().getEntity() instanceof ServerPlayer attacker) {
            forEachActiveOption(attacker, (effect, value) ->
                    event.setAmount(effect.outgoingDamage(attacker, event.getEntity(), event.getAmount(), value)));
        }
    }

    /** Once a second, options that act on their own while worn (Guadalete's fear). */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            forEachActiveOption(player, (effect, value) -> effect.tickWorn(player, value));
        }
    }

    /** Life steal and any other on-hit option, read from the attacker's main hand. */
    private static void onDamageDealt(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || event.getNewDamage() <= 0 || !MitofusWorld.is(attacker.level())) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        if (!meetsRequirements(attacker, weapon)) {
            return;
        }
        for (RolledOption rolled : AppraisalService.activeOptions(weapon)) {
            Definitions.get(attacker.level(), ModDatapackRegistries.ITEM_OPTION, rolled.option())
                    .ifPresent(def -> def.effect().onHit(attacker, event.getEntity(), event.getNewDamage(), rolled.value()));
        }
    }

    /** Fighting with an item whose requirements you don't meet is not allowed. */
    private static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (MitofusWorld.is(player.level()) && !meetsRequirements(player, player.getMainHandItem())) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("message.mitofus.requirements_unmet"), true);
        }
    }

    private static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        event.getToolTip().addAll(Math.min(1, event.getToolTip().size()),
                EquipmentTooltip.lines(stack, event.getContext().registries(), event.getEntity()));
        if (WeightBalance.current().enabled() && ItemWeights.gramsEach(stack) > 0) {
            event.getToolTip().add(Component.translatable("tooltip.mitofus.weight",
                    WeightRules.formatKg(ItemWeights.gramsEach(stack))).withStyle(ChatFormatting.GRAY));
        }
    }
}
