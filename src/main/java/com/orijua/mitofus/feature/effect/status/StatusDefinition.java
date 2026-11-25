package com.orijua.mitofus.feature.effect.status;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.effect.MobEffect;

/**
 * A status (a buff or debuff given to a creature) as written in data/&lt;namespace&gt;/mitofus/status/&lt;id&gt;.json.
 * The three things a status has are kept apart: the <b>chance</b> belongs to whoever gives it (a passive, a skill, an item),
 * the <b>intensity</b> and the <b>duration</b> are the defaults written here, which the giver may change within the maximums.
 * <ul>
 *   <li>{@code effect}: the game effect that carries it (a vanilla one or one of the mod's).</li>
 *   <li>{@code duration_ticks} / {@code max_duration_ticks}: default and highest duration.</li>
 *   <li>{@code intensity} / {@code max_intensity}: default and highest level (1 = the base effect).</li>
 *   <li>{@code max_chance}: the highest chance any giver can reach, whatever it adds up to.</li>
 *   <li>{@code immunity_ticks}: after the status ends, the target cannot get it again for this long (0 = no window).</li>
 * </ul>
 * Giving a status the target already has renews its duration; it never stacks its intensity.
 */
public record StatusDefinition(Component name, Holder<MobEffect> effect, int durationTicks, int maxDurationTicks, int intensity,
                               int maxIntensity, double maxChance, int immunityTicks) {
    public StatusRules.Limits limits() {
        return new StatusRules.Limits(durationTicks, maxDurationTicks, intensity, maxIntensity, maxChance, immunityTicks);
    }

    public static final Codec<StatusDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ComponentSerialization.CODEC.fieldOf("name").forGetter(StatusDefinition::name),
            BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("effect").forGetter(StatusDefinition::effect),
            Codec.intRange(1, 72_000).fieldOf("duration_ticks").forGetter(StatusDefinition::durationTicks),
            Codec.intRange(1, 72_000).fieldOf("max_duration_ticks").forGetter(StatusDefinition::maxDurationTicks),
            Codec.intRange(1, 10).optionalFieldOf("intensity", 1).forGetter(StatusDefinition::intensity),
            Codec.intRange(1, 10).optionalFieldOf("max_intensity", 1).forGetter(StatusDefinition::maxIntensity),
            Codec.doubleRange(0, 1).optionalFieldOf("max_chance", 0.75).forGetter(StatusDefinition::maxChance),
            Codec.intRange(0, 72_000).optionalFieldOf("immunity_ticks", 0).forGetter(StatusDefinition::immunityTicks)
    ).apply(instance, StatusDefinition::new));
}
