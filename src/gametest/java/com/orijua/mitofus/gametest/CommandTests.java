package com.orijua.mitofus.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.debug.DebugGear;
import com.orijua.mitofus.core.debug.SiteLanding;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.effect.ActiveTraits;
import com.orijua.mitofus.feature.effect.BonusType;
import com.orijua.mitofus.feature.skill.MasteryRules;
import com.orijua.mitofus.feature.skill.SkillService;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.feature.equipment.EquipRequirements;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.equipment.option.RolledOption;
import com.orijua.mitofus.feature.progression.StatType;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetService;
import com.orijua.mitofus.feature.vrhelmet.LogoutService;
import com.orijua.mitofus.feature.vrhelmet.WorldSeparation;
import com.orijua.mitofus.feature.npc.ApprenticeSmith;
import com.orijua.mitofus.feature.npc.NpcGrade;
import com.orijua.mitofus.feature.sancho.NunoBetrayal;
import com.orijua.mitofus.feature.sancho.SanchoStory;
import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.feature.progression.PlayerStats;
import com.orijua.mitofus.feature.progression.ProgressionRules;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.quest.PlayerQuests;
import com.orijua.mitofus.feature.quest.QuestService;
import com.orijua.mitofus.feature.quest.goal.GoalTracker;
import com.orijua.mitofus.feature.world.MitofusWorld;
import com.orijua.mitofus.feature.world.MitofusMapData;
import com.orijua.mitofus.feature.world.MitofusPlaces;
import com.orijua.mitofus.feature.world.WorldSites;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.mojang.brigadier.suggestion.Suggestion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class CommandTests {
    private CommandTests() {
    }

    /** Runs a command as {@code player} with op permission, as typed in chat (without the slash). */
    private static void run(GameTestHelper helper, ServerPlayer player, String command) {
        helper.getLevel().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), command);
    }

    /** Like {@link #run}, but returns the command's result and throws if it fails. */
    private static int execute(GameTestHelper helper, ServerPlayer player, String command) {
        try {
            return helper.getLevel().getServer().getCommands().getDispatcher().execute(command, player.createCommandSourceStack().withPermission(4));
        } catch (CommandSyntaxException e) {
            throw new AssertionError(command + ": " + e.getMessage(), e);
        }
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void debugPassiveGivesAndRemovesPassives(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        helper.assertTrue(ActiveTraits.total(player, BonusType.MAX_MANA) == 0, "no extra mana without passives");
        execute(helper, player, "mitofus passive give mitofus:mana_reserve");
        helper.assertTrue(ActiveTraits.total(player, BonusType.MAX_MANA) == 20, "Mana reserve gives +20 maximum mana, whatever the class");
        execute(helper, player, "mitofus passive list");
        execute(helper, player, "mitofus passive remove mitofus:mana_reserve");
        helper.assertTrue(ActiveTraits.total(player, BonusType.MAX_MANA) == 0, "removed");
        execute(helper, player, "mitofus passive give mitofus:mana_reserve");
        execute(helper, player, "mitofus passive clear");
        helper.assertTrue(player.getData(ModAttachments.PASSIVE_OVERRIDES).isEmpty(), "clear forgets the overrides");
        helper.assertTrue(execute(helper, player, "mitofus passive give mitofus:nope") == 0, "unknown passive refused");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void debugMasterySetsLevelAndUses(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ResourceLocation strike = Mitofus.id("heavy_strike");
        execute(helper, player, "mitofus skill learn mitofus:heavy_strike");
        helper.assertTrue(SkillService.get(player).knows(strike), "learned");
        execute(helper, player, "mitofus mastery mitofus:heavy_strike level 5");
        helper.assertTrue(player.getData(ModAttachments.SKILL_MASTERY).level(strike) == 5, "level 5");
        helper.assertTrue(player.getData(ModAttachments.SKILL_MASTERY).uses(strike) == MasteryRules.usesToReach(5), "uses of level 5");
        execute(helper, player, "mitofus mastery mitofus:heavy_strike uses 99");
        helper.assertTrue(player.getData(ModAttachments.SKILL_MASTERY).level(strike) == 1, "99 uses are still level 1");
        execute(helper, player, "mitofus mastery list");
        execute(helper, player, "mitofus mastery mitofus:heavy_strike reset");
        helper.assertTrue(player.getData(ModAttachments.SKILL_MASTERY).uses(strike) == 0, "reset");
        helper.assertTrue(execute(helper, player, "mitofus mastery mitofus:nope level 3") == 0, "unknown skill refused");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void everyMitofusSubcommandHasAHelpLineAndDebugStaysAsAlias(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        java.util.Set<String> registered = new java.util.HashSet<>();
        dispatcher.getRoot().getChild("mitofus").getChildren().forEach(child -> registered.add(child.getName()));
        registered.remove("debug");
        helper.assertTrue(registered.equals(com.orijua.mitofus.core.debug.HelpCommand.names()),
                "help lists exactly the subcommands: " + registered + " vs " + com.orijua.mitofus.core.debug.HelpCommand.names());
        helper.assertTrue(execute(helper, player, "mitofus help") == registered.size(), "an operator sees every line");
        // The old spelling still reaches the same commands.
        execute(helper, player, "mitofus debug set level 7");
        helper.assertTrue(ProgressionService.get(player).level() == 7, "/mitofus debug set level still works");
        execute(helper, player, "mitofus set level 9");
        helper.assertTrue(ProgressionService.get(player).level() == 9, "/mitofus set level works");
        // Without permission only help and logout are there.
        CommandSourceStack plain = player.createCommandSourceStack().withPermission(0);
        try {
            helper.assertTrue(dispatcher.execute("mitofus help", plain) == 2, "a player sees help and logout");
        } catch (CommandSyntaxException e) {
            throw new AssertionError(e.getMessage(), e);
        }
        helper.assertTrue(dispatcher.parse("mitofus set level 3", plain).getContext().getNodes().size() < 3, "set is not for players");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void simulatedKillsCountAndRevealHiddenQuests(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ResourceLocation call = Mitofus.id("call_of_the_night");
        run(helper, player, "mitofus simulate kill minecraft:skeleton 30");
        helper.assertTrue(GoalTracker.counters(player).get("kill/minecraft:skeleton") == 30,
                "30 skeleton kills counted: " + GoalTracker.counters(player));
        helper.assertTrue(QuestService.get(player).isDiscovered(call), "30 simulated kills reveal the hidden quest");

        run(helper, player, "mitofus reset quests");
        helper.assertFalse(QuestService.get(player).isDiscovered(call), "quest reset forgets discoveries");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void debugSpawnCreatesALeveledEliteOrNormalMob(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        java.util.function.Supplier<java.util.List<net.minecraft.world.entity.monster.Zombie>> zombies = () ->
                helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class, player.getBoundingBox().inflate(6));
        run(helper, player, "mitofus spawn minecraft:zombie elite 42");
        helper.assertTrue(zombies.get().size() == 1, "one zombie spawned: " + zombies.get());
        var elite = com.orijua.mitofus.feature.mob.MobLevelService.get(zombies.get().get(0));
        helper.assertTrue(elite.elite() && elite.level() == 42, "an elite of level 42: " + elite);
        zombies.get().forEach(net.minecraft.world.entity.Entity::discard);

        run(helper, player, "mitofus spawn minecraft:zombie normal 7");
        var normal = com.orijua.mitofus.feature.mob.MobLevelService.get(zombies.get().get(0));
        helper.assertTrue(!normal.elite() && normal.level() == 7, "a normal one of level 7: " + normal);
        zombies.get().forEach(net.minecraft.world.entity.Entity::discard);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void debugSpawnIsRefusedOutsideMitofus(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        MitofusOnlyTests.realWorld(helper, () -> {
            run(helper, player, "mitofus spawn minecraft:zombie elite 10");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class,
                    player.getBoundingBox().inflate(6)).isEmpty(), "nothing spawns in the real world");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void discoveredQuestIsNewUntilTheTabIsOpened(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ResourceLocation call = Mitofus.id("call_of_the_night");
        run(helper, player, "mitofus simulate kill minecraft:skeleton 30");
        helper.assertTrue(QuestService.get(player).isNew(call), "a fresh discovery is new");
        QuestService.markDiscoveriesSeen(player); // what opening the Quests tab asks the server to do
        helper.assertFalse(QuestService.get(player).isNew(call), "seen once the tab was opened");
        helper.assertTrue(QuestService.get(player).isDiscovered(call), "and still discovered");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void simulatedForgesCountForTheirTier(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        run(helper, player, "mitofus simulate forge epic 2");
        helper.assertTrue(GoalTracker.counters(player).get("forge/epic") == 2, "2 epic forges: " + GoalTracker.counters(player));
        helper.assertTrue(GoalTracker.counters(player).get("forge/rare") == 2, "which also count as rare");
        helper.succeed();
    }

    /** Completing the end of Smith's chain completes the whole chain, so Nuno listens (what the command is for). */
    @GameTest(template = TestSupport.EMPTY)
    public static void questCompleteMarksTheWholeChainDone(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        run(helper, player, "mitofus quest complete mitofus:quality_test");
        PlayerQuests quests = QuestService.get(player);
        helper.assertTrue(quests.isCompleted(SanchoStory.SMITH_CHAIN_END), "the quest itself");
        helper.assertTrue(quests.isCompleted(Mitofus.id("first_commission")), "and the one before it");
        helper.assertTrue(quests.isCompleted(Mitofus.id("smith_firewood")), "back to the start of the chain");
        helper.assertTrue(player.getInventory().countItem(ModItems.ENHANCEMENT_STONE.get()) == 3, "the requested quest gives its 3 stones");

        run(helper, player, "mitofus quest complete mitofus:quality_test");
        helper.assertTrue(player.getInventory().countItem(ModItems.ENHANCEMENT_STONE.get()) == 3, "and not again");
        helper.assertTrue(QuestService.get(player).timesCompleted(SanchoStory.SMITH_CHAIN_END) == 1, "done ones are not done twice");

        NunoBetrayal.talk(player);
        helper.assertTrue(QuestService.get(player).isDiscovered(SanchoStory.SECRET_REQUEST), "Nuno now tells of his request");
        helper.succeed();
    }

    /** Only the quest named gives its rewards, once; the earlier ones of the chain are marked done without them. */
    @GameTest(template = TestSupport.EMPTY)
    public static void questCompleteGivesOnlyTheRequestedQuestRewardsOnce(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        run(helper, player, "mitofus quest complete mitofus:covadonga_talents");
        helper.assertTrue(QuestService.get(player).isCompleted(Mitofus.id("watch_commander")), "the chain is done");
        helper.assertTrue(player.getInventory().countItem(ModItems.GOLD_COIN.get()) == 5, "5 gold");
        helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "no plain emeralds");
        helper.assertTrue(player.getInventory().countItem(ModItems.COMMANDERS_SWORD.get()) == 0, "no reward of watch_commander");
        helper.assertTrue(player.getInventory().countItem(ModItems.RULERS_SWORD.get()) == 0, "no loan");
        PlayerStats expected = ProgressionRules.addXp(Balance.current(), PlayerStats.DEFAULT, 500).stats();
        PlayerStats stats = ProgressionService.get(player);
        helper.assertTrue(stats.level() == expected.level() && stats.xp() == expected.xp(), "only the 500 XP of the quest: " + stats);

        run(helper, player, "mitofus quest complete mitofus:covadonga_talents");
        helper.assertTrue(player.getInventory().countItem(ModItems.GOLD_COIN.get()) == 5, "repeating gives nothing more");
        PlayerStats again = ProgressionService.get(player);
        helper.assertTrue(again.level() == stats.level() && again.xp() == stats.xp(), "nor XP: " + again);
        helper.succeed();
    }

    /** Playtest: the class level by name (the Profile counts slots from 1, the commands from 0) and the whole tree in one command. */
    @GameTest(template = TestSupport.EMPTY)
    public static void classLevelByNameAndTalentBuyAll(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        helper.assertTrue(execute(helper, player, "mitofus set class 0 swordsman") == 1, "class placed");
        helper.assertTrue(execute(helper, player, "mitofus set classlevel swordsman 7") == 7, "level set by name");
        helper.assertTrue(execute(helper, player, "mitofus set classlevel mage 7") == 0, "a class that is not placed fails");
        helper.assertTrue(execute(helper, player, "mitofus talent buyall swordsman") > 0, "nodes bought");
        helper.assertTrue(execute(helper, player, "mitofus grade set swordsman 1") == 1, "grade set by class");
        helper.assertTrue(com.orijua.mitofus.feature.progression.ClassService.classes(player).slot(0).orElseThrow().grade()
                == com.orijua.mitofus.feature.progression.ClassGrade.APPRENTICE, "grade lowered to 1");
        helper.assertTrue(execute(helper, player, "mitofus grade set mage 2") == 0, "a class that is not placed fails");
        var slot = com.orijua.mitofus.feature.progression.ClassService.classes(player).slot(0).orElseThrow();
        helper.assertTrue(com.orijua.mitofus.feature.fusion.FusionRules.treeComplete(
                com.orijua.mitofus.feature.talent.TalentService.all(helper.getLevel()), slot), "its tree is complete");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void questCompleteOfNunoRequestGives16Gold(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        run(helper, player, "mitofus quest complete mitofus:nuno_secret_request");
        helper.assertTrue(player.getInventory().countItem(ModItems.GOLD_COIN.get()) == 16, "16 gold");
        helper.succeed();
    }

    /** An apprentice of a chosen grade and level, to see his window without the 90/9/1 roll (playtest). */
    @GameTest(template = TestSupport.EMPTY)
    public static void debugApprenticeHiresTheChosenGrade(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        helper.assertTrue(execute(helper, player, "mitofus set smithing 80") > 0, "smithing raised");
        helper.assertTrue(execute(helper, player, "mitofus spawn apprentice named 30") == 1, "hired");
        List<UUID> hired = player.getData(ModAttachments.APPRENTICES);
        helper.assertTrue(hired.size() == 1 && helper.getLevel().getEntity(hired.getFirst()) instanceof ApprenticeSmith,
                "one apprentice, in the world");
        ApprenticeSmith apprentice = (ApprenticeSmith) helper.getLevel().getEntity(hired.getFirst());
        helper.assertTrue(apprentice.grade() == NpcGrade.NAMED && apprentice.isOwnedBy(player), "named, and his");
        helper.assertTrue(apprentice.smithingLevel() == 30, "smithing 30: " + apprentice.smithingLevel());
        apprentice.discard();
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void gotoLandsOnTheSurfaceOrRefuses(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ServerLevel level = helper.getLevel();
        long seed = level.getSeed();
        Optional<ChunkPos> covadonga = WorldSites.siteFor(seed, WorldSites.COVADONGA);
        Optional<ChunkPos> cave = WorldSites.siteFor(seed, WorldSites.ALTAMIRA);
        Optional<BlockPos> landingBefore = WorldSites.landingFor(seed);
        try {
            WorldSites.forget(seed, WorldSites.ALTAMIRA);
            MitofusWorld.standIn(level); // the test server has no Mitofus dimension
            WorldSites.remember(seed, WorldSites.COVADONGA, new ChunkPos(level.getSharedSpawnPos()));
            WorldSites.rememberLanding(seed, level.getSharedSpawnPos());
            helper.assertTrue(SiteLanding.locate(level, WorldSites.ALTAMIRA).reason() == SiteLanding.Reason.NO_SITE, "no cave: NO_SITE, not sea");
            helper.assertTrue(SiteLanding.locate(level, "mar_del_este").reason() == SiteLanding.Reason.SEA, "a sea place: SEA");
            helper.assertTrue(SiteLanding.locate(level, "nowhere").reason() == SiteLanding.Reason.UNKNOWN, "an unknown name: UNKNOWN");
            helper.assertTrue(SiteLanding.find(level, WorldSites.ALTAMIRA).isEmpty(), "no cave in this world: nowhere to go");
            helper.assertTrue(SiteLanding.find(level, "nowhere").isEmpty(), "an unknown place");

            BlockPos landing = SiteLanding.find(level, WorldSites.COVADONGA).orElseThrow();
            BlockPos spawn = level.getSharedSpawnPos();
            helper.assertTrue(landing.getX() == spawn.getX() && landing.getZ() == spawn.getZ(), "Covadonga: its plaza, where the landing was saved");
            helper.assertTrue(level.getBlockState(landing).getCollisionShape(level, landing).isEmpty()
                    && level.getBlockState(landing.above()).getCollisionShape(level, landing.above()).isEmpty(), "room to stand");
            helper.assertFalse(level.getBlockState(landing.below()).isAir(), "on the ground");

            // A fake player ignores the teleport packet, so only the command's success is checked here.
            helper.assertTrue(execute(helper, player, "mitofus goto covadonga") == 1, "the command goes there");

            // Covadonga with no site and no landing in this world: "no such place", never "in the sea".
            WorldSites.forget(seed, WorldSites.COVADONGA);
            WorldSites.forgetLanding(seed);
            helper.assertTrue("message.mitofus.debug_goto_none".equals(refusal(helper, player, "mitofus goto covadonga")),
                    "Covadonga without a site is refused as 'none'");
            helper.assertTrue("message.mitofus.debug_goto_none".equals(refusal(helper, player, "mitofus goto altamira")),
                    "the cave without a site is refused as 'none'");
        } finally {
            covadonga.ifPresentOrElse(chunk -> WorldSites.remember(seed, WorldSites.COVADONGA, chunk), () -> WorldSites.forget(seed, WorldSites.COVADONGA));
            cave.ifPresent(chunk -> WorldSites.remember(seed, WorldSites.ALTAMIRA, chunk));
            MitofusWorld.restoreStandIn();
            landingBefore.ifPresentOrElse(pos -> WorldSites.rememberLanding(seed, pos), () -> WorldSites.forgetLanding(seed));
        }
        helper.succeed();
    }

    /** Runs a command with the given permission level and returns its result (a refusal throws). */
    private static int executeWith(GameTestHelper helper, ServerPlayer player, String command, int permission) throws CommandSyntaxException {
        return helper.getLevel().getServer().getCommands().getDispatcher()
                .execute(command, player.createCommandSourceStack().withPermission(permission));
    }

    private static boolean refusedWith(GameTestHelper helper, ServerPlayer player, String command, int permission) {
        try {
            executeWith(helper, player, command, permission);
            return false;
        } catch (CommandSyntaxException e) {
            return true;
        }
    }

    /**
     * Whether a source with {@code permission} may use every node along {@code path} (literal names). Fails the test if
     * the command does not exist, so a missing command never counts as "denied".
     */
    private static boolean canUse(GameTestHelper helper, ServerPlayer player, int permission, String... path) {
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        CommandSourceStack source = player.createCommandSourceStack().withPermission(permission);
        CommandNode<CommandSourceStack> node = dispatcher.getRoot();
        boolean allowed = true;
        for (String name : path) {
            node = node.getChild(name);
            helper.assertTrue(node != null, "the command node exists: " + String.join(" ", path));
            allowed &= node.canUse(source);
        }
        return allowed;
    }

    /** /mitofus logout is for everyone; the debug and validate parts of /mitofus stay op-only. */
    @GameTest(template = TestSupport.EMPTY)
    public static void logoutIsOpenToEveryoneButDebugAndValidateAreNot(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                helper.assertTrue(executeWith(helper, player, "mitofus logout", 0) == 1, "in Mitofus: it starts");
                helper.assertTrue(LogoutService.isCountingDown(player), "and the countdown is running");
            } catch (CommandSyntaxException e) {
                throw new AssertionError("mitofus logout with permission 0: " + e.getMessage(), e);
            } finally {
                LogoutService.forgetAll();
            }
        });
        VrHelmetTests.outsideMitofus(helper, () -> {
            try {
                helper.assertTrue(executeWith(helper, player, "mitofus logout", 0) == 0, "outside Mitofus: it returns 0");
                helper.assertFalse(LogoutService.isCountingDown(player), "and nothing counts");
            } catch (CommandSyntaxException e) {
                throw new AssertionError("mitofus logout with permission 0: " + e.getMessage(), e);
            }
        });
        helper.assertTrue(canUse(helper, player, 0, "mitofus", "logout"), "logout is open to everyone");
        helper.assertFalse(canUse(helper, player, 0, "mitofus", "reset", "quests"), "the debug commands need op (the node is denied)");
        helper.assertFalse(canUse(helper, player, 0, "mitofus", "validate"), "validate needs op (the node is denied)");
        helper.assertTrue(canUse(helper, player, 4, "mitofus", "reset", "quests"), "debug node is open to ops");
        helper.assertTrue(canUse(helper, player, 4, "mitofus", "validate"), "validate node is open to ops");
        helper.assertFalse(canUse(helper, player, 0, "mitofus", "debug"), "the old /mitofus debug spelling needs op too");
        helper.assertTrue(canUse(helper, player, 4, "mitofus", "debug"), "and works for ops");
        helper.assertTrue(refusedWith(helper, player, "mitofus reset quests", 0), "debug needs op");
        helper.assertTrue(refusedWith(helper, player, "mitofus validate", 0), "validate needs op");
        helper.assertFalse(refusedWith(helper, player, "mitofus reset quests", 4), "debug works for ops");
        helper.assertFalse(refusedWith(helper, player, "mitofus validate", 4), "and so does validate");
        helper.succeed();
    }

    /** In danger, or with a countdown already running, /mitofus logout returns 0 and starts nothing new. */
    @GameTest(template = TestSupport.EMPTY)
    public static void logoutIsRefusedInDangerAndWhileAlreadyRunning(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                LogoutService.markCombat(player, helper.getLevel().getServer().getTickCount());
                helper.assertTrue(executeWith(helper, player, "mitofus logout", 0) == 0, "in danger: returns 0");
                helper.assertFalse(LogoutService.isCountingDown(player), "and nothing counts (the message is client-side: N4)");

                LogoutService.forgetAll();
                helper.assertTrue(executeWith(helper, player, "mitofus logout", 0) == 1, "out of danger: starts");
                helper.assertTrue(executeWith(helper, player, "mitofus logout", 0) == 0, "already running: returns 0");
                helper.assertTrue(LogoutService.isCountingDown(player), "and the first countdown goes on");
            } catch (CommandSyntaxException e) {
                throw new AssertionError("mitofus logout with permission 0: " + e.getMessage(), e);
            } finally {
                LogoutService.forgetAll();
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void debugDisconnectIsInstant(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            helper.assertTrue(execute(helper, player, "mitofus vrhelmet disconnect") == 1, "no countdown: it disconnects at once");
            helper.assertTrue(VrHelmetService.state(player).lastSave().isPresent(), "and the spot is saved");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void debugVrHelmetCommandsShowAndSetTheTierAndTheRested(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        helper.assertTrue(execute(helper, player, "mitofus vrhelmet") == 1, "the info command answers");
        helper.assertTrue(execute(helper, player, "mitofus vrhelmet tier advanced") == 1, "a valid tier is set");
        helper.assertTrue(player.getData(com.orijua.mitofus.core.registry.ModAttachments.VR_HELMET_TIER.get())
                == com.orijua.mitofus.feature.vrhelmet.VrHelmetTier.ADVANCED, "the tier is written");
        List<Component> bad = new java.util.ArrayList<>();
        try {
            helper.assertTrue(executeKeeping(helper, player, "mitofus vrhelmet tier platinum", bad) == 0
                            && key(helper, bad).equals("message.mitofus.debug_vrhelmet_bad_tier"),
                    "an unknown tier fails with its own message and changes nothing: " + bad);
        } catch (CommandSyntaxException e) {
            throw new AssertionError(e.getMessage(), e);
        }
        helper.assertTrue(player.getData(com.orijua.mitofus.core.registry.ModAttachments.VR_HELMET_TIER.get())
                == com.orijua.mitofus.feature.vrhelmet.VrHelmetTier.ADVANCED, "the tier is still advanced");
        helper.assertTrue(execute(helper, player, "mitofus vrhelmet rested 120") == 120, "the rested seconds are set");
        helper.assertTrue(com.orijua.mitofus.feature.fatigue.FatigueService.restedSeconds(player) == 120, "and written");
        helper.assertTrue(execute(helper, player, "mitofus vrhelmet") == 1, "the info still answers");
        helper.assertTrue(executeFails(helper, player, "mitofus vrhelmet rested 86401"), "more than a day is refused");
        helper.assertTrue(executeFails(helper, player, "mitofus vrhelmet rested -1"), "negative is refused");
        helper.assertTrue(executeFails(helper, player, "mitofus vrhelmet tier"), "a tier without its name is not a command");
        helper.assertTrue(executeFails(helper, player, "mitofus vrhelmet rested"), "rested without seconds is not a command");
        helper.assertTrue(execute(helper, player, "mitofus vrhelmet rested 86400") == 86400, "exactly a day is accepted");
        helper.assertTrue(com.orijua.mitofus.feature.fatigue.FatigueService.restedSeconds(player) == 86400, "and written");
        execute(helper, player, "mitofus vrhelmet rested 0");
        helper.assertTrue(com.orijua.mitofus.feature.fatigue.FatigueService.restedSeconds(player) == 0, "zero is accepted and clears it");
        helper.succeed();
    }

    /** Runs a command as {@code player} with level 4 and keeps every message it sends; returns its result. */
    private static int executeKeeping(GameTestHelper helper, ServerPlayer player, String command, List<Component> answers) throws CommandSyntaxException {
        net.minecraft.commands.CommandSource sink = new net.minecraft.commands.CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                answers.add(message);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        return helper.getLevel().getServer().getCommands().getDispatcher()
                .execute(command, player.createCommandSourceStack().withPermission(4).withSource(sink));
    }

    /** The translatable part of the only answer; a failure comes wrapped in a red empty component, a success does not. */
    private static TranslatableContents only(GameTestHelper helper, List<Component> answers) {
        helper.assertTrue(answers.size() == 1, "one answer: " + answers);
        Component answer = answers.get(0);
        if (!(answer.getContents() instanceof TranslatableContents) && answer.getSiblings().size() == 1) {
            answer = answer.getSiblings().get(0);
        }
        helper.assertTrue(answer.getContents() instanceof TranslatableContents, "a translatable answer: " + answers);
        return (TranslatableContents) answer.getContents();
    }

    private static String key(GameTestHelper helper, List<Component> answers) {
        return only(helper, answers).getKey();
    }

    private static Object[] args(List<Component> answers) {
        return ((TranslatableContents) answers.get(0).getContents()).getArgs();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void debugVrHelmetCooldownIsSetAndShown(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        com.orijua.mitofus.feature.vrhelmet.VrHelmetTier tier = com.orijua.mitofus.feature.vrhelmet.VrHelmetTier.ADVANCED;
        player.setData(com.orijua.mitofus.core.registry.ModAttachments.VR_HELMET_TIER.get(), tier);
        player.setData(com.orijua.mitofus.core.registry.ModAttachments.RESTED_SECONDS.get(), 77);
        List<Component> set = new java.util.ArrayList<>();
        helper.assertTrue(executeKeeping(helper, player, "mitofus vrhelmet cooldown 120", set) == 120, "the wait is set");
        helper.assertTrue(com.orijua.mitofus.feature.fatigue.FatigueService.restedCooldown(player) == 120, "and written");
        helper.assertTrue(key(helper, set).equals("message.mitofus.debug_vrhelmet_cooldown") && args(set).length == 1 && "02:00".equals(args(set)[0]),
                "it confirms the wait as a clock: " + set);
        helper.assertTrue(com.orijua.mitofus.feature.fatigue.FatigueService.restedSeconds(player) == 77
                && player.getData(com.orijua.mitofus.core.registry.ModAttachments.VR_HELMET_TIER.get()) == tier, "the rested and the tier are not touched");

        com.orijua.mitofus.core.config.VrHelmetBalance.withOverride(com.orijua.mitofus.core.config.VrHelmetBalance.DEFAULT, () -> {
            try {
                List<Component> info = new java.util.ArrayList<>();
                helper.assertTrue(executeKeeping(helper, player, "mitofus vrhelmet", info) == 1, "the info answers");
                helper.assertTrue(key(helper, info).equals("message.mitofus.debug_vrhelmet") && args(info).length == 5, "the info has its five arguments: " + info);
                var vrhelmet = com.orijua.mitofus.core.config.VrHelmetBalance.DEFAULT;
                var fatigue = com.orijua.mitofus.core.config.FatigueBalance.current();
                helper.assertTrue(args(info)[0] instanceof Component name && name.getContents() instanceof TranslatableContents tierKey
                        && tierKey.getKey().equals("vrhelmet_tier.mitofus.advanced"), "the tier: " + args(info)[0]);
                helper.assertTrue("01:17".equals(args(info)[1]), "the rested as a clock: " + args(info)[1]);
                helper.assertTrue("02:00".equals(args(info)[2]), "the wait as a clock: " + args(info)[2]);
                helper.assertTrue(com.orijua.mitofus.feature.vrhelmet.VrHelmetText.minutes(vrhelmet.sessionMinutes(tier, fatigue)).equals(args(info)[3])
                        && com.orijua.mitofus.feature.vrhelmet.VrHelmetText.minutes(vrhelmet.recoveryMinutes(tier, fatigue)).equals(args(info)[4]),
                        "session and recovery: " + args(info)[3] + ", " + args(info)[4]);

                // A stored wait longer than the configured one is shown cut to the config (VrHelmetBalance.DEFAULT, fixed by the override above).
                player.setData(com.orijua.mitofus.core.registry.ModAttachments.RESTED_COOLDOWN.get(), 86400);
                info.clear();
                executeKeeping(helper, player, "mitofus vrhelmet", info);
                String cut = com.orijua.mitofus.feature.vrhelmet.VrHelmetText.clock(vrhelmet.restedCooldownMinutes() * 60);
                helper.assertTrue(cut.equals(args(info)[2]), "86400 s shows as the configured wait " + cut + ": " + args(info)[2]);
                helper.assertTrue(com.orijua.mitofus.feature.fatigue.FatigueService.restedCooldown(player) == 86400, "showing it does not rewrite it");
            } catch (CommandSyntaxException e) {
                throw new AssertionError(e.getMessage(), e);
            }
        });

        helper.assertTrue(executeFails(helper, player, "mitofus vrhelmet cooldown 86401"), "more than a day is refused");
        helper.assertTrue(executeFails(helper, player, "mitofus vrhelmet cooldown -1"), "negative is refused");
        helper.assertTrue(executeFails(helper, player, "mitofus vrhelmet cooldown"), "without seconds is not a command");
        List<Component> zero = new java.util.ArrayList<>();
        helper.assertTrue(executeKeeping(helper, player, "mitofus vrhelmet cooldown 0", zero) == 0 && key(helper, zero).equals("message.mitofus.debug_vrhelmet_cooldown")
                && "00:00".equals(args(zero)[0]), "zero is accepted and says so: " + zero);
        helper.assertTrue(com.orijua.mitofus.feature.fatigue.FatigueService.restedCooldown(player) == 0, "and clears it");
        helper.succeed();
    }

    private static boolean executeFails(GameTestHelper helper, ServerPlayer player, String command) {
        try {
            helper.getLevel().getServer().getCommands().getDispatcher().execute(command, player.createCommandSourceStack().withPermission(4));
            return false;
        } catch (CommandSyntaxException e) {
            return true;
        }
    }

    /** The debug goto is a permitted crossing: its flag is not left on, and it still goes (with the test level as Mitofus). */
    @GameTest(template = TestSupport.EMPTY)
    public static void gotoStillWorksAndLeavesNoCrossingFlag(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        try {
            MitofusWorld.standIn(helper.getLevel());
            helper.assertTrue(execute(helper, player, "mitofus goto sierra_nevada") == 1, "goto sierra_nevada");
            helper.assertFalse(WorldSeparation.crossingAllowed(), "the flag is off afterwards");
        } finally {
            MitofusWorld.restoreStandIn();
        }
        helper.succeed();
    }

    /** Whether the command fails with exactly that kind of exception (not just any failure). */
    private static boolean failsWith(GameTestHelper helper, ServerPlayer player, String command,
                                     com.mojang.brigadier.exceptions.CommandExceptionType type) {
        try {
            helper.getLevel().getServer().getCommands().getDispatcher().execute(command, player.createCommandSourceStack().withPermission(4));
            return false;
        } catch (CommandSyntaxException e) {
            return e.getType() == type;
        }
    }

    /** The key of the message a command fails with, or {@code null} if it does not fail. */
    private static String refusal(GameTestHelper helper, ServerPlayer player, String command) {
        try {
            helper.getLevel().getServer().getCommands().getDispatcher().execute(command, player.createCommandSourceStack().withPermission(4));
            return null;
        } catch (CommandSyntaxException e) {
            return e.getRawMessage() instanceof Component component && component.getContents() instanceof TranslatableContents contents
                    ? contents.getKey() : e.getMessage();
        }
    }

    /** Every stack of the player's inventory (main slots, armor and offhand) that is not empty. */
    private static List<ItemStack> carried(ServerPlayer player) {
        List<ItemStack> stacks = new java.util.ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void debugGiveGearGivesTheTestItems(GameTestHelper helper) throws CommandSyntaxException {
        for (StatType stat : StatType.values()) {
            String name = stat.getSerializedName();
            ServerPlayer player = TestSupport.fakePlayer(helper);
            List<Component> answers = new java.util.ArrayList<>();
            helper.assertTrue(executeKeeping(helper, player, "mitofus give gear " + name + " 10 5", answers) == 2, name + ": two items");
            helper.assertTrue(key(helper, answers).equals("message.mitofus.debug_gear_bonus") && args(answers).length == 3,
                    name + ": it confirms with the bonus message: " + answers);
            List<ItemStack> items = carried(player);
            helper.assertTrue(items.size() == 2, name + ": the sword and the chestplate: " + items);
            ItemStack sword = items.stream().filter(s -> s.is(Items.IRON_SWORD)).findFirst().orElseThrow(() -> new AssertionError(name + ": a sword"));
            ItemStack chestplate = items.stream().filter(s -> s.is(Items.IRON_CHESTPLATE)).findFirst().orElseThrow(() -> new AssertionError(name + ": a chestplate"));
            helper.assertTrue(new EquipRequirements(1, Map.of(stat, 10)).equals(sword.get(ModDataComponents.REQUIREMENTS)), name + ": the sword's requirement");
            helper.assertTrue(sword.get(ModDataComponents.ITEM_RATING) == ItemRating.UNIQUE, name + ": the sword is unique");
            helper.assertTrue(chestplate.get(ModDataComponents.ITEM_RATING) == ItemRating.UNIQUE, name + ": the chestplate is unique too");
            helper.assertTrue(chestplate.get(net.minecraft.core.component.DataComponents.ITEM_NAME) != null
                            && net.minecraft.network.chat.TextColor.fromRgb(ItemRating.UNIQUE.color())
                            .equals(chestplate.get(net.minecraft.core.component.DataComponents.ITEM_NAME).getStyle().getColor()),
                    name + ": the chestplate's name has the rating color: " + chestplate.get(net.minecraft.core.component.DataComponents.ITEM_NAME));
            helper.assertTrue(List.of(new RolledOption(DebugGear.optionId(stat), 5f)).equals(chestplate.get(ModDataComponents.ITEM_OPTIONS)),
                    name + ": the chestplate's bonus: " + chestplate.get(ModDataComponents.ITEM_OPTIONS));

            ServerPlayer other = TestSupport.fakePlayer(helper);
            List<Component> single = new java.util.ArrayList<>();
            helper.assertTrue(executeKeeping(helper, other, "mitofus give gear " + name + " 10", single) == 1, name + ": one item");
            helper.assertTrue(key(helper, single).equals("message.mitofus.debug_gear"), name + ": it confirms without the bonus: " + single);
            List<ItemStack> only = carried(other);
            helper.assertTrue(only.size() == 1 && only.get(0).is(Items.IRON_SWORD), name + ": only the sword: " + only);
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void debugGiveGearRefusals(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        helper.assertTrue("message.mitofus.debug_bad_stat".equals(refusal(helper, player, "mitofus give gear vit 10")), "an unknown stat is refused");
        helper.assertTrue(carried(player).isEmpty(), "and nothing is given");
        var tooLow = CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooLow();
        var tooHigh = CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooHigh();
        var unknown = CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand();
        helper.assertTrue(failsWith(helper, player, "mitofus give gear agility 0", tooLow), "a requirement of 0 is refused as too low");
        helper.assertTrue(failsWith(helper, player, "mitofus give gear agility -1", tooLow), "a negative one is refused as too low");
        helper.assertTrue(failsWith(helper, player, "mitofus give gear agility", unknown), "without the requirement is not a command");
        helper.assertTrue(failsWith(helper, player, "mitofus give gear agility 10 0", tooLow), "a bonus of 0 is refused as too low");
        helper.assertTrue(failsWith(helper, player, "mitofus give gear agility 100001", tooHigh), "a requirement over 100000 is refused as too high");
        helper.assertTrue(failsWith(helper, player, "mitofus give gear agility 10 100001", tooHigh), "a bonus over 100000 is refused as too high");
        helper.assertTrue(carried(player).isEmpty(), "none of them gave anything");

        ServerPlayer limit = TestSupport.fakePlayer(helper);
        helper.assertTrue(execute(helper, limit, "mitofus give gear agility 100000 100000") == 2, "the limits themselves are accepted");
        helper.assertTrue(carried(limit).size() == 2, "and give the two pieces: " + carried(limit));

        try {
            MitofusWorld.standIn(null);
            List<Component> answers = new java.util.ArrayList<>();
            helper.assertTrue(executeKeeping(helper, player, "mitofus give gear agility 10 5", answers) == 0, "outside Mitofus it gives nothing");
            helper.assertTrue(key(helper, answers).equals("message.mitofus.mitofus_only"), "and says it is only for Mitofus: " + answers);
            helper.assertTrue(carried(player).isEmpty(), "the inventory stays empty");
        } finally {
            MitofusWorld.restoreStandIn();
        }

        boolean refused = false;
        try {
            helper.getLevel().getServer().getCommands().getDispatcher()
                    .execute("mitofus give gear agility 10", player.createCommandSourceStack().withPermission(0));
        } catch (CommandSyntaxException e) {
            refused = true;
        }
        helper.assertTrue(refused, "without permission it is not even a command");
        helper.assertTrue(carried(player).isEmpty(), "and gives nothing");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void debugGiveGearDropsWhatDoesNotFit(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        }
        helper.assertTrue(execute(helper, player, "mitofus give gear agility 10 5") == 2, "it still reports the two pieces");
        helper.assertTrue(carried(player).stream().allMatch(s -> s.is(Items.STONE)), "the inventory keeps only stone");

        var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(6));
        List<ItemStack> dropped = drops.stream().map(net.minecraft.world.entity.item.ItemEntity::getItem).toList();
        try {
            helper.assertTrue(dropped.stream().anyMatch(s -> s.is(Items.IRON_SWORD) && s.has(ModDataComponents.REQUIREMENTS)), "the sword was dropped: " + dropped);
            helper.assertTrue(dropped.stream().anyMatch(s -> s.is(Items.IRON_CHESTPLATE) && s.has(ModDataComponents.ITEM_OPTIONS)), "the chestplate was dropped: " + dropped);
        } finally {
            drops.forEach(net.minecraft.world.entity.Entity::discard);
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void debugGiveGearSuggestsTheStats(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        List<String> suggested = dispatcher.getCompletionSuggestions(
                dispatcher.parse("mitofus give gear ", player.createCommandSourceStack().withPermission(4)))
                .join().getList().stream().map(Suggestion::getText).sorted().toList();
        List<String> expected = java.util.Arrays.stream(StatType.values()).map(StatType::getSerializedName).sorted().toList();
        helper.assertTrue(suggested.equals(expected), "the five stats: " + suggested);
        helper.succeed();
    }

    /** The map's places (C4): goto lands on dry ground near them, refuses the sea and unknown names, and completes them. */
    @GameTest(template = TestSupport.EMPTY)
    public static void gotoReachesTheMapPlacesOrRefusesTheSea(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ServerLevel level = helper.getLevel();
        MitofusWorld.standIn(level); // the test server has no Mitofus dimension: its flat overworld plays it
        try {
            helper.assertTrue(SiteLanding.sites().containsAll(List.of(WorldSites.COVADONGA, WorldSites.ALTAMIRA, WorldSites.TORRE_DEL_ORO,
                    WorldSites.EIBAR, "sierra_nevada", "desierto_de_tabernas", "mar_del_este")), "structures and map places: " + SiteLanding.sites());
            helper.assertTrue(SiteLanding.sites().size() == SiteLanding.sites().stream().distinct().count(), "each name once");

            MitofusMapData map = MitofusMapData.west();
            MitofusPlaces.Place sierra = MitofusPlaces.west().get("sierra_nevada").orElseThrow();
            BlockPos landing = SiteLanding.findPlace(level, "sierra_nevada").orElseThrow(() -> new AssertionError("the Sierra Nevada has a landing"));
            int[] expected = MitofusPlaces.landing(map, sierra).orElseThrow();
            helper.assertTrue(Math.abs(landing.getX() - expected[0]) <= SiteLanding.DRY_SEARCH && Math.abs(landing.getZ() - expected[1]) <= SiteLanding.DRY_SEARCH,
                    "Sierra Nevada: near its cell on the map, " + landing);
            helper.assertTrue(map.isLandCell(map.colAtBlock(landing.getX()), map.rowAtBlock(landing.getZ())), "on a land cell of the map");
            helper.assertTrue(level.getBlockState(landing).getCollisionShape(level, landing).isEmpty()
                    && level.getBlockState(landing.above()).getCollisionShape(level, landing.above()).isEmpty(), "room to stand");
            helper.assertFalse(level.getBlockState(landing.below()).isAir(), "on the ground");
            helper.assertTrue(level.getFluidState(landing.below()).isEmpty(), "not on water");

            // Every land place of the map that is not also a structure of the world (those have their own rules).
            long started = System.nanoTime();
            int visited = 0;
            for (MitofusPlaces.Place place : MitofusPlaces.west().all()) {
                if (!place.land() || SiteLanding.STRUCTURE_SITES.contains(place.id())) {
                    continue;
                }
                helper.assertTrue(SiteLanding.locate(level, place.id()).reason() != SiteLanding.Reason.UNKNOWN, place.id() + " is known");
                SiteLanding.Landing there = SiteLanding.locate(level, place.id());
                helper.assertTrue(there.reason() == SiteLanding.Reason.OK || there.reason() == SiteLanding.Reason.SEA, place.id() + ": " + there);
                if (there.reason() == SiteLanding.Reason.OK) {
                    BlockPos spot = there.pos().get();
                    helper.assertTrue(level.getFluidState(spot.below()).isEmpty(), place.id() + " lands on dry ground: " + spot);
                    visited++;
                }
            }
            Mitofus.LOGGER.info("goto: {} map places landed in {} ms", visited, (System.nanoTime() - started) / 1_000_000);
            helper.assertTrue(visited >= 2, "most land places have a landing: " + visited);

            helper.assertTrue(execute(helper, player, "mitofus goto sierra_nevada") == 1, "goto sierra_nevada");
            helper.assertTrue(execute(helper, player, "mitofus goto desierto_de_tabernas") == 1, "goto the desert");
            helper.assertTrue("message.mitofus.debug_goto_sea".equals(refusal(helper, player, "mitofus goto mar_del_este")),
                    "the sea is refused as sea");
            helper.assertTrue("message.mitofus.debug_goto_unknown".equals(refusal(helper, player, "mitofus goto atlantis")),
                    "an unknown name is refused");

            List<String> suggested = level.getServer().getCommands().getDispatcher()
                    .getCompletionSuggestions(level.getServer().getCommands().getDispatcher()
                            .parse("mitofus goto de", player.createCommandSourceStack().withPermission(4)))
                    .join().getList().stream().map(Suggestion::getText).toList();
            helper.assertTrue(suggested.contains("desierto_de_tabernas"), "desierto_de_tabernas is suggested: " + suggested);
        } finally {
            MitofusWorld.restoreStandIn();
        }
        helper.succeed();
    }
}
