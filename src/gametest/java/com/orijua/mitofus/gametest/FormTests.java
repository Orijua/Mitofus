package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.form.ElementRules;
import com.orijua.mitofus.feature.form.ElementTypeDefinition;
import com.orijua.mitofus.feature.form.FormService;
import com.orijua.mitofus.feature.form.PlayerForms;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/** Elemental types as data and the forms a player stores. The forms themselves come with each class's own PR. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class FormTests {
    private static final ResourceLocation FIRE = Mitofus.id("fire");
    private static final ResourceLocation WATER = Mitofus.id("water");
    private static final ResourceLocation EARTH = Mitofus.id("earth");
    private static final ResourceLocation LIGHTNING = Mitofus.id("lightning");
    private static final ResourceLocation LIGHT = Mitofus.id("light");
    private static final ResourceLocation SHADOW = Mitofus.id("shadow");
    private static final ResourceLocation SOME_FORM = Mitofus.id("no_such_form");

    private FormTests() {
    }

    private static double hit(GameTestHelper helper, ResourceLocation attacker, ResourceLocation defender) {
        var level = helper.getLevel();
        return ElementRules.multiplier(Optional.of(attacker), Definitions.get(level, ModDatapackRegistries.ELEMENT_TYPE, attacker),
                Optional.of(defender), Definitions.get(level, ModDatapackRegistries.ELEMENT_TYPE, defender));
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theSixTypesLoadWithTheirCycleOfAdvantages(GameTestHelper helper) {
        helper.assertTrue(Definitions.all(helper.getLevel(), ModDatapackRegistries.ELEMENT_TYPE).size() == 6, "six types");
        helper.assertTrue(Math.abs(hit(helper, WATER, FIRE) - 1.15) < 1e-9, "water beats fire");
        helper.assertTrue(Math.abs(hit(helper, FIRE, EARTH) - 1.15) < 1e-9, "fire beats earth");
        helper.assertTrue(Math.abs(hit(helper, EARTH, LIGHTNING) - 1.15) < 1e-9, "earth beats lightning");
        helper.assertTrue(Math.abs(hit(helper, LIGHTNING, WATER) - 1.15) < 1e-9, "lightning beats water");
        helper.assertTrue(Math.abs(hit(helper, FIRE, WATER) - 0.90) < 1e-9, "fire is weak against water");
        helper.assertTrue(Math.abs(hit(helper, LIGHT, SHADOW) - 1.15) < 1e-9 && Math.abs(hit(helper, SHADOW, LIGHT) - 1.15) < 1e-9,
                "light and shadow counter each other");
        helper.assertTrue(Math.abs(hit(helper, WATER, EARTH) - 1.0) < 1e-9, "unrelated types are neutral");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aPlayerStartsWithoutFormsAndCannotEquipOneThatDoesNotExist(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        helper.assertTrue(FormService.forms(player).equals(PlayerForms.EMPTY), "no forms at the start");
        helper.assertTrue(FormService.owned(player).isEmpty(), "nothing owned");
        helper.assertTrue(FormService.equip(player, SOME_FORM) == FormService.Result.UNKNOWN, "an unknown form is refused");
        helper.assertFalse(FormService.forceEquip(player, SOME_FORM), "the operator cannot equip it either");
        helper.assertFalse(FormService.addUse(player), "no form worn, no experience");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void formsAreKeptAndResetAllClearsThem(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        FormService.grant(player, SOME_FORM);
        player.setData(ModAttachments.PLAYER_FORMS, FormService.forms(player).withXp(SOME_FORM, 300).withEquipped(Optional.of(SOME_FORM)));
        PlayerForms stored = FormService.forms(player);
        helper.assertTrue(stored.isGranted(SOME_FORM) && stored.xpOf(SOME_FORM) == 300 && stored.equipped().equals(Optional.of(SOME_FORM)),
                "stored: " + stored);
        helper.assertTrue(FormService.equipped(player).isEmpty(), "a form the classes do not give is not worn");
        ModAttachments.resetAll(player);
        helper.assertTrue(FormService.forms(player).equals(PlayerForms.EMPTY), "reset all clears the forms");
        helper.succeed();
    }
}
