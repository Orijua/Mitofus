package com.orijua.mitofus.feature.effect;

import com.orijua.mitofus.feature.progression.PlayerStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;

/**
 * Everything an {@link Effect} needs to act. The caster is a {@link LivingEntity}, not a player, so bosses can use the engine.
 * {@code magnitude} scales the damage of an area_damage effect (1 = as written; talents raise it) and {@code skillLevel} is the mastery level.
 */
public record EffectContext(ServerLevel level, LivingEntity caster, Optional<LivingEntity> target, Vec3 position,
                            Vec3 direction, EffectSource source, float magnitude, int skillLevel, PlayerStats casterStats) {

    public EffectContext {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(caster, "caster");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(casterStats, "casterStats");
        if (magnitude < 0) {
            throw new IllegalArgumentException("magnitude must be >= 0: " + magnitude);
        }
        if (skillLevel < 1) {
            throw new IllegalArgumentException("skillLevel must be >= 1: " + skillLevel);
        }
    }

    /** A context at the caster's eyes looking where it looks, with no target. Server-side casters only. */
    public static EffectContext of(LivingEntity caster, EffectSource source, PlayerStats stats) {
        if (!(caster.level() instanceof ServerLevel level)) {
            throw new IllegalArgumentException("effects only run on the server");
        }
        return new EffectContext(level, caster, Optional.empty(), caster.getEyePosition(), caster.getLookAngle(),
                source, 1.0f, 1, stats);
    }

    public EffectContext withTarget(LivingEntity newTarget) {
        return new EffectContext(level, caster, Optional.of(newTarget), position, direction, source, magnitude, skillLevel, casterStats);
    }

    public EffectContext withMagnitude(float newMagnitude) {
        return new EffectContext(level, caster, target, position, direction, source, newMagnitude, skillLevel, casterStats);
    }

    public EffectContext withSkillLevel(int newSkillLevel) {
        return new EffectContext(level, caster, target, position, direction, source, magnitude, newSkillLevel, casterStats);
    }
}
