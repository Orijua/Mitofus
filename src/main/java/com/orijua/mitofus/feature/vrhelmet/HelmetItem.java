package com.orijua.mitofus.feature.vrhelmet;

import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModItems;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The connection helmet, in three tiers: right-click to put it on. The body lies down where the player stands (a
 * {@link VrHelmetBlock}, which exists only while they are connected) and, after a short "Connecting…", the player
 * appears in Mitofus. The helmet shows when it is bound to a player (the binding travels with the item).
 */
public class HelmetItem extends Item {
    private final VrHelmetTier tier;

    public HelmetItem(VrHelmetTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    /** The tier of this helmet. */
    public VrHelmetTier tier() {
        return tier;
    }

    /** A helmet of {@code tier}, bound to {@code owner} if there is one. */
    public static ItemStack stackFor(VrHelmetTier tier, Optional<UUID> owner) {
        ItemStack stack = new ItemStack(switch (tier) {
            case BASIC -> ModItems.HELMET.get();
            case ADVANCED -> ModItems.ADVANCED_HELMET.get();
            case DIAMOND -> ModItems.DIAMOND_HELMET.get();
        });
        owner.ifPresent(uuid -> stack.set(ModDataComponents.VR_HELMET_OWNER.get(), uuid));
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            VrHelmetService.putOn(serverPlayer, stack, tier);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (stack.has(ModDataComponents.VR_HELMET_OWNER.get())) {
            tooltip.add(Component.translatable("tooltip.mitofus.vrhelmet_bound").withStyle(ChatFormatting.GOLD));
        }
    }
}
