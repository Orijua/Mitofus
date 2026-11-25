package com.orijua.mitofus.feature.effect.status;

import com.orijua.mitofus.Mitofus;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Cursed: deals 20% less damage per level (amplifier + 1). Given by the {@code curse} status. */
public class CursedEffect extends MobEffect {
    public CursedEffect() {
        super(MobEffectCategory.HARMFUL, 0x5B2A86);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, Mitofus.id("effect.cursed.attack_damage"), -0.2,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
