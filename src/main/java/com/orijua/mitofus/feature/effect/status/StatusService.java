package com.orijua.mitofus.feature.effect.status;

import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.core.registry.ModEffects;
import com.orijua.mitofus.feature.world.MitofusOnly;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * The one place where statuses are given: a passive that hits, a skill, an item option, a mob... all go through here, so
 * the chance, the limits, the renewal and the immunity windows are the same whatever the source.
 */
public final class StatusService {
    private static final String IMMUNE_TAG_PREFIX = "mitofus_status_immune_";

    private StatusService() {
    }

    /**
     * Rolls {@code chance} (kept under the status's maximum) and, if it comes up, gives {@code target} the status with the
     * asked intensity and duration (or the status's own), kept under its maximums.
     *
     * @return whether the target got it
     */
    public static boolean apply(@Nullable LivingEntity source, LivingEntity target, ResourceLocation statusId, double chance,
                                Optional<Integer> intensity, Optional<Integer> duration) {
        Optional<StatusDefinition> found = Definitions.get(target.level(), ModDatapackRegistries.STATUS, statusId);
        if (found.isEmpty() || !target.isAlive() || MitofusOnly.active(target)) {
            return false;
        }
        StatusDefinition status = found.get();
        if (target.getRandom().nextDouble() >= StatusRules.chance(chance, status.limits())) {
            return false;
        }
        long now = target.level().getGameTime();
        String tag = IMMUNE_TAG_PREFIX + statusId.getPath();
        if (StatusRules.isImmune(now, target.getPersistentData().getLong(tag))) {
            return false;
        }
        int ticks = StatusRules.duration(duration, status.limits());
        int level = StatusRules.intensity(intensity, status.limits());
        if (!refresh(target, new MobEffectInstance(status.effect(), ticks, level - 1, false, true, true), source)) {
            return false;
        }
        long until = StatusRules.immuneUntil(now, ticks, status.limits());
        if (until > 0) {
            target.getPersistentData().putLong(tag, until);
        }
        if (status.effect().is(ModEffects.STUNNED.getId()) && target instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        return true;
    }

    /** Whether {@code target} carries the status right now (the game effect that carries it is active). */
    public static boolean has(LivingEntity target, ResourceLocation statusId) {
        return Definitions.get(target.level(), ModDatapackRegistries.STATUS, statusId)
                .map(status -> target.hasEffect(status.effect())).orElse(false);
    }

    /**
     * Gives a game effect that renews its duration if the target already has it, and never stacks its intensity. A
     * stronger instance the target already has is kept (see {@link StatusRules#keepsExisting}).
     *
     * @return whether the target now has the new instance
     */
    public static boolean refresh(LivingEntity target, MobEffectInstance effect, @Nullable LivingEntity source) {
        MobEffectInstance current = target.getEffect(effect.getEffect());
        if (current != null && StatusRules.keepsExisting(current.getAmplifier(), current.getDuration(), effect.getAmplifier(), effect.getDuration())) {
            return false;
        }
        target.removeEffect(effect.getEffect());
        return target.addEffect(effect, source);
    }
}
