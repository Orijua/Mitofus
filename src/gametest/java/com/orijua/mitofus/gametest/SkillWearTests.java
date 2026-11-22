package com.orijua.mitofus.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.skill.SkillDefinition;
import com.orijua.mitofus.feature.skill.SkillService;
import com.orijua.mitofus.feature.skill.WeaponWearRules;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class SkillWearTests {
    private static final ResourceLocation SANCHO_LINK = Mitofus.id("sancho_link");
    private static final ResourceLocation IRON_GUARD = Mitofus.id("iron_guard");

    private SkillWearTests() {
    }

    private static ItemStack hold(ServerPlayer player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return stack;
    }

    private static SkillDefinition sancho(ServerPlayer player) {
        return SkillService.definition(player, SANCHO_LINK).orElseThrow();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void requiredSkillWearsTheSword(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ItemStack sword = hold(player, new ItemStack(Items.IRON_SWORD));
        SkillService.learn(player, SANCHO_LINK);
        player.setData(ModAttachments.MANA, 1000f);
        int wear = sancho(player).weaponWear();
        helper.assertTrue(wear > 0, "sancho_link should wear the weapon");
        helper.assertTrue(SkillService.use(player, 0) == SkillService.Result.SUCCESS, "the cast succeeds");
        helper.assertTrue(sword.getDamageValue() == wear, "sword should lose " + wear + ", lost " + sword.getDamageValue());
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void cooldownRefusalWearsNothing(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ItemStack sword = hold(player, new ItemStack(Items.IRON_SWORD));
        SkillService.learn(player, SANCHO_LINK);
        player.setData(ModAttachments.MANA, 1000f);
        helper.assertTrue(SkillService.use(player, 0) == SkillService.Result.SUCCESS, "first cast");
        int worn = sword.getDamageValue();
        helper.assertTrue(SkillService.use(player, 0) == SkillService.Result.ON_COOLDOWN, "second cast is on cooldown");
        helper.assertTrue(sword.getDamageValue() == worn, "a cooldown refusal wears nothing");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void wrongItemWearsNothing(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ItemStack pickaxe = hold(player, new ItemStack(Items.IRON_PICKAXE));
        SkillService.learn(player, SANCHO_LINK);
        player.setData(ModAttachments.MANA, 1000f);
        helper.assertTrue(SkillService.use(player, 0) == SkillService.Result.WRONG_ITEM, "a pickaxe is not a sword");
        helper.assertTrue(pickaxe.getDamageValue() == 0, "a wrong-item refusal wears nothing");
        helper.assertTrue(player.getData(ModAttachments.MANA) == 1000f, "a wrong-item refusal costs no mana");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(SkillService.use(player, 0) == SkillService.Result.WRONG_ITEM, "an empty hand is not a sword either");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void manaRefusalWearsNothing(GameTestHelper helper) {
        ServerPlayer poor = TestSupport.fakePlayer(helper);
        ItemStack other = hold(poor, new ItemStack(Items.IRON_SWORD));
        SkillService.learn(poor, SANCHO_LINK);
        poor.setData(ModAttachments.MANA, sancho(poor).manaCost() - 1);
        helper.assertTrue(SkillService.use(poor, 0) == SkillService.Result.NO_MANA, "not enough mana");
        helper.assertTrue(other.getDamageValue() == 0, "a mana refusal wears nothing");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void creativeWearsNothing(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setGameMode(GameType.CREATIVE);
        ItemStack sword = hold(player, new ItemStack(Items.IRON_SWORD));
        SkillService.learn(player, SANCHO_LINK);
        player.setData(ModAttachments.MANA, 1000f);
        helper.assertTrue(SkillService.use(player, 0) == SkillService.Result.SUCCESS, "the cast succeeds");
        helper.assertTrue(sword.getDamageValue() == 0, "creative doesn't wear the weapon");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void breakingWeaponIsHarmless(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ItemStack sword = hold(player, new ItemStack(Items.IRON_SWORD));
        sword.setDamageValue(sword.getMaxDamage() - 1);
        SkillService.learn(player, SANCHO_LINK);
        player.setData(ModAttachments.MANA, 1000f);
        helper.assertTrue(sancho(player).weaponWear() > sword.getMaxDamage() - sword.getDamageValue(),
                "sancho_link must wear more than the sword has left, or this test proves nothing");
        float cost = sancho(player).manaCost();
        SkillService.Result result = SkillService.use(player, 0);
        helper.assertTrue(result == SkillService.Result.SUCCESS, "the cast still succeeds, got " + result);
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the sword broke");
        helper.assertTrue(player.getData(ModAttachments.MANA) == 1000f - cost, "the mana was spent despite the break");
        helper.assertTrue(SkillService.get(player).cooldownRemaining(SANCHO_LINK, helper.getLevel().getGameTime()) > 0,
                "the cooldown started despite the break");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void skillWithoutRequiredItemWearsNothing(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ItemStack sword = hold(player, new ItemStack(Items.IRON_SWORD));
        SkillService.learn(player, IRON_GUARD);
        player.setData(ModAttachments.MANA, 1000f);
        helper.assertTrue(SkillService.use(player, 0) == SkillService.Result.SUCCESS, "iron_guard casts");
        helper.assertTrue(sword.getDamageValue() == 0, "iron_guard doesn't wear the weapon");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void unbreakableWeaponWearsNothing(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ItemStack sword = hold(player, new ItemStack(Items.IRON_SWORD));
        sword.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        SkillService.learn(player, SANCHO_LINK);
        player.setData(ModAttachments.MANA, 1000f);
        helper.assertTrue(SkillService.use(player, 0) == SkillService.Result.SUCCESS, "the cast succeeds");
        helper.assertTrue(sword.getDamageValue() == 0, "an unbreakable sword doesn't wear");
        helper.succeed();
    }

    /** Minimal valid skill (reduced copy of iron_guard.json); update it if the Effect schema changes. */
    private static JsonObject skillJson(Integer weaponWear) {
        JsonObject json = JsonParser.parseString("""
                {
                  "name": { "text": "test" },
                  "mana_cost": 5,
                  "cooldown_ticks": 20,
                  "required_item": "minecraft:swords",
                  "effects": [ {
                    "type": "self_buff",
                    "effects": [ { "id": "minecraft:resistance", "amplifier": 0, "duration": 20 } ]
                  } ]
                }
                """).getAsJsonObject();
        if (weaponWear != null) {
            json.addProperty("weapon_wear", weaponWear);
        }
        return json;
    }

    private static DataResult<SkillDefinition> parse(GameTestHelper helper, Integer weaponWear) {
        return SkillDefinition.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()), skillJson(weaponWear));
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void weaponWearCodecDefaultsAndAcceptsLimits(GameTestHelper helper) {
        SkillDefinition missing = parse(helper, null).result().orElse(null);
        helper.assertTrue(missing != null, "the minimal skill parses");
        helper.assertTrue(missing.weaponWear() == WeaponWearRules.DEFAULT_WEAR, "a missing weapon_wear uses the default");
        for (int value : new int[]{0, 1000}) {
            SkillDefinition def = parse(helper, value).result().orElse(null);
            helper.assertTrue(def != null && def.weaponWear() == value, "weapon_wear " + value + " is accepted as is");
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void weaponWearCodecRejectsOutOfRange(GameTestHelper helper) {
        for (int value : new int[]{-1, 1001}) {
            helper.assertTrue(parse(helper, value).error().isPresent(), "weapon_wear " + value + " is rejected");
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void definitionWearMatchesTheServerRule(GameTestHelper helper) {
        for (Integer configured : new Integer[]{null, 0, 3}) {
            SkillDefinition def = parse(helper, configured).result().orElseThrow();
            for (boolean fromSlot : new boolean[]{false, true}) {
                int expected = WeaponWearRules.wear(def.requiredItem().isPresent(), fromSlot, def.weaponWear(), false);
                helper.assertTrue(def.weaponWear(fromSlot) == expected,
                        "the tab shows what the server charges (wear " + configured + ", slot " + fromSlot + ")");
            }
        }
        helper.succeed();
    }
}
