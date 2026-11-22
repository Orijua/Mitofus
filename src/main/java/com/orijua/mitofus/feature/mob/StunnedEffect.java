package com.orijua.mitofus.feature.mob;

import com.orijua.mitofus.Mitofus;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Stunned: cannot move, jump or swing. Given by {@link StunService} to whoever takes a blow worth 40% of their life. */
public class StunnedEffect extends MobEffect {
    public StunnedEffect() {
        super(MobEffectCategory.HARMFUL, 0xE8C547);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, Mitofus.id("effect.stunned.speed"), -1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH, Mitofus.id("effect.stunned.jump"), -1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.ATTACK_SPEED, Mitofus.id("effect.stunned.attack_speed"), -1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, Mitofus.id("effect.stunned.attack_damage"), -1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
