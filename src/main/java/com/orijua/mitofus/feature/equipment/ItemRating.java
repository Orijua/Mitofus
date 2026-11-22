package com.orijua.mitofus.feature.equipment;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

/**
 * Item rating tiers.
 * Mitofus grades equipment by rating (normal, rare, epic, unique, legendary, ancestral...).
 * Colors: Normal white, Rare sky blue, Epic magenta, Unique red, Legendary gold; Ancestral is dark red. The attack bonus values
 * are our own numbers.
 */
public enum ItemRating implements StringRepresentable {
    NORMAL("normal", 0xFFFFFF, 0.0),
    RARE("rare", 0x5FB4F5, 0.5),
    EPIC("epic", 0xE05AD0, 1.0),
    UNIQUE("unique", 0xE0484A, 2.0),
    LEGENDARY("legendary", 0xF2C134, 3.5),
    ANCESTRAL("ancestral", 0xB0102A, 5.0);

    // Codec = how it is saved to disk (NBT/JSON); StreamCodec = how it travels over the network.
    public static final Codec<ItemRating> CODEC = StringRepresentable.fromEnum(ItemRating::values);
    public static final StreamCodec<ByteBuf, ItemRating> STREAM_CODEC = ByteBufCodecs.idMapper(
            ByIdMap.continuous(ItemRating::ordinal, values(), ByIdMap.OutOfBoundsStrategy.CLAMP),
            ItemRating::ordinal);

    private final String name;
    private final int color;
    private final double attackDamageBonus;

    ItemRating(String name, int color, double attackDamageBonus) {
        this.name = name;
        this.color = color;
        this.attackDamageBonus = attackDamageBonus;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public int color() {
        return color;
    }

    /** Flat attack damage added on top of the weapon's base damage. */
    public double attackDamageBonus() {
        return attackDamageBonus;
    }

    public Component displayName() {
        return Component.translatable("rating.mitofus." + name).withColor(color);
    }
}
