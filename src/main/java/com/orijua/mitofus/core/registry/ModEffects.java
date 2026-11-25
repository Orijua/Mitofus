package com.orijua.mitofus.core.registry;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.mob.BleedingEffect;
import com.orijua.mitofus.feature.mob.StunnedEffect;
import com.orijua.mitofus.feature.skill.BlacksmithRageEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom status effects. Apply them from JSON like any vanilla effect ("mitofus:bleeding"),
 * e.g. in an {@code apply_effect} item option or a skill's {@code target_effects}.
 */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Mitofus.MODID);

    public static final DeferredHolder<MobEffect, BleedingEffect> BLEEDING = EFFECTS.register("bleeding", BleedingEffect::new);
    /** The Blacksmith's Rage: attack power +10%. */
    public static final DeferredHolder<MobEffect, BlacksmithRageEffect> BLACKSMITH_RAGE = EFFECTS.register("blacksmith_rage", BlacksmithRageEffect::new);

    /** Stun: a blow worth 40% of the victim's life. Given by {@code StunService}. */
    public static final DeferredHolder<MobEffect, StunnedEffect> STUNNED = EFFECTS.register("stunned", StunnedEffect::new);

    /** Burning and cursed: given by the statuses of the same name (see effect.status). */
    public static final DeferredHolder<MobEffect, com.orijua.mitofus.feature.effect.status.BurningEffect> BURNING =
            EFFECTS.register("burning", com.orijua.mitofus.feature.effect.status.BurningEffect::new);
    public static final DeferredHolder<MobEffect, com.orijua.mitofus.feature.effect.status.CursedEffect> CURSED =
            EFFECTS.register("cursed", com.orijua.mitofus.feature.effect.status.CursedEffect::new);

    /** data/mitofus/damage_type/bleeding.json; tagged minecraft:bypasses_armor. */
    public static final ResourceKey<DamageType> BLEEDING_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Mitofus.id("bleeding"));

    private ModEffects() {
    }
}
