package com.orijua.mitofus.gametest;

import com.mojang.authlib.GameProfile;
import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.config.VrHelmetBalance;
import com.orijua.mitofus.core.config.EnhanceBalance;
import com.orijua.mitofus.core.config.EquipLevelBalance;
import com.orijua.mitofus.core.config.FatigueBalance;
import com.orijua.mitofus.core.config.TerminalBalance;
import com.orijua.mitofus.core.config.TradeBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModEntities;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.mob.MobKilledEvent;
import com.orijua.mitofus.feature.npc.BlacksmithNpc;
import com.orijua.mitofus.feature.npc.VillageChiefNpc;
import com.orijua.mitofus.feature.quest.PlayerQuests;
import com.orijua.mitofus.feature.quest.QuestService;
import com.orijua.mitofus.feature.smithing.ItemForgedEvent;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Shared helpers for the game tests. Tests run with `gradlew runGameTestServer`
 * (or `/test runall` in a dev world); each class groups the tests of one system.
 */
final class TestSupport {
    /** data/mitofus/structure/empty.nbt: a 3x3x3 air box. */
    static final String EMPTY = "empty";
    /**
     * How far, in chunks, the searches for Covadonga and Eibar look on the vanilla overworld, whose terrain doesn't follow
     * map (seas and rivers anywhere): the radius they had before the Mitofus terrain followed the map.
     */
    static final int VANILLA_TERRAIN_SEARCH_RADIUS = 32;

    private TestSupport() {
    }

    /** A ServerPlayer with a no-op connection (syncing is ignored), standing in the test area and looking towards +Z. */
    static ServerPlayer fakePlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "mitofus-test"));
        Vec3 origin = helper.absoluteVec(new Vec3(0.5, 1, 0.5));
        player.moveTo(origin.x, origin.y, origin.z, 0.0f, 0.0f);
        player.setYHeadRot(0.0f);
        return player;
    }

    /** A fake player standing at the spawn of another level (e.g. the Nether), for tests that depend on the dimension. */
    static ServerPlayer fakePlayerIn(ServerLevel level) {
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "mitofus-test-other"));
        BlockPos spawn = level.getSharedSpawnPos();
        player.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0f, 0.0f);
        return player;
    }

    /**
     * Posts a zombie kill, as a real kill would after awarding XP (the zombie is never added to the world). It skips the
     * world filter of {@code ProgressionEvents.onLivingDeath}: don't use it in real-world tests (post a LivingDeathEvent).
     */
    static void killZombie(GameTestHelper helper, ServerPlayer player) {
        kill(helper, player, EntityType.ZOMBIE);
    }

    /** Posts a kill of a mob of {@code type}, which is never added to the world (also skips the world filter, see above). */
    static void kill(GameTestHelper helper, ServerPlayer player, EntityType<? extends LivingEntity> type) {
        NeoForge.EVENT_BUS.post(new MobKilledEvent(player, type.create(helper.getLevel())));
    }

    /** Posts a forge of the given rating, as taking it out of the smithing anvil would. */
    static void forge(ServerPlayer player, ItemRating rating) {
        forge(player, Items.IRON_SWORD, rating);
    }

    static void forge(ServerPlayer player, Item item, ItemRating rating) {
        NeoForge.EVENT_BUS.post(new ItemForgedEvent(player, new ItemStack(item), rating));
    }

    /** Smith (the blacksmith NPC), next to the fake player: the NPC to deal with for the quests he gives. */
    static BlacksmithNpc smith(GameTestHelper helper) {
        return helper.spawn(ModEntities.BLACKSMITH.get(), 1, 1, 2);
    }

    /** The village chief, next to the fake player. */
    static VillageChiefNpc chief(GameTestHelper helper) {
        return helper.spawn(ModEntities.VILLAGE_CHIEF.get(), 1, 1, 2);
    }

    /** Marks quests as already completed, to start a test further along a chain. */
    static void completed(ServerPlayer player, String... quests) {
        PlayerQuests state = QuestService.get(player);
        for (String quest : quests) {
            state = state.complete(Mitofus.id(quest));
        }
        player.setData(ModAttachments.PLAYER_QUESTS, state);
    }

    /** Attack damage {@code weapon} gives on top of a plain iron sword, for a level 1 player. */
    static double attackBonusOver(GameTestHelper helper, ItemStack weapon) {
        return attackBonusOver(helper, weapon, 1);
    }

    /** Like {@link #attackBonusOver(GameTestHelper, ItemStack)} for a player of the given level (a rated piece asks for one). */
    static double attackBonusOver(GameTestHelper helper, ItemStack weapon, int level) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(ModAttachments.PLAYER_STATS, player.getData(ModAttachments.PLAYER_STATS).withProgress(level, 0, 0));
        // Equipment modifiers are applied on the entity tick; force it so tests needn't wait.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        player.tick();
        double base = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        player.tick();
        return player.getAttributeValue(Attributes.ATTACK_DAMAGE) - base;
    }

    /** Armor points {@code piece} (worn on the chest) gives on top of a plain iron chestplate, for a level 1 player. */
    static double armorBonusOver(GameTestHelper helper, ItemStack piece) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(ModAttachments.PLAYER_STATS, player.getData(ModAttachments.PLAYER_STATS).withProgress(1, 0, 0));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        player.tick();
        double base = player.getAttributeValue(Attributes.ARMOR);
        player.setItemSlot(EquipmentSlot.CHEST, piece);
        player.tick();
        return player.getAttributeValue(Attributes.ARMOR) - base;
    }

    /** Runs {@code tick} on {@code player} {@code ticks} times, by hand (a fake player does not tick). */
    static void tickMany(ServerPlayer player, int ticks, Consumer<ServerPlayer> tick) {
        for (int i = 0; i < ticks; i++) {
            tick.accept(player);
        }
    }

    /** Posts the "damage applied" event for {@code amount} damage to {@code player} (nothing is actually taken). */
    static void hurt(ServerPlayer player, float amount) {
        NeoForge.EVENT_BUS.post(new LivingDamageEvent.Post(player, new DamageContainer(player.damageSources().generic(), amount)));
    }

    /**
     * Runs {@code body} with the factory numbers of every balance ({@code X.DEFAULT}), whatever the config says.
     * Use it as the OUTERMOST layer: the specific overrides of a test go inside it (the inner one wins), so never
     * call it inside another override. It only covers what runs synchronously inside {@code body}.
     */
    static void withAllDefaults(Runnable body) {
        Balance.withOverride(Balance.DEFAULT, () ->
                FatigueBalance.withOverride(FatigueBalance.DEFAULT, () ->
                        VrHelmetBalance.withOverride(VrHelmetBalance.DEFAULT, () ->
                                TerminalBalance.withOverride(TerminalBalance.DEFAULT, () ->
                                        TradeBalance.withOverride(TradeBalance.DEFAULT, () ->
                                                EquipLevelBalance.withOverride(EquipLevelBalance.DEFAULT, () ->
                                                        EnhanceBalance.withOverride(EnhanceBalance.DEFAULT, body)))))));
    }

    /** Runs {@code body} in Mitofus or the real world, with the default fatigue numbers (a world keeps the numbers of its saved config). */
    static void inFatigue(GameTestHelper helper, boolean mitofus, Runnable body) {
        try {
            MitofusWorld.standIn(mitofus ? helper.getLevel() : null);
            FatigueBalance.withOverride(FatigueBalance.DEFAULT, body);
        } finally {
            MitofusWorld.restoreStandIn();
        }
    }

    /** Like {@link VrHelmetTests#inMitofus}, with the default fatigue numbers (the saved config of the test world keeps its own). */
    static void inMitofusWithFatigueDefaults(GameTestHelper helper, BlockPos covadonga, Runnable body) {
        VrHelmetTests.inMitofus(helper, covadonga, () -> FatigueBalance.withOverride(FatigueBalance.DEFAULT, body));
    }

    static void assertClose(GameTestHelper helper, double expected, double actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < 1e-4, what + ": expected " + expected + ", got " + actual);
    }

    /** Puts {@code iron} in the player's purse (the only money). */
    static void setIron(net.minecraft.server.level.ServerPlayer player, long iron) {
        player.setData(com.orijua.mitofus.core.registry.ModAttachments.PURSE.get(), new com.orijua.mitofus.feature.economy.Purse(iron));
    }

    /** A sleeping body of {@code tier} lying at {@code relative}, owned by nobody online. */
    static void lieBody(GameTestHelper helper, BlockPos relative, com.orijua.mitofus.feature.vrhelmet.VrHelmetTier tier) {
        removeBody(helper, relative);
        com.orijua.mitofus.feature.vrhelmet.SleepingBody.spawn(helper.getLevel(), helper.absolutePos(relative), tier, UUID.randomUUID(), 0.0f);
    }

    /** The sleeping bodies lying at {@code relative} are removed. */
    static void removeBody(GameTestHelper helper, BlockPos relative) {
        com.orijua.mitofus.feature.vrhelmet.SleepingBody.at(helper.getLevel(), helper.absolutePos(relative)).forEach(Entity::discard);
    }

    /** Whether a sleeping body lies at the absolute position. */
    static boolean hasBody(GameTestHelper helper, BlockPos absolute) {
        return !com.orijua.mitofus.feature.vrhelmet.SleepingBody.at(helper.getLevel(), absolute).isEmpty();
    }
}
