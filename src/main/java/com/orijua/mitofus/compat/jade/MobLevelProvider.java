package com.orijua.mitofus.compat.jade;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.mob.MobLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** "Level 5", or a gold "★ Elite · Level 5": the same the name tag above the mob shows (MobLevelNameTags). */
enum MobLevelProvider implements IEntityComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = Mitofus.id("mob_level");

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        // The level is synced to every client: the name tags read it too.
        MobLevel level = accessor.getEntity().getData(ModAttachments.MOB_LEVEL);
        if (!level.isLeveled()) {
            return;
        }
        tooltip.add(level.elite()
                ? Component.translatable("jade.mitofus.elite_level", level.level()).withStyle(ChatFormatting.GOLD)
                : Component.translatable("jade.mitofus.level", level.level()));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
