package com.orijua.mitofus.feature.effect;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Who an effect may hurt. */
public final class EffectTargets {
    private EffectTargets() {
    }

    /**
     * Alive, not the caster, not an ally and, between two players, only when the server's PvP and the teams allow it.
     * Known gap: with a mob as caster it can hurt other non-allied mobs ({@code isAlliedTo} only looks at teams).
     */
    public static boolean canAffect(LivingEntity caster, LivingEntity target) {
        if (target == caster || !target.isAlive() || target.isAlliedTo(caster)) {
            return false;
        }
        return !(caster instanceof Player attacker && target instanceof Player victim) || attacker.canHarmPlayer(victim);
    }

    /**
     * Whether a solid block stops the caster seeing {@code target}: the ray goes from the caster's eyes to the target's eyes
     * or to its centre, and either one clear is enough (so a low wall in front of a tall creature doesn't hide it).
     */
    public static boolean canSee(LivingEntity caster, LivingEntity target) {
        Vec3 eye = caster.getEyePosition();
        return clear(caster, eye, target.getEyePosition()) || clear(caster, eye, target.getBoundingBox().getCenter());
    }

    private static boolean clear(LivingEntity caster, Vec3 from, Vec3 to) {
        return caster.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster))
                .getType() == HitResult.Type.MISS;
    }

    public static DamageSource attackSource(LivingEntity caster) {
        return caster instanceof Player player
                ? caster.damageSources().playerAttack(player)
                : caster.damageSources().mobAttack(caster);
    }

    /** The creature the caster is looking at within {@code range} blocks, if it can be affected. */
    public static Optional<LivingEntity> lookTarget(LivingEntity caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(range));
        // A solid block in the way stops the ray: no targeting through walls.
        BlockHitResult wall = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (wall.getType() != HitResult.Type.MISS) {
            end = wall.getLocation();
        }
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster.level(), caster, eye, end,
                new AABB(eye, end).inflate(1.0),
                entity -> entity instanceof LivingEntity living && canAffect(caster, living), 0.3f);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? Optional.of(living) : Optional.empty();
    }
}
