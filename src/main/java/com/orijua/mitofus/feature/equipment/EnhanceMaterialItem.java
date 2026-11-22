package com.orijua.mitofus.feature.equipment;

import com.orijua.mitofus.core.config.EnhanceBalance;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Optional;

/** A stone or scroll used on the smithing anvil to enhance a piece of equipment. */
public class EnhanceMaterialItem extends Item {
    private final EnhanceMaterial material;

    public EnhanceMaterialItem(EnhanceMaterial material, Properties properties) {
        super(properties);
        this.material = material;
    }

    public EnhanceMaterial material() {
        return material;
    }

    /** The material of a stack, if it is one. */
    public static Optional<EnhanceMaterial> of(ItemStack stack) {
        return stack.getItem() instanceof EnhanceMaterialItem item ? Optional.of(item.material) : Optional.empty();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String target = switch (material.target()) {
            case WEAPON -> "weapon";
            case ARMOR -> "armor";
            case ANY -> "any";
        };
        tooltip.add(Component.translatable("tooltip.mitofus.enhance_material." + target).withStyle(ChatFormatting.GRAY));
        EnhanceBalance balance = EnhanceBalance.current();
        if (material.kind() == EnhanceMaterial.Kind.ANCIENT_SCROLL) {
            tooltip.add(Component.translatable("tooltip.mitofus.enhance_material.ancient",
                    balance.ancientMinGain(), balance.ancientMaxGain()).withStyle(ChatFormatting.GOLD));
            return;
        }
        int lost = EnhanceRules.levelsLost(balance, material.kind());
        tooltip.add(lost > 0
                ? Component.translatable(EnhanceRules.levelsKey("tooltip.mitofus.enhance_material.loss", lost), lost).withStyle(ChatFormatting.RED)
                : Component.translatable("tooltip.mitofus.enhance_material.no_loss").withStyle(ChatFormatting.GREEN));
    }
}
