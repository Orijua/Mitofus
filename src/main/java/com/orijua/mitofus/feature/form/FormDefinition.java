package com.orijua.mitofus.feature.form;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A form as written in data/&lt;namespace&gt;/mitofus/form/&lt;id&gt;.json: an equippable form of one class.
 * <ul>
 *   <li>{@code class}: the class that owns it; {@code unlock_level}: the class level that unlocks it (50, 100, 150...);
 *       {@code source}: reaching that level ({@code level}) or also finishing the grade quest ({@code grade_quest}).</li>
 *   <li>{@code type}: its elemental type. {@code skills}: up to 3, in order attack, passive, ultimate, given at form
 *       levels {@value FormRules#SKILL_LEVELS_TEXT}. The attack and the ultimate are skills; the passive is a passive
 *       (data/&lt;namespace&gt;/mitofus/passive).</li>
 *   <li>{@code max_type_bonus}: the damage bonus of its type at form level 100 (default 20%), growing in a straight line.</li>
 *   <li>{@code inheritable}: a fusion of this class gets it too, when the fused class reaches {@code unlock_level}.</li>
 *   <li>{@code switch_delay_seconds}: seconds without dealing or taking damage before this form can be put on or taken off (default 5).</li>
 *   <li>{@code curve}: how fast the form levels up, see {@link Curve}.</li>
 * </ul>
 * A form has no talents and no stats of its own.
 */
public record FormDefinition(Component name, Component description, ResourceLocation playerClass, int unlockLevel,
                             FormSource source, ResourceLocation type, List<ResourceLocation> skills, double maxTypeBonus,
                             boolean inheritable, int switchDelaySeconds, Curve curve) {
    public static final double DEFAULT_MAX_TYPE_BONUS = 0.20;
    public static final int DEFAULT_SWITCH_DELAY_SECONDS = 5;

    /**
     * XP by use: going from form level n to n+1 takes {@code first_step * growth^(n-1)} uses. A form below
     * {@code boost_below} gains {@code boost} times more, so a low form is not abandoned.
     */
    public record Curve(int firstStep, double growth, int boostBelow, double boost) {
        public static final Curve DEFAULT = new Curve(100, 1.04, 25, 2.0);

        public static final Codec<Curve> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 1_000_000).optionalFieldOf("first_step", 100).forGetter(Curve::firstStep),
                Codec.doubleRange(1.0, 3.0).optionalFieldOf("growth", 1.04).forGetter(Curve::growth),
                Codec.intRange(0, FormRules.MAX_LEVEL).optionalFieldOf("boost_below", 25).forGetter(Curve::boostBelow),
                Codec.doubleRange(1.0, 100.0).optionalFieldOf("boost", 2.0).forGetter(Curve::boost)
        ).apply(instance, Curve::new));
    }

    /** The index of the passive in {@code skills}. */
    public static final int PASSIVE_INDEX = 1;

    /** The skills (attack and ultimate) of this form, in order, with the passive left out. */
    public List<ResourceLocation> activeSkills() {
        List<ResourceLocation> result = new java.util.ArrayList<>();
        for (int i = 0; i < skills.size(); i++) {
            if (i != PASSIVE_INDEX) {
                result.add(skills.get(i));
            }
        }
        return result;
    }

    public java.util.Optional<ResourceLocation> passive() {
        return skills.size() > PASSIVE_INDEX ? java.util.Optional.of(skills.get(PASSIVE_INDEX)) : java.util.Optional.empty();
    }

    public static final Codec<FormDefinition> CODEC = RecordCodecBuilder.<FormDefinition>create(instance -> instance.group(
            ComponentSerialization.CODEC.fieldOf("name").forGetter(FormDefinition::name),
            ComponentSerialization.CODEC.optionalFieldOf("description", Component.empty()).forGetter(FormDefinition::description),
            ResourceLocation.CODEC.fieldOf("class").forGetter(FormDefinition::playerClass),
            Codec.intRange(1, 100_000).fieldOf("unlock_level").forGetter(FormDefinition::unlockLevel),
            FormSource.CODEC.optionalFieldOf("source", FormSource.LEVEL).forGetter(FormDefinition::source),
            ResourceLocation.CODEC.fieldOf("type").forGetter(FormDefinition::type),
            ResourceLocation.CODEC.listOf().optionalFieldOf("skills", List.of()).forGetter(FormDefinition::skills),
            Codec.doubleRange(0.0, 5.0).optionalFieldOf("max_type_bonus", DEFAULT_MAX_TYPE_BONUS).forGetter(FormDefinition::maxTypeBonus),
            Codec.BOOL.optionalFieldOf("inheritable", false).forGetter(FormDefinition::inheritable),
            Codec.intRange(0, 600).optionalFieldOf("switch_delay_seconds", DEFAULT_SWITCH_DELAY_SECONDS).forGetter(FormDefinition::switchDelaySeconds),
            Curve.CODEC.optionalFieldOf("curve", Curve.DEFAULT).forGetter(FormDefinition::curve)
    ).apply(instance, FormDefinition::new)).validate(definition -> definition.skills().size() > FormRules.SKILL_LEVELS.size()
            ? DataResult.error(() -> "a form has at most " + FormRules.SKILL_LEVELS.size() + " skills")
            : DataResult.success(definition));
}
