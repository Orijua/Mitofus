package com.orijua.mitofus.feature.combination;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The name of a combination of branches, as written in data/&lt;namespace&gt;/mitofus/combination/&lt;id&gt;.json: {@code branches} lists one to
 * three different classes (in any order) and {@code name} is what the player's class is called while they hold exactly those. A set with
 * no entry gets a name made of its branches ({@link CombinationText}), so this list can grow without ever blocking anything.
 */
public record CombinationDefinition(List<ResourceLocation> branches, Component name) {
    public static final Codec<CombinationDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.listOf().fieldOf("branches").forGetter(CombinationDefinition::branches),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(CombinationDefinition::name)
    ).apply(instance, CombinationDefinition::new));
}
