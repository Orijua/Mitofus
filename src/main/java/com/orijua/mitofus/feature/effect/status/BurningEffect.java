package com.orijua.mitofus.feature.effect.status;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Burning: 1 fire damage per second per level (amplifier + 1). Given by the {@code burn} status. */
public class BurningEffect extends MobEffect {
    public static final int INTERVAL_TICKS = 20;

    public BurningEffect() {
        super(MobEffectCategory.HARMFUL, 0xE0551A);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % INTERVAL_TICKS == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        entity.hurt(entity.damageSources().onFire(), amplifier + 1);
        return true;
    }
}
