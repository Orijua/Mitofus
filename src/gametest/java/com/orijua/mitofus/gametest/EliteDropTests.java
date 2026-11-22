package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.config.RuleBalance;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.equipment.LootEvents;
import com.orijua.mitofus.feature.mob.MobLevel;
import com.orijua.mitofus.feature.mob.MobLevelService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The elite drop as it happens in play: a player kills an elite and LootEvents adds the piece to its drops.
 * The chance is forced to 100% through {@link Balance#withOverride} so the result never depends on luck.
 */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class EliteDropTests {
    private EliteDropTests() {
    }

    /** Reads the final drops of one mob, after every other listener (LOWEST priority runs last). */
    public static final class DropRecorder {
        private final Zombie victim;
        final List<ItemEntity> drops = new ArrayList<>();

        DropRecorder(Zombie victim) {
            this.victim = victim;
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onDrops(LivingDropsEvent event) {
            if (event.getEntity() == victim) {
                drops.addAll(event.getDrops());
            }
        }
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void eliteKilledByAPlayerDropsARatedPiece(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        List<ItemStack> pieces = killZombie(helper, true, 1.0, zombie -> player.damageSources().playerAttack(player));
        helper.assertTrue(pieces.size() == 1, "exactly one elite piece, got " + pieces);
        ItemRating rating = pieces.get(0).get(ModDataComponents.ITEM_RATING);
        helper.assertTrue(rating != null, "rated, got " + rating);
        helper.succeed();
    }

    /** The drop follows the elite's level: a level 60 one gives an Epic piece at least (the floor of its level). */
    @GameTest(template = TestSupport.EMPTY)
    public static void aHighLevelEliteKilledByAPlayerDropsAnEpicPiece(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        List<ItemStack> pieces = killZombie(helper, true, 1.0, 60, zombie -> player.damageSources().playerAttack(player));
        helper.assertTrue(pieces.size() == 1, "exactly one elite piece, got " + pieces);
        ItemRating rating = pieces.get(0).get(ModDataComponents.ITEM_RATING);
        helper.assertTrue(rating != null && rating.ordinal() >= ItemRating.EPIC.ordinal(), "Epic or better at level 60, got " + rating);
        helper.succeed();
    }

    /** The default curve: an elite of a very high level practically never drops the piece (here, 200 kills of a level 300 elite). */
    @GameTest(template = TestSupport.EMPTY)
    public static void aVeryHighLevelEliteAlmostNeverDrops(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        int drops = 0;
        for (int i = 0; i < 200; i++) {
            Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
            MobLevelService.assign(zombie, new MobLevel(300, true), Balance.DEFAULT);
            DropRecorder recorder = new DropRecorder(zombie);
            NeoForge.EVENT_BUS.register(recorder);
            try {
                RuleBalance.withOverride(RuleBalance.DEFAULT, () -> Balance.withOverride(Balance.DEFAULT,
                        () -> zombie.hurt(player.damageSources().playerAttack(player), 10_000.0f)));
            } finally {
                NeoForge.EVENT_BUS.unregister(recorder);
            }
            drops += (int) recorder.drops.stream().filter(drop -> drop.getItem().is(LootEvents.ELITE_DROPS)).count();
            recorder.drops.forEach(ItemEntity::discard);
        }
        helper.assertTrue(drops <= 2, "at 0.005% a level 300 elite almost never drops: " + drops + " drops in 200 kills");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void eliteKilledWithoutAPlayerDropsNoPiece(GameTestHelper helper) {
        List<ItemStack> pieces = killZombie(helper, true, 1.0, zombie -> zombie.damageSources().generic());
        helper.assertTrue(pieces.isEmpty(), "only player kills earn the piece, got " + pieces);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void eliteDropRespectsAZeroChance(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        List<ItemStack> pieces = killZombie(helper, true, 0.0, zombie -> player.damageSources().playerAttack(player));
        helper.assertTrue(pieces.isEmpty(), "a 0% chance never drops the piece, got " + pieces);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void regularMobKilledByAPlayerDropsNoPiece(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        List<ItemStack> pieces = killZombie(helper, false, 1.0, zombie -> player.damageSources().playerAttack(player));
        helper.assertTrue(pieces.isEmpty(), "only elites drop the piece, got " + pieces);
        helper.succeed();
    }

    /**
     * Spawns a zombie (elite or not), kills it with the given damage source at a 100% drop chance, and returns
     * the elite pieces among its drops. The dropped items are removed afterwards.
     */
    private static List<ItemStack> killZombie(GameTestHelper helper, boolean elite, double chance, Function<Zombie, DamageSource> source) {
        return killZombie(helper, elite, chance, 5, source);
    }

    private static List<ItemStack> killZombie(GameTestHelper helper, boolean elite, double chance, int mobLevel, Function<Zombie, DamageSource> source) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        MobLevelService.assign(zombie, new MobLevel(mobLevel, elite), Balance.DEFAULT);
        DropRecorder recorder = new DropRecorder(zombie);
        NeoForge.EVENT_BUS.register(recorder);
        try {
            // The chance falls with the level: with the floor at the same value and no decay it is flat, so the result never depends on luck.
            RuleBalance.withOverride(RuleBalance.DEFAULT.withEliteDrop(chance, 1.0), () ->
                    Balance.withOverride(Balance.DEFAULT.withEliteEquipmentChance(chance),
                            () -> zombie.hurt(source.apply(zombie), 10_000.0f)));
        } finally {
            NeoForge.EVENT_BUS.unregister(recorder);
        }
        helper.assertTrue(zombie.isDeadOrDying(), "the zombie should die");
        List<ItemStack> pieces = recorder.drops.stream().map(ItemEntity::getItem).filter(stack -> stack.is(LootEvents.ELITE_DROPS)).toList();
        recorder.drops.forEach(ItemEntity::discard);
        return pieces;
    }
}
