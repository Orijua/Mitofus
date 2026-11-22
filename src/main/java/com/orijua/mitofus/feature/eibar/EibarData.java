package com.orijua.mitofus.feature.eibar;

import com.orijua.mitofus.Mitofus;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * What happened to Eibar for the whole world, saved with the overworld (data/mitofus_eibar.dat): whether
 * Velasco died because "The Blacksmith's Rage" failed in a single-player world, and whether his smithy was rebuilt after
 * the contest was won.
 */
final class EibarData extends SavedData {
    private static final String NAME = Mitofus.MODID + "_eibar";
    private static final String VELASCO_DEAD = "velasco_dead";
    private static final String SMITHY_RESTORED = "smithy_restored";

    private boolean velascoDead;
    private boolean smithyRestored;

    static EibarData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(EibarData::new, EibarData::load), NAME);
    }

    private static EibarData load(CompoundTag tag, HolderLookup.Provider registries) {
        EibarData data = new EibarData();
        data.velascoDead = tag.getBoolean(VELASCO_DEAD);
        data.smithyRestored = tag.getBoolean(SMITHY_RESTORED);
        return data;
    }

    boolean velascoDead() {
        return velascoDead;
    }

    void setVelascoDead() {
        velascoDead = true;
        setDirty();
    }

    boolean smithyRestored() {
        return smithyRestored;
    }

    void setSmithyRestored() {
        smithyRestored = true;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(VELASCO_DEAD, velascoDead);
        tag.putBoolean(SMITHY_RESTORED, smithyRestored);
        return tag;
    }
}
