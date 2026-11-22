package com.orijua.mitofus.feature.sancho;

import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.mob.BossMob;
import com.orijua.mitofus.feature.mob.MobLevel;
import com.orijua.mitofus.feature.mob.MobLevelService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Conde Nuno and his knights. In his manor
 * they are peaceful and can't be hurt; once a player appraises Sancho's book they hunt that player
 * ({@link SanchoStory#isNunosEnemy}), and only that player can hurt them. Their strength is fixed ({@link BossMob}), and
 * the level they show is the story one. Those summoned by the betrayal go home (vanish) after two minutes without an
 * enemy in sight.
 */
public abstract class Mesnada extends PathfinderMob implements BossMob {
    /** Two minutes without anyone to hunt and the summoned ones leave. */
    static final int GIVE_UP_TICKS = 20 * 120;

    private boolean summoned;
    private int ticksWithoutEnemy;

    protected Mesnada(EntityType<? extends Mesnada> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    /** The level shown over its head. */
    protected abstract int storyLevel();

    /** The goal it fights with, at priority 2. */
    protected abstract void registerAttackGoal();

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        registerAttackGoal();
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
                target -> target instanceof Player player && SanchoStory.isNunosEnemy(player)));
    }

    public boolean isSummoned() {
        return summoned;
    }

    public void setSummoned(boolean summoned) {
        this.summoned = summoned;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!MobLevelService.get(this).isLeveled()) {
            setData(ModAttachments.MOB_LEVEL, new MobLevel(storyLevel(), false));
        }
        if (getTarget() instanceof Player player && !SanchoStory.isNunosEnemy(player)) {
            setTarget(null); // Nuno is dead for them, or they are in creative now
        }
        ticksWithoutEnemy = getTarget() != null ? 0 : ticksWithoutEnemy + 1;
        if (summoned && ticksWithoutEnemy > GIVE_UP_TICKS) {
            discard();
        }
    }

    /** Only the player they hunt (or a creative player, or /kill) can hurt them. */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return !(source.getEntity() instanceof Player player && (player.isCreative() || SanchoStory.isNunosEnemy(player)));
    }

    /** Nuno's spells don't hit his own knights. */
    @Override
    public boolean isAlliedTo(Entity other) {
        return other instanceof Mesnada || super.isAlliedTo(other);
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Summoned", summoned);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        summoned = tag.getBoolean("Summoned");
    }
}
