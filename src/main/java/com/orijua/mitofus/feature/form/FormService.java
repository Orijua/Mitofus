package com.orijua.mitofus.feature.form;

import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.effect.ActiveTraits;
import com.orijua.mitofus.feature.progression.PlayerClasses;
import com.orijua.mitofus.feature.skill.PlayerSkills;
import com.orijua.mitofus.feature.skill.SkillService;
import com.orijua.mitofus.feature.vrhelmet.CombatClock;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Reads and writes {@link PlayerForms}: owning, equipping and levelling forms. Only one form is equipped at a time. */
public final class FormService {
    public enum Result { OK, UNKNOWN, LOCKED, IN_COMBAT }

    private FormService() {
    }

    public static PlayerForms forms(Player player) {
        return player.getData(ModAttachments.PLAYER_FORMS);
    }

    /** The forms the player's classes give them, with their levels. Works on both sides. */
    public static List<FormCatalogue.Owned> owned(Player player) {
        PlayerClasses classes = player.getData(ModAttachments.PLAYER_CLASSES);
        return FormCatalogue.owned(Definitions.all(player.level(), ModDatapackRegistries.FORM), classes.slots(), forms(player));
    }

    /** The equipped form if the player still owns it. */
    public static Optional<FormCatalogue.Owned> equipped(Player player) {
        return forms(player).equipped().flatMap(id -> FormCatalogue.find(owned(player), id));
    }

    /** The equipped form's passive once its form level reaches it (also counts for the Mitofus world only). */
    public static Optional<ResourceLocation> equippedPassive(Player player) {
        if (!MitofusWorld.is(player.level())) {
            return Optional.empty();
        }
        PlayerForms forms = forms(player);
        return forms.equipped().flatMap(id -> Definitions.get(player.level(), ModDatapackRegistries.FORM, id)
                .flatMap(form -> FormRules.unlockedPassive(form, FormRules.level(form.curve(), forms.xpOf(id)))));
    }

    /** The damage bonus of the equipped form's type (0.1 = +10 %), 0 with no form or outside Mitofus. */
    public static double typeBonus(Player player) {
        if (!MitofusWorld.is(player.level())) {
            return 0;
        }
        PlayerForms forms = forms(player);
        return forms.equipped().flatMap(id -> Definitions.get(player.level(), ModDatapackRegistries.FORM, id)
                .map(form -> FormRules.typeBonus(form, FormRules.level(form.curve(), forms.xpOf(id))))).orElse(0.0);
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
        setEquipped(player, Optional.of(id));
        return Result.OK;
    }

    /** Takes the equipped form off (back to the base form of the class); only out of combat. */
    public static Result unequip(ServerPlayer player) {
        int delay = equipped(player).map(o -> o.form().switchDelaySeconds()).orElse(FormDefinition.DEFAULT_SWITCH_DELAY_SECONDS);
        if (inCombat(player, delay)) {
            return Result.IN_COMBAT;
        }
        setEquipped(player, Optional.empty());
        return Result.OK;
    }

    /** Operator: equips any defined form, ignoring unlocks and combat. */
    public static boolean forceEquip(ServerPlayer player, ResourceLocation id) {
        if (Definitions.get(player.level(), ModDatapackRegistries.FORM, id).isEmpty()) {
            return false;
        }
        setEquipped(player, Optional.of(id));
        return true;
    }

    /** Operator: takes the form off with no combat check. */
    public static void forceUnequip(ServerPlayer player) {
        setEquipped(player, Optional.empty());
    }

    private static void setEquipped(ServerPlayer player, Optional<ResourceLocation> id) {
        player.setData(ModAttachments.PLAYER_FORMS, forms(player).withEquipped(id));
        sync(player);
    }

    /** Called when a grade quest gives a form as its reward. */
    public static void grant(ServerPlayer player, ResourceLocation id) {
        player.setData(ModAttachments.PLAYER_FORMS, forms(player).withGranted(id));
    }

    /** Operator: sets the form level, as the uses that reach it. */
    public static boolean setLevel(ServerPlayer player, ResourceLocation id, int level) {
        return Definitions.get(player.level(), ModDatapackRegistries.FORM, id).map(form -> {
            player.setData(ModAttachments.PLAYER_FORMS, forms(player).withXp(id, FormRules.usesToReach(form.curve(), level)));
            sync(player);
            return true;
        }).orElse(false);
    }

    /** One use of the equipped form (a hit or a skill). Returns true if it gained a level. */
    public static boolean addUse(ServerPlayer player) {
        if (!MitofusWorld.is(player.level())) {
            return false;
        }
        Optional<FormCatalogue.Owned> equipped = equipped(player);
        if (equipped.isEmpty() || equipped.get().level() >= FormRules.MAX_LEVEL) {
            return false;
        }
        FormCatalogue.Owned owned = equipped.get();
        PlayerForms before = forms(player);
        int gain = (int) Math.round(FormRules.useValue(owned.form().curve(), owned.level()));
        int xp = before.xpOf(owned.id()) + gain;
        player.setData(ModAttachments.PLAYER_FORMS, before.withXp(owned.id(), xp));
        boolean levelled = FormRules.level(owned.form().curve(), xp) > owned.level();
        if (levelled) {
            sync(player);
        }
        return levelled;
    }

    /**
     * Makes the player's skills and passives match the equipped form: the attack and ultimate it has reached are
     * learned (back in the bar slot they had), every other form skill is forgotten, and the attribute passives are refreshed.
     */
    public static void sync(ServerPlayer player) {
        List<Definitions.Entry<FormDefinition>> all = Definitions.all(player.level(), ModDatapackRegistries.FORM);
        Set<ResourceLocation> every = new HashSet<>();
        all.forEach(entry -> every.addAll(entry.value().activeSkills()));
        Set<ResourceLocation> wanted = new HashSet<>();
        PlayerForms forms = forms(player);
        forms.equipped().flatMap(id -> Definitions.get(player.level(), ModDatapackRegistries.FORM, id)).ifPresent(form ->
                wanted.addAll(FormRules.unlockedActiveSkills(form, FormRules.level(form.curve(), forms.xpOf(forms.equipped().get())))));

        for (ResourceLocation skill : every) {
            PlayerSkills skills = SkillService.get(player);
            if (skills.knows(skill) && !wanted.contains(skill)) {
                Optional<Integer> slot = Optional.empty();
                for (int i = 0; i < skills.slots().size(); i++) {
                    if (skills.slots().get(i).equals(Optional.of(skill))) {
                        slot = Optional.of(i);
                    }
                }
                player.setData(ModAttachments.PLAYER_FORMS, forms(player).withBar(skill, slot));
                SkillService.forget(player, skill);
            }
        }
        for (ResourceLocation skill : wanted) {
            if (SkillService.learn(player, skill)) {
                forms(player).barSlot(skill).ifPresent(slot -> SkillService.assign(player, slot, Optional.of(skill)));
            }
        }
        ActiveTraits.apply(player);
    }

    /** What the server does with a click on the Forms tab: wears the form or takes it off, and says why when it cannot. */
    public static void handle(ServerPlayer player, Optional<ResourceLocation> form) {
        Result result = form.map(id -> equip(player, id)).orElseGet(() -> unequip(player));
        messageKey(result).ifPresent(key -> player.displayClientMessage(net.minecraft.network.chat.Component.translatable(key), true));
    }

    /** The action-bar message of a refusal; nothing when it went through. */
    public static Optional<String> messageKey(Result result) {
        return switch (result) {
            case OK -> Optional.empty();
            case UNKNOWN -> Optional.of("message.mitofus.form_unknown");
            case LOCKED -> Optional.of("message.mitofus.form_locked");
            case IN_COMBAT -> Optional.of("message.mitofus.form_in_combat");
        };
    }
}
