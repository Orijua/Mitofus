package com.orijua.mitofus.feature.fusion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A class fusion as written in data/&lt;namespace&gt;/mitofus/fusion/&lt;id&gt;.json: two classes become one hybrid class.
 * <ul>
 *   <li>{@code parents}: the two classes (different from each other); {@code resultClass}: the hybrid, a class of its own.</li>
 *   <li>{@code cost}: gold coins paid from the purse (default {@value #DEFAULT_COST}).</li>
 *   <li>{@code allowMixedGrades}: lets the two parents have different grades (rare, set by hand); by default they must share one.</li>
 *   <li>{@code synergyMultiplier}: what the hybrid's synergy with a job is worth when it declares none of its own: the better of
 *       its parents' synergies times this.</li>
 * </ul>
 * The trunk of the merged talent tree is made of ordinary talent nodes whose {@code class} is the result class.
 */
public record FusionDefinition(
        List<ResourceLocation> parents,
        ResourceLocation resultClass,
        int cost,
        boolean allowMixedGrades,
        double synergyMultiplier) {

    public static final int DEFAULT_COST = 10;
    public static final double DEFAULT_SYNERGY_MULTIPLIER = 1.25;

    public static final Codec<FusionDefinition> CODEC = RecordCodecBuilder.<FusionDefinition>create(instance -> instance.group(
            ResourceLocation.CODEC.listOf().fieldOf("parents").forGetter(FusionDefinition::parents),
            ResourceLocation.CODEC.fieldOf("result_class").forGetter(FusionDefinition::resultClass),
            Codec.intRange(0, 100_000).optionalFieldOf("cost", DEFAULT_COST).forGetter(FusionDefinition::cost),
            Codec.BOOL.optionalFieldOf("allow_mixed_grades", false).forGetter(FusionDefinition::allowMixedGrades),
            Codec.doubleRange(1.0, 10.0).optionalFieldOf("synergy_multiplier", DEFAULT_SYNERGY_MULTIPLIER).forGetter(FusionDefinition::synergyMultiplier)
    ).apply(instance, FusionDefinition::new)).validate(definition -> {
        if (definition.parents().size() != 2 || definition.parents().get(0).equals(definition.parents().get(1))) {
            return DataResult.error(() -> "a fusion needs exactly two different parent classes");
        }
        if (definition.parents().contains(definition.resultClass())) {
            return DataResult.error(() -> "the result class of a fusion must be a new class, not one of its parents");
        }
        return DataResult.success(definition);
    });
}
