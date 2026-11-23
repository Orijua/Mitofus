package com.orijua.mitofus.feature.vrhelmet;

import com.orijua.mitofus.core.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The body of a player who put on the connection helmet: it lies in the real world, with the player's skin, for as long
 * as they are connected. It has no item of its own (the helmet places it and takes it back) and nobody can hurt it;
 * only a creative-mode player removes it by hand, and the helmet it was holding drops.
 */
public class SleepingBody extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(SleepingBody.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(SleepingBody.class, EntityDataSerializers.INT);
    private static final String OWNER_KEY = "owner";
    private static final String TIER_KEY = "tier";
    /** How often (ticks) a body checks that its owner is still connected to it. */
    private static final int CHECK_INTERVAL = 20;

    public SleepingBody(EntityType<? extends SleepingBody> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Lies a body of {@code tier} down at the centre of {@code pos}, head towards {@code yaw}. */
    public static SleepingBody spawn(ServerLevel level, BlockPos pos, VrHelmetTier tier, UUID owner, float yaw) {
        SleepingBody body = new SleepingBody(ModEntities.SLEEPING_BODY.get(), level);
        body.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);
        body.entityData.set(OWNER, Optional.of(owner));
        body.entityData.set(TIER, tier.ordinal());
        level.addFreshEntity(body);
        return body;
    }

    /** The bodies lying at {@code pos} (normally none or one). */
    public static List<SleepingBody> at(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(SleepingBody.class, new AABB(pos).inflate(0.5));
    }

    public Optional<UUID> owner() {
        return entityData.get(OWNER);
    }

    public VrHelmetTier tier() {
        VrHelmetTier[] tiers = VrHelmetTier.values();
        return tiers[Math.max(0, Math.min(tiers.length - 1, entityData.get(TIER)))];
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, Optional.empty());
        builder.define(TIER, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID(OWNER_KEY)) {
            entityData.set(OWNER, Optional.of(tag.getUUID(OWNER_KEY)));
        }
        entityData.set(TIER, tag.getInt(TIER_KEY));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        owner().ifPresent(uuid -> tag.putUUID(OWNER_KEY, uuid));
        tag.putInt(TIER_KEY, entityData.get(TIER));
    }

    /**
     * A body whose owner is online but no longer connected to it (the disconnection could not find it because its chunk was
     * not loaded, a command, a crash) goes away. An offline owner's body stays: the server is still starting, or it is theirs.
     */
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % CHECK_INTERVAL == 1 && level() instanceof ServerLevel server) {
            ServerPlayer player = owner().map(uuid -> server.getServer().getPlayerList().getPlayer(uuid)).orElse(null);
            if (owner().isEmpty() || player != null && !VrHelmetService.isBodyOf(player, this)) {
                discard();
            }
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !(source.getEntity() instanceof Player player && player.isCreative());
    }

    /** Only a creative-mode player breaks a body; the helmet it was holding drops, bound to its owner. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && source.getEntity() instanceof Player player && player.isCreative()) {
            spawnAtLocation(HelmetItem.stackFor(tier(), owner()));
            discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return true;
    }
}
