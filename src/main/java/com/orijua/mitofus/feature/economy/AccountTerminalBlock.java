package com.orijua.mitofus.feature.economy;

import com.mojang.serialization.MapCodec;
import com.orijua.mitofus.core.config.VrHelmetBalance;
import com.orijua.mitofus.core.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

/**
 * The account terminal: right-click to see your coins and the daily offer. It only works in
 * the real world (in Mitofus it shows "No signal"). Faces the player who places it.
 */
public class AccountTerminalBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<AccountTerminalBlock> CODEC = simpleCodec(AccountTerminalBlock::new);

    public AccountTerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** A bound VR helmet in hand: the terminal asks for confirmation and unbinds it for a fee (see {@link VrHelmetUnbind}). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.has(ModDataComponents.VR_HELMET_OWNER.get())) {
            if (level.isClientSide) {
                // The player confirms the price first; the server validates and charges when the payload arrives.
                com.orijua.mitofus.feature.economy.client.UnbindConfirm.open(pos, hand, stack);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && TerminalService.open(serverPlayer, Optional.of(pos)) == TerminalService.Result.IN_MITOFUS) {
            serverPlayer.displayClientMessage(Component.translatable("message.mitofus.terminal.no_signal"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
