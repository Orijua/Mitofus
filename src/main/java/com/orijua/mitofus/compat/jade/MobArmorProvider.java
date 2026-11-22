package com.orijua.mitofus.compat.jade;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.mob.MobArmorRules;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * "Armor: 8": the armor points the mob or NPC has (its base plus what it wears). The attribute is synced to every client,
 * so it needs no server data. Nothing is shown for a mob without armor, nor outside Mitofus (the real world is vanilla).
 */
enum MobArmorProvider implements IEntityComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = Mitofus.id("mob_armor");

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof Mob mob)) {
            return;
        }
        int armor = mob.getArmorValue();
        if (MobArmorRules.showArmor(MitofusWorld.is(mob.level()), armor)) {
            tooltip.add(Component.translatable("jade.mitofus.armor", armor));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
