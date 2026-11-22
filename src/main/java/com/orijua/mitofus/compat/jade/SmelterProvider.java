package com.orijua.mitofus.compat.jade;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.smelting.SmelterBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** A smelter's temperature, with the same text as its window ("Temperature: 850°"). */
enum SmelterProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = Mitofus.id("smelter");
    private static final String TEMPERATURE = "temperature";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof SmelterBlockEntity smelter) {
            data.putInt(TEMPERATURE, smelter.temperature());
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data.contains(TEMPERATURE)) {
            tooltip.add(Component.translatable("screen.mitofus.smelter_temperature", data.getInt(TEMPERATURE)));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
