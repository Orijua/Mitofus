package com.orijua.mitofus.feature.sancho;

import com.orijua.mitofus.core.config.AppraisalBalance;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.equipment.AppraisalService;
import com.orijua.mitofus.feature.notification.NotificationEvents;
import com.orijua.mitofus.feature.notification.SystemNotification;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Appraiser Eye. One use is spent on the first thing it can appraise, in this order:
 * unappraised equipment in the other hand (it reveals some of its data, see {@link AppraisalService#appraise}), then an
 * unappraised book in the inventory (it reveals what it is; for Sancho's old book, that is the moment Conde Nuno comes for it,
 * {@link NunoBetrayal#betray}). Each use has a short cooldown.
 */
public class AppraiserEyeItem extends Item {
    public AppraiserEyeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack eye = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(eye);
        }
        AppraisalBalance balance = AppraisalBalance.current();
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (AppraisalService.isUnappraised(other)) {
            AppraisalService.appraise(serverPlayer, other, balance);
        } else if (!appraise(serverPlayer)) {
            player.displayClientMessage(Component.translatable("message.mitofus.nothing_to_appraise"), true);
            return InteractionResultHolder.fail(eye);
        }
        eye.consume(1, player);
        if (balance.eyeCooldownTicks() > 0) {
            player.getCooldowns().addCooldown(this, balance.eyeCooldownTicks());
        }
        return InteractionResultHolder.consume(eye);
    }

    /** Appraises the first unappraised book in the player's inventory; false if there is none. Doesn't spend the eye. */
    public static boolean appraise(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(ModItems.UNAPPRAISED_BOOK.get())) {
                inventory.getItem(slot).shrink(1);
                ItemStack book = new ItemStack(ModItems.ORBE_AURIFERO.get());
                if (!inventory.add(slot, book) && !inventory.add(book)) {
                    player.drop(book, false);
                }
                player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 0.7f);
                NotificationEvents.send(player, SystemNotification.Kind.ITEM, List.of(
                        Component.translatable("notification.mitofus.appraised", book.getHoverName()),
                        Component.translatable("item.mitofus.orbe_aurifero.effect")));
                NunoBetrayal.betray(player);
                return true;
            }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        AppraisalBalance balance = AppraisalBalance.current();
        tooltip.add(Component.translatable(getDescriptionId() + ".desc", balance.minData(), balance.maxData()).withStyle(ChatFormatting.GRAY));
    }
}
