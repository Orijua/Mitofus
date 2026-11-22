package com.orijua.mitofus.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.VrHelmetBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.progression.PlayerStats;
import com.orijua.mitofus.feature.progression.ProgressionRules;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.progression.XpRate;
import com.orijua.mitofus.feature.progression.XpRateRules;
import com.orijua.mitofus.feature.progression.XpRateService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** The XP per hour: what feeds it, when its clock runs, and that it is kept on death and cleared by a reset. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class XpRateTests {
    private XpRateTests() {
    }

    private static int execute(GameTestHelper helper, ServerPlayer player, String command) throws CommandSyntaxException {
        return helper.getLevel().getServer().getCommands().getDispatcher()
                .execute(command, player.createCommandSourceStack().withPermission(4));
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void awardedXpIsRecorded(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ProgressionService.awardXp(player, 100);
        helper.assertTrue(XpRateService.get(player).currentXp() == 100, "100 recorded: " + XpRateService.get(player));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void restedXpIsRecordedWithTheBonus(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ProgressionService.set(player, PlayerStats.DEFAULT.withProgress(30, 0, 0));
        player.setData(ModAttachments.RESTED_SECONDS.get(), 300);
        ProgressionService.awardXp(player, 100);
        int gained = ProgressionService.get(player).xp();
        int expected = ProgressionRules.restedXp(100, VrHelmetBalance.current().restedXpPercent());
        helper.assertTrue(expected > 100 && gained == expected, "rested gain: expected " + expected + ", got " + gained);
        helper.assertTrue(XpRateService.get(player).currentXp() == gained, "the raised amount is recorded: " + XpRateService.get(player));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void sixtySecondsCloseAMinute(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ProgressionService.awardXp(player, 10);
        TestSupport.tickMany(player, 59, XpRateService::tickSecond);
        helper.assertTrue(XpRateService.get(player).minutes().isEmpty(), "59 s: not closed");
        XpRateService.tickSecond(player);
        XpRate rate = XpRateService.get(player);
        helper.assertTrue(rate.minutes().equals(List.of(10)) && rate.currentXp() == 0 && rate.currentSeconds() == 0, "60 s: " + rate);
        helper.assertTrue(XpRateRules.xpPerHour(rate).orElse(-1) == 600, "10 XP in a minute is 600 an hour");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theClockStopsOutsideMitofus(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.outsideMitofus(helper, () -> {
            TestSupport.tickMany(player, 30, XpRateService::tickSecond);
            helper.assertTrue(XpRateService.get(player).equals(XpRate.EMPTY), "nothing counted: " + XpRateService.get(player));
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void settingTheLevelIsNotXp(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        execute(helper, player, "mitofus set level 20");
        helper.assertTrue(ProgressionService.get(player).level() == 20, "the level is set");
        helper.assertTrue(XpRateService.get(player).equals(XpRate.EMPTY), "but it is no gain: " + XpRateService.get(player));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theMeasurementSurvivesDeath(GameTestHelper helper) {
        ServerPlayer old = TestSupport.fakePlayer(helper);
        ProgressionService.awardXp(old, 40);
        XpRate before = XpRateService.get(old);
        ServerPlayer fresh = TestSupport.fakePlayer(helper);
        fresh.restoreFrom(old, false);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(fresh, false));
        helper.assertTrue(XpRateService.get(fresh).equals(before) && before.currentXp() == 40,
                "kept: " + XpRateService.get(fresh) + " vs " + before);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void resetAllClearsIt(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ProgressionService.awardXp(player, 40);
        ModAttachments.resetAll(player);
        helper.assertTrue(XpRateService.get(player).equals(XpRate.EMPTY), "cleared: " + XpRateService.get(player));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theDebugCommandsWork(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ProgressionService.set(player, PlayerStats.DEFAULT);
        ProgressionService.awardXp(player, 10);
        helper.assertTrue(execute(helper, player, "mitofus xp") == 1, "xp shows");
        helper.assertTrue(execute(helper, player, "mitofus xp reset") == 1, "xp reset runs");
        helper.assertTrue(XpRateService.get(player).equals(XpRate.EMPTY), "and clears the measurement");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void simulatingAVillagerKillIsRefused(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        int xpBefore = ProgressionService.get(player).xp();
        boolean refused = false;
        try {
            execute(helper, player, "mitofus simulate kill minecraft:villager");
        } catch (CommandSyntaxException e) {
            refused = true;
        }
        helper.assertTrue(refused, "a villager kill is not simulated");
        helper.assertTrue(ProgressionService.get(player).xp() == xpBefore, "and the XP does not change");
        helper.succeed();
    }
}
