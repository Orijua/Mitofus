package com.orijua.mitofus.feature.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
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
 */
public record Bonus(BonusType type, double amount, int perLevels, double cap, Optional<TagKey<Item>> withItem, boolean atNight) {
    public static final Codec<Bonus> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BonusType.CODEC.fieldOf("type").forGetter(Bonus::type),
            Codec.DOUBLE.fieldOf("amount").forGetter(Bonus::amount),
            Codec.intRange(0, 1000).optionalFieldOf("per_levels", 0).forGetter(Bonus::perLevels),
            Codec.doubleRange(0, 100000).optionalFieldOf("cap", 0.0).forGetter(Bonus::cap),
            TagKey.codec(Registries.ITEM).optionalFieldOf("with_item").forGetter(Bonus::withItem),
            Codec.BOOL.optionalFieldOf("at_night", false).forGetter(Bonus::atNight)
    ).apply(instance, Bonus::new));

    /** What the bonus gives a player of {@code level}: flat, or {@code amount} per completed step, never above the cap. */
    public double valueAt(int level) {
        double value = perLevels > 0 ? amount * (Math.max(0, level) / perLevels) : amount;
        return cap > 0 ? Math.min(value, cap) : value;
    }
}
