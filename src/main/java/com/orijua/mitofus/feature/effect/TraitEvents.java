package com.orijua.mitofus.feature.effect;

import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.effect.status.StatusService;
import com.orijua.mitofus.feature.form.FormService;
import com.orijua.mitofus.feature.progression.ProgressionService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Where the passive bonuses that act on events are applied: the damage the attacker deals (damage bonus, critical hits,
 * damage against the unaware), the damage the victim takes (reduction), what a hit gives back (life steal, statuses), what a
 * kill gives (health, mana) and the health that comes back with time.
 */
public final class TraitEvents {
    /** A critical hit deals this many times the damage before {@link BonusType#CRIT_DAMAGE}. */
    public static final double BASE_CRIT_MULTIPLIER = 1.5;
    private static final int REGEN_INTERVAL_TICKS = 20;

    private TraitEvents() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(TraitEvents::onIncomingDamage);
        gameBus.addListener(TraitEvents::onDamaged);
        gameBus.addListener(TraitEvents::onDeath);
        gameBus.addListener(TraitEvents::onPlayerTick);
    }

    /** The attacker's damage passives (+10 % with swords, at night, critical hits...) and the victim's damage reduction. */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer victim) {
            double reduction = ActiveTraits.total(victim, BonusType.DAMAGE_REDUCTION);
            if (reduction > 0) {
                event.setAmount((float) (event.getAmount() * (1 - Math.min(reduction, 1))));
            }
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        double bonus = ActiveTraits.damageBonus(attacker);
        LivingEntity target = event.getEntity();
        if (target instanceof Mob mob && mob.getTarget() != attacker) {
            bonus += ActiveTraits.total(attacker, BonusType.DAMAGE_VS_UNAWARE);
        }
        double multiplier = 1 + bonus;
        double critChance = ActiveTraits.total(attacker, BonusType.CRIT_CHANCE);
        if (critChance > 0 && attacker.getRandom().nextDouble() < critChance) {
            multiplier *= BASE_CRIT_MULTIPLIER + ActiveTraits.total(attacker, BonusType.CRIT_DAMAGE);
        }
        if (multiplier != 1) {
            event.setAmount((float) (event.getAmount() * multiplier));
        }
        FormService.addUse(attacker);
    }

    /** After a hit lands: the life it gives back and the statuses the attacker's passives give the target. */
    private static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || event.getNewDamage() <= 0) {
            return;
        }
        double steal = ActiveTraits.total(attacker, BonusType.LIFE_STEAL);
        if (steal > 0) {
            attacker.heal((float) (event.getNewDamage() * steal));
        }
        int level = ProgressionService.get(attacker).level();
        for (Bonus bonus : ActiveTraits.onHitStatuses(attacker)) {
            StatusService.apply(attacker, event.getEntity(), bonus.status().get(), bonus.valueAt(level), bonus.intensity(), bonus.duration());
        }
    }

    /** A kill gives back health and mana. */
    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        double health = ActiveTraits.total(killer, BonusType.HEAL_ON_KILL);
        if (health > 0) {
            killer.heal((float) health);
        }
        double mana = ActiveTraits.total(killer, BonusType.MANA_ON_KILL);
        if (mana > 0) {
            float max = ProgressionService.maxMana(killer);
            killer.setData(ModAttachments.MANA, (float) Math.min(max, killer.getData(ModAttachments.MANA) + mana));
        }
    }

    /** Health that comes back every second. */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % REGEN_INTERVAL_TICKS != 0
                || !player.isAlive() || player.getHealth() >= player.getMaxHealth()) {
            return;
        }
        double regen = ActiveTraits.total(player, BonusType.HEALTH_REGEN);
        if (regen > 0) {
            player.heal((float) regen);
        }
    }
}
