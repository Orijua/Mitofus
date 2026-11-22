package com.orijua.mitofus.compat.jade;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.npc.ApprenticeSmith;
import com.orijua.mitofus.feature.smelting.SmelterBlock;
import com.orijua.mitofus.feature.smelting.SmelterBlockEntity;
import net.minecraft.world.entity.Mob;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade integration (optional): what the player looks at shows the mob's level and armor, an apprentice's level (his work too when he is yours) and a smelter's
 * heat. Jade finds this class by its annotation, so nothing loads it without Jade. Each line can be turned off in
 * Jade's settings (config.jade.plugin_mitofus.*).
 */
@WailaPlugin(Mitofus.MODID)
public final class MitofusJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        // The smelter's heat lives only on the server (its window gets it through the menu), so Jade asks for it.
        registration.registerBlockDataProvider(SmelterProvider.INSTANCE, SmelterBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(MobLevelProvider.INSTANCE, Mob.class);
        registration.registerEntityComponent(MobArmorProvider.INSTANCE, Mob.class);
        registration.registerEntityComponent(ApprenticeProvider.INSTANCE, ApprenticeSmith.class);
        registration.registerBlockComponent(SmelterProvider.INSTANCE, SmelterBlock.class);
    }
}
