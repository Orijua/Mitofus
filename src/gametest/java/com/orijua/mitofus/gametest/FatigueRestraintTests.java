package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.FatigueBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetService;
import com.orijua.mitofus.feature.fatigue.FatigueRestraint;
import com.orijua.mitofus.feature.fatigue.FatigueService;
import com.orijua.mitofus.feature.progression.ClassService;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.skill.SkillService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The safety protocol of the fatigue holds the player still: no moving, no hitting, no shooting, no attack skills, no mining,
 * no placing blocks while the grace period runs. Fake players do not tick, so the tests call {@link FatigueRestraint#enforce}
 * and post the events by hand; the block tests combine those posted events with one real control
 * ({@code realMiningAndPlacingAreRefusedWhileRestrained}, through the player's game mode).
 */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class FatigueRestraintTests {
    private static final ResourceLocation SANCHO_LINK = Mitofus.id("sancho_link");
    private static final ResourceLocation IRON_GUARD = Mitofus.id("iron_guard");
    private static final ResourceLocation FLAME_BOLT = Mitofus.id("flame_bolt");

    private FatigueRestraintTests() {
    }

    private static void in(GameTestHelper helper, boolean mitofus, Runnable body) {
        TestSupport.inFatigue(helper, mitofus, body);
    }

    /** Runs out of energy and lets the first second of the grace period start. */
    private static void enterGrace(ServerPlayer player) {
        FatigueService.setEnergy(player, 0.0);
        FatigueService.tickSecond(player);
    }

    /** Gets the energy back and lets a second go by: the grace period ends. */
    private static void rest(ServerPlayer player) {
        FatigueService.setEnergy(player, 0.5);
        FatigueService.tickSecond(player);
    }

    private static void inMitofusWithDefaults(GameTestHelper helper, BlockPos covadonga, Runnable body) {
        TestSupport.inMitofusWithFatigueDefaults(helper, covadonga, body);
    }

    /** Aims the player level towards +Z, where the test mobs stand. */
    private static void faceForward(ServerPlayer player) {
        player.setYRot(0f);
        player.setXRot(0f);
        player.setYHeadRot(0f);
    }

    private static boolean cancelled(Event event) {
        NeoForge.EVENT_BUS.post(event);
        return event instanceof ICancellableEvent cancellable && cancellable.isCanceled();
    }

    private static boolean hitCancelled(LivingEntity victim, DamageSource source) {
        return cancelled(new LivingIncomingDamageEvent(victim, new DamageContainer(source, 5.0f)));
    }

    private static List<SmallFireball> fireballsAround(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getEntitiesOfClass(SmallFireball.class, new AABB(player.blockPosition()).inflate(8));
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void restrainedOnlyDuringTheGracePeriod(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        in(helper, true, () -> {
            FatigueService.setEnergy(player, 0.5);
            helper.assertFalse(FatigueRestraint.isRestrained(player), "free before");
            enterGrace(player);
            helper.assertTrue(FatigueRestraint.isRestrained(player), "held in the grace period");
            rest(player);
            helper.assertFalse(FatigueService.isInGrace(player), "no longer in the grace period with energy");
            helper.assertFalse(FatigueRestraint.isRestrained(player), "free again with energy");
            enterGrace(player);
            try {
                player.setGameMode(GameType.CREATIVE);
                helper.assertFalse(FatigueRestraint.isRestrained(player), "creative is never held");
                player.setGameMode(GameType.SPECTATOR);
                helper.assertFalse(FatigueRestraint.isRestrained(player), "nor spectator");
                player.setGameMode(GameType.ADVENTURE);
                helper.assertTrue(FatigueRestraint.isRestrained(player), "adventure is held like survival");
            } finally {
                player.setGameMode(GameType.SURVIVAL);
            }
            helper.assertTrue(FatigueRestraint.isRestrained(player), "survival again: held");
            try {
                player.setHealth(0f);
                helper.assertFalse(FatigueRestraint.isRestrained(player), "a dead player is not held");
            } finally {
                player.setHealth(player.getMaxHealth());
            }
            helper.assertTrue(FatigueRestraint.isRestrained(player), "alive again: held");
        });
        in(helper, false, () -> helper.assertFalse(FatigueRestraint.isRestrained(player), "never in the real world"));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aRestrainedPlayerCannotHit(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(0.5, 1, 2.5));
        zombie.setNoAi(true);
        in(helper, true, () -> {
            helper.assertFalse(cancelled(new AttackEntityEvent(player, zombie)), "free: the attack goes through");
            helper.assertFalse(hitCancelled(zombie, player.damageSources().playerAttack(player)), "free: the damage goes through");
            enterGrace(player);
            helper.assertTrue(cancelled(new AttackEntityEvent(player, zombie)), "held: the attack is cancelled");
            helper.assertTrue(hitCancelled(zombie, player.damageSources().playerAttack(player)), "held: the melee damage is cancelled");
            Snowball ball = new Snowball(helper.getLevel(), player);
            helper.assertTrue(hitCancelled(zombie, player.damageSources().thrown(ball, player)), "held: a projectile of theirs does nothing");
            helper.assertFalse(hitCancelled(zombie, player.damageSources().generic()), "damage of nobody still counts");
            helper.assertTrue(FatigueService.isProtected(player), "and they stay protected");
        });
        helper.succeed();
    }

    /**
     * The damage a player does to themselves is not the restraint's business. The grace protection would cancel most of it too,
     * so the source bypasses invulnerability (generic_kill) to tell the two apart; the same source against a zombie is cancelled.
     */
    @GameTest(template = TestSupport.EMPTY)
    public static void selfDamageIsNotCancelledByTheRestraint(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(0.5, 1, 2.5));
        zombie.setNoAi(true);
        in(helper, true, () -> {
            enterGrace(player);
            DamageSource bypassing = player.damageSources().source(DamageTypes.GENERIC_KILL, player);
            helper.assertTrue(FatigueRestraint.isRestrained(player), "held (setup)");
            helper.assertTrue(hitCancelled(zombie, bypassing), "control: held, the same source against a zombie is cancelled");
            helper.assertFalse(hitCancelled(player, bypassing), "held: damage to themselves is not cancelled");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aRestrainedPlayerCannotUseItems(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ItemStack[] stacks = {new ItemStack(Items.BOW), new ItemStack(Items.SNOWBALL), new ItemStack(Items.TRIDENT)};
        in(helper, true, () -> {
            for (ItemStack stack : stacks) {
                assertItemUse(helper, player, stack, 72000, false, "free");
            }
            enterGrace(player);
            for (ItemStack stack : stacks) {
                assertItemUse(helper, player, stack, 72000, true, "held");
            }
        });
        helper.succeed();
    }

    /** Right click and start of use of an item: both cancelled (and the click with FAIL) or both let through. */
    private static void assertItemUse(GameTestHelper helper, ServerPlayer player, ItemStack stack, int duration,
                                      boolean refused, String when) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var click = new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(cancelled(click) == refused, when + ": " + stack + " right click " + (refused ? "must be cancelled" : "must go through"));
        if (refused) {
            helper.assertTrue(click.getCancellationResult() == InteractionResult.FAIL, when + ": " + stack + " right click with a FAIL result");
        }
        helper.assertTrue(cancelled(new LivingEntityUseItemEvent.Start(player, stack, InteractionHand.MAIN_HAND, duration)) == refused,
                when + ": " + stack + " use " + (refused ? "must be cancelled" : "must go through"));
    }

    /** Food is refused too: final decision (BUG-087), it pins {@code FatigueRestraint.onUseItem}. */
    @GameTest(template = TestSupport.EMPTY)
    public static void foodIsRefusedWhileRestrained(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ItemStack[] foods = {new ItemStack(Items.BREAD), new ItemStack(Items.POTION), new ItemStack(Items.MILK_BUCKET)};
        in(helper, true, () -> {
            for (ItemStack food : foods) {
                assertItemUse(helper, player, food, 32, false, "free");
            }
            enterGrace(player);
            for (ItemStack food : foods) {
                assertItemUse(helper, player, food, 32, true, "held");
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aZombieUsingAnItemIsNotCancelled(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(0.5, 1, 2.5));
        zombie.setNoAi(true);
        in(helper, true, () -> {
            enterGrace(player);
            helper.assertTrue(FatigueRestraint.isRestrained(player), "a player is held (setup)");
            helper.assertFalse(cancelled(new LivingEntityUseItemEvent.Start(zombie, new ItemStack(Items.BOW), InteractionHand.MAIN_HAND, 72000)),
                    "a zombie drawing a bow is not cancelled");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void attackSkillsAreRefusedButBuffsAreNot(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        faceForward(player); // looks towards +Z: the zombie stands 2 blocks ahead
        SkillService.learn(player, SANCHO_LINK);
        SkillService.learn(player, IRON_GUARD);
        player.setData(ModAttachments.MANA, 1000f);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(0.5, 1, 2.5));
        zombie.setNoAi(true);
        // The pause between skills would refuse the casts that follow each other in one tick.
        com.orijua.mitofus.core.config.SkillBalance.withOverride(new com.orijua.mitofus.core.config.SkillBalance(0), () ->
        in(helper, true, () -> {
            enterGrace(player);
            helper.assertTrue(SkillService.use(player, 0) == SkillService.Result.RESTRAINED, "held: sancho_link is refused");
            helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(), "the zombie is untouched");
            helper.assertTrue(player.getData(ModAttachments.MANA) == 1000f, "no mana spent");
            helper.assertTrue(SkillService.get(player).cooldownRemaining(SANCHO_LINK, helper.getLevel().getGameTime()) == 0, "no cooldown started");
            helper.assertTrue(SkillService.use(player, 1) == SkillService.Result.SUCCESS, "held: a buff is allowed");
            rest(player);
            helper.assertTrue(SkillService.use(player, 0) == SkillService.Result.SUCCESS, "free again: sancho_link casts");
            helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "free: sancho_link hits the zombie (so the untouched check above proves something)");
        }));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void flameBoltIsRefusedWhileRestrained(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        faceForward(player);
        SkillService.learn(player, FLAME_BOLT);
        int slot = SkillService.get(player).slots().indexOf(Optional.of(FLAME_BOLT));
        helper.assertTrue(slot >= 0, "flame_bolt got a slot (setup)");
        player.setData(ModAttachments.MANA, 1000f);
        try {
            in(helper, true, () -> {
                enterGrace(player);
                helper.assertTrue(SkillService.use(player, slot) == SkillService.Result.RESTRAINED, "held: flame_bolt is refused");
                helper.assertTrue(player.getData(ModAttachments.MANA) == 1000f, "no mana spent");
                helper.assertTrue(SkillService.get(player).cooldownRemaining(FLAME_BOLT, helper.getLevel().getGameTime()) == 0, "no cooldown started");
                helper.assertTrue(fireballsAround(helper, player).isEmpty(), "no fireball was shot");
                rest(player);
                helper.assertTrue(SkillService.use(player, slot) == SkillService.Result.SUCCESS, "free again: flame_bolt casts");
                helper.assertFalse(fireballsAround(helper, player).isEmpty(), "free: fireballs were shot (so the empty check above proves something)");
            });
        } finally {
            fireballsAround(helper, player).forEach(SmallFireball::discard);
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theWeaponSkillIsRefusedWhileRestrained(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.grantJob(player, GuadaleteTests.SUCCESSOR);
        GuadaleteTests.hold(player, GuadaleteTests.forgedGuadalete(helper, player, ItemRating.LEGENDARY));
        GuadaleteTests.meetRequirements(player);
        player.setData(ModAttachments.MANA, 1000f);
        in(helper, true, () -> {
            enterGrace(player);
            helper.assertTrue(SkillService.use(player, SkillService.WEAPON_SLOT) == SkillService.Result.RESTRAINED, "held: Bisection is refused");
            helper.assertTrue(player.getData(ModAttachments.MANA) == 1000f, "no mana spent");
            helper.assertTrue(SkillService.get(player).cooldownRemaining(GuadaleteTests.BISECTION, helper.getLevel().getGameTime()) == 0, "no cooldown started");
            rest(player);
            helper.assertTrue(SkillService.use(player, SkillService.WEAPON_SLOT) == SkillService.Result.SUCCESS, "free again: Bisection casts");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void restrainedPlayerCannotMountAndStopsGliding(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(0.5, 1, 0.5));
        try {
            in(helper, true, () -> {
                // A fake player cannot ride (FakePlayer.startRiding is a no-op), so the dismount itself is left to a manual test.
                helper.assertFalse(cancelled(new EntityMountEvent(player, pig, helper.getLevel(), true)), "free: mounting is allowed");
                enterGrace(player);
                FatigueRestraint.enforce(player);
                helper.assertTrue(cancelled(new EntityMountEvent(player, pig, helper.getLevel(), true)), "held: mounting is cancelled");
                helper.assertFalse(cancelled(new EntityMountEvent(player, pig, helper.getLevel(), false)), "dismounting is never refused");

                player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
                player.startFallFlying();
                helper.assertTrue(player.isFallFlying(), "gliding (setup)");
                FatigueRestraint.enforce(player);
                helper.assertFalse(player.isFallFlying(), "held: glide stopped");
            });
        } finally {
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void enforceCutsSprintingAndUsingAnItem(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        in(helper, true, () -> {
            // Before the grace period: LivingEntityUseItemEvent.Start would be cancelled once the player is held.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
            player.setSprinting(true);
            player.startUsingItem(InteractionHand.MAIN_HAND);
            helper.assertTrue(player.isSprinting(), "sprinting (setup)");
            helper.assertTrue(player.isUsingItem(), "drawing the bow (setup)");
            enterGrace(player);
            FatigueRestraint.enforce(player);
            helper.assertFalse(player.isSprinting(), "held: sprint cut");
            helper.assertFalse(player.isUsingItem(), "held: the item use is cut");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void releasingABowOrTridentIsRefusedWhileHeld(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        in(helper, true, () -> {
            ItemStack bow = new ItemStack(Items.BOW);
            helper.assertFalse(cancelled(new LivingEntityUseItemEvent.Stop(player, bow, 10)), "free: the bow can be released");
            enterGrace(player);
            helper.assertTrue(cancelled(new LivingEntityUseItemEvent.Stop(player, bow, 10)), "held: the bow release is cancelled");
            helper.assertTrue(cancelled(new LivingEntityUseItemEvent.Stop(player, new ItemStack(Items.TRIDENT), 10)),
                    "held: the trident release is cancelled");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void serverHoldsAHeldPlayerStill(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        in(helper, true, () -> {
            enterGrace(player);
            FatigueRestraint.enforce(player);
            Vec3 start = player.position();
            // A modified client keeps walking: the motion is zeroed (falling is kept).
            player.setDeltaMovement(0.3, -0.2, 0.3);
            FatigueRestraint.enforce(player);
            helper.assertTrue(player.getDeltaMovement().x == 0.0 && player.getDeltaMovement().z == 0.0, "held: horizontal motion zeroed");
            helper.assertTrue(player.getDeltaMovement().y == -0.2, "held: falling is not touched");
            // Past the leeway it is put back where the hold began.
            player.setPos(start.x + 3.0, start.y, start.z);
            FatigueRestraint.enforce(player);
            helper.assertTrue(player.position().distanceTo(start) < 0.01, "held: put back where the hold began");
            // Free again: nothing is zeroed.
            rest(player);
            player.setDeltaMovement(0.3, 0.0, 0.3);
            FatigueRestraint.enforce(player);
            helper.assertTrue(player.getDeltaMovement().x == 0.3, "free: the motion is left alone");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void quittingKeepsTheRestraintAndRespawningFreesThePlayer(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        FatigueService.setClock(() -> 1_000_000L);
        try {
            in(helper, true, () -> {
                enterGrace(player);
                FatigueService.onLogout(player);
                helper.assertTrue(player.getData(ModAttachments.FATIGUE_AWAY_SINCE.get()) > 0L, "leaving remembers when");
                helper.assertTrue(FatigueRestraint.isRestrained(player), "after leaving: still held");
                FatigueService.onLogin(player);
                helper.assertTrue(player.getData(ModAttachments.FATIGUE_AWAY_SINCE.get()) == 0L, "coming back went through the login branch");
                helper.assertTrue(FatigueRestraint.isRestrained(player), "after coming back: still held (we are not in the real world)");
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player, false));
                helper.assertFalse(FatigueRestraint.isRestrained(player), "respawning frees them");
            });
        } finally {
            FatigueService.restoreClock();
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void changingDimensionFreesThePlayer(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        in(helper, true, () -> {
            enterGrace(player);
            helper.assertTrue(FatigueRestraint.isRestrained(player), "held (setup)");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD, Level.NETHER));
            helper.assertFalse(FatigueRestraint.isRestrained(player), "free after changing dimension");
            helper.assertTrue(FatigueService.graceLeft(player) == -1, "with no grace left");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void turningTheFatigueOffFreesThePlayer(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        FatigueBalance off = FatigueBalance.DEFAULT.withEnabled(false);
        in(helper, true, () -> {
            enterGrace(player);
            // Turning it off by hand changes nothing until the next second: the restraint is deduced from the saved grace.
            helper.assertTrue(FatigueRestraint.isRestrained(player), "held (setup)");
            FatigueBalance.withOverride(off, () -> FatigueService.tickSecond(player));
            helper.assertFalse(FatigueRestraint.isRestrained(player), "free once the fatigue is off");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void withNoGracePeriodThereIsNothingToRestrain(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlockPos covadonga = helper.absolutePos(new BlockPos(1, 1, 1));
        GlobalPos vrhelmet = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(0, 1, 0)));
        FatigueBalance noGrace = new FatigueBalance(true, 40, 0.5, 2, 20, 10, 0.1, 0, List.of(0.25), 0.3, 1.0);
        inMitofusWithDefaults(helper, covadonga, () -> {
            helper.assertTrue(VrHelmetService.connect(player, vrhelmet), "connected");
            FatigueService.setEnergy(player, 0.0);
            FatigueBalance.withOverride(noGrace, () -> FatigueService.tickSecond(player));
            helper.assertFalse(FatigueRestraint.isRestrained(player), "never held");
            helper.assertTrue(FatigueService.graceLeft(player) == -1, "no grace left over");
        });
        helper.succeed();
    }

    /** Fires every mining and placing event at a test position; per event name, whether it was refused. */
    private static Map<String, Boolean> blockEventsRefused(GameTestHelper helper, ServerPlayer player) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState stone = Blocks.STONE.defaultBlockState();
        var results = new LinkedHashMap<String, Boolean>();
        results.put("BreakEvent", cancelled(new BlockEvent.BreakEvent(helper.getLevel(), pos, stone, player)));
        results.put("LeftClickBlock", cancelled(new PlayerInteractEvent.LeftClickBlock(player, pos, Direction.UP,
                PlayerInteractEvent.LeftClickBlock.Action.START)));
        var speed = new PlayerEvent.BreakSpeed(player, stone, 4.0f, pos);
        NeoForge.EVENT_BUS.post(speed);
        results.put("BreakSpeed", speed.getNewSpeed() == 0.0f);
        BlockSnapshot snapshot = BlockSnapshot.create(helper.getLevel().dimension(), helper.getLevel(), pos);
        results.put("EntityPlaceEvent", cancelled(new BlockEvent.EntityPlaceEvent(snapshot, stone, player)));
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var click = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        results.put("RightClickBlock", cancelled(click) && click.getCancellationResult() == InteractionResult.FAIL);
        return results;
    }

    private static void assertBlockEvents(GameTestHelper helper, ServerPlayer player, boolean expectedRefused, String when) {
        List<String> failed = new ArrayList<>();
        blockEventsRefused(helper, player).forEach((name, refused) -> {
            if (refused != expectedRefused) {
                failed.add(name);
            }
        });
        helper.assertTrue(failed.isEmpty(), when + ": " + failed + (expectedRefused ? " must be refused" : " must go through"));
    }

    /** Refused = cancelled with FAIL; clean = not cancelled at all (not just "not FAIL"). */
    private static boolean entityEventOk(boolean expectedRefused, boolean cancelled, InteractionResult result) {
        return expectedRefused ? cancelled && result == InteractionResult.FAIL : !cancelled;
    }

    /** Fires both entity interaction events and hooks at a target; per name, whether they behaved as expected. */
    private static Map<String, Boolean> entityEventsBehaved(Entity target, ServerPlayer player, InteractionHand hand,
                                                            boolean expectedRefused) {
        var results = new LinkedHashMap<String, Boolean>();
        var plain = new PlayerInteractEvent.EntityInteract(player, hand, target);
        boolean plainCancelled = cancelled(plain);
        results.put("EntityInteract", entityEventOk(expectedRefused, plainCancelled, plain.getCancellationResult()));
        var specific = new PlayerInteractEvent.EntityInteractSpecific(player, hand, target, Vec3.ZERO);
        boolean specificCancelled = cancelled(specific);
        results.put("EntityInteractSpecific", entityEventOk(expectedRefused, specificCancelled, specific.getCancellationResult()));
        // The hooks the server calls when a client interaction packet arrives (null = nobody cancelled).
        InteractionResult hookPlain = CommonHooks.onInteractEntity(player, target, hand);
        results.put("hook EntityInteract", expectedRefused ? hookPlain == InteractionResult.FAIL : hookPlain == null);
        InteractionResult hookSpecific = CommonHooks.onInteractEntityAt(player, target, Vec3.ZERO, hand);
        results.put("hook EntityInteractSpecific", expectedRefused ? hookSpecific == InteractionResult.FAIL : hookSpecific == null);
        return results;
    }

    private static void assertEntityEvents(GameTestHelper helper, Entity target, ServerPlayer player, boolean expectedRefused,
                                           String when) {
        assertEntityEvents(helper, target, player, InteractionHand.MAIN_HAND, expectedRefused, when);
    }

    private static void assertEntityEvents(GameTestHelper helper, Entity target, ServerPlayer player, InteractionHand hand,
                                           boolean expectedRefused, String when) {
        List<String> failed = new ArrayList<>();
        entityEventsBehaved(target, player, hand, expectedRefused).forEach((name, ok) -> {
            if (!ok) {
                failed.add(name);
            }
        });
        helper.assertTrue(failed.isEmpty(), when + " (" + hand + ", isInGrace=" + FatigueService.isInGrace(player)
                + ", graceLeft=" + FatigueService.graceLeft(player) + ", isRestrained=" + FatigueRestraint.isRestrained(player)
                + "): " + failed + (expectedRefused ? " must be refused with FAIL" : " must go through untouched"));
    }

    /** Lets the grace period run out by itself (the player is forced out), then reconnects with energy: protected, not held. */
    private static void reconnectAfterForcedOut(GameTestHelper helper, ServerPlayer player, GlobalPos vrhelmet) {
        int limit = FatigueBalance.DEFAULT.graceSeconds() + 5;
        int guard = 0;
        while (FatigueService.isInGrace(player) && guard++ < limit) {
            FatigueService.tickSecond(player);
        }
        helper.assertTrue(guard <= limit, "the grace period ended by itself, not by the safety limit");
        helper.assertFalse(VrHelmetService.state(player).isConnected(), "forced out");
        FatigueService.setEnergy(player, 0.5);
        helper.assertTrue(VrHelmetService.connect(player, vrhelmet), "connected again");
        helper.assertTrue(FatigueService.isProtected(player), "protected for a few seconds");
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void breakSpeedIsZeroWhileRestrainedAndNotCancelled(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState stone = Blocks.STONE.defaultBlockState();
        in(helper, true, () -> {
            var free = new PlayerEvent.BreakSpeed(player, stone, 4.0f, pos);
            NeoForge.EVENT_BUS.post(free);
            helper.assertTrue(free.getNewSpeed() == 4.0f, "free: the original speed");
            enterGrace(player);
            var held = new PlayerEvent.BreakSpeed(player, stone, 4.0f, pos);
            NeoForge.EVENT_BUS.post(held);
            helper.assertTrue(held.getNewSpeed() == 0.0f, "held: speed zero");
            helper.assertFalse(held.isCanceled(), "held: the event is not cancelled, only the speed is zeroed");
        });
        helper.succeed();
    }

    /** Control with the real server path (the player's game mode), not only posted events. */
    @GameTest(template = TestSupport.EMPTY)
    public static void realMiningAndPlacingAreRefusedWhileRestrained(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlockPos support = new BlockPos(1, 0, 1);
        BlockPos target = new BlockPos(1, 1, 1);
        BlockPos absSupport = helper.absolutePos(support);
        BlockPos absTarget = helper.absolutePos(target);
        try {
            in(helper, true, () -> {
                // Mining
                helper.setBlock(target, Blocks.STONE);
                enterGrace(player);
                helper.assertFalse(player.gameMode.destroyBlock(absTarget), "held: destroyBlock refuses");
                helper.assertTrue(helper.getLevel().getBlockState(absTarget).is(Blocks.STONE), "held: the stone is still there");
                rest(player);
                helper.assertTrue(player.gameMode.destroyBlock(absTarget), "free: destroyBlock works");
                helper.assertTrue(helper.getLevel().getBlockState(absTarget).isAir(), "free: the stone is gone");

                // Placing
                helper.setBlock(support, Blocks.STONE);
                ItemStack cobble = new ItemStack(Items.COBBLESTONE, 4);
                player.setItemInHand(InteractionHand.MAIN_HAND, cobble);
                var hit = new BlockHitResult(Vec3.atCenterOf(absSupport).add(0, 0.5, 0), Direction.UP, absSupport, false);
                enterGrace(player);
                var held = player.gameMode.useItemOn(player, helper.getLevel(), cobble, InteractionHand.MAIN_HAND, hit);
                helper.assertFalse(held.consumesAction(), "held: placing does not consume the action (" + held + ")");
                helper.assertTrue(helper.getLevel().getBlockState(absTarget).isAir(), "held: nothing was placed");
                helper.assertTrue(cobble.getCount() == 4, "held: the stack did not go down");
                rest(player);
                var free = player.gameMode.useItemOn(player, helper.getLevel(), cobble, InteractionHand.MAIN_HAND, hit);
                helper.assertTrue(helper.getLevel().getBlockState(absTarget).is(Blocks.COBBLESTONE),
                        "free: the cobblestone was placed (result " + free + ", state " + helper.getLevel().getBlockState(absTarget)
                                + ", support " + helper.getLevel().getBlockState(absSupport) + ", count " + cobble.getCount() + ")");
            });
        } finally {
            helper.setBlock(target, Blocks.AIR);
            helper.setBlock(support, Blocks.AIR);
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void miningAndPlacingFollowTheGameModeAndLife(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        in(helper, true, () -> {
            enterGrace(player);
            try {
                player.setGameMode(GameType.ADVENTURE);
                assertBlockEvents(helper, player, true, "adventure");
            } finally {
                player.setGameMode(GameType.SURVIVAL);
            }
            try {
                player.setHealth(0f);
                assertBlockEvents(helper, player, false, "dead");
            } finally {
                player.setHealth(player.getMaxHealth());
            }
            assertBlockEvents(helper, player, true, "alive again (control)");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void anotherPlayerStaysFreeToMineAndPlace(GameTestHelper helper) {
        ServerPlayer held = TestSupport.fakePlayer(helper);
        ServerPlayer other = TestSupport.fakePlayer(helper);
        in(helper, true, () -> {
            helper.assertTrue(held != other, "two different players (setup)");
            enterGrace(held);
            helper.assertFalse(FatigueRestraint.isRestrained(other), "the other one is not held (setup)");
            helper.assertFalse(FatigueService.isInGrace(other), "the other one is not in grace (setup)");
            assertBlockEvents(helper, other, false, "the other player");
            assertBlockEvents(helper, held, true, "the held player (control)");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void placingWithNoEntityIsNotCancelled(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        in(helper, true, () -> {
            enterGrace(player);
            BlockSnapshot snapshot = BlockSnapshot.create(helper.getLevel().dimension(), helper.getLevel(), pos);
            helper.assertFalse(cancelled(new BlockEvent.EntityPlaceEvent(snapshot, Blocks.STONE.defaultBlockState(), null)),
                    "a block placed by no entity (a dispenser) is not cancelled");
        });
        helper.succeed();
    }

    /** Pins the current behavior: every left click action is cancelled while held (BUG-089 point 2 may change it). */
    @GameTest(template = TestSupport.EMPTY)
    public static void everyLeftClickActionIsCancelledWhileRestrained(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        in(helper, true, () -> {
            enterGrace(player);
            for (var action : PlayerInteractEvent.LeftClickBlock.Action.values()) {
                helper.assertTrue(cancelled(new PlayerInteractEvent.LeftClickBlock(player, pos, Direction.UP, action)),
                        "held: LeftClickBlock " + action + " is cancelled");
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aRestrainedPlayerCannotMineOrPlace(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        in(helper, true, () -> {
            assertBlockEvents(helper, player, false, "free");
            enterGrace(player);
            assertBlockEvents(helper, player, true, "held");
            rest(player);
            assertBlockEvents(helper, player, false, "free again");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void miningAndPlacingAreFreeInCreativeSpectatorAndTheRealWorld(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        in(helper, true, () -> {
            enterGrace(player);
            try {
                player.setGameMode(GameType.CREATIVE);
                assertBlockEvents(helper, player, false, "creative");
                player.setGameMode(GameType.SPECTATOR);
                assertBlockEvents(helper, player, false, "spectator");
            } finally {
                player.setGameMode(GameType.SURVIVAL);
            }
            assertBlockEvents(helper, player, true, "survival again (control)");
        });
        in(helper, false, () -> assertBlockEvents(helper, player, false, "real world"));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void miningAndPlacingAreFreeDuringTheProtectionAfterReconnecting(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlockPos covadonga = helper.absolutePos(new BlockPos(1, 1, 1));
        GlobalPos vrhelmet = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(0, 1, 0)));
        inMitofusWithDefaults(helper, covadonga, () -> {
            helper.assertTrue(VrHelmetService.connect(player, vrhelmet), "connected");
            enterGrace(player);
            assertBlockEvents(helper, player, true, "in the grace period (control)");
            reconnectAfterForcedOut(helper, player, vrhelmet);
            assertBlockEvents(helper, player, false, "protected");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void protectionAfterReconnectingDoesNotRestrain(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlockPos covadonga = helper.absolutePos(new BlockPos(1, 1, 1));
        GlobalPos vrhelmet = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(0, 1, 0)));
        inMitofusWithDefaults(helper, covadonga, () -> {
            helper.assertTrue(VrHelmetService.connect(player, vrhelmet), "connected");
            enterGrace(player);
            helper.assertTrue(FatigueService.isInGrace(player), "in the grace period (control)");
            helper.assertTrue(FatigueRestraint.isRestrained(player), "and held (control)");
            reconnectAfterForcedOut(helper, player, vrhelmet);
            helper.assertFalse(FatigueService.isInGrace(player), "not in a grace period");
            helper.assertTrue(FatigueService.graceLeft(player) == -1, "with no grace left");
            helper.assertFalse(FatigueRestraint.isRestrained(player), "but free to move");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void entityInteractionIsRefusedOnlyDuringTheGracePeriod(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Villager villager = helper.spawn(EntityType.VILLAGER, new Vec3(0.5, 1, 2.5));
        in(helper, true, () -> {
            assertEntityEvents(helper, villager, player, false, "before the grace period");
            enterGrace(player);
            assertEntityEvents(helper, villager, player, true, "in the grace period");
            rest(player);
            assertEntityEvents(helper, villager, player, false, "free again");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void entityInteractionIsFreeInCreativeSpectatorAndTheRealWorld(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Villager villager = helper.spawn(EntityType.VILLAGER, new Vec3(0.5, 1, 2.5));
        in(helper, true, () -> {
            enterGrace(player);
            try {
                player.setGameMode(GameType.CREATIVE);
                assertEntityEvents(helper, villager, player, false, "creative");
                player.setGameMode(GameType.SPECTATOR);
                assertEntityEvents(helper, villager, player, false, "spectator");
            } finally {
                player.setGameMode(GameType.SURVIVAL);
            }
            assertEntityEvents(helper, villager, player, true, "survival again (control)");
        });
        in(helper, false, () -> assertEntityEvents(helper, villager, player, false, "real world"));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void entityInteractionIsFreeDuringTheProtectionAfterReconnecting(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Villager villager = helper.spawn(EntityType.VILLAGER, new Vec3(0.5, 1, 2.5));
        BlockPos covadonga = helper.absolutePos(new BlockPos(1, 1, 1));
        GlobalPos vrhelmet = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(0, 1, 0)));
        inMitofusWithDefaults(helper, covadonga, () -> {
            helper.assertTrue(VrHelmetService.connect(player, vrhelmet), "connected");
            enterGrace(player);
            assertEntityEvents(helper, villager, player, true, "in the grace period (control)");
            reconnectAfterForcedOut(helper, player, vrhelmet);
            assertEntityEvents(helper, villager, player, false, "protected");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void anotherPlayerStaysFreeToInteractWithEntities(GameTestHelper helper) {
        ServerPlayer held = TestSupport.fakePlayer(helper);
        ServerPlayer other = TestSupport.fakePlayer(helper);
        Villager villager = helper.spawn(EntityType.VILLAGER, new Vec3(0.5, 1, 2.5));
        in(helper, true, () -> {
            helper.assertTrue(held != other, "two different players (setup)");
            enterGrace(held);
            helper.assertFalse(FatigueRestraint.isRestrained(other), "the other one is not held (setup)");
            helper.assertFalse(FatigueService.isInGrace(other), "the other one is not in grace (setup)");
            assertEntityEvents(helper, villager, other, false, "the other player");
            assertEntityEvents(helper, villager, held, true, "the held player (control)");
        });
        helper.succeed();
    }

    /** Two players in grace at once: one recovers and is free, the other stays held. */
    @GameTest(template = TestSupport.EMPTY)
    public static void oneRecoversWhileTheOtherStaysHeld(GameTestHelper helper) {
        ServerPlayer first = TestSupport.fakePlayer(helper);
        ServerPlayer second = TestSupport.fakePlayer(helper);
        Villager villager = helper.spawn(EntityType.VILLAGER, new Vec3(0.5, 1, 2.5));
        in(helper, true, () -> {
            enterGrace(first);
            enterGrace(second);
            helper.assertTrue(FatigueRestraint.isRestrained(first) && FatigueRestraint.isRestrained(second), "both held (setup)");
            assertEntityEvents(helper, villager, first, true, "first, both in grace");
            assertEntityEvents(helper, villager, second, true, "second, both in grace");
            rest(first);
            helper.assertFalse(FatigueRestraint.isRestrained(first), "the first one recovered");
            helper.assertTrue(FatigueRestraint.isRestrained(second), "the second one is still held");
            assertEntityEvents(helper, villager, first, false, "first, recovered");
            assertEntityEvents(helper, villager, second, true, "second, still in grace");
            assertBlockEvents(helper, first, false, "first, recovered (blocks)");
            assertBlockEvents(helper, second, true, "second, still in grace (blocks)");
        });
        helper.succeed();
    }

    /** Opens a chest menu on the server side by hand (a fake player has no screen, only the menu matters). */
    private static void openChest(ServerPlayer player) {
        player.containerMenu = ChestMenu.threeRows(77, player.getInventory(), new SimpleContainer(27));
    }

    /** Items of that kind lying on the floor close to the player (a few blocks, so other tests' drops do not count). */
    private static List<ItemEntity> looseEntities(GameTestHelper helper, ServerPlayer player, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(3)).stream()
                .filter(e -> e.getItem().is(item) && e.distanceToSqr(player) <= 9.0).toList();
    }

    private static int loose(GameTestHelper helper, ServerPlayer player, Item item) {
        return looseEntities(helper, player, item).stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    /** Opens a chest, ticks the restraint and expects that window to be still the open one (and the player not held). */
    private static void assertStaysOpen(GameTestHelper helper, ServerPlayer player, String label) {
        openChest(player);
        AbstractContainerMenu menu = player.containerMenu;
        helper.assertFalse(FatigueRestraint.isRestrained(player), label + ": not restrained (setup)");
        FatigueRestraint.enforce(player);
        helper.assertTrue(player.containerMenu == menu && menu != player.inventoryMenu, label + ": stays open");
    }

    /** The positive control: the player is now held, and the very same open window is closed by the next tick. */
    private static void assertClosesNow(GameTestHelper helper, ServerPlayer player, String label) {
        AbstractContainerMenu menu = player.containerMenu;
        helper.assertTrue(menu != player.inventoryMenu, label + ": the window is still open before the control (setup)");
        helper.assertTrue(FatigueRestraint.isRestrained(player), label + ": now restrained (control)");
        FatigueRestraint.enforce(player);
        helper.assertTrue(player.containerMenu == player.inventoryMenu, label + ": the same window is now closed (control)");
    }

    private static int carried(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** BUG-091: a window open when the grace period starts is closed, and the cursor goes back to the inventory. */
    @GameTest(template = TestSupport.EMPTY)
    public static void enteringTheGraceClosesTheOpenWindowAndKeepsTheCursor(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        in(helper, true, () -> {
            player.getInventory().clearContent();
            openChest(player);
            ChestMenu menu = (ChestMenu) player.containerMenu;
            menu.setCarried(new ItemStack(Items.DIAMOND, 3));
            FatigueRestraint.enforce(player);
            helper.assertTrue(player.containerMenu != player.inventoryMenu, "free: the window stays open");
            enterGrace(player);
            FatigueRestraint.enforce(player);
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "held: the window is closed");
            helper.assertTrue(menu.getCarried().isEmpty(), "the closed menu's cursor is empty");
            helper.assertTrue(carried(player, Items.DIAMOND) == 3, "the diamonds went back to the inventory");
            helper.assertTrue(loose(helper, player, Items.DIAMOND) == 0, "nothing was dropped on the floor");
            FatigueRestraint.enforce(player);
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "idempotent: a second tick changes nothing");
            helper.assertTrue(carried(player, Items.DIAMOND) == 3, "and nothing is duplicated");
            // The player's own inventory screen is not a window to close.
            player.inventoryMenu.setCarried(new ItemStack(Items.GOLD_INGOT, 2));
            FatigueRestraint.enforce(player);
            helper.assertTrue(player.inventoryMenu.getCarried().is(Items.GOLD_INGOT)
                    && player.inventoryMenu.getCarried().getCount() == 2, "the own inventory screen is left alone, cursor intact");
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
            // The window opened again while still held (a new open slipping through) is closed by the next tick: real idempotence.
            openChest(player);
            helper.assertTrue(player.containerMenu != player.inventoryMenu, "reopened while held (setup)");
            FatigueRestraint.enforce(player);
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "reopened while held: the next tick closes it again");
        });
        helper.succeed();
    }

    /** With no room in the inventory the cursor cannot go back: it is dropped next to the player, not lost nor duplicated. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aFullInventoryDropsTheCursorWhenTheWindowCloses(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        try {
            in(helper, true, () -> {
                player.getInventory().clearContent();
                for (int i = 0; i < player.getInventory().items.size(); i++) {
                    player.getInventory().items.set(i, new ItemStack(Items.COBBLESTONE, 64));
                }
                openChest(player);
                player.containerMenu.setCarried(new ItemStack(Items.DIAMOND, 3));
                enterGrace(player);
                FatigueRestraint.enforce(player);
                helper.assertTrue(player.containerMenu == player.inventoryMenu, "held: the window is closed");
                helper.assertTrue(carried(player, Items.DIAMOND) == 0, "no room: nothing went back to the inventory");
                helper.assertTrue(loose(helper, player, Items.DIAMOND) == 3, "the 3 diamonds were dropped next to the player");
                helper.assertTrue(player.containerMenu.getCarried().isEmpty(), "the cursor is empty");
            });
        } finally {
            looseEntities(helper, player, Items.DIAMOND).forEach(Entity::discard);
            player.getInventory().clearContent();
        }
        helper.succeed();
    }

    // Not covered: player.openMenu(SimpleMenuProvider) does not open anything with a FakePlayer (no network connection), so the
    // window is assigned by hand in openChest; the closing path (closeContainer) is the same.

    @GameTest(template = TestSupport.EMPTY)
    public static void windowsStayOpenWhereTheRestraintDoesNotApply(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlockPos covadonga = helper.absolutePos(new BlockPos(1, 1, 1));
        GlobalPos vrhelmet = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(0, 1, 0)));
        in(helper, true, () -> {
            enterGrace(player);
            helper.assertTrue(FatigueRestraint.isRestrained(player), "held in the grace period (setup)");
            try {
                player.setGameMode(GameType.CREATIVE);
                assertStaysOpen(helper, player, "creative");
                player.setGameMode(GameType.SURVIVAL);
                assertClosesNow(helper, player, "creative back to survival");
                player.setGameMode(GameType.SPECTATOR);
                assertStaysOpen(helper, player, "spectator");
                player.setGameMode(GameType.SURVIVAL);
                assertClosesNow(helper, player, "spectator back to survival");
            } finally {
                player.setGameMode(GameType.SURVIVAL);
                player.closeContainer();
            }
            rest(player);
            assertStaysOpen(helper, player, "outside the margin");
            enterGrace(player);
            assertClosesNow(helper, player, "outside the margin, then margin again");
        });
        // Real world: enter the grace period inside Mitofus and step out WITHOUT a tickSecond (it would end the grace period).
        in(helper, true, () -> {
            enterGrace(player);
            helper.assertTrue(player.getData(ModAttachments.FATIGUE_GRACE) >= 0, "the grace period is running (precondition)");
            MitofusWorld.standIn(null);
            try {
                helper.assertTrue(player.getData(ModAttachments.FATIGUE_GRACE) >= 0, "still in grace in the real world (precondition)");
                assertStaysOpen(helper, player, "real world");
            } finally {
                MitofusWorld.standIn(helper.getLevel());
            }
            assertClosesNow(helper, player, "back in Mitofus");
        });
        inMitofusWithDefaults(helper, covadonga, () -> {
            helper.assertTrue(VrHelmetService.connect(player, vrhelmet), "connected");
            enterGrace(player);
            reconnectAfterForcedOut(helper, player, vrhelmet);
            helper.assertFalse(FatigueRestraint.isRestrained(player), "protected, not held (setup)");
            assertStaysOpen(helper, player, "protected after reconnecting");
            player.closeContainer();
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void anotherPlayerKeepsTheirWindowOpen(GameTestHelper helper) {
        ServerPlayer held = TestSupport.fakePlayer(helper);
        ServerPlayer other = TestSupport.fakePlayer(helper);
        try {
            in(helper, true, () -> {
                enterGrace(held);
                openChest(held);
                openChest(other);
                other.containerMenu.setCarried(new ItemStack(Items.DIAMOND, 4));
                AbstractContainerMenu otherMenu = other.containerMenu;
                helper.assertFalse(FatigueRestraint.isRestrained(other), "the other one is not held (setup)");
                FatigueRestraint.enforce(held);
                FatigueRestraint.enforce(other);
                helper.assertTrue(held.containerMenu == held.inventoryMenu, "the held player's window closed");
                helper.assertTrue(other.containerMenu == otherMenu && otherMenu != other.inventoryMenu,
                        "the other player's window stays open");
                helper.assertTrue(otherMenu.getCarried().is(Items.DIAMOND) && otherMenu.getCarried().getCount() == 4,
                        "the other player's cursor is intact");
            });
        } finally {
            other.containerMenu.setCarried(ItemStack.EMPTY);
            other.closeContainer();
            held.closeContainer();
        }
        helper.succeed();
    }

    /**
     * An armor stand is interacted with at a point (EntityInteractSpecific) and the off hand is a different hand than the main one.
     * The real effect goes through the same two steps as the server packet handler: the hook, then {@code interactAt}.
     */
    @GameTest(template = TestSupport.EMPTY)
    public static void armorStandAndOffHandAreRefusedOnlyWhileRestrained(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new Vec3(0.5, 1, 2.5));
        try {
            in(helper, true, () -> {
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_HELMET));
                assertEntityEvents(helper, stand, player, InteractionHand.OFF_HAND, false, "free");
                enterGrace(player);
                assertEntityEvents(helper, stand, player, InteractionHand.OFF_HAND, true, "in the grace period");
                assertEntityEvents(helper, stand, player, InteractionHand.MAIN_HAND, true, "in the grace period");
                Vec3 head = new Vec3(0, 1.7, 0);
                InteractionResult hook = CommonHooks.onInteractEntityAt(player, stand, head, InteractionHand.OFF_HAND);
                helper.assertTrue(hook == InteractionResult.FAIL, "held: the hook refuses (" + hook + ")");
                helper.assertTrue(stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "held: the stand got no helmet");
                helper.assertTrue(player.getItemInHand(InteractionHand.OFF_HAND).is(Items.IRON_HELMET), "held: the helmet stays in hand");
                rest(player);
                assertEntityEvents(helper, stand, player, InteractionHand.OFF_HAND, false, "free again");
                InteractionResult freeHook = CommonHooks.onInteractEntityAt(player, stand, head, InteractionHand.OFF_HAND);
                helper.assertTrue(freeHook == null, "free: the hook lets it through (" + freeHook + ")");
                stand.interactAt(player, head, InteractionHand.OFF_HAND);
                helper.assertTrue(stand.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
                        "free: the stand wears the helmet (so the empty check above proves something)");
            });
        } finally {
            stand.discard();
        }
        helper.succeed();
    }
}
