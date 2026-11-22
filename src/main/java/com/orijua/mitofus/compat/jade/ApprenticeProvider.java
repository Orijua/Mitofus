package com.orijua.mitofus.compat.jade;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.view.Observation;
import com.orijua.mitofus.feature.npc.ApprenticeSmith;
import com.orijua.mitofus.feature.npc.client.ApprenticeUi;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Any apprentice shows his stage and level (hidden by View, as in his window). What he is doing shows only for the
 * player's own, as someone else's window does not open for them either.
 */
enum ApprenticeProvider implements IEntityComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = Mitofus.id("apprentice");

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof ApprenticeSmith apprentice)) {
            return;
        }
        int view = Observation.effectiveView(accessor.getPlayer());
        tooltip.add(ApprenticeUi.stageLine(apprentice, view));
        if (!apprentice.isOwnedBy(accessor.getPlayer())) {
            return;
        }
        Component status = ApprenticeUi.statusText(apprentice.statusText(), apprentice.status().stopped());
        tooltip.add(status);
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
