package com.orijua.mitofus.feature.vrhelmet;

import com.orijua.mitofus.core.registry.ModBlockEntities;
import com.orijua.mitofus.core.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.UUID;

/** The player a VR helmet is bound to (nobody until it is used for the first time). It travels with the item. */
public class VrHelmetBlockEntity extends BlockEntity {
    private static final String OWNER = "owner";

    private UUID owner;

    public VrHelmetBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VR_HELMET.get(), pos, state);
    }

    public Optional<UUID> owner() {
        return Optional.ofNullable(owner);
    }

    public void bind(UUID player) {
        owner = player;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.put(OWNER, NbtUtils.createUUID(owner));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.contains(OWNER) ? NbtUtils.loadUUID(tag.get(OWNER)) : null;
    }

    /** Placing a bound VR helmet keeps its owner. */
    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        owner = input.get(ModDataComponents.VR_HELMET_OWNER.get());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (owner != null) {
            builder.set(ModDataComponents.VR_HELMET_OWNER.get(), owner);
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove(OWNER);
    }
}
