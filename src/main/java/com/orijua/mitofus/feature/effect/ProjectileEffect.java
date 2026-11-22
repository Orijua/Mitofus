package com.orijua.mitofus.feature.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.phys.Vec3;

/** Shoots small fireballs where the player looks, spread a little when {@code count} > 1. */
public record ProjectileEffect(int count, double speed, double speedPerLevel) implements Effect {

    public ProjectileEffect(int count, double speed) {
        this(count, speed, 0.0);
    }


    public static final MapCodec<ProjectileEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.intRange(1, 10).optionalFieldOf("count", 1).forGetter(ProjectileEffect::count),
            Codec.doubleRange(0.1, 5).optionalFieldOf("speed", 1.0).forGetter(ProjectileEffect::speed),
            // Mastery: speed added for each skill level above the first.
            Codec.doubleRange(0, 5).optionalFieldOf("speed_per_level", 0.0).forGetter(ProjectileEffect::speedPerLevel)
    ).apply(instance, ProjectileEffect::new));

    @Override
    public EffectType type() {
        return EffectType.PROJECTILE;
    }

    @Override
    public boolean apply(EffectContext context) {
        Vec3 look = context.direction();
        Vec3 start = context.position().add(look);
        for (int i = 0; i < count; i++) {
            double spread = EffectRules.projectileSpread(i, count);
            Vec3 direction = look.add(-look.z * spread, 0, look.x * spread).normalize().scale(speed + speedPerLevel * (context.skillLevel() - 1));
            SmallFireball fireball = new SmallFireball(context.level(), context.caster(), direction);
            fireball.setPos(start);
            context.level().addFreshEntity(fireball);
        }
        context.level().playSound(null, context.caster().blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }
}
