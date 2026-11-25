package com.orijua.mitofus.feature.form;

import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.progression.PlayerClasses;
import com.orijua.mitofus.feature.vrhelmet.CombatClock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/** Reads and writes {@link PlayerForms}: owning, equipping and levelling forms. Only one form is equipped at a time. */
public final class FormService {
    public enum Result { OK, UNKNOWN, LOCKED, IN_COMBAT }

    private FormService() {
    }

    public static PlayerForms forms(ServerPlayer player) {
        return player.getData(ModAttachments.PLAYER_FORMS);
    }

    /** The forms the player's classes give them, with their levels. */
    public static List<FormCatalogue.Owned> owned(ServerPlayer player) {
        PlayerClasses classes = player.getData(ModAttachments.PLAYER_CLASSES);
        return FormCatalogue.owned(Definitions.all(player.level(), ModDatapackRegistries.FORM), classes.slots(), forms(player));
    }

    /** The equipped form if the player still owns it. */
    public static Optional<FormCatalogue.Owned> equipped(ServerPlayer player) {
        return forms(player).equipped().flatMap(id -> FormCatalogue.find(owned(player), id));
    }

    /** In combat for a form: it dealt or took damage less than the form's {@code switch_delay_seconds} ago. */
    public static boolean inCombat(ServerPlayer player, int delaySeconds) {
        var last = CombatClock.last(player);
        return last.isPresent() && player.server.getTickCount() - last.getAsLong() < delaySeconds * 20L;
    }

    /** Equips {@code id}; only out of combat and only a form the player owns. */
    public static Result equip(ServerPlayer player, ResourceLocation id) {
        if (Definitions.get(player.level(), ModDatapackRegistries.FORM, id).isEmpty()) {
            return Result.UNKNOWN;
        }
        var target = FormCatalogue.find(owned(player), id);
        if (target.isEmpty()) {
            return Result.LOCKED;
        }
        if (inCombat(player, target.get().form().switchDelaySeconds())) {
            return Result.IN_COMBAT;
        }
        player.setData(ModAttachments.PLAYER_FORMS, forms(player).withEquipped(Optional.of(id)));
        return Result.OK;
    }

    /** Takes the equipped form off (back to the base form of the class); only out of combat. */
    public static Result unequip(ServerPlayer player) {
        int delay = equipped(player).map(o -> o.form().switchDelaySeconds()).orElse(FormDefinition.DEFAULT_SWITCH_DELAY_SECONDS);
        if (inCombat(player, delay)) {
            return Result.IN_COMBAT;
        }
        player.setData(ModAttachments.PLAYER_FORMS, forms(player).withEquipped(Optional.empty()));
        return Result.OK;
    }

    /** Operator: equips any defined form, ignoring unlocks and combat. */
    public static boolean forceEquip(ServerPlayer player, ResourceLocation id) {
        if (Definitions.get(player.level(), ModDatapackRegistries.FORM, id).isEmpty()) {
            return false;
        }
        player.setData(ModAttachments.PLAYER_FORMS, forms(player).withEquipped(Optional.of(id)));
        return true;
    }

    /** Called when a grade quest gives a form as its reward. */
    public static void grant(ServerPlayer player, ResourceLocation id) {
        player.setData(ModAttachments.PLAYER_FORMS, forms(player).withGranted(id));
    }

    /** Operator: sets the form level, as the uses that reach it. */
    public static boolean setLevel(ServerPlayer player, ResourceLocation id, int level) {
        return Definitions.get(player.level(), ModDatapackRegistries.FORM, id).map(form -> {
            player.setData(ModAttachments.PLAYER_FORMS, forms(player).withXp(id, FormRules.usesToReach(form.curve(), level)));
            return true;
        }).orElse(false);
    }

    /** One use of the equipped form (a hit or a skill): the hook for the combat PRs. Returns true if it gained a level. */
    public static boolean addUse(ServerPlayer player) {
        Optional<FormCatalogue.Owned> equipped = equipped(player);
        if (equipped.isEmpty() || equipped.get().level() >= FormRules.MAX_LEVEL) {
            return false;
        }
        FormCatalogue.Owned owned = equipped.get();
        PlayerForms before = forms(player);
        int gain = (int) Math.round(FormRules.useValue(owned.form().curve(), owned.level()));
        int xp = before.xpOf(owned.id()) + gain;
        player.setData(ModAttachments.PLAYER_FORMS, before.withXp(owned.id(), xp));
        return FormRules.level(owned.form().curve(), xp) > owned.level();
    }
}
