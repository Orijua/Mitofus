package com.orijua.mitofus.feature.mob;

import com.orijua.mitofus.core.config.StunBalance;
import com.orijua.mitofus.core.registry.ModEffects;
import com.orijua.mitofus.feature.world.MitofusOnly;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Stuns anyone (player or mob) who takes a creature's blow worth 40% or more of their maximum life. The damage
 * counted is the final one, after armor and absorption. A stunned player is held by {@code FatigueRestraint}; a stunned mob
 * loses its target and cannot pick another, so it neither walks to you nor shoots. Bosses ({@link BossMob}) are never stunned.
 * Only in Mitofus: the real world is vanilla, so nothing is stunned there ({@link MitofusOnly#active}).
 */
public final class StunService {
    /** Game tick until which the victim cannot be stunned again, in its persistent data. */
    private static final String IMMUNE_UNTIL_TAG = "mitofus_stun_immune_until";

    private StunService() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(StunService::onDamaged);
        gameBus.addListener(StunService::onChangeTarget);
    }

    private static void onDamaged(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!victim.isAlive() || victim instanceof BossMob || MitofusOnly.active(victim) || !isBlow(event.getSource())) {
            return;
        }
        StunBalance balance = StunBalance.current();
        if (StunRules.stuns(balance, event.getNewDamage(), victim.getMaxHealth())) {
            stun(victim, balance);
        }
    }

    /** Only a blow from a creature counts, not a fall, fire, bleeding, thorns or a stray projectile with no owner. */
    private static boolean isBlow(DamageSource source) {
        return source.getEntity() instanceof LivingEntity && !source.is(DamageTypes.THORNS);
    }

    private static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() != null && event.getEntity().hasEffect(ModEffects.STUNNED)) {
            event.setCanceled(true);
        }
    }

    /** Applies the stun, unless the victim is already stunned (a second blow does not extend it). */
    static void stun(LivingEntity victim, StunBalance balance) {
        if (MitofusOnly.active(victim)) {
            return;
        }
        long now = victim.level().getGameTime();
        if (victim.hasEffect(ModEffects.STUNNED) || StunRules.isImmune(now, victim.getPersistentData().getLong(IMMUNE_UNTIL_TAG))) {
            return;
        }
        victim.getPersistentData().putLong(IMMUNE_UNTIL_TAG, StunRules.immuneUntil(balance, now));
        victim.addEffect(new MobEffectInstance(ModEffects.STUNNED, balance.durationTicks(), 0, false, true, true));
        if (victim instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        if (victim instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("effect.mitofus.stunned.hit"), true);
        }
    }
}
