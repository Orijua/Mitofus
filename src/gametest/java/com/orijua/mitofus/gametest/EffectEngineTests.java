package com.orijua.mitofus.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.effect.AreaDamageEffect;
import com.orijua.mitofus.feature.effect.Condition;
import com.orijua.mitofus.feature.effect.Effect;
import com.orijua.mitofus.feature.effect.EffectContext;
import com.orijua.mitofus.feature.effect.EffectEngine;
import com.orijua.mitofus.feature.effect.EffectSource;
import com.orijua.mitofus.feature.effect.EffectTargets;
import com.orijua.mitofus.feature.effect.EffectType;
import com.orijua.mitofus.feature.effect.ProjectileEffect;
import com.orijua.mitofus.feature.effect.SelfBuffEffect;
import com.orijua.mitofus.feature.progression.PlayerStats;
import com.orijua.mitofus.feature.skill.SkillService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** The effect engine: data decoding, the Mitofus-only rule, mobs as casters and PvP between players. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class EffectEngineTests {
    private static final EffectSource SOURCE = new EffectSource(EffectSource.Kind.BOSS, Mitofus.id("test"), Component.literal("test"));

    private EffectEngineTests() {
    }

    private static Effect decode(String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        return Effect.CODEC.parse(JsonOps.INSTANCE, object).getOrThrow(IllegalStateException::new);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void shippedSkillsDecodeToEngineEffects(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        expect(helper, player, "sancho_link", EffectType.AREA_DAMAGE);
        expect(helper, player, "rending_slash", EffectType.AREA_DAMAGE);
        expect(helper, player, "heavy_strike", EffectType.AREA_DAMAGE);
        expect(helper, player, "flame_bolt", EffectType.PROJECTILE);
        expect(helper, player, "iron_guard", EffectType.SELF_BUFF);
        expect(helper, player, "war_cry", EffectType.SELF_BUFF);
        helper.succeed();
    }

    private static void expect(GameTestHelper helper, ServerPlayer player, String skill, EffectType type) {
        EffectType found = SkillService.definition(player, Mitofus.id(skill)).orElseThrow().effects().get(0).type();
        helper.assertTrue(found == type, skill + " should be " + type + ", is " + found);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void effectCodecKeepsTheShortTypeName(GameTestHelper helper) {
        Effect effect = decode("{\"type\":\"projectile\",\"count\":2,\"speed\":1.5}");
        helper.assertTrue(effect instanceof ProjectileEffect p && p.count() == 2 && p.speed() == 1.5, "decodes the fields");
        JsonObject written = Effect.CODEC.encodeStart(JsonOps.INSTANCE, effect).getOrThrow(IllegalStateException::new).getAsJsonObject();
        helper.assertTrue(written.get("type").getAsString().equals("projectile"), "no namespace in type: " + written);
        helper.assertTrue(decode(written.toString()).equals(effect), "round trip");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void badEffectDataFails(GameTestHelper helper) {
        helper.assertTrue(Effect.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"nope\"}")).error().isPresent(),
                "unknown type");
        helper.assertTrue(Effect.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"area_damage\"}")).error().isPresent(),
                "area_damage without range");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void conditionsDecodeAndTest(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        EffectContext context = EffectContext.of(player, SOURCE, PlayerStats.DEFAULT);
        Condition inMitofus = Condition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"in_mitofus\"}"))
                .getOrThrow(IllegalStateException::new);
        Condition needsTarget = Condition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"requires_target\"}"))
                .getOrThrow(IllegalStateException::new);
        helper.assertTrue(inMitofus.test(context), "the test level plays Mitofus");
        helper.assertFalse(needsTarget.test(context), "no target");
        helper.assertTrue(needsTarget.test(context.withTarget(helper.spawn(EntityType.PIG, new Vec3(0.5, 1, 2.5)))), "with a target");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void engineRefusesOutsideMitofus(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Effect buff = new SelfBuffEffect(List.of(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100)));
        VrHelmetTests.outsideMitofus(helper, () -> {
            boolean applied = EffectEngine.apply(buff, EffectContext.of(player, SOURCE, PlayerStats.DEFAULT));
            helper.assertFalse(applied, "the engine says nothing was applied");
            helper.assertFalse(player.hasEffect(MobEffects.DAMAGE_RESISTANCE), "and nothing was");
        });
        helper.assertTrue(EffectEngine.apply(buff, EffectContext.of(player, SOURCE, PlayerStats.DEFAULT)), "in Mitofus it applies");
        helper.assertTrue(player.hasEffect(MobEffects.DAMAGE_RESISTANCE), "the buff is on");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aFailedConditionStopsTheEffect(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Effect buff = new SelfBuffEffect(List.of(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100)));
        Condition needsTarget = new com.orijua.mitofus.feature.effect.RequiresTargetCondition();
        helper.assertFalse(EffectEngine.apply(buff, List.of(needsTarget), EffectContext.of(player, SOURCE, PlayerStats.DEFAULT)),
                "no target: refused");
        helper.assertFalse(player.hasEffect(MobEffects.DAMAGE_RESISTANCE), "nothing applied");
        helper.succeed();
    }

    /** Bosses use the engine: a mob caster hurts a pig, not itself; buffs itself; owns its fireballs. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aMobCasterUsesTheEngine(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(0.5, 1, 0.5));
        zombie.setNoAi(true);
        zombie.setYRot(0.0f);
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(0.5, 1, 2.5));
        pig.setNoAi(true);
        EffectContext context = EffectContext.of(zombie, SOURCE, PlayerStats.DEFAULT);

        EffectEngine.apply(new AreaDamageEffect(5, 360, 1.0, 0, 0, List.of()), context);
        helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the pig is hurt");
        helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(), "the caster is not");

        EffectEngine.apply(new SelfBuffEffect(List.of(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100))), context);
        helper.assertTrue(zombie.hasEffect(MobEffects.DAMAGE_RESISTANCE), "the mob is buffed");

        EffectEngine.apply(new ProjectileEffect(1, 1.0), context);
        List<SmallFireball> balls = helper.getLevel().getEntitiesOfClass(SmallFireball.class, new AABB(helper.absolutePos(new net.minecraft.core.BlockPos(0, 1, 0))).inflate(20));
        helper.assertTrue(balls.stream().anyMatch(b -> b.getOwner() == zombie), "the fireball belongs to the caster");
        balls.forEach(SmallFireball::discard);
        helper.succeed();
    }

    /**
     * Who an effect may hit. NeoForge's FakePlayer refuses {@code canHarmPlayer} always, so the player-versus-player rule
     * ({@code ServerPlayer.canHarmPlayer}, which follows the server's PvP) is left to the in-game test list.
     */
    @GameTest(template = TestSupport.EMPTY)
    public static void effectTargetsAreEnemiesOnly(GameTestHelper helper) {
        ServerPlayer caster = TestSupport.fakePlayer(helper);
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(0.5, 1, 2.5));
        helper.assertTrue(EffectTargets.canAffect(caster, pig), "a living mob can be affected");
        helper.assertFalse(EffectTargets.canAffect(caster, caster), "never the caster");
        pig.kill();
        helper.assertFalse(EffectTargets.canAffect(caster, pig), "nor a dead one");
        helper.succeed();
    }

    private static final String BUFF = "{\"type\":\"self_buff\",\"effects\":[{\"id\":\"minecraft:resistance\",\"amplifier\":0,\"duration\":20}]}";

    private static com.mojang.serialization.DataResult<com.orijua.mitofus.feature.skill.SkillDefinition> skill(
            GameTestHelper helper, String effects, String conditions) {
        String json = "{\"name\":{\"text\":\"t\"},\"mana_cost\":1,\"cooldown_ticks\":1,\"effects\":" + effects
                + (conditions == null ? "" : ",\"conditions\":" + conditions) + "}";
        return com.orijua.mitofus.feature.skill.SkillDefinition.CODEC.parse(
                net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()), JsonParser.parseString(json));
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void skillV2ReadsEffectListAndConditions(GameTestHelper helper) {
        var parsed = skill(helper, "[" + BUFF + "," + BUFF + "]",
                "[{\"type\":\"requires_target\"},{\"type\":\"while_sneaking\"}]").getOrThrow(IllegalStateException::new);
        helper.assertTrue(parsed.effects().size() == 2, "two effects");
        helper.assertTrue(parsed.conditions().size() == 2 && parsed.needsTarget(), "two conditions, one needs a target");
        helper.assertFalse(parsed.dealsDamage(), "a buff hurts nobody");
        helper.assertTrue(skill(helper, "[" + BUFF + "]", null).getOrThrow(IllegalStateException::new).conditions().isEmpty(),
                "conditions are optional");
        helper.assertTrue(skill(helper, "[]", null).error().isPresent(), "a skill needs an effect");
        helper.assertTrue(skill(helper, BUFF, null).error().isPresent(), "the old single-object form is gone");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void sneakingConditionAndEffectListRun(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        var def = skill(helper, "[" + BUFF + "]", "[{\"type\":\"while_sneaking\"}]").getOrThrow(IllegalStateException::new);
        EffectContext context = EffectContext.of(player, SOURCE, PlayerStats.DEFAULT);
        helper.assertFalse(EffectEngine.apply(def.effects(), def.conditions(), context), "standing: refused");
        helper.assertFalse(player.hasEffect(MobEffects.DAMAGE_RESISTANCE), "nothing applied");
        player.setShiftKeyDown(true);
        helper.assertTrue(EffectEngine.apply(def.effects(), def.conditions(), context), "sneaking: runs");
        helper.assertTrue(player.hasEffect(MobEffects.DAMAGE_RESISTANCE), "and applies");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void lookTargetFindsWhatIsInFront(GameTestHelper helper) {
        Zombie caster = helper.spawn(EntityType.ZOMBIE, new Vec3(0.5, 1, 0.5));
        caster.setNoAi(true);
        caster.setYRot(0.0f);
        caster.setXRot(0.0f);
        helper.assertTrue(EffectTargets.lookTarget(caster, 8).isEmpty(), "nothing in front yet");
        Zombie other = helper.spawn(EntityType.ZOMBIE, new Vec3(0.5, 1, 2.5)); // as tall as the caster: the level look ray hits it (inside the walled 3x3x3 test box)
        other.setNoAi(true);
        helper.assertTrue(EffectTargets.lookTarget(caster, 8).filter(other::equals).isPresent(), "the creature in front");
        helper.assertTrue(EffectTargets.lookTarget(caster, 1).isEmpty(), "out of reach");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void lookTargetStopsAtWalls(GameTestHelper helper) {
        Zombie caster = helper.spawn(EntityType.ZOMBIE, new Vec3(0.5, 1, 0.5));
        caster.setNoAi(true);
        caster.setYRot(0.0f);
        caster.setXRot(0.0f);
        Zombie other = helper.spawn(EntityType.ZOMBIE, new Vec3(0.5, 1, 2.5));
        other.setNoAi(true);
        helper.assertTrue(EffectTargets.lookTarget(caster, 8).isPresent(), "visible without a wall");
        for (int y = 0; y <= 2; y++) {
            helper.setBlock(0, y, 1, Blocks.STONE);
        }
        helper.assertTrue(EffectTargets.lookTarget(caster, 8).isEmpty(), "a wall in between blocks the target");
        helper.succeed();
    }

    /** An area skill hits what the caster can see: a wall in between protects, and it hits again once the wall is gone. */
    @GameTest(template = TestSupport.EMPTY)
    public static void areaDamageDoesNotGoThroughWalls(GameTestHelper helper) {
        Zombie caster = helper.spawn(EntityType.ZOMBIE, new Vec3(0.5, 1, 0.5));
        caster.setNoAi(true);
        caster.setYRot(0.0f);
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(0.5, 1, 2.5));
        pig.setNoAi(true);
        EffectContext context = EffectContext.of(caster, SOURCE, PlayerStats.DEFAULT);
        for (int y = 0; y <= 2; y++) {
            helper.setBlock(0, y, 1, Blocks.STONE);
        }
        EffectEngine.apply(new AreaDamageEffect(5, 360, 1.0, 0, 0, List.of()), context);
        helper.assertTrue(pig.getHealth() == pig.getMaxHealth(), "the wall protects the pig");
        for (int y = 0; y <= 2; y++) {
            helper.setBlock(0, y, 1, Blocks.AIR);
        }
        EffectEngine.apply(new AreaDamageEffect(5, 360, 1.0, 0, 0, List.of()), context);
        helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "without the wall it is hurt");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aSkillCanCarryAPassiveTrait(GameTestHelper helper) {
        String passive = "{\"bonuses\":[{\"type\":\"max_mana\",\"amount\":10}]}";
        var withPassive = com.orijua.mitofus.feature.skill.SkillDefinition.CODEC.parse(
                net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()),
                JsonParser.parseString("{\"name\":{\"text\":\"t\"},\"mana_cost\":1,\"cooldown_ticks\":1,\"effects\":[" + BUFF
                        + "],\"passive\":" + passive + "}")).getOrThrow(IllegalStateException::new);
        helper.assertTrue(withPassive.passive().isPresent() && withPassive.passive().get().bonuses().size() == 1, "the passive reads");
        helper.assertTrue(skill(helper, "[" + BUFF + "]", null).getOrThrow(IllegalStateException::new).passive().isEmpty(),
                "and is optional");
        helper.succeed();
    }

    /** Mastery reaches the effects as the skill level: a higher level hits harder and a cone that hits nobody is idle. */
    @GameTest(template = TestSupport.EMPTY)
    public static void skillLevelScalesAreaDamageAndIdleIsReported(GameTestHelper helper) {
        Zombie caster = helper.spawn(EntityType.ZOMBIE, new Vec3(0.5, 1, 0.5));
        caster.setNoAi(true);
        caster.setYRot(0.0f);
        AreaDamageEffect effect = new AreaDamageEffect(5, 360, 1.0, 0, 0, List.of(), 0.5);
        EffectContext context = EffectContext.of(caster, SOURCE, PlayerStats.DEFAULT);
        helper.assertTrue(EffectEngine.run(List.of(effect), List.of(), context) == EffectEngine.Outcome.IDLE, "nobody there: idle");

        Pig low = helper.spawn(EntityType.PIG, new Vec3(0.5, 1, 2.5));
        low.setNoAi(true);
        helper.assertTrue(EffectEngine.run(List.of(effect), List.of(), context) == EffectEngine.Outcome.ACTED, "a pig in the cone: acted");
        float firstHit = low.getMaxHealth() - low.getHealth();
        low.setHealth(low.getMaxHealth());
        low.invulnerableTime = 0;
        EffectEngine.run(List.of(effect), List.of(), context.withSkillLevel(3));
        float levelThreeHit = low.getMaxHealth() - low.getHealth();
        helper.assertTrue(levelThreeHit > firstHit, "level 3 hits harder: " + levelThreeHit + " vs " + firstHit);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void masteryCountsOnlySuccessfulUsesOfLearnedSkills(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        net.minecraft.resources.ResourceLocation id = Mitofus.id("iron_guard");
        helper.assertTrue(player.getData(com.orijua.mitofus.core.registry.ModAttachments.SKILL_MASTERY).level(id) == 1, "starts at 1");
        SkillService.learn(player, id);
        player.setData(com.orijua.mitofus.core.registry.ModAttachments.MANA, 1000f);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
        helper.assertTrue(SkillService.use(player, 0) == SkillService.Result.SUCCESS, "the buff casts");
        helper.assertTrue(player.getData(com.orijua.mitofus.core.registry.ModAttachments.SKILL_MASTERY).uses(id) == 1, "and counts one use");
        helper.succeed();
    }
}
