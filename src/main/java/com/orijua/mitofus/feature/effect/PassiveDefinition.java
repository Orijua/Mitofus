package com.orijua.mitofus.feature.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * A passive as written in data/&lt;namespace&gt;/mitofus/passive/&lt;id&gt;.json. It needs no slot: it works as soon as a class
 * gives it to the player.
 */
public record PassiveDefinition(Component name, Component description, Optional<Item> icon, Trait trait) {
    public static final Codec<PassiveDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ComponentSerialization.CODEC.fieldOf("name").forGetter(PassiveDefinition::name),
            ComponentSerialization.CODEC.optionalFieldOf("description", Component.empty()).forGetter(PassiveDefinition::description),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("icon").forGetter(PassiveDefinition::icon),
            Trait.CODEC.forGetter(PassiveDefinition::trait)
    ).apply(instance, PassiveDefinition::new));

    public ItemStack iconStack() {
        return icon.map(ItemStack::new).orElse(ItemStack.EMPTY);
    }
}
