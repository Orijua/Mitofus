package com.orijua.mitofus.feature.form;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * An elemental type as written in data/&lt;namespace&gt;/mitofus/element_type/&lt;id&gt;.json.
 * <ul>
 *   <li>{@code strong_against}: the types this one hits harder. Where two types list each other (light and shadow) both
 *       hit the other harder; otherwise the type that is listed by the other one is the weak side of that relation.</li>
 *   <li>{@code advantage}: the damage bonus of an attack of this type against a type it is strong against (default 15%).</li>
 *   <li>{@code disadvantage}: the damage penalty of an attack of this type against a type that is strong against it (default 10%).</li>
 *   <li>{@code color}: RGB, for the icon and the name of mobs and forms.</li>
 * </ul>
 */
public record ElementTypeDefinition(Component name, int color, List<ResourceLocation> strongAgainst, double advantage,
                                    double disadvantage) {
    public static final double DEFAULT_ADVANTAGE = 0.15;
    public static final double DEFAULT_DISADVANTAGE = 0.10;

    public static final Codec<ElementTypeDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ComponentSerialization.CODEC.fieldOf("name").forGetter(ElementTypeDefinition::name),
            Codec.intRange(0, 0xFFFFFF).optionalFieldOf("color", 0xFFFFFF).forGetter(ElementTypeDefinition::color),
            ResourceLocation.CODEC.listOf().optionalFieldOf("strong_against", List.of()).forGetter(ElementTypeDefinition::strongAgainst),
            Codec.doubleRange(0.0, 5.0).optionalFieldOf("advantage", DEFAULT_ADVANTAGE).forGetter(ElementTypeDefinition::advantage),
            Codec.doubleRange(0.0, 0.95).optionalFieldOf("disadvantage", DEFAULT_DISADVANTAGE).forGetter(ElementTypeDefinition::disadvantage)
    ).apply(instance, ElementTypeDefinition::new));
}
