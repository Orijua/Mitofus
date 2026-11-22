package com.orijua.mitofus.feature.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Hits every enemy in a cone in front of the player that it can see (a solid block in between protects), scaling the weapon's damage with STR/DEX.
 * Optional {@code target_effects} are given to each enemy hit, e.g. {"id": "mitofus:bleeding", "duration": 100}.
 */
public record AreaDamageEffect(double range, double coneDegrees, double damageMultiplier,
                               double strengthScaling, double dexterityScaling,
                               List<MobEffectInstance> targetEffects, double damageMultiplierPerLevel) implements Effect {

    public AreaDamageEffect(double range, double coneDegrees, double damageMultiplier, double strengthScaling,
                            double dexterityScaling, List<MobEffectInstance> targetEffects) {
        this(range, coneDegrees, damageMultiplier, strengthScaling, dexterityScaling, targetEffects, 0.0);
    }

    public static final MapCodec<AreaDamageEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.doubleRange(0.5, 16).fieldOf("range").forGetter(AreaDamageEffect::range),
            Codec.doubleRange(1, 360).optionalFieldOf("cone_degrees", 140.0).forGetter(AreaDamageEffect::coneDegrees),
            Codec.doubleRange(0, 100).optionalFieldOf("damage_multiplier", 1.0).forGetter(AreaDamageEffect::damageMultiplier),
            Codec.DOUBLE.optionalFieldOf("strength_scaling", 0.0).forGetter(AreaDamageEffect::strengthScaling),
            Codec.DOUBLE.optionalFieldOf("dexterity_scaling", 0.0).forGetter(AreaDamageEffect::dexterityScaling),
            MobEffectInstance.CODEC.listOf().optionalFieldOf("target_effects", List.of()).forGetter(AreaDamageEffect::targetEffects),
            // Mastery: the multiplier grows by this much for every skill level above the first.
            Codec.doubleRange(0, 10).optionalFieldOf("damage_multiplier_per_level", 0.0).forGetter(AreaDamageEffect::damageMultiplierPerLevel)
    ).apply(instance, AreaDamageEffect::new));

    @Override
    public EffectType type() {
        return EffectType.AREA_DAMAGE;
    }

    @Override
    public boolean apply(EffectContext context) {
        LivingEntity caster = context.caster();
        ServerLevel level = context.level();
        Vec3 eye = context.position();
        Vec3 look = context.direction();
        double minDot = EffectRules.coneMinDot(coneDegrees);
        // A caster without an attack attribute (some mobs) does no base damage.
        double attack = caster.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? caster.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0;
        float damage = EffectRules.areaDamage(attack, damageMultiplier + damageMultiplierPerLevel * (context.skillLevel() - 1), strengthScaling, dexterityScaling,
                context.casterStats().strength(), context.casterStats().dexterity());

        List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(range),
                target -> EffectTargets.canAffect(caster, target) && inCone(eye, look, target, minDot) && EffectTargets.canSee(caster, target));
        hit.forEach(target -> {
                    target.hurt(EffectTargets.attackSource(caster), damage);
                    target.knockback(0.5, -look.x, -look.z);
                    targetEffects.forEach(effect -> target.addEffect(new MobEffectInstance(effect), caster));
                });

        for (int i = 1; i <= 3; i++) {
            Vec3 p = eye.add(look.scale(i * range / 4));
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y - 0.3, p.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, caster.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.8f);
        return !hit.isEmpty();
    }

    private boolean inCone(Vec3 eye, Vec3 look, LivingEntity target, double minDot) {
        Vec3 toTarget = target.getBoundingBox().getCenter().subtract(eye);
        double distance = toTarget.length();
        return EffectRules.inCone(distance, distance < 1.0 ? 1.0 : toTarget.normalize().dot(look), range, minDot);
    }
}
