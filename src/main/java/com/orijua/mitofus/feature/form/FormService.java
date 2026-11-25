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

    private record FormList(Object registries, List<Definitions.Entry<FormDefinition>> forms) {
    }

    private static volatile FormList cachedForms = new FormList(null, List.of());

    /** Every form definition, sorted once per registry instead of on every hit. */
    private static List<Definitions.Entry<FormDefinition>> allForms(Player player) {
        Object registries = player.level().registryAccess();
        FormList current = cachedForms;
        if (current.registries() != registries) {
            current = new FormList(registries, Definitions.all(player.level(), ModDatapackRegistries.FORM));
            cachedForms = current;
        }
        return current.forms();
    }

    /** The forms the player's classes give them, with their levels. Works on both sides. */
    public static List<FormCatalogue.Owned> owned(Player player) {
        PlayerClasses classes = player.getData(ModAttachments.PLAYER_CLASSES);
        return FormCatalogue.owned(allForms(player), classes.slots(), forms(player));
    }

    /**
     * The equipped form if the player still owns it. On the server, a form the player lost (the class level dropped or
     * the class was replaced) is taken off here, so nothing keeps working from a form that is no longer theirs.
     */
    public static Optional<FormCatalogue.Owned> equipped(Player player) {
        Optional<FormCatalogue.Owned> found = ownedEquipped(player);
        if (found.isEmpty() && forms(player).equipped().isPresent() && player instanceof ServerPlayer server) {
            setEquipped(server, Optional.empty());
        }
        return found;
    }

    /** The equipped form if owned, with no side effect: looks up just that form, not the whole catalogue. */
    private static Optional<FormCatalogue.Owned> ownedEquipped(Player player) {
        PlayerForms forms = forms(player);
        if (forms.equipped().isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation id = forms.equipped().get();
        PlayerClasses classes = player.getData(ModAttachments.PLAYER_CLASSES);
        return Definitions.get(player.level(), ModDatapackRegistries.FORM, id)
                .flatMap(form -> FormCatalogue.ownedForm(id, form, allForms(player), classes.slots(), forms));
    }

    /** The equipped form's passive once its form level reaches it (also counts for the Mitofus world only). */
    public static Optional<ResourceLocation> equippedPassive(Player player) {
        if (!MitofusWorld.is(player.level())) {
            return Optional.empty();
        }
        return equipped(player).flatMap(owned -> FormRules.unlockedPassive(owned.form(), owned.level()));
    }

    /** The damage bonus of the equipped form's type (0.1 = +10 %), 0 with no form or outside Mitofus. */
    public static double typeBonus(Player player) {
        if (!MitofusWorld.is(player.level())) {
            return 0;
        }
        return equipped(player).map(owned -> FormRules.typeBonus(owned.form(), owned.level())).orElse(0.0);
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

    /** Operator: equips a form the player owns, ignoring combat. A form the classes do not give cannot be worn. */
    public static boolean forceEquip(ServerPlayer player, ResourceLocation id) {
        Optional<FormDefinition> form = Definitions.get(player.level(), ModDatapackRegistries.FORM, id);
        if (form.isEmpty() || FormCatalogue.ownedForm(id, form.get(), allForms(player),
                player.getData(ModAttachments.PLAYER_CLASSES).slots(), forms(player)).isEmpty()) {
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
        ownedEquipped(player).ifPresent(owned -> wanted.addAll(FormRules.unlockedActiveSkills(owned.form(), owned.level())));

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
}
