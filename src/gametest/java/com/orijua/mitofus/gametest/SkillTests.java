package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.skill.SkillService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class SkillTests {
    private static final ResourceLocation SANCHO_LINK = Mitofus.id("sancho_link");
    private static final ResourceLocation IRON_GUARD = Mitofus.id("iron_guard");

    private SkillTests() {
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void learnedSkillFillsFirstFreeSlot(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        helper.assertTrue(SkillService.learn(player, SANCHO_LINK), "sancho_link should be defined in the datapack");
        helper.assertTrue(SkillService.learn(player, IRON_GUARD), "iron_guard should be defined");
        helper.assertFalse(SkillService.learn(player, SANCHO_LINK), "learning twice must fail");
        helper.assertTrue(SkillService.get(player).slot(1).filter(IRON_GUARD::equals).isPresent(), "second skill should land in slot 2");

        SkillService.Result noSword = SkillService.use(player, 0);
        helper.assertTrue(noSword == SkillService.Result.WRONG_ITEM, "sancho_link needs a sword, got " + noSword);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void sanchoLinkHitsMobInFront(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        SkillService.learn(player, SANCHO_LINK);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(0.5, 1, 2.5));
        zombie.setNoAi(true);

        SkillService.Result result = SkillService.use(player, 0);
        helper.assertTrue(result == SkillService.Result.SUCCESS, "skill should fire, got " + result);
        helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "zombie in front should be hit");

        SkillService.Result again = SkillService.use(player, 0);
        helper.assertTrue(again == SkillService.Result.ON_COOLDOWN, "second use should be on cooldown, got " + again);
        helper.succeed();
    }

    /** The warrior's Heavy Strike hits the one in front harder than a plain sancho_link would, and not one off to the side. */
    @GameTest(template = TestSupport.EMPTY)
    public static void heavyStrikeHitsOnlyTheTargetInFront(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        helper.assertTrue(SkillService.learn(player, Mitofus.id("heavy_strike")), "heavy_strike should be defined");
        Zombie front = helper.spawn(EntityType.ZOMBIE, new Vec3(0.5, 1, 2.5));
        Zombie side = helper.spawn(EntityType.ZOMBIE, new Vec3(2.5, 1, 1.5));
        front.setNoAi(true);
        side.setNoAi(true);
        SkillService.Result result = SkillService.use(player, 0);
        helper.assertTrue(result == SkillService.Result.SUCCESS, "skill should fire, got " + result);
        helper.assertTrue(front.getHealth() < front.getMaxHealth(), "the one in front is hit");
        helper.assertTrue(side.getHealth() == side.getMaxHealth(), "the one to the side is not");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void skillIconIsReadFromJson(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        helper.assertTrue(SkillService.definition(player, SANCHO_LINK).flatMap(def -> def.icon()).filter(Items.IRON_SWORD::equals).isPresent(),
                "sancho_link should use the iron sword as icon");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void skillNeedsMana(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        SkillService.learn(player, SANCHO_LINK);
        float cost = SkillService.definition(player, SANCHO_LINK).orElseThrow().manaCost();

        player.setData(ModAttachments.MANA, cost - 1);
        helper.assertTrue(SkillService.use(player, 0) == SkillService.Result.NO_MANA, "one point short of the cost");
        helper.assertTrue(player.getData(ModAttachments.MANA) == cost - 1, "a refused cast spends nothing");

        player.setData(ModAttachments.MANA, cost + 5);
        helper.assertTrue(SkillService.use(player, 0) == SkillService.Result.SUCCESS, "with enough mana it casts");
        helper.assertTrue(player.getData(ModAttachments.MANA) == 5f, "and spends exactly the cost, left " + player.getData(ModAttachments.MANA));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY, timeoutTicks = 200)
    public static void cooldownRunsOut(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        SkillService.learn(player, SANCHO_LINK);
        player.setData(ModAttachments.MANA, 1000f);
        int cooldown = SkillService.definition(player, SANCHO_LINK).orElseThrow().cooldownTicks();
        helper.assertTrue(SkillService.use(player, 0) == SkillService.Result.SUCCESS, "the first cast");
        helper.assertTrue(SkillService.use(player, 0) == SkillService.Result.ON_COOLDOWN, "then it rests");
        helper.runAtTickTime(cooldown + 2, () -> {
            helper.assertTrue(SkillService.use(player, 0) == SkillService.Result.SUCCESS, "it casts again once the cooldown is over");
            helper.succeed();
        });
    }

    /** Only open slots take a skill and cast: at level 1 a player has three. */
    @GameTest(template = TestSupport.EMPTY)
    public static void lockedSlotsRefuseSkills(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        SkillService.learn(player, IRON_GUARD);
        SkillService.assign(player, 5, java.util.Optional.of(IRON_GUARD));
        helper.assertTrue(SkillService.get(player).slot(5).isEmpty(), "slot 6 is still locked at level 1");
        helper.assertTrue(SkillService.use(player, 5) == SkillService.Result.EMPTY_SLOT, "and casts nothing");
        SkillService.assign(player, 2, java.util.Optional.of(IRON_GUARD));
        helper.assertTrue(SkillService.get(player).slot(2).filter(IRON_GUARD::equals).isPresent(), "slot 3 is open");
        helper.succeed();
    }
}
