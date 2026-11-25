package com.orijua.mitofus.feature.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.Optional;

/**
 * One effect of a passive, as written in data/&lt;namespace&gt;/mitofus/passive/&lt;id&gt;.json.
 *
 * @param amount    the bonus: per step when {@code perLevels} is above 0, otherwise flat
 * @param perLevels the bonus grows by {@code amount} every this many player levels (0 = it doesn't grow)
 * @param cap       the most the bonus can reach (0 = no cap)
 * @param withItem  for {@link BonusType#DAMAGE}: only counts while the main hand holds an item of this tag
 * @param atNight   for {@link BonusType#DAMAGE}: only counts at night
 * @param status    for {@link BonusType#APPLY_STATUS}: the status given on a hit ({@code amount} is the chance)
 * @param intensity for {@link BonusType#APPLY_STATUS}: overrides the status's intensity (never above its maximum)
 * @param duration  for {@link BonusType#APPLY_STATUS}: overrides the status's duration in ticks (never above its maximum)
 */
public record Bonus(BonusType type, double amount, int perLevels, double cap, Optional<TagKey<Item>> withItem, boolean atNight,
                    Optional<ResourceLocation> status, Optional<Integer> intensity, Optional<Integer> duration) {
    public static final Codec<Bonus> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BonusType.CODEC.fieldOf("type").forGetter(Bonus::type),
            Codec.DOUBLE.fieldOf("amount").forGetter(Bonus::amount),
            Codec.intRange(0, 1000).optionalFieldOf("per_levels", 0).forGetter(Bonus::perLevels),
            Codec.doubleRange(0, 100000).optionalFieldOf("cap", 0.0).forGetter(Bonus::cap),
            TagKey.codec(Registries.ITEM).optionalFieldOf("with_item").forGetter(Bonus::withItem),
            Codec.BOOL.optionalFieldOf("at_night", false).forGetter(Bonus::atNight),
            ResourceLocation.CODEC.optionalFieldOf("status").forGetter(Bonus::status),
            Codec.intRange(1, 10).optionalFieldOf("intensity").forGetter(Bonus::intensity),
            Codec.intRange(1, 72_000).optionalFieldOf("duration").forGetter(Bonus::duration)
    ).apply(instance, Bonus::new));

    public Bonus(BonusType type, double amount, int perLevels, double cap, Optional<TagKey<Item>> withItem, boolean atNight) {
        this(type, amount, perLevels, cap, withItem, atNight, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** What the bonus gives a player of {@code level}: flat, or {@code amount} per completed step, never above the cap. */
    public double valueAt(int level) {
        double value = perLevels > 0 ? amount * (Math.max(0, level) / perLevels) : amount;
        return cap > 0 ? Math.min(value, cap) : value;
    }
}
