package com.orijua.mitofus.feature.vrhelmet;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orijua.mitofus.core.config.FatigueBalance;
import com.orijua.mitofus.feature.fatigue.FatigueService;
import com.orijua.mitofus.core.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The VR helmet, in three tiers: basic, advanced and diamond. Right-click to lie down: after a short "Connecting…" the player
 * appears in Mitofus. Faces the player who places it.
 */
public class VrHelmetBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<VrHelmetBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            VrHelmetTier.CODEC.fieldOf("tier").forGetter(VrHelmetBlock::tier),
            propertiesCodec()).apply(instance, VrHelmetBlock::new));

    private final VrHelmetTier tier;

    public VrHelmetBlock(VrHelmetTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public VrHelmetTier tier() {
        return tier;
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

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VrHelmetBlockEntity(pos, state);
    }

    /** Nobody but the owner breaks a bound VR helmet (creative and explosions aside). */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof VrHelmetBlockEntity vrhelmet
                && VrHelmetOwnerRules.access(vrhelmet.owner(), player.getUUID()) == VrHelmetOwnerRules.Access.STRANGER) {
            return 0.0f;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    /** Breaking the body gives the helmet back, bound to its owner. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        Optional<UUID> owner = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof VrHelmetBlockEntity vrhelmet
                ? vrhelmet.owner() : Optional.empty();
        return List.of(HelmetItem.stackFor(tier, owner));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return HelmetItem.stackFor(tier, level.getBlockEntity(pos) instanceof VrHelmetBlockEntity vrhelmet ? vrhelmet.owner() : Optional.empty());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            switch (VrHelmetService.begin(serverPlayer, pos)) {
                case IN_MITOFUS, ALREADY_CONNECTED -> serverPlayer.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.in_mitofus"), true);
                case NOT_OVERWORLD -> serverPlayer.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.only_overworld"), true);
                case NO_MITOFUS -> serverPlayer.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.no_mitofus"), true);
                case TOO_TIRED -> serverPlayer.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.too_tired",
                        Math.round(FatigueService.energy(serverPlayer) * 100), Math.round(FatigueBalance.current().minEnergyToConnect() * 100)), true);
                case NOT_YOURS -> serverPlayer.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.not_yours"), true);
                case LOCKED -> serverPlayer.displayClientMessage(VrHelmetService.lockedMessage(serverPlayer), true);
                case NO_ROOM -> serverPlayer.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.no_room"), true);
                case ALREADY_CONNECTING, STARTED -> {
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
