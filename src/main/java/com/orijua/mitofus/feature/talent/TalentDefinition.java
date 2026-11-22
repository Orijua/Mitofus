package com.orijua.mitofus.feature.talent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orijua.mitofus.feature.progression.ClassGrade;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Optional;

/**
 * A node of a class's talent tree, as written in data/&lt;namespace&gt;/mitofus/talent/&lt;id&gt;.json.
 * <ul>
 *   <li>{@code playerClass}: the class whose tree it belongs to; {@code branch} groups the nodes of one specialty.</li>
 *   <li>{@code requires}: nodes that must be owned first. A node with none is a root; roots cost 0 and are owned from the start.</li>
 *   <li>{@code cost}: talent points; most nodes cost 1, the strongest at the end of a branch up to {@value #MAX_COST}.</li>
 *   <li>{@code minGrade}: the class grade needed to buy it (Apprentice means no grade needed).</li>
 *   <li>{@code x}, {@code y}: the place on the tree screen, in node cells.</li>
 * </ul>
 */
public record TalentDefinition(
        ResourceLocation playerClass,
        String branch,
        Component name,
        Component description,
        Optional<Item> icon,
        int cost,
        List<ResourceLocation> requires,
        ClassGrade minGrade,
        int x,
        int y,
        List<TalentEffect> effects) {

    public static final int MAX_COST = 3;

    public static final Codec<TalentDefinition> CODEC = RecordCodecBuilder.<TalentDefinition>create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("class").forGetter(TalentDefinition::playerClass),
            Codec.STRING.fieldOf("branch").forGetter(TalentDefinition::branch),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(TalentDefinition::name),
            ComponentSerialization.CODEC.optionalFieldOf("description", Component.empty()).forGetter(TalentDefinition::description),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("icon").forGetter(TalentDefinition::icon),
            Codec.intRange(0, MAX_COST).optionalFieldOf("cost", 1).forGetter(TalentDefinition::cost),
            ResourceLocation.CODEC.listOf().optionalFieldOf("requires", List.of()).forGetter(TalentDefinition::requires),
            ClassGrade.CODEC.optionalFieldOf("min_grade", ClassGrade.APPRENTICE).forGetter(TalentDefinition::minGrade),
            Codec.intRange(-50, 50).fieldOf("x").forGetter(TalentDefinition::x),
            Codec.intRange(-50, 50).fieldOf("y").forGetter(TalentDefinition::y),
            TalentEffect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(TalentDefinition::effects)
    ).apply(instance, TalentDefinition::new)).validate(definition -> {
        if (definition.requires().isEmpty() != (definition.cost() == 0)) {
            return DataResult.error(() -> "a root (no requires) must cost 0 and any other node at least 1");
        }
        return DataResult.success(definition);
    });

    public boolean isRoot() {
        return requires.isEmpty();
    }
}
