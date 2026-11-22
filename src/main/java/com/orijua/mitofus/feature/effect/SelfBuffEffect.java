package com.orijua.mitofus.feature.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.List;

/** Applies vanilla potion effects to the caster, e.g. {"id": "minecraft:resistance", "amplifier": 1, "duration": 200}. */
public record SelfBuffEffect(List<MobEffectInstance> effects, int durationPerLevel) implements Effect {

    public SelfBuffEffect(List<MobEffectInstance> effects) {
        this(effects, 0);
    }


    public static final MapCodec<SelfBuffEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            MobEffectInstance.CODEC.listOf().fieldOf("effects").forGetter(SelfBuffEffect::effects),
            // Mastery: ticks added to every effect for each skill level above the first.
            Codec.intRange(0, 72000).optionalFieldOf("duration_per_level", 0).forGetter(SelfBuffEffect::durationPerLevel)
    ).apply(instance, SelfBuffEffect::new));

    @Override
    public EffectType type() {
        return EffectType.SELF_BUFF;
    }

    @Override
    public boolean apply(EffectContext context) {
        int extra = durationPerLevel * (context.skillLevel() - 1);
        effects.forEach(effect -> context.caster().addEffect(new MobEffectInstance(effect.getEffect(), effect.getDuration() + extra,
                effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon())));
        context.level().playSound(null, context.caster().blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
        return true;
    }
}
