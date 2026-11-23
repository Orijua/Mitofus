package com.orijua.mitofus.feature.talent;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Right-click to get every bought talent of every class back as free points. Consumed on use; stat points are not touched (see the respec scroll). */
public class TalentScrollItem extends Item {
    public TalentScrollItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (!TalentService.hasBought(serverPlayer)) {
                serverPlayer.displayClientMessage(Component.translatable("message.mitofus.talent_scroll_nothing"), true);
                return InteractionResultHolder.fail(stack);
            }
            int refunded = TalentService.refundAll(serverPlayer);
            stack.consume(1, player);
            serverPlayer.displayClientMessage(Component.translatable("message.mitofus.talent_scroll_done", refunded), true);
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
