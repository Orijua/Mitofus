package com.orijua.mitofus.feature.skill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orijua.mitofus.feature.effect.Condition;
import com.orijua.mitofus.feature.effect.ConditionType;
import com.orijua.mitofus.feature.effect.Effect;
import com.orijua.mitofus.feature.effect.Trait;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * A skill as written in data/<namespace>/mitofus/skill/<id>.json.
 * Its optional {@code passive} gives bonuses as soon as the skill is learned, with no slot (see ActiveTraits).
 * {@code icon} is any item whose texture the HUD and the Skills tab show for this skill.
 */
public record SkillDefinition(
        Component name,
        Component description,
        Optional<Item> icon,
        float manaCost,
        int cooldownTicks,
        Optional<TagKey<Item>> requiredItem,
        int weaponWear,
        List<Effect> effects,
        List<Condition> conditions,
        Optional<Trait> passive) {

    public static final Codec<SkillDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ComponentSerialization.CODEC.fieldOf("name").forGetter(SkillDefinition::name),
            ComponentSerialization.CODEC.optionalFieldOf("description", Component.empty()).forGetter(SkillDefinition::description),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("icon").forGetter(SkillDefinition::icon),
            Codec.floatRange(0, 1000).fieldOf("mana_cost").forGetter(SkillDefinition::manaCost),
            Codec.intRange(0, 72000).fieldOf("cooldown_ticks").forGetter(SkillDefinition::cooldownTicks),
            TagKey.codec(Registries.ITEM).optionalFieldOf("required_item").forGetter(SkillDefinition::requiredItem),
            Codec.intRange(0, 1000).optionalFieldOf("weapon_wear", WeaponWearRules.DEFAULT_WEAR).forGetter(SkillDefinition::weaponWear),
            Effect.CODEC.listOf().validate(list -> list.isEmpty()
                    ? DataResult.error(() -> "a skill needs at least one effect") : DataResult.success(list)).fieldOf("effects").forGetter(SkillDefinition::effects),
            Condition.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(SkillDefinition::conditions),
            Trait.CODEC.codec().optionalFieldOf("passive").forGetter(SkillDefinition::passive)
    ).apply(instance, SkillDefinition::new));

    /** Whether any of its effects hurts others (refused during the safety protocol of the fatigue). */
    public boolean dealsDamage() {
        return effects.stream().anyMatch(effect -> effect.type().dealsDamage());
    }

    /** Whether it needs the creature the caster looks at. */
    public boolean needsTarget() {
        return conditions.stream().anyMatch(condition -> condition.type() == ConditionType.REQUIRES_TARGET);
    }

    /** Durability the held weapon loses per cast; 0 for skills that neither need a weapon nor come from one. */
    public int weaponWear(boolean fromWeaponSlot) {
        return WeaponWearRules.wear(requiredItem.isPresent(), fromWeaponSlot, weaponWear);
    }

    /** "mana · cooldown · wear" line shared by the Skills tab and the weapon tooltip. */
    public Component costLine(boolean fromWeaponSlot) {
        int wear = weaponWear(fromWeaponSlot);
        int mana = SkillCostRules.manaShown(manaCost);
        int seconds = SkillCostRules.cooldownSeconds(cooldownTicks);
        return SkillCostRules.showsWear(wear)
                ? Component.translatable("screen.mitofus.skill_cost_wear", mana, seconds, wear)
                : Component.translatable("screen.mitofus.skill_cost", mana, seconds);
    }

    public boolean canUseWith(ItemStack mainHand) {
        return requiredItem.map(mainHand::is).orElse(true);
    }

    /** The icon as a stack ready to draw, or empty when the skill has none. */
    public ItemStack iconStack() {
        return icon.map(ItemStack::new).orElse(ItemStack.EMPTY);
    }
}
