package com.orijua.mitofus.feature.equipment;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.EnhanceBalance;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.equipment.option.RolledOption;
import com.orijua.mitofus.feature.progression.PlayerStats;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Applies the mod's equipment bonuses (rating, enhancement, options) to the wearer as attribute
 * modifiers. Done per player rather than per item, so an item whose requirements the wearer
 * doesn't meet gives nothing. Recomputed on every equipment change and stat change.
 */
public final class EquipmentBonuses {
    private static final String PREFIX = "equip_";

    private EquipmentBonuses() {
    }

    public static void apply(Player player) {
        removeAll(player);
        if (!MitofusWorld.is(player.level())) {
            // The real world gets no bonuses from the mod's equipment.
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
            return;
        }
        PlayerStats stats = ProgressionService.statsForRequirements(player);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() && slot == bonusSlot(stack) && EquipmentEvents.meetsRequirements(stats, stack)) {
                forEachBonus(stack, slot, (attribute, modifier) -> {
                    AttributeInstance instance = player.getAttribute(attribute);
                    if (instance != null) {
                        instance.addOrUpdateTransientModifier(modifier);
                    }
                });
            }
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /** The items the player wears where their bonuses count; their requirements aren't checked here. */
    public static List<ItemStack> wornEquipment(Player player) {
        List<ItemStack> worn = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() && slot == bonusSlot(stack)) {
                worn.add(stack);
            }
        }
        return worn;
    }

    /** Armor counts in its own slot; anything else only in the main hand. */
    private static EquipmentSlot bonusSlot(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem armor ? armor.getEquipmentSlot() : EquipmentSlot.MAINHAND;
    }

    /** Whether the item is a melee weapon: the only equipment that gets rating, enhancement and option bonuses. */
    public static boolean isWeapon(ItemStack stack) {
        return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                .anyMatch(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE));
    }

    /** Whether the item is armor worn by a player (animal armor, like the wolf's, is excluded). */
    public static boolean isArmor(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem armor && !(armor instanceof AnimalArmorItem);
    }

    /**
     * What the enhancement of a piece adds: an attribute, the amount and the compounding factor (1.05 = +5%).
     *
     * @param attribute attack damage (weapons) or armor (armor)
     * @param amount    points added: base value * (factor - 1)
     * @param factor    the compounding factor of the level
     */
    public record EnhancementBonus(Holder<Attribute> attribute, double amount, double factor) {
    }

    /**
     * The enhancement bonus of a weapon or armor: its level's compounding percentage of the item's base attack or base armor.
     * The base is the sum of the item's own ADD_VALUE modifiers (not the +1 of the player, nor rating or options).
     * Empty at +0, for a piece that is neither weapon nor armor, or with no base value (e.g. an enhanced hammer).
     */
    public static Optional<EnhancementBonus> enhancementBonus(ItemStack stack, EnhanceBalance balance) {
        int level = stack.getOrDefault(ModDataComponents.ENHANCEMENT, 0);
        if (level <= 0) {
            return Optional.empty();
        }
        Holder<Attribute> attribute;
        EquipmentSlot slot;
        if (isWeapon(stack)) {
            attribute = Attributes.ATTACK_DAMAGE;
            slot = EquipmentSlot.MAINHAND;
        } else if (isArmor(stack)) {
            attribute = Attributes.ARMOR;
            slot = ((ArmorItem) stack.getItem()).getEquipmentSlot();
        } else {
            return Optional.empty();
        }
        double base = stack.getAttributeModifiers().modifiers().stream()
                .filter(entry -> entry.attribute().equals(attribute) && entry.slot().test(slot)
                        && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE)
                .mapToDouble(entry -> entry.modifier().amount())
                .sum();
        double amount = EnhanceRules.bonusAmount(balance, base, level);
        return amount > 0 ? Optional.of(new EnhancementBonus(attribute, amount, EnhanceRules.bonusFactor(balance, level))) : Optional.empty();
    }

    private static void forEachBonus(ItemStack stack, EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> add) {
        String base = PREFIX + slot.getName() + "_";
        if (isWeapon(stack)) {
            // An unappraised piece counts as Normal (no rating bonus) until it is appraised in full.
            ItemRating rating = AppraisalService.activeRating(stack);
            if (rating != null && rating.attackDamageBonus() > 0) {
                add.accept(Attributes.ATTACK_DAMAGE, flat(base + "rating", rating.attackDamageBonus()));
            }
        }
        enhancementBonus(stack, EnhanceBalance.current())
                .ifPresent(bonus -> add.accept(bonus.attribute(), flat(base + "enhancement", bonus.amount())));
        List<RolledOption> options = AppraisalService.activeOptions(stack);
        for (int i = 0; i < options.size(); i++) {
            RolledOption rolled = options.get(i);
            ResourceLocation id = Mitofus.id(base + "option_" + i);
            Definitions.get(ModDatapackRegistries.ITEM_OPTION, rolled.option())
                    .ifPresent(def -> def.effect().modifiers(id, rolled.value(), add));
        }
    }

    private static AttributeModifier flat(String path, double amount) {
        return new AttributeModifier(Mitofus.id(path), amount, AttributeModifier.Operation.ADD_VALUE);
    }

    /** Stateless clean-up: drops every modifier this class may have added, on any attribute. */
    private static void removeAll(Player player) {
        BuiltInRegistries.ATTRIBUTE.holders().forEach(attribute -> {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                instance.getModifiers().stream()
                        .map(AttributeModifier::id)
                        .filter(id -> id.getNamespace().equals(Mitofus.MODID) && id.getPath().startsWith(PREFIX))
                        .toList()
                        .forEach(instance::removeModifier);
            }
        });
    }
}
